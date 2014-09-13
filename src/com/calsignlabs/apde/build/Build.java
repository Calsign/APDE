/*
 * Seriously hacked from the Processing Project... it might still be recognizable
 * Code taken from JavaBuild, AndroidBuild, Base, AndroidPreprocessor, Preprocessor, Mode, Library, probably others
 * 
 * Added some code as well, specifically: changed build sequence from ANT to ECJ and Java tools (as opposed to command line tools)
 * Also used some ideas from the Java-IDE-Droid open-source project
 */

package com.calsignlabs.apde.build;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.security.Security;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import kellinwood.security.zipsigner.ZipSigner;
import kellinwood.security.zipsigner.optional.CustomKeySigner;

import org.eclipse.jdt.internal.compiler.batch.Main;
import org.spongycastle.jce.provider.BouncyCastleProvider;

import processing.app.Preferences;
import processing.core.PApplet;
import processing.mode.java.preproc.PdePreprocessor;
import processing.mode.java.preproc.PreprocessorResult;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetManager;
import android.net.Uri;
import android.os.Environment;
import android.preference.PreferenceManager;

import com.calsignlabs.apde.*;
import com.calsignlabs.apde.contrib.Library;

public class Build {
	public static final String PACKAGE_REGEX ="(?:^|\\s|;)package\\s+(\\S+)\\;";
	
	private EditorActivity editor;
	
	public String sketchName;
	private FileMeta[] tabs;
	
	private File buildFolder;
	private File srcFolder;
	private File genFolder;
	private File libsFolder;
	private File assetsFolder;
	private File binFolder;
	private File tmpFolder;
	private File dexedLibsFolder;
	
	private ArrayList<Library> importedLibraries;
	
	protected String classPath;
	protected String javaLibraryPath;
	
	protected boolean foundMain;
	
	private static final String ICON_96 = "icon-96.png";
	private static final String ICON_72 = "icon-72.png";
	private static final String ICON_48 = "icon-48.png";
	private static final String ICON_36 = "icon-36.png";
	
	private static AtomicBoolean running;
	
	private String keystore;
	private char[] keystorePassword;
	private String keyAlias;
	private char[] keyAliasPassword;
	
	private boolean injectLogBroadcaster;
	
	public Build(APDE global) {
		this.editor = global.getEditor();
		
		sketchName = global.getSketchName();
		tabs = editor.getTabMetas();
		
		running = new AtomicBoolean(true);
		
		injectLogBroadcaster = PreferenceManager.getDefaultSharedPreferences(global).getBoolean("inject_log_broadcaster", true);
	}
	
	public void setKey(String keystore, char[] keystorePassword, String keyAlias, char[] keyAliasPassword) {
		this.keystore = keystore;
		this.keystorePassword = keystorePassword;
		this.keyAlias = keyAlias;
		this.keyAliasPassword = keyAliasPassword;
	}
	
	/**
	 * Stops the build process, after finishing the current step in the sequence
	 */
	public static void halt() {
		if(!running.compareAndSet(true, false)) {
			//Something went wrong...
			//...but it doesn't matter because this is what we want, anyway
		}
	}
	
	private void cleanUpError() {
		cleanUp();
		editor.errorExt(editor.getResources().getString(R.string.build_failed));
	}
	
	private void cleanUpHalt() {
		cleanUp();
		editor.messageExt(editor.getResources().getString(R.string.build_halted));
	}
	
	private void cleanUp() {
		//Anything we need to clean up now...
	}
	
	public static void cleanUpPostLaunch(EditorActivity editor) {
		if(PreferenceManager.getDefaultSharedPreferences(editor).getBoolean("pref_build_discard", true)) {
			System.out.println("Deleting build folder...");
			
			//Delete the build folder
			deleteFile((new Build(((APDE) editor.getApplicationContext())).getBuildFolder()));
		}
		
		if(PreferenceManager.getDefaultSharedPreferences(editor).getBoolean("pref_build_internal_storage", true)) {
			//If we built on the internal storage, we need to delete the copied APK file
			//This is so that the package installer can still see it
			
			File destApkFile = new File(editor.getFilesDir(), ((APDE) editor.getApplicationContext()).getSketchName() + ".apk");
			System.out.println(destApkFile.delete() ? "Successfully deleted old APK file" : "Failed to delete old APK file");
		}
	}
	
	//Recursive file deletion
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
	
    public void exportAndroidEclipseProject(File dest, String target) {
    	editor.messageExt(editor.getResources().getString(R.string.build_sketch_message));
		System.out.println("Initializing build sequence...");
    	
    	buildFolder = dest;
		srcFolder = new File(buildFolder, "src");
		genFolder = new File(buildFolder, "gen");
		libsFolder = new File(buildFolder, "libs");
		assetsFolder = new File(buildFolder, "assets");
		binFolder = new File(buildFolder, "bin");
		
		File buildFile = new File(buildFolder, "build.xml");
		
		//Wipe the old export folder
		if(buildFolder.exists()) {
			System.out.println("Deleting old export folder...");
			deleteFile(buildFolder);
			System.out.println("Successfully deleted old export folder");
		}
		
		buildFolder.mkdir();
		srcFolder.mkdir();
		libsFolder.mkdir();
		assetsFolder.mkdir();
		binFolder.mkdir();
		
		//Make sure we have the latest version of the libraries folder
		((APDE) editor.getApplicationContext()).rebuildLibraryList();
		
		Manifest manifest = null;
		String sketchClassName = null;
		
		editor.messageExt(editor.getResources().getString(R.string.gen_project_message));
		
		try {
			manifest = new Manifest(this);
			
			Preferences.setInteger("editor.tabs.size", 2); //TODO this is the default... so a tab adds two spaces
			
			//Enable all of the fancy preprocessor stuff
			Preferences.setBoolean("preproc.enhanced_casting", true);
			Preferences.setBoolean("preproc.web_colors", true);
			Preferences.setBoolean("preproc.color_datatype", true);
			Preferences.setBoolean("preproc.substitute_floats", true);
			Preferences.setBoolean("preproc.substitute_unicode", true);
			
			Preproc preproc = new Preproc(sketchName, manifest.getPackageName());
			
			//Combine all of the tabs to check for size
			String combinedText = "";
			for(FileMeta tab : tabs)
				combinedText += tab.getText();
			preproc.initSketchSize(combinedText, editor);
			sketchClassName = preprocess(srcFolder, manifest.getPackageName(), preproc, false);
			
			if(sketchClassName != null) {
				File tempManifest = new File(buildFolder, "AndroidManifest.xml");
				manifest.writeBuild(tempManifest, sketchClassName, target.equals("debug"));
				
				writeAntProps(new File(buildFolder, "ant.properties"), manifest.getPackageName());
				writeBuildXML(buildFile, sketchName);
				writeProjectProps(new File(buildFolder, "project.properties"), Integer.toString(manifest.getTargetSdk(editor)));
//				writeLocalProps(new File(buildFolder, "local.properties"));
				
				final File resFolder = new File(buildFolder, "res");
				writeRes(resFolder, sketchClassName);
				
//				final File libsFolder = mkdirs(buildFolder, "libs");
//				final File assetsFolder = mkdirs(buildFolder, "assets");
				
				AssetManager am = editor.getAssets();
				
				//Copy native libraries
				
				String[] libsToCopy = {"processing-core"};
				String prefix = "libs/";
				String suffix = ".jar";
				
				//Copy for the compiler
				for(String lib : libsToCopy) {
					InputStream inputStream = am.open(prefix + lib + suffix);
					createFileFromInputStream(inputStream, new File(libsFolder, lib + suffix));
					inputStream.close();
				}
				
				// Copy any imported libraries (their libs and assets),
				// and anything in the code folder contents to the project.
				copyLibraries(libsFolder, assetsFolder);
				copyCodeFolder(libsFolder);
				
				// Copy the data folder (if one exists) to the project's 'assets' folder
				final File sketchDataFolder = getSketchDataFolder();
				if(sketchDataFolder.exists())
					copyDir(sketchDataFolder, assetsFolder);
				
				// Do the same for the 'res' folder.
				// http://code.google.com/p/processing/issues/detail?id=767
				final File sketchResFolder = new File(getSketchFolder(), "res");
				if(sketchResFolder.exists())
					copyDir(sketchResFolder, resFolder);
			}
		} catch (IOException e) {
			e.printStackTrace();
		} catch (SketchException e) {
			e.printStackTrace();
			
			editor.errorExt(e.getMessage());
			editor.highlightLineExt(e.getCodeIndex(), e.getCodeLine());
			
			//Bail out
			cleanUp();
			return;
		} catch (RuntimeException e) {
			e.printStackTrace();
			
			editor.errorExt(e.getMessage());
			
			//Bail out
			cleanUp();
			return;
		}
		
		System.out.println("Exported to " + dest.getAbsolutePath());
		editor.messageExt(editor.getResources().getString(R.string.export_eclipse_project_complete));
    }
    
	/**
	 * @param target either "release" or "debug"
	 */
	@SuppressLint("WorldReadableFiles")
	@SuppressWarnings("deprecation")
	public void build(String target) {
		boolean debug = target.equals("debug");
		
		running.set(true);
		
		//Throughout this function, perform periodic checks to see if the user has cancelled the build
		
		editor.messageExt(editor.getResources().getString(R.string.build_sketch_message));
		System.out.println("Initializing build sequence...");
		
		if(!running.get()) { //CHECK
			cleanUpHalt();
			return;
		}
		
		buildFolder = getBuildFolder();
		srcFolder = new File(buildFolder, "src");
		genFolder = new File(buildFolder, "gen");
		libsFolder = new File(buildFolder, "libs");
		assetsFolder = new File(buildFolder, "assets");
		binFolder = new File(buildFolder, "bin");
		dexedLibsFolder = new File(binFolder, "dexedLibs");
		
		tmpFolder = getTempFolder();
		
//		buildFile = new File(buildFolder, "build.xml");
		
		//Wipe the old build folder
		if(buildFolder.exists()) {
			System.out.println("Deleting old build folder...");
			deleteFile(buildFolder);
			System.out.println("Successfully deleted old build folder");
		}
		
		buildFolder.mkdir();
		srcFolder.mkdir();
		libsFolder.mkdir();
		assetsFolder.mkdir();
		binFolder.mkdir();
		dexedLibsFolder.mkdir();
		
		tmpFolder.mkdir();
		
		//Make sure we have the latest version of the libraries folder
		((APDE) editor.getApplicationContext()).rebuildLibraryList();
		
		Manifest manifest = null;
		String sketchClassName = null;
		
		editor.messageExt(editor.getResources().getString(R.string.gen_project_message));
		
		if(!running.get()) { //CHECK
			cleanUpHalt();
			return;
		}
		
		//Used to determine whether or not to build with ALL of the OpenGL libraries...
		//...it takes a lot longer to run DEX if they're included
//		boolean isOpenGL = false;
		//File glLibLoc = new File(binFolder, "libs.dex");
		
		File androidJarLoc = new File(tmpFolder, "android.jar");
		
		try {
			manifest = new Manifest(this);
			
			String packageName = manifest.getPackageName();
			
			if(!running.get()) { //CHECK
				cleanUpHalt();
				return;
			}
			
			Preferences.setInteger("editor.tabs.size", 2); //TODO this is the default... so a tab adds two spaces
			
			//Enable all of the fancy preprocessor stuff
			Preferences.setBoolean("preproc.enhanced_casting", true);
			Preferences.setBoolean("preproc.web_colors", true);
			Preferences.setBoolean("preproc.color_datatype", true);
			Preferences.setBoolean("preproc.substitute_floats", true);
			Preferences.setBoolean("preproc.substitute_unicode", true);
			
			Preproc preproc = new Preproc(sketchName, packageName);
			
			//Combine all of the tabs to check for size
			String combinedText = "";
			for(FileMeta tab : tabs)
				combinedText += tab.getText();
			preproc.initSketchSize(combinedText, editor);
			sketchClassName = preprocess(srcFolder, packageName, preproc, false);
			
			//Detect if the renderer is one of the OpenGL renderers
			//XTODO support custom renderers that require OpenGL or... other problems that may arise
//			String sketchRenderer = preproc.getSketchRenderer();
//			if(sketchRenderer != null)
//				isOpenGL = sketchRenderer.equals("OPENGL") || sketchRenderer.equals("P3D") || sketchRenderer.equals("P2D");
//			else
//				isOpenGL = false;
			
			//This OpenGL-checking isn't really useful anymore...
			
//			if(isOpenGL)
//				System.out.println("Detected renderer " + sketchRenderer + "; including OpenGL libraries");
//			else
//				System.out.println("Detected renderer " + sketchRenderer + "; leaving out OpenGL libraries");
			
			if(!running.get()) { //CHECK
				cleanUpHalt();
				return;
			}
			
			if(sketchClassName != null) {
				File tempManifest = new File(buildFolder, "AndroidManifest.xml");
				manifest.writeBuild(tempManifest, sketchClassName, debug);
				
				if(!running.get()) { //CHECK
					cleanUpHalt();
					return;
				}
				
				//writeAntProps(new File(buildFolder, "ant.properties"), manifest.getPackageName());
				//writeBuildXML(buildFile, sketchName);
				//writeProjectProps(new File(buildFolder, "project.properties"), Manifest.MIN_SDK);
				//writeLocalProps(new File(buildFolder, "local.properties"));
				
				final File resFolder = new File(buildFolder, "res");
				writeRes(resFolder, sketchClassName);
				
				final File libsFolder = mkdirs(buildFolder, "libs");
				final File assetsFolder = mkdirs(buildFolder, "assets");
				
				AssetManager am = editor.getAssets();
				
				//Copy android.jar if it hasn't been done yet
				if(!androidJarLoc.exists()) {
					InputStream is = am.open("android.jar");
					createFileFromInputStream(is, androidJarLoc);
					is.close();
				}
				
				//Copy native libraries
				
				String[] libsToCopy = {"processing-core"};//, "jogl-all", "gluegen-rt", "jogl-all-natives", "gluegen-rt-natives"};
				String prefix = "libs/";
				String suffix = ".jar";
				
				//Copy for the compiler
				for(String lib : libsToCopy) {
					InputStream inputStream = am.open(prefix + lib + suffix);
					createFileFromInputStream(inputStream, new File(libsFolder, lib + suffix));
					inputStream.close();
				}
				
				//Copy dexed versions to speed up the DEX process
				
//				if(isOpenGL) {
//					//For OpenGL sketches (includes P2D and P3D)
////					InputStream inputStream = am.open("libs-dex/" + "libs.dex");
////					createFileFromInputStream(inputStream, glLibLoc);
//					InputStream inputStream = am.open("libs-dex/" + "libs.jar");
//					createFileFromInputStream(inputStream, new File(dexedLibsFolder, "libs.jar"));
//				} else {
//					//For non-OpenGL sketches
//					InputStream inputStream = am.open("libs-dex/" + "processing-core-dex.jar");
//					createFileFromInputStream(inputStream, new File(dexedLibsFolder, "processing-core-dex.jar"));
//				}
				
				String[] dexLibsToCopy = {"processing-core-dex", "annotations-dex"};//, "jogl-all", "gluegen-rt", "jogl-all-natives", "gluegen-rt-natives"};
				String dexPrefix = "libs-dex/";
				String dexSuffix = ".jar";
				
//				InputStream inputStream = am.open("libs-dex/" + "processing-core-dex.jar");
//				createFileFromInputStream(inputStream, new File(dexedLibsFolder, "processing-core-dex.jar"));
				
				//Copy for the dexer
				for(String lib : dexLibsToCopy) {
					InputStream inputStream = am.open(dexPrefix + lib + dexSuffix);
					createFileFromInputStream(inputStream, new File(dexedLibsFolder, lib + dexSuffix));
					inputStream.close();
				}
				
				// Copy any imported libraries (their libs and assets),
				// and anything in the code folder contents to the project.
				copyLibraries(libsFolder, dexedLibsFolder, assetsFolder);
				copyCodeFolder(libsFolder);
				
				// Copy the dexed JARs from the code-dex folder
				copyCodeDexFolder(dexedLibsFolder);
				
				// Copy the data folder (if one exists) to the project's 'assets' folder
				final File sketchDataFolder = getSketchDataFolder();
				if(sketchDataFolder.exists())
					copyDir(sketchDataFolder, assetsFolder);
				
				// Do the same for the 'res' folder.
				// http://code.google.com/p/processing/issues/detail?id=767
				final File sketchResFolder = new File(getSketchFolder(), "res");
				if(sketchResFolder.exists())
					copyDir(sketchResFolder, resFolder);
				
				if(!running.get()) { //CHECK
					cleanUpHalt();
					return;
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		} catch (SketchException e) {
			e.printStackTrace();
			
			editor.errorExt(e.getMessage());
			editor.highlightLineExt(e.getCodeIndex(), e.getCodeLine());
			
			//Bail out
			cleanUp();
			return;
		} catch (RuntimeException e) {
			e.printStackTrace();
			
			editor.errorExt(e.getMessage());
			
			//Bail out
			cleanUp();
			return;
		}
		
		System.out.println("Detected architecture " + android.os.Build.CPU_ABI);
		
		String arch = android.os.Build.CPU_ABI.substring(0, 3).toLowerCase(Locale.US);
		String aaptName;
		
		if (arch.equals("x86")) {
			// x86
			aaptName = "aapt-x86";
		} else if (arch.equals("mip")) {
			// MIPS
			aaptName = "aapt-mips";
		} else {
			// ARM or its variants
			// Also, default to arm just in case...
			aaptName = "aapt";
		}
		
		File aaptLoc = new File(tmpFolder, "aapt"); //Use the same name for the destination so that the hyphens aren't an issue
		
		//AAPT setup
		try {
			AssetManager am = editor.getAssets();
			
			InputStream inputStream = am.open(aaptName);
			createFileFromInputStream(inputStream, aaptLoc);
			inputStream.close();
			
			//Run "chmod" on aapt so that we can execute it
			String[] chmod = {"chmod", "744", aaptLoc.getAbsolutePath()};
			Runtime.getRuntime().exec(chmod);
		} catch (IOException e) {
			System.out.println("Unable to make AAPT executable");
			e.printStackTrace();
		}
		
		if(!running.get()) { //CHECK
			cleanUpHalt();
			return;
		}
		
		//Let's try a different method - who needs ANT, anyway?
		
//		String androidVersion = "android-10";
		String mainActivityLoc = manifest.getPackageName().replace(".", "/");
		
		Process aaptProc = null;
		
		// NOTE: make sure that all places where build folders are specfied
		// (e.g. "buildFolder") it is followed by ".getAbsolutePath()"!!!!!
		
		editor.messageExt(editor.getResources().getString(R.string.run_aapt));
		
		//Copy GLSL shader files
		
		//GLSL files need to be placed in the root of the APK file
		File glslFolder = new File(binFolder, "processing.zip");
		
		try {
			//Copy the zip archive
			InputStream inputStream = editor.getAssets().open("glsl/processing.zip");
			createFileFromInputStream(inputStream, glslFolder);
		} catch(IOException e) { //Uh-oh...
			System.out.println("Failed to copy GLSL resources");
			e.printStackTrace();
			
			cleanUpError();
			return;
		}
		
		if(!running.get()) { //CHECK
			cleanUpHalt();
			return;
		}
		
		//Run AAPT
		try {
			System.out.println("Running AAPT...");
			
			//Create folder structure for R.java TODO why is this necessary?
			(new File(genFolder.getAbsolutePath() + "/" + mainActivityLoc + "/")).mkdirs();
			
			String[] args = {
				aaptLoc.getAbsolutePath(), //The location of AAPT
				"package", "-v", "-f", "-m",
				"-S", buildFolder.getAbsolutePath() + "/res/", //The location of the /res folder
				"-J", genFolder.getAbsolutePath(), //The location of the /gen folder
				"-A", assetsFolder.getAbsolutePath(), //The location of the /assets folder
				"-M", buildFolder.getAbsolutePath() + "/AndroidManifest.xml", //The location of the AndroidManifest.xml file
				"-I", androidJarLoc.getAbsolutePath(), //The location of the android.jar resource
				"-F", binFolder.getAbsolutePath() + "/" + sketchName + ".apk.res" //The location of the output .apk.res file
			};
			
			aaptProc = Runtime.getRuntime().exec(args);
			
			System.out.println("Ran AAPT successfully");
		} catch (IOException e) {
			//Something weird happened
			System.out.println("AAPT failed");
			e.printStackTrace();
			
			cleanUpError();
			return;
		}
		
		if(!running.get()) { //CHECK
			cleanUpHalt();
			return;
		}
		
		editor.messageExt(editor.getResources().getString(R.string.run_ecj));
		
		//Run ECJ
		{
			System.out.println("Running ECJ...");
			
			Main main = new Main(new PrintWriter(System.out), new PrintWriter(System.err), false, null, null);
			String[] args = {
				"-verbose",
				"-extdirs", libsFolder.getAbsolutePath(), //The location of the external libraries (Processing's core.jar and others)
				"-bootclasspath", androidJarLoc.getAbsolutePath(), //buildFolder.getAbsolutePath() + "/sdk/platforms/" + androidVersion + "/android.jar", //The location of android.jar
				"-classpath", srcFolder.getAbsolutePath() //The location of the source folder
				+ ":" + genFolder.getAbsolutePath() //The location of the generated folder
				+ ":" + libsFolder.getAbsolutePath(), //The location of the library folder
				"-1.5",
				"-target", "1.5", //Target Java level
				"-d", binFolder.getAbsolutePath() + "/classes/", //The location of the output folder
				srcFolder.getAbsolutePath() + "/" + mainActivityLoc + "/" + sketchName + ".java" //The location of the main Activity
			};
			
			System.out.println("ECJing: " + srcFolder.getAbsolutePath() + "/" + mainActivityLoc + "/" + sketchName + ".java");
			
			if(main.compile(args)) {
				System.out.println();
				System.out.println("ECJ compilation successful");
			} else {
				//We have some compilation errors
				System.out.println();
				System.out.println("ECJ compilation failed");
				
				cleanUpError();
				return;
			}
		}
		
		if(!running.get()) { //CHECK
			cleanUpHalt();
			return;
		}
		
		editor.messageExt(editor.getResources().getString(R.string.run_dx));
		
		//Run DX
		try { //TODO dex non-processing core libraries
			System.out.println("Running DX...");
			
			String[] args = new String[] {
				"--dex",
				"--output=" + binFolder.getAbsolutePath() + "/classes.dex", //The location of the output DEX class file
				binFolder.getAbsolutePath() + "/classes/", //add "/classes/" to get DX to work properly
				dexedLibsFolder.getAbsolutePath()
			};
			
			com.android.dx.command.Main.main(args);
			
			System.out.println("Ran DX successfuly");
		} catch(Exception e) {
			System.out.println("DX failed");
			e.printStackTrace();
			
			cleanUpError();
			return;
		}
		
		if(!running.get()) { //CHECK
			cleanUpHalt();
			return;
		}
		
		editor.messageExt(editor.getResources().getString(R.string.run_apkbuilder));
		
		//Run APKBuilder
		try {
			System.out.println("Running APKBuilder...");
			
			String[] args = {
				binFolder.getAbsolutePath() + "/" + sketchName + ".apk.unsigned", //The location of the output APK file (unsigned)
				"-u",
				"-z", binFolder.getAbsolutePath() + "/" + sketchName + ".apk.res", //The location of the output .apk.res file
				"-f", binFolder.getAbsolutePath() + "/classes.dex", //The location of the DEX class file
				"-z", glslFolder.getAbsolutePath(), //Location of GLSL files
				"-rf", srcFolder.getAbsolutePath() //The location of the source folder
			};
			
			com.android.sdklib.build.ApkBuilderMain.main(args);
			
			System.out.println("Ran APKBuilder succesfully");
		} catch(Exception e) {
			System.out.println("APKBuilder failed");
			e.printStackTrace();
			
			cleanUpError();
			return;
		}
		
		//TODO Switch over to AAPT...
		//This is seriously messed up right now...
		
//		//Run AAPT
//		try {
//			System.out.println("Running AAPT...");
//			
//			String[] args = {
//					aaptLoc.getAbsolutePath(), //The location of AAPT
//					"package", "-v", "-f",
//					"-j", binFolder.getAbsolutePath() + "/" + sketchName + ".apk.res",
//					"-j", glslFolder.getAbsolutePath(),
//					"-F", binFolder.getAbsolutePath() + "/" + sketchName + ".apk.unsigned",
//					binFolder.getAbsolutePath()
//			};
//			
//			aaptProc = Runtime.getRuntime().exec(args);
//			
//			System.out.println("Ran AAPT successfully");
//		} catch(IOException e) {
//			System.out.println("AAPT failed");
//			e.printStackTrace();
//			
//			cleanUpError();
//			return;
//		}
//		
		if(!running.get()) { //CHECK
			cleanUpHalt();
			return;
		}
		
		editor.messageExt(editor.getResources().getString(R.string.run_zipsigner));
		
		if (debug) {
			//Sign the APK using ZipSigner
			signApk();
		} else {
			System.out.println("Signing with private key...");
			
			//We want to sign for release!!!
			signApkRelease();
			
			System.out.println("Exported to: " + getSketchBinFolder().getAbsolutePath() + "/" + sketchName + ".apk");
			editor.messageExt(editor.getResources().getString(R.string.export_signed_package_complete));
			
			cleanUp();
			return;
		}
		
		//TODO this writes AAPT error logs, is it necessary?
//		System.out.println("AAPT logs:");
		copyStream(aaptProc.getErrorStream(), System.err);
		
		if(!running.get()) { //CHECK
			cleanUpError();
			return;
		}
		
		editor.messageExt(editor.getResources().getString(R.string.run_sketch));
		
		//Copy the APK file to a new (and hopefully readable) location
		
		String apkName = sketchName + ".apk";
		String apkLoc = binFolder.getAbsolutePath() + "/" + apkName;
		File apkFile = new File(apkLoc);
		File destApkFile = new File(editor.getFilesDir(), apkName);
		
		Intent promptInstall;
		
		//We only need to do our copying-voodoo if the user is crazy enough to want to build on the internal storage (or if they don't have an external storage...)
		if(PreferenceManager.getDefaultSharedPreferences(editor).getBoolean("pref_build_internal_storage", true)) {
			try {
				//Yes, I know that MODE_WORLD_READABLE is risky...
				//...this is the only way to get the package installer to be able to read the APK file from the internal storage
				//It's not like there's any personal data in the sketch...
				copyFileToOutputStream(apkFile, editor.openFileOutput(apkName, Context.MODE_WORLD_READABLE));
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}

			//Prompt the user to install the APK file
			promptInstall = new Intent(Intent.ACTION_VIEW)
			.setDataAndType(Uri.fromFile(
					destApkFile), //The location of the APK
					"application/vnd.android.package-archive"
					);
		} else {
			//Prompt the user to install the APK file
			promptInstall = new Intent(Intent.ACTION_VIEW)
			.setDataAndType(Uri.parse("file:///" +
					apkLoc), //The location of the APK
					"application/vnd.android.package-archive"
					);
		}
		
		if (injectLogBroadcaster) {
			//Make some space in the console
			for (int i = 0; i < 10; i ++) {
				System.out.println("");
			}
		}
		
		//Get a result so that we can delete the APK file
		editor.startActivityForResult(promptInstall, EditorActivity.FLAG_DELETE_APK);
		
		cleanUp();
	}
	
	private void signApk() {
		String mode = "testkey";
		String inFilename = binFolder.getAbsolutePath() + "/" + sketchName + ".apk.unsigned";
		String outFilename = binFolder.getAbsolutePath() + "/" + sketchName + ".apk";
		
		ZipSigner signer;
		
		try {
			signer = new ZipSigner();
			
			signer.setKeymode(mode);
			
			signer.signZip(inFilename, outFilename);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private void signApkRelease() {
		Security.addProvider(new BouncyCastleProvider());
		
		String inFilename = binFolder.getAbsolutePath() + "/" + sketchName + ".apk.unsigned";
		String outFilename = getSketchBinFolder().getAbsolutePath() + "/" + sketchName + ".apk";
		
		ZipSigner signer;
		
		try {
			signer = new ZipSigner();
			
//			signer.signZip(new URL("file://" + keystore), "bks", keystorePassword, keyAlias, keyAliasPassword, "SHA1WITHRSA", inFilename, outFilename);
			//Let's take advantage of ZipSigner's ability to load JKS keystores as well
			CustomKeySigner.signZip(signer, keystore, keystorePassword, keyAlias, keyAliasPassword, "SHA1WITHRSA", inFilename, outFilename);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
//	public void antBuildProblems(String outPile, String errPile) throws SketchException {
//		final String[] outLines = outPile.split(System.getProperty("line.separator"));
//		final String[] errLines = errPile.split(System.getProperty("line.separator"));
//
//		for(final String line : outLines) {
//			final String javacPrefix = "[javac]";
//			final int javacIndex = line.indexOf(javacPrefix);
//			
//			if(javacIndex != -1) {
//				int offset = javacIndex + javacPrefix.length() + 1;
//				String[] pieces = PApplet.match(line.substring(offset), "^(.+):([0-9]+):\\s+(.+)$");
//				
//				if(pieces != null) {
//					String fileName = pieces[1];
//					// remove the path from the front of the filename
//					fileName = fileName.substring(fileName.lastIndexOf(File.separatorChar) + 1);
//					final int lineNumber = PApplet.parseInt(pieces[2]) - 1;
//					SketchException rex = placeException(pieces[3], fileName, lineNumber);
//					if(rex != null)
//						throw rex;
//				}
//			}
//		}
//		
//		// Couldn't parse the exception, so send something generic
//		SketchException skex = new SketchException("Error from inside the Android tools, check the console.");
//		
//		// Try to parse anything else we might know about
//		for(final String line : errLines) {
//			if(line.contains("Unable to resolve target '" + Manifest.MIN_SDK + "'")) {
//				System.err.println("Use the Android SDK Manager (under the Android");
//				System.err.println("menu) to install the SDK platform and ");
//				System.err.println("Google APIs for Android " + Manifest.MIN_SDK +
//						" (API " + Manifest.MIN_SDK + ")");
//				skex = new SketchException("Please install the SDK platform and " +
//						"Google APIs for API " + Manifest.MIN_SDK);
//			}
//		}
//		// Stack trace is not relevant, just the message.
//		skex.hideStackTrace();
//		throw skex;
//	}
	
//	public SketchException placeException(String message, String dotJavaFilename, int dotJavaLine) {
//		int codeIndex = 0;
//		int codeLine = -1;
//		
//		// first check to see if it's a .java file
//		for(int i = 0; i < tabs.length; i++) {
//			FileMeta meta = tabs[i];
//			
//			if(meta.getSuffix().equals("java")) {
//				if(dotJavaFilename.equals(meta.getFilename())) {
//					codeIndex = i;
//					codeLine = dotJavaLine;
//					return new SketchException(message, codeIndex, codeLine);
//				}
//			}
//		}
//		
//		// If not the preprocessed file at this point, then need to get out
//		if(!dotJavaFilename.equals(sketchName + ".java"))
//			return null;
//		
//		// if it's not a .java file, codeIndex will still be 0
//		// this section searches through the list of .pde files
//		codeIndex = 0;
//		for(int i = 0; i < tabs.length; i++) {
//			FileMeta meta = tabs[i];
//			
//			if(meta.getSuffix().equals("pde")) {
//				if(meta.getPreprocOffset() <= dotJavaLine) {
//					codeIndex = i;
//					codeLine = dotJavaLine - meta.getPreprocOffset();
//				}
//			}
//		}
//		// could not find a proper line number, so deal with this differently.
//		// but if it was in fact the .java file we're looking for, though,
//		// send the error message through.
//		// this is necessary because 'import' statements will be at a line
//		// that has a lower number than the preproc offset, for instance.
//		return new SketchException(message, codeIndex, codeLine, -1, false); // changed for 0194 for compile errors, but...
//	}
	
	public String preprocess(File srcFolder, String packageName, PdePreprocessor preprocessor, boolean sizeWarning) throws SketchException {
		if(getSketchFolder().exists());
		
		classPath = binFolder.getAbsolutePath();
		javaLibraryPath = "";
		
		// figure out the contents of the code folder to see if there
		// are files that need to be added to the imports
		String[] codeFolderPackages = null;
		if(getSketchCodeFolder().exists()) {
			File codeFolder = getSketchCodeFolder();
			javaLibraryPath = codeFolder.getAbsolutePath();
			
			// get a list of .jar files in the "code" folder
			// (class files in subfolders should also be picked up)
			String codeFolderClassPath = contentsToClassPath(codeFolder);
			// append the jar files in the code folder to the class path
			classPath += File.pathSeparator + codeFolderClassPath;
			// get list of packages found in those jars
			codeFolderPackages = packageListFromClassPath(codeFolderClassPath);
		}
		
		if (injectLogBroadcaster) {
			//Add "LogBroadcaster.pde" to the code
			
			try {
				InputStream stream = editor.getAssets().open("LogBroadcaster.pde");
				
				int size = stream.available();
				byte[] buffer = new byte[size];
				
				stream.read(buffer);
				stream.close();
				
				String text = new String(buffer);
				
				FileMeta[] oldMeta = tabs;
				tabs = new FileMeta[oldMeta.length + 1];
				System.arraycopy(oldMeta, 0, tabs, 0, oldMeta.length);
				tabs[tabs.length - 1] = new FileMeta("LogBroadcaster.pde", text, 0, 0, 0, 0);
				
				System.out.println("Successfuly injected LogBroadcaster.pde");
			} catch (IOException e) {
				System.err.println("Failed to inject LogBroadcaster.pde into sketch!");
				e.printStackTrace();
			}
		}
		
		// 1. concatenate all .pde files to the 'main' pde
		// store line number for starting point of each code bit
		
		StringBuilder bigCode = new StringBuilder();
		int bigCount = 0;
		for(FileMeta meta : tabs) {
			if(meta.getSuffix().equals(".pde")) {
				meta.setPreprocOffset(bigCount);
				bigCode.append(meta.getText());
				bigCode.append("\n");
				bigCount += numLines(meta.getText());
			}
		}
		
		PreprocessorResult result;
		try {
			File outputFolder = (packageName == null) ? srcFolder : new File(srcFolder, packageName.replace('.', '/'));
			outputFolder.mkdirs();
			final File java = new File(outputFolder, sketchName + ".java");
			final PrintWriter stream = new PrintWriter(new FileWriter(java));
			try {
				result = preprocessor.write(stream, bigCode.toString(), codeFolderPackages);
			} finally {
				stream.close();
			}
		} catch (FileNotFoundException fnfe) {
			fnfe.printStackTrace();
			String msg = "Build folder disappeared or could not be written";
			throw new SketchException(msg);
			
		} catch (antlr.RecognitionException re) {
			// re also returns a column that we're not bothering with for now
			// first assume that it's the main file
			int errorLine = re.getLine() - 1;
			
			// then search through for anyone else whose preprocName is null,
			// since they've also been combined into the main pde.
			int errorFile = findErrorFile(errorLine);
			errorLine -= tabs[errorFile].getPreprocOffset();
			
			String msg = re.getMessage();
			
			if (msg.contains("expecting RCURLY")) {
				// This can be a problem since the error is sometimes listed as a line
				// that's actually past the number of lines. For instance, it might
				// report "line 15" of a 14 line program. Added code to highlightLine()
				// inside Editor to deal with this situation (since that code is also
				// useful for other similar situations).
				throw new SketchException("Found one too many { characters " +
						"without a } to match it.",
						errorFile, errorLine, re.getColumn(), false);
			}
			
			if (msg.contains("expecting LCURLY")) {
				System.err.println(msg);
				String suffix = ".";
				String[] m = PApplet.match(msg, "found ('.*')");
				if (m != null) {
					suffix = ", not " + m[1] + ".";
				}
				throw new SketchException("Was expecting a { character" + suffix,
						errorFile, errorLine, re.getColumn(), false);
			}
			
			if (msg.indexOf("expecting RBRACK") != -1) {
				System.err.println(msg);
				throw new SketchException("Syntax error, " +
						"maybe a missing ] character?",
						errorFile, errorLine, re.getColumn(), false);
			}
			
			if (msg.indexOf("expecting SEMI") != -1) {
				System.err.println(msg);
				throw new SketchException("Syntax error, " +
						"maybe a missing semicolon?",
						errorFile, errorLine, re.getColumn(), false);
			}
			
			if (msg.indexOf("expecting RPAREN") != -1) {
				System.err.println(msg);
				throw new SketchException("Syntax error, " +
						"maybe a missing right parenthesis?",
						errorFile, errorLine, re.getColumn(), false);
			}
			
			if (msg.indexOf("preproc.web_colors") != -1) {
				throw new SketchException("A web color (such as #ffcc00) " +
						"must be six digits.",
						errorFile, errorLine, re.getColumn(), false);
			}
			
			//System.out.println("msg is " + msg);
			throw new SketchException(msg, errorFile,
					errorLine, re.getColumn(), false);
			
		} catch (antlr.TokenStreamRecognitionException tsre) {
			// while this seems to store line and column internally,
			// there doesn't seem to be a method to grab it..
			// so instead it's done using a regexp
			
			// TODO (P5) not tested since removing ORO matcher.. ^ could be a problem
			String mess = "^line (\\d+):(\\d+):\\s";
			
			String[] matches = PApplet.match(tsre.toString(), mess);
			if (matches != null) {
				int errorLine = Integer.parseInt(matches[1]) - 1;
				int errorColumn = Integer.parseInt(matches[2]);

				int errorFile = 0;
				for (int i = 1; i < tabs.length; i++) {
					FileMeta meta = tabs[i];
					if(meta.getSuffix().equals(".pde") && (meta.getPreprocOffset() < errorLine))
						errorFile = i;
				}
				errorLine -= tabs[errorFile].getPreprocOffset();

				throw new SketchException(tsre.getMessage(), errorFile, errorLine, errorColumn);
				
			} else {
				// this is bad, defaults to the main class.. hrm.
				String msg = tsre.toString();
				throw new SketchException(msg, 0, -1, -1);
			}
		} catch (Exception ex) {
			// TODO (P5) better method for handling this?
			System.err.println("Uncaught exception type:" + ex.getClass());
			ex.printStackTrace();
			throw new SketchException(ex.toString());
		}
		
		// grab the imports from the code just preproc'd
		
		importedLibraries = new ArrayList<Library>();
//		Library core = mode.getCoreLibrary();
//		if (core != null) {
//			importedLibraries.add(core);
//			classPath += core.getClassPath();
//		}
		
		for(String item : result.extraImports) {
			//Remove things up to the last dot
			int dot = item.lastIndexOf('.');
			//http://dev.processing.org/bugs/show_bug.cgi?id=1145
			String entry = (dot == -1) ? item : item.substring(0, dot);
			Library library = getLibrary(entry);
			
			if(library != null) {
				if(!importedLibraries.contains(library)) {
					importedLibraries.add(library);
					classPath += library.getClassPath((APDE) editor.getApplicationContext());
//					javaLibraryPath += File.pathSeparator + library.getNativePath();
				}
			} else {
				boolean found = false;
				//If someone insists on unnecessarily repeating the code folder
				//import, don't show an error for it.
				if(codeFolderPackages != null) {
					String itemPkg = item.substring(0, item.lastIndexOf('.'));
					for(String pkg : codeFolderPackages) {
						if(pkg.equals(itemPkg)) {
							found = true;
							break;
						}
					}
				}
				if(ignorableImport(item)) {
					found = true;
				}
				if(!found) {
					System.err.println("No library found for " + entry);
				}
			}
		}
		
		// Finally, add the regular Java CLASSPATH. This contains everything
		// imported by the PDE itself (core.jar, pde.jar, quaqua.jar) which may
		// in fact be more of a problem.
		String javaClassPath = System.getProperty("java.class.path");
		// Remove quotes if any.. A messy (and frequent) Windows problem
		if (javaClassPath.startsWith("\"") && javaClassPath.endsWith("\"")) {
			javaClassPath = javaClassPath.substring(1, javaClassPath.length() - 1);
		}
		classPath += File.pathSeparator + javaClassPath;
		
		// But make sure that there isn't anything in there that's missing,
		// otherwise ECJ will complain and die. For instance, Java 1.7 (or maybe
		// it's appbundler?) adds Java/Classes to the path, which kills us.
		//String[] classPieces = PApplet.split(classPath, File.pathSeparator);
		// Nah, nevermind... we'll just create the @!#$! folder until they fix it.


		// 3. then loop over the code[] and save each .java file

		for(FileMeta meta : tabs) {
			if(meta.getSuffix().equals("java")) {
				// In most cases, no pre-processing services necessary for Java files.
				// Just write the the contents of 'program' to a .java file
				// into the build directory. However, if a default package is being
				// used (as in Android), and no package is specified in the source,
				// then we need to move this code to the same package as the sketch.
				// Otherwise, the class may not be found, or at a minimum, the default
				// access across the packages will mean that things behave incorrectly.
				// For instance, desktop code that uses a .java file with no packages,
				// will be fine with the default access, but since Android's PApplet
				// requires a package, code from that (default) package (such as the
				// PApplet itself) won't have access to methods/variables from the
				// package-less .java file (unless they're all marked public).
				String filename = meta.getFilename();
				try {
					String javaCode = meta.getText();
					String[] packageMatch = PApplet.match(javaCode, PACKAGE_REGEX);
					// if no package, and a default package is being used
					// (i.e. on Android) we'll have to add one
					
					if(packageMatch == null && packageName == null) {
						meta.writeData(editor.getApplicationContext(), srcFolder.getAbsolutePath() + File.separator + filename); //TODO does this actually do what we want?
						//sc.copyTo(new File(srcFolder, filename));
					} else {
						if(packageMatch == null) {
							// use the default package name, since mixing with package-less code will break
							packageMatch = new String[] { packageName };
							// add the package name to the source before writing it
							javaCode = "package " + packageName + ";" + javaCode;
						}
						File packageFolder = new File(srcFolder, packageMatch[0].replace('.', '/'));
						packageFolder.mkdirs();
						saveFile(javaCode, new File(packageFolder, filename));
					}

				} catch (IOException e) {
					e.printStackTrace();
					String msg = "Problem moving " + filename + " to the build folder";
					throw new SketchException(msg);
				}

			} else if (meta.getSuffix().equals("pde")) {
				// The compiler and runner will need this to have a proper offset
				meta.addPreprocOffset(result.headerOffset);
			}
		}
		foundMain = preprocessor.hasMethod("main");
		return result.className;
	}
	
	private int numLines(String input) {
		int count = 1;
		
		for(int i = 0; i < input.length(); i ++)
			if(input.charAt(i) == '\n')
				count ++;
		
		return count;
	}
	
	protected boolean ignorableImport(String pkg) {
		if (pkg.startsWith("android.")) return true;
		if (pkg.startsWith("java.")) return true;
		if (pkg.startsWith("javax.")) return true;
		if (pkg.startsWith("org.apache.http.")) return true;
		if (pkg.startsWith("org.json.")) return true;
		if (pkg.startsWith("org.w3c.dom.")) return true;
		if (pkg.startsWith("org.xml.sax.")) return true;

		if (pkg.startsWith("processing.core.")) return true;
		if (pkg.startsWith("processing.data.")) return true;
		if (pkg.startsWith("processing.event.")) return true;
		if (pkg.startsWith("processing.opengl.")) return true;

		return false;
	}
	
	private Library getLibrary(String pkgName) throws SketchException {
		ArrayList<Library> libraries = ((APDE) editor.getApplicationContext()).getImportToLibraryTable().get(pkgName);
		if (libraries == null) {
			return null;
		} else if (libraries.size() > 1) { //This is if there are multiple libraries with the same package name... but when does this ever happen?
			String primary = "More than one library is competing for this sketch.\n";
			String secondary = "The import " + pkgName + " points to multiple libraries:\n";
			for (Library library : libraries) {
				String location = library.getLibraryFolder((APDE) editor.getApplicationContext()).getAbsolutePath();
//				if (location.startsWith(getLibrariesFolder().getAbsolutePath())) { //Android mode has no core libraries - but we'll leave this just in case
//					location = "part of Processing";
//				}
				secondary += library.getName() + " (" + location + ")\n";
			}
			secondary += "Extra libraries need to be removed before this sketch can be used.";
			System.err.println("Duplicate Library Problem\n\n" + primary + secondary);
			throw new SketchException("Duplicate libraries found for " + pkgName + ".");
		} else {
			return libraries.get(0);
		}
	}
	
	protected int findErrorFile(int errorLine) {
		for (int i = tabs.length - 1; i > 0; i --) {
			FileMeta meta = tabs[i];
			if (meta.getSuffix().equals(".pde") && (meta.getPreprocOffset() <= errorLine)) {
				// keep looping until the errorLine is past the offset
				return i;
			}
		}
		return 0; // i give up
	}

	private void writeAntProps(final File file, String packageName) {
		try {
			PrintWriter writer = new PrintWriter(file);
			writer.println("application-package=" + packageName);
			writer.flush();
			writer.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}
	
	private void writeBuildXML(final File file, final String projectName) {
		try {
			final PrintWriter writer = new PrintWriter(file);
			writer.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
			
			writer.println(" <project name=\"" + projectName + "\" default=\"help\">");
	
			writer.println(" <property file=\"local.properties\" />");
			writer.println(" <property file=\"ant.properties\" />");
			
			//Disabled for Eclipse project export
//			//TODO added this to use Eclipse's compiler istead of javac
//			writer.println("<property name=\"build.compiler\" value=\"org.eclipse.jdt.core.JDTCompilerAdapter\"/>");
			
			writer.println(" <property environment=\"env\" />");
			writer.println(" <condition property=\"sdk.dir\" value=\"${env.ANDROID_HOME}\">");
			writer.println(" <isset property=\"env.ANDROID_HOME\" />");
			writer.println(" </condition>");
	
			writer.println(" <loadproperties srcFile=\"project.properties\" />");
	
			writer.println(" <fail message=\"sdk.dir is missing. Make sure to generate local.properties using 'android update project'\" unless=\"sdk.dir\" />");
	
			writer.println(" <import file=\"custom_rules.xml\" optional=\"true\" />");
	
			writer.println(" <!-- version-tag: 1 -->"); // should this be 'custom' instead of 1?
			writer.println(" <import file=\"${sdk.dir}/tools/ant/build.xml\" />");
	
			writer.println("</project>");
			writer.flush();
			writer.close();
		} catch(FileNotFoundException e) {
			e.printStackTrace();
		}
	}
	
	private void writeProjectProps(final File file, String sdkVersion) {
		try {
			final PrintWriter writer = new PrintWriter(file);
			writer.println("target=" + "android-" + sdkVersion);
			writer.println();
			// http://stackoverflow.com/questions/4821043/includeantruntime-was-not-set-for-android-ant-script
			writer.println("# Suppress the javac task warnings about \"includeAntRuntime\"");
			writer.println("build.sysclasspath=last");
			writer.flush();
			writer.close();
		} catch(FileNotFoundException e) {
			e.printStackTrace();
		}
	}
	
//	private void writeLocalProps(final File file) {
//		try {
//			final PrintWriter writer = new PrintWriter(file);
//			final String sdkPath = "";
//			
//			writer.println("sdk.dir=" + sdkPath);
//			writer.flush();
//			writer.close();
//		} catch(FileNotFoundException e) {
//			e.printStackTrace();
//		}
//	}
	
	private void writeRes(File resFolder, String className) throws SketchException {
		File layoutFolder = mkdirs(resFolder, "layout");
		File layoutFile = new File(layoutFolder, "main.xml");
		writeResLayoutMain(layoutFile);
		
		// write the icon files
		File sketchFolder = getSketchFolder();
		File localIcon36 = new File(sketchFolder, ICON_36);
		File localIcon48 = new File(sketchFolder, ICON_48);
		File localIcon72 = new File(sketchFolder, ICON_72);
		File localIcon96 = new File(sketchFolder, ICON_96);
		
		File buildIcon48 = new File(resFolder, "drawable/icon.png");
		File buildIcon36 = new File(resFolder, "drawable-ldpi/icon.png");
		File buildIcon72 = new File(resFolder, "drawable-hdpi/icon.png");
		File buildIcon96 = new File(resFolder, "drawable-xhdpi/icon.png");
		
		if (!localIcon36.exists() && !localIcon48.exists() && !localIcon72.exists() && !localIcon96.exists()) {
			try {
				AssetManager am = editor.getAssets();
				
				// if no icons are in the sketch folder, then copy all the defaults
				if(buildIcon36.getParentFile().mkdirs()) {
					InputStream inputStream = am.open("icon-36.png");
					createFileFromInputStream(inputStream, buildIcon36);
					inputStream.close();
				} else {
					System.err.println("Could not create \"drawable-ldpi\" folder.");
				}
				if(buildIcon48.getParentFile().mkdirs()) {
					InputStream inputStream = am.open("icon-48.png");
					createFileFromInputStream(inputStream, buildIcon48);
					inputStream.close();
				} else {
					System.err.println("Could not create \"drawable\" folder.");
				}
				if(buildIcon72.getParentFile().mkdirs()) {
					InputStream inputStream = am.open("icon-72.png");
					createFileFromInputStream(inputStream, buildIcon72);
					inputStream.close();
				} else {
					System.err.println("Could not create \"drawable-hdpi\" folder.");
				}
				if(buildIcon96.getParentFile().mkdirs()) { //TODO make a properly scaled "icon-96.png" graphic - right now, it's scaled up from the 72p version
					InputStream inputStream = am.open("icon-96.png");
					createFileFromInputStream(inputStream, buildIcon96);
					inputStream.close();
				} else {
					System.err.println("Could not create \"drawable-xhdpi\" folder.");
				}
			} catch (IOException e) {
				e.printStackTrace();
				//throw new SketchException("Could not get Android icons");
			}
		} else {
			// if at least one of the icons already exists, then use that across the board
			try {
				if (localIcon36.exists()) {
					if (new File(resFolder, "drawable-ldpi").mkdirs()) {
						copyFile(localIcon36, buildIcon36);
					}
				}
				if (localIcon48.exists()) {
					if (new File(resFolder, "drawable").mkdirs()) {
						copyFile(localIcon48, buildIcon48);
					}
				}
				if (localIcon72.exists()) {
					if (new File(resFolder, "drawable-hdpi").mkdirs()) {
						copyFile(localIcon72, buildIcon72);
					}
				}
				if (localIcon96.exists()) {
					if (new File(resFolder, "drawable-xhdpi").mkdirs()) {
						copyFile(localIcon96, buildIcon96);
					}
				}
			} catch (IOException e) {
				System.err.println("Problem while copying icons.");
				e.printStackTrace();
			}
		}
	}
	
	private void writeResLayoutMain(final File file) {
		try {
			final PrintWriter writer = new PrintWriter(file);
			writer.println("<?xml version=\"1.0\" encoding=\"utf-8\"?>");
			writer.println("<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"");
			writer.println(" android:orientation=\"vertical\"");
			writer.println(" android:layout_width=\"fill_parent\"");
			writer.println(" android:layout_height=\"fill_parent\">");
			writer.println("</LinearLayout>");
			writer.flush();
			writer.close();
		} catch(FileNotFoundException e) {
			e.printStackTrace();
		}
	}
	
	private void copyLibraries(final File libsFolder, final File assetsFolder) throws IOException { //TODO support native library stuffs
		for (Library library : importedLibraries) {
			//Add each item from the library folder / export list to the output
			for (File exportFile : library.getAndroidExports((APDE) editor.getApplicationContext())) {
				String exportName = exportFile.getName();
				if (!exportFile.exists()) {
					System.err.println(exportFile.getName() +
							" is mentioned in export.txt, but it's " +
							"a big fat lie and does not exist.");
				} else if (exportFile.isDirectory()) {
					//Copy native library folders to the correct location
					if (exportName.equals("armeabi") ||
							exportName.equals("armeabi-v7a") ||
							exportName.equals("x86")) {
						copyDir(exportFile, new File(libsFolder, exportName));
					} else {
						//Copy any other directory to the assets folder
						copyDir(exportFile, new File(assetsFolder, exportName));
					}
				} else if (exportName.toLowerCase(Locale.US).endsWith(".zip")) {
					// As of r4 of the Android SDK, it looks like .zip files
					// are ignored in the libs folder, so rename to .jar
					System.err.println(".zip files are not allowed in Android libraries.");
					System.err.println("Please rename " + exportFile.getName() + " to be a .jar file.");
					String jarName = exportName.substring(0, exportName.length() - 4) + ".jar";
					copyFile(exportFile, new File(libsFolder, jarName));
				} else if (exportName.toLowerCase(Locale.US).endsWith(".jar")) {
					copyFile(exportFile, new File(libsFolder, exportName));
				} else {
					copyFile(exportFile, new File(assetsFolder, exportName));
				}
			}
		}
	}
	
	private void copyLibraries(final File libsFolder, final File dexedLibsFolder, final File assetsFolder) throws IOException { //TODO support native library stuffs
		for (Library library : importedLibraries) {
			//Add each item from the library folder / export list to the output
			for (File exportFile : library.getAndroidExports((APDE) editor.getApplicationContext())) {
				String exportName = exportFile.getName();
				if (!exportFile.exists()) {
					System.err.println(exportFile.getName() +
							" is mentioned in export.txt, but it's " +
							"a big fat lie and does not exist.");
				} else if (exportFile.isDirectory()) {
					//Copy native library folders to the correct location
					if (exportName.equals("armeabi") ||
							exportName.equals("armeabi-v7a") ||
							exportName.equals("x86")) {
						copyDir(exportFile, new File(libsFolder, exportName));
					} else {
						//Copy any other directory to the assets folder
						copyDir(exportFile, new File(assetsFolder, exportName));
					}
				} else if (exportName.toLowerCase(Locale.US).endsWith(".zip")) {
					// As of r4 of the Android SDK, it looks like .zip files
					// are ignored in the libs folder, so rename to .jar
					System.err.println(".zip files are not allowed in Android libraries.");
					System.err.println("Please rename " + exportFile.getName() + " to be a .jar file.");
					String jarName = exportName.substring(0, exportName.length() - 4) + ".jar";
					copyFile(exportFile, new File(libsFolder, jarName));
				} else if(exportName.toLowerCase(Locale.US).endsWith("-dex.jar")) {
					//Handle the dexed JARs
					copyFile(exportFile, new File(dexedLibsFolder, exportName));
				} else if (exportName.toLowerCase(Locale.US).endsWith(".jar")) {
					copyFile(exportFile, new File(libsFolder, exportName));
				} else {
					copyFile(exportFile, new File(assetsFolder, exportName));
				}
			}
		}
	}
	
	private void copyCodeFolder(final File libsFolder) throws IOException {
		// Copy files from the 'code' directory into the 'libs' folder
		final File codeFolder = getSketchCodeFolder();
		if(codeFolder != null && codeFolder.exists()) {
			for(final File item : codeFolder.listFiles()) {
				if(!item.isDirectory()) {
					final String name = item.getName();
					final String lcname = name.toLowerCase(Locale.US);
					if(lcname.endsWith(".jar") || lcname.endsWith(".zip")) {
						String jarName = name.substring(0, name.length() - 4) + ".jar";
						copyFile(item, new File(libsFolder, jarName));
					}
				}
			}
		}
	}
	
	private void copyCodeDexFolder(final File libsDexFolder) throws IOException {
		// Copy files from the 'code-dex' directory into the 'libs' folder
		final File codeDexFolder = getSketchCodeDexFolder();
		if(codeDexFolder != null && codeDexFolder.exists()) {
			for(final File item : codeDexFolder.listFiles()) {
				if(!item.isDirectory()) {
					final String name = item.getName();
					final String lcname = name.toLowerCase(Locale.US);
					if(lcname.endsWith(".jar") || lcname.endsWith(".zip")) {
						String jarName = name.substring(0, name.length() - 4) + ".jar";
						copyFile(item, new File(libsDexFolder, jarName));
					}
				}
			}
		}
	}
	
	//http://stackoverflow.com/questions/11820142/how-to-pass-a-file-path-which-is-in-assets-folder-to-filestring-path
	private File createFileFromInputStream(InputStream inputStream, File destFile) {
		try {
			FileOutputStream outputStream = new FileOutputStream(destFile);
			byte buffer[] = new byte[1024];
			int length = 0;
			
			while((length = inputStream.read(buffer)) > 0)
				outputStream.write(buffer, 0, length);
			
			outputStream.close();
			inputStream.close();
			
			return destFile;
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return null;
	}
	
	//http://stackoverflow.com/questions/16983989/copy-directory-from-assets-to-data-folder
//	private static boolean copyAssetFolder(AssetManager assetManager, String fromAssetPath, String toPath) {
//		try {
//			String[] files = assetManager.list(fromAssetPath);
//			new File(toPath).mkdirs();
//			boolean res = true;
//			for(String file : files)
//				if((file.contains(".") && !file.equals("android-4.4")) || file.equals("aapt") || file.equals("aidl")) //TODO changed this, it's awfully hard-coded now
//					res &= copyAsset(assetManager, fromAssetPath + "/" + file, toPath + "/" + file);
//				else 
//					res &= copyAssetFolder(assetManager, fromAssetPath + "/" + file, toPath + "/" + file);
//			
//			return res;
//		} catch (Exception e) {
//			e.printStackTrace();
//			return false;
//		}
//	}

//	private static boolean copyAsset(AssetManager assetManager, String fromAssetPath, String toPath) {
//		InputStream in = null;
//		OutputStream out = null;
//		try {
//			in = assetManager.open(fromAssetPath);
//			new File(toPath).createNewFile();
//			out = new FileOutputStream(toPath);
//			copyFile(in, out);
//			in.close();
//			in = null;
//			out.flush();
//			out.close();
//			out = null;
//			
//			return true;
//		} catch(Exception e) {
//			e.printStackTrace();
//			return false;
//		}
//	}
	
	static public File mkdirs(final File parent, final String name) {
		final File result = new File(parent, name);
		if(!(result.exists() || result.mkdirs())) {
			//throw new SketchException("Could not create " + result);
			System.out.println("Could not create " + result); //TODO changed this around to avoid SketchException for Build Path reasons
			return null;
		}
		return result;
	}
	
	static public void copyFileToOutputStream(File sourceFile, FileOutputStream targetFile) throws IOException {
		BufferedInputStream from = new BufferedInputStream(new FileInputStream(sourceFile));
		byte[] buffer = new byte[16 * 1024];
		int bytesRead;
		while((bytesRead = from.read(buffer)) != -1)
			targetFile.write(buffer, 0, bytesRead);
		from.close();
		from = null;

		targetFile.flush();
		targetFile.close();
		targetFile = null;
	}
	
	static public void copyFile(File sourceFile, File targetFile) throws IOException {
		BufferedInputStream from = new BufferedInputStream(new FileInputStream(sourceFile));
		BufferedOutputStream to = new BufferedOutputStream(new FileOutputStream(targetFile));
		byte[] buffer = new byte[16 * 1024];
		int bytesRead;
		while((bytesRead = from.read(buffer)) != -1)
			to.write(buffer, 0, bytesRead);
		from.close();
		from = null;

		to.flush();
		to.close();
		to = null;

		targetFile.setLastModified(sourceFile.lastModified());
		targetFile.setExecutable(sourceFile.canExecute());
	}
	
//	private static void copyFile(InputStream in, OutputStream out) throws IOException {
//		byte[] buffer = new byte[1024];
//		int read;
//		while((read = in.read(buffer)) != -1)
//			out.write(buffer, 0, read);
//	}
	
	static public void copyDir(File sourceDir, File targetDir) throws IOException {
		if(sourceDir.equals(targetDir)) {
			final String urDum = "source and target directories are identical";
			throw new IllegalArgumentException(urDum);
		}
		targetDir.mkdirs();
		String files[] = sourceDir.list();
		for(int i = 0; i < files.length; i++) {
			// Ignore dot files (.DS_Store), dot folders (.svn) while copying
			if(files[i].charAt(0) == '.') continue;
			File source = new File(sourceDir, files[i]);
			File target = new File(targetDir, files[i]);
			if(source.isDirectory()) {
				copyDir(source, target);
				target.setLastModified(source.lastModified());
			} else {
				copyFile(source, target);
			}
		}
	}
	
	static public String contentsToClassPath(File folder) {
		if(folder == null) return "";
		
		StringBuffer abuffer = new StringBuffer();
		String sep = System.getProperty("path.separator");
		
		try {
			String path = folder.getCanonicalPath();
			
			// When getting the name of this folder, make sure it has a slash
			// after it, so that the names of sub-items can be added.
			if(!path.endsWith(File.separator)) {
				path += File.separator;
			}

			String list[] = folder.list();
			for(int i = 0; i < list.length; i++) {
				// Skip . and ._ files. Prior to 0125p3, .jar files that had
				// OS X AppleDouble files associated would cause trouble.
				if(list[i].startsWith(".")) continue;

				if(list[i].toLowerCase(Locale.US).endsWith(".jar") || list[i].toLowerCase(Locale.US).endsWith(".zip")) {
					abuffer.append(sep);
					abuffer.append(path);
					abuffer.append(list[i]);
				}
			}
		} catch(IOException e) {
			e.printStackTrace();  // this would be odd
		}
		
		return abuffer.toString();
	}
	
	static public void saveFile(String str, File file) throws IOException {
		File temp = File.createTempFile(file.getName(), null, file.getParentFile());
		try {
			// fix from cjwant to prevent symlinks from being destroyed.
			File canon = file.getCanonicalFile();
			file = canon;
		} catch(IOException e) {
			throw new IOException("Could not resolve canonical representation of " +
					file.getAbsolutePath());
		}
		PApplet.saveStrings(temp, new String[] { str });
		if(file.exists()) {
			boolean result = file.delete();
			if (!result) {
				throw new IOException("Could not remove old version of " +
						file.getAbsolutePath());
			}
		}
		boolean result = temp.renameTo(file);
		if(!result)
			throw new IOException("Could not replace " + file.getAbsolutePath());
	}
	
	static public String[] packageListFromClassPath(String path) {
		Hashtable<String, Object> table = new Hashtable<String, Object>();
		String pieces[] = PApplet.split(path, File.pathSeparatorChar);
		
		for(int i = 0; i < pieces.length; i++) {
			if(pieces[i].length() == 0) continue;
			
			if(pieces[i].toLowerCase(Locale.US).endsWith(".jar") || pieces[i].toLowerCase(Locale.US).endsWith(".zip"))
				packageListFromZip(pieces[i], table);
			else {  // it's another type of file or directory
				File dir = new File(pieces[i]);
				if(dir.exists() && dir.isDirectory())
					packageListFromFolder(dir, null, table);
			}
		}
		int tableCount = table.size();
		String output[] = new String[tableCount];
		int index = 0;
		Enumeration<String> e = table.keys();
		while(e.hasMoreElements())
			output[index++] = (e.nextElement()).replace('/', '.');
		
		return output;
	}
	
	static private void packageListFromZip(String filename, Hashtable<String, Object> table) {
		try {
			ZipFile file = new ZipFile(filename);
			Enumeration<? extends ZipEntry> entries = file.entries();
			while(entries.hasMoreElements()) {
				ZipEntry entry = (ZipEntry) entries.nextElement();

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
			System.err.println("Ignoring " + filename + " (" + e.getMessage() + ")");
		}
	}
	
	static private void packageListFromFolder(File dir, String sofar, Hashtable<String, Object> table) {
		boolean foundClass = false;
		String files[] = dir.list();
		
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
	
	public File getBuildFolder() {
		//Let the user pick where to build
		if(PreferenceManager.getDefaultSharedPreferences(editor).getBoolean("pref_build_internal_storage", true))
			return editor.getDir("build", 0);
		else
			return new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getParentFile(), "build");
	}
	
	public File getTempFolder() {
		return new File(editor.getFilesDir(), "tmp");
	}
	
	public File getSketchFolder() {
		return ((APDE) editor.getApplication()).getSketchLocation();
	}
	
	public File getSketchDataFolder() {
		return new File(getSketchFolder(), "data");
	}
	
	public File getSketchCodeFolder() {
		return new File(getSketchFolder(), "code");
	}
	
	public File getSketchCodeDexFolder() {
		return new File(getSketchFolder(), "code-dex");
	}
	
	public File getSketchBinFolder() {
		return new File(getSketchFolder(), "bin");
	}
	
	//StackOverflow: http://codereview.stackexchange.com/questions/8835/java-most-compact-way-to-print-inputstream-to-system-out
	public static long copyStream(InputStream is, OutputStream os) {
		final int BUFFER_SIZE = 8192;

		byte[] buf = new byte[BUFFER_SIZE];
		long total = 0;
		int len = 0;
		try {
			while (-1 != (len = is.read(buf))) {
				os.write(buf, 0, len);
				total += len;
			}
		} catch (IOException ioe) {
			throw new RuntimeException("error reading stream", ioe);
		}
		return total;
	}
}