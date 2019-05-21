package com.calsignlabs.apde.build.dag;

import android.support.annotation.Nullable;
import android.util.Log;

import com.calsignlabs.apde.APDE;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BuildTaskRunner {
	private APDE global;
	
	private BuildTask buildTask;
	private BuildContext buildContext;
	
	private Set<String> runningTasks, finishedTasks;
	private Map<BuildTask, List<BuildTask>> completionListeners;
	private Map<String, Boolean> successes;
	
	private static boolean debugOut = true;
	private static int logLevel = 1;
	
	public BuildTaskRunner(APDE global, BuildTask buildTask, BuildContext buildContext) {
		this.global = global;
		this.buildTask = buildTask;
		this.buildContext = buildContext;
	}
	
	public void addOnCompleteListener(BuildTask.OnCompleteListener onCompleteListener) {
		buildTask.addOnCompleteListener(onCompleteListener);
	}
	
	public void run() {
		// Setup is not insignificant
		(new Thread(() -> startBuildTask(buildTask))).start();
	}
	
	public Set<String> getFailedTasks() {
		Set<String> failures = new HashSet<>();
		for (String key : successes.keySet()) {
			if (!successes.get(key)) {
				failures.add(key);
			}
		}
		return failures;
	}
	
	// Seems like we can turn off the synchronized collections with no harm done. It even gives us
	// a performance boost. Shrug?
	public static final boolean synchronize = true;
	
	private <T> Set<T> getSynchronizedSet() {
		return synchronize ? Collections.synchronizedSet(new LinkedHashSet<>())
				: new LinkedHashSet<>();
	}
	
	private <K, V> Map<K, V> getSynchronizedMap() {
		return synchronize ? Collections.synchronizedMap(new LinkedHashMap<>())
				: new LinkedHashMap<>();
	}
	
	private <T> List<T> getSynchronizedList() {
		return synchronize ? Collections.synchronizedList(new LinkedList<>())
				: new LinkedList<>();
	}
	
	private void startBuildTask(BuildTask task) {
		runningTasks = getSynchronizedSet();
		finishedTasks = buildContext.getCompletedTasks();
		completionListeners = getSynchronizedMap();
		successes = getSynchronizedMap();
		executeWithDependencies(task);
	}
	
	private void executeWithDependencies(BuildTask task) {
		List<BuildTask> deps = new ArrayList<>();
		boolean shouldRun = task.hasChanged(buildContext).changed() || task.shouldRunIfNotUpdated();
		for (BuildTask dep : task.getDependencies(buildContext)) {
			boolean changeReady = true;
			for (BuildTask changeDep : task.getChangeDependencies()) {
				if (!containsList(finishedTasks, changeDep)) {
					changeReady = false;
					deps.add(changeDep);
				}
			}
			if (changeReady
					&& (dep.hasChanged(buildContext).changed()
						|| dep.treeShouldRunIfNotUpdated(buildContext)
						|| buildContext.isPreviousFailedTask(dep))
					&& !containsList(finishedTasks, dep)) {
				writeLog("debug add dep name: " + dep.getName() + ", tag: " + dep.getTag() + ", changed: "
						+ dep.hasChanged(buildContext) + ", finished: " + containsList(finishedTasks, dep), 3);
				deps.add(dep);
				shouldRun |= dep.hasChanged(buildContext).changed();
			} else {
				writeLog("debug dont dep name: " + dep.getName() + ", tag: " + dep.getTag() + ", changed: "
						+ dep.hasChanged(buildContext) + ", finished: " + containsList(finishedTasks, dep), 3);
			}
		}
		
		synchronized (this) {
			guardedPutMap(successes, task, Boolean.TRUE);
		}
		
		if (!deps.isEmpty()) {
			if (debugOut) {
				StringBuilder remaining = new StringBuilder();
				for (BuildTask remDep : deps) {
					remaining.append(remDep.getName());
					remaining.append(", ");
				}
				writeLog("task " + task.getName() + " has remaining deps " + remaining.toString(), 2);
			}
			
			for (BuildTask dep : deps) {
				addOnCompleteListener(task, dep, success -> {
					boolean failedBefore = false;
					
					synchronized (BuildTaskRunner.this) {
						if (!getMap(successes, task)) {
							failedBefore = true;
						} else if (!success) {
							putMap(successes, task, Boolean.FALSE);
						}
					}
					
					if (!failedBefore) {
						if (success) {
							writeLog("task " + task.getName() + " finished dep " + dep.getName(), 2);
							executeWithDependencies(task);
						} else {
							writeLog("FAILURE in task " + dep.getName() + " or one of its dependencies");
							task.fail();
						}
					}
					return true;
				});
				if (!containsList(runningTasks, dep) && !containsList(finishedTasks, dep)) {
					executeWithDependencies(dep);
				}
			}
		} else {
			if (shouldRun) {
				boolean actuallyRun = false;
				synchronized (this) {
					if (!containsList(runningTasks, task) && !containsList(finishedTasks, task)) {
						runningTasks.add(task.getName());
						actuallyRun = true;
					}
				}
				if (actuallyRun) {
					addOnCompleteListener(null, task, success -> {
						addList(finishedTasks, task);
						if (!success) {
							writeLog("task FAILED: " + task.getName());
						} else {
							writeLog("task succeeded: " + task.getName());
						}
						return true;
					});
					writeLog("launching task: " + task.getName());
					launchBuildTask(task);
				}
			} else {
				writeLog("short circuit task: " + task.getName());
				addList(runningTasks, task);
				addList(finishedTasks, task);
				task.finish(true);
				task.stop();
			}
		}
	}
	
	private void addList(Set<String> list, BuildTask task) {
		list.add(task.getName());
	}
	
	private boolean containsList(Set<String> list, BuildTask task) {
		return list.contains(task.getName());
	}
	
	private <V> void guardedPutMap(Map<String, V> map, BuildTask task, V val) {
		if (!map.containsKey(task.getName())) {
			putMap(map, task, val);
		}
	}
	
	private <V> void putMap(Map<String, V> map, BuildTask task, V val) {
		map.put(task.getName(), val);
	}
	
	private <V> V getMap(Map<String, V> map, BuildTask task) {
		return map.get(task.getName());
	}
	
	private void addOnCompleteListener(@Nullable BuildTask parentTask, BuildTask depTask, BuildTask.OnCompleteListener listener) {
		if (parentTask == null || !(completionListeners.containsKey(parentTask)
				&& completionListeners.get(parentTask).contains(depTask))) {
			if (parentTask != null) {
				if (!completionListeners.containsKey(parentTask)) {
					completionListeners.put(parentTask, getSynchronizedList());
				}
				completionListeners.get(parentTask).add(depTask);
			}
			
			synchronized (depTask.getLock()) {
				if (finishedTasks.contains(depTask.getName())) {
					// We have some synchronization issues, but they are resolved by directly
					// invoking the on completion listener a little bit later on the main thread
					// TODO I don't think this is enough, it might just be a band-aid
					buildContext.post(() -> listener.onComplete(depTask.success()));
				} else {
					depTask.addOnCompleteListener(listener);
				}
			}
		}
	}
	
	private static void writeLog(String message) {
		writeLog(message, 0);
	}
	
	private static void writeLog(String message, int level) {
		if (debugOut && level <= logLevel) {
			System.out.println(message);
			Log.d("modular_build", message);
		}
	}
	
	private void launchBuildTask(BuildTask task) {
		if (!global.getTaskManager().containsTask(task.getTag())) {
			global.getTaskManager().registerTask(task.getTag(), task);
		}
		task.setBuildContext(buildContext);
		global.getTaskManager().startBackgroundTask(task);
	}
}
