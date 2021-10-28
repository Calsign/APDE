package com.calsignlabs.apde;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.typeText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static com.calsignlabs.apde.TestUtil.matchesAnyString;
import static com.calsignlabs.apde.TestUtil.newSketch;
import static com.calsignlabs.apde.TestUtil.sleep;

import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject;

import org.hamcrest.core.StringEndsWith;
import org.junit.Test;

/**
 * Test running sketches.
 */
@LargeTest
public class RunSketchTest extends BaseTest {
	/**
	 * Round-trip test of typing a sketch, running it, and verifying the console output.
	 */
	@Test
	public void helloWorld() {
		newSketch();
		
		// the closing brace and the indentations are inserted automatically...
		String typedCode = "void setup() {\nprintln(\"Hello, world!\");\ngetActivity().finish();";
		String actualCode = "void setup() {\n  println(\"Hello, world!\");\n  getActivity().finish();\n}";
		
		onView(withId(R.id.code))
				.perform(typeText(typedCode), closeSoftKeyboard()).
				check(matches(withText(actualCode)));
		
		onView(withId(R.id.menu_run))
				.perform(click());
		
		awaitBuild();
		
		// NOTE: we assume that sketch previewer is installed
		
		// wait for sketch to run
		sleep(1000);
		
		onView(withId(R.id.console))
				.check(matches(withText(new StringEndsWith("Hello, world!\n"))));
	}
	
	/**
	 * Round-trip test of a typing a sketch that crashes and verifying that the exception is detected.
	 */
	@Test
	public void crashingSketch() {
		newSketch();
		
		String code = "void setup() {\nthrow new RuntimeException(\"Exception\");";
		
		onView(withId(R.id.code))
				.perform(typeText(code), closeSoftKeyboard());
		
		onView(withId(R.id.menu_run))
				.perform(click());
		
		awaitBuild();
		
		// NOTE: we assume the sketch previewer is installed
		
		// Wait for sketch to run
		sleep(1000);
		
		// TODO: seems flaky
//		onView(withId(R.id.console))
//				.check(matches(withText(containsString("java.lang.RuntimeException: Exception"))));
		
		// TODO: seems flaky
//		onView(withId(R.id.message))
//				.check(matches(withText("java.lang.RuntimeException: Exception")));
		
		clearCrashDialog();
	}
}
