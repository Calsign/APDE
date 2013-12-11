package com.calsignlabs.apde;

import java.io.File;

import android.app.Application;
import android.os.Environment;

public class APDE extends Application {
	private String sketchName;
	private int selectedSketch;
	
	private EditorActivity editor;
	private SketchPropertiesActivity properties;
	
	public void setSketchName(String sketchName) {
		this.sketchName = sketchName;
		
		if(editor != null) {
			editor.getSupportActionBar().setTitle(sketchName);
			editor.setSaved(false);
		}
		if(properties != null) properties.getSupportActionBar().setTitle(sketchName);
	}
	
	public String getSketchName() {
		return sketchName;
	}

	public EditorActivity getEditor() {
		return editor;
	}

	public void setEditor(EditorActivity editor) {
		this.editor = editor;
	}

	public SketchPropertiesActivity getProperties() {
		return properties;
	}

	public void setProperties(SketchPropertiesActivity properties) {
		this.properties = properties;
	}

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