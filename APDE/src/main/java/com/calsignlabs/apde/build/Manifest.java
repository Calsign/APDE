/*
 * Somewhat hacked from the Processing Project... it's probably still recognizable
 */

package com.calsignlabs.apde.build;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.calsignlabs.apde.R;
import com.calsignlabs.apde.build.dag.BuildContext;
import com.calsignlabs.apde.build.dag.WriteTemplateBuildTask;

import org.xml.sax.SAXException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import javax.xml.parsers.ParserConfigurationException;

import processing.core.PApplet;
import processing.data.XML;

public class Manifest {
	public static final String MANIFEST_XML = "AndroidManifest.xml";
	
	public static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyMMdd.HHmm", Locale.US);
	
	public static ArrayList<Permission> permissions;
	
	private BuildContext context;
	
	/** the manifest data read from the file */
	private XML xml;
	
	public Manifest(BuildContext context) {
		this.context = context;
	}
	
	public Manifest(Build build) {
		this.context = BuildContext.create(build.editor.getGlobalState());
	}
	
	public static void loadPermissions(Context context) {
		//TODO this is probably a grossly incorrect method of doing this...
		
		//Load the raw list of permissions
		InputStream rawPermissionsStream = context.getResources().openRawResource(context.getResources().getIdentifier("raw/permissions_list", "raw", context.getPackageName()));
		String[] rawPermissions = PApplet.loadStrings(rawPermissionsStream);
		
		permissions = new ArrayList<>(rawPermissions.length);
		
		//Add the permissions
		for(int i = 0; i < rawPermissions.length; i ++) {
			String raw = rawPermissions[i];
			//Get the description from the String resources
			String desc = context.getResources().getString(context.getResources().getIdentifier(raw, "string", context.getPackageName()));
			//Add the permission
			addPermission(PERMISSION_PREFIX, raw, desc, false);
		}
		
		//Add user's custom permissions
		
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		String perms = prefs.getString("user_perms", "");
		String[] parts = perms.split(",");
		
		for(String part : parts)
			if(part.length() > 0)
				addPermission(PERMISSION_PREFIX, part, context.getResources().getString(R.string.permissions_custom_perm), true);
		
		//Sort the permissions (custom permissions are unsorted, even though the loaded ones are sorted)
		sortPermissions();
	}
	
	//Get a working list of permission names
	public static String[] getPermissionNames() {
		String[] perms = new String[permissions.size()];
		for(int i = 0; i < perms.length; i ++)
			perms[i] = permissions.get(i).name();
		
		return perms;
	}
	
	public static void addCustomPermission(String name, String desc, Context context) {
		addPermission(PERMISSION_PREFIX, name, desc, true);
		
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		SharedPreferences.Editor edit = prefs.edit();
		
		//Append the permission to the list of user permissions
		
		String perms = prefs.getString("user_perms", "") + name + ",";
		
		edit.putString("user_perms", perms);
		edit.commit();
	}
	
	public static void addPermission(String prefix, String name, String desc, boolean custom) {
		permissions.add(new Permission(prefix, name, desc, custom));
	}
	
	public static void removeCustomPermission(int perm, Context context) {
		String name = permissions.get(perm).name();
		
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		SharedPreferences.Editor edit = prefs.edit();
		
		String oldPerms = prefs.getString("user_perms", "");
		
		String[] parts = oldPerms.split(name + ","); //TODO watch out for REGEX-injection... it shouldn't really be much of a problem, though...
		
		String perms = "";
		for(String part : parts)
			perms += part;
		
		edit.putString("user_perms", perms);
		edit.commit();
		
		//do this after so that the permission is still there...
		removePermission(perm);
	}
	
	public static void removePermission(int perm) {
		permissions.remove(perm);
	}
	
	public static void sortPermissions() {
		Collections.sort(permissions);
		//A sorted list is backwards
		Collections.reverse(permissions);
	}
	
	public String getPackageName() {
		return xml.getString("package");
	}
	
	public void setPackageName(String packageName) {
		xml.setString("package", packageName);
	}
	
	public static final String PERMISSION_PREFIX = "android.permission.";
	
	public String[] getPermissions() {
		XML[] elements = xml.getChildren("uses-permission");
		int count = elements.length;
		String[] names = new String[count];
		for (int i = 0; i < count; i ++) {
			names[i] = elements[i].getString("android:name");
		}
		return names;
	}
	
	public void setPermissions(String[] names) {
		// Check permissions from the template
		// e.g. watch face already has WAKE_LOCK
		// And don't add them if they're already there
		
		XML[] existingPermissionsXml = xml.getChildren("uses-permission");
		List<String> existingPermissions = new ArrayList<>(existingPermissionsXml.length);
		for (XML perm : existingPermissionsXml) {
			existingPermissions.add(perm.getString("android:name"));
		}
		
		for (String name : names) {
			if (!existingPermissions.contains(name)) {
				XML newbie = xml.addChild("uses-permission");
				newbie.setString("android:name", name);
			}
		}
	}
	
	public void initBlank() {
		File buildManifest = new File(context.getBuildFolder(), MANIFEST_XML);
		writeBlankManifest(buildManifest, context.getComponentTarget());
		load(buildManifest, context.getComponentTarget());
	}
	
	private void writeBlankManifest(final File xmlFile, final ComponentTarget appComp) {
		HashMap<String, String> replaceMap = new HashMap<String, String>();
		replaceMap.put("@@min_sdk@@", Integer.toString(appComp.getMinSdk()));
		replaceMap.put("@@target_sdk@@", Integer.toString(getDefaultTargetSdk()));
		
		WriteTemplateBuildTask.createFileFromTemplate(appComp.getManifestTemplate(), xmlFile, replaceMap, context);
	}
	
	private int getDefaultTargetSdk() {
		return Integer.parseInt(context.getResources().getString(R.string.prop_target_sdk_default));
	}
	
	public void writeCopy(File file) {
		writeCopy(file, context.getSketchName(), context.getComponentTarget());
	}
	
	/**
	 * Save a new version of the manifest info to the build location.
	 * Also fill in any missing attributes that aren't yet set properly.
	 */
	protected void writeCopy(File file, String className, ComponentTarget appComp) {
		// write a copy to the build location
		save(file);
		
		// load the copy from the build location and start messing with it
		try {
			XML mf = new XML(file);
			
			// package name, or default
			String p = mf.getString("package").trim();
			if (p.length() == 0) {
				mf.setString("package", SketchProperties.defaultPackageName(context.getSketchName()));
			}
			
			// app name and label, or the class name
			XML app = mf.getChild("application");
			String label = app.getString("android:label");
			if (label.length() == 0) {
				app.setString("android:label", className);
			}
			app.setString("android:debuggable", "true");
			
			// Services need the label also in the service section
			if (appComp == ComponentTarget.WALLPAPER || appComp == ComponentTarget.WATCHFACE) {
				XML serv = app.getChild("service");
				label = serv.getString("android:label");
				if (label.length() == 0) {
					serv.setString("android:label", className);
				}
			}
			
			save(file);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * @param prettyName
	 */
	public void setPrettyName(String prettyName) {
		xml.getChild("application").setString("android:label", prettyName);
		if (context.getComponentTarget() == ComponentTarget.WALLPAPER) {
			xml.getChild("application").getChild("service").setString("android:label", prettyName);
		}
	}
	
	/**
	 * @return
	 */
	public String getPrettyName() {
		return xml.getChild("application").getString("android:label", "");
	}
	
	/**
	 * @param versionCode
	 */
	public void setVersionCode(int versionCode) {
		xml.setInt("android:versionCode", versionCode);
	}
	
	public int getVersionCode() {
		return xml.getInt("android:versionCode", context.getResources().getInteger(R.integer.prop_version_code_default));
	}
	
	/**
	 * @param prettyVersion
	 */
	public void setPrettyVersion(String prettyVersion) {
		xml.setString("android:versionName", prettyVersion);
	}
	
	public String getPrettyVersion() {
		return xml.getString("android:versionName", context.getResources().getString(R.string.prop_pretty_version_default));
	}
	
	/**
	 * @param targetSdk
	 */
	public void setTargetSdk(int targetSdk) {
		xml.getChild("uses-sdk").setInt("android:targetSdkVersion", targetSdk);
	}
	
	public void setMinSdk(int minSdk) {
		xml.getChild("uses-sdk").setInt("android:minSdkVersion", minSdk);
	}
	
	public int getTargetSdk() {
		return xml.getChild("uses-sdk").getInt("android:targetSdkVersion", context.getResources().getInteger(R.integer.prop_target_sdk_default));
	}
	
	public int getMinSdk() {
		return xml.getChild("uses-sdk").getInt("android:minSdkVersion", context.getResources().getInteger(R.integer.prop_min_sdk_default));
	}
	
	/**
	 * @param orientation
	 */
	public void setOrientation(String orientation) {
		// VR sketches seem to break when specifying "unspecified" as the orientation
		// Because stereo VR needs to be landscape
		// Luckily, this is the same as leaving the orientation blank
		if (!orientation.equals("unspecified")) {
			xml.getChild("application").getChild("activity").setString("android:screenOrientation", orientation);
		}
	}
	
	public String getOrientation() {
		return xml.getChild("application").getChild("activity").getString("android:screenOrientation", context.getResources().getString(R.string.prop_orientation_default));
	}
	
	public void load(File manifestFile, ComponentTarget appComp) {
		if (manifestFile.exists()) {
			try {
				xml = new XML(manifestFile);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		
		String[] permissions = null;
		String pkgName = null;
		int versionCode = -1;
		String versionName = null;
		int targetSdk = -1;
		String orientation = null;
		String prettyName = null;
		
		if (xml == null) {
			try {
				xml = new XML(manifestFile);
				if (permissions != null) {
					setPermissions(permissions);
				}
				if (pkgName != null) {
					xml.setString("package", pkgName);
				}
				if (versionCode != -1) {
					xml.setString("android:versionCode", Integer.toString(versionCode));
				}
				if (versionName != null) {
					xml.setString("android:versionName", versionName);
				}
				if (targetSdk != -1) {
					xml.getChild("uses-sdk").setString("android:targetSdkVersion", Integer.toString(targetSdk));
				}
				if (orientation != null) {
					xml.getChild("application").getChild("activity").setString("android:screenOrientation", orientation);
				}
				if (prettyName != null) {
					xml.getChild("application").setString("android:label", prettyName);
				}
			} catch (FileNotFoundException e) {
				System.err.println(String.format(Locale.US, context.getResources().getString(R.string.manifest_read_failed), manifestFile.getAbsolutePath()));
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			} catch (ParserConfigurationException e) {
				e.printStackTrace();
			} catch (SAXException e) {
				e.printStackTrace();
			} catch (RuntimeException e) {
				// Hopefully this solves some crashes from users doing things that they shouldn't be...
				System.err.println(String.format(Locale.US, context.getResources().getString(R.string.manifest_read_failed_corrupted), manifestFile.getAbsolutePath()));
				e.printStackTrace();
			}
		}
		if (xml == null) {
			System.err.println();
			System.err.println(context.getResources().getString(R.string.manifest_world_of_hurt));
			System.err.println();
		}
	}
	
	/**
	 * Save to the sketch folder, so that it can be copied in later.
	 */
	protected void save(File file) {
		try {
			PrintWriter writer = new PrintWriter(file);
			writer.print(xml.toString());
			writer.flush();
			writer.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}
	
	static public String getDateStamp(long stamp) {
		return dateFormat.format(new Date(stamp));
	}
	
	/**
	 * @return whether or not this manifest file needs to be updated to use fragments, as in
	 * Android Mode 3.0
	 */
	public boolean needsProcessing3Update() {
		return !xml.getChild("application").getChild("activity").getString("android:name").equals(".MainActivity");
	}
	
	public void updateProcessing3() {
		xml.getChild("application").getChild("activity").setString("android:name", ".MainActivity");
		if (xml.getChild("uses-sdk").getInt("android:minSdkVersion") < 17) {
			xml.getChild("uses-sdk").setInt("android:minSdkVersion", 17);
		}
		if (xml.getChild("uses-sdk").getInt("android:targetSdkVersion") < 17) {
			xml.getChild("uses-sdk").setInt("android:targetSdkVersion", 17);
		}
	}
	
	public void loadProperties() {
		loadProperties(context.getSketchProperties(), context.getSketchName());
	}
	
	public void loadProperties(SketchProperties properties, String sketchName) {
		setPackageName(properties.getPackageName(sketchName));
		setPrettyName(properties.getDisplayName(sketchName));
		setVersionCode(properties.getVersionCode());
		setPrettyVersion(properties.getVersionName());
		// Make sure min and target sdks are appropriate
		setMinSdk(Math.max(properties.getMinSdk(), context.getComponentTarget().getMinSdk()));
		setTargetSdk(Math.max(properties.getTargetSdk(), getMinSdk()));
		setOrientation(properties.getOrientation());
		setPermissions(properties.getPermissions());
	}
	
	public SketchProperties copyToProperties() {
		SketchProperties properties = new SketchProperties(context, null);
		properties.setPackageName(getPackageName());
		properties.setDisplayName(getPrettyName());
		properties.setVersionCode(getVersionCode());
		properties.setVersionName(getPrettyVersion());
		properties.setMinSdk(getMinSdk());
		properties.setTargetSdk(getTargetSdk());
		properties.setOrientation(getOrientation());
		try {
			properties.addPermissions(getPermissions());
		} catch (SketchProperties.BadPermissionNameException e) {
			e.printStackTrace();
		}
		return properties;
	}
}