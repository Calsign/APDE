package com.calsignlabs.apde.build;

import com.calsignlabs.apde.APDE;
import com.calsignlabs.apde.R;
import com.calsignlabs.apde.task.Task;

import java.io.IOException;

public class CopyAndroidJarTask extends Task {
	private APDE global;
	
	@Override
	public void init(APDE global) {
		this.global = global;
	}
	
	@Override
	public void run() {
		try {
			postStatus("Copying android.jar file...");
			Build.copyAndroidJar(global);
			postStatus("Copied android.jar file");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	@Override
	public CharSequence getTitle() {
		return global.getResources().getString(R.string.pref_build_recopy_android_jar);
	}
}
