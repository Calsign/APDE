/*
 * Somewhat hacked from the Processing Project... it's probably still recognizable
 */

package com.calsignlabs.apde.build;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import android.content.Context;

import processing.app.*;
import processing.core.PApplet;
import processing.data.XML;

public class Manifest {
	public static final String MANIFEST_XML = "AndroidManifest.xml";
	
	public static final String WORLD_OF_HURT_COMING =
			"Errors occurred while reading or writing " + MANIFEST_XML + ",\n" +
			"which means lots of things are likely to stop working properly.\n" +
			"To prevent losing any data, it's recommended that you use “Save As”\n" +
			"to save a separate copy of your sketch, and the restart Processing.";
	public static final String MULTIPLE_ACTIVITIES =
			"Processing only supports a single Activity in the AndroidManifest.xml\n" +
			"file. Only the first activity entry will be updated, and you better \n" +
			"hope that's the right one, smartypants.";
	
	public static final String MIN_SDK = "10";
	
	public static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyMMdd.HHmm", Locale.US);
	
	public static ArrayList<Permission> permissions;
	
	private Build build;
	
	/** the manifest data read from the file */
	private XML xml;
	
	public Manifest(Build build) {
		this.build = build;
		load();
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
			permissions.add(new Permission(PERMISSION_PREFIX, raw, desc));
		}
		
		//Permissions should be sorted already, so don't need to sort them...
		//...but if the problem arises in the future, we can sort them
	}
	
	//Get a working list of permission names
	public static String[] getPermissionNames() {
		String[] perms = new String[permissions.size()];
		for(int i = 0; i < perms.length; i ++)
			perms[i] = permissions.get(i).name();
		
		return perms;
	}
	
	public static void addCustomPermission(String name, String desc) {
		addPermission(PERMISSION_PREFIX, name, desc, false);
	}
	
	public static void addPermission(String prefix, String name, String desc, boolean custom) {
		permissions.add(new Permission(prefix, name, desc, custom));
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
		for (int i = 0; i < count; i++) {
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
			Base.showWarning("Don't touch that", MULTIPLE_ACTIVITIES, null);
		}
		XML activity = kids[0];
		String currentName = activity.getString("android:name");
		// only update if there are changes
		if (currentName == null || !currentName.equals(className)) {
			activity.setString("android:name", "." + className);
			save();
		}
	}
	
	private void writeBlankManifest(final File file) {
		PrintWriter writer;
		
		try {
			writer = new PrintWriter(file);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			return;
		}
		
		writer.println("<?xml version=\"1.0\" encoding=\"utf-8\"?>");
		writer.println("<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\" ");
		// writer.println(" package=\"" + defaultPackageName() + "\" ");
		writer.println(" package=\"\" ");
		
		// Tempting to use 'preferExternal' here, but might annoy some users.
		// 'auto' at least enables it to be moved back and forth
		// http://developer.android.com/guide/appendix/install-location.html
		// writer.println(" android:installLocation=\"auto\" ");
		// Disabling this for now (0190), requires default.properties to use API 8
		
		// This is just a number (like the Processing 'revision'). It should
		// increment with each release. Perhaps P5 should do this automatically
		// with each build or read/write of the manifest file?
		writer.println(" android:versionCode=\"1\" ");
		// This is the version number/name seen by users
		writer.println(" android:versionName=\"1.0\">");
		
		// for now including this... we're wiring to a particular SDK version anyway...
		writer.println(" <uses-sdk android:minSdkVersion=\"" + MIN_SDK + "\" />");
		writer.println(" <application android:label=\"\""); // insert pretty name
		writer.println(" android:icon=\"@drawable/icon\"");
		writer.println(" android:debuggable=\"true\">");
		
		// activity/android:name should be the full name (package + class name) of
		// the actual activity class. or the package can be replaced by a single
		// dot as a prefix as an easier shorthand.
		writer.println(" <activity android:name=\"\">");
		
		writer.println(" <intent-filter>");
		writer.println(" <action android:name=\"android.intent.action.MAIN\" />");
		writer.println(" <category android:name=\"android.intent.category.LAUNCHER\" />");
		writer.println(" </intent-filter>");
		writer.println(" </activity>");
		writer.println(" </application>");
		writer.println("</manifest>");
		writer.flush();
		writer.close();
	}
	
	/**
	 * Save a new version of the manifest info to the build location.
	 * Also fill in any missing attributes that aren't yet set properly.
	 */
	protected void writeBuild(File file, String className, boolean debug) throws IOException {
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
			app.setString("android:debuggable", debug ? "true" : "false");
			
			XML activity = app.getChild("activity");
			// the '.' prefix is just an alias for the full package name
			// http://developer.android.com/guide/topics/manifest/activity-element.html#name
			activity.setString("android:name", "." + className); // this has to be right
			
			PrintWriter writer = new PrintWriter(file);
			writer.print(mf.toString());
			writer.flush();
			writer.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	protected void load() {
		File manifestFile = getManifestFile();
		if (manifestFile.exists()) {
			try {
				xml = new XML(manifestFile);
			} catch (Exception e) {
				e.printStackTrace();
				System.err.println("Problem reading AndroidManifest.xml, creating a new version");

				// remove the old manifest file, rename it with date stamp
				long lastModified = manifestFile.lastModified();
				String stamp = getDateStamp(lastModified);
				File dest = new File(build.getSketchFolder(), MANIFEST_XML + "." + stamp);
				boolean moved = manifestFile.renameTo(dest);
				if (!moved) {
					System.err.println("Could not move/rename " + manifestFile.getAbsolutePath());
					System.err.println("You'll have to move or remove it before continuing.");
					return;
				}
			}
		}
		if (xml == null) {
			writeBlankManifest(manifestFile);
			try {
				xml = new XML(manifestFile);
			} catch (FileNotFoundException e) {
				System.err.println("Could not read " + manifestFile.getAbsolutePath());
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			} catch (ParserConfigurationException e) {
				e.printStackTrace();
			} catch (SAXException e) {
				e.printStackTrace();
			}
		}
		if (xml == null) {
			Base.showWarning("Error handling " + MANIFEST_XML, WORLD_OF_HURT_COMING, null);
		}
	}
	
	protected void save() {
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
	
	private File getManifestFile() {
		return new File(build.getSketchFolder(), MANIFEST_XML);
	}
	
	static public String getDateStamp() {
		return dateFormat.format(new Date());
	}
	
	static public String getDateStamp(long stamp) {
		return dateFormat.format(new Date(stamp));
	}
}