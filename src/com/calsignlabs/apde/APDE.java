package com.calsignlabs.apde;

import java.io.File;

import android.annotation.SuppressLint;
import android.app.Application;
import android.os.Environment;

/**
 * This is the Application global state for APDE. It manages things like the
 * currently selected sketch and references to the various activities.
 */
public class APDE extends Application {
	private String sketchName;
	private int selectedSketch;
	
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
}