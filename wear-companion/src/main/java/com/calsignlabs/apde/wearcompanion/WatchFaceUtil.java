package com.calsignlabs.apde.wearcompanion;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.preference.PreferenceManager;

import com.calsignlabs.apde.wearcompanion.watchface.DefaultSketchWatchFace;

import java.io.File;

import dalvik.system.PathClassLoader;
import processing.android.PWatchFaceCanvas;
import processing.android.PWatchFaceGLES;
import processing.core.PApplet;

public class WatchFaceUtil {
	public static PApplet loadSketchPApplet(Context context, boolean gles) {
		File apkFile = getSketchApk(context);
		
		if (apkFile.exists()) {
			// Really important to set up our class loader with the default one as parent
			// Otherwise we get two copies of loaded classes and things get ugly
			PathClassLoader classLoader = new PathClassLoader(apkFile.getAbsolutePath(), PApplet.class.getClassLoader());
			String classPath = getSketchPackage(context) + "." + getSketchClass(context);
			
			try {
				Class<?> sketch = Class.forName(classPath, true, classLoader);
				
				if (PApplet.class.isAssignableFrom(sketch)) {
					return (PApplet) sketch.newInstance();
				}
			} catch (ClassNotFoundException | InstantiationException | IllegalAccessException | ClassCastException e) {
				e.printStackTrace();
			}
		}
		
		// Default wallpaper if things fail
		return getDefaultSketch(gles);
	}
	
	public static PApplet getDefaultSketch(boolean gles) {
		return new DefaultSketchWatchFace(gles);
	}
	
	public static File getSketchApk(Context context) {
		return new File(context.getFilesDir(), "sketch.apk");
	}
	
	public static String getSketchPackage(Context context) {
		PackageInfo info = context.getPackageManager().getPackageArchiveInfo(getSketchApk(context).getAbsolutePath(), 0);
		return info != null ? info.packageName : null;
	}
	
	public static String getSketchClass(Context context) {
		// The class name is the same as the application label
		PackageInfo info = context.getPackageManager().getPackageArchiveInfo(getSketchApk(context).getAbsolutePath(), 0);
		return info != null? info.applicationInfo.loadLabel(context.getPackageManager()).toString() : null;
	}
	
	private static final int FAILURE = 0;
	private static final int CANVAS = 1;
	private static final int GLES = 2;
	
	/**
	 * Detect whether the sketch is a canvas- or GLES-based watchface (i.e. which renderer it uses).
	 * We do this by checking the base class of the service.
	 *
	 * @param context
	 * @return an integer corresponding to the service type (CANVAS, GLES, or FAILURE)
	 */
	private static int detectServiceType(Context context) {
		File apkFile = getSketchApk(context);
		
		if (apkFile.exists()) {
			// Really important to set up our class loader with the default one as parent
			// Otherwise we get two copies of loaded classes and things get ugly
			PathClassLoader classLoader = new PathClassLoader(apkFile.getAbsolutePath(), PApplet.class.getClassLoader());
			String classPath = getSketchPackage(context) + ".MainService";
			
			try {
				Class<?> service = Class.forName(classPath, true, classLoader);
				
				if (PWatchFaceCanvas.class.isAssignableFrom(service)) {
					return CANVAS;
				} else if (PWatchFaceGLES.class.isAssignableFrom(service)) {
					return GLES;
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		
		return FAILURE;
	}
	
	public static void updateServiceType(Context context) {
		// We want to commit, not apply, because we need the value to be set pretty much immediately
		PreferenceManager.getDefaultSharedPreferences(context).edit().putInt("serviceType", detectServiceType(context)).commit();
	}
	
	public static int getServiceType(Context context) {
		return PreferenceManager.getDefaultSharedPreferences(context).getInt("serviceType", FAILURE);
	}
	
	public static boolean isSketchCanvas(Context context) {
		return getServiceType(context) == CANVAS;
	}
	
	public static boolean isSketchGles(Context context) {
		return getServiceType(context) == GLES;
	}
}
