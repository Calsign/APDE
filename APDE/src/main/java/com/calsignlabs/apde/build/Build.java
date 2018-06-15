/*
 * Seriously hacked from the Processing Project... it might still be recognizable
 * Code taken from JavaBuild, AndroidBuild, Base, AndroidPreprocessor, Preprocessor, Mode, Library, probably others
 * 
 * Added some code as well, specifically: changed build sequence from ANT to ECJ and Java tools (as opposed to command line tools)
 * Also used some ideas from the Java-IDE-Droid open-source project
 */

package com.calsignlabs.apde.build;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.WallpaperManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.AssetManager;
import android.net.Uri;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.v4.content.FileProvider;
import android.view.inputmethod.InputMethodManager;

import com.android.sdklib.build.ApkBuilder;
import com.calsignlabs.apde.APDE;
import com.calsignlabs.apde.EditorActivity;
import com.calsignlabs.apde.R;
import com.calsignlabs.apde.SketchFile;
import com.calsignlabs.apde.contrib.Library;

import org.eclipse.jdt.internal.compiler.batch.Main;
import org.spongycastle.jce.provider.BouncyCastleProvider;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.security.Security;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import kellinwood.security.zipsigner.ZipSigner;
import kellinwood.security.zipsigner.optional.CustomKeySigner;
import processing.app.Preferences;
import processing.core.PApplet;
import processing.data.StringList;
import processing.mode.java.preproc.PdePreprocessor;
import processing.mode.java.preproc.PreprocessorResult;
import processing.mode.java.preproc.SurfaceInfo;

public class Build {
	public static final String PACKAGE_REGEX ="(?:^|\\s|;)package\\s+(\\S+)\\;";
	
	protected EditorActivity editor;
	
	public String sketchName;
	private SketchFile[] tabs;
	
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
	
	// Additional resources
	static private final String LAYOUT_ACTIVITY_TEMPLATE = "LayoutActivity.xml.tmpl";
	static private final String STYLES_FRAGMENT_TEMPLATE = "StylesFragment.xml.tmpl";
	static private final String STYLES_VR_TEMPLATE = "StylesVR.xml.tmpl";
	static private final String XML_WALLPAPER_TEMPLATE = "XMLWallpaper.xml.tmpl";
	static private final String STRINGS_WALLPAPER_TEMPLATE = "StringsWallpaper.xml.tmpl";
	static private final String XML_WATCHFACE_TEMPLATE = "XMLWatchFace.xml.tmpl";
	
	// Icons
	private static final String ICON_192 = "icon-192.png";
	private static final String ICON_144 = "icon-144.png";
	private static final String ICON_96 = "icon-96.png";
	private static final String ICON_72 = "icon-72.png";
	private static final String ICON_48 = "icon-48.png";
	private static final String ICON_36 = "icon-36.png";
	static final String WATCHFACE_ICON_CIRCULAR = "preview_circular.png";
	static final String WATCHFACE_ICON_RECTANGULAR = "preview_rectangular.png";
	
	// Prefer the higher-resolution icons
	public static final String[] ICON_LIST = {ICON_192, ICON_144, ICON_96, ICON_72, ICON_48, ICON_36};
	
	private static AtomicBoolean running;
	
	private String keystore;
	private char[] keystorePassword;
	private String keyAlias;
	private char[] keyAliasPassword;
	
	private boolean injectLogBroadcaster;
	
	private static boolean verbose;
	
	private ComponentTarget appComponent;
	
	public Build(APDE global) {
		this.editor = global.getEditor();
		
		sketchName = global.getSketchName();
		tabs = editor.getTabMetas();
		
		running = new AtomicBoolean(true);
		
		injectLogBroadcaster = PreferenceManager.getDefaultSharedPreferences(global).getBoolean("inject_log_broadcaster", true);
		verbose = PreferenceManager.getDefaultSharedPreferences(global).getBoolean("build_output_verbose", false);
	}
	
	public ComponentTarget getAppComponent() {
		return appComponent;
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
		editor.errorExt(editor.getResources().getString(R.string.build_message_failed));
	}
	
	private void cleanUpHalt() {
		cleanUp();
		editor.messageExt(editor.getResources().getString(R.string.build_message_stopped_early));
	}
	
	private void cleanUp() {
		//Anything we need to clean up now...
	}
	
	public static void cleanUpPostLaunch(EditorActivity editor) {
		if(!PreferenceManager.getDefaultSharedPreferences(editor).getBoolean("pref_build_folder_keep", true)) {
			//Delete the build folder
			if (!deleteFile((new Build(((APDE) editor.getApplicationContext())).getBuildFolder()), editor)) {
				System.out.println(editor.getResources().getString(R.string.build_delete_old_build_folder_failed));
			} else if (verbose) {
				System.out.println(editor.getResources().getString(R.string.build_delete_old_build_folder_success));
			}
		}
		
		if(PreferenceManager.getDefaultSharedPreferences(editor).getBoolean("pref_build_internal_storage", true)) {
			//If we built on the internal storage, we need to delete the copied APK file
			//This is so that the package installer can still see it
			
			File destApkFile = new File(editor.getFilesDir(), ((APDE) editor.getApplicationContext()).getSketchName() + ".apk");
			if (!destApkFile.delete()) {
				System.out.println(editor.getResources().getString(R.string.build_delete_old_apk_failed));
			} else if (verbose) {
				System.out.println(editor.getResources().getString(R.string.build_delete_old_apk_success));
			}
		}
	}
	
	public static void launchSketchPostLaunch(EditorActivity editor) {
		String packageName = editor.getGlobalState().getSketchPackageName();
		Intent intent = editor.getPackageManager().getLaunchIntentForPackage(packageName);
		
		if (intent == null) {
			System.err.println(editor.getResources().getString(R.string.build_launch_sketch_activity_failure));
			return;
		}
		
		if (android.os.Build.VERSION.SDK_INT >= 24) {
			intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT);
		} else {
			intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		}
		
		try {
			editor.startActivity(intent);
		} catch (ActivityNotFoundException e) {
			e.printStackTrace();
			System.err.println(editor.getResources().getString(R.string.build_launch_sketch_activity_failure));
		}
	}
	
	public static void setWallpaperPostLaunch(EditorActivity editor) {
		Intent intent = new Intent();
		
		if (android.os.Build.VERSION.SDK_INT >= 16) {
			intent.setAction(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER);
			String packageName = editor.getGlobalState().getSketchPackageName();
			String canonicalName = packageName + "." + "MainService";
			intent.putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, new ComponentName(packageName, canonicalName));
		} else {
			intent.setAction(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER);
		}
		
		editor.startActivity(intent);
	}
	
	//Recursive file deletion
    public static boolean deleteFile(File f, Context context) {
		if (f.isDirectory()) {
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
			return false;
		}
	
		return true;
	}
	
	public static File getAndroidJarLoc(Context context) {
		return new File(getTempFolder(context), "android.jar");
	}
    
	public static void copyAndroidJar(Context context) throws IOException {
		InputStream is = context.getAssets().open("android.jar");
		createFileFromInputStream(is, getAndroidJarLoc(context));
		is.close();
	}
	
	/**
	 * @param target either "release" or "debug"
	 */
	@SuppressLint("WorldReadableFiles")
	@SuppressWarnings("deprecation")
	public void build(String target, ComponentTarget comp) {
		boolean debug = target.equals("debug");
		appComponent = comp;
		
		running.set(true);
		
		//Throughout this function, perform periodic checks to see if the user has cancelled the build
		
		editor.messageExt(editor.getResources().getString(R.string.build_message_begin));
		System.out.println(editor.getResources().getString(R.string.build_initializing));
		
		if (verbose) {
			System.out.println(String.format(Locale.US, editor.getResources().getString(R.string.build_target_release_debug), target));
		}
		
		if (debug && getAppComponent() == ComponentTarget.WATCHFACE) {
			// We want to test to make sure that a watch is available
			// If it is, then all good
			// If not, then stop the build and tell the user that something is wrong
			// Note: We are testing specifically for the presence of the APDE Wear Companion
			
			WearableUtil.checkWatchAvailable(editor, new WearableUtil.ResultCallback() {
				@Override
				public void success() {
					if (verbose) {
						System.out.println(editor.getResources().getString(R.string.watchface_check_available_success));
					}
				}
				
				@Override
				public void failure() {
					halt();
					
					editor.runOnUiThread(new Runnable() {
						@Override
						public void run() {
							AlertDialog.Builder builder = new AlertDialog.Builder(editor);
							
							builder.setTitle(R.string.watchface_watch_disconnected_dialog_title);
							builder.setMessage(R.string.watchface_watch_disconnected_dialog_message);
							
							builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
								@Override
								public void onClick(DialogInterface dialogInterface, int i) {}
							});
							
							builder.show();
							
							System.err.println();
							System.err.println(editor.getResources().getString(R.string.watchface_check_available_failure));
							System.err.println();
						}
					});
				}
			});
		}
		
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
		
		tmpFolder = getTempFolder(editor);
		
//		buildFile = new File(buildFolder, "build.xml");
		
		//Wipe the old build folder
		if(buildFolder.exists()) {
			if (deleteFile(buildFolder, editor)) {
				System.out.println(editor.getResources().getString(R.string.build_delete_old_build_folder_success));
			} else if (verbose) {
				System.out.println(editor.getResources().getString(R.string.build_delete_old_build_folder_failed));
			}
		}
		
		buildFolder.mkdir();
		srcFolder.mkdir();
		libsFolder.mkdir();
		assetsFolder.mkdir();
		binFolder.mkdir();
		dexedLibsFolder.mkdir();
		
		if (!tmpFolder.exists()) {
			tmpFolder.mkdir();
		}
		
		//Make sure we have the latest version of the libraries folder
		((APDE) editor.getApplicationContext()).rebuildLibraryList();
		
		Manifest manifest = null;
		String sketchClassName = null;
		
		editor.messageExt(editor.getResources().getString(R.string.build_message_gen_project));
		
		if(!running.get()) { //CHECK
			cleanUpHalt();
			return;
		}
		
		//Used to determine whether or not to build with ALL of the OpenGL libraries...
		//...it takes a lot longer to run DEX if they're included
//		boolean isOpenGL = false;
		//File glLibLoc = new File(binFolder, "libs.dex");
		
		File androidJarLoc = getAndroidJarLoc(editor);
		
		try {
			// Generate a new manifest and populate it with sketch properties
			manifest = new Manifest(this);
			manifest.initBlank();
			manifest.loadProperties(editor.getGlobalState().getProperties(), sketchName); // unsure whether to use sketchName or sketchClassName here
			
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
			
			if (verbose) {
				System.out.println(editor.getResources().getString(R.string.build_preprocessing));
			}
			
			Preproc preproc = new Preproc(sketchName, packageName);
			
			//Combine all of the tabs to check for size
			String combinedText = "";
			for(SketchFile tab : tabs)
				combinedText += tab.getText();
			SurfaceInfo surfaceInfo = preproc.initSketchSize(combinedText, editor, getAppComponent());
			preproc.initSketchSmooth(combinedText, editor);
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
				if (verbose) {
					System.out.println(editor.getResources().getString(R.string.build_writing_manifest));
				}
				
				File tempManifest = new File(buildFolder, "AndroidManifest.xml");
				manifest.writeCopy(tempManifest, sketchClassName, getAppComponent());
				
				if(!running.get()) { //CHECK
					cleanUpHalt();
					return;
				}
				
				if (verbose) {
					System.out.println(editor.getResources().getString(R.string.build_writing_resources));
				}
				
				final File resFolder = new File(buildFolder, "res");
				writeRes(resFolder, sketchClassName);
				
				writeMainClass(srcFolder, Preproc.getRenderer(surfaceInfo), sketchClassName, manifest.getPackageName(), false, debug && injectLogBroadcaster);
				
				final File libsFolder = mkdirs(buildFolder, "libs", editor);
				final File assetsFolder = mkdirs(buildFolder, "assets", editor);
				
				AssetManager am = editor.getAssets();
				
				// Copy AppCompat res files
				
				File supportResFolder = new File(buildFolder, "support-res");
				resFolder.mkdir();
				createFolderFromZippedAssets(editor.getAssets(), "support-res.zip", supportResFolder);
				
				// We want to make these directories whether or not we're actually building for that target
				// If we don't make the directory then AAPT yells at us
				File wearableResFolder = new File(buildFolder, "support-wearable-res");
				wearableResFolder.mkdir();
				File vrResFolder = new File(buildFolder, "vr-res");
				vrResFolder.mkdir();
				
				if (getAppComponent() == ComponentTarget.WATCHFACE) {
					// Copy support-wearable res files
					createFolderFromZippedAssets(editor.getAssets(), "support-wearable-res.zip", wearableResFolder);
				}
				
				if (getAppComponent() == ComponentTarget.VR) {
					// Copy GVR res files
					createFolderFromZippedAssets(editor.getAssets(), "vr-res.zip", vrResFolder);
					
					// Copy GVR binaries
					File gvrBinaryZip = new File(buildFolder, "vr-lib.zip");
					InputStream inputStream = am.open("vr-lib.zip");
					createFileFromInputStream(inputStream, gvrBinaryZip);
				}
				
				// Copy android.jar if it hasn't been done yet
				if (!androidJarLoc.exists()) {
					if (verbose) {
						System.out.println(editor.getResources().getString(R.string.build_copying_android_jar));
					}
					
					copyAndroidJar(editor);
				}
				
				//Copy native libraries
				
				if (verbose) {
					System.out.println(editor.getResources().getString(R.string.build_copying_processing_libraries));
				}
				
				String[] libsToCopy = {
						"processing-core", "support-core-utils", "support-compat",
						"appcompat", "support-fragment",
						getAppComponent() == ComponentTarget.WATCHFACE ? "support-wearable" : "",
						getAppComponent() == ComponentTarget.WATCHFACE ? "percent" : "",
						getAppComponent() == ComponentTarget.VR ? "gvr" : "",
						getAppComponent() == ComponentTarget.VR ? "vr" : ""
				};//, "support-vector-drawable"};
				String prefix = "libs/";
				String suffix = ".jar";
				
				//Copy for the compiler
				for(String lib : libsToCopy) {
					if (lib.equals("")) continue; // pass through, lets us use ternary if in the array
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
				
				if (verbose) {
					System.out.println(editor.getResources().getString(R.string.build_copying_dexed_processing_libraries));
				}
				
				String[] dexLibsToCopy = {
						"all-lib-dex",
						/*
						 * Part of bugfix for #43. 2018-06-03.
						 *
						 * Android mode classes reference the wearable support library. This means
						 * that we get a VerifyError on 4.4 (API level 19) because that platform
						 * does not have the wearable library classes, even though these classes are
						 * never touched. So we add the dexed support library on 4.4 and below so
						 * that Android can see the classes and stop complaining.
						 *
						 * API level 20 is 4.4W (W for Wear) - uncertain whether or not these
						 * classes are present. It doesn't hurt too much to include them, so that's
						 * what we're doing.
						 */
						getAppComponent() == ComponentTarget.WATCHFACE || android.os.Build.VERSION.SDK_INT <= 20 ? "support-wearable-dex" : "",
						getAppComponent() == ComponentTarget.VR ? "vr-dex" : ""
				};
				String dexPrefix = "libs-dex/";
				String dexSuffix = ".jar";
				
//				InputStream inputStream = am.open("libs-dex/" + "processing-core-dex.jar");
//				createFileFromInputStream(inputStream, new File(dexedLibsFolder, "processing-core-dex.jar"));
				
				//Copy for the dexer
				for(String lib : dexLibsToCopy) {
					if (lib.equals("")) continue; // pass through, lets us use ternary if in the array
					InputStream inputStream = am.open(dexPrefix + lib + dexSuffix);
					createFileFromInputStream(inputStream, new File(dexedLibsFolder, lib + dexSuffix));
					inputStream.close();
				}
				
				if (verbose) {
					System.out.println(editor.getResources().getString(R.string.build_copying_contributed_libraries));
				}
				
				// Copy any imported libraries (their libs and assets),
				// and anything in the code folder contents to the project.
				copyLibraries(libsFolder, dexedLibsFolder, assetsFolder);
				copyCodeFolder(libsFolder);
				
				// Copy the dexed JARs from the code-dex folder
				copyCodeDexFolder(dexedLibsFolder);
				
				// Copy the data folder (if one exists) to the project's 'assets' folder
				final File sketchDataFolder = getSketchDataFolder();
				if(sketchDataFolder.exists()) {
					if (verbose) {
						System.out.println(editor.getResources().getString(R.string.build_copying_data_folder));
					}
					
					copyDir(sketchDataFolder, assetsFolder);
				}
				
				// Do the same for the 'res' folder.
				// http://code.google.com/p/processing/issues/detail?id=767
				final File sketchResFolder = new File(getSketchFolder(), "res");
				if(sketchResFolder.exists()) {
					if (verbose) {
						System.out.println(editor.getResources().getString(R.string.build_copying_res_folder));
					}
					
					copyDir(sketchResFolder, resFolder);
				}
				
				if(!running.get()) { //CHECK
					cleanUpHalt();
					return;
				}
				
				// Watchfaces send logs differently
				if (debug && injectLogBroadcaster && getAppComponent() != ComponentTarget.WATCHFACE) {
					// Add "LogBroadcasterInsert.java" and "APDEInternalLogBroadcasterUtil.java" to the code
					// The insert goes directly in the code
					// The util file contains the classes that are used by the insert
					
					// Read the .java file
					
					InputStream stream = am.open("log-broadcaster/APDEInternalLogBroadcasterUtil.java");
					
					int size = stream.available();
					byte[] buffer = new byte[size];
					
					stream.read(buffer);
					stream.close();
					
					String text = new String(buffer);
					
					// Add the package declaration
					text = "package " + manifest.getPackageName() + ";\n\n" + text;
					
					// Save the .java file
					saveFile(text, new File(srcFolder.getAbsolutePath(), manifest.getPackageName().replace('.', '/') + "/APDEInternalLogBroadcasterUtil.java"));
				}
				
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
		
		System.out.println(String.format(Locale.US, editor.getResources().getString(R.string.build_detected_architecture), android.os.Build.CPU_ABI));
		
		String arch = android.os.Build.CPU_ABI.substring(0, 3).toLowerCase(Locale.US);
		String aaptName;
		
		int numCores = getNumCores();
		
		if (verbose) {
			System.out.println(String.format(editor.getResources().getString(R.string.build_available_cores), numCores));
		}
		
		// Position Independent Executables (PIE) were first supported in Jelly Bean 4.1 (API level 16)
		// In Android 5.0, they are required
		// Android versions before 4.1 still need the old binary...
		boolean usePie = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN;
		
		// Get the correct AAPT binary for this processor architecture
		switch (arch) {
		case "x86":
			if (usePie) {
				aaptName = "aapt-binaries/aapt-x86-pie";
				
				if (verbose) {
					System.out.println(editor.getResources().getString(R.string.build_using_pie_aapt_binary));
				}
			} else {
				aaptName = "aapt-binaries/aapt-x86";
			}
			break;
		case "arm":
		default:
			// Default to ARM, just in case
			
			if (usePie) {
				// Disabled above pref because old aapt contains vulnerable version of libpng
				aaptName = "aapt-binaries/aapt-arm-pie";
				
				if (verbose) {
					System.out.println(editor.getResources().getString(R.string.build_using_pie_aapt_binary));
				}
			} else {
				aaptName = "aapt-binaries/aapt-arm";
			}
			break;
		}
		
		// TODO: Only re-copy AAPT if we need to
		// Note: Make sure that it gets re-copied if the user changes the pre-0.3.3 preference
		
		File aaptLoc = new File(tmpFolder, "aapt"); //Use the same name for the destination so that the hyphens aren't an issue
		
		//AAPT setup
		try {
			if (verbose) {
				System.out.println(editor.getResources().getString(R.string.build_copying_aapt));
			}
			
			AssetManager am = editor.getAssets();
			
			InputStream inputStream = am.open(aaptName);
			createFileFromInputStream(inputStream, aaptLoc);
			inputStream.close();
			
			if (verbose) {
				System.out.println(editor.getResources().getString(R.string.build_changing_aapt_execution_permissions));
			}
			
			// We don't need to use chmod!!!!!!!
			if (!aaptLoc.setExecutable(true, true)){
				System.err.println(editor.getResources().getString(R.string.build_change_aapt_execution_permissions_failed));
				
				cleanUpError();
				return;
			}
		} catch (IOException e) {
			System.out.println(editor.getResources().getString(R.string.build_change_aapt_execution_permissions_failed));
			e.printStackTrace();
		}
		
		if(!running.get()) { //CHECK
			cleanUpHalt();
			return;
		}
		
		// Let's try a different method - who needs ANT, anyway?
		
		String mainActivityLoc = manifest.getPackageName().replace(".", "/");
		
		// NOTE: make sure that all places where build folders are specfied
		// (e.g. "buildFolder") it is followed by ".getAbsolutePath()"!!!!!
		
		editor.messageExt(editor.getResources().getString(R.string.build_message_run_aapt));
		
		File glslFolder = null;
		
		if (!isBuildForClassLoader(debug)) {
			// Copy GLSL shader files
			
			// GLSL files need to be placed in the root of the APK file
			glslFolder = new File(binFolder, "glsl-processing.zip");
			
			try {
				if (verbose) {
					System.out.println(editor.getResources().getString(R.string.build_copying_glsl));
				}
				
				//Copy the zip archive
				InputStream inputStream = editor.getAssets().open("glsl-processing.zip");
				createFileFromInputStream(inputStream, glslFolder);
			} catch (IOException e) { //Uh-oh...
				System.out.println(editor.getResources().getString(R.string.build_copy_glsl_failed));
				e.printStackTrace();
				
				cleanUpError();
				return;
			}
		}
		
		if(!running.get()) { //CHECK
			cleanUpHalt();
			return;
		}
		
		System.out.println(); //Separator
		
		// Run AAPT
		try {
			System.out.println(editor.getResources().getString(R.string.build_packaging_aapt));
			
			// Create folder structure for R.java TODO why is this necessary?
			(new File(genFolder.getAbsolutePath() + "/" + mainActivityLoc + "/")).mkdirs();
			
			String[] args = {
				aaptLoc.getAbsolutePath(), // The location of AAPT
				"package", "-v", "-f", "-m", "--auto-add-overlay",
				"--no-version-vectors", // Fix crash on 4.4 due to some support library vector voodoo
				"-S", buildFolder.getAbsolutePath() + "/res/", // The location of the /res folder
				"-S", buildFolder.getAbsolutePath() + "/support-res/", // The location of the support lib res folder
				"-S", buildFolder.getAbsolutePath() + "/support-wearable-res/",
				"-S", buildFolder.getAbsolutePath() + "/vr-res/",
				"--extra-packages", (getAppComponent() == ComponentTarget.VR ? "com.google.vr.cardboard" : "android.support.v7.appcompat"), // Make AAPT generate R.java for AppCompat/GVR
				"-J", genFolder.getAbsolutePath(), // The location of the /gen folder
				"-A", assetsFolder.getAbsolutePath(), // The location of the /assets folder
				"-M", buildFolder.getAbsolutePath() + "/AndroidManifest.xml", // The location of the AndroidManifest.xml file
				"-I", androidJarLoc.getAbsolutePath(), // The location of the android.jar resource
				"-F", binFolder.getAbsolutePath() + "/" + sketchName + ".apk.res" // The location of the output .apk.res file
			};
			
			Process aaptProcess = Runtime.getRuntime().exec(args);
			
			// We have to give it an output stream for some reason
			// So give it one that ignores the data
			// We don't want to print the standard out to the console because it is WAY too much stuff
			// It even causes APDE to crash because there is too much text in the console to fit in a transaction
			// We want to show the error stream in verbose mode though because this lets us debug things
			copyStream(aaptProcess.getInputStream(), new NullOutputStream());
			copyStream(aaptProcess.getErrorStream(), verbose ? System.err : new NullOutputStream());
			
			int code = aaptProcess.waitFor();
			
			if (code != 0) {
				System.err.println(String.format(Locale.US, editor.getResources().getString(R.string.build_aapt_failed_error_code), code));
				
				cleanUpError();
				return;
			}
		} catch (IOException e) {
			//Something weird happened
			System.out.println(editor.getResources().getString(R.string.build_aapt_failed));
			e.printStackTrace();
			
			cleanUpError();
			return;
		} catch (InterruptedException e) {
			//Something even weirder happened
			System.out.println(editor.getResources().getString(R.string.build_aapt_failed));
			e.printStackTrace();

			cleanUpError();
			return;
		}
		
		if(!running.get()) { //CHECK
			cleanUpHalt();
			return;
		}
		
		editor.messageExt(editor.getResources().getString(R.string.build_message_run_ecj));
		
		//Run ECJ
		{
			System.out.println(editor.getResources().getString(R.string.build_compiling_ecj));
			
			Main main = new Main(new PrintWriter(System.out), new PrintWriter(System.err), false, null, null);
			String[] args = {
				(verbose ? "-verbose"
						: "-warn:-unusedImport"), // Disable warning for unused imports (the preprocessor gives us a lot of them, so this is just a lot of noise)
				"-extdirs", libsFolder.getAbsolutePath(), // The location of the external libraries (Processing's core.jar and others)
				"-bootclasspath", androidJarLoc.getAbsolutePath(), //buildFolder.getAbsolutePath() + "/sdk/platforms/" + androidVersion + "/android.jar", // The location of android.jar
				"-classpath", srcFolder.getAbsolutePath() // The location of the source folder
				+ ":" + genFolder.getAbsolutePath() // The location of the generated folder
				+ ":" + libsFolder.getAbsolutePath(), // The location of the library folder
				"-1.6",
				"-target", "1.6", // Target Java level
				"-proc:none", // Disable annotation processors...
				"-d", binFolder.getAbsolutePath() + "/classes/", // The location of the output folder
				srcFolder.getAbsolutePath() + "/",
				genFolder.getAbsolutePath() + "/",
			};
			
			if (verbose) {
				System.out.println(String.format(Locale.US, editor.getResources().getString(R.string.build_compiling_file), srcFolder.getAbsolutePath() + "/" + mainActivityLoc + "/" + sketchName + ".java"));
			}
			
			if(main.compile(args)) {
				System.out.println();
			} else {
				//We have some compilation errors
				System.out.println();
				System.out.println(editor.getResources().getString(R.string.build_ecj_failed));
				
				cleanUpError();
				return;
			}
		}
		
		if(!running.get()) { //CHECK
			cleanUpHalt();
			return;
		}
		
		editor.messageExt(editor.getResources().getString(R.string.build_message_run_dx));
		
		//Run DX Dexer
		try {
			System.out.println(editor.getResources().getString(R.string.build_dx_dexer));
			
			String[] args;
			
			//Yuck, this is the best way to support verbose output...
			if (verbose ) {
				args = new String[] {
						"--verbose",
						"--num-threads=" + numCores,
						"--output=" + binFolder.getAbsolutePath() + "/sketch-classes.dex", //The output location of the sketch's dexed classes
						binFolder.getAbsolutePath() + "/classes/" //add "/classes/" to get DX to work properly
				};
			} else {
				args = new String[] {
						"--num-threads=" + numCores,
						"--output=" + binFolder.getAbsolutePath() + "/sketch-classes.dex", //The output location of the sketch's dexed classes
						binFolder.getAbsolutePath() + "/classes/" //add "/classes/" to get DX to work properly
				};
			}
			
			//This is some side-stepping to avoid System.exit() calls
			
			com.androidjarjar.dx.command.dexer.Main.Arguments dexArgs = new com.androidjarjar.dx.command.dexer.Main.Arguments();
			dexArgs.parse(args);
			
			int resultCode = com.androidjarjar.dx.command.dexer.Main.run(dexArgs);
			
			if (resultCode != 0) {
				System.err.println(String.format(Locale.US, editor.getResources().getString(R.string.build_dx_dexer_failed_error_code), resultCode));
			}
		} catch(Exception e) {
			System.out.println(editor.getResources().getString(R.string.build_dx_dexer_failed));
			e.printStackTrace();
			
			cleanUpError();
			return;
		}
		
		if(!running.get()) { //CHECK
			cleanUpHalt();
			return;
		}
		
		//Run DX Merger
		try {
			System.out.println(editor.getResources().getString(R.string.build_dx_merger));
			
			File[] dexedLibs = dexedLibsFolder.listFiles(new FilenameFilter() {
				@Override
				public boolean accept(File dir, String filename) {
					return filename.endsWith("-dex.jar");
				}
			});
			
			String[] args = new String[dexedLibs.length + 2];
			args[0] = binFolder.getAbsolutePath() + "/classes.dex"; //The location of the output DEX class file
			args[1] = binFolder.getAbsolutePath() + "/sketch-classes.dex"; //The location of the sketch's dexed classes
			
			//Apparently, this tool accepts as many dex files as we want to throw at it...
			for (int i = 0; i < dexedLibs.length; i ++) {
				args[i + 2] = dexedLibs[i].getAbsolutePath();
			}
			
			com.androidjarjar.dx.merge.DexMerger.main(args);
		} catch (Exception e) {
			System.out.println(editor.getResources().getString(R.string.build_dx_merger_failed));
			e.printStackTrace();
			
			cleanUpError();
			return;
		}
		
		if(!running.get()) { //CHECK
			cleanUpHalt();
			return;
		}
		
		editor.messageExt(editor.getResources().getString(R.string.build_message_run_apkbuilder));
		
		//Run APKBuilder
		try {
			System.out.println(editor.getResources().getString(R.string.build_building_apkbuilder));
			
//			String[] args = {
//				binFolder.getAbsolutePath() + "/" + sketchName + ".apk.unsigned", //The location of the output APK file (unsigned)
//				"-u",
//				"-z", binFolder.getAbsolutePath() + "/" + sketchName + ".apk.res", //The location of the .apk.res file
//				"-f", binFolder.getAbsolutePath() + "/classes.dex", //The location of the DEX class file
//				"-z", glslFolder.getAbsolutePath(), //Location of GLSL files
//				"-rf", srcFolder.getAbsolutePath() //The location of the source folder
//			};
//			
//			com.android.sdklib.build.ApkBuilderMain.main(args);
			
			// Create the builder with the basic files
			ApkBuilder builder = new ApkBuilder(new File(binFolder.getAbsolutePath() + "/" + sketchName + ".apk.unsigned"), //The location of the output APK file (unsigned)
					new File(binFolder.getAbsolutePath() + "/" + sketchName + ".apk.res"), //The location of the .apk.res file
					new File(binFolder.getAbsolutePath() + "/classes.dex"), //The location of the DEX class file
					null, (verbose ? System.out : null) //Only specify an output stream if we want verbose output
			);
			
			// Add everything else
			
			// Leave them out if we are class loading
			if (!isBuildForClassLoader(debug) && glslFolder != null) {
				builder.addZipFile(glslFolder); // Location of GLSL files
			}
			builder.addSourceFolder(srcFolder); // The location of the source folder
			
			if (getAppComponent() == ComponentTarget.VR) {
				// Add GVR binary libs
				builder.addZipFile(new File(buildFolder, "vr-lib.zip"));
			}
			
			// Seal the APK
			builder.sealApk();
		} catch(Exception e) {
			System.out.println(editor.getResources().getString(R.string.build_building_apkbuilder_failed));
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
		
		editor.messageExt(editor.getResources().getString(debug ? R.string.build_message_run_zipsigner_debug : R.string.build_message_run_zipsigner_export));
		
		System.out.println(); //Separator
		
		if (debug) {
			System.out.println(editor.getResources().getString(R.string.build_zipsigner));
			
			//Sign the APK using ZipSigner
			signApk();
		} else {
			System.out.println(editor.getResources().getString(R.string.build_signing_private_key));
			
			File outputBinFolder = new File((editor.getGlobalState().isExample() || editor.getGlobalState().isTemp()) ? editor.getGlobalState().getSketchbookFolder() : editor.getGlobalState().getSketchLocation(), "bin");
			String outFilename = outputBinFolder.getAbsolutePath() + "/" + sketchName + ".apk";
			
			//We want to sign for release!!!
			signApkRelease(outFilename);
			
			System.out.println(String.format(Locale.US, editor.getResources().getString(R.string.build_exported_to), outFilename));
			editor.messageExt(editor.getResources().getString(R.string.export_signed_package_success));
			
			cleanUp();
			return;
		}
		
		//TODO this writes AAPT error logs, is it necessary?
//		System.out.println("AAPT logs:");
//		copyStream(aaptProc.getErrorStream(), System.err);
		
		if(!running.get()) { //CHECK
			cleanUpError();
			return;
		}
		
		if (getAppComponent() == ComponentTarget.WATCHFACE) {
			editor.messageExt(editor.getResources().getString(R.string.build_message_send_to_watch));
			System.out.println(editor.getResources().getString(R.string.build_sending_apk_to_watch));
		} else {
			editor.messageExt(editor.getResources().getString(R.string.build_message_run_sketch));
			System.out.println(editor.getResources().getString(R.string.build_installing_apk));
		}
		
		
		//Copy the APK file to a new (and hopefully readable) location
		
		String apkName = sketchName + ".apk";
		File apkFile = new File(binFolder.getAbsolutePath() + "/" + apkName);
		
		// We only need to copy the APK file if we are building on the internal storage
		if (PreferenceManager.getDefaultSharedPreferences(editor).getBoolean("pref_build_internal_storage", true)) {
			try {
				File destApkFile;
				
				if (android.os.Build.VERSION.SDK_INT >= 24) {
					File apksDir = new File(editor.getFilesDir(), "apks");
					apksDir.mkdir();
					destApkFile = new File(apksDir, apkName);
					
					copyFile(apkFile, destApkFile);
				} else {
					destApkFile = new File(editor.getFilesDir(), apkName);
					
					// Still have to use MODE_WORLD_READABLE...
					copyFileToOutputStream(apkFile, editor.openFileOutput(apkName, Context.MODE_WORLD_READABLE));
				}
				
				apkFile = destApkFile;
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		if (getAppComponent() == ComponentTarget.WATCHFACE) {
			// Send the watchface to the watch
			WearableUtil.sendApkToWatch(editor, apkFile, new WearableUtil.ResultCallback() {
				@Override
				public void success() {
					if (verbose) {
						System.out.println(editor.getResources().getString(R.string.watchface_push_success));
					}
					
					// Make some space in the console
					for (int i = 0; i < 10; i++) {
						System.out.println("");
					}
				}
				
				@Override
				public void failure() {
					System.err.println(editor.getResources().getString(R.string.watchface_push_failure));
				}
			});
		} else {
			// Prompt the user to install the APK file
			Intent promptInstall;
			
			if (android.os.Build.VERSION.SDK_INT >= 24) {
				// Need to use FileProvider
				Uri apkUri = FileProvider.getUriForFile(editor, "com.calsignlabs.apde.fileprovider", apkFile);
				promptInstall = new Intent(Intent.ACTION_INSTALL_PACKAGE).setData(apkUri).setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
			} else {
				// The package manager doesn't seem to like FileProvider...
				promptInstall = new Intent(Intent.ACTION_VIEW).setDataAndType(Uri.fromFile(apkFile), "application/vnd.android.package-archive");
			}
			
			// Get result from installation so that we can launch the sketch afterward
			promptInstall.putExtra(Intent.EXTRA_RETURN_RESULT, true);
			
			if (injectLogBroadcaster) {
				// Make some space in the console
				for (int i = 0; i < 10; i++) {
					System.out.println("");
				}
			}
			
			// Hide the keyboard just before opening the installer dialog so that it doesn't
			// obscure the "Install" button
			InputMethodManager imm = (InputMethodManager) editor.getSystemService(Context.INPUT_METHOD_SERVICE);
			imm.hideSoftInputFromWindow(editor.findViewById(R.id.content).getWindowToken(), 0);
			
			//Get a result so that we can delete the APK file
			editor.startActivityForResult(promptInstall, getAppComponent() == ComponentTarget.WALLPAPER ? EditorActivity.FLAG_SET_WALLPAPER : EditorActivity.FLAG_LAUNCH_SKETCH);
		}
		
		cleanUp();
	}
	
	private boolean isBuildForClassLoader(boolean debug) {
		return getAppComponent() == ComponentTarget.WATCHFACE && debug;
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
	
	private void signApkRelease(String outputFilename) {
		Security.addProvider(new BouncyCastleProvider());
		
		String inFilename = binFolder.getAbsolutePath() + "/" + sketchName + ".apk.unsigned";
		
		ZipSigner signer;
		
		try {
			signer = new ZipSigner();
			
//			signer.signZip(new URL("file://" + keystore), "bks", keystorePassword, keyAlias, keyAliasPassword, "SHA1WITHRSA", inFilename, outFilename);
			//Let's take advantage of ZipSigner's ability to load JKS keystores as well
			CustomKeySigner.signZip(signer, keystore, keystorePassword, keyAlias, keyAliasPassword, "SHA1WITHRSA", inFilename, outputFilename);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Gets the number of cores available in this device, across all processors.
	 * Requires: Ability to peruse the filesystem at "/sys/devices/system/cpu"
	 * 
	 * From StackOverflow: http://stackoverflow.com/a/10377934
	 * 
	 * @return The number of cores, or Runtime.availableProcessors() if failed to get result
	 */
	private int getNumCores() {
		//Private Class to display only CPU devices in the directory listing
		class CpuFilter implements FileFilter {
			@Override
			public boolean accept(File pathname) {
				//Check if filename is "cpu", followed by a single digit number
				if (Pattern.matches("cpu[0-9]+", pathname.getName())) {
					return true;
				}
				return false;
			}
		}
		
		try {
			//Get directory containing CPU info
			File dir = new File("/sys/devices/system/cpu/");
			//Filter to only list the devices we care about
			File[] files = dir.listFiles(new CpuFilter());
			//Return the number of cores (virtual CPU devices)
			return files.length;
		} catch (Exception e) {
			//Default to return 1 core
			return Runtime.getRuntime().availableProcessors();
		}
	}
	
	protected String removeSize(String code) {
		final String left = "(?:^|\\s|;)";
		final String right = "\\s*\\(([^\\)]*)\\)\\s*;";
		String regexp = left + "size" + right;
		Pattern p = Pattern.compile(regexp, Pattern.MULTILINE | Pattern.DOTALL);
		Matcher m = p.matcher(code);
		if (m.find()) {
			return code.substring(0, m.start()) + code.substring(m.end());
		} else {
			return code;
		}
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
			codeFolderPackages = packageListFromClassPath(codeFolderClassPath, editor);
		}
		
		// 1. concatenate all .pde files to the 'main' pde
		// store line number for starting point of each code bit
		
		StringBuilder bigCode = new StringBuilder();
		int bigCount = 0;
		for(SketchFile meta : tabs) {
			if(meta.getSuffix().equals(".pde")) {
				meta.setPreprocOffset(bigCount);
				bigCode.append(meta.getText());
				bigCode.append("\n");
				bigCount += numLines(meta.getText());
			}
		}
		
		String combinedCode = removeSize(bigCode.toString());
		
		PreprocessorResult result;
		try {
			File outputFolder = (packageName == null) ? srcFolder : new File(srcFolder, packageName.replace('.', '/'));
			outputFolder.mkdirs();
			final File java = new File(outputFolder, sketchName + ".java");
			final PrintWriter stream = new PrintWriter(new FileWriter(java));
			try {
				result = preprocessor.write(stream, combinedCode, codeFolderPackages == null ? null : new StringList(codeFolderPackages));
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
				throw new SketchException(editor.getResources().getString(R.string.preproc_mismatched_braces),
						errorFile, errorLine, re.getColumn(), false);
			}
			
			if (msg.contains("expecting LCURLY")) {
				System.err.println(msg);
				String[] m = PApplet.match(msg, "found ('.*')");
				
				throw new SketchException(m != null
						? String.format(Locale.US, editor.getResources().getString(R.string.preproc_missing_left_brace_not), m[1])
						: editor.getResources().getString(R.string.preproc_missing_left_brace),
						errorFile, errorLine, re.getColumn(), false);
			}
			
			if (msg.indexOf("expecting RBRACK") != -1) {
				System.err.println(msg);
				throw new SketchException(editor.getResources().getString(R.string.preproc_missing_right_bracket),
						errorFile, errorLine, re.getColumn(), false);
			}
			
			if (msg.indexOf("expecting SEMI") != -1) {
				System.err.println(msg);
				throw new SketchException(editor.getResources().getString(R.string.preproc_missing_semi),
						errorFile, errorLine, re.getColumn(), false);
			}
			
			if (msg.indexOf("expecting RPAREN") != -1) {
				System.err.println(msg);
				throw new SketchException(editor.getResources().getString(R.string.preproc_missing_right_paren),
						errorFile, errorLine, re.getColumn(), false);
			}
			
			if (msg.indexOf("preproc.web_colors") != -1) {
				throw new SketchException(editor.getResources().getString(R.string.preproc_bad_web_color),
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
					SketchFile meta = tabs[i];
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
			System.err.println(String.format(Locale.US, editor.getString(R.string.preproc_uncaught_exception), ex.getClass()));
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
				if(ignorableImport(item, getAppComponent())) {
					found = true;
				}
				if(!found) {
					System.err.println(String.format(Locale.US, editor.getResources().getString(R.string.build_library_import_missing), entry));
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

		for(SketchFile meta : tabs) {
			if(meta.getSuffix().equals(".java")) {
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
						meta.writeData(srcFolder.getAbsolutePath() + File.separator + filename); //TODO does this actually do what we want?
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
					throw new SketchException(String.format(Locale.US, editor.getResources().getString(R.string.preproc_problem_moving_file), filename));
				}

			} else if (meta.getSuffix().equals("pde")) {
				// The compiler and runner will need this to have a proper offset
				meta.addPreprocOffset(result.headerOffset);
			}
		}
		foundMain = preprocessor.hasMethod("main");
		return result.className;
	}
	
	//These RegExes are borrowed from Processing's preprocessor
	
	private static final Pattern PUBLIC_CLASS =
			Pattern.compile("(^|;)\\s*public\\s+class\\s+\\S+\\s+extends\\s+PApplet", Pattern.MULTILINE);
	
	private static final Pattern FUNCTION_DECL =
			Pattern.compile("(^|;)\\s*((public|private|protected|final|static)\\s+)*" +
					"(void|int|float|double|String|char|byte)" +
					"(\\s*\\[\\s*\\])?\\s+[a-zA-Z0-9]+\\s*\\(",
					Pattern.MULTILINE);
	
	/**
	 * Determine whether the code uses active or static mode.
	 * 
	 * @param code
	 * @return
	 */
	private boolean active(CharSequence code) {
		String uncomment = PdePreprocessor.scrubComments(code.toString());
		
		return PUBLIC_CLASS.matcher(uncomment).find() || FUNCTION_DECL.matcher(uncomment).find();
	}
	
	private int numLines(String input) {
		int count = 1;
		
		for(int i = 0; i < input.length(); i ++)
			if(input.charAt(i) == '\n')
				count ++;
		
		return count;
	}
	
	protected boolean ignorableImport(String pkg, ComponentTarget comp) {
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
		
		// We import the VR library by default when using the VR target
		if (pkg.startsWith("processing.vr.") && comp == ComponentTarget.VR) return true;
		
		return false;
	}
	
	private Library getLibrary(String pkgName) throws SketchException {
		ArrayList<Library> libraries = ((APDE) editor.getApplicationContext()).getImportToLibraryTable().get(pkgName);
		if (libraries == null) {
			return null;
		} else if (libraries.size() > 1) { //This is if there are multiple libraries with the same package name... but when does this ever happen?
//			String primary = "More than one library is competing for this sketch.\n";
//			String secondary = "The import " + pkgName + " points to multiple libraries:\n";
//			for (Library library : libraries) {
//				String location = library.getLibraryFolder((APDE) editor.getApplicationContext()).getAbsolutePath();
////				if (location.startsWith(getLibrariesFolder().getAbsolutePath())) { //Android mode has no core libraries - but we'll leave this just in case
////					location = "part of Processing";
////				}
//				secondary += library.getName() + " (" + location + ")\n";
//			}
//			secondary += "Extra libraries need to be removed before this sketch can be used.";
//			System.err.println("Duplicate Library Problem\n\n" + primary + secondary);
//			throw new SketchException("Duplicate libraries found for " + pkgName + ".");
			StringBuilder libraryConflicts = new StringBuilder();
			for (Library library : libraries) {
				libraryConflicts.append(library.getName());
				libraryConflicts.append(" (");
				libraryConflicts.append(library.getLibraryFolder((APDE) editor.getApplicationContext()).getAbsolutePath());
				libraryConflicts.append(")\n");
			}
			System.err.println(String.format(Locale.US, editor.getResources().getString(R.string.build_duplicate_libraries_message), pkgName, libraryConflicts.toString()));
			throw new SketchException(String.format(Locale.US, editor.getResources().getString(R.string.build_duplicate_libraries), pkgName));
		} else {
			return libraries.get(0);
		}
	}
	
	protected int findErrorFile(int errorLine) {
		for (int i = tabs.length - 1; i > 0; i --) {
			SketchFile meta = tabs[i];
			if (meta.getSuffix().equals(".pde") && (meta.getPreprocOffset() <= errorLine)) {
				// keep looping until the errorLine is past the offset
				return i;
			}
		}
		return 0; // i give up
	}
	
	private void writeRes(File resFolder, String className) throws SketchException {
		File layoutFolder = mkdirs(resFolder, "layout", editor);
		writeResLayoutMainActivity(layoutFolder, className);
		
		ComponentTarget comp = getAppComponent();
		if (comp == ComponentTarget.APP) {
			File valuesFolder = mkdirs(resFolder, "values", editor);
			writeResStylesFragment(valuesFolder);
		}
		
		if (comp == ComponentTarget.WALLPAPER) {
			File xmlFolder = mkdirs(resFolder, "xml", editor);
			writeResXMLWallpaper(xmlFolder);
			
			File valuesFolder = mkdirs(resFolder, "values", editor);
			writeResStringsWallpaper(valuesFolder, className);
		}
		
		if (comp == ComponentTarget.WATCHFACE) {
			File xmlFolder = mkdirs(resFolder, "xml", editor);
			writeResXMLWatchFace(xmlFolder);
		}
		
		if (comp == ComponentTarget.VR) {
			File valuesFolder = mkdirs(resFolder, "values", editor);
			writeResStylesVR(valuesFolder);
		}
		
		File sketchFolder = getSketchFolder();
		writeIconFiles(sketchFolder, resFolder);
	}
	
	private void writeIconFiles(File sketchFolder, File resFolder) {
		AssetManager am = editor.getAssets();
		
		File localIcon36 = new File(sketchFolder, ICON_36);
		File localIcon48 = new File(sketchFolder, ICON_48);
		File localIcon72 = new File(sketchFolder, ICON_72);
		File localIcon96 = new File(sketchFolder, ICON_96);
		File localIcon144 = new File(sketchFolder, ICON_144);
		File localIcon192 = new File(sketchFolder, ICON_192);
		
		File buildIcon48 = new File(resFolder, "drawable/icon.png");
		File buildIcon36 = new File(resFolder, "drawable-ldpi/icon.png");
		File buildIcon72 = new File(resFolder, "drawable-hdpi/icon.png");
		File buildIcon96 = new File(resFolder, "drawable-xhdpi/icon.png");
		File buildIcon144 = new File(resFolder, "drawable-xxhdpi/icon.png");
		File buildIcon192 = new File(resFolder, "drawable-xxxhdpi/icon.png");
		
		if (!localIcon36.exists() && !localIcon48.exists() && !localIcon72.exists() && !localIcon96.exists() && !localIcon144.exists() && !localIcon192.exists()) {
			try {
				// if no icons are in the sketch folder, then copy all the defaults
				if(buildIcon36.getParentFile().mkdirs()) {
					InputStream inputStream = am.open("icons/" + ICON_36);
					createFileFromInputStream(inputStream, buildIcon36);
					inputStream.close();
				} else {
					System.err.println(editor.getResources().getString(R.string.build_write_res_ldpi_failed));
				}
				if(buildIcon48.getParentFile().mkdirs()) {
					InputStream inputStream = am.open("icons/" + ICON_48);
					createFileFromInputStream(inputStream, buildIcon48);
					inputStream.close();
				} else {
					System.err.println(editor.getResources().getString(R.string.build_write_res_mdpi_failed));
				}
				if(buildIcon72.getParentFile().mkdirs()) {
					InputStream inputStream = am.open("icons/" + ICON_72);
					createFileFromInputStream(inputStream, buildIcon72);
					inputStream.close();
				} else {
					System.err.println(editor.getResources().getString(R.string.build_write_res_hdpi_failed));
				}
				if(buildIcon96.getParentFile().mkdirs()) {
					InputStream inputStream = am.open("icons/" + ICON_96);
					createFileFromInputStream(inputStream, buildIcon96);
					inputStream.close();
				} else {
					System.err.println(editor.getResources().getString(R.string.build_write_res_xhdpi_failed));
				}
				if(buildIcon144.getParentFile().mkdirs()) {
					InputStream inputStream = am.open("icons/" + ICON_144);
					createFileFromInputStream(inputStream, buildIcon144);
					inputStream.close();
				} else {
					System.err.println(editor.getResources().getString(R.string.build_write_res_xxhdpi_failed));
				}
				if(buildIcon192.getParentFile().mkdirs()) {
					InputStream inputStream = am.open("icons/" + ICON_192);
					createFileFromInputStream(inputStream, buildIcon192);
					inputStream.close();
				} else {
					System.err.println(editor.getResources().getString(R.string.build_write_res_xxxhdpi_failed));
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
				if (localIcon144.exists()) {
					if (new File(resFolder, "drawable-xxhdpi").mkdirs()) {
						copyFile(localIcon144, buildIcon144);
					}
				}
				if (localIcon192.exists()) {
					if (new File(resFolder, "drawable-xxxhdpi").mkdirs()) {
						copyFile(localIcon192, buildIcon192);
					}
				}
			} catch (IOException e) {
				System.err.println(editor.getResources().getString(R.string.build_write_res_icon_copy_failed));
				e.printStackTrace();
			}
		}
		
		if (getAppComponent() == ComponentTarget.WATCHFACE) {
			File localIconCircle = new File(sketchFolder, WATCHFACE_ICON_CIRCULAR);
			File localIconRect = new File(sketchFolder, WATCHFACE_ICON_RECTANGULAR);
			
			File buildIconCircle = new File(resFolder, "drawable/" + WATCHFACE_ICON_CIRCULAR);
			File buildIconRect = new File(resFolder, "drawable-ldpi/" + WATCHFACE_ICON_RECTANGULAR);
			
			try {
				if (localIconCircle.exists()) {
					if (new File(resFolder, "drawable").mkdirs()) {
						copyFile(localIconCircle, buildIconCircle);
					}
				} else {
					InputStream inputStream = am.open("icons/" + WATCHFACE_ICON_CIRCULAR);
					createFileFromInputStream(inputStream, buildIconCircle);
					inputStream.close();
				}
				
				if (localIconRect.exists()) {
					if (new File(resFolder, "drawable").mkdirs()) {
						copyFile(localIconRect, buildIconRect);
					}
				} else {
					InputStream inputStream = am.open("icons/" + WATCHFACE_ICON_RECTANGULAR);
					createFileFromInputStream(inputStream, buildIconRect);
					inputStream.close();
				}
			} catch (IOException e) {
				System.err.println(editor.getResources().getString(R.string.build_write_res_icon_copy_failed));
				e.printStackTrace();
			}
		}
	}
	
	private static boolean isOpenGL(String renderer) {
		return renderer != null && (renderer.equals("P2D") || renderer.equals("P3D") || renderer.equals("OPENGL"));
	}
	
	private void writeMainClass(final File srcDirectory, String renderer, String sketchClassName, String packageName, boolean external, boolean injectLogBroadcaster) {
		ComponentTarget comp = getAppComponent();
		if (comp == ComponentTarget.APP) {
			writeFragmentActivity(srcDirectory, sketchClassName, packageName, external, injectLogBroadcaster);
		} else if (comp == ComponentTarget.WALLPAPER) {
			writeWallpaperService(srcDirectory, sketchClassName, packageName, external, injectLogBroadcaster);
		} else if (comp == ComponentTarget.WATCHFACE) {
			if (isOpenGL(renderer)) {
				writeWatchFaceGLESService(srcDirectory, sketchClassName, packageName, external, injectLogBroadcaster);
			} else {
				writeWatchFaceCanvasService(srcDirectory, sketchClassName, packageName, external, injectLogBroadcaster);
			}
		} else if (comp == ComponentTarget.VR) {
			writeVRActivity(srcDirectory, sketchClassName, packageName, external, injectLogBroadcaster);
		}
	}
	
	private String getLogBroadcasterInsert() {
		try {
			InputStream stream = editor.getAssets().open("log-broadcaster/LogBroadcasterInsert.java");
			
			int size = stream.available();
			byte[] buffer = new byte[size];
			
			stream.read(buffer);
			stream.close();
			
			String out = new String(buffer);
			
			System.out.println(editor.getResources().getString(R.string.build_inject_log_broadcaster_success));
			
			return out;
		} catch (IOException e) {
			System.err.println(editor.getResources().getString(R.string.build_inject_log_broadcaster_failed));
			e.printStackTrace();
			
			return "";
		}
	}
	
	private void writeFragmentActivity(final File srcDirectory, String sketchClassName, String packageName, boolean external, boolean injectLogBroadcaster) {
		File javaFile = new File(new File(srcDirectory, packageName.replace(".", "/")), "MainActivity.java");
		
		HashMap<String, String> replaceMap = new HashMap<String, String>();
		replaceMap.put("@@package_name@@", packageName);
		replaceMap.put("@@sketch_class_name@@", sketchClassName);
		replaceMap.put("@@external@@", external ? "sketch.setExternal(true);" : "");
		replaceMap.put("@@log_broadcaster@@", injectLogBroadcaster ? getLogBroadcasterInsert() : "");
		
		createFileFromTemplate(getAppComponent().getMainClassTemplate(), javaFile, replaceMap, editor);
	}
	
	private void writeWallpaperService(final File srcDirectory, String sketchClassName, String packageName, boolean external, boolean injectLogBroadcaster) {
		File javaFile = new File(new File(srcDirectory, packageName.replace(".", "/")), "MainService.java");
		
		HashMap<String, String> replaceMap = new HashMap<String, String>();
		replaceMap.put("@@package_name@@", packageName);
		replaceMap.put("@@sketch_class_name@@", sketchClassName);
		replaceMap.put("@@external@@", external ? "sketch.setExternal(true);" : "");
		replaceMap.put("@@log_broadcaster@@", injectLogBroadcaster ? getLogBroadcasterInsert() : "");
		
		createFileFromTemplate(getAppComponent().getMainClassTemplate(), javaFile, replaceMap, editor);
	}
	
	private void writeWatchFaceGLESService(final File srcDirectory, String sketchClassName, String packageName, boolean external, boolean injectLogBroadcaster) {
		File javaFile = new File(new File(srcDirectory, packageName.replace(".", "/")), "MainService.java");
		
		HashMap<String, String> replaceMap = new HashMap<String, String>();
		replaceMap.put("@@watchface_classs@@", "PWatchFaceGLES");
		replaceMap.put("@@package_name@@", packageName);
		replaceMap.put("@@sketch_class_name@@", sketchClassName);
		replaceMap.put("@@external@@", external ? "sketch.setExternal(true);" : "");
		// Watchfaces send console output differently
		
		createFileFromTemplate(getAppComponent().getMainClassTemplate(), javaFile, replaceMap, editor);
	}
	
	private void writeWatchFaceCanvasService(final File srcDirectory, String sketchClassName, String packageName, boolean external, boolean injectLogBroadcaster) {
		File javaFile = new File(new File(srcDirectory, packageName.replace(".", "/")), "MainService.java");
		
		HashMap<String, String> replaceMap = new HashMap<String, String>();
		replaceMap.put("@@watchface_classs@@", "PWatchFaceCanvas");
		replaceMap.put("@@package_name@@", packageName);
		replaceMap.put("@@sketch_class_name@@", sketchClassName);
		replaceMap.put("@@external@@", external ? "sketch.setExternal(true);" : "");
		// Watchfaces send console output differently
		
		createFileFromTemplate(getAppComponent().getMainClassTemplate(), javaFile, replaceMap, editor);
	}
	
	private void writeVRActivity(final File srcDirectory, String sketchClassName, String packageName, boolean external, boolean injectLogBroadcaster) {
		File javaFile = new File(new File(srcDirectory, packageName.replace(".", "/")), "MainActivity.java");
		
		HashMap<String, String> replaceMap = new HashMap<String, String>();
		replaceMap.put("@@package_name@@", packageName);
		replaceMap.put("@@sketch_class_name@@", sketchClassName);
		replaceMap.put("@@external@@", external ? "sketch.setExternal(true);" : "");
		replaceMap.put("@@log_broadcaster@@", injectLogBroadcaster ? getLogBroadcasterInsert() : "");
		
		createFileFromTemplate(getAppComponent().getMainClassTemplate(), javaFile, replaceMap, editor);
	}
	
	private void writeResLayoutMainActivity(final File layoutFolder, String sketchClassName) {
		File xmlFile = new File(layoutFolder, "main.xml");
		
		HashMap<String, String> replaceMap = new HashMap<String, String>();
		replaceMap.put("@@sketch_class_name@@", sketchClassName);
		
		createFileFromTemplate(LAYOUT_ACTIVITY_TEMPLATE, xmlFile, replaceMap, editor);
	}
	
	private void writeResStylesFragment(final File valuesFolder) {
		File xmlFile = new File(valuesFolder, "styles.xml");
		createFileFromTemplate(STYLES_FRAGMENT_TEMPLATE, xmlFile, null, editor);
	}
	
	private void writeResStylesVR(final File valuesFolder) {
		File xmlFile = new File(valuesFolder, "styles.xml");
		createFileFromTemplate(STYLES_VR_TEMPLATE, xmlFile, null, editor);
	}
	
	private void writeResXMLWallpaper(final File xmlFolder) {
		File xmlFile = new File(xmlFolder, "wallpaper.xml");
		createFileFromTemplate(XML_WALLPAPER_TEMPLATE, xmlFile, null, editor);
	}
	
	private void writeResStringsWallpaper(final File valuesFolder, String sketchClassName) {
		File xmlFile = new File(valuesFolder, "strings.xml");
		
		HashMap<String, String> replaceMap = new HashMap<String, String>();
		replaceMap.put("@@sketch_class_name@@", sketchClassName);
		
		createFileFromTemplate(STRINGS_WALLPAPER_TEMPLATE, xmlFile, replaceMap, editor);
	}
	
	private void writeResXMLWatchFace(final File xmlFolder) {
		File xmlFile = new File(xmlFolder, "watch_face.xml");
		createFileFromTemplate(XML_WATCHFACE_TEMPLATE, xmlFile, null, editor);
	}
	
	private String generatePermissionsString(final String[] permissions) {
		String permissionsStr = "";
		for (String p: permissions) {
			permissionsStr += (0 < permissionsStr.length() ? "," : "");
			if (p.indexOf("permission") == -1) {
				permissionsStr += "Manifest.permission." + p;
			} else if (p.indexOf("Manifest.permission") == 0) {
				permissionsStr += p;
			} else {
				permissionsStr += "\"" + p + "\"";
			}
		}
		permissionsStr = "{" + permissionsStr + "}";
		return permissionsStr;
	}
	
	private void copyLibraries(final File libsFolder, final File assetsFolder) throws IOException { //TODO support native library stuffs
		for (Library library : importedLibraries) {
			//Add each item from the library folder / export list to the output
			for (File exportFile : library.getAndroidExports((APDE) editor.getApplicationContext())) {
				String exportName = exportFile.getName();
				if (!exportFile.exists()) {
					System.err.println(String.format(Locale.US, editor.getResources().getString(R.string.build_export_library_file_missing), exportFile.getName()));
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
					System.err.println(String.format(Locale.US, editor.getResources().getString(R.string.build_library_zip), exportFile.getName()));
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
					System.err.println(String.format(Locale.US, editor.getResources().getString(R.string.build_export_library_file_missing), exportFile.getName()));
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
					System.err.println(String.format(Locale.US, editor.getResources().getString(R.string.build_library_zip), exportFile.getName()));
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
	
	// https://gist.github.com/tylerchesley/6198074
	private static File createFolderFromAssets(AssetManager am, String folder, File destFile) {
		String[] assets = null;
		try {
			assets = am.list(folder);
			if (assets.length == 0) {
				createFileFromInputStream(am.open(folder), destFile);
			} else {
				if (!destFile.exists()) {
					destFile.mkdir();
				}
				
				for (String asset : assets) {
					createFolderFromAssets(am, folder + "/" + asset, new File(destFile, asset));
				}
			}
			
			return destFile;
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return null;
	}
	
	private static File createFolderFromZippedAssets(AssetManager am, String zipFile, File destFile) {
		try {
			ZipInputStream inputStream = new ZipInputStream(am.open(zipFile));
			
			ZipEntry zipEntry = null;
			while ((zipEntry = inputStream.getNextEntry()) != null) {
				File file = new File(destFile.getAbsolutePath(), zipEntry.getName());
				
				if (zipEntry.isDirectory()) {
					file.mkdirs();
				} else {
					createFileFromInputStream(inputStream, file, false);
					inputStream.closeEntry();
				}
			}
			
			inputStream.close();
			
			return destFile;
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return null;
	}
	
	private static File createFileFromInputStream(InputStream inputStream, File destFile) {
		return createFileFromInputStream(inputStream, destFile, true);
	}
	
	// http://stackoverflow.com/questions/11820142/how-to-pass-a-file-path-which-is-in-assets-folder-to-filestring-path
	private static File createFileFromInputStream(InputStream inputStream, File destFile, boolean close) {
		try {
			// Make sure that the parent folder exists
			destFile.getParentFile().mkdirs();
			
			FileOutputStream outputStream = new FileOutputStream(destFile);
			byte buffer[] = new byte[1024];
			int length = 0;
			
			while ((length = inputStream.read(buffer)) > 0) {
				outputStream.write(buffer, 0, length);
			}
			
			outputStream.close();
			if (close) {
				inputStream.close();
			}
			
			return destFile;
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return null;
	}
	
	static public File mkdirs(final File parent, final String name, Context context) {
		final File result = new File(parent, name);
		if(!(result.exists() || result.mkdirs())) {
			//throw new SketchException("Could not create " + result);
			System.out.println(String.format(Locale.US, context.getResources().getString(R.string.build_dir_create_failed), result.getAbsolutePath())); //TODO changed this around to avoid SketchException for Build Path reasons
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
	
	static public String[] packageListFromClassPath(String path, Context context) {
		Hashtable<String, Object> table = new Hashtable<String, Object>();
		String pieces[] = PApplet.split(path, File.pathSeparatorChar);
		
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
		String output[] = new String[tableCount];
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
			System.err.println(String.format(Locale.US, context.getResources().getString(R.string.build_package_from_zip_ignoring), filename, e.getMessage()));
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
	
	public static File getTempFolder(Context context) {
		return new File(context.getFilesDir(), "tmp");
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
	
	/**
	 * Create a file from the specified template using the provided replaceMap.
	 *
	 * @param template name of the template file
	 * @param dest destination file
	 * @param replaceMap map of replacements
	 * @param context context
	 */
	public static void createFileFromTemplate(String template, File dest, Map<String, String> replaceMap, Context context) {
		try {
			BufferedReader reader = new BufferedReader(new InputStreamReader(context.getAssets().open("templates/" + template)));
			PrintStream writer = new PrintStream(new FileOutputStream(dest));
			
			String line = null;
			while ((line = reader.readLine()) != null) {
				// From AndroidUtil.java in processing/processing-android
				if (line.contains("@@") && replaceMap != null) {
					StringBuilder sb = new StringBuilder(line);
					int index = 0;
					for (String key : replaceMap.keySet()) {
						String val = replaceMap.get(key);
						while ((index = sb.indexOf(key)) != -1) {
							sb.replace(index, index + key.length(), val);
						}
					}
					line = sb.toString();
				}
				
				writer.println(line);
			}
			
			reader.close();
			writer.flush();
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Takes output and does nothing with it.
	 */
	protected class NullOutputStream extends OutputStream {
		public NullOutputStream() {}
		
		@Override
		public void write(int i) throws IOException {}
	}
}