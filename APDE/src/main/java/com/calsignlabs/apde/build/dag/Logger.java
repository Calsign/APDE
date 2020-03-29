package com.calsignlabs.apde.build.dag;

import android.util.Log;

import com.calsignlabs.apde.APDE;
import com.calsignlabs.apde.R;

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
	
	public static void setLogLevel(int level) {
		LOG_LEVEL = level;
	}
	
	public static void setLogLevelFromPrefs(APDE apde) {
		String levelStr = apde.getPref("pref_build_modular_log_level",
				apde.getResources().getString(R.string.pref_build_modular_log_level_default_value));
		try {
			setLogLevel(Integer.parseInt(levelStr));
		} catch (NumberFormatException e) {
			System.err.println("Number format exception while setting log level, shouldn't happen");
		}
	}
}
