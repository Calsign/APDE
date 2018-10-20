package com.calsignlabs.apde.build.dag;

import java.util.Collection;

public class CompoundBuildTask extends BuildTask {
	public CompoundBuildTask(BuildTask... deps) {
		super(deps);
	}
	
	public CompoundBuildTask(Collection<BuildTask> deps) {
		super(deps.toArray(new BuildTask[deps.size()]));
	}
	
	@Override
	public void run() throws InterruptedException {
		succeed();
	}
	
	@Override
	public CharSequence getTitle() {
		return "";
	}
}
