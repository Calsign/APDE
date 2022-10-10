package com.calsignlabs.apde.build;
import com.calsignlabs.apde.R;
import com.calsignlabs.apde.build.dag.BuildContext;
import com.calsignlabs.apde.support.MaybeDocumentFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Properties;

public class SketchProperties {
	protected Properties properties;
	protected BuildContext context;
	
	public static final String KEY_PACKAGE_NAME = "manifest.package";
	public static final String KEY_DISPLAY_NAME = "manifest.label";
	public static final String KEY_VERSION_CODE = "manifest.version.code";
	public static final String KEY_VERSION_NAME = "manifest.version.name";
	public static final String KEY_MIN_SDK = "manifest.sdk.min";
	public static final String KEY_TARGET_SDK = "manifest.sdk.target";
	public static final String KEY_ORIENTATION = "manifest.orientation";
	public static final String KEY_PERMISSIONS = "manifest.permissions";
	
	public SketchProperties(BuildContext context, MaybeDocumentFile propertiesFile) {
		this.context = context;
		try {
			if (propertiesFile != null && propertiesFile.exists()) {
				properties = new Properties(getDefaults());
				loadProperties(properties, propertiesFile.openIn(context.getContentResolver()));
			} else {
				properties = getDefaults();
			}
		} catch (IOException | MaybeDocumentFile.MaybeDocumentFileException e) {
			e.printStackTrace();
			properties = new Properties();
		}
	}
	
	protected Properties getDefaults() throws IOException {
		Properties defaults = new Properties();
		loadProperties(defaults, context.getResources().getAssets().open("sketch.properties.default"));
		return defaults;
	}
	
	public static void loadProperties(Properties properties, InputStream inputStream) throws IOException {
		properties.load(new InputStreamReader(inputStream, Charset.forName("UTF-8")));
	}
	
	public void setPackageName(String packageName) {
		// TODO validate package name?
		properties.setProperty(KEY_PACKAGE_NAME, packageName);
	}
	
	public String getPackageName(String sketchName) {
		String pkgName = properties.getProperty(KEY_PACKAGE_NAME);
		return pkgName.length() != 0 ? pkgName : defaultPackageName(sketchName);
	}
	
	protected static String defaultPackageName(String sketchName) {
		return "processing.test." + sketchName.toLowerCase(Locale.US);
	}
	
	public void setDisplayName(String displayName) {
		properties.setProperty(KEY_DISPLAY_NAME, displayName);
	}
	
	public String getDisplayName(String sketchName) {
		String displayName = properties.getProperty(KEY_DISPLAY_NAME);
		return displayName.length() == 0 ? sketchName : displayName;
	}
	
	public void setVersionCode(int versionCode) {
		properties.setProperty(KEY_VERSION_CODE, Integer.toString(versionCode));
	}
	
	public int getVersionCode() {
		try {
			return Integer.parseInt(properties.getProperty(KEY_VERSION_CODE));
		} catch (NumberFormatException e) {
			System.err.println("Could not read version code: \"" + properties.getProperty(KEY_VERSION_CODE) + "\"");
			return context.getResources().getInteger(R.integer.prop_version_code_default);
		}
	}
	
	public void setVersionName(String versionName) {
		properties.setProperty(KEY_VERSION_NAME, versionName);
	}
	
	public String getVersionName() {
		return properties.getProperty(KEY_VERSION_NAME);
	}
	public void setMinSdk(int minSdk) {
		properties.setProperty(KEY_MIN_SDK, Integer.toString(minSdk));
	}
	
	public int getMinSdk() {
		try {
			return Integer.parseInt(properties.getProperty(KEY_MIN_SDK));
		} catch (NumberFormatException e) {
			System.err.println("Could not read min sdk: \"" + properties.getProperty(KEY_MIN_SDK) + "\"");
			return context.getResources().getInteger(R.integer.prop_min_sdk_default);
		}
	}
	
	public void setTargetSdk(int targetSdk) {
		properties.setProperty(KEY_TARGET_SDK, Integer.toString(targetSdk));
	}
	
	public int getTargetSdk() {
		try {
			return Integer.parseInt(properties.getProperty(KEY_TARGET_SDK));
		} catch (NumberFormatException e) {
			System.err.println("Could not read target sdk: \"" + properties.getProperty(KEY_TARGET_SDK) + "\"");
			return context.getResources().getInteger(R.integer.prop_target_sdk_default);
		}
	}
	
	public void setOrientation(String orientation) {
		properties.setProperty(KEY_ORIENTATION, orientation);
	}
	
	public String getOrientation() {
		return properties.getProperty(KEY_ORIENTATION);
	}
	
	public String getPermissionsString() {
		return properties.getProperty(KEY_PERMISSIONS);
	}
	
	public void setPermissionsString(String permissionsString) {
		properties.setProperty(KEY_PERMISSIONS, permissionsString);
	}
	
	public String[] getPermissions() {
		String[] permissions = getPermissionsString().split(",");
		if (permissions.length == 1 && permissions[0].length() == 0) {
			// Splitting an empty string leaves one empty string
			// Don't want that
			return new String[] {};
		} else {
			return permissions;
		}
	}
	
	public void addPermission(String permission) throws BadPermissionNameException {
		if (permission.contains(",")) {
			throw new BadPermissionNameException("Permissions should not contain ',': " + permission);
		}
		
		setPermissionsString(getPermissionsString() + permission + ",");
	}
	
	public void removePermission(String permission) throws BadPermissionNameException {
		if (permission.contains(",")) {
			throw new BadPermissionNameException("Permissions should not contain ',': " + permission);
		}
		
		String oldStr = getPermissionsString();
		int pos = oldStr.indexOf(permission + ",");
		
		if (pos != -1) {
			setPermissionsString(oldStr.substring(0, pos) + oldStr.substring(pos + permission.length() + 1));
		}
	}
	
	public boolean hasPermission(String permission) throws BadPermissionNameException {
		if (permission.contains(",")) {
			throw new BadPermissionNameException("Permissions should not contain ',': " + permission);
		}
		
		return getPermissionsString().contains(permission + ",");
	}
	
	public void clearPermissions() {
		setPermissionsString("");
	}
	
	public void addPermissions(String... permissions) throws BadPermissionNameException {
		for (String permission : permissions) {
			addPermission(permission);
		}
	}
	
	public void save(MaybeDocumentFile propertiesFile) {
		try {
			OutputStream outputStream = propertiesFile.openOut(context.getContentResolver());
			properties.store(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8), null);
		} catch (IOException | MaybeDocumentFile.MaybeDocumentFileException e) {
			e.printStackTrace();
		}
	}
	
	public static class BadPermissionNameException extends Exception {
		public BadPermissionNameException() {
			super();
		}
		
		public BadPermissionNameException(String message) {
			super(message);
		}
	}
}
