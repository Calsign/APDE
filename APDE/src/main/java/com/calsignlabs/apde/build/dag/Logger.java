package com.calsignlabs.apde.build.dag;

import android.util.Log;

public class Logger {
	private static int LOG_LEVEL = 0;
	
	public static void writeLog(String message) {
		writeLog(message, 0);
	}
	
	public static void writeLog(String message, int level) {
		if (level <= LOG_LEVEL) {
			System.out.println(message);
			Log.d("modular_build", message);
		}
	}
	
	public static int getLogLevel() {
		return LOG_LEVEL;
	}
}
