package com.calsignlabs.apde.sketchpreview;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import processing.android.CompatUtils;
import processing.android.PFragment;
import processing.core.PApplet;

/**
 * This activity substitutes for MainActivity when running in preview mode.
 */
public class PreviewActivity extends AppCompatActivity {
	private PApplet sketch;
	private FrameLayout frame;
	
	private BroadcastReceiver broadcastReceiver;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		frame = new FrameLayout(this);
		frame.setId(CompatUtils.getUniqueViewId());
		setContentView(frame, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.MATCH_PARENT));
		
		// We receive a message when a new sketch is going to be loaded
		// We need to finish the activity so that it can be recreated for the new sketch
		broadcastReceiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				if (intent.getAction() != null && intent.getAction().equals("com.calsignlabs.apde.STOP_SKETCH_PREVIEW")) {
					finish();
				}
			}
		};
		
		registerReceiver(broadcastReceiver, new IntentFilter("com.calsignlabs.apde.STOP_SKETCH_PREVIEW"));
		
		if (getIntent().getAction() != null && getIntent().getAction().equals("com.calsignlabs.apde.RUN_SKETCH_PREVIEW")) {
			init();
		} else {
			// Automatically close - this shouldn't ever happen
			finish();
		}
	}
	
	@Override
	public void onDestroy() {
		unregisterReceiver(broadcastReceiver);
		super.onDestroy();
	}
	
	protected void init() {
		Intent intent = getIntent();
		
		// Get all of the sketch data from the intent
		String dexFile = intent.getStringExtra("SKETCH_DEX");
		String dataFolder = intent.getStringExtra("SKETCH_DATA_FOLDER");
		String[] dexedLibs = intent.getStringArrayExtra("SKETCH_DEXED_LIBS");
		final String orientation = intent.getStringExtra("SKETCH_ORIENTATION");
		String packageName = intent.getStringExtra("SKETCH_PACKAGE_NAME");
		String className = intent.getStringExtra("SKETCH_CLASS_NAME");
		
		PreviewUtil.clearAssets(this);
		PreviewUtil.clearSharedPrefs(this);
		PreviewUtil.clearDexedLibs(this);
		
		PreviewUtil.setupSketchDex(this, dexFile);
		PreviewUtil.copyAssets(this, dataFolder);
		PreviewUtil.setupDexedLibs(this, dexedLibs);
		
		// Reflection voodoo
		sketch = PreviewUtil.loadSketchPApplet(this, packageName, className);
		
		// Redirect console output
		System.setOut(new java.io.PrintStream(new APDEInternalLogBroadcasterUtil.APDEInternalConsoleStream('o', this)));
		System.setErr(new java.io.PrintStream(new APDEInternalLogBroadcasterUtil.APDEInternalConsoleStream('e', this)));
		
		Thread.setDefaultUncaughtExceptionHandler(new APDEInternalLogBroadcasterUtil.APDEInternalExceptionHandler(this));
		
		if (sketch != null) {
			PFragment fragment = new PFragment(sketch);
			fragment.setView(frame, this);
			
			// If the sketch calls orientation(), then that messes us up
			// So we wait for the sketch to call orientation() and then we only set the orientation
			// defined in the manifest if the sketch has not done so
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					if (getRequestedOrientation() == ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
						// FYI this orientation comes from the sketch's manifest
						setOrientation(orientation);
					}
				}
			});
		}
	}
	
	@Override
	public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
		if (sketch != null) {
			sketch.onRequestPermissionsResult(requestCode, permissions, grantResults);
		}
	}
	
	@Override
	public void onNewIntent(Intent intent) {
		if (sketch != null) {
			sketch.onNewIntent(intent);
		}
	}
	
	private void setOrientation(String orientation) {
		// Set correct orientation
		int orientationConst;
		switch (orientation) {
			case "portrait":
				orientationConst = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
				break;
			case "landscape":
				orientationConst = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
				break;
			case "reverseLandscape":
				orientationConst = ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
				break;
			case "unspecified":
			default:
				orientationConst = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
				break;
		}
		setRequestedOrientation(orientationConst);
	}
}
