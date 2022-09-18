package com.calsignlabs.apde;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.clearText;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.action.ViewActions.swipeUp;
import static androidx.test.espresso.action.ViewActions.typeText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.intent.Intents.intending;
import static androidx.test.espresso.intent.matcher.IntentMatchers.hasAction;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static com.calsignlabs.apde.TestUtil.newSketch;
import static com.calsignlabs.apde.TestUtil.onDialogEditText;
import static com.calsignlabs.apde.TestUtil.openMenu;
import static com.calsignlabs.apde.TestUtil.sleep;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.test.espresso.intent.Intents;

import com.calsignlabs.apde.build.StaticBuildResources;

import org.hamcrest.core.StringEndsWith;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

public class SketchPropertiesTest extends BaseTest {
	public static File extractAsset(Context testContext, Context appContext, String name) {
		try {
			File dest = new File(appContext.getFilesDir(), name);
			InputStream is = testContext.getAssets().open(name);
			StaticBuildResources.createFileFromInputStream(is, dest);
			is.close();
			return dest;
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	
	/**
	 * Test that editing the sketch properties is saved properly.
	 *
	 * TODO: Test all sketch properties options.
	 */
	@Test
	public void sketchPropertiesTest() {
		String prettySketchName = "fancySketch";
		
		newSketch();
		
		openMenu();
		
		onView(withText(R.string.title_activity_sketch_properties)).perform(click());
		
		// edit a property
		
		onView(withText(R.string.prop_pretty_name)).perform(click());
		
		onDialogEditText().perform(clearText(), typeText(prettySketchName));
		onView(withText(R.string.ok)).perform(click());
		
		pressBack();
		
		// make sure the sketch properties file was written
		editorActivityRule.getScenario().onActivity(editorActivity -> {
			assert editorActivity.getGlobalState().getSketchPropertiesFile().exists();
		});
	}
	
	@Test
	public void sketchDataFiles() {
		String dataFilename = "foobar.txt";
		File dataFile = extractAsset(getInstrumentation().getContext(), getInstrumentation().getTargetContext(), dataFilename);
		
		newSketch();
		
		openMenu();
		
		// add a file to the data folder
		
		onView(withText(R.string.title_activity_sketch_properties)).perform(click());
		
		// mock the SAF
		Intents.init();
		
		Intent resultData = new Intent();
		resultData.setData(Uri.fromFile(dataFile));
		Instrumentation.ActivityResult result =
				new Instrumentation.ActivityResult(Activity.RESULT_OK, resultData);
		
		intending(hasAction(Intent.ACTION_GET_CONTENT)).respondWith(result);
		
		onView(withId(R.id.sketch_properties_fragment)).perform(swipeUp());
		onView(withText(R.string.prop_add_file)).perform(click());
		
		Intents.release();
		
		// make sure the file was added correctly
		editorActivityRule.getScenario().onActivity(editorActivity -> {
			File addedFile = new File(new File(
					editorActivity.getGlobalState().getSketchLocation(), "data"), dataFilename);
			assert addedFile.exists();
		});
		
		pressBack();
		
		// run a sketch that loads the data file
		
		String typedCode = String.format(
				"void setup() {\nString[] data = loadStrings(\"%s\");\nprintln(data[0]);\ngetActivity().finish();", dataFilename);
		
		onView(withId(R.id.code))
				.perform(typeText(typedCode), closeSoftKeyboard());
		
		onView(withId(R.id.menu_run))
				.perform(click());
		
		awaitBuild();
		
		// NOTE: we assume that sketch previewer is installed
		
		// wait for sketch to run
		sleep(2000);
		
		// make sure it prints the correct output
		// TODO: load this from the file instead of duplicating it here
		onView(withId(R.id.console))
				.check(matches(withText(new StringEndsWith("this is a data file\n"))));
	}
	
	@Test
	public void changeSketchIcon() {
		File iconFile = extractAsset(getInstrumentation().getContext(), getInstrumentation().getTargetContext(), "icon.png");
		
		newSketch();
		
		openMenu();
		
		// change the sketch icon
		
		onView(withText(R.string.title_activity_sketch_properties)).perform(click());
		
		onView(withId(R.id.sketch_properties_fragment)).perform(swipeUp());
		onView(withText(R.string.prop_change_icon)).perform(click());
		
		// mock the SAF
		Intents.init();
		
		Intent resultData = new Intent();
		resultData.setData(Uri.fromFile(iconFile));
		Instrumentation.ActivityResult result =
				new Instrumentation.ActivityResult(Activity.RESULT_OK, resultData);
		
		intending(hasAction(Intent.ACTION_GET_CONTENT)).respondWith(result);
		
		onView(withId(R.id.change_icon_file_select)).perform(click());
		
		Intents.release();
		
		onView(withText(R.string.ok)).perform(click());
		
		// make sure the icon files were created
		editorActivityRule.getScenario().onActivity(editorActivity -> {
			// we just check one icon, should be fine
			File icon96File = new File(
					editorActivity.getGlobalState().getSketchLocation(), "icon-96.png");
			assert icon96File.exists();
		});
	}
}
