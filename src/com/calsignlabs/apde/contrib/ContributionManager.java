package com.calsignlabs.apde.contrib;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import android.os.Handler;
import android.os.Message;

import com.calsignlabs.apde.APDE;

//This is a utility class for managing libraries (maybe other contributions later) - installing them, checking for updates (planned), etc.
public class ContributionManager {
	//This is a number - used as an ID for the handler updates
	public static final int LIBRARY_UPDATE = 22885;
	
	/**
	 * Installs a library from a ZIP archive
	 * 
	 * @param libraryZip
	 * @param context
	 */
	public static Library installLibrary(Library library, File libraryZip, Handler handler, APDE context) {
		library.setStatus(Library.Status.EXTRACTING);
		handler.sendMessage(Message.obtain(handler, LIBRARY_UPDATE, library.getStatus()));
		
		//Extract to the libraries folder
		extractFile(libraryZip, library.getLibraryFolder(context));
		
		library.setStatus(Library.Status.DEXING);
		handler.sendMessage(Message.obtain(handler, LIBRARY_UPDATE, library.getStatus()));
		
		File libDexJar = library.getLibraryDexJar(context);
		//Make sure that we have a dexed library directory
		libDexJar.getParentFile().mkdir();
		
		//We dex during the install to save build time
		dexJar(library.getLibraryJar(context), libDexJar);
		
		library.setStatus(Library.Status.INSTALLED);
		handler.sendMessage(Message.obtain(handler, LIBRARY_UPDATE, library.getStatus()));
		
		return library;
	}
	
	/**
	 * Extracts the input archive and saves it to the output
	 * 
	 * @param input
	 * @param output
	 * @return
	 */
	public static boolean extractFile(File input, File output) {
		//StackOverflow: http://stackoverflow.com/questions/3382996/how-to-unzip-files-programmatically-in-android
		
		InputStream is;
		ZipInputStream zis;
		
		try {
			String filename;
			is = new FileInputStream(input);
			zis = new ZipInputStream(new BufferedInputStream(is));
			ZipEntry ze;
			byte[] buffer = new byte[1024];
			int count;
			
			String rootDir = output.getParentFile().getAbsolutePath() + "/";
			
			while((ze = zis.getNextEntry()) != null) {
				filename = ze.getName();
				
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
	
	/**
	 * Dexes the input JAR file and saves it to the output
	 * This is just a wrapper function for the command line / Android native functionality
	 * 
	 * @param input
	 * @param output
	 */
	public static void dexJar(File input, File output) {
		String[] args = new String[] {
				"--dex",
				"--output=" + output.getAbsolutePath(), //The location of the output DEXed file
				input.getAbsolutePath(), //The location of the file to DEXify
		};
		
		com.android.dx.command.Main.main(args);
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