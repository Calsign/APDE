package com.calsignlabs.apde;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;

import com.calsignlabs.apde.build.Manifest;
import com.calsignlabs.apde.contrib.Library;
import com.calsignlabs.apde.tool.AutoFormat;
import com.calsignlabs.apde.tool.ImportLibrary;
import com.calsignlabs.apde.tool.Tool;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Environment;
import android.preference.PreferenceManager;

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
	
	private HashMap<String, ArrayList<Library>> importToLibraryTable;
	private ArrayList<Library> contributedLibraries;
	
	private HashMap<String, Tool> packageToToolTable;
	private ArrayList<Tool> tools;
	
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
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		
		if(prefs.getBoolean("internal_storage_sketchbook", false)) {
			//The "sketchbook" directory on the internal storage
			return getDir("sketchbook", 0);
		} else {
			//The user defined sketchbook location
			String path = prefs.getString("pref_sketchbook_location", "");
			File loc = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getParentFile(), path);
			
			//Only return this if the specified path is valid...
			if(loc.exists() && path.length() > 0)
				return loc;
			else {
				//Update the stored value
				prefs.edit().putString("pref_sketchbook_location", "Sketchbook").commit();
				
				return getDefaultSketchbookFolder();
			}
		}
	}
	
	/**
	 * @return the location of the libraries folder within the Sketchbook
	 */
	public File getLibrariesFolder() {
		return new File(getSketchbookFolder(), "libraries");
	}
	
	/**
	 * @return the default location of the Sketchbook folder (on the external storage)
	 */
	public File getDefaultSketchbookFolder() {
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
	
	public void rebuildLibraryList() {
		//Reset the table mapping imports to libraries
		importToLibraryTable = new HashMap<String, ArrayList<Library>>();
		
		//Android mode has no core libraries - but we'll leave this here just in case
//	    coreLibraries = Library.list(librariesFolder);
//	    for (Library lib : coreLibraries) {
//	      lib.addPackageList(importToLibraryTable);
//	    }
		
		File contribLibrariesFolder = getLibrariesFolder();
		if (contribLibrariesFolder != null) {
			contributedLibraries = Library.list(contribLibrariesFolder);
			for (Library lib : contributedLibraries) {
				lib.addPackageList(importToLibraryTable, (APDE) editor.getApplicationContext());
			}
		}
	}
	
	public HashMap<String, ArrayList<Library>> getImportToLibraryTable() {
		return importToLibraryTable;
	}
	
	public ArrayList<Library> getLibraries() {
		return contributedLibraries;
	}
	
	public String[] listLibraries() {
		String[] output = new String[contributedLibraries.size()];
		for(int i = 0; i < contributedLibraries.size(); i ++) {
			output[i] = contributedLibraries.get(i).getName();
		}
		
		return output;
	}
	
	/**
	 * @param name
	 * @return the library with the specified name, or null if it cannot be found
	 */
	public Library getLibraryByName(String name) {
		for(Library lib : getLibraries()) {
			if(lib.getName().equals(name)) {
				return lib;
			}
		}

		return null;
	}
	
	public void rebuildToolList() {
		if(tools == null) {
			tools = new ArrayList<Tool>();
		} else {
			tools.clear();
		}
		
		if(packageToToolTable == null) {
			packageToToolTable = new HashMap<String, Tool>();
		} else {
			packageToToolTable.clear();
		}
		
		String[] coreTools = new String[] {AutoFormat.PACKAGE_NAME, ImportLibrary.PACKAGE_NAME};
		
		for(String coreTool : coreTools) {
			loadTool(tools, packageToToolTable, coreTool);
		}
		
		//Sort the tools alphabetically
		Collections.sort(tools, new Comparator<Tool>() {
			@Override
			public int compare(Tool a, Tool b) {
				return a.getMenuTitle().compareTo(b.getMenuTitle());
			}
		});
	}
	
	private void loadTool(ArrayList<Tool> list, HashMap<String, Tool> table, String toolName) {
		try {
			Class<?> toolClass = Class.forName(toolName);
			Tool tool = (Tool) toolClass.newInstance();
			
			tool.init(this);
			
			tools.add(tool);
			table.put(toolName, tool);
		} catch (ClassNotFoundException e) {
			System.err.println("Failed to load tool " + toolName);
			e.printStackTrace();
		} catch (InstantiationException e) {
			System.err.println("Failed to load tool " + toolName);
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			System.err.println("Failed to load tool " + toolName);
			e.printStackTrace();
		} catch (Error e) {
			System.err.println("Failed to load tool " + toolName);
			e.printStackTrace();
		} catch (Exception e) {
			System.err.println("Failed to load tool " + toolName);
			e.printStackTrace();
		}
	}
	
	public HashMap<String, Tool> getPackageToToolTable() {
		return packageToToolTable;
	}
	
	public ArrayList<Tool> getTools() {
		return tools;
	}
	
	public String[] listTools() {
		String[] output = new String[tools.size()];
		for(int i = 0; i < tools.size(); i ++) {
			output[i] = tools.get(i).getMenuTitle();
		}
		
		return output;
	}
	
	public Tool getToolByPackageName(String packageName) {
		return packageToToolTable.get(packageName);
	}
}