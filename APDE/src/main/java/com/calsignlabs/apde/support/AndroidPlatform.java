package com.calsignlabs.apde.support;

import java.io.File;

import processing.app.platform.DefaultPlatform;

//Hacky way to deal with Processing 3.0a4
public class AndroidPlatform extends DefaultPlatform {
	public static File dir;
	
	public static void setDir(File toDir) {
		dir = toDir;
	}
	
	@Override
	public File getSettingsFolder() throws Exception {
		return dir;
	}
}
