package com.calsignlabs.apde.build.dag;

import com.calsignlabs.apde.R;
import com.calsignlabs.apde.build.Manifest;

import java.io.File;

public class GenerateManifestBuildTask extends BuildTask {
	private Getter<File> manifestFile;
	protected Manifest previous;
	
	public GenerateManifestBuildTask(Getter<File> manifestFile, BuildTask... deps) {
		super(deps);
		
		this.manifestFile = manifestFile;
		
		// We need to reload the first time, in addition to whenever any of the dependencies change
		orChangeNoticer(context -> previous == null);
		orChangeNoticer(new ChecksumChangeNoticer(manifestFile));
		orGetterChangeNoticer(manifestFile);
	}
	
	@Override
	public void run() throws InterruptedException {
		// We only need to reload if dependencies have changed
		if (previous == null || hasChanged(getBuildContext())) {
			Manifest manifest = new Manifest(getBuildContext());
			manifest.initBlank();
			manifest.loadProperties();
			manifest.writeCopy(manifestFile.get(getBuildContext()));
			
			previous = manifest;
		}
		
		getBuildContext().setManifest(previous);
		
		succeed();
	}
	
	@Override
	public boolean shouldRunIfNotUpdated() {
		// We need to set the manifest even if this hasn't changed
		return true;
	}
	
	@Override
	public CharSequence getTitle() {
		return getBuildContext().getResources().getString(R.string.build_writing_manifest);
	}
}
