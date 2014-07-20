package com.calsignlabs.apde;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;

import com.calsignlabs.apde.FileNavigatorAdapter.FileItem;
import com.calsignlabs.apde.build.Manifest;
import com.calsignlabs.apde.contrib.Library;
import com.calsignlabs.apde.tool.AutoFormat;
import com.calsignlabs.apde.tool.ColorSelector;
import com.calsignlabs.apde.tool.ImportLibrary;
import com.calsignlabs.apde.tool.Tool;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.Context;
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
	public static final String DEFAULT_SKETCHBOOK_LOCATION = "Sketchbook";
	public static final String LIBRARIES_FOLDER = "libraries";
	public static final String DEFAULT_SKETCH_NAME = "sketch";
	
	private String sketchName;
	
	private EditorActivity editor;
	private SketchPropertiesActivity properties;
	
	private HashMap<String, ArrayList<Library>> importToLibraryTable;
	private ArrayList<Library> contributedLibraries;
	
	private HashMap<String, Tool> packageToToolTable;
	private ArrayList<Tool> tools;
	
	public static enum SketchLocation {
		SKETCHBOOK, // A sketch in the sketchbook folder
		EXAMPLE, // An example on the the internal storage
		LIBRARY_EXAMPLE, // An example packaged with a (contributed) library
		EXTERNAL, // A sketch located on the file system (not in the sketchbook)
		TEMPORARY; // A sketch that has yet to be saved
		
		@Override
		public String toString() {
			switch (this) {
			case SKETCHBOOK:
				return "sketchbook";
			case EXAMPLE:
				return "example";
			case LIBRARY_EXAMPLE:
				return "libraryExample";
			case EXTERNAL:
				return "external";
			case TEMPORARY:
				return "temporary";
			default:
				// Uh-oh...
				return "";
			}
		}
		
		public String toReadableString(Context context) {
			switch(this) {
			case SKETCHBOOK:
				return context.getResources().getString(R.string.sketches);
			case EXAMPLE:
				return context.getResources().getString(R.string.examples);
			case LIBRARY_EXAMPLE:
				return context.getResources().getString(R.string.library_examples);
			default:
				return "";
			}
		}
		
		public static SketchLocation fromString(String value) {
			if (value.equals("sketchbook"))
				return SketchLocation.SKETCHBOOK;
			if (value.equals("example"))
				return SketchLocation.EXAMPLE;
			if (value.equals("libraryExample"))
				return SketchLocation.LIBRARY_EXAMPLE;
			if (value.equals("external"))
				return SketchLocation.EXTERNAL;
			if (value.equals("temporary"))
				return SketchLocation.TEMPORARY;
			
			// Strange...
			return null;
		}
	}
	
	// Location group of the sketch
	private SketchLocation sketchLocation;
	// Relative path to the sketch within its location group
	private String sketchPath;
	
	/**
	 * Changes the name of the current sketch and updates the editor accordingly
	 * Note: This may or may not do what you think it does
	 * 
	 * @param sketchName
	 *            the new name of the sketch
	 */
	@SuppressLint("NewApi")
	public void setSketchName(String sketchName) {
		this.sketchName = sketchName;

		if (editor != null) {
			editor.getSupportActionBar().setTitle(sketchName);
			editor.setSaved(false);
		}
		// Yet another unfortunate casualty of AppCompat
		if (properties != null && android.os.Build.VERSION.SDK_INT >= 11) {
			properties.getActionBar().setTitle(sketchName);
		}
	}
	
	/**
	 * @return the name of the current sketch
	 */
	public String getSketchName() {
		return sketchName;
	}
	
	/**
	 * Select a sketch with the given location and relative path
	 * 
	 * @param sketchPath
	 * @param sketchLocation
	 */
	public void selectSketch(String sketchPath, SketchLocation sketchLocation) {
		this.sketchPath = sketchPath;
		this.sketchLocation = sketchLocation;
		
		if(sketchLocation.equals(SketchLocation.TEMPORARY)) {
			setSketchName(DEFAULT_SKETCH_NAME);
		} else {
			setSketchName(getSketchLocation().getName());
		}
	}
	
	/**
	 * Searches a folder for valid sketches (any folder containing a .PDE file, not including subfolders of other sketches)
	 * 
	 * @param directory
	 * @param depth
	 * @return the list of sketches in the given directory
	 */
	public ArrayList<File> listSketches(File directory, int depth) {
		//Convenience method
		return listSketches(directory, depth, new String[] {});
	}
	
	/**
	 * Searches a folder for valid sketches (any folder containing a .PDE file, not including subfolders of other sketches)
	 * 
	 * @param directory
	 * @param depth
	 * @param ignoreFilenames
	 * @return the list of sketches in the given directory
	 */
	public ArrayList<File> listSketches(File directory, int depth, String[] ignoreFilenames) {
		//Sanity check...
		if(!directory.isDirectory()) {
			return new ArrayList<File>();
		}
		
		//Make sure we don't want to ignore this directory
		for(String ignore : ignoreFilenames) {
			if(directory.getName().equals(ignore)) {
				return new ArrayList<File>();
			}
		}
		
		//Let's check this folder first...
		if(validSketch(directory)) {
			ArrayList<File> output = new ArrayList<File>();
			output.add(directory);
			
			return output;
		}
		
		File[] contents = directory.listFiles();
		ArrayList<File> output = new ArrayList<File>();
		
		//This check permits anything greater the "0" for a countdown...
		//...or values like "-1" for infinite search depth
		if(depth != 0) {
			//Check the subfolders
			for(File file : contents) {
				if(file.isDirectory()) { //This check is redundant, but it's here anyway
					output.addAll(listSketches(file, depth - 1, ignoreFilenames));
				}
			}
		}
		
		return output;
	}
	
	/**
	 * @param directory
	 * @return whether or not the directory contains any sketches
	 */
	public boolean containsSketches(File directory) {
		//Convenience method
		return containsSketches(directory, new String[] {});
	}
	
	/**
	 * @param directory
	 * @param ignoreFilenames
	 * @return whether or not the directory contains any sketches
	 */
	public boolean containsSketches(File directory, String[] ignoreFilenames) {
		//Sanity check...
		if(!directory.isDirectory()) {
			return false;
		}
		
		//Make sure we don't want to ignore this directory
		for(String ignore : ignoreFilenames) {
			if(directory.getName().equals(ignore)) {
				return false;
			}
		}
		
		//Let's check this folder first...
		if(validSketch(directory)) {
			return true;
		}
		
		File[] contents = directory.listFiles();
		
		//Check the subfolders
		for(File file : contents) {
			if(file.isDirectory()) {
				if(validSketch(file)) {
					return true;
				} else if(containsSketches(file)) {
					return true;
				}
			}
		}
		
		return false;
	}
	
	/**
	 * @param sketchFolder
	 * @return whether or not the given folder is a valid sketch folder
	 */
	public boolean validSketch(File sketchFolder) {
		//Sanity check
		if((!sketchFolder.exists()) || (!sketchFolder.isDirectory())) {
			return false;
		}
		
		File[] contents = sketchFolder.listFiles();
		
		for(File file : contents) {
			//Get the file extension
			String filename = file.getName();
			int lastDot = filename.lastIndexOf('.');
			String extension = lastDot != -1 ? filename.substring(lastDot) : "";
			
			//Check for .PDE
			if(extension.equalsIgnoreCase(".pde")) {
				//We have our match
				return true;
			}
		}
		
		return false;
	}
	
	public ArrayList<FileNavigatorAdapter.FileItem> listSketchContainingFolders(File directory) {
		//Convenience method
		return listSketchContainingFolders(directory, new String[] {});
	}
	
	public ArrayList<FileNavigatorAdapter.FileItem> listSketchContainingFolders(File directory, final String[] ignoreFilenames) {
		//Sanity check...
		if(!directory.isDirectory()) {
			return new ArrayList<FileNavigatorAdapter.FileItem>();
		}
		
		File[] contents = directory.listFiles(new FilenameFilter() {
			@Override
			public boolean accept(File dir, String filename) {
				for(String ignore : ignoreFilenames) {
					if(filename.equals(ignore)) {
						return false;
					}
				}
				
				return true;
			}
		});
		
		ArrayList<FileNavigatorAdapter.FileItem> output = new ArrayList<FileNavigatorAdapter.FileItem>();
		
		//Add the "navigate up" button
		output.add(new FileNavigatorAdapter.FileItem(FileNavigatorAdapter.NAVIGATE_UP_TEXT, FileNavigatorAdapter.FileItemType.NAVIGATE_UP));
		
		//Cycle through the files
		for(File file : contents) {
			//Check to see if this folder has anything worth our time
			if(validSketch(file)) {
				output.add(new FileNavigatorAdapter.FileItem(file.getName(), FileNavigatorAdapter.FileItemType.SKETCH));
			} else if(containsSketches(file)) {
				output.add(new FileNavigatorAdapter.FileItem(file.getName(), FileNavigatorAdapter.FileItemType.FOLDER));
			}
		}
		
		//Sort the output alphabetically with folders on top
		Collections.sort(output, new Comparator<FileNavigatorAdapter.FileItem>() {
			@Override
			public int compare(FileItem one, FileItem two) {
				if(one.getType().equals(two.getType())) {
					return one.getText().compareTo(two.getText());
				}
				
				return one.getType().compareTo(two.getType());
			}
		});
		
		return output;
	}
	
	/**
	 * @return the location of the current sketch, be it a sketch, an example, or something else
	 */
	public File getSketchLocation() {
		// Decide what to do...
		
		switch (sketchLocation) {
		case SKETCHBOOK:
			return new File(getSketchbookFolder(), sketchPath);
		case EXAMPLE:
			return new File(getExamplesFolder(), sketchPath);
		case LIBRARY_EXAMPLE:
			return new File(getLibrariesFolder(), sketchPath);
		case EXTERNAL:
			return new File(sketchPath);
		default:
			// Maybe a temporary sketch...
			return null;
		}
	}
	
	/**
	 * @return the location of the sketch, be it a sketch, an example, or something else
	 */
	public File getSketchLocation(String sketchPath, SketchLocation sketchLocation) {
		// Decide what to do...

		switch (sketchLocation) {
		case SKETCHBOOK:
			return new File(getSketchbookFolder(), sketchPath);
		case EXAMPLE:
			return new File(getExamplesFolder(), sketchPath);
		case LIBRARY_EXAMPLE:
			return new File(getLibrariesFolder(), sketchPath);
		case EXTERNAL:
			return new File(sketchPath);
		default:
			// Uh-oh...
			return null;
		}
	}
	
	public SketchLocation getSketchLocationType() {
		return sketchLocation;
	}
	
	public String getSketchPath() {
		return sketchPath;
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
	 * @return the location of the Sketchbook folder on the external storage
	 */
	public File getSketchbookFolder() {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		
		if (prefs.getBoolean("internal_storage_sketchbook", false)) {
			// The "sketchbook" directory on the internal storage
			return getDir("sketchbook", 0);
		} else {
			// The user defined sketchbook location
			String path = prefs.getString("pref_sketchbook_location", "");
			File loc = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getParentFile(), path);
			
			// Only return this if the specified path is valid...
			if (loc.exists() && path.length() > 0)
				return loc;
			else {
				// Update the stored value
				prefs.edit().putString("pref_sketchbook_location", DEFAULT_SKETCHBOOK_LOCATION).commit();
				
				return getDefaultSketchbookFolder();
			}
		}
	}
	
	/**
	 * @return the location of the libraries folder within the Sketchbook
	 */
	public File getLibrariesFolder() {
		return new File(getSketchbookFolder(), LIBRARIES_FOLDER);
	}
	
	/**
	 * @return the default location of the Sketchbook folder (on the external storage)
	 */
	public File getDefaultSketchbookFolder() {
		return new File(Environment.getExternalStoragePublicDirectory(
				Environment.DIRECTORY_DCIM).getParentFile(),
				DEFAULT_SKETCHBOOK_LOCATION);
	}
	
	/**
	 * @return the location of the examples folder on the private internal storage
	 */
	public File getExamplesFolder() {
		/*
		 * We're using the internal private storage directory for now
		 * 
		 * Benefits:
		 *  - Available on all devices
		 *  - Users can't mess with it
		 *  
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
			// http://stackoverflow.com/questions/6593592/get-application-version-programatically-in-android
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
		return sketchLocation.equals(SketchLocation.EXAMPLE) || sketchLocation.equals(SketchLocation.LIBRARY_EXAMPLE);
	}
	
	/**
	 * @return whether or not the sketch is temporary
	 */
	public boolean isTemp() {
		return sketchLocation.equals(SketchLocation.TEMPORARY);
	}
	
	public void putRecentSketch(SketchLocation location, String path) {
		ArrayList<SketchMeta> oldSketches = getRecentSketches();
		SketchMeta[] sketches = new SketchMeta[oldSketches.size() + 1];
		
		//Add the new sketch
		sketches[0] = new SketchMeta(location, path);
		//Copy all of the old sketches over
		System.arraycopy(oldSketches.toArray(), 0, sketches, 1, oldSketches.size());
		
		//We should get a list with the newest sketches on top...
		
		String data = "";
		
		for(int i = 0; i < sketches.length; i ++) {
			data += sketches[i].getLocation().toString() + "," + sketches[i].getPath() + ",\n";
		}
		
		PreferenceManager.getDefaultSharedPreferences(this).edit().putString("recent", data).commit();
	}
	
	public ArrayList<SketchMeta> getRecentSketches() {
		String data = PreferenceManager.getDefaultSharedPreferences(this).getString("recent", "");
		String[] sketchLines = data.split("\n");
		
		ArrayList<SketchMeta> sketches = new ArrayList<SketchMeta>(sketchLines.length);
		
		//20 here is the number of sketches to keep in the recent list
		//TODO maybe make this a preference?
		for(int i = Math.min(sketchLines.length - 1, 20); i >= 0; i --) {
			String[] parts = sketchLines[i].split(",");
			
			//Skip over bad data - this should only happen if the saved data is empty
			if(parts.length < 2) {
				continue;
			}
			
			SketchMeta sketch = new SketchMeta(SketchLocation.fromString(parts[0]), parts[1]);
			
			//Filter out bad sketches
			if(!validSketch(getSketchLocation(sketch.getPath(), sketch.getLocation()))) {
				continue;
			}
			
			//Avoid duplicates
			for(int j = 0; j < sketches.size(); j ++) {
				if(sketches.get(j).equals(sketch)) {
					sketches.remove(j);
				}
			}
			
			sketches.add(sketch);
		}
		
		//Reverse the list...
		Collections.reverse(sketches);
		
		return sketches;
	}
	
	public ArrayList<FileNavigatorAdapter.FileItem> listRecentSketches() {
		ArrayList<SketchMeta> sketches = getRecentSketches();
		
		ArrayList<FileNavigatorAdapter.FileItem> fileItems = new ArrayList<FileNavigatorAdapter.FileItem>(sketches.size() + 1);
		
		//Add the "navigate up" button
		fileItems.add(new FileNavigatorAdapter.FileItem(FileNavigatorAdapter.NAVIGATE_UP_TEXT, FileNavigatorAdapter.FileItemType.NAVIGATE_UP));
		
		for(int i = 0; i < sketches.size(); i ++) {
			fileItems.add(new FileNavigatorAdapter.FileItem(sketches.get(i).getLocation().toReadableString(this) + sketches.get(i).getPathPrefix(), sketches.get(i).getName(), FileNavigatorAdapter.FileItemType.SKETCH));
		}
		
		return fileItems;
	}
	
	public static class SketchMeta {
		private SketchLocation location;
		private String path;
		
		public SketchMeta(SketchLocation location, String path) {
			this.location = location;
			this.path = path;
		}
		
		public SketchLocation getLocation() {
			return location;
		}
		
		public String getPath() {
			return path;
		}
		
		public String getPathPrefix() {
			int lastSlash = path.lastIndexOf('/');
			
			return lastSlash != -1 ? path.substring(0, lastSlash + 1) : "";
		}
		
		public String getName() {
			int lastSlash = path.lastIndexOf('/');
			
			return lastSlash != -1 && path.length() > lastSlash + 1 ? path.substring(lastSlash + 1, path.length()) : path;
		}
		
		@Override
		public boolean equals(Object other) {
			if(other instanceof SketchMeta) {
				SketchMeta otherSketchMeta = (SketchMeta) other;
				
				return otherSketchMeta.getLocation().equals(location) && otherSketchMeta.getPath().equals(path);
			} else {
				return false;
			}
		}
	}
	
	/**
	 * Note: This function loads the manifest as well. For efficiency, call this
	 * function once and store a reference to it.
	 * 
	 * @return the manifest associated with the current sketch
	 */
	public Manifest getManifest() {
		Manifest mf = new Manifest(new com.calsignlabs.apde.build.Build(this));
		mf.load();

		return mf;
	}
	
	public void rebuildLibraryList() {
		// Reset the table mapping imports to libraries
		importToLibraryTable = new HashMap<String, ArrayList<Library>>();
		
		// Android mode has no core libraries - but we'll leave this here just in case
		
//		coreLibraries = Library.list(librariesFolder);
//		for (Library lib : coreLibraries) {
//			lib.addPackageList(importToLibraryTable);
//		}
		
		File contribLibrariesFolder = getLibrariesFolder();
		if (contribLibrariesFolder != null) {
			contributedLibraries = Library.list(contribLibrariesFolder);
			for (Library lib : contributedLibraries) {
				lib.addPackageList(importToLibraryTable,
						(APDE) editor.getApplicationContext());
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
		for (int i = 0; i < contributedLibraries.size(); i++) {
			output[i] = contributedLibraries.get(i).getName();
		}
		
		return output;
	}
	
	/**
	 * @param name
	 * @return the library with the specified name, or null if it cannot be
	 *         found
	 */
	public Library getLibraryByName(String name) {
		for (Library lib : getLibraries()) {
			if (lib.getName().equals(name)) {
				return lib;
			}
		}

		return null;
	}
	
	public void rebuildToolList() {
		if (tools == null) {
			tools = new ArrayList<Tool>();
		} else {
			tools.clear();
		}
		
		if (packageToToolTable == null) {
			packageToToolTable = new HashMap<String, Tool>();
		} else {
			packageToToolTable.clear();
		}
		
		String[] coreTools = new String[] { AutoFormat.PACKAGE_NAME, ImportLibrary.PACKAGE_NAME, ColorSelector.PACKAGE_NAME };
		
		for (String coreTool : coreTools) {
			loadTool(tools, packageToToolTable, coreTool);
		}
		
		// Sort the tools alphabetically
		Collections.sort(tools, new Comparator<Tool>() {
			@Override
			public int compare(Tool a, Tool b) {
				return a.getMenuTitle().compareTo(b.getMenuTitle());
			}
		});
	}
	
	private void loadTool(ArrayList<Tool> list, HashMap<String, Tool> table,String toolName) {
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
		for (int i = 0; i < tools.size(); i++) {
			output[i] = tools.get(i).getMenuTitle();
		}

		return output;
	}
	
	public Tool getToolByPackageName(String packageName) {
		return packageToToolTable.get(packageName);
	}
}