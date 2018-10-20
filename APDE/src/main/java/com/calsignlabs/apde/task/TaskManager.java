package com.calsignlabs.apde.task;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.view.View;

import com.calsignlabs.apde.APDE;
import com.calsignlabs.apde.R;
import com.calsignlabs.apde.build.dag.BuildContext;
import com.calsignlabs.apde.build.dag.ModularBuild;
import com.calsignlabs.apde.support.CustomProgressDialog;

import java.util.HashMap;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TaskManager {
	private APDE context;
	
	private HashMap<String, Task> tasks;
	private HashMap<Task, Thread> taskThreads;
	
	private ExecutorService threadPool;
	
	public TaskManager(APDE context) {
		this.context = context;
		
		tasks = new HashMap<>();
		taskThreads = new HashMap<>();
		
//		threadPool = Executors.newCachedThreadPool();
		threadPool = Executors.newFixedThreadPool(BuildContext.getNumCores());
	}
	
	/**
	 * Register and start a new task.
	 * 
	 * @param tag the task's tag
	 * @param foreground true if the task should start in the foreground, false if it should start in the background
	 * @param activityContext if running in the foreground, the activity context (may be null otherwise)
	 * @param replaceExisting whether or not this task should replace an existing task with the same tag
	 * @param task the task
	 */
	public void launchTask(String tag, boolean foreground, Activity activityContext, boolean replaceExisting, Task task) {
		if (containsTask(tag) && getTask(tag).isRunning()) {
			if (replaceExisting) {
				unregisterTask(tag);
			} else {
				//That's a no-no
				return;
			}
		}
		
		registerTask(tag, task);
		
		if (foreground) {
			//For long tasks
			startForegroundTask(tag, activityContext);
		} else {
			//For quick tasks
			startBackgroundTask(tag);
		}
	}
	
	public boolean containsTask(String tag) {
		return tasks.containsKey(tag);
	}
	
	public void registerTask(String tag, Task task) {
		if (containsTask(tag) && getTask(tag).isRunning()) {
			//That's a no-no
			System.err.println(String.format(Locale.US, context.getResources().getString(R.string.task_register_fail_duplicate_tag), tag));
			return;
		}
		
		task.init(context);
		
		tasks.put(tag, task);
	}
	
	public void unregisterTask(String tag) {
		if (tasks.get(tag).isRunning()) {
			//That's a no-no
			System.err.println(String.format(Locale.US, context.getResources().getString(R.string.task_unregister_fail_not_killed_tag), tag));
			return;
		}
		
		tasks.remove(tag);
	}
	
	public Task getTask(String tag) {
		if (!tasks.containsKey(tag)) {
			System.err.println(String.format(Locale.US, context.getResources().getString(R.string.task_get_fail_nonexistent_tag), tag));
			return null;
		}

		return tasks.get(tag);
	}
	
	public void startBackgroundTask(String tag) {
		final Task task = getTask(tag);
		
		if (task.isRunning()) {
			// That's a no-no
			System.err.println(String.format(Locale.US, context.getResources().getString(R.string.task_start_fail_already_running_tag), tag));
			return;
		}
		
		startBackgroundTask(task);
	}
	
	public void startBackgroundTask(final Task task) {
		if (task.isRunning()) {
			// That's a no-no
			System.err.println(String.format(Locale.US, context.getResources().getString(R.string.task_start_fail_already_running_tag), task.getTitle()));
			return;
		}

		moveToBackground(task);
		startTask(task);
	}
	
	public void startForegroundTask(String tag, Activity activityContext) {
		final Task task = getTask(tag);
		
		if (task.isRunning()) {
			// That's a no-no
			System.err.println(String.format(Locale.US, context.getResources().getString(R.string.task_start_fail_already_running_tag), tag));
			return;
		}

		moveToForeground(task, activityContext);
		startTask(task);
	}
	
	private void startTask(final Task task) {
		if (task.isRunning()) {
			//That's a no-no
			System.err.println(context.getResources().getString(R.string.task_start_fail_already_running));
			return;
		}
		
		Runnable runnable = () -> {
			taskThreads.put(task, Thread.currentThread());
			task.setContext(context);
			
			try {
				task.start();
				task.run();
				task.stop();
			} catch (InterruptedException e) {
				// Do nothing
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				// Try to close resources...
				try {
					task.stop();
				} catch (Exception e2) {
					e2.printStackTrace();
				}
			}
		};
		
		threadPool.submit(runnable);
//		(new Thread(runnable)).start();
	}
	
	public void moveToBackground(final Task task) {
		TaskStatusRelay statusRelay = new BackgroundTaskRelay(context.getEditor());
		
		TaskStatusRelay previousStatusRelay = task.getStatusRelay();
		if (previousStatusRelay != null) {
			statusRelay.setStatusHistory(previousStatusRelay.getStatusHistory());
			previousStatusRelay.close();
		}
		
		task.setStatusRelay(statusRelay);
	}
	
	public void moveToForeground(final Task task, Activity activityContext) {
		CustomProgressDialog progressDialog = new CustomProgressDialog(activityContext, View.GONE, View.GONE);
		progressDialog.setTitle(task.getTitle());
		if (task.getMessage() != null) {
			progressDialog.setMessage(task.getMessage());
		}
		
		progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
		progressDialog.setIndeterminate(true);
		progressDialog.setCanceledOnTouchOutside(false);
		
		progressDialog.setButton(DialogInterface.BUTTON_NEGATIVE, context.getResources().getString(R.string.cancel), new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				cancelTask(task);
			}
		});
		
		if (task.canRunInBackground()) {
			progressDialog.setButton(DialogInterface.BUTTON_NEUTRAL, context.getResources().getString(R.string.task_run_in_background), new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					moveToBackground(task);
				}
			});
		}
		
		TaskStatusRelay statusRelay = new ForegroundStatusRelay(this, progressDialog);
		
		TaskStatusRelay previousStatusRelay = task.getStatusRelay();
		if (previousStatusRelay != null) {
			statusRelay.setStatusHistory(previousStatusRelay.getStatusHistory());
			previousStatusRelay.close();
		}
		
		task.setStatusRelay(statusRelay);
		
		progressDialog.show();
	}
	
	public void killTask(Task task) {
		if (!task.isRunning()) {
			//That's a no-no
			System.err.println(context.getResources().getString(R.string.task_kill_fail_not_running));
			return;
		}
		
		taskThreads.get(task).interrupt();
		taskThreads.remove(task);
		task.stop();
	}
	
	public void cancelTask(Task task) {
		if (!task.isRunning()) {
			//That's a no-no
			System.err.println(context.getResources().getString(R.string.task_kill_fail_not_running));
			return;
		}
		
		taskThreads.get(task).interrupt();
		taskThreads.remove(task);
		task.cancel();
		task.stop();
	}
	
	public void runOnUiThread(Runnable runnable) {
		context.getEditor().runOnUiThread(runnable);
	}
}
