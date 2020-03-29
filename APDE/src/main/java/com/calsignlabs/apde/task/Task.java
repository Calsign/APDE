package com.calsignlabs.apde.task;

import com.calsignlabs.apde.APDE;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public abstract class Task {
	private AtomicBoolean running = new AtomicBoolean(false);
	private TaskStatusRelay statusRelay;
	
	private APDE context;
	
	private AtomicLong duration = new AtomicLong(-1);
	
	protected void setContext(APDE context) {
		this.context = context;
	}
	
	public APDE getContext() {
		return context;
	}
	
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
	 * For use by the task.
	 * Sends a status update to the status relay.
	 *
	 * @param resId resource ID of string to set as status
	 */
	public void postStatus(int resId) {
		statusRelay.postStatus(context.getString(resId));
	}
	
	/**
	 * Called when the task starts. Perform any resource allocation here.
	 * If overriding, make sure to call super.start().
	 */
	public void start() {
		running.set(true);
	}
	
	/**
	 * Called when the task is being run.
	 * Overriding this method is required.
	 */
	public abstract void run() throws InterruptedException;
	
	/**
	 * For use by the task. The running state is managed by the superclass.
	 * Determine whether or not the task is currently running.
	 * 
	 * @return whether or not the task is running
	 */
	public boolean isRunning() {
		return running.get();
	}
	
	/**
	 * Called when the task stops. Perform any resource de-allocation here.
	 * This method will always be called, even if the task is killed prematurely.
	 * If overriding, make sure to call super.stop().
	 */
	public void stop() {
		if (statusRelay != null) {
			statusRelay.close();
		}
		running.set(false);
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
	
	// Called from TaskManager
	protected void setDuration(long duration) {
		this.duration.set(duration);
	}
	
	public long getDuration() {
		return duration.get();
	}
}
