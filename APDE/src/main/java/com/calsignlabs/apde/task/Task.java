package com.calsignlabs.apde.task;

import com.calsignlabs.apde.APDE;

public abstract class Task {
	private boolean running;
	private TaskStatusRelay statusRelay;

	/**
	 * Called when the task is first registered. Used to obtain an instance of the application.
	 * 
	 * @param context the application context
	 */
	public void init(APDE context) {}
	
	/**
	 * Set the task's status relay.
	 * 
	 * @param statusRelay
	 */
	public void setStatusRelay(TaskStatusRelay statusRelay) {
		this.statusRelay = statusRelay;
	}

	/**
	 * 
	 * @return
	 */
	public TaskStatusRelay getStatusRelay() {
		return statusRelay;
	}

	/**
	 * For use by the task.
	 * Send a status update to the status relay.
	 * 
	 * @param status
	 */
	public void postStatus(CharSequence status) {
		statusRelay.postStatus(status);
	}

	/**
	 * Called when the task starts. Perform any resource allocation here.
	 * If overriding, make sure to call super.start().
	 */
	public void start() {
		running = true;
	}

	/**
	 * Called when the task is being run.
	 * Overriding this method is required.
	 */
	public abstract void run();

	/**
	 * For use by the task. The running state is managed by the superclass.
	 * Determine whether or not the task is currently running.
	 * 
	 * @return whether or not the task is running
	 */
	public boolean isRunning() {
		return running;
	}

	/**
	 * Called when the task stops. Perform any resource de-allocation here.
	 * This method will always be called, even if the task is killed prematurely.
	 * If overriding, make sure to call super.stop().
	 */
	public void stop() {
		statusRelay.close();
		running = false;
	}

	/**
	 * Called only when the task is canceled.
	 * Use this to undo any progress if applicable.
	 */
	public void cancel() {
		
	}

	/**
	 * Get the task's human-readable title.
	 * Overriding this method is required.
	 * 
	 * @return the task's title
	 */
	public abstract CharSequence getTitle();

	/**
	 * Get the task's human-readable message description.
	 * By default, returns no message (null).
	 * 
	 * @return the task's message
	 */
	public CharSequence getMessage() {
		return null;
	}

	/**
	 * Called to determine whether or not the task can be moved to the background.
	 * 
	 * @return whether or not the task can be run in the background
	 */
	public boolean canRunInBackground() {
		return true;
	}
}
