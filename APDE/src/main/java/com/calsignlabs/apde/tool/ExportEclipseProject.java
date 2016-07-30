package com.calsignlabs.apde.tool;

import android.view.MenuItem;
import android.widget.TextView;

import com.calsignlabs.apde.APDE;
import com.calsignlabs.apde.KeyBinding;
import com.calsignlabs.apde.R;
import com.calsignlabs.apde.build.Build;

import java.io.File;

/**
 * Exports the current sketch as an Eclipse-compatible Android project
 */
public class ExportEclipseProject implements Tool {
	public static final String PACKAGE_NAME = "com.calsignlabs.apde.tool.ExportEclipseProject";
	
	private APDE context;
	
	private boolean exporting;
	
	@Override
	public void init(APDE context) {
		this.context = context;
		
		exporting = false;
	}
	
	@Override
	public String getMenuTitle() {
		return context.getResources().getString(R.string.export_eclipse_project);
	}
	
	@Override
	public void run() {
		switch(context.getSketchLocationType()) {
    	case EXAMPLE:
    	case LIBRARY_EXAMPLE:
    		break;
    	case SKETCHBOOK:
    	case EXTERNAL:
		case TEMPORARY:
    		context.getEditor().saveSketch();
    		break;
    	}
		
		//Don't try to export if we're already exporting...
		if (exporting) {
			return;
		}
		
		//If this is an example, then put the sketch in the "bin" directory within the sketchbook
		final File binFolder = new File((context.isExample() || context.isTemp()) ? context.getSketchbookFolder() : context.getSketchLocation(), "bin");
		final File exportFolder = new File(binFolder, "export");
		
		binFolder.mkdir();
		exportFolder.mkdir();
		
		//Clear the console
    	((TextView) context.getEditor().findViewById(R.id.console)).setText("");
		
		final Build builder = new Build(context);
		
		new Thread(new Runnable() {
			public void run() {
				exporting = true;
				builder.exportAndroidEclipseProject(exportFolder, "debug");
				exporting = false;
			}
		}).start();
	}
	
	@Override
	public KeyBinding getKeyBinding() {
		return context.getEditor().getKeyBindings().get("export_eclipse_project");
	}
	
	@Override
	public boolean showInToolsMenu(APDE.SketchLocation sketchLocation) {
		return true;
	}
	
	@Override
	public boolean createSelectionActionModeMenuItem(MenuItem convert) {
		return false;
	}
}