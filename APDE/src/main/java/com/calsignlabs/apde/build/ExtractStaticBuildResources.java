package com.calsignlabs.apde.build;

import com.calsignlabs.apde.APDE;
import com.calsignlabs.apde.R;
import com.calsignlabs.apde.task.Task;

import java.io.IOException;

public class ExtractStaticBuildResources extends Task {
	private APDE global;
	
	@Override
	public void init(APDE global) {
		this.global = global;
	}
	
	@Override
	public void run() {
		try {
			postStatus(global.getString(R.string.task_extract_static_build_resources_start));
			StaticBuildResources.extractAll(global);
			postStatus(global.getString(R.string.task_extract_static_build_resources_done));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	@Override
	public CharSequence getTitle() {
		return global.getString(R.string.pref_build_extract_static_build_resources);
	}
}
