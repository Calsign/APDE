package com.calsignlabs.apde.build.dag;

public class EmptyBuildTask extends BuildTask {
	@Override
	public void run() throws InterruptedException {}
	
	@Override
	public CharSequence getTitle() {
		return "";
	}
}
