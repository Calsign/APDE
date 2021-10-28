package com.calsignlabs.apde;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.clearText;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.typeText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withSpinnerText;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import static com.calsignlabs.apde.TestUtil.deleteFile;
import static com.calsignlabs.apde.TestUtil.newSketch;
import static com.calsignlabs.apde.TestUtil.openMenu;
import static com.calsignlabs.apde.TestUtil.performInstall;
import static com.calsignlabs.apde.TestUtil.sleep;
import static com.calsignlabs.apde.TestUtil.uninstallApp;
import static org.hamcrest.Matchers.equalToIgnoringCase;

import android.content.Intent;
import android.net.Uri;
import android.os.Environment;

import androidx.core.content.FileProvider;
import androidx.test.filters.LargeTest;

import org.junit.Test;

import java.io.File;

/**
 * Test exporting a signed package.
 */
@LargeTest
public class ExportTest extends BaseTest {
	/**
	 * Test exporting a signed package.
	 */
	@Test
	public void exportSignedPackage() {
		newSketch();
		
		// closing brace and tabs are inserted automatically
		String code = "void setup() {\nprintln(\"Hello, export!\");";
		
		onView(withId(R.id.code))
				.perform(typeText(code));
		
		File tmpDir = new File(Environment.getExternalStorageDirectory(), "apde_test_temp");
		
		tmpDir.mkdir();
		
		File keystoreFile = new File(tmpDir, "keystore");
		String keystorePassword = "keystorePassword";
		String keyAlias = "keyAlias";
		String keyPassword = "keyPassword";
		int keyValidity = 25;
		
		try {
			openMenu();
			
			onView(withText(R.string.editor_menu_tools))
					.check(matches(isDisplayed()))
					.perform(click());
			
			onView(withText(R.string.export_signed_package))
					.inRoot(isDialog())
					.check(matches(isDisplayed()))
					.perform(click());
			
			onView(withId(R.id.keystore_new))
					.perform(click());
			
			onView(withId(R.id.create_keystore_file))
					.perform(typeText(keystoreFile.getAbsolutePath()));
			
			onView(withId(R.id.create_keystore_password))
					.perform(typeText(keystorePassword));
			
			onView(withId(R.id.create_keystore_password_confirm))
					.perform(typeText(keystorePassword));
			
			// NOTE: it's tricky to match on the "Ready to create JKS keystore" text because
			// it depends on the type of keystore (JKS)
			
			onView(withText(R.string.create))
					.inRoot(isDialog())
					.perform(click());
			
			onView(withId(R.id.keystore_file))
					.check(matches(withText(keystoreFile.getAbsolutePath())));
			
			onView(withId(R.id.keystore_password))
					.check(matches(withText(keystorePassword)));
			
			onView(withId(R.id.alias_new))
					.perform(click());
			
			onView(withId(R.id.create_alias_alias))
					.perform(typeText(keyAlias));
			
			onView(withId(R.id.create_alias_password))
					.perform(typeText(keyPassword));
			
			onView(withId(R.id.create_alias_password_confirm))
					.perform(typeText(keyPassword));
			
			onView(withId(R.id.create_alias_validity))
					.perform(clearText(), typeText(Integer.toString(keyValidity)), closeSoftKeyboard());
			
			onView(withText(R.string.create))
					.inRoot(isDialog())
					.perform(click());
			
			onView(withId(R.id.alias))
					.check(matches(withSpinnerText(equalToIgnoringCase(keyAlias))));
			
			onView(withId(R.id.alias_password))
					.check(matches(withText(keyPassword)));
			
			onView(withText(R.string.export_signed_package_export))
					.perform(click());
			
			// TODO: wait for release build to finish
			// unfortunately, it uses a different mechanism from regular build
			sleep(10000);
			
			// TODO: not opening app because seeing issues with not being able to get out of the
			// immersive dialog confirmation
			performInstall(false, () -> editorActivityRule.getScenario().onActivity(editorActivity -> {
				File binDir = new File(editorActivity.getGlobalState().getSketchbookFolder(), "bin");
				File apk = new File(binDir, editorActivity.getGlobalState().getSketchName() + ".apk");
				
				Intent promptInstall;
				if (android.os.Build.VERSION.SDK_INT >= 24) {
					// Need to use FileProvider
					Uri apkUri = FileProvider.getUriForFile( editorActivity.getGlobalState(),
							"com.calsignlabs.apde.fileprovider", apk);
					promptInstall = new Intent(Intent.ACTION_INSTALL_PACKAGE).setData(apkUri)
							.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
				} else {
					promptInstall = new Intent(Intent.ACTION_VIEW).setDataAndType(Uri.fromFile(apk),
							"application/vnd.android.package-archive");
				}
				
				editorActivity.startActivity(promptInstall);
			}));
			
			// TODO: check to make sure that running the sketch worked?
		} finally {
			deleteFile(tmpDir, true);
			
			// TODO delete the exported APK?
			
			// Uninstall the app
			editorActivityRule.getScenario().onActivity(editorActivity ->
					uninstallApp(editorActivity, editorActivity.getGlobalState().getSketchPackageName(), false));
		}
	}
}
