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
		orChangeNoticer(context -> ChangeStatus.bool(previous == null));
		orGetterChangeNoticer(manifestFile);
		
		// The user does not modify the manifest directly; instead they modify sketch.properties
		// through the sketch properties interface. Thus this check is not necessary.
		// This check causes problems because it is actually comparing the previous manifest against
		// the one before that, meaning that any change to the manifest requires two build cycles
		// to resolve the changed status. This can be fixed essentially by generating a new manifest
		// every build and then diffing against that, but this seems like a lot of work to do when
		// the manifest is always generated from sketch properties, and never changes unless sketch
		// properties also changes.
		//orChangeNoticer(new ChecksumChangeNoticer(manifestFile));
	}
	
	@Override
	public void run() throws InterruptedException {
		// We only need to reload if dependencies have changed
		
		if (previous == null || hasChanged(getBuildContext()).changed()) {
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
