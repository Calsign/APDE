package com.calsignlabs.apde.contrib;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Properties;

import com.calsignlabs.apde.APDE;
import com.calsignlabs.apde.build.Build;

public class Library {
	public static final String propertiesFilename = "library.properties";
	
	private String libraryName;
	
	public enum Status {
		EXTRACTING, DEXING, INSTALLED
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
	
	public String getAuthorList(APDE context) {
		try {
			return getProperty("authorList", context);
		} catch(Exception e) {
			return "";
		}
	}
	
	public String getSentence(APDE context) {
		try {
			return getProperty("sentence", context);
		} catch(Exception e) {
			return "";
		}
	}
	
	/**
	 * @param context
	 * @return the library's root folder
	 */
	public File getLibraryFolder(APDE context) {
		return new File(context.getLibrariesFolder(), libraryName);
	}
	
	public File getPropertiesFile(APDE context) {
		return new File(getLibraryFolder(context), propertiesFilename);
	}
	
	public Properties getProperties(APDE context) throws FileNotFoundException, IOException {
		Properties props = new Properties();
		props.load(new FileInputStream(getPropertiesFile(context)));
		
		return props;
	}
	
	public String getProperty(String key, APDE context) throws FileNotFoundException, IOException {
		return getProperties(context).getProperty(key);
	}
	
	/**
	 * @param context
	 * @return the library's main JAR file
	 */
	public File getLibraryJar(APDE context) {
		return new File(new File(getLibraryFolder(context), "library"), libraryName + ".jar");
	}
	
	//NOTE: Dexed library JARs are created once and stored in the library folder so that we don't have to re-dex them every time we build
	
	/**
	 * @param context
	 * @return the dexed version of the library's main JAR file
	 */
	public File getLibraryDexJar(APDE context) {
		return new File(new File(getLibraryFolder(context), "library-dex"), libraryName + "-dex.jar");
	}
	
	/**
	 * @param context
	 * @return the library's examples folder
	 */
	public File getExamplesFolder(APDE context) {
		return new File(getLibraryFolder(context), "examples");
	}
	
	//This prepends a colon so that it can be appended to other paths safely
	public String getClassPath(APDE context) {
		return File.pathSeparatorChar + getLibraryJar(context).getAbsolutePath();
	}
	
	public File[] getAndroidExports(APDE context) {
		return new File[] {
			getLibraryJar(context),
			getLibraryDexJar(context)
		};
	}
	
	static private FilenameFilter junkFolderFilter = new FilenameFilter() {
		public boolean accept(File dir, String name) {
			//Skip .DS_Store files, .svn folders, etc
			if (name.charAt(0) == '.') return false;
			if (name.equals("CVS")) return false;
			return (new File(dir, name).isDirectory());
		}
	};
	
	static public ArrayList<Library> list(File folder) {
		ArrayList<Library> libraries = new ArrayList<Library>();
		list(folder, libraries);
		return libraries;
	}
	
	static public void list(File folder, ArrayList<Library> libraries) {
		ArrayList<File> librariesFolders = new ArrayList<File>();
		discover(folder, librariesFolders);
		
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
	
	static public void discover(File folder, ArrayList<File> libraries) {
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
						String mess = "The library \""
								+ potentialName
								+ "\" cannot be used.\n"
								+ "Library names must contain only basic letters and numbers.\n"
								+ "(ASCII only and no spaces, and it cannot start with a number)";
						System.err.println("Ignoring bad library name\n\n" + mess);
						continue;
					}
				}
			}
		}
	}
	
	public String[] getPackageList(APDE context) {
		return Build.packageListFromClassPath(getClassPath(context));
	}
	
	//Add this library's packages to the master list
	public void addPackageList(HashMap<String, ArrayList<Library>> importToLibraryTable, APDE context) {
		String[] packageList = getPackageList(context);
		
		for (String pkg : packageList) {
			ArrayList<Library> libraries = importToLibraryTable.get(pkg);
			if (libraries == null) {
				libraries = new ArrayList<Library>();
				importToLibraryTable.put(pkg, libraries);
			} else {
				System.err.println("The library found in");
				System.err.println(getLibraryFolder(context).getAbsolutePath());
				System.err.println("conflicts with");
				for (Library library : libraries) {
					System.err.println(library.getLibraryFolder(context).getAbsolutePath());
				}
				System.err.println("which already define(s) the package " + pkg);
				System.err.println("If you have a line in your sketch that reads");
				System.err.println("import " + pkg + ".*;");
				System.err.println("Then you'll need to first remove one of those libraries.");
				System.err.println();
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
}