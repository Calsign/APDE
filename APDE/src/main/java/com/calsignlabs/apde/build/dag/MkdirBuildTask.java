package com.calsignlabs.apde.build.dag;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MkdirBuildTask extends BuildTask {
	private List<Getter<File>> dirs;
	
	public MkdirBuildTask(Getter<File>... dirs) {
		this(Arrays.asList(dirs));
	}
	
	public MkdirBuildTask(List<Getter<File>> dirs, BuildTask... deps) {
		super(deps);
		
		this.dirs = new ArrayList<>();
		this.dirs.addAll(dirs);
		
		orChangeNoticer(context -> {
			boolean hasChanged = false;
			for (Getter<File> dir : dirs) {
				hasChanged |= !dir.get(context).exists();
			}
			return hasChanged;
		});
		orGetterChangeNoticer(dirs);
	}
	
	public MkdirBuildTask(Getter<File> dir, BuildTask... deps) {
		this(Arrays.asList(dir), deps);
	}
	
	@Override
	public void run() throws InterruptedException {
		boolean success = true;
		for (Getter<File> dir : dirs) {
			File file = dir.get(getBuildContext());
			success &= file.exists() || file.mkdirs();
		}
		finish(success);
	}
	
	@Override
	public CharSequence getTitle() {
		return "Making dirs...";
	}
}
