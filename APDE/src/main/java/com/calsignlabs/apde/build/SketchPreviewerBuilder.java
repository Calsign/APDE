package com.calsignlabs.apde.build;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.v4.content.FileProvider;
import android.view.inputmethod.InputMethodManager;

import com.calsignlabs.apde.APDE;
import com.calsignlabs.apde.EditorActivity;
import com.calsignlabs.apde.R;
import com.calsignlabs.apde.task.Task;

import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.xml.parsers.ParserConfigurationException;

import kellinwood.security.zipsigner.ZipSigner;
import processing.data.XML;

/**
 * Utility for building the sketch preview APK and installing it. Very similar to Build.java, but
 * much more streamlined (and better-implemented IMO).
 */
public class SketchPreviewerBuilder extends Task {
	protected EditorActivity editor;
	protected String[] permissions;
	
	private boolean launchSketch;
	private boolean forceSetPermissions;
	
	public SketchPreviewerBuilder(EditorActivity editor, String[] permissions, boolean launchSketch, boolean forceSetPermissions) {
		this.editor = editor;
		this.permissions = permissions;
		this.launchSketch = launchSketch;
		this.forceSetPermissions = forceSetPermissions;
	}
	
	public static boolean isPreviewerInstalled(Context context) {
		try {
			context.getPackageManager().getPackageInfo("com.calsignlabs.apde.sketchpreview", PackageManager.GET_PERMISSIONS);
			return true;
		} catch (PackageManager.NameNotFoundException e) {
			return false;
		}
	}
	
	public static String[] getInstalledPermissions(Context context) {
		String[] permissions = null;
		try {
			permissions = context.getPackageManager().getPackageInfo("com.calsignlabs.apde.sketchpreview", PackageManager.GET_PERMISSIONS).requestedPermissions;
		} catch (PackageManager.NameNotFoundException e) {
			// Oh well, the sketch previewer isn't installed
		}
		return permissions != null ? permissions : new String[] {};
	}
	
	protected File getBuildFolder() {
		// Re-using preference from regular build
		if (PreferenceManager.getDefaultSharedPreferences(editor).getBoolean("pref_build_internal_storage", true)) {
			return editor.getDir("preview_build", 0);
		} else {
			return new File(Environment.getExternalStorageDirectory(), "preview_build");
		}
	}
	
	protected File getUnsignedApk() {
		return new File(getBuildFolder(), "sketch_previewer.unsigned.apk");
	}
	
	protected File getFinalApk() {
		// Need to store in main files dir in order to share with sketch previewer
		return new File(editor.getFilesDir(), "sketch_previewer.apk");
	}
	
	protected File getManifestFile() {
		return new File(getBuildFolder(), "AndroidManifest.xml");
	}
	
	protected File getAssetsFolder() {
		return new File(getBuildFolder(), "assets");
	}
	
	protected File getResFolder() {
		return new File(getBuildFolder(), "res");
	}
	
	protected File getSupportResFolder() {
		return new File(getBuildFolder(), "support-res");
	}
	
	protected File getClassesDexFile() {
		return new File(getBuildFolder(), "classes.dex");
	}
	
	protected File getAaptFile() {
		return new File(Build.getTempFolder(editor), "aapt");
	}
	
	public File getAndroidJarLoc() {
		return new File(Build.getTempFolder(editor), "android.jar");
	}
	
	protected static void unzipFile(InputStream input, File destFolder) throws IOException {
		ZipInputStream inputStream = new ZipInputStream(input);
		
		ZipEntry zipEntry = null;
		while ((zipEntry = inputStream.getNextEntry()) != null) {
			String name = zipEntry.getName();
			
			File file = new File(destFolder.getAbsolutePath(), name);
			
			if (zipEntry.isDirectory()) {
				file.mkdirs();
			} else {
				Build.createFileFromInputStream(inputStream, file, false);
				inputStream.closeEntry();
			}
		}
		
		inputStream.close();
	}
	
	@Override
	public void run() throws InterruptedException {
		// Below is the entire build/install sequence
		try {
			postStatus(R.string.preview_build_copying_preview_build_directory);
			copyPreviewBuild();
			postStatus(R.string.preview_build_copying_android_jar);
			copyAndroidJar();
			postStatus(R.string.preview_build_setting_manifest_permissions);
			setManifestPermissions();
			postStatus(R.string.preview_build_setting_up_aapt);
			setupAapt();
			postStatus(R.string.preview_build_running_aapt);
			buildApk();
			addClassesDex();
			postStatus(R.string.preview_build_signing_apk);
			signApk();
			postStatus(R.string.preview_build_installing_apk);
			installApk();
		} catch (BuildFailedException e) {
			postStatus(R.string.preview_build_failure);
			System.err.println(editor.getResources().getString(R.string.preview_build_failure));
		}
	}
	
	protected void copyPreviewBuild() throws BuildFailedException {
		try {
			APDE.deleteFile(getBuildFolder());
			unzipFile(editor.getAssets().open("preview_build.zip"), getBuildFolder());
		} catch (IOException e) {
			e.printStackTrace();
			throw new BuildFailedException();
		}
	}
	
	protected void copyAndroidJar() throws BuildFailedException {
		if (!getAndroidJarLoc().exists()) {
			try {
				Build.copyAndroidJar(editor);
			} catch (IOException e) {
				e.printStackTrace();
				throw new BuildFailedException();
			}
		}
	}
	
	protected void setManifestPermissions() throws BuildFailedException {
		try {
			XML xml = new XML(getManifestFile());
			
			// Load baseline permissions
			XML[] existingPermissionsXml = xml.getChildren("uses-permission");
			List<String> existingPermissions = new ArrayList<>(existingPermissionsXml.length);
			for (XML perm : existingPermissionsXml) {
				existingPermissions.add(perm.getString("android:name"));
			}
			
			// Force set means potentially wiping the ones that were there previously
			if (!forceSetPermissions) {
				// Load permissions that were installed previously
				for (String name : getInstalledPermissions(editor)) {
					if (!existingPermissions.contains(name)) {
						XML newbie = xml.addChild("uses-permission");
						newbie.setString("android:name", name);
						existingPermissions.add(name);
					}
				}
			}
			
			// Load newly-requested permissions
			for (String name : permissions) {
				if (!existingPermissions.contains(name)) {
					XML newbie = xml.addChild("uses-permission");
					newbie.setString("android:name", name);
					existingPermissions.add(name);
				}
			}
			
			xml.save(getManifestFile(), "");
		} catch (IOException | ParserConfigurationException | SAXException e) {
			e.printStackTrace();
			throw new BuildFailedException();
		}
	}
	
	protected void setupAapt() throws BuildFailedException {
		if (!getAaptFile().exists()) {
			try {
				File aaptLoc = getAaptFile();
				
				InputStream inputStream = editor.getAssets().open(Build.getAaptName(editor, false));
				Build.createFileFromInputStream(inputStream, aaptLoc);
				
				if (!aaptLoc.setExecutable(true, true)) {
					throw new BuildFailedException();
				}
			} catch (IOException e) {
				e.printStackTrace();
				throw new BuildFailedException();
			}
		}
	}
	
	protected void buildApk() throws BuildFailedException, InterruptedException {
		// Not sure if the appcompat stuff is necessary, but it doesn't hurt
		String[] args = {
				getAaptFile().getAbsolutePath(),
				"package", "-f", "--auto-add-overlay", "--no-version-vectors",
				"--extra-packages", "android.support.v7.appcompat",
				"-S", getResFolder().getAbsolutePath(), // res
				"-S", getSupportResFolder().getAbsolutePath(), // support lib res
				"-A", getAssetsFolder().getAbsolutePath(), // assets
				"-M", getManifestFile().getAbsolutePath(), // manifest
				"-I", getAndroidJarLoc().getAbsolutePath(), // android.jar
				"-F", getUnsignedApk().getAbsolutePath(), // output apk
		};
		
		runAapt(args);
	}
	
	protected void addClassesDex() throws BuildFailedException, InterruptedException {
		// -k is needed to ignore the path of classes.dex, otherwise the entire file structure
		// gets replicated within the apk and that is bad news for everyone
		String[] args = {
				getAaptFile().getAbsolutePath(),
				"add", "-f", "-k",
				getUnsignedApk().getAbsolutePath(),
				getClassesDexFile().getAbsolutePath(),
		};
		
		runAapt(args);
	}
	
	protected void runAapt(String[] args) throws BuildFailedException, InterruptedException {
		// Runs AAPT with the provided set of arguments - we use this twice
		
		boolean verbose = PreferenceManager.getDefaultSharedPreferences(editor).getBoolean("pref_debug_global_verbose_output", false);
		
		try {
			ProcessBuilder pb = new ProcessBuilder(args);
			// Combine output streams, otherwise we need two threads or risk deadlock
			pb.redirectErrorStream(true);
			Process aaptProcess = pb.start();
			
			// We have to give it an output stream for some reason
			// So give it one that ignores the data
			// We don't want to print the standard out to the console because it is WAY too much stuff
			// It even causes APDE to crash because there is too much text in the console to fit in a transaction
			// We want to show the error stream in verbose mode though because this lets us debug things
			Build.copyStream(aaptProcess.getInputStream(), verbose ? System.out : new Build.NullOutputStream());
			
			int code = aaptProcess.waitFor();
			
			if (code != 0) {
				throw new BuildFailedException();
			}
		} catch (IOException e) {
			e.printStackTrace();
			throw new BuildFailedException();
		}
	}
	
	protected void signApk() throws BuildFailedException {
		String mode = "testkey";
		String inFilename = getUnsignedApk().getAbsolutePath();
		String outFilename = getFinalApk().getAbsolutePath();
		
		ZipSigner signer;
		
		try {
			signer = new ZipSigner();
			signer.setKeymode(mode);
			signer.signZip(inFilename, outFilename);
		} catch (Exception e) {
			e.printStackTrace();
			throw new BuildFailedException();
		}
	}
	
	protected void installApk() {
		File finalApk = getFinalApk();
		Intent promptInstall;
		
		if (android.os.Build.VERSION.SDK_INT >= 24) {
			Uri uri = FileProvider.getUriForFile(editor, "com.calsignlabs.apde.fileprovider", finalApk);
			promptInstall = new Intent(Intent.ACTION_INSTALL_PACKAGE).setData(uri).setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
		} else {
			if (!finalApk.setReadable(true, false)) {
				System.err.println("failed to make apk readable");
			}
			promptInstall = new Intent(Intent.ACTION_VIEW).setDataAndType(Uri.fromFile(finalApk), "application/vnd.android.package-archive");
		}
		
		// Get result from installation so that we can launch the sketch right away
		promptInstall.putExtra(Intent.EXTRA_RETURN_RESULT, true);
		
		// Hide the keyboard just before opening the installer dialog so that it doesn't
		// obscure the "Install" button
		InputMethodManager imm = (InputMethodManager) editor.getSystemService(Context.INPUT_METHOD_SERVICE);
		imm.hideSoftInputFromWindow(editor.findViewById(R.id.content).getWindowToken(), 0);
		
		if (launchSketch) {
			editor.startActivityForResult(promptInstall, EditorActivity.FLAG_RUN_PREVIEW);
		} else {
			editor.startActivity(promptInstall);
		}
	}
	
	/**
	 * An exception indicating that the build has failed.
	 */
	protected static class BuildFailedException extends Exception {}
	
	@Override
	public CharSequence getTitle() {
		return editor.getString(R.string.task_install_sketch_previewer);
	}
}
