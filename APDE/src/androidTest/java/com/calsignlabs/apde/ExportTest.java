package com.calsignlabs.apde;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.clearText;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.typeText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.intent.Intents.intending;
import static androidx.test.espresso.intent.matcher.IntentMatchers.hasAction;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withSpinnerText;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.calsignlabs.apde.TestUtil.deleteFile;
import static com.calsignlabs.apde.TestUtil.newSketch;
import static com.calsignlabs.apde.TestUtil.openMenu;
import static com.calsignlabs.apde.TestUtil.performInstall;
import static com.calsignlabs.apde.TestUtil.sleep;
import static com.calsignlabs.apde.TestUtil.uninstallApp;
import static org.hamcrest.Matchers.equalToIgnoringCase;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;

import androidx.core.content.FileProvider;
import androidx.test.espresso.intent.Intents;
import androidx.test.filters.LargeTest;

import com.calsignlabs.apde.support.MaybeDocumentFile;

import org.junit.Test;

import java.io.File;
import java.io.IOException;

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
		
		File tmpDir;
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
			// On Android 10+, we need the keystore to be somewhere writeable
			tmpDir = new File(getInstrumentation().getTargetContext().getFilesDir(), "apde_test_temp");
		} else {
			tmpDir = new File(Environment.getExternalStorageDirectory(), "apde_test_temp");
		}
		
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
			
			// mock the SAF
			Intents.init();
			
			Intent resultData = new Intent();
			resultData.setData(Uri.fromFile(keystoreFile));
			resultData.addCategory(Intent.CATEGORY_OPENABLE);
			Instrumentation.ActivityResult result =
					new Instrumentation.ActivityResult(Activity.RESULT_OK, resultData);
			
			intending(hasAction(Intent.ACTION_CREATE_DOCUMENT)).respondWith(result);
			
			onView(withId(R.id.create_keystore_file_select)).perform(click());
			
			Intents.release();
			
			onView(withId(R.id.create_keystore_password))
					.perform(typeText(keystorePassword));
			
			onView(withId(R.id.create_keystore_password_confirm))
					.perform(typeText(keystorePassword));
			
			// NOTE: it's tricky to match on the "Ready to create JKS keystore" text because
			// it depends on the type of keystore (JKS)
			
			onView(withText(R.string.create))
					.inRoot(isDialog())
					.perform(click());
			
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
				try {
					MaybeDocumentFile binDir = editorActivity.getGlobalState().getSketchbookFolder().childDirectory("bin");
					MaybeDocumentFile apk = binDir.child(editorActivity.getGlobalState().getSketchName() + ".apk", "application/vnd.android.package-archive");
					
					// we need to copy the APK so that it can be exposed through the FileProvider
					File copiedApk = new File(editorActivity.getFilesDir(), "export_test.apk");
					APDE.copyDocumentFile(apk.resolve(), MaybeDocumentFile.fromFile(copiedApk), editorActivity.getContentResolver());
					
					Intent promptInstall;
					if (android.os.Build.VERSION.SDK_INT >= 24) {
						// Need to use FileProvider
						Uri apkUri = FileProvider.getUriForFile(editorActivity.getGlobalState(),
								"com.calsignlabs.apde.fileprovider", copiedApk);
						promptInstall = new Intent(Intent.ACTION_INSTALL_PACKAGE).setData(apkUri)
								.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
						
						// TODO: this doesn't seem to be working on Android 10+
					} else {
						promptInstall = new Intent(Intent.ACTION_VIEW).setDataAndType(
								apk.resolve().getUri(),
								"application/vnd.android.package-archive");
					}
					
					editorActivity.startActivity(promptInstall);
				} catch (MaybeDocumentFile.MaybeDocumentFileException | IOException e) {
					throw new RuntimeException(e);
				}
			}));
			
			// TODO: check to make sure that running the sketch worked?
		} finally {
			deleteFile(tmpDir, true);
			
			// TODO delete the exported APK?
			
			// Uninstall the app
			editorActivityRule.getScenario().onActivity(editorActivity -> {
				try {
					uninstallApp(editorActivity, editorActivity.getGlobalState().getSketchPackageName(), false);
				} catch (MaybeDocumentFile.MaybeDocumentFileException e) {
					e.printStackTrace();
				}
			});
		}
	}
}
