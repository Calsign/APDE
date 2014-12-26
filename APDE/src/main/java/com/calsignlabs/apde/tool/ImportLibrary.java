package com.calsignlabs.apde.tool;

import android.view.MenuItem;

import com.calsignlabs.apde.APDE;
import com.calsignlabs.apde.KeyBinding;
import com.calsignlabs.apde.R;

/**
 * Launches EditorActivity's "Import Library" dialog - this class is just a wrapper.
 */
public class ImportLibrary implements Tool {
	public static final String PACKAGE_NAME = "com.calsignlabs.apde.tool.ImportLibrary";
	
	private APDE context;
	
	@Override
	public void init(APDE context) {
		this.context = context;
	}
	
	@Override
	public String getMenuTitle() {
		return context.getResources().getString(R.string.import_library);
	}
	
	@Override
	public void run() {
		context.getEditor().launchImportLibrary();
	}
	
	@Override
	public KeyBinding getKeyBinding() {
		return context.getEditor().getKeyBindings().get("import_library");
	}
	
	@Override
	public boolean showInToolsMenu(APDE.SketchLocation sketchLocation) {
		return !sketchLocation.isExample();
	}
	
	@Override
	public boolean createSelectionActionModeMenuItem(MenuItem convert) {
		return false;
	}
}