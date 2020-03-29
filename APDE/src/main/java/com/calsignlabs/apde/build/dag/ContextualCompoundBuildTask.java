package com.calsignlabs.apde.build.dag;

import java.util.ArrayList;
import java.util.List;

public class ContextualCompoundBuildTask extends BuildTask {
	private ContextualTaskBuilder depGetter;
	
	public ContextualCompoundBuildTask(ContextualTaskBuilder depGetter) {
		super();
		this.depGetter = depGetter;
	}
	
	@Override
	public List<BuildTask> getDependencies(BuildContext context) {
		List<BuildTask> tasks = new ArrayList<>();
		depGetter.addTasks(context, tasks);
		return tasks;
	}
	
	@Override
	public void run() throws InterruptedException {
		succeed();
	}
	
	@Override
	public CharSequence getTitle() {
		return "";
	}
	
	public interface ContextualTaskBuilder {
		void addTasks(BuildContext context, List<BuildTask> tasks);
	}
}
