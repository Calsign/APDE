package com.calsignlabs.apde.contrib;

import android.content.Context;

import com.calsignlabs.apde.APDE;
import com.calsignlabs.apde.R;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import processing.core.PApplet;

public class Library {
	public static final String propertiesFilename = "library.properties";
	
	private String libraryName;
	
	public enum Status {
		COPYING, EXTRACTING, DEXING, INSTALLED
	}
	
	private Status status;
	
	public Library(String libraryName) {
		this.libraryName = libraryName;
	}
	
	public Library(File libraryFile) {
		this.libraryName = libraryFile.getName();
	}
	
	public Status getStatus() {
		return status;
	}
	
	public void setStatus(Status status) {
		this.status = status;
	}
	
	public String getName() {
		return libraryName;
	}
	
	public void setName(String name) {
		libraryName = name;
	}
	
	public String getAuthorList(File sketchbookLibrariesFolder) {
		try {
			// Not all library.properties file are consistent
			if (hasProperty("authorList", sketchbookLibrariesFolder)) return getProperty("authorList", sketchbookLibrariesFolder);
			else if (hasProperty("authors", sketchbookLibrariesFolder)) return getProperty("authors", sketchbookLibrariesFolder);
			else if (hasProperty("author", sketchbookLibrariesFolder)) return getProperty("author", sketchbookLibrariesFolder);
			else return "";
		} catch(Exception e) {
			return "";
		}
	}
	
	public String getSentence(File sketchbookLibrariesFolder) {
		try {
			return getProperty("sentence", sketchbookLibrariesFolder);
		} catch(Exception e) {
			return "";
		}
	}
	
	/**
	 * @param sketchbookLibrariesFolder
	 * @return the library's root folder
	 */
	public File getLibraryFolder(File sketchbookLibrariesFolder) {
		return new File(sketchbookLibrariesFolder, libraryName);
	}
	
	public File getPropertiesFile(File sketchbookLibrariesFolder) {
		return new File(getLibraryFolder(sketchbookLibrariesFolder), propertiesFilename);
	}
	
	public Properties getProperties(File sketchbookLibrariesFolder) throws FileNotFoundException, IOException {
		Properties props = new Properties();
		props.load(new FileInputStream(getPropertiesFile(sketchbookLibrariesFolder)));
		
		return props;
	}
	
	public String getProperty(String key, File sketchbookLibrariesFolder) throws FileNotFoundException, IOException {
		return getProperties(sketchbookLibrariesFolder).getProperty(key);
	}
	
	public boolean hasProperty(String key, File sketchbookLibrariesFolder) throws FileNotFoundException, IOException {
		return getProperties(sketchbookLibrariesFolder).containsKey(key);
	}
	
	public File getLibraryJarFolder(File sketchbookLibrariesFolder) {
		return new File(getLibraryFolder(sketchbookLibrariesFolder), "library");
	}
	
	/**
	 * @param sketchbookLibrariesFolder
	 * @return the library's JAR files
	 */
	public File[] getLibraryJars(File sketchbookLibrariesFolder) {
		String[] filenames = getLibraryJarNames(sketchbookLibrariesFolder);
		File[] files = new File[filenames.length];
		
		for(int i = 0; i < files.length; i ++) {
			files[i] = new File(getLibraryJarFolder(sketchbookLibrariesFolder), filenames[i] + ".jar");
		}
		
		return files;
	}
	
	//NOTE: Dexed library JARs are created once and stored in the library folder so that we don't have to re-dex them every time we build
	
	public File getLibraryJarDexFolder(File sketchbookLibrariesFolder) {
		return new File(getLibraryFolder(sketchbookLibrariesFolder), "library-dex");
	}
	
	/**
	 * @param sketchbookLibrariesFolder
	 * @return the dexed versions of the library's JAR files
	 */
	public File[] getLibraryDexJars(File sketchbookLibrariesFolder) {
		String[] filenames = getLibraryJarNames(sketchbookLibrariesFolder);
		File[] files = new File[filenames.length];
		
		for(int i = 0; i < files.length; i ++) {
			files[i] = new File(getLibraryJarDexFolder(sketchbookLibrariesFolder), filenames[i] + "-dex.jar");
		}
		
		return files;
	}
	
	/**
	 * @param sketchbookLibrariesFolder
	 * @return the JARs found in the "library" subfolder, without the ".jar" suffix
	 */
	private String[] getLibraryJarNames(File sketchbookLibrariesFolder) {
		//The files in the "library" and "library-dex" folders should match up
		//The dexed versions don't exist when we're installing the library, so we'll read the regular ones
		
		File libraryJarFolder = getLibraryJarFolder(sketchbookLibrariesFolder);
		
		String[] files = libraryJarFolder.list(jarFilter);
		
		//Strip the ".jar"
		for(int i = 0; i < files.length; i ++) {
			files[i] = files[i].substring(0, files[i].length() - 4);
		}
		
		return files;
	}
	
	static private FilenameFilter jarFilter = new FilenameFilter() {
		public boolean accept(File dir, String name) {
			//Skip directories
			if (new File(dir, name).isDirectory()) return false;
			return (name.endsWith(".jar"));
		}
	};
	
	/**
	 * @param sketchbookLibrariesFolder
	 * @return the library's examples folder
	 */
	public File getExamplesFolder(File sketchbookLibrariesFolder) {
		return new File(getLibraryFolder(sketchbookLibrariesFolder), "examples");
	}
	
	//This prepends a colon so that it can be appended to other paths safely
	public String getClassPath(File sketchbookLibrariesFolder) {
		String classPath = "";
		
		for(File file : getLibraryJars(sketchbookLibrariesFolder)) {
			classPath += File.pathSeparatorChar + file.getAbsolutePath();
		}
		
		return classPath;
	}
	
	public File[] getAndroidExports(File sketchbookLibrariesFolder) {
		File[] jars = getLibraryJars(sketchbookLibrariesFolder);
		File[] dexJars = getLibraryDexJars(sketchbookLibrariesFolder);
		
		//Concatenate the arrays
		
		File[] exports = new File[jars.length + dexJars.length];
		
		System.arraycopy(jars, 0, exports, 0, jars.length);
		System.arraycopy(dexJars, 0, exports, jars.length, dexJars.length);
		
		return exports;
	}
	
	static private FilenameFilter junkFolderFilter = new FilenameFilter() {
		public boolean accept(File dir, String name) {
			//Skip .DS_Store files, .svn folders, etc
			if (name.charAt(0) == '.') return false;
			if (name.equals("CVS")) return false;
			return (new File(dir, name).isDirectory());
		}
	};
	
	static public ArrayList<Library> list(File folder, Context context) {
		ArrayList<Library> libraries = new ArrayList<Library>();
		list(folder, libraries, context);
		return libraries;
	}
	
	static public void list(File folder, ArrayList<Library> libraries, Context context) {
		ArrayList<File> librariesFolders = new ArrayList<File>();
		discover(folder, librariesFolders, context);
		
		for (File baseFolder : librariesFolders) {
			libraries.add(new Library(baseFolder));
		}
		
		//How about... let's not support subfolders just yet. That sounds good...
//		String[] list = folder.list(junkFolderFilter);
//		if (list != null) {
//			for (String subfolderName : list) {
//				File subfolder = new File(folder, subfolderName);
//				
//				if (!libraries.contains(subfolder)) {
//					ArrayList<File> discoveredLibFolders = new ArrayList<File>();
//					discover(subfolder, discoveredLibFolders);
//					
//					for (File discoveredFolder : discoveredLibFolders) {
//						libraries.add(new Library(discoveredFolder, subfolderName));
//					}
//				}
//			}
//		}
	}
	
	static public void discover(File folder, ArrayList<File> libraries, Context context) {
		String[] list = folder.list(junkFolderFilter);
		
		//If a bad folder or something like that, this might come back null
		if (list != null) {
			//Alphabetize list, since it's not always alpha order
			//Replaced hella slow bubble sort with this feller for 0093
			Arrays.sort(list, String.CASE_INSENSITIVE_ORDER);
			
			for (String potentialName : list) {
				File baseFolder = new File(folder, potentialName);
				File libraryFolder = new File(baseFolder, "library");
				File libraryJar = new File(libraryFolder, potentialName + ".jar");
				//If a .jar file of the same prefix as the folder exists
				//inside the 'library' subfolder of the sketch
				if (libraryJar.exists()) {
					String sanityCheck = processing.app.Sketch.sanitizeName(potentialName);
					if (sanityCheck.equals(potentialName)) {
						libraries.add(baseFolder);
					} else {
						System.err.println(String.format(Locale.US, context.getResources().getString(R.string.library_add_sanity_check_mess_message), potentialName));
						
						continue;
					}
				}
			}
		}
	}
	
	public String[] getPackageList(APDE context) {
		return packageListFromClassPath(getClassPath(context.getLibrariesFolder()), context);
	}
	
	//Add this library's packages to the master list
	public void addPackageList(Map<String, List<Library>> importToLibraryTable, APDE context) {
		String[] packageList = getPackageList(context);
		
		for (String pkg : packageList) {
			List<Library> libraries = importToLibraryTable.get(pkg);
			if (libraries == null) {
				libraries = new ArrayList<>();
				importToLibraryTable.put(pkg, libraries);
			} else {
				StringBuilder libraryConflicts = new StringBuilder();
				for (Library library : libraries) {
					libraryConflicts.append(library.getLibraryFolder(context.getLibrariesFolder()).getAbsolutePath());
				}
				
				System.err.println(String.format(Locale.US,
						context.getResources().getString(R.string.library_package_name_conflict),
						getLibraryFolder(context.getLibrariesFolder()).getAbsolutePath(), libraryConflicts.toString(), pkg));
			}
			libraries.add(this);
		}
	}
	
	//This value *should* be different for every library - yes, libraries can have the same name... but it looks like we aren't worring about that right now
	//Seeing as we might not be connected to the internet, we can't rely on the nice table that the Processing devs compiled for us (which has separate IDs for each library)...
	//...additionally, that wouldn't work with custom-built libraries
	//Ideally, we'd have some kind checksum... but that's too much work for right now
	//So we're just using library names
	//...
	//Besides, the libraries of the same name would conflict because they'd both try to use the same folder in the libraries folder anyway
	public String consumableValue() {
		return getName();
	}
	
	static public String[] packageListFromClassPath(String path, Context context) {
		Hashtable<String, Object> table = new Hashtable<>();
		String[] pieces = PApplet.split(path, File.pathSeparatorChar);
		
		for(int i = 0; i < pieces.length; i++) {
			if(pieces[i].length() == 0) continue;
			
			if(pieces[i].toLowerCase(Locale.US).endsWith(".jar") || pieces[i].toLowerCase(Locale.US).endsWith(".zip"))
				packageListFromZip(pieces[i], table, context);
			else {  // it's another type of file or directory
				File dir = new File(pieces[i]);
				if(dir.exists() && dir.isDirectory())
					packageListFromFolder(dir, null, table);
			}
		}
		int tableCount = table.size();
		String[] output = new String[tableCount];
		int index = 0;
		Enumeration<String> e = table.keys();
		while(e.hasMoreElements())
			output[index++] = (e.nextElement()).replace('/', '.');
		
		return output;
	}
	
	static private void packageListFromZip(String filename, Hashtable<String, Object> table, Context context) {
		try {
			ZipFile file = new ZipFile(filename);
			Enumeration<? extends ZipEntry> entries = file.entries();
			while(entries.hasMoreElements()) {
				ZipEntry entry = entries.nextElement();
				
				if(!entry.isDirectory()) {
					String name = entry.getName();
					
					if(name.endsWith(".class")) {
						int slash = name.lastIndexOf('/');
						if(slash == -1) continue;
						
						String pname = name.substring(0, slash);
						if(table.get(pname) == null) {
							table.put(pname, new Object());
						}
					}
				}
			}
			file.close();
		} catch (IOException e) {
			System.err.println(String.format(Locale.US, context.getResources().getString(R.string.build_package_from_zip_ignoring), filename, e.getMessage()));
		}
	}
	
	static private void packageListFromFolder(File dir, String sofar, Hashtable<String, Object> table) {
		boolean foundClass = false;
		String[] files = dir.list();
		
		for(int i = 0; i < files.length; i++) {
			if(files[i].equals(".") || files[i].equals("..")) continue;
			
			File sub = new File(dir, files[i]);
			if(sub.isDirectory()) {
				String nowfar = (sofar == null) ? files[i] : (sofar + "." + files[i]);
				packageListFromFolder(sub, nowfar, table);
			} else if(!foundClass) {  // if no classes found in this folder yet
				if(files[i].endsWith(".class")) {
					table.put(sofar, new Object());
					foundClass = true;
				}
			}
		}
	}
}