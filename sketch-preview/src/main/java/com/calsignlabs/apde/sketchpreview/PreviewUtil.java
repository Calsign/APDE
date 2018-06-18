package com.calsignlabs.apde.sketchpreview;

import android.content.Context;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import dalvik.system.PathClassLoader;
import processing.core.PApplet;

/**
 * The brunt work of the sketch previewer. Analogous to the wear companion's WatchFaceUtil.
 */
public class PreviewUtil {
	public static PApplet loadSketchPApplet(Context context, String packageName, String className) {
		File dexFile = getSketchDex(context);
		
		if (dexFile.exists()) {
			// We need to include all of the libraries in addition to the sketch dex
			// This is because we are skipping DX merge to speed up build times
			StringBuilder dexPathList = new StringBuilder(dexFile.getAbsolutePath());
			for (File file : getDexedLibs(context)) {
				dexPathList.append(':');
				dexPathList.append(file.getAbsolutePath());
			}
			
			// Really important to set up our class loader with the default one as parent
			// Otherwise we get two copies of loaded classes and things get ugly
			PathClassLoader classLoader = new PathClassLoader(dexPathList.toString(), PApplet.class.getClassLoader());
			String classPath = packageName + "." + className;
			
			try {
				Class<?> sketch = Class.forName(classPath, true, classLoader);
				
				if (PApplet.class.isAssignableFrom(sketch)) {
					return (PApplet) sketch.newInstance();
				}
			} catch (ClassNotFoundException | InstantiationException | IllegalAccessException | ClassCastException e) {
				e.printStackTrace();
			}
		}
		
		return null;
	}
	
	public static void setupSketchDex(Context context, String path) {
		try {
			copyUri(context, Uri.parse(path), getSketchDex(context));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public static File getSketchDex(Context context) {
		return new File(context.getFilesDir(), "sketch.dex");
	}
	
	/**
	 * Get all of the assets and stick them into the internal storage so that the sketch can use
	 * them.
	 *
	 * @param dataFolder
	 */
	public static void copyAssets(Context context, String dataFolder) {
		// Empty string means data folder is empty
		if (dataFolder.length() > 0) {
			unzipFile(context, Uri.parse(dataFolder), context.getFilesDir());
		}
	}
	
	public static void setupDexedLibs(Context context, String[] dexedLibs) {
		getDexedLibsDir(context).mkdirs();
		
		if (dexedLibs != null) {
			int index = 0;
			for (String dexedLib : dexedLibs) {
				// For some reason we sometimes get null
				if (dexedLib != null) {
					try {
						// It's too much work to get the name from the URI, so just use incremental names
						copyUri(context, Uri.parse(dexedLib), new File(getDexedLibsDir(context), "lib" + index + "-dex.jar"));
					} catch (IOException e) {
						e.printStackTrace();
					}
					index++;
				}
			}
		}
	}
	
	public static File getDexedLibsDir(Context context) {
		return new File(context.getFilesDir(), "dexed_libs");
	}
	
	public static File[] getDexedLibs(Context context) {
		return getDexedLibsDir(context).listFiles(new FilenameFilter() {
			@Override
			public boolean accept(File dir, String filename) {
				return filename.endsWith("-dex.jar");
			}
		});
	}
	
	/**
	 * Clear any shared preferences that the app has set.
	 */
	public static void clearSharedPrefs(Context context) {
		// The sketch might have created other shared preferences files, but we will pretend that
		// they don't exist for now. If this becomes a problem then we can change this.
		PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit();
	}
	
	/**
	 * Clear the assets of the last sketch.
	 */
	public static void clearAssets(Context context) {
		File[] files = context.getFilesDir().listFiles();
		File dexFile = getSketchDex(context);
		for (File file : files) {
			// Don't delete the sketch's APK!
			if (!file.equals(dexFile)) {
				// These log messages don't hurt anybody
				if (!file.delete()) {
					Log.d("apde", "failed to delete " + file.getAbsolutePath());
				} else {
					Log.d("apde", "deleted " + file.getAbsolutePath());
				}
			}
		}
	}
	
	public static void clearDexedLibs(Context context) {
		File[] dexedLibs = getDexedLibs(context);
		// This happens when running the first time because the directory isn't made yet
		if (dexedLibs != null) {
			for (File dexedLib : getDexedLibs(context)) {
				if (!dexedLib.delete()) {
					Log.d("apde", "failed to delete dexed lib: " + dexedLib.getName());
				}
			}
		}
	}
	
	public static void copyUri(Context context, Uri sourceFile, File targetFile) throws IOException {
		BufferedInputStream from = new BufferedInputStream(context.getContentResolver().openInputStream(sourceFile));
		BufferedOutputStream to = new BufferedOutputStream(new FileOutputStream(targetFile));
		byte[] buffer = new byte[16 * 1024];
		int bytesRead;
		while((bytesRead = from.read(buffer)) != -1) {
			to.write(buffer, 0, bytesRead);
		}
		from.close();
		to.flush();
		to.close();
	}
	
	/**
	 * Get all of the data files and stick them into the internal storage so that the sketch can use
	 * them.
	 *
	 * @param zipFile
	 * @param destFolder
	 */
	public static void unzipFile(Context context, Uri zipFile, File destFolder) {
		try {
			ZipInputStream inputStream = new ZipInputStream(context.getContentResolver().openInputStream(zipFile));
			
			ZipEntry zipEntry = null;
			while ((zipEntry = inputStream.getNextEntry()) != null) {
				String name = zipEntry.getName();
				
				File file = new File(destFolder.getAbsolutePath(), name);
				
				if (zipEntry.isDirectory()) {
					file.mkdirs();
				} else {
					createFileFromInputStream(inputStream, file, false);
					inputStream.closeEntry();
				}
			}
			
			inputStream.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	// http://stackoverflow.com/questions/11820142/how-to-pass-a-file-path-which-is-in-assets-folder-to-filestring-path
	private static File createFileFromInputStream(InputStream inputStream, File destFile, boolean close) throws IOException {
		// Make sure that the parent folder exists
		destFile.getParentFile().mkdirs();
		
		FileOutputStream outputStream = new FileOutputStream(destFile);
		byte buffer[] = new byte[4096];
		int length = 0;
		
		while ((length = inputStream.read(buffer)) > 0) {
			outputStream.write(buffer, 0, length);
		}
		
		outputStream.close();
		if (close) {
			inputStream.close();
		}
		
		return destFile;
	}
}
