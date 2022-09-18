package com.calsignlabs.apde;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.swipeUp;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static com.calsignlabs.apde.TestUtil.openDrawer;
import static com.calsignlabs.apde.TestUtil.openMenu;
import static com.calsignlabs.apde.TestUtil.sleep;

import android.view.View;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.junit.After;
import org.junit.Test;

import java.io.IOException;

public class DownloadExamplesTest extends BaseTest {
	/**
	 * Given a matcher that matches multiple views, match the view at the given index into the set
	 * of matched views.
	 *
	 * @param matcher the inner matcher
	 * @param index the index to select
	 * @return the outer matcher
	 */
	private static Matcher<View> matchIndex(final Matcher<View> matcher, final int index) {
		return new BaseMatcher<View>() {
			int count;
			
			@Override
			public boolean matches(Object item) {
				if (matcher.matches(item)) {
					if (count == index) {
						count++;
						return true;
					} else {
						count++;
					}
				}
				
				return false;
			}
			
			@Override
			public void describeTo(Description description) {
				description.appendText(String.format("Match at index %d of: ", index));
				matcher.describeTo(description);
			}
		};
	}
	
	/**
	 * Test downloading the examples, and make sure they exist.
	 */
	@Test
	public void downloadExamplesTest() {
		openMenu();
		
		onView(withText(R.string.title_activity_settings)).perform(click());
		onView(withText(R.string.pref_general)).perform(click());
		onView(withId(R.id.settings_fragment_container)).perform(swipeUp());
		// there are two views with this text, need to match the second one
		onView(matchIndex(withText(R.string.pref_examples_updates), 1)).perform(click());
		onView(withText(R.string.examples_update_settings_download_now)).perform(click());
		
		pressBack();
		pressBack();
		pressBack();
		
		// wait for examples to download
		sleep(4000);
		
		openDrawer();
		
		onView(withText(R.string.drawer_folder_examples)).perform(click());
		onView(withText("Basics")).perform(click());
	}
	
	/**
	 * It's important to remove the downloaded examples so that other subsequent tests work
	 * correctly.
	 */
	@After
	public void cleanUp() {
		editorActivityRule.getScenario().onActivity(editorActivity -> {
			try {
				APDE.deleteFile(editorActivity.getGlobalState().getExamplesRepoFolder());
			} catch (IOException e) {
				e.printStackTrace();
			}
		});
	}
}
