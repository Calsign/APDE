package com.calsignlabs.apde.build;

import android.content.Context;
import android.content.res.AssetManager;

import com.calsignlabs.apde.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Manages static build resources, build files that don't change between builds. We store them
 * outside the build folder to avoid having to do extra work (deleting and copying) each build.
 *
 * The resources are extracted by ExtractStaticBuildResources.
 */
public class StaticBuildResources {
	public static void extractAll(Context context) throws IOException {
		copyAndroidJar(context);
		extractAssets(context, "res-compiled", StaticBuildResources.getResCompiledDir(context), true);
		extractAssets(context, "jniLibs", StaticBuildResources.getJniLibsDir(context), false);
		extractAssets(context, "libs", StaticBuildResources.getLibsDir(context), false);
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
		
		AssetManager am = context.getAssets();
		
		outputDir.mkdir();
		
		String[] assetFiles = am.list(assetsPrefix);
		if (assetFiles == null) {
			System.err.println("Could not access assets directory: " + assetsPrefix);
			throw new IOException("Could not access assets directory: " + assetsPrefix);
		}
		for (String asset : assetFiles) {
			String targetPrefix = asset.substring(0, asset.indexOf("__"));
			File targetOutputDir = new File(outputDir, targetPrefix);
			if (unzip) {
				createFolderFromZippedAssets(am, assetsPrefix + asset, targetOutputDir);
			} else {
				InputStream inputStream = am.open(assetsPrefix + asset);
				createFileFromInputStream(inputStream, new File(targetOutputDir, asset));
				inputStream.close();
			}
		}
	}
	
	private static File createFolderFromZippedAssets(AssetManager am, String zipFile, File destFile) {
		try {
			ZipInputStream inputStream = new ZipInputStream(am.open(zipFile));
			
			ZipEntry zipEntry = null;
			while ((zipEntry = inputStream.getNextEntry()) != null) {
				File file = new File(destFile.getAbsolutePath(), zipEntry.getName());
				
				if (zipEntry.isDirectory()) {
					file.mkdirs();
				} else {
					createFileFromInputStream(inputStream, file, false);
					inputStream.closeEntry();
				}
			}
			
			inputStream.close();
			
			return destFile;
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return null;
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
			
			return destFile;
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (close) {
				try {
					inputStream.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		
		return null;
	}
}
