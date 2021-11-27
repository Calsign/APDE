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
			postStatus(global.getString(R.string.task_extract_static_build_resources_android_jar));
			StaticBuildResources.copyAndroidJar(global);
			
			postStatus(global.getString(R.string.task_extract_static_build_resources_compiled_res));
			StaticBuildResources.extractAssets(global, "res-compiled", StaticBuildResources.getResCompiledDir(global), true);
			
			postStatus(global.getString(R.string.task_extract_static_build_resources_jni_libs));
			StaticBuildResources.extractAssets(global, "jniLibs", StaticBuildResources.getJniLibsDir(global), false);
			
			postStatus(global.getString(R.string.task_extract_static_build_resources_libs));
			StaticBuildResources.extractAssets(global, "libs", StaticBuildResources.getLibsDir(global), false);
			
			postStatus(global.getString(R.string.task_extract_static_build_resources_libs_dex));
			StaticBuildResources.extractAssets(global, "libs-dex", StaticBuildResources.getLibsDexDir(global), false);
			
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
