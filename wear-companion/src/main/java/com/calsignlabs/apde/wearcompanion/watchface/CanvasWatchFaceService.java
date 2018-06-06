package com.calsignlabs.apde.wearcompanion.watchface;


import com.calsignlabs.apde.wearcompanion.WatchFaceUtil;

import java.io.PrintStream;

import processing.android.PWatchFaceCanvas;
import processing.core.PApplet;

public abstract class CanvasWatchFaceService extends PWatchFaceCanvas {
	@Override
	public PApplet createSketch() {
		LoggingUtil.initMessageBroadcaster(this);
		System.setOut(new PrintStream(new LoggingUtil.ConsoleStream('o', this)));
		System.setErr(new PrintStream(new LoggingUtil.ConsoleStream('e', this)));
		Thread.setDefaultUncaughtExceptionHandler(new LoggingUtil.ExceptionHandler(this));
		
		return WatchFaceUtil.isSketchCanvas(this)
				? WatchFaceUtil.loadSketchPApplet(this, false)
				: WatchFaceUtil.getDefaultSketch(false);
	}
	
	public static class A extends CanvasWatchFaceService {}
	public static class B extends CanvasWatchFaceService {}
}
