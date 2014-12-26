package com.calsignlabs.apde.contrib;

import android.os.Handler;
import android.os.Message;

import com.calsignlabs.apde.APDE;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

//This is a utility class for managing libraries (maybe other contributions later) - installing them, checking for updates (planned), etc.
public class ContributionManager {
	//This is a number - used as an ID for the handler updates
	public static final int LIBRARY_UPDATE = 22885;
	
	/**
	 * Installs a library from an existing directory
	 * 
	 * @param library
	 * @param libraryDir
	 * @param handler
	 * @param context
	 */
	public static boolean installDirLibrary(Library library, File libraryDir, Handler handler, APDE context) {
		library.setStatus(Library.Status.COPYING);
		handler.sendMessage(Message.obtain(handler, LIBRARY_UPDATE, library.getStatus()));
		
		//Copy to the libraries folder
		if (!copyFile(libraryDir, library.getLibraryFolder(context))) {
			System.err.println("Unexcepted error occurred while copying the library.");
			return false;
		}
		
		library.setStatus(Library.Status.DEXING);
		handler.sendMessage(Message.obtain(handler, LIBRARY_UPDATE, library.getStatus()));
		
		//We dex during the install to save build time
		
		//Make sure that we have a dexed library directory
		library.getLibraryJarDexFolder(context).mkdir();
		
		try {
			File[] jars = library.getLibraryJars(context);
			File[] dexJars = library.getLibraryDexJars(context);
			
			//Dex all of the files...
			for(int i = 0; i < jars.length; i ++) {
				dexJar(jars[i], dexJars[i]);
			}
		} catch (NullPointerException e) {
			//If we can't find the JARs
			System.err.println("Unable to locate the library JAR files at " + library.getLibraryJarFolder(context));
			System.err.println("Try organizing the folder structure according the Processing library formatting guidelines.");
			e.printStackTrace();
			return false;
		}
		
		library.setStatus(Library.Status.INSTALLED);
		handler.sendMessage(Message.obtain(handler, LIBRARY_UPDATE, library.getStatus()));
		
		return false;
	}
	
	/**
	 * Installs a library from a ZIP archive
	 * 
	 * @param libraryZip
	 * @param context
	 */
	public static boolean installZipLibrary(Library library, File libraryZip, Handler handler, APDE context) {
		library.setStatus(Library.Status.EXTRACTING);
		handler.sendMessage(Message.obtain(handler, LIBRARY_UPDATE, library.getStatus()));
		
		//Extract to the libraries folder
		if(!extractFile(libraryZip, library.getLibraryFolder(context))) {
			System.err.println("Unexcepted error occurred while extracting the library.");
			return false;
		}
		
		library.setStatus(Library.Status.DEXING);
		handler.sendMessage(Message.obtain(handler, LIBRARY_UPDATE, library.getStatus()));
		
		//We dex during the install to save build time
		
		//Make sure that we have a dexed library directory
		library.getLibraryJarDexFolder(context).mkdir();
		
		try {
			File[] jars = library.getLibraryJars(context);
			File[] dexJars = library.getLibraryDexJars(context);
			
			//Dex all of the files...
			for(int i = 0; i < jars.length; i ++) {
				dexJar(jars[i], dexJars[i]);
			}
		} catch (NullPointerException e) {
			//If we can't find the JARs
			System.err.println("Unable to locate the library JAR files at " + library.getLibraryJarFolder(context));
			System.err.println("Try organizing the folder structure within the ZIP file according the Processing library formatting guidelines.");
			e.printStackTrace();
			return false;
		}
		
		library.setStatus(Library.Status.INSTALLED);
		handler.sendMessage(Message.obtain(handler, LIBRARY_UPDATE, library.getStatus()));
		
		return true;
	}
	
	/**
	 * Searches a zip file for the name of the library that it (hopefully) contains
	 * 
	 * @param libraryZip
	 * @return the name of the zipped library
	 */
	public static String detectLibraryName(File libraryZip) {
		//Read the contents of the zip file, searching for the name of the library that it contains
		
		ZipInputStream zis = null;
		
		try {
			String filename;
			zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(libraryZip)));
			ZipEntry ze;
			
			//Just in case things don't quite work out
			String backupName = "";
			
			while((ze = zis.getNextEntry()) != null) {
				filename = ze.getName();
				
				if(isJunkFilename(filename)) {
					zis.closeEntry();
					
					continue;
				}
				
				//Assign the backup name to the first non-junk directory name (should be the library name, fingers crossed)
				if(backupName.equals("") && ze.isDirectory()) {
					//Trim the trailing slash from the directory name
					backupName = filename.substring(0, filename.length() - 1);
				}
				
				//Get the filename by itself, without the path
				int lastSlash = filename.lastIndexOf("/");
				
				//Look for the "library.properties" file...
				if((lastSlash >= 0 ? filename.substring(lastSlash, filename.length()) : filename).equals(Library.propertiesFilename)) {
					ZipFile zipFile = new ZipFile(libraryZip);
					
					//...and read the properties
					Properties props = new Properties();
					props.load(zipFile.getInputStream(ze));
					
					//Specifically, the name
					String libraryName = props.getProperty("name");
					
					zipFile.close();
					
					//But make sure that the properties file is formatted properly
					if(libraryName != null) {
						return libraryName;
					}
				}
				
				zis.closeEntry();
			}
			
			//If we've made it this far, then there must not be a properties file
			//Let's just make sure that there was a directory, at least...
			if(!backupName.equals("")) {
				//...and return its name
				return backupName;
			}
			
			zis.close();
		} catch(IOException e) {
			e.printStackTrace();
		} finally {
			try {
				if (zis != null) {
					zis.close();
				}
			} catch (IOException e) {
				//...
				e.printStackTrace();
			}
		}
		
		//If all else fails, return the name of the zip file
		String zipName = libraryZip.getName();
		
		//It had better end with ".zip", because it's a zip file... but you never know
		if(zipName.endsWith(".zip")) {
			zipName = zipName.substring(0, zipName.length() - 4);
		}
		
		return zipName;
	}
	
	/**
	 * Extracts the input archive and saves it to the output
	 * 
	 * @param input
	 * @param output
	 * @return
	 */
	public static boolean extractFile(File input, File output) {
		//Modified from:
		//StackOverflow: http://stackoverflow.com/questions/3382996/how-to-unzip-files-programmatically-in-android
		
		ZipInputStream zis;
		
		try {
			String filename;
			zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(input)));
			ZipEntry ze;
			byte[] buffer = new byte[1024];
			int count;
			
			String rootDir = output.getParentFile().getAbsolutePath() + "/";
			
			while((ze = zis.getNextEntry()) != null) {
				filename = ze.getName();
				
				if(isJunkFilename(filename)) {
					zis.closeEntry();
					
					continue;
				}
				
				//Need to create directories if they don't exist, or it will throw an Exception...
				if(ze.isDirectory()) {
					File fmd = new File(rootDir + filename);
					fmd.mkdirs();
					
					continue;
				}
				
				FileOutputStream fout = new FileOutputStream(rootDir + filename);
				
				while((count = zis.read(buffer)) != -1) {
					fout.write(buffer, 0, count);
				}
				
				fout.close();
				zis.closeEntry();
			}
			
			zis.close();
		} catch(IOException e) {
			e.printStackTrace();
			return false;
		}
		
		return true;
	}
	
	private static boolean copyFile(File input, File output) {
		try {
			APDE.copyFile(input, output);
		} catch (Exception e) {
			e.printStackTrace();
			
			return false;
		}
		
		return true;
	}
	
	private static boolean isJunkFilename(String zipFilename) {
		//Is this zip entry worth extracting?
		//This deals with "__MACOSX" and ".DS_Store" files (cough, cough, Mac OSX), potentially others later on
		//Currently leaves some potentially annoying files (such as "INSTALL.txt" in oscP5), but removing these might have unintentional side effects
		
		//Get a list of each level in the hierarchy...
		//...because if one part of the path is junk, everything beneath it is, too
		String[] files = zipFilename.split("/");
		
		for(String file : files) {
			//The perpretrators
			if(file.startsWith("_") || file.startsWith(".")) {
				return true;
			}
		}
			
		return false;
	}
	
	/**
	 * Dexes the input JAR file and saves it to the output
	 * This is just a wrapper function for the command line / Android native functionality
	 * 
	 * @param input
	 * @param output
	 */
	public static void dexJar(File input, File output) {
		try {
			String[] args = new String[] {
					"--output=" + output.getAbsolutePath(), //The location of the output DEXed file
					input.getAbsolutePath(), //The location of the file to DEXify
			};
			
			//This is some side-stepping to avoid System.exit() calls
			
			com.android.dx.command.dexer.Main.Arguments dexArgs = new com.android.dx.command.dexer.Main.Arguments();
			dexArgs.parse(args);
			
			int resultCode = com.android.dx.command.dexer.Main.run(dexArgs);
			
			if (resultCode != 0) {
				System.err.println("DX Dexer failed, error code: " + resultCode);
			}
		} catch (Exception e) {
			System.err.println("DX Dexer failed");
			e.printStackTrace();
		}
	}
	
	/**
	 * Uninstalls the library by deleting its folder
	 * 
	 * @param library
	 */
	public static void uninstallLibrary(Library library, APDE context) {
		deleteFile(library.getLibraryFolder(context));
	}
	
	/**
	 * Recursive file deletion
	 * 
	 * @param f
	 */
	public static void deleteFile(File f) {
		if(f.isDirectory())
			for(File c : f.listFiles())
				deleteFile(c);
		
		//Renaming solution for the file system lock with EBUSY errors
		//StackOverflow: http://stackoverflow.com/questions/11539657/open-failed-ebusy-device-or-resource-busy
		final File to = new File(f.getAbsolutePath() + System.currentTimeMillis());
		f.renameTo(to);
		
		if(!to.delete())
			System.err.println("Failed to delete file: " + f);
	}
}