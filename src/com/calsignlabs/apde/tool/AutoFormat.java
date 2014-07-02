package com.calsignlabs.apde.tool;

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
		return context.getResources().getString(R.string.auto_format);
	}
	
	@Override
	public void run() {
		if(!context.isExample()) {
			CodeEditText code = (CodeEditText) context.getEditor().findViewById(R.id.code);
			
			processing.app.Preferences.setInteger("editor.tabs.size", 2);
			
			code.setUpdateText((new processing.mode.java.AutoFormat()).format(code.getText().toString()));
			code.clearTokens();
			
			context.getEditor().message(context.getResources().getString(R.string.auto_formatter_complete));
		}
	}
	
	@Override
	public KeyBinding getKeyBinding() {
		return context.getEditor().getKeyBindings().get("auto_format");
	}
}