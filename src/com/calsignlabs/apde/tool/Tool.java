package com.calsignlabs.apde.tool;

import java.lang.Runnable;

import com.calsignlabs.apde.APDE;
import com.calsignlabs.apde.KeyBinding;

public interface Tool extends Runnable {
	public void init(APDE context);
	
	/**
	 * @return the tool name to be displayed in the tool menu
	 */
	public String getMenuTitle();
	
	/**
	 * Returns a key binding that will run this tool. Key bindings are NOT required.
	 * 
	 * @return the key binding
	 */
	public KeyBinding getKeyBinding();
}