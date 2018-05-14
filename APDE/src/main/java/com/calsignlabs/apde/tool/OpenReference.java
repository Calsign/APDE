package com.calsignlabs.apde.tool;

import android.content.Intent;
import android.view.MenuItem;

import com.calsignlabs.apde.APDE;
import com.calsignlabs.apde.KeyBinding;
import com.calsignlabs.apde.R;
import com.calsignlabs.apde.ReferenceActivity;

public class OpenReference implements Tool {
	public static final String PACKAGE_NAME = "com.calsignlabs.apde.tool.OpenReference";
	
	private APDE context;
	
	@Override
	public void init(APDE context) {
		this.context = context;
	}
	
	@Override
	public String getMenuTitle() {
		return context.getResources().getString(R.string.tool_open_reference);
	}
	
	@Override
	public KeyBinding getKeyBinding() {
		return context.getEditor().getKeyBindings().get("open_reference");
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
		context.getEditor().runOnUiThread(new Runnable() {
			@Override
			public void run() {
				context.getEditor().startActivity(new Intent(context.getEditor(), ReferenceActivity.class));
			}
		});
	}
}
