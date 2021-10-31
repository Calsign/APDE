package com.android.tools.aapt2;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * AAPT2 hard-codes the package and class name of the JNI interface, but I created this custom
 * implementation. AAPT2's JNI implementation includes support for reporting logs, which I leverage
 * here.
 */
public class Aapt2Jni {
	private static final Aapt2Jni SINGLETON = new Aapt2Jni();
	
	private final List<Log> logs;
	
	private Aapt2Jni() {
		System.loadLibrary("aapt2_jni");
		logs = new ArrayList<>();
	}
	
	public static Aapt2Jni get() {
		return SINGLETON;
	}
	
	public static class Log {
		public enum Level {
			ERROR, WARN, NOTE;
			
			public static Level fromInt(int level) {
				switch (level) {
					case 3:
						return ERROR;
					case 2:
						return WARN;
					case 1:
						return NOTE;
					default:
						throw new RuntimeException("Unrecognized log level: " + level);
				}
			}
		}
		
		public final Level level;
		public final String path;
		public final long line;
		public final String message;
		
		public Log(Level level, String path, long line, String message) {
			this.level = level;
			this.path = path;
			this.line = line;
			this.message = message;
		}
		
		@Override
		@NonNull
		public String toString() {
			return level + " " + path + ": " + line + ": " + message;
		}
	}
	
	/**
	 * Called by AAPT2 through JNI.
	 *
	 * @param level log level (3 = error, 2 = warning, 1 = info)
	 * @param path path to the file with the issue
	 * @param line line number of the issue
	 * @param message issue message
	 */
	@SuppressWarnings("unused")
	private void log(int level, String path, long line, String message) {
		Log log = new Log(Log.Level.fromInt(level), path, line, message);
		logs.add(log);
		android.util.Log.d("APDE AAPT2", log.toString());
	}
	
	private void clearLogs() {
		logs.clear();
	}
	
	private native int nativeCompile(List<String> args, Aapt2Jni diagnostics);
	
	private native int nativeLink(List<String> args, Aapt2Jni diagnostics);
	
	public static void compile(List<String> args) {
		Aapt2Jni aapt2 = get();
		aapt2.clearLogs();
		int exitCode = aapt2.nativeCompile(args, aapt2);
		if (exitCode != 0) {
			throw new RuntimeException("AAPT2 compile failed: " + exitCode + ", " + getLogs());
		}
	}
	
	public static void link(List<String> args) {
		Aapt2Jni aapt2 = get();
		aapt2.clearLogs();
		int exitCode = aapt2.nativeLink(args, aapt2);
		if (exitCode != 0) {
			throw new RuntimeException("AAPT2 link failed: " + exitCode + ", " + getLogs());
		}
	}
	
	public static List<Log> getLogs() {
		return Collections.unmodifiableList(get().logs);
	}
}
