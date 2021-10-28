package com.calsignlabs.apde;

import static androidx.test.espresso.Espresso.onData;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.clearText;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.typeText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.contrib.DrawerActions.open;
import static androidx.test.espresso.contrib.DrawerMatchers.isClosed;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.matcher.ViewMatchers.hasDescendant;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withClassName;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import static com.calsignlabs.apde.TestUtil.newSketch;
import static com.calsignlabs.apde.TestUtil.onDialogEditText;
import static com.calsignlabs.apde.TestUtil.openDrawer;
import static com.calsignlabs.apde.TestUtil.openMenu;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;

import android.view.Gravity;

import androidx.test.filters.LargeTest;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;

/**
 * Test managing sketches (loading, renaming, deleting, etc.).
 */
@LargeTest
public class ManageSketchTest extends BaseTest {
	/**
	 * Test moving a sketch from temporary storage to the sketchbook.
	 */
	@Test
	public void moveTempToSketchbook() {
		newSketch();
		
		String code = "// dummy code";
		
		onView(withId(R.id.code))
				.perform(typeText(code), closeSoftKeyboard())
				.check(matches(withText(code)));
		
		openMenu();
		onView(withText(R.string.editor_menu_move_temp_to_sketchbook))
				.perform(click());
		
		onView(withText(R.string.move_temp_to_sketchbook_button))
				.perform(click());
		
		String sketchName = "dummySketch";
		
		openMenu();
		onView(withText(R.string.editor_menu_rename_sketch))
				.perform(click());
		
		onDialogEditText()
				.perform(clearText())
				.perform(typeText(sketchName));
		
		onView(withText(R.string.rename_sketch_button))
				.inRoot(isDialog())
				.perform(click());
		
		try {
			onView(withId(R.id.toolbar))
					.check(matches(hasDescendant(withText(sketchName))));
		} finally {
			// Delete the sketch so that we can run the test again
			openMenu();
			onView(withText(R.string.editor_menu_delete_sketch))
					.perform(click());
			
			onView(withText(R.string.delete))
					.inRoot(isDialog())
					.perform(click());
		}
	}
	
	/**
	 * Test copying an example to the sketchbook.
	 */
	@Test
	public void copyExampleToSketchbook() {
		openDrawer();
		
		onView(withText(R.string.drawer_folder_examples))
				.perform(click());
		
		String exampleName = "Yellowtail";
		
		// We assume that the examples repo hasn't been downloaded
		onView(withText(exampleName))
				.perform(click());
		
		onView(withId(R.id.toolbar))
				.check(matches(hasDescendant(withText(exampleName))));
		
		openMenu();
		onView(withText(R.string.editor_menu_copy_to_sketchbook))
				.perform(click());
		
		try {
			onView(allOf(withId(R.id.code), isDisplayed()))
					.perform(clearText())
					.perform(typeText("// This code is editable now"));
		} finally {
			// Delete the sketch so that we can run the test again
			openMenu();
			onView(withText(R.string.editor_menu_delete_sketch))
					.perform(click());
			
			onView(withText(R.string.delete))
					.inRoot(isDialog())
					.perform(click());
		}
	}
}
