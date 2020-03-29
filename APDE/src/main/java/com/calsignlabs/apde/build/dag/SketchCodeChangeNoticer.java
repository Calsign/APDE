package com.calsignlabs.apde.build.dag;

import java.util.List;

public class SketchCodeChangeNoticer implements BuildTask.ChangeNoticer {
	private List<SketchCode> lastFiles;
	
	@Override
	public BuildTask.ChangeStatus hasChanged(BuildContext context) {
		// Make changes up here so that we only have to do them once
		// Very important to use only previous, not lastFiles below
		List<SketchCode> previous = lastFiles;
		List<SketchCode> current = context.getSketchFiles();
		
		lastFiles = context.getSketchFiles();
		
		if (previous == null) {
			return BuildTask.ChangeStatus.CHANGED;
		} else {
			if (current.size() != previous.size()) {
				return BuildTask.ChangeStatus.CHANGED;
			}
			
			for (int i = 0; i < current.size(); i++) {
				if (!current.get(i).equals(previous.get(i))) {
					return BuildTask.ChangeStatus.CHANGED;
				}
			}
			
			return BuildTask.ChangeStatus.UNCHANGED;
		}
	}
}
