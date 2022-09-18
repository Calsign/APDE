package com.calsignlabs.apde;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.doesNotExist;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.calsignlabs.apde.TestUtil.deleteFile;
import static com.calsignlabs.apde.TestUtil.isPackageInstalled;
import static com.calsignlabs.apde.TestUtil.matchesAnyString;
import static com.calsignlabs.apde.TestUtil.openMenu;
import static com.calsignlabs.apde.TestUtil.performInstall;
import static com.calsignlabs.apde.TestUtil.sleep;

import static org.hamcrest.Matchers.allOf;

import android.Manifest;

import androidx.test.espresso.NoMatchingRootException;
import androidx.test.espresso.NoMatchingViewException;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.GrantPermissionRule;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject;
import androidx.test.uiautomator.UiObjectNotFoundException;

import com.calsignlabs.apde.build.ExtractStaticBuildResources;
import com.calsignlabs.apde.build.StaticBuildResources;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutionException;

@RunWith(AndroidJUnit4.class)
public abstract class BaseTest {
	/**
	 * Delete all app data, including shared preferences, temporary sketches, etc.
	 */
	private static void eraseData() {
		// we want everything, not just the "data" directory
		File appData = getInstrumentation().getTargetContext().getFilesDir().getParentFile();
		for (File file : appData.listFiles()) {
			deleteFile(file, false);
		}
	}
	
	@BeforeClass
	public static void performBeforeClass() {
		// TODO: this is causing problems for some reason.
		// eraseData();
	}
	
	@Rule
	public ActivityScenarioRule<EditorActivity> editorActivityRule
			= new ActivityScenarioRule<>(EditorActivity.class);
	
	@Rule
	public GrantPermissionRule permissionsRule
			= GrantPermissionRule.grant(Manifest.permission.WRITE_EXTERNAL_STORAGE);
	
	public void clearCrashDialog() {
		// TODO: it may be possible to make this better with UiWatcher or something
		
		// on some API versions we get this "APDE Sketch Previewer has stopped" dialog
		// (or "Unfortunately, APDE Sketch Previewer has stopped.")
		// need to close it so that the rest of the tests can run OK
		UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
		UiObject hasStopped = device.findObject(matchesAnyString(".*has stopped.*"));
		if (hasStopped.waitForExists(1000)) {
			UiObject ok = device.findObject(matchesAnyString("OK"));
			if (ok.waitForExists(200)) {
				try {
					ok.click();
				} catch (UiObjectNotFoundException e) {
					e.printStackTrace();
				}
			} else {
				device.pressBack();
			}
		}
	}
	
	/**
	 * Hide the "what's new" and examples update dialogs on startup.
	 */
	private void dismissStartupDialogs() {
		// Try to close the what's new dialog
		try {
			onView(withText(R.string.ok))
					.check(matches(isDisplayed()))
					.perform(click());
		} catch (NoMatchingViewException | NoMatchingRootException e) {
			// shrug
		}
		
		// Try to close the examples updates dialog
		try {
			onView(withText(R.string.cancel))
					.check(matches(isDisplayed()))
					.perform(click());
		} catch (NoMatchingViewException | NoMatchingRootException e) {
			// shrug
		}
	}
	
	/**
	 * Install the sketch previewer if it isn't already installed.
	 */
	public void installSketchPreviewerIfNeeded() {
		if (!isPackageInstalled("com.calsignlabs.apde.sketchpreview")) {
			openMenu();
			
			onView(withText(R.string.menu_open_settings))
					.check(matches(isDisplayed()))
					.perform(click());
			
			onView(withText(R.string.pref_build))
					.check(matches(isDisplayed()))
					.perform(click());
			
			performInstall(false, () -> {
				onView(withText(R.string.pref_build_preview_reinstall))
						.check(matches(isDisplayed()))
						.perform(click());
				
				// wait for sketch previewer to build
				// TODO: somehow use an idling resource
				sleep(2000);
			});
			
			// wait for install to finish
			sleep(2000);
			
			pressBack();
			pressBack();
		}
	}
	
	/**
	 * Extract static build resources. These often get corrupted during tests because we kill
	 * things willy-nilly.
	 */
	public void extractStaticBuildResources() {
		try {
			StaticBuildResources.extractAll(getInstrumentation().getTargetContext());
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	@Before
	public void performBefore() {
		clearCrashDialog();
		dismissStartupDialogs();
		installSketchPreviewerIfNeeded();
		extractStaticBuildResources();
	}
	
	protected void awaitBuild() {
		editorActivityRule.getScenario().onActivity(editorActivity -> {
			try {
				editorActivity.autoCompileTask.get();
			} catch (ExecutionException | InterruptedException e) {
				e.printStackTrace();
				throw new RuntimeException(e);
			}
		});
	}
	
	/**
	 * Stop the timer-triggered tasks (auto-build and auto-save).
	 */
	private void shutdownTimers() {
		editorActivityRule.getScenario().onActivity(editorActivity -> {
			editorActivity.autoCompileTimer.shutdownNow();
			editorActivity.autoSaveTimer.shutdownNow();
		});
	}
	
	@After
	public void performAfter() {
		shutdownTimers();
		clearCrashDialog();
	}
}
