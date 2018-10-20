package com.calsignlabs.apde.build.dag;

import com.calsignlabs.apde.APDE;
import com.calsignlabs.apde.R;

import java.io.File;
import java.io.IOException;

public class DeleteFileTask extends BuildTask {
	private Getter<File> file;
	private boolean contentsOnly, vacuousSuccess, dependencyOnly;
	
	public DeleteFileTask(Getter<File> file, BuildTask... deps) {
		super(deps);
		
		this.file = file;
		
		vacuousSuccess = true;
		contentsOnly = false;
		dependencyOnly = false;
		
		orChangeNoticer(context -> !dependencyOnly && file.get(context).exists());
		orGetterChangeNoticer(file);
	}
	
	/**
	 * If true, and is a directory, then the directory itself will be preserved, but its contents
	 * will be deleted. Otherwise the entire directory is deleted. If this file is not a directory
	 * then this flag has no effect. Default is false.
	 *
	 * @param contentsOnly whether or not to delete just the contents of the directory
	 * @return this task, for chaining
	 */
	public DeleteFileTask setContentsOnly(boolean contentsOnly) {
		this.contentsOnly = contentsOnly;
		return this;
	}
	
	/**
	 * Set success if the file to delete does not exist. Default is true.
	 *
	 * @param vacuousSuccess success if the file does not exist
	 * @return this task, for chaining
	 */
	public DeleteFileTask setVacuousSuccess(boolean vacuousSuccess) {
		this.vacuousSuccess = vacuousSuccess;
		return this;
	}
	
	/**
	 * If true, task will have changed only if its dependencies have changed. If false, task will
	 * have changed if its dependencies have changed or if the file has been re-created.
	 * Default is false.
	 *
	 * @param dependencyOnly dependency-only mode
	 * @return this task, for chaining
	 */
	public DeleteFileTask setDependencyOnly(boolean dependencyOnly) {
		this.dependencyOnly = dependencyOnly;
		return this;
	}
	
	@Override
	public void run() throws InterruptedException {
		try {
			File toDelete = file.get(getBuildContext());
			
			if (toDelete.exists()) {
				if (toDelete.isDirectory() && contentsOnly) {
					// Delete all files in directory, but not directory itself
					for (File f : toDelete.listFiles()) {
						APDE.deleteFile(f);
					}
				} else {
					APDE.deleteFile(toDelete);
				}
				succeed();
			} else {
				finish(vacuousSuccess);
			}
		} catch (IOException e) {
			e.printStackTrace();
			fail();
		}
	}
	
	@Override
	public CharSequence getTitle() {
		// TODO change this
		return getBuildContext().getResources().getString(R.string.delete);
	}
}
