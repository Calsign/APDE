/*
 * Seriously hacked from the Processing Project... it might still be recognizable
 * Code taken from JavaBuild, AndroidBuild, Base, AndroidPreprocessor, Preprocessor, probably others
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
import java.util.Collection;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import kellinwood.security.zipsigner.ZipSigner;

import org.eclipse.jdt.internal.compiler.batch.Main;

import processing.app.Preferences;
import processing.core.PApplet;
import processing.mode.java.preproc.PdePreprocessor;
import processing.mode.java.preproc.PreprocessorResult;

import android.content.Intent;
import android.content.res.AssetManager;
import android.net.Uri;
import android.os.Environment;

import com.calsignlabs.apde.*;

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
	
//	private File buildFile;
	
	protected String classPath;
	protected String javaLibraryPath;
	
	protected boolean foundMain;
	
	private static final String ICON_96 = "icon-96.png";
	private static final String ICON_72 = "icon-72.png";
	private static final String ICON_48 = "icon-48.png";
	private static final String ICON_36 = "icon-36.png";
	
	private static AtomicBoolean running;
	
	public static AtomicBoolean customManifest;
	public static AtomicReference<String> prettyName;
	public static AtomicReferenceArray<String> perms;
	public static AtomicInteger targetSdk;
	public static AtomicReference<String> orientation;
	
	public Build(APDE global) {
		this.editor = global.getEditor();
		
		sketchName = global.getSketchName();
		tabs = new FileMeta[editor.tabBar.getTabCount()];
		Collection<FileMeta> metas = editor.getTabs().values();
		int i = 0;
		for(FileMeta meta : metas) {
			tabs[i] = meta;
			i ++;
		}
		
		running = new AtomicBoolean(true);
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
		//TODO erase build folder after the build...
		//...leaving it here for debugging
	}
	
	//Recursive file deletion
    void deleteFile(File f) {
    	if(f.isDirectory())
    		for(File c : f.listFiles())
    			deleteFile(c);
    	
    	if(!f.delete())
    		System.err.println("Failed to delete file: " + f);
    }
	
	/**
	 * @param target either "release" or "debug"
	 */
	public void build(String target) {
		running.set(true);
		
		//Throughout this function, perform periodic checks to see if the user has cancelled the build
		
		editor.messageExt(editor.getResources().getString(R.string.build_sketch_message));
		
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
		if(buildFolder.exists())
			deleteFile(buildFolder);
		
		buildFolder.mkdir();
		srcFolder.mkdir();
		libsFolder.mkdir();
		assetsFolder.mkdir();
		binFolder.mkdir();
		dexedLibsFolder.mkdir();
		
		tmpFolder.mkdir();
		
		Manifest manifest = null;
		String sketchClassName = null;
		
		editor.messageExt(editor.getResources().getString(R.string.gen_project_message));
		
		if(!running.get()) { //CHECK
			cleanUpHalt();
			return;
		}
		
		//Used to determine whether or not to build with ALL of the OpenGL libraries...
		//...it takes a lot longer to run DEX if they're included
		boolean isOpenGL = false;
		//File glLibLoc = new File(binFolder, "libs.dex");
		
		File androidJarLoc = new File(tmpFolder, "android.jar");
		
		try {
			manifest = new Manifest(this);
			Preferences.setInteger("editor.tabs.size", 2); //TODO this is the default... so a tab adds two spaces
			Preproc preproc = new Preproc(sketchName, manifest.getPackageName());
			
			//TODO what if the tab containing "size()" isn't the first tab?
			preproc.initSketchSize(tabs[0].getText());
			sketchClassName = preprocess(srcFolder, manifest.getPackageName(), preproc, false);
			
			//Detect if the renderer is one of the OpenGL renderers
			//TODO support custom renderers that require OpenGL or... other problems that may arise
			String sketchRenderer = preproc.getSketchRenderer();
			if(sketchRenderer != null)
				isOpenGL = sketchRenderer.equals("OPENGL") || sketchRenderer.equals("P3D") || sketchRenderer.equals("P2D");
			else
				isOpenGL = false;
			
			if(isOpenGL)
				System.out.println("Detected renderer " + sketchRenderer + "; including OpenGL libraries");
			else
				System.out.println("Detected renderer " + sketchRenderer + "; leaving out OpenGL libraries");
			
			if(!running.get()) { //CHECK
				cleanUpHalt();
				return;
			}
			
			if(sketchClassName != null) {
				String[] permsList = new String[perms.length()];
				for(int i = 0; i < permsList.length; i ++)
					permsList[i] = perms.get(i);
				
				File tempManifest = new File(buildFolder, "AndroidManifest.xml");
				manifest.writeBuild(tempManifest, sketchClassName, target.equals("debug"), customManifest.get(), prettyName.get(), permsList, targetSdk.get(), orientation.get());
				
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
				}
				
				//Copy native libraries
				
				String[] libsToCopy = {"processing-core"};//, "jogl-all", "gluegen-rt", "jogl-all-natives", "gluegen-rt-natives"};
				String prefix = "libs/";
				String suffix = ".jar";
				
				//Copy for the compiler
				for(String lib : libsToCopy) {
					InputStream inputStream = am.open(prefix + lib + suffix);
					createFileFromInputStream(inputStream, new File(libsFolder, lib + suffix));
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
				
				//Copy for the compiler
				for(String lib : dexLibsToCopy) {
					InputStream inputStream = am.open(dexPrefix + lib + dexSuffix);
					createFileFromInputStream(inputStream, new File(dexedLibsFolder, lib + dexSuffix));
				}
				
				// Copy any imported libraries (their libs and assets),
				// and anything in the code folder contents to the project.
				//copyLibraries(libsFolder, assetsFolder); TODO implement libraries
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
				
				if(!running.get()) { //CHECK
					cleanUpHalt();
					return;
				}
			}
		} catch(IOException e) {
			e.printStackTrace();
		} catch(SketchException e) {
			e.printStackTrace();
			
			editor.errorExt(e.getMessage());
			editor.highlightLineExt(e.getCodeIndex(), e.getCodeLine());
			
			//Bail out
			cleanUp();
			return;
		}
		
		File aaptLoc = new File(tmpFolder, "aapt");
		
		//AAPT setup
		try {
			AssetManager am = editor.getAssets();
			
			InputStream inputStream = am.open("aapt");
			createFileFromInputStream(inputStream, aaptLoc);
			
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
				"-M", buildFolder.getAbsolutePath() + "/AndroidManifest.xml", //The location of the AndroidManifest.xml file
				"-I", androidJarLoc.getAbsolutePath(), //buildFolder.getAbsolutePath() + "/sdk/platforms/" + androidVersion + "/android.jar", //The location of the android.jar resource
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
		
		if(!running.get()) { //CHECK
			cleanUpHalt();
			return;
		}
		
		editor.messageExt(editor.getResources().getString(R.string.run_zipsigner));
		
		//Sign the APK using ZipSigner
		signApk();
		
		//TODO this writes AAPT error logs, is it necessary?
		System.out.println("AAPT logs:");
		copyStream(aaptProc.getErrorStream(), System.err);
		
		if(!running.get()) { //CHECK
			cleanUpError();
			return;
		}
		
		editor.messageExt(editor.getResources().getString(R.string.run_sketch));
		
		//Prompt the user to install the APK file
		Intent promptInstall = new Intent(Intent.ACTION_VIEW)
			.setDataAndType(Uri.parse(
				"file:///" + binFolder.getAbsolutePath() + "/" + sketchName + ".apk"), //The location of the APK
				"application/vnd.android.package-archive"
			);
		editor.startActivity(promptInstall);
		
		cleanUp();
	}
	
	//TODO implement private key signing
	private void signApk() {
		String mode = "testkey";
		String infilename = binFolder.getAbsolutePath() + "/" + sketchName + ".apk.unsigned";
		String outfilename = binFolder.getAbsolutePath() + "/" + sketchName + ".apk";
//		String provider = null;
//		String keyfilename = null;
//		String keypass = null;
//		String certfilename = null;
//		String templatefilename = null;
		
		ZipSigner signer;
//		PrivateKey privateKey = null;
//		URL privateKeyUrl, certUrl, sbtUrl;
//		X509Certificate cert = null;
//		byte[] sigBlockTemplate = null;
		
		try {
			signer = new ZipSigner();
//			if(provider!=null) signer.loadProvider(provider);
//			
//			if(keyfilename!=null) {
//				certUrl = new File( certfilename).toURI().toURL();
//				cert = signer.readPublicKey(certUrl);
//				sbtUrl = new File(templatefilename).toURI().toURL();
//				sigBlockTemplate = signer.readContentAsBytes(sbtUrl);
//				privateKeyUrl = new File(keyfilename).toURI().toURL();
//				privateKey = signer.readPrivateKey(privateKeyUrl, keypass);
//				signer.setKeys("custom", cert, privateKey, sigBlockTemplate);
//			} else {
//				signer.setKeymode(mode);
//			}
			
			signer.setKeymode(mode);
			
			signer.signZip(infilename, outfilename);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	//TODO don't need this anymore, I guess...
	public void antBuildProblems(String outPile, String errPile) throws SketchException {
		final String[] outLines = outPile.split(System.getProperty("line.separator"));
		final String[] errLines = errPile.split(System.getProperty("line.separator"));

		for(final String line : outLines) {
			final String javacPrefix = "[javac]";
			final int javacIndex = line.indexOf(javacPrefix);
			
			if(javacIndex != -1) {
				int offset = javacIndex + javacPrefix.length() + 1;
				String[] pieces = PApplet.match(line.substring(offset), "^(.+):([0-9]+):\\s+(.+)$");
				
				if(pieces != null) {
					String fileName = pieces[1];
					// remove the path from the front of the filename
					fileName = fileName.substring(fileName.lastIndexOf(File.separatorChar) + 1);
					final int lineNumber = PApplet.parseInt(pieces[2]) - 1;
					SketchException rex = placeException(pieces[3], fileName, lineNumber);
					if(rex != null)
						throw rex;
				}
			}
		}
		
		// Couldn't parse the exception, so send something generic
		SketchException skex = new SketchException("Error from inside the Android tools, check the console.");
		
		// Try to parse anything else we might know about
		for(final String line : errLines) {
			if(line.contains("Unable to resolve target '" + Manifest.MIN_SDK + "'")) {
				System.err.println("Use the Android SDK Manager (under the Android");
				System.err.println("menu) to install the SDK platform and ");
				System.err.println("Google APIs for Android " + Manifest.MIN_SDK +
						" (API " + Manifest.MIN_SDK + ")");
				skex = new SketchException("Please install the SDK platform and " +
						"Google APIs for API " + Manifest.MIN_SDK);
			}
		}
		// Stack trace is not relevant, just the message.
		skex.hideStackTrace();
		throw skex;
	}
	
	public SketchException placeException(String message, String dotJavaFilename, int dotJavaLine) {
		int codeIndex = 0;
		int codeLine = -1;
		
		// first check to see if it's a .java file
		for(int i = 0; i < tabs.length; i++) {
			FileMeta meta = tabs[i];
			
			if(meta.getSuffix().equals("java")) {
				if(dotJavaFilename.equals(meta.getFilename())) {
					codeIndex = i;
					codeLine = dotJavaLine;
					return new SketchException(message, codeIndex, codeLine);
				}
			}
		}

		// If not the preprocessed file at this point, then need to get out
		if(!dotJavaFilename.equals(sketchName + ".java"))
			return null;

		// if it's not a .java file, codeIndex will still be 0
		// this section searches through the list of .pde files
		codeIndex = 0;
		for(int i = 0; i < tabs.length; i++) {
			FileMeta meta = tabs[i];

			if(meta.getSuffix().equals("pde")) {
				if(meta.getPreprocOffset() <= dotJavaLine) {
					codeIndex = i;
					codeLine = dotJavaLine - meta.getPreprocOffset();
				}
			}
		}
		// could not find a proper line number, so deal with this differently.
		// but if it was in fact the .java file we're looking for, though,
		// send the error message through.
		// this is necessary because 'import' statements will be at a line
		// that has a lower number than the preproc offset, for instance.
		return new SketchException(message, codeIndex, codeLine, -1, false); // changed for 0194 for compile errors, but...
	}
	
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
		
		// 1. concatenate all .pde files to the 'main' pde
		// store line number for starting point of each code bit
		
		StringBuffer bigCode = new StringBuffer();
		int bigCount = 0;
		for(FileMeta meta : tabs) {
			if(meta.getSuffix().equals(".pde")) {
				meta.setPreprocOffset(bigCount);
				bigCode.append(meta.getText());
				bigCode.append('\n');
				bigCount += meta.getText().split("\n").length;
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

//		ArrayList<Library> importedLibraries = new ArrayList<Library>(); //TODO implement libraries
//		Library core = mode.getCoreLibrary();
//		if (core != null) {
//			importedLibraries.add(core);
//			classPath += core.getClassPath();
//		}
//		
//		for (String item : result.extraImports) {
//			// remove things up to the last dot
//			int dot = item.lastIndexOf('.');
//			// http://dev.processing.org/bugs/show_bug.cgi?id=1145
//			String entry = (dot == -1) ? item : item.substring(0, dot);
//			//System.out.println("library searching for " + entry);
//			Library library = mode.getLibrary(entry);
//			//System.out.println(" found " + library);
//
//			if (library != null) {
//				if (!importedLibraries.contains(library)) {
//					importedLibraries.add(library);
//					classPath += library.getClassPath();
//					javaLibraryPath += File.pathSeparator + library.getNativePath();
//				}
//			} else {
//				boolean found = false;
//				// If someone insists on unnecessarily repeating the code folder
//				// import, don't show an error for it.
//				if (codeFolderPackages != null) {
//					String itemPkg = item.substring(0, item.lastIndexOf('.'));
//					for (String pkg : codeFolderPackages) {
//						if (pkg.equals(itemPkg)) {
//							found = true;
//							break;
//						}
//					}
//				}
//				if (ignorableImport(item)) {
//					found = true;
//				}
//				if (!found) {
//					System.err.println("No library found for " + entry);
//				}
//			}
//		}
		
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

//	private void writeAntProps(final File file, String packageName) {
//		try {
//			PrintWriter writer = new PrintWriter(file);
//			writer.println("application-package=" + packageName);
//			writer.flush();
//			writer.close();
//		} catch (FileNotFoundException e) {
//			e.printStackTrace();
//		}
//	}
	
//	private void writeBuildXML(final File file, final String projectName) {
//		try {
//			final PrintWriter writer = new PrintWriter(file);
//			writer.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
//			
//			writer.println(" <project name=\"" + projectName + "\" default=\"help\">");
//	
//			writer.println(" <property file=\"local.properties\" />");
//			writer.println(" <property file=\"ant.properties\" />");
//			
//			//TODO added this to use Eclipse's compiler istead of javac
//			writer.println("<property name=\"build.compiler\" value=\"org.eclipse.jdt.core.JDTCompilerAdapter\"/>");
//			
//			writer.println(" <property environment=\"env\" />");
//			writer.println(" <condition property=\"sdk.dir\" value=\"${env.ANDROID_HOME}\">");
//			writer.println(" <isset property=\"env.ANDROID_HOME\" />");
//			writer.println(" </condition>");
//	
//			writer.println(" <loadproperties srcFile=\"project.properties\" />");
//	
//			writer.println(" <fail message=\"sdk.dir is missing. Make sure to generate local.properties using 'android update project'\" unless=\"sdk.dir\" />");
//	
//			writer.println(" <import file=\"custom_rules.xml\" optional=\"true\" />");
//	
//			writer.println(" <!-- version-tag: 1 -->"); // should this be 'custom' instead of 1?
//			writer.println(" <import file=\"${sdk.dir}/tools/ant/build.xml\" />");
//	
//			writer.println("</project>");
//			writer.flush();
//			writer.close();
//		} catch(FileNotFoundException e) {
//			e.printStackTrace();
//		}
//	}
	
//	private void writeProjectProps(final File file, String sdkVersion) {
//		try {
//			final PrintWriter writer = new PrintWriter(file);
//			writer.println("target=" + "android-" + sdkVersion);
//			writer.println();
//			// http://stackoverflow.com/questions/4821043/includeantruntime-was-not-set-for-android-ant-script
//			writer.println("# Suppress the javac task warnings about \"includeAntRuntime\"");
//			writer.println("build.sysclasspath=last");
//			writer.flush();
//			writer.close();
//		} catch(FileNotFoundException e) {
//			e.printStackTrace();
//		}
//	}
	
//	private void writeLocalProps(final File file) {
//		try {
//			final PrintWriter writer = new PrintWriter(file);
//			
//			File destSdk = new File(getBuildFolder(), "sdk");
//			AssetManager am = editor.getAssets();
//			//InputStream inputStream = am.open("sdk");
//			//createFileFromInputStream(inputStream, destSdk);
//			//createFolderFromInputStream(inputStream, destSdk);
//			copyAssetFolder(am, "sdk", destSdk.getAbsolutePath());
//			
//			final String sdkPath = destSdk.getAbsolutePath(); //TODO is this considered a valid SDK directory?
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
				} else {
					System.err.println("Could not create \"drawable-ldpi\" folder.");
				}
				if(buildIcon48.getParentFile().mkdirs()) {
					InputStream inputStream = am.open("icon-48.png");
					createFileFromInputStream(inputStream, buildIcon48);
				} else {
					System.err.println("Could not create \"drawable\" folder.");
				}
				if(buildIcon72.getParentFile().mkdirs()) {
					InputStream inputStream = am.open("icon-72.png");
					createFileFromInputStream(inputStream, buildIcon72);
				} else {
					System.err.println("Could not create \"drawable-hdpi\" folder.");
				}
				if(buildIcon96.getParentFile().mkdirs()) { //TODO make a properly scaled "icon-96.png" graphic - right now, it's scaled up from the 72p version
					InputStream inputStream = am.open("icon-96.png");
					createFileFromInputStream(inputStream, buildIcon96);
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
	
//	private void copyLibraries(final File libsFolder, final File assetsFolder) throws IOException { //TODO implement libraries
//		for (Library library : getImportedLibraries()) {
//			// add each item from the library folder / export list to the output
//			for (File exportFile : library.getAndroidExports()) {
//				String exportName = exportFile.getName();
//				if (!exportFile.exists()) {
//					System.err.println(exportFile.getName() +
//							" is mentioned in export.txt, but it's " +
//							"a big fat lie and does not exist.");
//				} else if (exportFile.isDirectory()) {
//					// Copy native library folders to the correct location
//					if (exportName.equals("armeabi") ||
//							exportName.equals("armeabi-v7a") ||
//							exportName.equals("x86")) {
//						Base.copyDir(exportFile, new File(libsFolder, exportName));
//					} else {
//						// Copy any other directory to the assets folder
//						Base.copyDir(exportFile, new File(assetsFolder, exportName));
//					}
//				} else if (exportName.toLowerCase().endsWith(".zip")) {
//					// As of r4 of the Android SDK, it looks like .zip files
//					// are ignored in the libs folder, so rename to .jar
//					System.err.println(".zip files are not allowed in Android libraries.");
//					System.err.println("Please rename " + exportFile.getName() + " to be a .jar file.");
//					String jarName = exportName.substring(0, exportName.length() - 4) + ".jar";
//					Base.copyFile(exportFile, new File(libsFolder, jarName));
//
//				} else if (exportName.toLowerCase().endsWith(".jar")) {
//					Base.copyFile(exportFile, new File(libsFolder, exportName));
//
//				} else {
//					Base.copyFile(exportFile, new File(assetsFolder, exportName));
//				}
//			}
//		}
//	}
	
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
		//TODO revert this
		return new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "build");
		//return new File(editor.getFilesDir(), "build");
	}
	
	public File getTempFolder() {
		return new File(editor.getFilesDir(), "tmp");
	}
	
	public File getSketchFolder() {
		return editor.getSketchLoc(sketchName);
	}
	
	public File getSketchDataFolder() {
		return new File(getSketchFolder(), "data");
	}
	
	public File getSketchCodeFolder() {
		return new File(getSketchFolder(), "code");
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