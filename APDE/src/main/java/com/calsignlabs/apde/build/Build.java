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
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.AssetManager;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.preference.PreferenceManager;
import androidx.core.content.FileProvider;
import com.calsignlabs.apde.support.documentfile.DocumentFile;

import android.view.inputmethod.InputMethodManager;

import com.android.sdklib.build.ApkBuilder;
import com.android.tools.aapt2.Aapt2Jni;
import com.calsignlabs.apde.APDE;
import com.calsignlabs.apde.BuildConfig;
import com.calsignlabs.apde.EditorActivity;
import com.calsignlabs.apde.R;
import com.calsignlabs.apde.SketchFile;
import com.calsignlabs.apde.build.dag.BuildContext;
import com.calsignlabs.apde.contrib.Library;
import com.calsignlabs.apde.support.FileSelection;
import com.calsignlabs.apde.support.InputStreamKeySigner;
import com.calsignlabs.apde.support.MaybeDocumentFile;

import org.eclipse.jdt.core.compiler.IProblem;
import org.spongycastle.jce.provider.BouncyCastleProvider;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.RandomAccessFile;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.security.Security;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import kellinwood.security.zipsigner.ZipSigner;
import processing.core.PApplet;

public class Build {
	protected EditorActivity editor;
	
	public String sketchName;
	
	private File buildFolder;
	private File srcFolder;
	private File genFolder;
	private File libsFolder;
	private File assetsFolder;
	private File binFolder;
	private File tmpFolder;
	private File dexedLibsFolder;
	
	private List<Library> importedLibraries;
	
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
	
	private Uri keystore;
	private char[] keystorePassword;
	private String keyAlias;
	private char[] keyAliasPassword;
	
	private boolean injectLogBroadcaster;
	
	private static boolean verbose;
	
	private ComponentTarget appComponent;
	
	protected List<CompilerProblem> compilerProblems;
	
	protected BuildContext buildContext;
	
	public Build(APDE global, BuildContext buildContext) {
		this.editor = global.getEditor();
		
		sketchName = global.getSketchName();
		
		running = new AtomicBoolean(true);
		
		injectLogBroadcaster = PreferenceManager.getDefaultSharedPreferences(global).getBoolean("inject_log_broadcaster", true);
		verbose = PreferenceManager.getDefaultSharedPreferences(global).getBoolean("build_output_verbose", false);
		
		compilerProblems = new ArrayList<>();
		
		this.buildContext = buildContext;
	}
	
	public ComponentTarget getAppComponent() {
		return appComponent;
	}
	
	public void setKey(Uri keystoreUri, char[] keystorePassword, String keyAlias, char[] keyAliasPassword) {
		this.keystore = keystoreUri;
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
			if (!deleteFile(getBuildFolder(editor), editor)) {
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
		String packageName;
		try {
			packageName = editor.getGlobalState().getSketchPackageName();
		} catch (MaybeDocumentFile.MaybeDocumentFileException e) {
			e.printStackTrace();
			return;
		}
		Intent intent = editor.getPackageManager().getLaunchIntentForPackage(packageName);
		
		if (intent == null) {
			System.err.println(editor.getResources().getString(R.string.build_launch_sketch_activity_failure));
			return;
		}
		
		if (shouldLaunchSplitScreen(editor.getGlobalState())) {
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
		String packageName;
		try {
			packageName = editor.getGlobalState().getSketchPackageName();
		} catch (MaybeDocumentFile.MaybeDocumentFileException e) {
			e.printStackTrace();
			return;
		}
		
		Intent intent = new Intent();
		
		intent.setAction(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER);
		String canonicalName = packageName + "." + "MainService";
		intent.putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, new ComponentName(packageName, canonicalName));
		
		editor.startActivity(intent);
	}
	
	//Recursive file deletion
    public static boolean deleteFile(File f, Context context) {
		if (f.isDirectory()) {
			for (File c : f.listFiles()) {
				deleteFile(c, context);
			}
		}
		
		boolean tryRename = false;
		
		try {
			// attempt to delete it without renaming first; this should be faster
			if (!f.delete()) {
				tryRename = true;
			}
		} catch (Exception e) {
			e.printStackTrace();
			tryRename = true;
		}
		
		if (tryRename) {
			//Renaming solution for the file system lock with EBUSY errors
			//StackOverflow: http://stackoverflow.com/questions/11539657/open-failed-ebusy-device-or-resource-busy
			final File to = new File(f.getAbsolutePath() + System.currentTimeMillis());
			if (!f.renameTo(to) || !to.delete()) {
				System.err.println(String.format(Locale.US, context.getResources().getString(R.string.delete_file_failure), f.getAbsolutePath()));
				return false;
			}
		}
		
		return true;
	}
	
	public void build(String target, ComponentTarget comp) {
		build(target, comp, false);
	}
	
	/**
	 * @param target either "release" or "debug"
	 */
	@SuppressLint("WorldReadableFiles")
	@SuppressWarnings("deprecation")
	public void build(String target, ComponentTarget comp, boolean stopAfterCompile) {
		boolean debug = target.equals("debug");
		appComponent = comp;
		
		running.set(true);
		
		// Reset compiler problems
		compilerProblems.clear();
		
		//Throughout this function, perform periodic checks to see if the user has cancelled the build
		
		if (!stopAfterCompile) {
			editor.messageExt(editor.getResources().getString(R.string.build_message_begin));
		}
		if (!stopAfterCompile || verbose) {
			System.out.println(editor.getResources().getString(R.string.build_initializing));
		}
		
		if (verbose) {
			System.out.println(String.format(Locale.US, editor.getResources().getString(R.string.build_target_release_debug), target));
		}
		
		if (debug && getAppComponent() == ComponentTarget.WATCHFACE && !stopAfterCompile) {
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
					
					editor.runOnUiThread(() -> {
						if (editor.isFinishing()) {
							return;
						}
						
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
				if (!stopAfterCompile || verbose) {
					System.out.println(editor.getResources().getString(R.string.build_delete_old_build_folder_success));
				}
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
		
		try {
			// Make sure we have the latest version of the libraries folder
			((APDE) editor.getApplicationContext()).rebuildLibraryList();
		} catch (MaybeDocumentFile.MaybeDocumentFileException e) {
			e.printStackTrace();
			cleanUpError();
			return;
		}
		
		Manifest manifest = null;
		String sketchClassName = null;
		
		if (!stopAfterCompile) {
			editor.messageExt(editor.getResources().getString(R.string.build_message_gen_project));
		}
		
		if(!running.get()) { //CHECK
			cleanUpHalt();
			return;
		}
		
		File androidJarLoc = StaticBuildResources.getAndroidJarLoc(editor);
		
		boolean customProblems = PreferenceManager.getDefaultSharedPreferences(editor).getBoolean("pref_problem_overview_enable", true);
		
		Preprocessor preprocessor = null;
		
		try {
			// Generate a new manifest and populate it with sketch properties
			manifest = new Manifest(buildContext);
			manifest.initBlank();
			manifest.loadProperties(editor.getGlobalState().getProperties(buildContext), sketchName); // unsure whether to use sketchName or sketchClassName here
			
			// We need to do this after we load the manifest because it depends on permissions
			if (getAppComponent() == ComponentTarget.PREVIEW && !stopAfterCompile) {
				// Make sure that the sketch previewer is installed
				
				Intent intent = new Intent("com.calsignlabs.apde.RUN_SKETCH_PREVIEW");
				intent.setPackage("com.calsignlabs.apde.sketchpreview");
				
				final String[] sketchPermissions = manifest.getPermissions();
				
				// These are the permissions that
				final List<String> additionalPermissions = getAdditionalRequiredPermissions(sketchPermissions);
				
				if (intent.resolveActivity(editor.getPackageManager()) == null || additionalPermissions.size() > 0) {
					// Need to install or add permissions
					
					editor.runOnUiThread(() -> {
						AlertDialog.Builder builder = new AlertDialog.Builder(editor);
						
						StringBuilder message = new StringBuilder(editor.getResources().getString(R.string.preview_sketch_previewer_install_dialog_message));
						if (additionalPermissions.size() > 0) {
							// Tell the user which new permissions are being installed
							message.append("\n\n");
							for (String permission : additionalPermissions) {
								message.append(permission);
								message.append("\n");
							}
						}
						
						builder.setTitle(R.string.preview_sketch_previewer_install_dialog_title);
						builder.setMessage(message.toString());
						
						builder.setPositiveButton(R.string.preview_sketch_previewer_install_dialog_install_button,
								(dialogInterface, i) -> editor.getGlobalState().getTaskManager()
								.launchTask("sketchPreviewBuild", false,null, false, new SketchPreviewerBuilder(editor, sketchPermissions, true, false)));
						builder.setNegativeButton(R.string.cancel, (dialogInterface, i) -> {});
						
						if (editor.isFinishing()) {
							return;
						}
						
						builder.show();
					});
					
					cleanUpHalt();
					return;
				}
			}
			
			String packageName = manifest.getPackageName();
			
			if(!running.get()) { //CHECK
				cleanUpHalt();
				return;
			}
			
			if (verbose) {
				System.out.println(editor.getResources().getString(R.string.build_preprocessing));
			}
			
			preprocessor = new Preprocessor(buildContext, this, packageName, sketchName, getCodeFolderPackages());
			preprocessor.preprocess();
			sketchClassName = sketchName; // stupid holdover, get rid of in the future
			importedLibraries = preprocessor.getImportedLibraries();
			
			// We have this disabled because we want to show the problems all at once
			// This technically lets us show preprocessor problems faster, but we will be moving to
			// synchronous build anyway, which makes that a moot point
			
//			// This will update with preprocessor problems
//			editor.showProblems();
			
			writePreprocessedFiles(editor.getSketchFiles(), preprocessor.getPreprocessedText(), packageName);
			
			if(!running.get()) { //CHECK
				cleanUpHalt();
				return;
			}
			
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
			
			writeMainClass(srcFolder, preprocessor.isOpenGL(), sketchClassName, manifest.getPackageName(), false, debug && injectLogBroadcaster);
			
			final File libsFolder = mkdirs(buildFolder, "libs", editor);
			final File assetsFolder = mkdirs(buildFolder, "assets", editor);
			
			AssetManager am = editor.getAssets();
			
			// Copy android.jar if it hasn't been done yet
			if (!androidJarLoc.exists()) {
				if (verbose) {
					System.out.println(editor.getResources().getString(R.string.build_copying_android_jar));
				}
				
				// TODO: maybe we can just copy all the static resources here instead?
				StaticBuildResources.copyAndroidJar(editor);
			}
			
			// TODO: detect static build resources not copied yet and copy them
			
			if (verbose) {
				System.out.println(editor.getResources().getString(R.string.build_copying_contributed_libraries));
			}
			
			// Copy any imported libraries (their libs and assets),
			// and anything in the code folder contents to the project.
			copyLibraries(libsFolder, dexedLibsFolder, assetsFolder);
			copyCodeFolder(getSketchCodeFolder(), libsFolder);
			// Copy the dexed JARs from the code-dex folder
			copyCodeFolder(getSketchCodeDexFolder(), dexedLibsFolder);
			
			// Copy the data folder (if one exists) to the project's 'assets' folder
			final MaybeDocumentFile sketchDataFolder = getSketchDataFolder();
			if (sketchDataFolder.exists()) {
				if (verbose) {
					System.out.println(editor.getResources().getString(R.string.build_copying_data_folder));
				}
				
				APDE.copyDocumentFile(sketchDataFolder.resolve(),
						MaybeDocumentFile.fromDirectory(assetsFolder, editor), editor.getContentResolver());
			}
			
			// Do the same for the 'res' folder.
			// http://code.google.com/p/processing/issues/detail?id=767
			final MaybeDocumentFile sketchResFolder = getSketchFolder().childDirectory("res");
			if (sketchResFolder.exists()) {
				if (verbose) {
					System.out.println(editor.getResources().getString(R.string.build_copying_res_folder));
				}
				
				APDE.copyDocumentFile(sketchResFolder.resolve(),
						MaybeDocumentFile.fromDirectory(resFolder, editor), editor.getContentResolver());
			}
			
			if(!running.get()) { //CHECK
				cleanUpHalt();
				return;
			}
			
			// Watchfaces send logs differently
			if (debug && injectLogBroadcaster && getAppComponent() != ComponentTarget.WATCHFACE) {
				// TODO switch this to a template
				
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
		} catch (IOException e) {
			e.printStackTrace();
		} catch (RuntimeException e) {
			e.printStackTrace();
			
			editor.errorExt(e.getMessage());
			
			//Bail out
			cleanUpError();
			return;
		} catch (MaybeDocumentFile.MaybeDocumentFileException e) {
			e.printStackTrace();
			cleanUpError();
			return;
		}
		
		if (!stopAfterCompile || verbose) {
			System.out.println(String.format(Locale.US, editor.getResources().getString(R.string.build_detected_architecture), android.os.Build.CPU_ABI));
		}
		
		int numCores = getNumCores();
		
		if (verbose) {
			System.out.println(String.format(editor.getResources().getString(R.string.build_available_cores), numCores));
		}
		
		if(!running.get()) { //CHECK
			cleanUpHalt();
			return;
		}
		
		// Let's try a different method - who needs ANT, anyway?
		
		String mainActivityLoc = manifest.getPackageName().replace(".", "/");
		
		// NOTE: make sure that all places where build folders are specfied
		// (e.g. "buildFolder") it is followed by ".getAbsolutePath()"!!!!!
		
		if (!stopAfterCompile) {
			editor.messageExt(editor.getResources().getString(R.string.build_message_run_aapt));
		}
		
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
				StaticBuildResources.createFileFromInputStream(inputStream, glslFolder);
			} catch (IOException e) { //Uh-oh...
				System.out.println(editor.getResources().getString(R.string.build_copy_glsl_failed));
				e.printStackTrace();
				
				cleanUpError();
				return;
			}
		}
		
		if(!running.get()) { // CHECK
			cleanUpHalt();
			return;
		}
		
		if (!stopAfterCompile || verbose) {
			System.out.println(); // Separator
		}
		
		if (!running.get()) { //CHECK
			cleanUpHalt();
			return;
		}
		
		// Run AAPT2
		try {
			if (!stopAfterCompile || verbose) {
				System.out.println(editor.getResources().getString(R.string.build_packaging_aapt));
			}
			
			// NOTE: we pre-compile the resources for the artifacts, so we only need to compile
			// the artifacts in res.
			// In theory we could add additional res dirs in the future, hence the list.
			List<File> resDirs = Collections.singletonList(new File(buildFolder, "res"));
			
			File compiledRes = new File(binFolder, "res-compiled");
			
			for (File resDir : resDirs) {
				File outputDir = new File(compiledRes, resDir.getName());
				outputDir.mkdirs();
				for (File innerDir : resDir.listFiles()) {
					for (File resFile : innerDir.listFiles()) {
						String[] args = {
								resFile.getAbsolutePath(),
								"-o", outputDir.getAbsolutePath(),
						};
						
						Aapt2Wrapper.compile(editor.getGlobalState(), Arrays.asList(args));
					}
				}
			}
			
			List<String> linkArgs = new ArrayList<>(
					Arrays.asList(
							"-o", binFolder.getAbsolutePath() + "/" + sketchName + ".apk.res",
							"--manifest", buildFolder.getAbsolutePath() + "/AndroidManifest.xml",
							"-I", androidJarLoc.getAbsolutePath(),
							"-A", assetsFolder.getAbsolutePath(),
							"--java", genFolder.getAbsolutePath(),
							"--auto-add-overlay",
							"--no-version-vectors"
					)
			);
			
			// The artifact JARs are modified to point to one common R class instead of their own.
			// This allows us to generate only two R.java files - one for the sketch and one common
			// one used by the artifacts. This speeds up compile and dex times considerably compared
			// to the other option, passing --extra-packages for each artifact.
			if (!manifest.getPackageName().equals(BuildConfig.COMMON_R_JAVA_PACKAGE)) {
				linkArgs.add("--extra-packages");
				linkArgs.add(BuildConfig.COMMON_R_JAVA_PACKAGE);
			}
			
			if (editor.getGlobalState().getPref("pref_build_generate_all_support_library_resource_classes", false)) {
				// If requested, we can still generate all of the R.java files. This is only
				// necessary if a third-party library references resources from the support library,
				// but we include it as an escape hatch because the thing we are doing by default
				// is truly quite hacky and perhaps surprising.
				
				try {
					List<String> artifactPackages = getArtifactPackages();
					
					for (String extraPackage : artifactPackages) {
						linkArgs.add("--extra-packages");
						linkArgs.add(extraPackage);
					}
				} catch (IOException e) {
					e.printStackTrace();
					cleanUpError();
					return;
				}
			}
			
			List<File> compiledResDirs = new ArrayList<>(Arrays.asList(compiledRes.listFiles()));
			for (File targetCompiledResDir : StaticBuildResources.getTargetDirs(StaticBuildResources.getResCompiledDir(editor), getAppComponent())) {
				if (targetCompiledResDir.exists()) {
					compiledResDirs.addAll(Arrays.asList(targetCompiledResDir.listFiles()));
				}
			}
		
			for (File compiledResDir : compiledResDirs) {
				for (File compiledResFile : compiledResDir.listFiles()) {
					linkArgs.add("-R");
					linkArgs.add(compiledResFile.getAbsolutePath());
				}
			}
			
			Aapt2Wrapper.link(editor.getGlobalState(), linkArgs);
			
			if (customProblems) {
				// add problems
				// they will be reported after compilation
				for (Aapt2Jni.Log log : Aapt2Jni.getLogs()) {
					compilerProblems.add(Aapt2Wrapper.buildCompilerProblem(log));
				}
			}
		} catch (Aapt2Wrapper.InvocationFailedException e) {
			System.out.println(editor.getResources().getString(R.string.build_aapt_failed));
			
			if (customProblems) {
				for (Aapt2Jni.Log log : e.logs) {
					compilerProblems.add(Aapt2Wrapper.buildCompilerProblem(log));
				}
				
				// show problems now because we aren't going to make it to compilation
				editor.showProblems(compilerProblems);
			} else {
				e.printStackTrace();
				
				for (Aapt2Jni.Log log : e.logs) {
					System.err.println(log.toString());
				}
			}
			
			cleanUpError();
			return;
		} catch (IOException | InterruptedException e) {
			// Something weird happened
			System.out.println(editor.getResources().getString(R.string.build_aapt_failed));
			e.printStackTrace();
			
			cleanUpError();
			return;
		}
		
		if(!running.get()) { //CHECK
			cleanUpHalt();
			return;
		}
		
		if (!stopAfterCompile) {
			editor.messageExt(editor.getResources().getString(R.string.build_message_run_ecj));
		}
		
		//Run ECJ
		{
			if (!stopAfterCompile || verbose) {
				System.out.println(editor.getResources().getString(R.string.build_compiling_ecj));
			}
			
			Compiler compiler = new Compiler(customProblems);
			
			List<String> args = new ArrayList<String>();
			
			if (verbose) {
				args.add("-verbose");
			} else {
				// Disable warning for unused imports (the preprocessor gives us a lot of them, so this is just a lot of noise)
				args.add("-warn:-unusedImport");
			}
			
			List<File> extDirs = new ArrayList<File>();
			
			// user libraries
			extDirs.add(libsFolder);
			
			List<File> classPathEntries = new ArrayList<>();
			classPathEntries.add(srcFolder);
			classPathEntries.add(genFolder);
			classPathEntries.add(libsFolder);
			
			// artifact libraries
			for (File targetLibsDir : StaticBuildResources.getTargetDirs(StaticBuildResources.getLibsDir(editor), getAppComponent())) {
				if (targetLibsDir.exists()) {
					extDirs.add(targetLibsDir);
					classPathEntries.add(targetLibsDir);
				}
			}
			
			args.add("-extdirs");
			args.add(joinFilesForEcj(extDirs));
			
			args.add("-bootclasspath");
			args.add(androidJarLoc.getAbsolutePath());
			
			args.add("-classpath");
			args.add(joinFilesForEcj(classPathEntries));
			
			// target java 1.6
			args.add("-1.6");
			args.add("-target");
			args.add("1.6");
			
			// disable annotation processors
			args.add("-proc:none");
			
			// where to output .class files
			args.add("-d");
			args.add(binFolder.getAbsolutePath() + "/classes/");
			
			// source code locations
			args.add(srcFolder.getAbsolutePath() + "/");
			args.add(genFolder.getAbsolutePath() + "/");
			
			if (verbose) {
				System.out.println(String.format(Locale.US, editor.getResources().getString(R.string.build_compiling_file), srcFolder.getAbsolutePath() + "/" + mainActivityLoc + "/" + sketchName + ".java"));
			}
			
			boolean success = compiler.compile(args.toArray(new String[] {})) && !preprocessor.hasSyntaxErrors();
			
			if (customProblems) {
				try {
					for (IProblem problem : compiler.getProblems()) {
						compilerProblems.add(preprocessor.buildCompilerProblem(problem));
					}
				} catch (TextTransform.LockException e) {
					e.printStackTrace();
				}
				
				editor.showProblems(compilerProblems);
			}
			
			if (success) {
				if (!stopAfterCompile || verbose) {
					System.out.println();
				}
			} else {
				//We have some compilation errors
				if (!stopAfterCompile || verbose) {
					System.out.println();
					System.out.println(editor.getResources().getString(R.string.build_ecj_failed));
				}
				
				if (customProblems) {
					// Don't show the "build failed" message, show the ECJ error message
					cleanUp();
				} else {
					cleanUpError();
				}
				return;
			}
		}
		
		if (stopAfterCompile) {
			cleanUp();
			return;
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
			if (verbose) {
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
			
			Method mtd = com.androidjarjar.dx.command.dexer.Main.Arguments.class.getDeclaredMethod("parse", String[].class);
			mtd.setAccessible(true);
			mtd.invoke(dexArgs, new Object[] {args});
			
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
		
		// NOTE: this is only dexed libs from libraries and the code folder.
		// Processing core and the support library are handled out of band.
		File[] dexedLibs = dexedLibsFolder.listFiles((dir, filename) -> filename.endsWith("-dex.jar"));
		
		// PREVIEW STOPS HERE
		
		if (getAppComponent() == ComponentTarget.PREVIEW) {
			editor.messageExt(editor.getResources().getString(R.string.build_message_preview_launch));
			System.out.println(editor.getResources().getString(R.string.build_preview_launch));
			
			launchPreview(manifest, new File(binFolder, "sketch-classes.dex"), dexedLibs);
			cleanUp();
			return;
		}
		
		//Run DX Merger
		try {
			System.out.println(editor.getResources().getString(R.string.build_dx_merger));
			
			List<String> args = new ArrayList<>();
			args.add(binFolder.getAbsolutePath() + "/classes.dex"); // where the output of merging should go
			args.add(binFolder.getAbsolutePath() + "/sketch-classes.dex"); // the sketch's dexed classes
			
			// Grab the user libraries
			for (File dexedLib : dexedLibs) {
				args.add(dexedLib.getAbsolutePath());
			}
			
			// Include the artifact libraries (processing core, support libs, etc.)
			for (File targetLibDexDir : StaticBuildResources.getTargetDirs(StaticBuildResources.getLibsDexDir(editor), getAppComponent())) {
				if (targetLibDexDir.exists()) {
					for (File libDex : targetLibDexDir.listFiles()) {
						args.add(libDex.getAbsolutePath());
					}
				}
			}
			
			com.androidjarjar.dx.merge.DexMerger.main(args.toArray(new String[] {}));
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
			
			// Add JNI libs - currently only used for VR and AR
			for (File targetJniLibsDir : StaticBuildResources.getTargetDirs(StaticBuildResources.getJniLibsDir(editor), getAppComponent())) {
				if (targetJniLibsDir.exists()) {
					for (File jniLibZip : targetJniLibsDir.listFiles()) {
						builder.addZipFile(jniLibZip);
					}
				}
			}
			
			// Seal the APK
			builder.sealApk();
		} catch(Exception e) {
			System.out.println(editor.getResources().getString(R.string.build_building_apkbuilder_failed));
			e.printStackTrace();
			
			cleanUpError();
			return;
		}
		
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
			try {
				System.out.println(editor.getResources().getString(R.string.build_signing_private_key));
				
				MaybeDocumentFile outputBinFolder;
				if (editor.getGlobalState().isExample() || editor.getGlobalState().isTemp()) {
					outputBinFolder = editor.getGlobalState().getSketchbookFolder().childDirectory("bin");
				} else {
					outputBinFolder = editor.getGlobalState().getSketchLocation().childDirectory("bin");
				}
				MaybeDocumentFile outFile = outputBinFolder.child(sketchName + ".apk",
						"application/vnd.android.package-archive");
				
				// Zipsigner doesn't support SAF. So make the file on the internal storage and
				// send then copy it over.
				File tempOutputFile = new File(binFolder, sketchName + ".apk");
				
				// We want to sign for release!!!
				signApkRelease(tempOutputFile.getAbsolutePath());
				
				APDE.copyDocumentFile(DocumentFile.fromFile(tempOutputFile), outFile,
						editor.getContentResolver());
				
				System.out.println(String.format(Locale.US,
						editor.getResources().getString(R.string.build_exported_to),
						outputBinFolder.toString()));
				editor.messageExt(editor.getResources().getString(R.string.export_signed_package_success));
				
				cleanUp();
				return;
			} catch (MaybeDocumentFile.MaybeDocumentFileException | IOException e) {
				e.printStackTrace();
				cleanUpError();
				return;
			}
		}
		
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
		
		// Copy the APK file to a new (and hopefully readable) location
		
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
			
			// Hide the keyboard just before opening the installer dialog so that it doesn't
			// obscure the "Install" button
			InputMethodManager imm = (InputMethodManager) editor.getSystemService(Context.INPUT_METHOD_SERVICE);
			imm.hideSoftInputFromWindow(editor.findViewById(R.id.content).getWindowToken(), 0);
			
			//Get a result so that we can delete the APK file
			editor.startActivityForResult(promptInstall, getAppComponent() == ComponentTarget.WALLPAPER ? EditorActivity.FLAG_SET_WALLPAPER : EditorActivity.FLAG_LAUNCH_SKETCH);
		}
		
		cleanUp();
	}
	
	private String joinFilesForEcj(List<File> files) {
		StringBuilder joined = new StringBuilder();
		for (int i = 0; i < files.size(); i++) {
			if (i != 0) {
				joined.append(':');
			}
			joined.append(files.get(i).getAbsolutePath());
		}
		return joined.toString();
	}
	
	/**
	 * Get the package names of all the artifacts for the current build target.
	 *
	 * @return the artifact package names
	 */
	private List<String> getArtifactPackages() throws IOException {
		// Package names for all artifacts
		List<String> artifactPackages = new ArrayList<>();
		// Package names we need are generated by gradle and placed in this file.
		// (See the packageNames task in gradle/assets.gradle.)
		BufferedReader reader = new BufferedReader(new InputStreamReader(
				editor.getAssets().open("artifact_package_names.txt")));
		String line;
		while ((line = reader.readLine()) != null) {
			// each line is delimited by semicolons
			String[] split = line.split(";");
			// the first item is the assetPrefix
			// we want to add the package names needed for all asset prefixes we need
			for (String assetPrefix : getAppComponent().getAssetPrefixes()) {
				if (split.length > 0 && assetPrefix.equals(split[0])) {
					// the remaining items are package names
					artifactPackages.addAll(Arrays.asList(split).subList(1, split.length));
				}
			}
		}
		return artifactPackages;
	}
	
	/**
	 * Check to see which permissions are required by the sketch but aren't included in the
	 * installed sketch previewer app.
	 *
	 * @param sketchPermissions
	 * @return
	 */
	private List<String> getAdditionalRequiredPermissions(String[] sketchPermissions) {
		String[] installedPermissions = SketchPreviewerBuilder.getInstalledPermissions(editor);
		List<String> additional = new ArrayList<String>();
		
		for (String sketchPermission : sketchPermissions) {
			if (!arrayContains(installedPermissions, sketchPermission)) {
				additional.add(sketchPermission);
			}
		}
		
		return additional;
	}
	
	private boolean arrayContains(String[] array, String test) {
		for (String s : array) {
			if (s.equals(test)) {
				return true;
			}
		}
		return false;
	}
	
	/**
	 * Run the sketch in the sketch previewer.
	 *
	 * @param manifest
	 * @param classesDex
	 * @param dexedLibs
	 */
	private void launchPreview(Manifest manifest, File classesDex, File[] dexedLibs) {
		// Stop the old sketch
		editor.sendBroadcast(new Intent("com.calsignlabs.apde.STOP_SKETCH_PREVIEW"));
		
		File dexFile = new File(editor.getFilesDir(), "sketch.dex");
		Uri dexUri;
		
		try {
			copyFile(classesDex, dexFile);
			
			Uri dataUri = null;
			
			// Zip up the assets
			// We need this if the sketch is on the internal storage because this is protected
			// But just do it for all sketches because it's easier
			
			File dataZip = new File(editor.getFilesDir().getAbsolutePath() + "/preview_sketch_data.zip");
			
			// Only make the zip if the data folder has files in it
			if (assetsFolder.isDirectory()) {
				File[] files = assetsFolder.listFiles();
				if (files != null && files.length > 0) {
					try {
						makeCompressedFile(DocumentFile.fromFile(assetsFolder), dataZip,
								editor.getContentResolver());
						dataUri = makeFileAvailableToPreview(dataZip);
					} catch (IOException e) {
						e.printStackTrace();
						System.err.println(editor.getResources().getString(R.string.build_preview_data_compress_failed));
						cleanUpError();
						return;
					}
				}
			}
			
			dexUri = makeFileAvailableToPreview(dexFile);
			
			// Delete old dexed libs
			File[] oldDexedLibs = editor.getFilesDir().listFiles();
			if (oldDexedLibs != null) {
				for (File file : oldDexedLibs) {
					if (file.getName().startsWith("preview_dexed_lib_")) {
						deleteFile(file, editor);
					}
				}
			}
			
			// We need to copy the dexed libs to the sketch previewer
			String[] libUris = new String[dexedLibs.length];
			for (int i = 0; i < dexedLibs.length; i++) {
				try {
					// We used to put the dexed libs in their own folder, but this didn't work on 4.4
					// So instead we give them all a prefix
					File dest = new File(editor.getFilesDir(), "preview_dexed_lib_" + dexedLibs[i].getName());
					copyFile(dexedLibs[i], dest);
					libUris[i] = makeFileAvailableToPreview(dest).toString();
				} catch (IOException e) {
					System.err.println(editor.getResources().getString(R.string.build_preview_dexed_lib_copy_failed));
					cleanUpError();
					return;
				}
			}
			
			// Build intent specifically for sketch previewer
			Intent intent = new Intent("com.calsignlabs.apde.RUN_SKETCH_PREVIEW");
			intent.setPackage("com.calsignlabs.apde.sketchpreview");
			intent.putExtra("SKETCH_DEX", dexUri.toString());
			intent.putExtra("SKETCH_DATA_FOLDER", dataUri == null ? "" : dataUri.toString());
			intent.putExtra("SKETCH_DEXED_LIBS", libUris);
			intent.putExtra("SKETCH_ORIENTATION", manifest.getOrientation());
			intent.putExtra("SKETCH_PACKAGE_NAME", manifest.getPackageName());
			intent.putExtra("SKETCH_CLASS_NAME", sketchName);
			
			// Launch in multi-window mode if available
			if (shouldLaunchSplitScreen(editor.getGlobalState())) {
				intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT);
			} else {
				intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			}
			if (intent.resolveActivity(editor.getPackageManager()) != null) {
				editor.startActivity(intent);
			}
		} catch (IOException e) {
			e.printStackTrace();
			cleanUpError();
		}
	}
	
	/**
	 * Use FileProvider to share the given file with the sketch previewer.
	 *
	 * @param file
	 * @return
	 */
	protected Uri makeFileAvailableToPreview(File file) {
		Uri uri;
		if (android.os.Build.VERSION.SDK_INT >= 24) {
			uri = FileProvider.getUriForFile(editor, "com.calsignlabs.apde.fileprovider", file);
			editor.grantUriPermission("com.calsignlabs.apde.sketchpreview", uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
		} else {
			if (!file.setReadable(true, false)) {
				System.err.println("failed to make file readable: " + file.getAbsolutePath());
			}
			uri = Uri.fromFile(file);
		}
		return uri;
	}
	
	/**
	 * Are we building the sketch to be loaded with classloader? (preview and watch face)
	 * Some things aren't necessary or are handled differnetly.
	 *
	 * @param debug
	 * @return
	 */
	private boolean isBuildForClassLoader(boolean debug) {
		return debug && (getAppComponent() == ComponentTarget.WATCHFACE
				|| getAppComponent() == ComponentTarget.PREVIEW);
	}
	
	public static boolean isSplitScreenAvailable() {
		return android.os.Build.VERSION.SDK_INT >= 24;
	}
	
	public static boolean shouldLaunchSplitScreen(APDE global) {
		return isSplitScreenAvailable() && global.getPref("pref_launch_sketch_split_screen", false);
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
			
			if (keystore == null) {
				System.err.println("Got a bad keystore");
				return;
			}
			
			ParcelFileDescriptor fd = FileSelection.openUri(editor, keystore, FileSelection.Mode.READ);
			if (fd == null) {
				System.err.println("Could not open Uri: " + keystore.toString());
				return;
			}
			
			// Let's take advantage of ZipSigner's ability to load JKS keystores as well
			InputStreamKeySigner.signZip(signer, FileSelection.fdIn(fd), keystorePassword, keyAlias, keyAliasPassword,
					"SHA1WITHRSA", inFilename, outputFilename);
			
			FileSelection.closeFd(fd);
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
	
	private Set<String> getCodeFolderPackages() throws MaybeDocumentFile.MaybeDocumentFileException {
		MaybeDocumentFile codeFolder = getSketchCodeFolder();
		if (codeFolder.exists()) {
			return Library.packageListFromDocumentFiles(Arrays.asList(codeFolder.resolve().listFiles()), editor);
		} else {
			return Collections.emptySet();
		}
	}
	
	@SuppressWarnings("ResultOfMethodCallIgnored")
	private int writePreprocessedFile(String code, String filename, String packageName) throws IOException {
		String packageMatch = Preprocessor.extractPackageName(code);
		int added = 0;
		if (packageMatch == null) {
			String toInsert = "package " + packageName + ";\n";
			code = toInsert + code;
			packageMatch = packageName;
			added = toInsert.length();
		}
		
		File pkgFolder = new File(srcFolder, packageMatch.replace('.', '/'));
		pkgFolder.mkdirs();
		saveFile(code, new File(pkgFolder, filename));
		
		return added;
	}
	
	private void writePreprocessedFiles(List<SketchFile> sketchFiles, CharSequence mainFile, String packageName) throws IOException {
		// Write main .java file
		writePreprocessedFile(mainFile.toString(), getSketchMainFilename(), packageName);
		
		// Write all .java files
		for (SketchFile sketchFile : sketchFiles) {
			if (sketchFile.isJava()) {
				sketchFile.javaImportHeaderOffset = writePreprocessedFile(sketchFile.getText(), sketchFile.getFilename(), packageName);
			}
		}
	}
	
	public String getSketchMainFilename() {
		return sketchName + ".java";
	}
	
	private void writeRes(File resFolder, String className) throws MaybeDocumentFile.MaybeDocumentFileException {
		File layoutFolder = mkdirs(resFolder, "layout", editor);
		writeResLayoutMainActivity(layoutFolder, className);
		
		ComponentTarget comp = getAppComponent();
		if (comp == ComponentTarget.APP || comp == ComponentTarget.PREVIEW) {
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
		
		MaybeDocumentFile sketchFolder = getSketchFolder();
		writeIconFiles(sketchFolder, resFolder);
	}
	
	private void writeIconFile(String sourceFilename, String destFilename, String directoryName,
	                           MaybeDocumentFile sketchFolder, File resFolder, boolean useOnlyUserIcons)
			throws MaybeDocumentFile.MaybeDocumentFileException {
		File destDir = new File(resFolder, directoryName);
		File dest = new File(destDir, destFilename);
		
		try {
			MaybeDocumentFile userFile = sketchFolder.child(sourceFilename, "image/png");
			
			InputStream source;
			if (userFile.exists()) {
				source = userFile.openIn(editor.getContentResolver());
			} else if (useOnlyUserIcons) {
				// we want to use only user icons, but the user hasn't provided this one.
				return;
			} else {
				source = editor.getAssets().open("icons/" + sourceFilename);
			}
			
			if (!dest.exists() && !dest.getParentFile().mkdirs()) {
				System.err.println(String.format(editor.getResources().getString(R.string.build_write_res_icon_failed), directoryName));
				return;
			}
			
			// NOTE: this function closes the stream
			StaticBuildResources.createFileFromInputStream(source, dest);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private void writeIconFiles(MaybeDocumentFile sketchFolder, File resFolder) throws MaybeDocumentFile.MaybeDocumentFileException {
		String[] iconSizes = {"36", "48", "72", "96", "144", "192"};
		String[] iconDensities = {"ldpi", "mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"};
		
		// if the user has supplied any icons, then use them
		boolean useUserIcons = false;
		for (String iconSize : iconSizes) {
			if (sketchFolder.child("icon-" + iconSize + ".png", "image/png").exists()) {
				useUserIcons = true;
				break;
			}
		}
		
		for (int i = 0; i < iconSizes.length; i++) {
			String filename = "icon-" + iconSizes[i] + ".png";
			String directoryName = "mipmap-" + iconDensities[i];
			
			writeIconFile(filename, "ic_launcher.png", directoryName, sketchFolder, resFolder, useUserIcons);
		}
		
		if (getAppComponent() == ComponentTarget.WATCHFACE) {
			writeIconFile(WATCHFACE_ICON_CIRCULAR, WATCHFACE_ICON_CIRCULAR, "drawable", sketchFolder, resFolder, false);
			writeIconFile(WATCHFACE_ICON_RECTANGULAR, WATCHFACE_ICON_RECTANGULAR, "drawable-ldpi", sketchFolder, resFolder, false);
		}
	}
	
	private void writeMainClass(final File srcDirectory, boolean isOpenGL, String sketchClassName, String packageName, boolean external, boolean injectLogBroadcaster) {
		ComponentTarget comp = getAppComponent();
		if (comp == ComponentTarget.APP || comp == ComponentTarget.PREVIEW) {
			writeFragmentActivity(srcDirectory, sketchClassName, packageName, external, injectLogBroadcaster);
		} else if (comp == ComponentTarget.WALLPAPER) {
			writeWallpaperService(srcDirectory, sketchClassName, packageName, external, injectLogBroadcaster);
		} else if (comp == ComponentTarget.WATCHFACE) {
			if (isOpenGL) {
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
			
			if (verbose) {
				System.out.println(editor.getResources().getString(R.string.build_inject_log_broadcaster_success));
			}
			
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
	
	private void copyLibraries(final File libsFolder, final File dexedLibsFolder, final File assetsFolder)
			throws IOException, MaybeDocumentFile.MaybeDocumentFileException {
		Set<String> nativeLibFolders = new HashSet<>(
				Arrays.asList("armeabi", "armeabi-v7a", "arm64", "arm64-v8a", "x86", "x86_64"));
		
		for (Library library : importedLibraries) {
			// Add each item from the library folder / export list to the output
			for (DocumentFile exportFile : library.getAndroidExports(((APDE) editor.getApplicationContext()).getLibrariesFolder())) {
				if (!exportFile.exists()) {
					System.err.println(String.format(Locale.US,
							editor.getResources().getString(R.string.build_export_library_file_missing), exportFile.getName()));
					continue;
				}
				if (exportFile.getName() == null) {
					continue;
				}
				
				String exportName = exportFile.getName();
				File dest;
				if (exportFile.isDirectory()) {
					if (nativeLibFolders.contains(exportName)) {
						// Copy native library folders to the correct location
						dest = new File(libsFolder, exportName);
					} else {
						// Copy any other directory to the assets folder
						dest = new File(assetsFolder, exportName);
					}
				} else if (exportName.toLowerCase(Locale.US).endsWith(".zip")) {
					// As of r4 of the Android SDK, it looks like .zip files
					// are ignored in the libs folder, so rename to .jar
					System.err.println(String.format(Locale.US, editor.getResources().getString(R.string.build_library_zip), exportFile.getName()));
					String jarName = exportName.substring(0, exportName.length() - 4) + ".jar";
					dest = new File(libsFolder, jarName);
				} else if (exportName.toLowerCase(Locale.US).endsWith("-dex.jar")) {
					// Handle the dexed JARs
					dest = new File(dexedLibsFolder, exportName);
				} else if (exportName.toLowerCase(Locale.US).endsWith(".jar")) {
					dest = new File(libsFolder, exportName);
				} else {
					dest = new File(assetsFolder, exportName);
				}
				
				APDE.copyDocumentFile(exportFile, MaybeDocumentFile.fromFile(dest), editor.getContentResolver());
			}
		}
	}
	
	/**
	 * Copy files from the 'code' or 'code-dex' folder into the 'libs' or 'libs-dex' folder.
	 *
	 * @param codeFolder
	 * @param libsFolder
	 * @throws IOException
	 * @throws MaybeDocumentFile.MaybeDocumentFileException
	 */
	private void copyCodeFolder(MaybeDocumentFile codeFolder, File libsFolder)
			throws IOException, MaybeDocumentFile.MaybeDocumentFileException {
		// Copy files from the 'code' directory into the 'libs' folder
		if (codeFolder != null && codeFolder.exists()) {
			for (DocumentFile item : codeFolder.resolve().listFiles()) {
				if (!item.isDirectory() && item.getName() != null) {
					String name = item.getName();
					String lcname = name.toLowerCase(Locale.US);
					if (lcname.endsWith(".jar") || lcname.endsWith(".zip")) {
						String jarName = name.substring(0, name.length() - 4) + ".jar";
						if (!libsFolder.exists()) {
							libsFolder.mkdirs();
						}
						File dest = new File(libsFolder, jarName);
						StaticBuildResources.createFileFromInputStream(
								editor.getContentResolver().openInputStream(item.getUri()), dest);
					}
				}
			}
		}
	}
	
	static public File mkdirs(final File parent, final String name, Context context) {
		final File result = new File(parent, name);
		if(!(result.exists() || result.mkdirs())) {
			System.out.println(String.format(Locale.US, context.getResources().getString(R.string.build_dir_create_failed), result.getAbsolutePath()));
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
	
	public static File getBuildFolder(Context context) {
		//Let the user pick where to build
		if(PreferenceManager.getDefaultSharedPreferences(context).getBoolean("pref_build_internal_storage", true))
			return context.getDir("build", 0);
		else
			return new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getParentFile(), "build");
	}
	
	public File getBuildFolder() {
		return getBuildFolder(editor);
	}
	
	public static File getTempFolder(Context context) {
		return new File(context.getFilesDir(), "tmp");
	}
	
	public MaybeDocumentFile getSketchFolder() throws MaybeDocumentFile.MaybeDocumentFileException {
		return ((APDE) editor.getApplication()).getSketchLocation();
	}
	
	public MaybeDocumentFile getSketchDataFolder() throws MaybeDocumentFile.MaybeDocumentFileException {
		return getSketchFolder().childDirectory("data");
	}
	
	public MaybeDocumentFile getSketchCodeFolder() throws MaybeDocumentFile.MaybeDocumentFileException {
		return getSketchFolder().childDirectory("code");
	}
	
	public MaybeDocumentFile getSketchCodeDexFolder() throws MaybeDocumentFile.MaybeDocumentFileException {
		return getSketchFolder().childDirectory("code-dex");
	}
	
	public MaybeDocumentFile getSketchBinFolder() throws MaybeDocumentFile.MaybeDocumentFileException {
		return getSketchFolder().childDirectory("bin");
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
		} catch (IOException e) {
			e.printStackTrace();
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
	public static boolean createFileFromTemplate(String template, File dest, Map<String, String> replaceMap, Context context) {
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
			
			return true;
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
	}
	
	protected static class DocumentFilePath {
		protected final String path;
		protected final DocumentFile file;
		
		protected DocumentFilePath(String path, DocumentFile file) {
			this.path = path;
			this.file = file;
		}
	}
	
	protected static void makeCompressedFile(DocumentFile folder, File compressedFile,
	                                         ContentResolver contentResolver) throws IOException {
		List<DocumentFilePath> files = new ArrayList<>();
		buildFileList(files, new DocumentFilePath("", folder));
		
		int bufferSize = 4096;
		byte[] buffer = new byte[bufferSize];
		
		int count;
		
		ZipOutputStream outputStream =
				new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(compressedFile)));
		
		for (DocumentFilePath file : files) {
			BufferedInputStream inputStream = new BufferedInputStream(
					contentResolver.openInputStream(file.file.getUri()), bufferSize);
			ZipEntry entry = new ZipEntry(file.path);
			outputStream.putNextEntry(entry);
			
			System.out.println("put zip entry: " + file.path);
			
			while ((count = inputStream.read(buffer, 0, bufferSize)) != -1) {
				outputStream.write(buffer, 0, count);
			}
			inputStream.close();
			
			outputStream.closeEntry();
		}
		
		outputStream.close();
	}
	
	protected static void buildFileList(List<DocumentFilePath> ret, DocumentFilePath dir) {
		if (dir != null && dir.file.exists()) {
			if (dir.file.isDirectory()) {
				for (DocumentFile file : dir.file.listFiles()) {
					buildFileList(ret, new DocumentFilePath(dir.path + "/" + file.getName(), file));
				}
			} else if (dir.file.isFile()) {
				ret.add(dir);
			}
		}
	}
	
	/**
	 * Takes output and does nothing with it.
	 */
	protected static class NullOutputStream extends OutputStream {
		public NullOutputStream() {}
		
		@Override
		public void write(int i) throws IOException {}
	}
}