package com.calsignlabs.apde.contrib;

import android.content.Context;

import com.calsignlabs.apde.support.documentfile.DocumentFile;

import com.calsignlabs.apde.APDE;
import com.calsignlabs.apde.R;
import com.calsignlabs.apde.support.MaybeDocumentFile;

import java.io.BufferedInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class Library {
	public static final String propertiesFilename = "library.properties";
	
	private String libraryName;
	
	private final APDE context;
	
	public enum Status {
		COPYING, EXTRACTING, DEXING, INSTALLED
	}
	
	private Status status;
	
	public Library(String libraryName, APDE context) {
		this.libraryName = libraryName;
		this.context = context;
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
	
	public String getAuthorList(MaybeDocumentFile sketchbookLibrariesFolder) {
		try {
			// Not all library.properties file are consistent
			if (hasProperty("authorList", sketchbookLibrariesFolder)) {
				return getProperty("authorList", sketchbookLibrariesFolder);
			} else if (hasProperty("authors", sketchbookLibrariesFolder)) {
				return getProperty("authors", sketchbookLibrariesFolder);
			} else if (hasProperty("author", sketchbookLibrariesFolder)) {
				return getProperty("author", sketchbookLibrariesFolder);
			} else {
				return "";
			}
		} catch (Exception e) {
			return "";
		}
	}
	
	public String getSentence(MaybeDocumentFile sketchbookLibrariesFolder) {
		try {
			return getProperty("sentence", sketchbookLibrariesFolder);
		} catch (Exception e) {
			return "";
		}
	}
	
	/**
	 * @param sketchbookLibrariesFolder
	 * @return the library's root folder
	 */
	public MaybeDocumentFile getLibraryFolder(MaybeDocumentFile sketchbookLibrariesFolder) throws MaybeDocumentFile.MaybeDocumentFileException {
		return sketchbookLibrariesFolder.childDirectory(libraryName);
	}
	
	public MaybeDocumentFile getPropertiesFile(MaybeDocumentFile sketchbookLibrariesFolder) throws MaybeDocumentFile.MaybeDocumentFileException {
		return getLibraryFolder(sketchbookLibrariesFolder).child(propertiesFilename, "text/x-java-properties");
	}
	
	public Properties getProperties(MaybeDocumentFile sketchbookLibrariesFolder) throws IOException, MaybeDocumentFile.MaybeDocumentFileException {
		Properties props = new Properties();
		MaybeDocumentFile propertiesFile = getPropertiesFile(sketchbookLibrariesFolder);
		if (propertiesFile.exists()) {
			props.load(getPropertiesFile(sketchbookLibrariesFolder).openIn(context.getContentResolver()));
		}
		return props;
	}
	
	public String getProperty(String key, MaybeDocumentFile sketchbookLibrariesFolder) throws IOException, MaybeDocumentFile.MaybeDocumentFileException {
		return getProperties(sketchbookLibrariesFolder).getProperty(key);
	}
	
	public boolean hasProperty(String key, MaybeDocumentFile sketchbookLibrariesFolder) throws IOException, MaybeDocumentFile.MaybeDocumentFileException {
		return getProperties(sketchbookLibrariesFolder).containsKey(key);
	}
	
	public MaybeDocumentFile getLibraryJarFolder(MaybeDocumentFile sketchbookLibrariesFolder) throws MaybeDocumentFile.MaybeDocumentFileException {
		return getLibraryFolder(sketchbookLibrariesFolder).childDirectory("library");
	}
	
	/**
	 * @param sketchbookLibrariesFolder
	 * @return the library's JAR files
	 */
	public List<DocumentFile> getLibraryJars(MaybeDocumentFile sketchbookLibrariesFolder) throws MaybeDocumentFile.MaybeDocumentFileException {
		List<String> filenames = getLibraryJarNames(sketchbookLibrariesFolder);
		MaybeDocumentFile libraryJarFolder = getLibraryJarFolder(sketchbookLibrariesFolder);
		List<DocumentFile> jars = new ArrayList<>();
		if (libraryJarFolder.exists()) {
			for (String filename : filenames) {
				MaybeDocumentFile jar = libraryJarFolder.child(filename + ".jar", "application/java-archive");
				if (jar.exists()) {
					jars.add(jar.resolve());
				}
			}
		}
		return jars;
	}
	
	//NOTE: Dexed library JARs are created once and stored in the library folder so that we don't have to re-dex them every time we build
	
	public MaybeDocumentFile getLibraryJarDexFolder(MaybeDocumentFile sketchbookLibrariesFolder) throws MaybeDocumentFile.MaybeDocumentFileException {
		return getLibraryFolder(sketchbookLibrariesFolder).childDirectory("library-dex");
	}
	
	/**
	 * @param sketchbookLibrariesFolder
	 * @return the dexed versions of the library's JAR files
	 */
	public List<MaybeDocumentFile> getLibraryDexJars(MaybeDocumentFile sketchbookLibrariesFolder) throws MaybeDocumentFile.MaybeDocumentFileException {
		List<String> filenames = getLibraryJarNames(sketchbookLibrariesFolder);
		MaybeDocumentFile libraryJarDexFolder = getLibraryJarDexFolder(sketchbookLibrariesFolder);
		List<MaybeDocumentFile> dexJars = new ArrayList<>();
		if (libraryJarDexFolder.exists()) {
			for (String filename : filenames) {
				dexJars.add(libraryJarDexFolder.child(filename + "-dex.jar", "application/java-archive"));
			}
		}
		return dexJars;
	}
	
	/**
	 * @param sketchbookLibrariesFolder
	 * @return the JARs found in the "library" subfolder, without the ".jar" suffix
	 */
	private List<String> getLibraryJarNames(MaybeDocumentFile sketchbookLibrariesFolder) throws MaybeDocumentFile.MaybeDocumentFileException {
		// The files in the "library" and "library-dex" folders should match up
		// The dexed versions don't exist when we're installing the library, so we'll read the regular ones
		
		MaybeDocumentFile libraryJarFolder = getLibraryJarFolder(sketchbookLibrariesFolder);
		List<String> filenames = new ArrayList<>();
		if (libraryJarFolder.exists()) {
			for (DocumentFile file : libraryJarFolder.resolve().listFiles()) {
				if (file.isFile() && file.getName() != null && file.getName().endsWith(".jar")) {
					// trim the .jar suffix
					filenames.add(file.getName().substring(0, file.getName().length() - 4));
				}
			}
		}
		return filenames;
	}
	
	/**
	 * @param sketchbookLibrariesFolder
	 * @return the library's examples folder
	 */
	public MaybeDocumentFile getExamplesFolder(MaybeDocumentFile sketchbookLibrariesFolder) throws MaybeDocumentFile.MaybeDocumentFileException {
		return getLibraryFolder(sketchbookLibrariesFolder).childDirectory("examples");
	}
	
	public List<DocumentFile> getAndroidExports(MaybeDocumentFile sketchbookLibrariesFolder) throws MaybeDocumentFile.MaybeDocumentFileException {
		List<DocumentFile> exports = new ArrayList<>();
		exports.addAll(getLibraryJars(sketchbookLibrariesFolder));
		for (MaybeDocumentFile dexJar : getLibraryDexJars(sketchbookLibrariesFolder)) {
			exports.add(dexJar.resolve());
		}
		return exports;
	}
	
	private static boolean isJunkFile(String name) {
		return name.startsWith(".") || name.equals("CVS");
	}
	
	public static List<Library> list(DocumentFile folder, APDE context) {
		ArrayList<Library> libraries = new ArrayList<>();
		if (folder.exists() && folder.isDirectory()) {
			DocumentFile[] potentialLibraries = folder.listFiles();
			for (DocumentFile potentialLibrary : potentialLibraries) {
				if (isLibraryFolder(potentialLibrary, context)) {
					libraries.add(new Library(potentialLibrary.getName(), context));
				}
			}
		}
		return libraries;
	}
	
	public static boolean isLibraryFolder(DocumentFile potentialLibrary, Context context) {
		if (potentialLibrary == null || potentialLibrary.getName() == null
				|| isJunkFile(potentialLibrary.getName()) || !potentialLibrary.isDirectory()) {
			return false;
		}
		DocumentFile libraryFolder = potentialLibrary.findFile("library");
		if (libraryFolder != null && libraryFolder.isDirectory()) {
			DocumentFile libraryJar = libraryFolder.findFile(potentialLibrary.getName() + ".jar");
			if (libraryJar != null && libraryJar.exists() && libraryJar.isFile()) {
				String sanityCheck = processing.app.Sketch.sanitizeName(potentialLibrary.getName());
				if (sanityCheck.equals(potentialLibrary.getName())) {
					return true;
				} else {
					System.err.println(String.format(Locale.US,
							context.getResources().getString(R.string.library_add_sanity_check_mess_message),
							potentialLibrary.getName()));
				}
			}
		}
		return false;
	}
	
	public List<String> getPackageList(APDE context) throws MaybeDocumentFile.MaybeDocumentFileException {
		return new ArrayList<>(packageListFromDocumentFiles(getLibraryJars(context.getLibrariesFolder()), context));
	}
	
	// Add this library's packages to the master list
	public void addPackageList(Map<String, List<Library>> importToLibraryTable, APDE context) throws MaybeDocumentFile.MaybeDocumentFileException {
		List<String> packageList = getPackageList(context);
		
		for (String pkg : packageList) {
			List<Library> libraries = importToLibraryTable.get(pkg);
			if (libraries == null) {
				libraries = new ArrayList<>();
				importToLibraryTable.put(pkg, libraries);
			} else {
				StringBuilder libraryConflicts = new StringBuilder();
				for (Library library : libraries) {
					libraryConflicts.append(library.getName());
				}
				
				System.err.println(String.format(Locale.US,
						context.getResources().getString(R.string.library_package_name_conflict),
						getName(), libraryConflicts.toString(), pkg));
			}
			libraries.add(this);
		}
	}
	
	// This value *should* be different for every library - yes, libraries can have the same name... but it looks like we aren't worrying about that right now
	// Seeing as we might not be connected to the internet, we can't rely on the nice table that the Processing devs compiled for us (which has separate IDs for each library)...
	// ...additionally, that wouldn't work with custom-built libraries
	// Ideally, we'd have some kind checksum... but that's too much work for right now
	// So we're just using library names
	// ...
	// Besides, the libraries of the same name would conflict because they'd both try to use the same folder in the libraries folder anyway
	public String consumableValue() {
		return getName();
	}
	
	public static Set<String> packageListFromDocumentFiles(List<DocumentFile> files, Context context) {
		Set<String> packages = new HashSet<>();
		for (DocumentFile file : files) {
			if (file == null || file.getName() == null) {
				return Collections.emptySet();
			}
			String lowercasedName = file.getName().toLowerCase(Locale.US);
			if (lowercasedName.endsWith(".jar") || lowercasedName.endsWith(".zip")) {
				packages.addAll(packageListFromZip(file, context));
			} else if (file.isDirectory()) {
				packages.addAll(packageListFromFolder(file, context, ""));
			}
		}
		return packages;
	}
	
	private static Set<String> packageListFromZip(DocumentFile file, Context context) {
		Set<String> packages = new HashSet<>();
		try (ZipInputStream input = new ZipInputStream(new BufferedInputStream(
				context.getContentResolver().openInputStream(file.getUri())))) {
			ZipEntry entry;
			while ((entry = input.getNextEntry()) != null) {
				if (!entry.isDirectory()) {
					String name = entry.getName();
					if (name.endsWith(".class")) {
						int slash = name.lastIndexOf('/');
						if (slash != -1) {
							String packageName = name.substring(0, slash);
							packages.add(packageName.replace('/', '.'));
						}
					}
				}
			}
		} catch (IOException e) {
			System.err.println(String.format(Locale.US,
					context.getResources().getString(R.string.build_package_from_zip_ignoring),
					file.getName(), e.getMessage()));
		}
		return packages;
	}
	
	private static Set<String> packageListFromFolder(DocumentFile folder, Context context, String sofar) {
		Set<String> packages = new HashSet<>();
		for (DocumentFile file : folder.listFiles()) {
			if (file.getName() != null) {
				if (file.isDirectory()) {
					packages.addAll(packageListFromFolder(file, context, sofar + "." + file.getName()));
				} else if (sofar.length() > 0) {
					packages.add(sofar);
				}
			}
		}
		return packages;
	}
}