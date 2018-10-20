package com.calsignlabs.apde.build.dag;

public class LambdaBuildTask extends BuildTask {
	private Action action;
	private CharSequence title;
	
	public LambdaBuildTask(Action action, CharSequence title, BuildTask... deps) {
		super(deps);
		this.action = action;
		this.title = title;
	}
	
	@Override
	public void run() throws InterruptedException {
		finish(action.run(getBuildContext()));
	}
	
	@Override
	public CharSequence getTitle() {
		return title;
	}
	
	public interface Action {
		/**
		 * @return success
		 */
		boolean run(BuildContext context) throws InterruptedException;
	}
}
