/*
 * Somewhat hacked from the Processing Project... it's probably still recognizable
 */

package com.calsignlabs.apde.build;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.calsignlabs.apde.R;

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
import java.util.Locale;

import javax.xml.parsers.ParserConfigurationException;

import processing.core.PApplet;
import processing.data.XML;

public class Manifest {
	public static final String MANIFEST_XML = "AndroidManifest.xml";
	
	public static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyMMdd.HHmm", Locale.US);
	
	public static ArrayList<Permission> permissions;
	
	private Build build;
	
	/** the manifest data read from the file */
	private XML xml;
	
	static private final String[] MANIFEST_TEMPLATE = {
			"AppManifest.xml.tmpl",
			"WallpaperManifest.xml.tmpl",
			"WatchFaceManifest.xml.tmpl",
			"VRManifest.xml.tmpl",
	};
	
	public Manifest(Build build, int appComp, boolean forceNew) {
		this.build = build;
		load(forceNew, appComp);
	}
	
	public static void loadPermissions(Context context) {
		//TODO this is probably a grossly incorrect method of doing this...
		
		//Load the raw list of permissions
		InputStream rawPermissionsStream = context.getResources().openRawResource(context.getResources().getIdentifier("raw/permissions_list", "raw", context.getPackageName()));
		String[] rawPermissions = PApplet.loadStrings(rawPermissionsStream);
		
		permissions = new ArrayList<Permission>(rawPermissions.length);
		
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
	
	private String defaultPackageName() {
		return "processing.test." + build.sketchName.toLowerCase(Locale.US);
	}
	
	// called by other classes who want an actual package name
	// internally, we'll figure this out ourselves whether it's filled or not
	public String getPackageName() {
		String pkg = xml.getString("package");
		return pkg.length() == 0 ? defaultPackageName() : pkg;
	}
	
	public void setPackageName(String packageName) {
		// this.packageName = packageName;
		// this is the package attribute in the root <manifest> object
		xml.setString("package", packageName);
		save();
	}
	
	public static final String PERMISSION_PREFIX = "android.permission.";
	
	public String[] getPermissions() {
		XML[] elements = xml.getChildren("uses-permission");
		int count = elements.length;
		String[] names = new String[count];
		for (int i = 0; i < count; i ++) {
			names[i] = elements[i].getString("android:name").substring(PERMISSION_PREFIX.length());
		}
		return names;
	}
	
	public void setPermissions(String[] names) {
		// just remove all the old ones
		for (XML kid : xml.getChildren("uses-permission")) {
			xml.removeChild(kid);
		}
		// ...and add the new kids back
		for (String name : names) {
			// PNode newbie = new PNodeXML("uses-permission");
			// newbie.setString("android:name", PERMISSION_PREFIX + name);
			// xml.addChild(newbie);
			XML newbie = xml.addChild("uses-permission");
			newbie.setString("android:name", PERMISSION_PREFIX + name);
		}
		save();
	}
	
	public void setClassName(String className) {
		XML[] kids = xml.getChildren("application/activity");
		if (kids.length != 1) {
//			Base.showWarning("Don't touch that", MULTIPLE_ACTIVITIES, null);
			System.err.println(build.editor.getResources().getString(R.string.manifest_multiple_activities));
		}
		XML activity = kids[0];
		String currentName = activity.getString("android:name");
		// only update if there are changes
		if (currentName == null || !currentName.equals(className)) {
			activity.setString("android:name", "." + className);
			save();
		}
	}
	
	private void writeBlankManifest(final File xmlFile, final int appComp) {
		HashMap<String, String> replaceMap = new HashMap<String, String>();
		if (appComp == Build.APP) {
			replaceMap.put("@@min_sdk@@", Build.MIN_SDK_APP);
		} else if (appComp == Build.WALLPAPER) {
			replaceMap.put("@@min_sdk@@", Build.MIN_SDK_WALLPAPER);
		} else if (appComp == Build.WATCHFACE) {
			replaceMap.put("@@min_sdk@@", Build.MIN_SDK_WATCHFACE);
		} else if (appComp == Build.VR) {
			replaceMap.put("@@min_sdk@@", Build.MIN_SDK_VR);
		}
		replaceMap.put("@@target_sdk@@", Integer.toString(getDefaultTargetSdk()));
		
		Build.createFileFromTemplate(MANIFEST_TEMPLATE[appComp], xmlFile, replaceMap, build.editor);
	}
	
	private int getDefaultTargetSdk() {
		return Integer.parseInt(build.editor.getGlobalState().getString(R.string.prop_target_sdk_default));
	}
	
	/**
	 * Save a new version of the manifest info to the build location.
	 * Also fill in any missing attributes that aren't yet set properly.
	 */
	protected void writeCopy(File file, String className, int appComp) throws IOException {
		// write a copy to the build location
		save(file);
		
		// load the copy from the build location and start messing with it
		XML mf = null;
		try {
			mf = new XML(file);
			
			// package name, or default
			String p = mf.getString("package").trim();
			if (p.length() == 0) {
				mf.setString("package", defaultPackageName());
			}
			
			// app name and label, or the class name
			XML app = mf.getChild("application");
			String label = app.getString("android:label");
			if (label.length() == 0) {
				app.setString("android:label", className);
			}
			app.setString("android:debuggable", "true");
			
			// Services need the label also in the service section
			if (appComp == Build.WALLPAPER || appComp == Build.WATCHFACE) {
				XML serv = app.getChild("service");
				label = serv.getString("android:label");
				if (label.length() == 0) {
					serv.setString("android:label", className);
				}
			}
			
			// Make sure that the required permissions for watch faces and VR apps are
			// included.
			if (appComp == Build.WATCHFACE || appComp == Build.VR) {
				fixPermissions(mf, appComp);
			}
			
			PrintWriter writer = PApplet.createWriter(file);
			writer.print(mf.format(4));
			writer.flush();
			writer.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private void fixPermissions(XML mf, int appComp) {
		boolean hasWakeLock = false;
		boolean hasVibrate = false;
		boolean hasReadExtStorage = false;
		for (XML kid : mf.getChildren("uses-permission")) {
			String name = kid.getString("android:name");
			if (appComp == Build.WATCHFACE && name.equals(PERMISSION_PREFIX + "WAKE_LOCK")) {
				hasWakeLock = true;
				continue;
			}
			if (appComp == Build.VR && name.equals(PERMISSION_PREFIX + "VIBRATE")) {
				hasVibrate = true;
				continue;
			}
			if (appComp == Build.VR && name.equals(PERMISSION_PREFIX + "READ_EXTERNAL_STORAGE")) {
				hasReadExtStorage = true;
				continue;
			}
		}
		if (appComp == Build.WATCHFACE && !hasWakeLock) {
			mf.addChild("uses-permission").setString("android:name", PERMISSION_PREFIX + "WAKE_LOCK");
		}
		if (appComp == Build.VR && !hasVibrate) {
			mf.addChild("uses-permission").setString("android:name", PERMISSION_PREFIX + "VIBRATE");
		}
		if (appComp == Build.VR && !hasReadExtStorage) {
			mf.addChild("uses-permission").setString("android:name", PERMISSION_PREFIX + "READ_EXTERNAL_STORAGE");
		}
	}
	
	//get/setCustomPermissions() - these names are misleading
	//They refer to the permissions for this Manifest instance
	
	/**
	 * @param perms
	 */
	public void setCustomPermissions(String[] perms) {
		//Remove the old permissions
		for(XML perm : xml.getChildren("uses-permission"))
			xml.removeChild(perm);
		
		//Add the permissions
		for(String perm : perms) {
			//For some reason, this crashes on 2.3.3
//			XML permXML = new XML("uses-permission");
//			permXML.setString("android:name", perm);
//			xml.addChild(permXML);
			
			if (!perm.equals("")) {
				//Add a new permission
				xml.addChild("uses-permission");
				//Select the last permission (the newly added one) and change the value
				XML[] permNodes = xml.getChildren("uses-permission");
				permNodes[permNodes.length - 1].setString("android:name", perm);
			}
		}
	}
	
	public String getCustomPermissions() {
		String perms = "";
		
		XML[] children = xml.getChildren("uses-permission");
		for(XML child : children)
			perms += child.getString("android:name", "") + ",";
		
		return perms;
	}
	
	/**
	 * @param prettyName
	 */
	public void setPrettyName(String prettyName) {
		if(!prettyName.equals(".")) //Don't want the default "." sketch!
			xml.getChild("application").setString("android:label", prettyName);
	}
	
	/**
	 * @return
	 */
	public String getPrettyName() {
		return xml.getChild("application").getString("android:label", ".");
	}
	
	/**
	 * @param versionCode
	 */
	public void setVersionCode(int versionCode) {
		xml.setInt("android:versionCode", versionCode);
	}
	
	/**
	 * @param context
	 * @return
	 */
	public int getVersionCode(Context context) {
		return xml.getInt("android:versionCode", context.getResources().getInteger(R.integer.prop_version_code_default));
	}
	
	/**
	 * @param prettyVersion
	 */
	public void setPrettyVersion(String prettyVersion) {
		xml.setString("android:versionName", prettyVersion);
	}
	
	/**
	 * @param context
	 * @return
	 */
	public String getPrettyVersion(Context context) {
		return xml.getString("android:versionName", context.getResources().getString(R.string.prop_pretty_version_default));
	}
	
	/**
	 * @param targetSdk
	 */
	public void setTargetSdk(int targetSdk) {
		xml.getChild("uses-sdk").setInt("android:targetSdkVersion", targetSdk);
	}
	
	/**
	 * @param context
	 * @return
	 */
	public int getTargetSdk(Context context) {
		return xml.getChild("uses-sdk").getInt("android:targetSdkVersion", context.getResources().getInteger(R.integer.prop_target_sdk_default));
	}
	
	/**
	 * @param orientation
	 */
	public void setOrientation(String orientation) {
		xml.getChild("application").getChild("activity").setString("android:screenOrientation", orientation);
	}
	
	public String getOrientation(Context context) {
		return xml.getChild("application").getChild("activity").getString("android:screenOrientation", context.getResources().getString(R.string.prop_orientation_default));
	}
	
	public void load(boolean forceNew, int appComp) {
		File manifestFile = getManifestFile();
		if (manifestFile.exists()) {
			try {
				xml = new XML(manifestFile);
			} catch (Exception e) {
				e.printStackTrace();
				System.err.println(build.editor.getResources().getString(R.string.manifest_problem_reading_creating_new));
				
				// remove the old manifest file, rename it with date stamp
				long lastModified = manifestFile.lastModified();
				String stamp = getDateStamp(lastModified);
				File dest = new File(build.getSketchFolder(), MANIFEST_XML + "." + stamp);
				boolean moved = manifestFile.renameTo(dest);
				if (!moved) {
					System.err.println(String.format(Locale.US, build.editor.getResources().getString(R.string.manifest_move_rename_failed), manifestFile.getAbsolutePath()));
					return;
				}
			}
		}
		
		String[] permissionNames = null;
		String pkgName = null;
		int versionCode = -1;
		String versionName = null;
		int targetSdk = -1;
		String orientation = null;
		String prettyName = null;
		if (xml != null && forceNew) {
			permissionNames = getPermissions();
			pkgName = getPackageName();
			versionCode = getVersionCode(build.editor);
			versionName = getPrettyVersion(build.editor);
			targetSdk = getTargetSdk(build.editor);
			orientation = getOrientation(build.editor);
			prettyName = getPrettyName();
			xml = null;
			
			// If this sketch is out of date, give it the latest and greatest
			if (targetSdk < build.getMinSdk()) {
				targetSdk = getDefaultTargetSdk();
			}
		}
		
		if (xml == null) {
			writeBlankManifest(manifestFile, appComp);
			try {
				xml = new XML(manifestFile);
				if (permissionNames != null) {
					setPermissions(permissionNames);
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
				if (prettyName != null && !prettyName.equals(".")) {
					//Don't want the default "." sketch!
					xml.getChild("application").setString("android:label", prettyName);
				}
			} catch (FileNotFoundException e) {
				System.err.println(String.format(Locale.US, build.editor.getResources().getString(R.string.manifest_read_failed), manifestFile.getAbsolutePath()));
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			} catch (ParserConfigurationException e) {
				e.printStackTrace();
			} catch (SAXException e) {
				e.printStackTrace();
			} catch (RuntimeException e) {
				// Hopefully this solves some crashes from users doing things that they shouldn't be...
				System.err.println(String.format(Locale.US, build.editor.getResources().getString(R.string.manifest_read_failed_corrupted), manifestFile.getAbsolutePath()));
				e.printStackTrace();
			}
		}
		if (xml == null) {
			System.err.println();
			System.err.println(build.editor.getResources().getString(R.string.manifest_world_of_hurt));
			System.err.println();
		}
	}
	
	public void save() {
		save(getManifestFile());
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
	
	public File getManifestFile() {
		return new File(build.getSketchFolder(), MANIFEST_XML);
	}
	
	static public String getDateStamp() {
		return dateFormat.format(new Date());
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
		
		// Don't overwrite examples... they should be updated through the repository
		// But this will allow them to be built properly because it still stores the changes in
		// memory for when the manifest file is copied to the build folder
		if (!build.editor.getGlobalState().isExample()) {
			save();
		}
	}
}