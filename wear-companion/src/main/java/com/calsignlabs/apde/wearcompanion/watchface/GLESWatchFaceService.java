package com.calsignlabs.apde.wearcompanion.watchface;

import com.calsignlabs.apde.wearcompanion.WatchFaceUtil;

import java.io.PrintStream;

import processing.android.PWatchFaceGLES;
import processing.core.PApplet;

public abstract class GLESWatchFaceService extends PWatchFaceGLES {
	@Override
	public PApplet createSketch() {
		LoggingUtil.initMessageBroadcaster(this);
		System.setOut(new PrintStream(new LoggingUtil.ConsoleStream('o', this)));
		System.setErr(new PrintStream(new LoggingUtil.ConsoleStream('e', this)));
		Thread.setDefaultUncaughtExceptionHandler(new LoggingUtil.ExceptionHandler(this));
		
		return WatchFaceUtil.isSketchGles(this)
				? WatchFaceUtil.loadSketchPApplet(this, true)
				: WatchFaceUtil.getDefaultSketch(true);
	}
	
	public static class A extends GLESWatchFaceService {}
	public static class B extends GLESWatchFaceService {}
}
