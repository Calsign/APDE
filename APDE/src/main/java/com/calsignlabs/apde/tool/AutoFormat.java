package com.calsignlabs.apde.tool;

import android.view.MenuItem;

import com.calsignlabs.apde.APDE;
import com.calsignlabs.apde.CodeEditText;
import com.calsignlabs.apde.KeyBinding;
import com.calsignlabs.apde.R;

/**
 * Runs the code through Processing's Auto Formatter
 */
public class AutoFormat implements Tool {
	public static final String PACKAGE_NAME = "com.calsignlabs.apde.tool.AutoFormat";
	
	private APDE context;
	
	@Override
	public void init(APDE context) {
		this.context = context;
	}
	
	@Override
	public String getMenuTitle() {
		return context.getResources().getString(R.string.tool_auto_format);
	}
	
	@Override
	public void run() {
		if(!context.isExample()) {
			CodeEditText code = (CodeEditText) context.getEditor().findViewById(R.id.code);
			
			processing.app.Preferences.setInteger("editor.tabs.size", 2);
			
			code.setUpdateText((new processing.mode.java.AutoFormat()).format(code.getText().toString()));
			code.clearTokens();
			
			context.getEditor().message(context.getResources().getString(R.string.tool_auto_format_success));
		}
	}
	
	@Override
	public KeyBinding getKeyBinding() {
		return context.getEditor().getKeyBindings().get("auto_format");
	}
	
	@Override
	public boolean showInToolsMenu(APDE.SketchLocation sketchLocation) {
		return !sketchLocation.isExample();
	}
	
	@Override
	public boolean createSelectionActionModeMenuItem(MenuItem convert) {
		//TODO maybe support auto formatting of selection, not just the entire file
		return false;
	}
}