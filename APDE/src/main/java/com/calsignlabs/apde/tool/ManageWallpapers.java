package com.calsignlabs.apde.tool;

import android.app.WallpaperManager;
import android.content.Intent;
import android.view.MenuItem;

import com.calsignlabs.apde.APDE;
import com.calsignlabs.apde.KeyBinding;
import com.calsignlabs.apde.R;

/**
 * Launches Android's native live wallpaper selector. This tool is just a wrapper.
 * This tool is called "Live Wallpapers" in the tools menu.
 */
public class ManageWallpapers implements Tool {
	public static final String PACKAGE_NAME = "com.calsignlabs.apde.tool.ManageWallpapers";
	
	private APDE context;
	
	@Override
	public void init(APDE context) {
		this.context = context;
	}
	
	@Override
	public String getMenuTitle() {
		return context.getResources().getString(R.string.tool_manage_wallpapers);
	}
	
	@Override
	public KeyBinding getKeyBinding() {
		return null;
	}
	
	@Override
	public boolean showInToolsMenu(APDE.SketchLocation sketchLocation) {
		return true;
	}
	
	@Override
	public boolean createSelectionActionModeMenuItem(MenuItem convert) {
		return false;
	}
	
	@Override
	public void run() {
		Intent intent = new Intent();
		intent.setAction(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER);
		context.getEditor().startActivity(intent);
	}
}
