package com.calsignlabs.apde.build.dag;

import com.androidjarjar.dx.command.dexer.Main;
import com.calsignlabs.apde.R;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class DxDexBuildTask extends ArgLambdaBuildTask {
	public DxDexBuildTask(Getter<File> output, Getter<File> classesFolder, BuildTask... deps) {
		super(context -> {
			List<String> args = new ArrayList<>();
			
			if (context.isVerbose()) {
				args.add("--verbose");
			}
			args.add("--num-threads=" + BuildContext.getNumCores());
			args.add("--output=" + output.get(context).getAbsolutePath());
			args.add(classesFolder.get(context).getAbsolutePath());
			
			try {
				Main.Arguments dexArgs = new Main.Arguments();
				dexArgs.parse(argsArray(args));
				int resultCode = Main.run(dexArgs);
				
				if (resultCode != 0) {
					System.err.println(String.format(Locale.US, context.getResources().getString(R.string.build_dx_dexer_failed_error_code), resultCode));
				}
				
				return resultCode == 0;
			} catch (Exception e) {
				e.printStackTrace();
				return false;
			}
		}, "Dexing...", deps);
		
		orGetterChangeNoticer(output, classesFolder);
	}
}
