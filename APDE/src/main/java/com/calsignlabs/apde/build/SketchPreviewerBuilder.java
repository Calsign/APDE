package com.calsignlabs.apde.build;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Environment;
import android.preference.PreferenceManager;
import androidx.core.content.FileProvider;
import android.view.inputmethod.InputMethodManager;

import com.android.tools.aapt2.Aapt2Jni;
import com.calsignlabs.apde.APDE;
import com.calsignlabs.apde.EditorActivity;
import com.calsignlabs.apde.R;
import com.calsignlabs.apde.task.Task;

import org.xml.sax.SAXException;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

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
	
	protected File getResApk() {
		return new File(getBuildFolder(), "sketch_previewer.res.apk");
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
	
	protected File getCompiledResFolder() {
		return new File(getBuildFolder(), "compiled_res");
	}
	
	protected File getClassesDexFile() {
		return new File(getBuildFolder(), "classes.dex");
	}
	
	public File getAndroidJarLoc() {
		return StaticBuildResources.getAndroidJarLoc(editor);
	}
	
	protected static void unzipFile(InputStream input, File destFolder) throws IOException {
		try (ZipInputStream inputStream = new ZipInputStream(input)) {
			ZipEntry zipEntry = null;
			while ((zipEntry = inputStream.getNextEntry()) != null) {
				String name = zipEntry.getName();
				
				File file = new File(destFolder.getAbsolutePath(), name);
				
				if (zipEntry.isDirectory()) {
					file.mkdirs();
				} else {
					StaticBuildResources.createFileFromInputStream(inputStream, file, false);
					inputStream.closeEntry();
				}
			}
		}
	}
	
	/**
	 * Add a file to a zip archive, outputting to a new zip archive.
	 *
	 * @param inZip the zip file to copy
	 * @param outZip the file to write the new zip archive with the additional file
	 * @param addFileName the path to the added file in the zip archive
	 * @param addFile the file to add
	 * @throws IOException if some IO thing failed
	 */
	protected static void addFileZip(File inZip, File outZip, String addFileName, File addFile) throws IOException {
		try (ZipInputStream inputStream = new ZipInputStream(new BufferedInputStream(new FileInputStream(inZip)));
		     InputStream addInpuStream = new BufferedInputStream(new FileInputStream(addFile));
		     ZipOutputStream outputStream = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(outZip)))) {
			
			int read;
			byte[] buffer = new byte[4096];
			
			// copy over all existing entries
			ZipEntry entry;
			while ((entry = inputStream.getNextEntry()) != null) {
				outputStream.putNextEntry(new ZipEntry(entry.getName()));
				while ((read = inputStream.read(buffer)) > 0) {
					outputStream.write(buffer, 0, read);
				}
				outputStream.closeEntry();
			}
			
			outputStream.putNextEntry(new ZipEntry(addFileName));
			while ((read = addInpuStream.read(buffer)) > 0) {
				outputStream.write(buffer, 0, read);
			}
			outputStream.closeEntry();
			
			outputStream.flush();
		}
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
				StaticBuildResources.copyAndroidJar(editor);
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
	
	protected void buildApk() throws BuildFailedException, InterruptedException {
		try {
			if (!getCompiledResFolder().mkdirs()) {
				System.err.println("Failed to make compiled res folder");
				throw new BuildFailedException();
			}
			
			// TODO: perform compile step in advance
			for (File resDir : getResFolder().listFiles()) {
				for (File resFile : resDir.listFiles()) {
					String[] args = {
							resFile.getAbsolutePath(),
							"-o", getCompiledResFolder().getAbsolutePath(),
					};
					
					Aapt2Wrapper.compile(editor.getGlobalState(), Arrays.asList(args));
				}
			}
			
			List<String> linkArgs = new ArrayList<>(Arrays.asList(
					"-o", getResApk().getAbsolutePath(),
					"--manifest", getManifestFile().getAbsolutePath(),
					"-I", getAndroidJarLoc().getAbsolutePath(),
					"-A", getAssetsFolder().getAbsolutePath(),
					"--auto-add-overlay",
					"--no-version-vectors",
					"--extra-packages", "android.support.v7.appcompat"
			));
			
			List<File> compiledResDirs = new ArrayList<>();
			compiledResDirs.add(getCompiledResFolder());
			// pull in compiled resources from the artifacts
			for (File targetCompiledResDir : StaticBuildResources.getTargetDirs(StaticBuildResources.getResCompiledDir(editor), ComponentTarget.APP)) {
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
		} catch (Aapt2Wrapper.InvocationFailedException e) {
			e.printStackTrace();
			
			for (Aapt2Jni.Log log : e.logs) {
				System.err.println(log);
			}
			
			throw new BuildFailedException();
		} catch (IOException e) {
			e.printStackTrace();
			throw new BuildFailedException();
		}
	}
	
	protected void addClassesDex() throws BuildFailedException {
		try {
			addFileZip(getResApk(), getUnsignedApk(), "classes.dex", getClassesDexFile());
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
