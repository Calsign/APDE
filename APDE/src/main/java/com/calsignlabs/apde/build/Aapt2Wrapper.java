package com.calsignlabs.apde.build;

import com.android.tools.aapt2.Aapt2Jni;
import com.calsignlabs.apde.APDE;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Aapt2Wrapper {
	/**
	 * Whether to use the AAPT2 binary rather than the shared library. The shared library is better
	 * because it provides better error output and it continues working in Android 12+.
	 * Unfortunately, I haven't gotten the shared library to work in older versions of Android, so
	 * we use the binary there instead.
	 *
	 * @return whether to use the AAPT2 binary rather than the shared library.
	 */
	public static boolean useAapt2Bin() {
		return android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.P;
	}
	
	private static File getAapt2BinFile(APDE context) {
		return new File(context.getApplicationInfo().nativeLibraryDir, "libaapt2_bin.so");
	}
	
	public static void compile(APDE context, List<String> args) throws InvocationFailedException, IOException, InterruptedException {
		if (useAapt2Bin()) {
			List<String> fullArgs = new ArrayList<>(args);
			fullArgs.add(0, getAapt2BinFile(context).getAbsolutePath());
			fullArgs.add(1, "compile");
			invokeBinary(context, fullArgs.toArray(new String[0]));
		} else {
			Aapt2Jni.compile(args);
		}
	}
	
	public static void link(APDE context, List<String> args) throws InvocationFailedException, IOException, InterruptedException {
		if (useAapt2Bin()) {
			List<String> fullArgs = new ArrayList<>(args);
			fullArgs.add(0, getAapt2BinFile(context).getAbsolutePath());
			fullArgs.add(1, "link");
			invokeBinary(context, fullArgs.toArray(new String[0]));
		} else {
			Aapt2Jni.link(args);
		}
	}
	
	public static class InvocationFailedException extends Exception {
		public final int exitCode;
		public final String[] args;
		
		public InvocationFailedException(int exitCode, String[] args) {
			super("Execution failed with exit code " + exitCode + ": " + Arrays.toString(args));
			this.exitCode = exitCode;
			this.args = args;
		}
	}
	
	public static void invokeBinary(APDE context, String[] args) throws InvocationFailedException, IOException, InterruptedException {
		boolean verbose = context.getPref("pref_debug_global_verbose_output", false);
		
		ProcessBuilder pb = new ProcessBuilder(args);
		// Combine output streams, otherwise we need two threads or risk deadlock
		pb.redirectErrorStream(true);
		Process aaptProcess = pb.start();
		
		// We have to give it an output stream for some reason
		// So give it one that ignores the data
		// We don't want to print the standard out to the console because it is WAY too much stuff
		// It even causes APDE to crash because there is too much text in the console to fit in a transaction
		// We want to show the error stream in verbose mode though because this lets us debug things
		Build.copyStream(aaptProcess.getInputStream(), verbose ? System.out : new Build.NullOutputStream());
		
		int code = aaptProcess.waitFor();
		
		if (code != 0) {
			System.err.println("AAPT2 exited with error code: " + code);
			throw new InvocationFailedException(code, args);
		}
	}
}
