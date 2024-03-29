package com.calsignlabs.apde.tool;

import android.view.MenuItem;
import android.widget.TextView;

import com.calsignlabs.apde.APDE;
import com.calsignlabs.apde.KeyBinding;
import com.calsignlabs.apde.R;
import com.calsignlabs.apde.build.Build;
import com.calsignlabs.apde.build.dag.BuildContext;

import java.io.File;

/**
 * Exports the current sketch as a Gradle project compatible with Android Studio
 * This tool formerly exported Eclipse projects
 */
public class ExportGradleProject implements Tool {
	public static final String PACKAGE_NAME = "com.calsignlabs.apde.tool.ExportGradleProject";
	
	private APDE context;
	
	private boolean exporting;
	
	@Override
	public void init(APDE context) {
		this.context = context;
		
		exporting = false;
	}
	
	@Override
	public String getMenuTitle() {
		return context.getResources().getString(R.string.export_gradle_project);
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
		
		// Don't try to export if we're already exporting...
		if (exporting) {
			return;
		}
		
		new Thread(() -> {
			exporting = true;
			// TODO
			System.out.println("not yet implemented");
			exporting = false;
		}).start();
	}
	
	@Override
	public KeyBinding getKeyBinding() {
		return context.getEditor().getKeyBindings().get("export_gradle_project");
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