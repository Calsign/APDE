package com.calsignlabs.apde.tool;

import android.view.MenuItem;

import com.calsignlabs.apde.APDE;
import com.calsignlabs.apde.KeyBinding;
import com.calsignlabs.apde.R;

/**
 * "Manage Libraries" button, shown when "Import Library" is hidden so that we still provide the installation functionality
 */
public class ManageLibraries implements Tool {
	public static final String PACKAGE_NAME = "com.calsignlabs.apde.tool.ManageLibraries";
	
	private APDE context;
	
	@Override
	public void init(APDE context) {
		this.context = context;
	}
	
	@Override
	public String getMenuTitle() {
		return context.getResources().getString(R.string.library_manager_open);
	}
	
	@Override
	public void run() {
		context.getEditor().launchManageLibraries();
	}
	
	@Override
	public KeyBinding getKeyBinding() {
		return null;
	}
	
	@Override
	public boolean showInToolsMenu(APDE.SketchLocation sketchLocation) {
		return sketchLocation.isExample();
	}
	
	@Override
	public boolean createSelectionActionModeMenuItem(MenuItem convert) {
		return false;
	}
}