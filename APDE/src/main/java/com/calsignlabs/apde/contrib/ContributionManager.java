package com.calsignlabs.apde.contrib;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.webkit.MimeTypeMap;

import androidx.annotation.Nullable;
import com.calsignlabs.apde.support.documentfile.DocumentFile;

import com.calsignlabs.apde.APDE;
import com.calsignlabs.apde.R;
import com.calsignlabs.apde.build.Build;
import com.calsignlabs.apde.build.StaticBuildResources;
import com.calsignlabs.apde.support.FileSelection;
import com.calsignlabs.apde.support.MaybeDocumentFile;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;
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
	public static boolean installDirLibrary(Library library, DocumentFile libraryDir, Handler handler, APDE context) {
		try {
			library.setStatus(Library.Status.COPYING);
			handler.sendMessage(Message.obtain(handler, LIBRARY_UPDATE, library.getStatus()));
			
			// Copy to the libraries folder
			APDE.copyDocumentFile(libraryDir, library.getLibraryFolder(context.getLibrariesFolder()), context.getContentResolver());
			
			library.setStatus(Library.Status.DEXING);
			handler.sendMessage(Message.obtain(handler, LIBRARY_UPDATE, library.getStatus()));
			
			// We dex during the install to save build time
			
			// Make sure that we have a dexed library directory
			library.getLibraryJarDexFolder(context.getLibrariesFolder()).resolve();
			
			performInstallDex(library, context);
			
			library.setStatus(Library.Status.INSTALLED);
			handler.sendMessage(Message.obtain(handler, LIBRARY_UPDATE, library.getStatus()));
			
			return true;
		} catch (MaybeDocumentFile.MaybeDocumentFileException | IOException e) {
			e.printStackTrace();
			return false;
		}
	}
	
	private static void performInstallDex(Library library, APDE context) throws FileNotFoundException, MaybeDocumentFile.MaybeDocumentFileException {
		List<DocumentFile> jars = library.getLibraryJars(context.getLibrariesFolder());
		List<MaybeDocumentFile> dexJars = library.getLibraryDexJars(context.getLibrariesFolder());
		
		assert jars.size() == dexJars.size();
		
		// Dex all of the files...
		for (int i = 0; i < jars.size(); i++) {
			dexJar(jars.get(i), dexJars.get(i), context);
		}
	}
	
	/**
	 * Installs a library from a ZIP archive
	 * 
	 * @param libraryZip
	 * @param context
	 */
	public static boolean installZipLibrary(Library library, Uri libraryZip, Handler handler, APDE context) {
		try {
			library.setStatus(Library.Status.EXTRACTING);
			handler.sendMessage(Message.obtain(handler, LIBRARY_UPDATE, library.getStatus()));
			
			ParcelFileDescriptor fd = FileSelection.openUri(context, libraryZip, FileSelection.Mode.READ);
			if (fd == null) {
				System.err.println(context.getResources().getString(R.string.install_zip_library_failure_unexpected_error));
				return false;
			}
			
			FileInputStream inputStream = new FileInputStream(fd.getFileDescriptor());
			
			try {
				// Extract to the libraries folder
				extractFile(inputStream, library.getLibraryFolder(context.getLibrariesFolder()), context, true);
				
				library.setStatus(Library.Status.DEXING);
				handler.sendMessage(Message.obtain(handler, LIBRARY_UPDATE, library.getStatus()));
				
				// We dex during the install to save build time
				
				// Make sure that we have a dexed library directory
				library.getLibraryJarDexFolder(context.getLibrariesFolder()).resolve();
				
				performInstallDex(library, context);
			} finally {
				FileSelection.closeFd(fd);
			}
			
			library.setStatus(Library.Status.INSTALLED);
			handler.sendMessage(Message.obtain(handler, LIBRARY_UPDATE, library.getStatus()));
			
			return true;
		} catch (MaybeDocumentFile.MaybeDocumentFileException | FileNotFoundException e) {
			e.printStackTrace();
			return false;
		}
	}
	
	/**
	 * Searches a zip file for the name of the library that it (hopefully) contains
	 * 
	 * @param uri
	 * @return the name of the zipped library
	 */
	@Nullable
	public static String detectLibraryName(Context context, Uri uri) {
		//Read the contents of the zip file, searching for the name of the library that it contains
		
		if (uri == null) {
			return null;
		}
		ParcelFileDescriptor fd = FileSelection.openUri(context, uri, FileSelection.Mode.READ, true);
		if (fd == null) {
			return null;
		}
		InputStream inputStream = new FileInputStream(fd.getFileDescriptor());
		
		ZipInputStream zis = null;
		
		try {
			String filename;
			zis = new ZipInputStream(new BufferedInputStream(inputStream));
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
					//...and read the properties
					Properties props = new Properties();
					props.load(zis);
					
					//Specifically, the name
					String libraryName = props.getProperty("name");
					
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
				fd.close();
			} catch (IOException e) {
				//...
				e.printStackTrace();
			}
		}
		
		//If all else fails, return the name of the zip file
		String zipName = FileSelection.uriToFilename(context, uri);
		if (zipName == null) {
			return null;
		}
		
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
	public static boolean extractFile(InputStream input, MaybeDocumentFile output, Context context, boolean skipFirstDirectory) throws MaybeDocumentFile.MaybeDocumentFileException {
		// Modified from StackOverflow:
		// http://stackoverflow.com/questions/3382996/how-to-unzip-files-programmatically-in-android
		
		ZipInputStream zis;
		
		try {
			String filename;
			zis = new ZipInputStream(new BufferedInputStream(input));
			ZipEntry ze;
			byte[] buffer = new byte[1024];
			int count;
			
			while ((ze = zis.getNextEntry()) != null) {
				filename = ze.getName();
				
				if (skipFirstDirectory) {
					int firstSlash = filename.indexOf('/');
					if (firstSlash == -1 || firstSlash == filename.length() - 1) {
						zis.closeEntry();
						continue;
					} else {
						filename = filename.substring(firstSlash + 1);
					}
				}
				
				if (isJunkFilename(filename)) {
					zis.closeEntry();
					continue;
				}
				
				// Need to create directories if they don't exist, or it will throw an Exception...
				if (ze.isDirectory()) {
					MaybeDocumentFile fmd = output.childPathDirectory(filename);
					fmd.resolve();
					zis.closeEntry();
					continue;
				}
				
				
				String mimeType = null;
				
				// infer mime type
				int index = filename.indexOf(".");
				if (index != -1) {
					String extension = filename.substring(index + 1).toLowerCase(Locale.US);
					mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
				}
				
				if (mimeType == null) {
					// default to a binary blob
					mimeType = "application/octet-stream";
				}
				OutputStream fout = output.childPath(filename, mimeType).openOut(context.getContentResolver());
				
				while ((count = zis.read(buffer)) != -1) {
					fout.write(buffer, 0, count);
				}
				
				fout.close();
				zis.closeEntry();
			}
			
			zis.close();
		} catch (IOException e) {
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
		// Is this zip entry worth extracting?
		// This deals with "__MACOSX" and ".DS_Store" files (cough, cough, Mac OSX), potentially others later on
		// Currently leaves some potentially annoying files (such as "INSTALL.txt" in oscP5), but removing these might have unintentional side effects
		
		// Get a list of each level in the hierarchy...
		// ...because if one part of the path is junk, everything beneath it is, too
		String[] segments = zipFilename.split("/");
		
		for (String segment : segments) {
			if (segment.startsWith("_") || segment.startsWith(".")) {
				return true;
			}
		}
			
		return false;
	}
	
	public static void dexJar(DocumentFile input, MaybeDocumentFile output, Context context) throws MaybeDocumentFile.MaybeDocumentFileException, FileNotFoundException {
		String tmpInName = input.getName();
		if (tmpInName == null) {
			tmpInName = "tmpDexIn.jar";
		}
		String tmpOutName = output.getName();
		if (tmpOutName == null) {
			tmpOutName = "tmpDexOut.jar";
		}
		
		File tmpIn = new File(context.getFilesDir(), tmpInName);
		File tmpOut = new File(context.getFilesDir(), tmpOutName);
		
		StaticBuildResources.createFileFromInputStream(new MaybeDocumentFile(input).openIn(context.getContentResolver()), tmpIn);
		
		dexJar(tmpIn, tmpOut, context);
		
		OutputStream os = output.openOut(context.getContentResolver());
		
		try (InputStream is = new FileInputStream(tmpOut)) {
			Build.copyStream(is, os);
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		try {
			os.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		tmpIn.delete();
		tmpOut.delete();
	}
	
	/**
	 * Dexes the input JAR file and saves it to the output
	 * This is just a wrapper function for the command line / Android native functionality
	 * 
	 * @param input
	 * @param output
	 */
	public static void dexJar(File input, File output, Context context) {
		try {
			String[] args = new String[] {
					"--output=" + output.getAbsolutePath(), //The location of the output DEXed file
					input.getAbsolutePath(), //The location of the file to DEXify
			};
			
			//This is some side-stepping to avoid System.exit() calls
			
			com.androidjarjar.dx.command.dexer.Main.Arguments dexArgs = new com.androidjarjar.dx.command.dexer.Main.Arguments();
			
			Method mtd = com.androidjarjar.dx.command.dexer.Main.Arguments.class.getDeclaredMethod("parse", String[].class);
			mtd.setAccessible(true);
			mtd.invoke(dexArgs, new Object[] {args});
			
			int resultCode = com.androidjarjar.dx.command.dexer.Main.run(dexArgs);
			
			if (resultCode != 0) {
				System.err.println(String.format(Locale.US, context.getResources().getString(R.string.dex_jar_failure_error_code), resultCode));
			}
		} catch (Exception e) {
			System.err.println(context.getResources().getString(R.string.dex_jar_failure));
			e.printStackTrace();
		}
	}
	
	/**
	 * Uninstalls the library by deleting its folder
	 * 
	 * @param library
	 */
	public static void uninstallLibrary(Library library, APDE context) {
		try {
			library.getLibraryFolder(context.getLibrariesFolder()).delete();
		} catch (MaybeDocumentFile.MaybeDocumentFileException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Recursive file deletion
	 * 
	 * @param f
	 */
	public static void deleteFile(File f, Context context) {
		if(f.isDirectory()) {
			for (File c : f.listFiles()) {
				deleteFile(c, context);
			}
		}
		
		//Renaming solution for the file system lock with EBUSY errors
		//StackOverflow: http://stackoverflow.com/questions/11539657/open-failed-ebusy-device-or-resource-busy
		final File to = new File(f.getAbsolutePath() + System.currentTimeMillis());
		f.renameTo(to);
		
		if (!to.delete()) {
			System.err.println(String.format(Locale.US, context.getResources().getString(R.string.delete_file_failure), f.getAbsolutePath()));
		}
	}
}