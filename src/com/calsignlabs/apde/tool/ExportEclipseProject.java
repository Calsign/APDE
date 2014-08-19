package com.calsignlabs.apde.tool;

import java.io.File;

import android.view.MenuItem;
import android.widget.TextView;

import com.calsignlabs.apde.APDE;
import com.calsignlabs.apde.KeyBinding;
import com.calsignlabs.apde.R;
import com.calsignlabs.apde.build.Build;

/**
 * Exports the current sketch as an Eclipse-compatible Android project
 */
public class ExportEclipseProject implements Tool {
	public static final String PACKAGE_NAME = "com.calsignlabs.apde.tool.ExportEclipseProject";
	
	private APDE context;
	
	@Override
	public void init(APDE context) {
		this.context = context;
	}
	
	@Override
	public String getMenuTitle() {
		return context.getResources().getString(R.string.export_eclipse_project);
	}
	
	@Override
	public void run() {
		final File binFolder = new File(context.getSketchLocation(), "bin");
		final File exportFolder = new File(binFolder, "export");
		
		binFolder.mkdir();
		exportFolder.mkdir();
		
		//Clear the console
    	((TextView) context.getEditor().findViewById(R.id.console)).setText("");
		
		final Build builder = new Build(context);
		
		new Thread(new Runnable() {
			public void run() {
				builder.exportAndroidEclipseProject(exportFolder, "debug");
			}
		}).start();
	}
	
	@Override
	public KeyBinding getKeyBinding() {
		return context.getEditor().getKeyBindings().get("export_eclipse_project");
	}
	
	@Override
	public boolean showInToolsMenu() {
		return true;
	}
	
	@Override
	public boolean createSelectionActionModeMenuItem(MenuItem convert) {
		return false;
	}
}