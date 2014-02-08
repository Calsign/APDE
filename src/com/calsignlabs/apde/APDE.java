package com.calsignlabs.apde;

import java.io.File;

import com.calsignlabs.apde.build.Manifest;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Environment;

/**
 * This is the Application global state for APDE. It manages things like the
 * currently selected sketch and references to the various activities.
 */
public class APDE extends Application {
	private String sketchName;
	private int selectedSketch;
	private boolean example;
	
	private EditorActivity editor;
	private SketchPropertiesActivity properties;
	
	/**
	 * Changes the name of the current sketch and updates the editor accordingly
	 * Note: This may or may not do what you think it does
	 * 
	 * @param sketchName the new name of the sketch
	 */
	@SuppressLint("NewApi")
	public void setSketchName(String sketchName) {
		this.sketchName = sketchName;
		
		if(editor != null) {
			editor.getSupportActionBar().setTitle(sketchName);
			editor.setSaved(false);
		}
		//Yet another unfortunate casualty of AppCompat
		if(properties != null && android.os.Build.VERSION.SDK_INT >= 11)
			properties.getActionBar().setTitle(sketchName);
	}
	
	/**
	 * @return the name of the current sketch
	 */
	public String getSketchName() {
		return sketchName;
	}
	
	/**
	 * @return a reference to the current EditorActivity
	 */
	public EditorActivity getEditor() {
		return editor;
	}
	
	public void setEditor(EditorActivity editor) {
		this.editor = editor;
	}

	/**
	 * @return a reference to the current SketchProperties activity
	 */
	public SketchPropertiesActivity getProperties() {
		return properties;
	}

	public void setProperties(SketchPropertiesActivity properties) {
		this.properties = properties;
	}
	
	/**
	 * @return the index of the currently selected sketch
	 */
	public int getSelectedSketch() {
		return selectedSketch;
	}
	
	public void setSelectedSketch(int selectedSketch) {
		this.selectedSketch = selectedSketch;
	}
	
	/**
	 * @return the location of the Sketchbook folder on the external storage
	 */
	public File getSketchbookFolder() {
		return new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getParentFile(), "Sketchbook");
	}
	
	/**
	 * @return the location of the examples folder on the private internal storage
	 */
	public File getExamplesFolder() {
		/* 
		 * We're using the internal private storage directory for now
		 * Benefits:
		 *  - Available on all devices
		 *  - Users can't mess with it
		 * Downsides:
		 *  - Adds to the app's apparent required storage
		 *  - Will fail if the internal storage is full
		 */
		
		return getDir("examples", 0);
	}
	
	/**
	 * @return the current version code of APDE
	 */
	public int appVersionCode() {
		try {
			//http://stackoverflow.com/questions/6593592/get-application-version-programatically-in-android
			PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
			return pInfo.versionCode;
		} catch (NameNotFoundException e) {
			e.printStackTrace();
		}
		
		return -1;
	}
	
	/**
	 * @return whether or not the current sketch is an example
	 */
	public boolean isExample() {
		return example;
	}
	
	/**
	 * @param example whether or not the current sketch is an example
	 */
	public void setExample(boolean example) {
		this.example = example;
	}
	
	/**
	 * Note: This function loads the manifest as well.
	 * For efficiency, call this function once and store a reference to it.
	 * 
	 * @return the manifest associated with the current sketch
	 */
	public Manifest getManifest() {
		Manifest mf = new Manifest(new com.calsignlabs.apde.build.Build(this));
		mf.load();
		
		return mf;
	}
}