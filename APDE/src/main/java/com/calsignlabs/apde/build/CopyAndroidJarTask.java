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
			postStatus(global.getString(R.string.task_copy_android_jar_file_running));
			Build.copyAndroidJar(global);
			postStatus(global.getString(R.string.task_copy_android_jar_file_completed));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	@Override
	public CharSequence getTitle() {
		return global.getString(R.string.pref_build_recopy_android_jar);
	}
}
