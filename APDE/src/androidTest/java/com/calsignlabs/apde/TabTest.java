package com.calsignlabs.apde;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.clearText;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.swipeRight;
import static androidx.test.espresso.action.ViewActions.typeText;
import static androidx.test.espresso.assertion.ViewAssertions.doesNotExist;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withClassName;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withParent;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import static com.calsignlabs.apde.TestUtil.newSketch;
import static com.calsignlabs.apde.TestUtil.onDialogEditText;
import static com.calsignlabs.apde.TestUtil.openMenu;
import static com.calsignlabs.apde.TestUtil.sleep;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.endsWith;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.test.espresso.ViewInteraction;
import androidx.test.filters.LargeTest;

import com.calsignlabs.apde.support.MaybeDocumentFile;

import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

/**
 * Test sketch tabs.
 */
@LargeTest
public class TabTest extends BaseTest {
	public static final String DEFAULT_TAB_NAME = "sketch";
	
	protected ViewInteraction onTab(String tabName) {
		return onView(allOf(withClassName(endsWith("TabView")), withContentDescription(tabName)));
	}
	
	/**
	 * Test behavior when all tabs are deleted, and that a new tab can still be created.
	 */
	@Test
	public void noTabsTest() {
		newSketch();
		
		onTab(DEFAULT_TAB_NAME)
				.perform(click());
		
		onView(withText(R.string.editor_menu_tab_delete))
				.check(matches(isDisplayed()))
				.perform(click());
		
		onView(withText(R.string.delete))
				.inRoot(isDialog())
				.perform(click());
		
		onView(withId(R.id.code))
				.check(doesNotExist());
		
		onView(withId(R.id.menu_run))
				.check(doesNotExist());
		
		openMenu();
		onView(withText(R.string.editor_menu_tab_new))
				.check(matches(isDisplayed()))
				.perform(click());
		
		onDialogEditText()
				.perform(typeText(DEFAULT_TAB_NAME));
		
		onView(withText(R.string.ok))
				.inRoot(isDialog())
				.perform(click());
		
		onView(withId(R.id.menu_run))
				.check(matches(isDisplayed()));
		
		onView(withParent(withId(R.id.code_pager_tabs)))
				.check(matches(isDisplayed()));
	}
	
	/**
	 * Test having 3+ tabs.
	 */
	@Test
	public void manyTabsTest() {
		// this test takes ~16 seconds. reduce N to make it faster.
		int N = 5;
		
		newSketch();
		
		String lastTab = DEFAULT_TAB_NAME;
		
		for (int n = 1; n <= N; n++) {
			onTab(lastTab)
					.perform(click());
			
			onView(withText(R.string.editor_menu_tab_new))
					.check(matches(isDisplayed()))
					.perform(click());
			
			lastTab = "tab" + n;
			onDialogEditText()
					.perform(typeText(lastTab));
			
			onView(withText(R.string.ok))
					.inRoot(isDialog())
					.perform(click());
			
			onView(allOf(withId(R.id.code), isDisplayed()))
					.perform(typeText("// " + lastTab));
		}
		
		for (int n = N; n > 0; n--) {
			onView(allOf(withId(R.id.code), isDisplayed()))
					.check(matches(withText("// tab" + n)));
			
			onView(withId(R.id.code_pager))
					.perform(swipeRight());
			
			// Wait for the animation
			sleep(200);
		}
	}
	
	/**
	 * Test renaming tabs, making sure that the file is renamed on disk.
	 */
	@Test
	public void renameTabTest() {
		newSketch();
		
		String renamedTabName = "differentFile";
		
		editorActivityRule.getScenario().onActivity(editorActivity -> {
			// make sure the file exists
			editorActivity.autoSave();
			
			try {
				MaybeDocumentFile sketchPde = editorActivity.getGlobalState().getSketchLocation().child(DEFAULT_TAB_NAME + ".pde", SketchFile.PDE_MIME_TYPE);
				assertTrue(sketchPde.exists());
			} catch (MaybeDocumentFile.MaybeDocumentFileException e) {
				throw new RuntimeException(e);
			}
		});
		
		onTab(DEFAULT_TAB_NAME)
				.perform(click());
		
		onView(withText(R.string.editor_menu_tab_rename))
				.check(matches(isDisplayed()))
				.perform(click());
		
		onDialogEditText()
				.perform(clearText(), typeText(renamedTabName));
		
		onView(withText(R.string.ok))
				.inRoot(isDialog())
				.perform(click());
		
		editorActivityRule.getScenario().onActivity(editorActivity -> {
			// make sure the files are updated
			editorActivity.autoSave();
			
			try {
				MaybeDocumentFile sketchFolder = editorActivity.getGlobalState().getSketchLocation();
				MaybeDocumentFile sketchPde = sketchFolder.child(DEFAULT_TAB_NAME + ".pde", SketchFile.PDE_MIME_TYPE);
				MaybeDocumentFile renamedPde = sketchFolder.child(renamedTabName + ".pde", SketchFile.PDE_MIME_TYPE);
				
				assertFalse(sketchPde.exists());
				assertTrue(renamedPde.exists());
			} catch (MaybeDocumentFile.MaybeDocumentFileException e) {
				e.printStackTrace();
			}
		});
	}
	
	/**
	 * Test creating new tabs and renaming tabs, making sure that name conflicts are not permitted.
	 */
	@Test
	public void noDuplicateTabsTest() {
		newSketch();
		
		onTab(DEFAULT_TAB_NAME)
				.perform(click());
		
		onView(withText(R.string.editor_menu_tab_new))
				.check(matches(isDisplayed()))
				.perform(click());
		
		onDialogEditText()
				.perform(typeText(DEFAULT_TAB_NAME));
		
		onView(withText(R.string.ok))
				.inRoot(isDialog())
				.perform(click());
		
		// make sure there is only one "sketch" tab (if a new one got created, there would be an
		// error due to ambiguous selection)
		onTab(DEFAULT_TAB_NAME)
				.check(matches(isDisplayed()));
		
		// TODO: this check is flaky because build messages are clobbering it
//		onView(withId(R.id.message))
//				.check(matches(withText(R.string.tab_name_invalid_same_title)));
		
		onTab(DEFAULT_TAB_NAME)
				.perform(click());
		
		onView(withText(R.string.editor_menu_tab_new))
				.check(matches(isDisplayed()))
				.perform(click());
		
		String anotherTab = "anotherTab";
		
		onDialogEditText()
				.perform(typeText(anotherTab));
		
		onView(withText(R.string.ok))
				.inRoot(isDialog())
				.perform(click());
		
		onTab(anotherTab)
				.perform(click());
		
		onView(withText(R.string.editor_menu_tab_rename))
				.check(matches(isDisplayed()))
				.perform(click());
		
		onDialogEditText()
				.perform(clearText(), typeText(DEFAULT_TAB_NAME));
		
		onView(withText(R.string.ok))
				.inRoot(isDialog())
				.perform(click());
		
		// make sure the tab was not renamed
		onTab(anotherTab)
				.check(matches(isDisplayed()));
		
//		onView(withId(R.id.message))
//				.check(matches(withText(R.string.tab_name_invalid_same_title)));
	}
	
	/**
	 * Test that deleting a tab also removes the file from disk.
	 */
	@Test
	public void deleteTabTest() {
		newSketch();
		
		String newFile = "newFile";
		
		onTab(DEFAULT_TAB_NAME)
				.perform(click());
		
		onView(withText(R.string.editor_menu_tab_new))
				.check(matches(isDisplayed()))
				.perform(click());
		
		onDialogEditText()
				.perform(typeText(newFile));
		
		onView(withText(R.string.ok))
				.inRoot(isDialog())
				.perform(click());
		
		// need to select twice - first to switch, second to open dialog
		onTab(DEFAULT_TAB_NAME)
				.perform(click(), click());
		
		onView(withText(R.string.editor_menu_tab_delete))
				.check(matches(isDisplayed()))
				.perform(click());
		
		onView(withText(R.string.delete))
				.inRoot(isDialog())
				.perform(click());
		
		editorActivityRule.getScenario().onActivity(editorActivity -> {
			// make sure the files are updated
			editorActivity.autoSave();
			
			try {
				MaybeDocumentFile sketchFolder = editorActivity.getGlobalState().getSketchLocation();
				MaybeDocumentFile sketchPde = sketchFolder.child(DEFAULT_TAB_NAME + ".pde", SketchFile.PDE_MIME_TYPE);
				MaybeDocumentFile renamedPde = sketchFolder.child(newFile + ".pde", SketchFile.PDE_MIME_TYPE);
				
				assertFalse(sketchPde.exists());
				assertTrue(renamedPde.exists());
			} catch (MaybeDocumentFile.MaybeDocumentFileException e) {
				e.printStackTrace();
			}
		});
	}
	
	/**
	 * Make sure tabs get preserved when closing and re-opening the app.
	 */
	@Test
	public void tabsReopenedTest() {
		newSketch();
		
		String newFile = "newFile";
		String code = "// some code";
		
		onTab(DEFAULT_TAB_NAME)
				.perform(click());
		
		onView(withText(R.string.editor_menu_tab_new))
				.perform(click());
		
		onDialogEditText()
				.perform(typeText(newFile));
		
		onView(withText(R.string.ok))
				.inRoot(isDialog())
				.perform(click());
		
		onView(allOf(withId(R.id.code), isDisplayed()))
				.perform(typeText(code));
		
		// close and re-open APDE
		editorActivityRule.getScenario().recreate();
		
		onTab(DEFAULT_TAB_NAME)
				.check(matches(isDisplayed()));
		
		// TODO this tab is still selected when re-opening, which I find surprising
		onTab(newFile)
				.check(matches(isDisplayed()));
		
		onView(allOf(withId(R.id.code), isDisplayed()))
				.check(matches(withText(code)));
	}
}
