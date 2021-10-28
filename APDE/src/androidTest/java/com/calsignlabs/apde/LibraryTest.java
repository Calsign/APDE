package com.calsignlabs.apde;

import static androidx.test.espresso.Espresso.onData;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.clearText;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.typeText;
import static androidx.test.espresso.assertion.ViewAssertions.doesNotExist;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.intent.Intents.intending;
import static androidx.test.espresso.intent.matcher.IntentMatchers.hasAction;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static com.calsignlabs.apde.TestUtil.deleteFile;
import static com.calsignlabs.apde.TestUtil.getString;
import static com.calsignlabs.apde.TestUtil.matchesAnyString;
import static com.calsignlabs.apde.TestUtil.newSketch;
import static com.calsignlabs.apde.TestUtil.openMenu;
import static com.calsignlabs.apde.TestUtil.sleep;
import static org.hamcrest.Matchers.containsString;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;

import androidx.test.espresso.intent.Intents;
import androidx.test.espresso.matcher.BoundedMatcher;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;

import com.calsignlabs.apde.contrib.Library;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.core.StringEndsWith;
import org.junit.Test;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;

/**
 * Test importing libraries, installing libraries, managing libraries, and using the DX dexer.
 */
@LargeTest
public class LibraryTest extends BaseTest {
	// We choose the sound library because it is kept up-to-date and is directly sponsored by the
	// Processing Foundation. Might be a good idea to update the URL when new updates are released.
	private static final String LIB_ZIP_URL =
			"https://github.com/processing/processing-sound/releases/download/v2.3.1/sound.zip";
	private static final String LIB_NAME = "sound";
	private static final String LIB_DESC = "Provides a simple way to work with audio.";
	// NOTE: there's actually a bunch of other imports for jsyn etc., but we just check this one
	private static final String LIB_IMPORTS = "import processing.sound.*;";
	
	private static Matcher<Object> withLibraryName(final String libraryName) {
		return new BoundedMatcher<Object, Library>(Library.class) {
			@Override
			public void describeTo(Description description) {
				description.appendText("with library name: '" + libraryName + "'");
			}
			
			@Override
			protected boolean matchesSafely(Library item) {
				return item.getName().equals(libraryName);
			}
		};
	}
	
	/**
	 * Synchronously download a file from the given url and save it to the given destination.
	 *
	 * @param urlStr
	 * @param dest
	 */
	private static void downloadFile(String urlStr, File dest) {
		try {
			final int BUFFER_SIZE = 1024;
			InputStream input = new BufferedInputStream(new URL(urlStr).openStream(), BUFFER_SIZE);
			OutputStream output = new BufferedOutputStream(new FileOutputStream(dest));
			byte[] data = new byte[BUFFER_SIZE];
			int count = -1;
			while ((count = input.read(data)) != -1) {
				output.write(data, 0, count);
			}
			output.flush();
			output.close();
			input.close();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	
	/**
	 * Test installing a library, importing it, running a sketch with it, and then deleting it.
	 */
	@Test
	public void libraryTest() {
		File tmpDir = new File(Environment.getExternalStorageDirectory(), "apde_test_temp");
		File libraryZip = new File(tmpDir, LIB_NAME + ".zip");
		
		tmpDir.mkdir();
		
		// NOTE: downloading takes about 2 second in my tests, which makes it worth not pulling
		// the file into the repo
		downloadFile(LIB_ZIP_URL, libraryZip);
		
		try {
			newSketch();
			
			openMenu();
			
			onView(withText(R.string.editor_menu_tools))
					.check(matches(isDisplayed()))
					.perform(click());
			
			onView(withText(R.string.tool_import_library))
					.inRoot(isDialog())
					.check(matches(isDisplayed()))
					.perform(click());
			
			onView(withText(R.string.library_manager_open))
					.inRoot(isDialog())
					.check(matches(isDisplayed()))
					.perform(click());
			
			openMenu();
			
			// use espresso-intents to mock the Storage Access Framework
			Intents.init();
			
			Intent resultData = new Intent();
			resultData.setData(Uri.fromFile(libraryZip));
			Instrumentation.ActivityResult result =
					new Instrumentation.ActivityResult(Activity.RESULT_OK, resultData);
			
			intending(hasAction(Intent.ACTION_GET_CONTENT))
					.respondWith(result);
			
			onView(withText(R.string.library_manager_menu_install_zip_library))
					.check(matches(isDisplayed()))
					.perform(click());
			
			Intents.release();
			
			// TODO: use idling resource
			// this is probably best achieved by migrating library installation to TaskManager
			UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
			device.findObject(
					matchesAnyString(getString(R.string.library_manager_install_dialog_title)
							+ " " + LIB_NAME)).waitUntilGone(20000);
			sleep(500);
			
			onView(withText(LIB_NAME))
					.check(matches(isDisplayed()));
			
			onView(withText(LIB_DESC))
					.check(matches(isDisplayed()));
			
			pressBack();
			
			openMenu();
			
			onView(withText(R.string.editor_menu_tools))
					.check(matches(isDisplayed()))
					.perform(click());
			
			onView(withText(R.string.tool_import_library))
					.inRoot(isDialog())
					.check(matches(isDisplayed()))
					.perform(click());
			
			onView(withText(LIB_NAME))
					.inRoot(isDialog())
					.check(matches(isDisplayed()))
					.perform(click());
			
			onView(withId(R.id.code))
					.check(matches(isDisplayed()))
					.check(matches(withText(containsString(LIB_IMPORTS))));
			
			// need to clear the code because we can't move the cursor to the end.
			// we could write code that uses the sound library, but that's kind of tough
			// in practice, if importing it succeeds, then the library should be working.
			onView(withId(R.id.code))
					.perform(clearText())
					.perform(typeText(LIB_IMPORTS + "\nprintln(\"foobar\");\ngetActivity().finish();"));
			
			onView(withId(R.id.menu_run))
					.perform(click());
			
			awaitBuild();
			
			// wait for sketch to run
			sleep(1000);
			
			onView(withId(R.id.console))
					.check(matches(withText(new StringEndsWith("foobar\n"))));
			
			openMenu();
			
			onView(withText(R.string.editor_menu_tools))
					.check(matches(isDisplayed()))
					.perform(click());
			
			onView(withText(R.string.tool_import_library))
					.inRoot(isDialog())
					.check(matches(isDisplayed()))
					.perform(click());
			
			onView(withText(R.string.library_manager_open))
					.inRoot(isDialog())
					.check(matches(isDisplayed()))
					.perform(click());
			
			onData(withLibraryName(LIB_NAME))
					.inAdapterView(withId(R.id.library_manager_list))
					.atPosition(0)
					.onChildView(withId(R.id.library_manager_list_item_actions))
					.check(matches(isDisplayed()))
					.perform(click());
			
			onView(withText(R.string.library_manager_contrib_menu_uninstall))
					.check(matches(isDisplayed()))
					.perform(click());
			
			onView(withText(R.string.library_manager_contrib_uninstall_warning_title))
					.inRoot(isDialog())
					.check(matches(isDisplayed()))
					.perform(click());
			
			// TODO: wait for library to get deleted (should be same as install above)
			sleep(1000);
			
			onView(withText(LIB_NAME))
					.check(doesNotExist());
		} finally {
			deleteFile(tmpDir, true);
			
			editorActivityRule.getScenario().onActivity(editorActivity -> {
				File installedLibrary =
						new File(new File(editorActivity.getGlobalState().getSketchbookFolder(),
								"libraries"), LIB_NAME);
				if (installedLibrary.exists()) {
					deleteFile(installedLibrary, true);
				}
			});
		}
	}
	
	/**
	 * Test dx dexer.
	 */
	@Test
	public void dxDexTest() {
		// TODO
	}
}
