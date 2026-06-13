package com.calsignlabs.apde.build;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipFile;

/**
 * Manages static build resources, build files that don't change between builds. We store them
 * outside the build folder to avoid having to do extra work (deleting and copying) each build.
 *
 * The resources are extracted by ExtractStaticBuildResources.
 */
public class StaticBuildResources {
	public static void extractAll(Context context) throws IOException {
		copyAndroidJar(context);
		deleteFile(getResCompiledDir(context));
		extractAssets(context, "res-compiled", StaticBuildResources.getResCompiledDir(context), true);
		deleteFile(getJniLibsDir(context));
		extractAssets(context, "jniLibs", StaticBuildResources.getJniLibsDir(context), false);
		deleteFile(getLibsDir(context));
		extractAssets(context, "libs", StaticBuildResources.getLibsDir(context), false);
		deleteFile(getLibsDexDir(context));
		extractAssets(context, "libs-dex", StaticBuildResources.getLibsDexDir(context), false);
	}
	
	public static File getStaticBuildResourcesDir(Context context) {
		return context.getDir("static_build_resources", 0);
	}
	
	public static File getResCompiledDir(Context context) {
		return new File(getStaticBuildResourcesDir(context), "res_compiled");
	}
	
	public static File getJniLibsDir(Context context) {
		return new File(getStaticBuildResourcesDir(context), "jni_libs");
	}
	
	public static File getLibsDir(Context context) {
		return new File(getStaticBuildResourcesDir(context), "libs");
	}
	
	public static File getLibsDexDir(Context context) {
		return new File(getStaticBuildResourcesDir(context), "libs_dex");
	}
	
	public static File getAndroidJarLoc(Context context) {
		return new File(getStaticBuildResourcesDir(context), "android.jar");
	}
	
	public static List<File> getTargetDirs(File artifactDir, ComponentTarget target) {
		List<File> dirs = new ArrayList<>();
		for (String prefix : target.getAssetPrefixes()) {
			dirs.add(new File(artifactDir, prefix));
		}
		return dirs;
	}
	
	public static void copyAndroidJar(Context context) throws IOException {
		InputStream is = context.getAssets().open("android.jar");
		createFileFromInputStream(is, getAndroidJarLoc(context));
		is.close();
	}
	
	public static void extractAssets(Context context, String assetsPrefix, File outputDir, boolean unzip) throws IOException {
		if (!assetsPrefix.endsWith("/")) {
			assetsPrefix = assetsPrefix + "/";
		}
		
		outputDir.mkdir();
		
		String[] assetFiles = listAssetFiles(context, assetsPrefix);
		if (assetFiles.length == 0) {
			throw new IOException("No assets found in directory: " + assetsPrefix);
		}
		for (String asset : assetFiles) {
			String targetPrefix = asset.substring(0, asset.indexOf("__"));
			File targetOutputDir = new File(outputDir, targetPrefix);
			if (unzip) {
				createFolderFromZippedAssets(context, assetsPrefix + asset, targetOutputDir);
			} else {
				createFileFromAsset(context, assetsPrefix + asset, new File(targetOutputDir, asset));
			}
		}
	}
	
	private static String[] listAssetFiles(Context context, String assetsPrefix) throws IOException {
		String[] assetFiles = context.getAssets().list(assetsPrefix);
		if (assetFiles != null && assetFiles.length > 0) {
			return assetFiles;
		}
		
		List<String> zipAssetFiles = new ArrayList<>();
		String zipPrefix = "assets/" + assetsPrefix;
		try (ZipFile apk = new ZipFile(context.getApplicationInfo().sourceDir)) {
			Enumeration<? extends ZipEntry> entries = apk.entries();
			while (entries.hasMoreElements()) {
				String name = entries.nextElement().getName();
				if (!name.startsWith(zipPrefix) || name.length() == zipPrefix.length()) {
					continue;
				}
				
				String childName = name.substring(zipPrefix.length());
				int slash = childName.indexOf('/');
				if (slash >= 0) {
					childName = childName.substring(0, slash);
				}
				
				if (childName.length() > 0 && !zipAssetFiles.contains(childName)) {
					zipAssetFiles.add(childName);
				}
			}
		}
		
		return zipAssetFiles.toArray(new String[0]);
	}
	
	private static void createFileFromAsset(Context context, String assetPath, File destFile) throws IOException {
		try {
			InputStream inputStream = context.getAssets().open(assetPath);
			if (createFileFromInputStream(inputStream, destFile) == null) {
				throw new IOException("Failed to extract asset: " + assetPath);
			}
			return;
		} catch (IOException ignored) {
			// Fall through to reading directly from the APK.
		}
		
		try (ZipFile apk = new ZipFile(context.getApplicationInfo().sourceDir)) {
			ZipEntry entry = apk.getEntry("assets/" + assetPath);
			if (entry == null) {
				throw new IOException("Could not access asset: " + assetPath);
			}
			
			if (createFileFromInputStream(apk.getInputStream(entry), destFile) == null) {
				throw new IOException("Failed to extract asset: " + assetPath);
			}
		}
	}
	
	private static void createFolderFromZippedAssets(Context context, String zipFile, File destFile) throws IOException {
		try {
			InputStream inputStream = context.getAssets().open(zipFile);
			createFolderFromZipInputStream(inputStream, zipFile, destFile);
			return;
		} catch (IOException ignored) {
			// Fall through to reading directly from the APK.
		}
		
		try (ZipFile apk = new ZipFile(context.getApplicationInfo().sourceDir)) {
			ZipEntry entry = apk.getEntry("assets/" + zipFile);
			if (entry == null) {
				throw new IOException("Could not access asset zip: " + zipFile);
			}
			
			createFolderFromZipInputStream(apk.getInputStream(entry), zipFile, destFile);
		}
	}
	
	private static void createFolderFromZipInputStream(InputStream input, String zipFile, File destFile) throws IOException {
		try (ZipInputStream inputStream = new ZipInputStream(input)) {
			boolean found = false;
			ZipEntry zipEntry = null;
			while ((zipEntry = inputStream.getNextEntry()) != null) {
				found = true;
				File file = new File(destFile.getAbsolutePath(), zipEntry.getName());
				
				if (zipEntry.isDirectory()) {
					file.mkdirs();
				} else {
					if (createFileFromInputStream(inputStream, file, false) == null) {
						throw new IOException("Failed to extract " + zipEntry.getName() + " from " + zipFile);
					}
					inputStream.closeEntry();
				}
			}
			
			if (!found) {
				throw new IOException("No entries found in asset zip: " + zipFile);
			}
		}
	}
	
	private static void deleteFile(File file) throws IOException {
		if (!file.exists()) {
			return;
		}
		
		if (file.isDirectory()) {
			File[] children = file.listFiles();
			if (children != null) {
				for (File child : children) {
					deleteFile(child);
				}
			}
		}
		
		if (!file.delete()) {
			throw new IOException("Failed to delete file: " + file.getAbsolutePath());
		}
	}
	
	public static File createFileFromInputStream(InputStream inputStream, File destFile) {
		return createFileFromInputStream(inputStream, destFile, true);
	}
	
	// http://stackoverflow.com/questions/11820142/how-to-pass-a-file-path-which-is-in-assets-folder-to-filestring-path
	protected static File createFileFromInputStream(InputStream inputStream, File destFile, boolean close) {
		try {
			// Make sure that the parent folder exists
			destFile.getParentFile().mkdirs();
			
			FileOutputStream outputStream = new FileOutputStream(destFile);
			byte buffer[] = new byte[1024];
			int length = 0;
			
			while ((length = inputStream.read(buffer)) > 0) {
				outputStream.write(buffer, 0, length);
			}
			
			outputStream.close();
			if (close) {
				inputStream.close();
			}
			
			return destFile;
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return null;
	}
}
