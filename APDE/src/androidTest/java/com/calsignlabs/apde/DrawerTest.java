package com.calsignlabs.apde;

import static androidx.test.espresso.Espresso.onData;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.clearText;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.swipeDown;
import static androidx.test.espresso.action.ViewActions.swipeUp;
import static androidx.test.espresso.action.ViewActions.typeText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.matcher.ViewMatchers.hasDescendant;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import static com.calsignlabs.apde.TestUtil.deleteFile;
import static com.calsignlabs.apde.TestUtil.getString;
import static com.calsignlabs.apde.TestUtil.newSketch;
import static com.calsignlabs.apde.TestUtil.onDialogEditText;
import static com.calsignlabs.apde.TestUtil.openDrawer;
import static com.calsignlabs.apde.TestUtil.openMenu;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.anything;
import static org.hamcrest.Matchers.containsString;

import androidx.test.espresso.matcher.BoundedMatcher;
import androidx.test.filters.LargeTest;

import com.calsignlabs.apde.support.MaybeDocumentFile;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.junit.Test;

import java.io.File;

/**
 * Test navigating sketches in the drawer.
 *
 * TODO: Figure out hot to test the drag and drop things. This isn't supported by espresso.
 */
@LargeTest
public class DrawerTest extends BaseTest {
	public static final String NAVIGATE_UP = "..";
	
	/**
	 * Can use this matcher in onData() to select drawer list items with the given sketch name.
	 *
	 * @param sketchName
	 * @return
	 */
	private static Matcher<Object> withSketchName(final String sketchName) {
		return new BoundedMatcher<Object, FileNavigatorAdapter.FileItem>(FileNavigatorAdapter.FileItem.class) {
			@Override
			public void describeTo(Description description) {
				description.appendText("with sketch name: '" + sketchName + "'");
			}
			
			@Override
			protected boolean matchesSafely(FileNavigatorAdapter.FileItem item) {
				return item.getText().equals(sketchName);
			}
		};
	}
	
	/**
	 * Test that the recents shows recently opened sketches and examples.
	 */
	@Test
	public void recentsTest() {
		openDrawer();
		
		String bouncyBubbles = "BouncyBubbles";
		String sketch = "mySketch";
		String conway = "Conway";
		
		onView(withText(R.string.drawer_folder_examples))
				.perform(click());
		
		onView(withText(bouncyBubbles))
				.perform(click());
		
		onView(allOf(withId(R.id.code), isDisplayed()))
				.check(matches(withText(containsString("Bouncy Bubbles"))));
		
		newSketch();
		
		openMenu();
		
		try {
			onView(withText(R.string.editor_menu_move_temp_to_sketchbook))
					.perform(click());
			
			onView(withText(R.string.move_temp_to_sketchbook_button))
					.inRoot(isDialog())
					.perform(click());
			
			openMenu();
			
			onView(withText(R.string.editor_menu_rename_sketch))
					.perform(click());
			
			onDialogEditText()
					.perform(clearText(), typeText(sketch));
			
			onView(withText(R.string.rename_sketch_button))
					.inRoot(isDialog())
					.perform(click());
			
			openDrawer();
			
			onView(withText(conway))
					.perform(click());
			
			onView(allOf(withId(R.id.code), isDisplayed()))
					.check(matches(withText(containsString("Conway's Game of Life"))));
			
			openDrawer();
			
			onView(withText(NAVIGATE_UP))
					.perform(click());
			
			onView(withText(R.string.drawer_folder_recent))
					.perform(click());
			
			onView(withId(R.id.drawer_list)).perform(swipeDown());
			
			onData(anything())
					.inAdapterView(withId(R.id.drawer_list))
					.atPosition(1)  // 0 is ..
					.check(matches(hasDescendant(withText(conway))))
					.check(matches(hasDescendant(withText(containsString(getString(R.string.drawer_folder_examples))))));
			
			onData(anything())
					.inAdapterView(withId(R.id.drawer_list))
					.atPosition(2)
					.check(matches(hasDescendant(withText(sketch))))
					.check(matches(hasDescendant(withText(containsString(getString(R.string.drawer_folder_sketches))))));
			
			onData(anything())
					.inAdapterView(withId(R.id.drawer_list))
					.atPosition(3)
					.check(matches(hasDescendant(withText(bouncyBubbles))))
					.check(matches(hasDescendant(withText(containsString(getString(R.string.drawer_folder_examples))))));
		} finally {
			// delete the sketch we created
			editorActivityRule.getScenario().onActivity(editorActivity -> {
				// don't take any chances trying to navigate the UI
				// just make sure the thing gets deleted
				try {
					MaybeDocumentFile sketchFolder = editorActivity.getGlobalState().getSketchbookFolder().childDirectory(sketch);
					sketchFolder.delete();
				} catch (MaybeDocumentFile.MaybeDocumentFileException e) {
					e.printStackTrace();
				}
				
				try {
					MaybeDocumentFile sketchFolder = editorActivity.getGlobalState().getSketchbookFolder().childDirectory(bouncyBubbles);
					sketchFolder.delete();
				} catch (MaybeDocumentFile.MaybeDocumentFileException e) {
					e.printStackTrace();
				}
			});
		}
	}
}
