package com.calsignlabs.apde;

public abstract class BareSketchFile {
	/**
	 * @return the tab name as it is displayed in the GUI
	 */
	public abstract String getTitle();
	
	/**
	 * @return the file name without extension
	 */
	public abstract String getRawTitle();
	
	/**
	 * @return the file type, including the dot, e.g. ".pde"
	 */
	public abstract String getSuffix();
	
	/**
	 * @return the filename as it is saved (name + suffix)
	 */
	public String getFilename() {
		if(getSuffix().equalsIgnoreCase(".java")&&getTitle().endsWith(getSuffix())) {
			return getTitle();
		} else {
			return getTitle() + getSuffix();
		}
	}
	
	public boolean isPde() {
		return getSuffix().equalsIgnoreCase(".pde");
	}
	
	public boolean isJava() {
		return getSuffix().equalsIgnoreCase(".java");
	}
	
	/**
	 * @return the code oin this sketch file
	 */
	public abstract CharSequence getText();
}
