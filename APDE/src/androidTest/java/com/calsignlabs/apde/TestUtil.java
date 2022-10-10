package com.calsignlabs.apde;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.contrib.DrawerActions.open;
import static androidx.test.espresso.contrib.DrawerMatchers.isClosed;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withClassName;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static org.hamcrest.Matchers.endsWith;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import android.view.Gravity;

import androidx.test.espresso.NoMatchingViewException;
import androidx.test.espresso.ViewInteraction;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject;
import androidx.test.uiautomator.UiObjectNotFoundException;
import androidx.test.uiautomator.UiScrollable;
import androidx.test.uiautomator.UiSelector;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class TestUtil {
	protected static void deleteFile(File file, boolean throwOnError) {
		try {
			APDE.deleteFile(file);
		} catch (IOException e) {
			if (throwOnError) {
				throw new RuntimeException(e);
			} else {
				e.printStackTrace();
			}
		}
	}
	
	public static void openMenu() {
		openActionBarOverflowOrOptionsMenu(getInstrumentation().getTargetContext());
	}
	
	public static void openDrawer() {
		onView(withId(R.id.drawer))
				.check(matches(isClosed(Gravity.LEFT)))
				.perform(open());
	}
	
	public static void newSketch() {
		openMenu();
		onView(withText(R.string.editor_menu_new_sketch))
				.perform(click());
	}
	
	public static ViewInteraction onDialogEditText() {
		// full class name includes package name
		return onView(withClassName(endsWith("EditText")))
				.inRoot(isDialog());
	}
	
	public static String getString(int resourceId) {
		return InstrumentationRegistry.getInstrumentation().getTargetContext()
				.getResources().getString(resourceId);
	}
	
	public static void sleep(int millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}
	
	public static UiSelector matchesAnyString(String... strings) {
		return new UiSelector().textMatches(String.join("|", strings));
	}
	
	/**
	 * Given an install dialog on the screen, performs the install. Navigates into settings to
	 * enable installation from unknown sources if necessary.
	 *
	 * @param openApp open the app after installing
	 * @param launchInstall runnable to make the installer appear; may be called multiple times
	 */
	public static void performInstall(boolean openApp, Runnable launchInstall) {
		try {
			launchInstall.run();
			
			UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
			
			// TODO: support internationalization of these strings
			
			UiSelector install = matchesAnyString("Install", "INSTALL");
			
			UiObject settings = device.findObject(matchesAnyString("Settings", "SETTINGS"));
			if (settings.waitForExists(200)) {
				settings.click();
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
					device.findObject(matchesAnyString("Allow from this source")).click();
					device.pressBack();
				} else {
					UiSelector unknownSources = matchesAnyString("Unknown sources");
					
					UiScrollable listView = new UiScrollable(new UiSelector());
					listView.setMaxSearchSwipes(20);
					listView.scrollIntoView(unknownSources);
					listView.waitForExists(5000);
					
					device.findObject(unknownSources).click();
					
					UiObject dialogOk = device.findObject(matchesAnyString("OK"));
					if (dialogOk.waitForExists(1000)) {
						dialogOk.click();
					}
					
					device.pressBack();
					
					// we might need to start the whole install over again, grumble grumble...
					UiObject installButton = device.findObject(install);
					if (!installButton.waitForExists(1000)) {
						launchInstall.run();
					}
				}
			}
			
			device.findObject(install).click();
			if (openApp) {
				device.findObject(matchesAnyString("Open", "OPEN")).click();
			} else {
				UiObject done = device.findObject(matchesAnyString("Done", "DONE"));
				if (done.waitForExists(1000)) {
					done.click();
				}
			}
		} catch (UiObjectNotFoundException e) {
			throw new RuntimeException(e);
		}
	}
	
	public static boolean isPackageInstalled(String packageName) {
		List<PackageInfo> pkgs =
				InstrumentationRegistry.getInstrumentation().getContext()
						.getPackageManager().getInstalledPackages(0);
		for (PackageInfo pkg : pkgs) {
			if (pkg.packageName.equals(packageName)) {
				return true;
			}
		}
		return false;
	}
	
	/**
	 * Uninstall the app with the specified package name.
	 *
	 * @param activity the activity
	 * @param packageName the package name
	 * @param errorIfNotInstalled throw an exception if the package is not installed
	 */
	public static void uninstallApp(Activity activity, String packageName, boolean errorIfNotInstalled) {
		if (!isPackageInstalled(packageName)) {
			if (errorIfNotInstalled) {
				throw new RuntimeException("Tried to uninstall package that was not installed: " + packageName);
			}
			return;
		}
		
		Intent uninstallIntent = new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + packageName));
		activity.startActivity(uninstallIntent);
		
		try {
			UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
			device.findObject(matchesAnyString("OK")).click();
		} catch (UiObjectNotFoundException e) {
			e.printStackTrace();
		}
	}
}
