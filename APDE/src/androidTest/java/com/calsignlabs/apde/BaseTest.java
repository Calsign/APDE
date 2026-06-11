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
import android.os.Build;
import android.os.ParcelFileDescriptor;

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
import org.junit.rules.ExternalResource;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileInputStream;
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

	private static void executeShellCommand(String command) throws IOException {
		try (ParcelFileDescriptor fd = getInstrumentation().getUiAutomation().executeShellCommand(command);
				FileInputStream input = new FileInputStream(fd.getFileDescriptor())) {
			byte[] buffer = new byte[1024];
			while (input.read(buffer) != -1) {
				// Read until EOF so the command completes before the activity launches.
			}
		}
	}

	private static void grantManageExternalStoragePermission() {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
			return;
		}

		String packageName = getInstrumentation().getTargetContext().getPackageName();
		try {
			executeShellCommand("appops set --uid " + packageName + " MANAGE_EXTERNAL_STORAGE allow");
			executeShellCommand("appops set " + packageName + " MANAGE_EXTERNAL_STORAGE allow");
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	
	@BeforeClass
	public static void performBeforeClass() {
		// TODO: this is causing problems for some reason.
		// eraseData();
	}
	
	public ActivityScenarioRule<EditorActivity> editorActivityRule
			= new ActivityScenarioRule<>(EditorActivity.class);
	
	public GrantPermissionRule permissionsRule
			= GrantPermissionRule.grant(Manifest.permission.WRITE_EXTERNAL_STORAGE);

	private final TestRule manageExternalStorageRule = new ExternalResource() {
		@Override
		protected void before() {
			grantManageExternalStoragePermission();
		}
	};

	@Rule
	public RuleChain ruleChain = RuleChain
			.outerRule(manageExternalStorageRule)
			.around(permissionsRule)
			.around(editorActivityRule);
	
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
