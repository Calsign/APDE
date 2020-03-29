package com.calsignlabs.apde.build.dag;

import com.calsignlabs.apde.R;
import com.calsignlabs.apde.build.ComponentTarget;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class AaptBuildTask extends ArgLambdaBuildTask {
	public AaptBuildTask(Getter<File> aapt, Getter<File> res, Getter<File> supportRes,
						 Getter<File> supportWearableRes, Getter<File> vrRes, Getter<File> gen,
						 Getter<File> assets, Getter<File> manifest, Getter<File> androidJar,
						 Getter<File> out, BuildTask... deps) {
		super(context -> {
			List<String> args = new ArrayList<>();
			
			args.add(aapt.get(context).getAbsolutePath());
			addAll(args, "package", "-v", "-f", "-m", "--auto-add-overlay", "--no-version-vectors");
			addAll(args, "-S", res.get(context).getAbsolutePath());
			addAll(args, "-S", supportRes.get(context).getAbsolutePath());
			addAll(args, "-S", supportWearableRes.get(context).getAbsolutePath());
			if (context.getComponentTarget() == ComponentTarget.VR) {
				addAll(args, "-S", vrRes.get(context).getAbsolutePath());
				addAll(args, "--extra-packages", "com.google.vr.cardboard");
			} else {
				addAll(args, "--extra-packages", "android.support.v7.appcompat");
			}
			addAll(args, "-J", gen.get(context).getAbsolutePath());
			addAll(args, "-A", assets.get(context).getAbsolutePath());
			addAll(args, "-M", manifest.get(context).getAbsolutePath());
			addAll(args, "-I", androidJar.get(context).getAbsolutePath());
			addAll(args, "-F", out.get(context).getAbsolutePath());
			
			Process aaptProcess;
			
			try {
				aaptProcess = Runtime.getRuntime().exec(argsArray(args));
				
				CopyBuildTask.copyStream(aaptProcess.getInputStream(), new NullOutputStream());
				CopyBuildTask.copyStream(aaptProcess.getErrorStream(), context.isVerbose() ? System.err : new NullOutputStream());
			} catch (IOException e) {
				e.printStackTrace();
				return false;
			}
			
			int code = aaptProcess.waitFor();
			
			if (code == 0) {
				return true;
			} else {
				System.err.println(context.getResources().getString(R.string.build_aapt_failed_error_code, code));
				return false;
			}
		}, "Running AAPT...", deps);
		
		orGetterChangeNoticer(aapt, res, supportRes, supportWearableRes, vrRes, gen, assets,
				manifest, androidJar, out);
	}
	
	/**
	 * Takes output and does nothing with it.
	 */
	protected static class NullOutputStream extends OutputStream {
		public NullOutputStream() {}
		
		@Override
		public void write(int i) throws IOException {}
	}
}
