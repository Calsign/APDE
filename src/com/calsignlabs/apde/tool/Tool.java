package com.calsignlabs.apde.tool;

import java.lang.Runnable;

import android.view.MenuItem;

import com.calsignlabs.apde.APDE;
import com.calsignlabs.apde.KeyBinding;

public interface Tool extends Runnable {
	public void init(APDE context);
	
	/**
	 * @return the tool name to be displayed in the tool menu
	 */
	public String getMenuTitle();
	
	/**
	 * Returns a key binding that will run this tool.
	 * Return null for a tool that is not run with a key binding
	 * 
	 * @return the key binding
	 */
	public KeyBinding getKeyBinding();
	
	/**
	 * @return should this tool appear in the tools menu?
	 */
	public boolean showInToolsMenu();
	
	/**
	 * Returns a converted MenuItem for use in the selection Contextual Action Bar.
	 * Return false for a tool that does not appear in the selection CAB
	 * 
	 * @param convert the MenuItem to convert
	 * @return whether or not the MenuItem should appear in the selection CAB
	 */
	public boolean createSelectionActionModeMenuItem(MenuItem convert);
}