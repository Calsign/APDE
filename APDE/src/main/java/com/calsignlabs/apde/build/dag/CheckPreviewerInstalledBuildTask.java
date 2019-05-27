package com.calsignlabs.apde.build.dag;

import android.content.Intent;

import com.calsignlabs.apde.build.ComponentTarget;
import com.calsignlabs.apde.build.SketchPreviewerBuilder;

import java.util.ArrayList;
import java.util.List;

public class CheckPreviewerInstalledBuildTask extends BuildTask {
	public CheckPreviewerInstalledBuildTask(BuildTask... deps) {
		super(deps);
	}
	
	private boolean arrayContains(String[] array, String test) {
		for (String s : array) {
			if (s.equals(test)) {
				return true;
			}
		}
		return false;
	}
	
	@Override
	public void run() {
		if (getBuildContext().getComponentTarget() == ComponentTarget.PREVIEW) {
			Intent intent = new Intent("com.calsignlabs.apde.RUN_SKETCH_PREVIEW");
			intent.setPackage("com.calsignlabs.apde.sketchpreview");
			
			final String[] sketchPermissions = getBuildContext().getManifest().getPermissions();
			
			String[] installedPermissions = SketchPreviewerBuilder.getInstalledPermissions(getContext());
			List<String> additionalPermissions = new ArrayList<>();
			
			for (String sketchPermission : sketchPermissions) {
				if (!arrayContains(installedPermissions, sketchPermission)) {
					additionalPermissions.add(sketchPermission);
				}
			}
			
			if (intent.resolveActivity(getContext().getPackageManager()) == null
					|| additionalPermissions.size() > 0) {
				
				getBuildContext().setPreviewAdditionalRequiredPermissions(additionalPermissions);
				fail();
			} else {
				succeed();
			}
		} else {
			succeed();
		}
	}
	
	@Override
	public CharSequence getTitle() {
		return "Check Previewer Installed";
	}
	
	@Override
	public boolean shouldRunIfNotUpdated() {
		return true;
	}
}
