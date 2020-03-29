package com.calsignlabs.apde.build.dag;

import com.calsignlabs.apde.APDE;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class CleanOldBuildTask extends BuildTask {
	private Getter<File> directory;
	private Pattern regex;
	private Filter filter;
	
	public CleanOldBuildTask(Getter<File> directory, String regex, Filter filter, BuildTask... deps) {
		super(deps);
		this.directory = directory;
		this.regex = Pattern.compile(regex);
		this.filter = filter;
	}
	
	@Override
	public void run() throws InterruptedException {
		File dirFile = directory.get(getBuildContext());
		if (dirFile.exists() && dirFile.isDirectory()) {
			List<File> rejected = new ArrayList<>();
			
			for (File file : dirFile.listFiles()) {
				if (regex.matcher(file.getName()).matches()) {
					if (!filter.accept(getBuildContext(), file, file.getName())) {
						rejected.add(file);
					}
				}
			}
			
			for (File rejectedFile : rejected) {
				try {
					APDE.deleteFile(rejectedFile);
				} catch (IOException e) {
					// Somehow the file got deleted?
					e.printStackTrace();
				}
			}
			
			succeed();
		} else {
			Logger.writeLog("Cleanup directory " + dirFile.getAbsolutePath() + " does not exist or is not a directory", 1);
		}
	}
	
	@Override
	public boolean shouldRunIfNotUpdated() {
		return true;
	}
	
	@Override
	public CharSequence getTitle() {
		return "";
	}
	
	public interface Filter {
		boolean accept(BuildContext context, File file, String filename);
	}
}
