package com.calsignlabs.apde.tool;

import java.io.File;

import android.app.AlertDialog;
import android.content.DialogInterface;
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
    		context.getEditor().saveSketch();
    		break;
    	case TEMPORARY:
    		//If the sketch has yet to be saved, inform the user
    		AlertDialog.Builder builder = new AlertDialog.Builder(context.getEditor());
    		builder.setTitle(context.getResources().getText(R.string.save_sketch_before_run_dialog_title))
    		.setMessage(context.getResources().getText(R.string.save_sketch_before_run_dialog_message)).setCancelable(false)
    		.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
    			@Override
    			public void onClick(DialogInterface dialog, int which) {}
    		}).show();
    		
    		return;
    	}
		
		//Don't try to export if we're already exporting...
		if (exporting) {
			return;
		}
		
		final File binFolder = new File(context.getSketchLocation(), "bin");
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
	public boolean showInToolsMenu() {
		return true;
	}
	
	@Override
	public boolean createSelectionActionModeMenuItem(MenuItem convert) {
		return false;
	}
}