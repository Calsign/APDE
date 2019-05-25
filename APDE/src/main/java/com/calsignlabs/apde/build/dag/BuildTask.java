package com.calsignlabs.apde.build.dag;

import com.calsignlabs.apde.task.Task;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public abstract class BuildTask extends Task {
	private BuildContext buildContext;
	
	private List<BuildTask> dependencies;
	private AtomicBoolean success;
	private ChangeNoticer changeNoticer;
	private final List<OnCompleteListener> onCompleteListeners;
	
	private String name, tag;
	private static AtomicLong tagCounter = new AtomicLong(0);
	
	public BuildTask(BuildTask... deps) {
		this.name = null;
		
		// Default to false in case of a crash
		success = new AtomicBoolean(false);
		
		dependencies = new ArrayList<>(deps.length);
		dependencies.addAll(Arrays.asList(deps));
		
		changeNoticer = context -> ChangeStatus.UNCHANGED;
		
		changeNoticer = context -> {
			ChangeStatus status = ChangeStatus.UNCHANGED;

			for (BuildTask buildTask : getDependencies(context)) {
				for (BuildTask changeDep : buildTask.getChangeDependencies()) {
					if (!context.isTaskCompleted(changeDep)) {
						// Unstable - could still turn out to be unchanged
						return ChangeStatus.UNSTABLE;
					}
				}
				
				status = ChangeStatus.or(status, buildTask.hasChanged(context));
				if (status.changed()) {
					return status;
				}
			}
			return status;
		};
		
		tag = "build" + tagCounter.getAndIncrement();
		
		onCompleteListeners = Collections.synchronizedList(new ArrayList<>());
	}
	
	public List<BuildTask> getDependencies(BuildContext context) {
		return dependencies;
	}
	
	public BuildTask orChangeNoticer(ChangeNoticer changeNoticer) {
		ChangeNoticer old = this.changeNoticer;
		
		List<BuildTask> changeDeps;
		if (old.hasDependencies()) {
			changeDeps = old.getDependencies();
			changeDeps.addAll(changeNoticer.getDependencies());
		} else {
			changeDeps = changeNoticer.getDependencies();
		}
		
		this.changeNoticer = new ChangeNoticer() {
			@Override
			public ChangeStatus hasChanged(BuildContext context) {
				return ChangeStatus.or(old.hasChanged(context), changeNoticer.hasChanged(context));
			}
			
			@Override
			public List<BuildTask> getDependencies() {
				return changeDeps;
			}
		};
		return this;
	}
	
	public <T> BuildTask orGetterChangeNoticer(List<Getter<T>> getters) {
		List<BuildTask> getterDeps;
		
		if (getters.size() == 0) {
			return this;
		} else if (getters.size() == 1) {
			getterDeps = getters.get(0).getDependencies();
		} else {
			getterDeps = new ArrayList<>();
			for (Getter<T> getter : getters) {
				getterDeps.addAll(getter.getDependencies());
			}
		}
		
		orChangeNoticer(new ChangeNoticer() {
			@Override
			public ChangeStatus hasChanged(BuildContext context) {
				return ChangeStatus.UNCHANGED;
			}
			
			@Override
			public List<BuildTask> getDependencies() {
				return getterDeps;
			}
		});
		return this;
	}
	
	public BuildTask orGetterChangeNoticer(Getter... getters) {
		List<BuildTask> getterDeps;
		
		if (getters.length == 0) {
			return this;
		} else if (getters.length == 1) {
			getterDeps = getters[0].getDependencies();
		} else {
			getterDeps = new ArrayList<>();
			for (Getter getter : getters) {
				getterDeps.addAll(getter.getDependencies());
			}
		}
		
		orChangeNoticer(new ChangeNoticer() {
			@Override
			public ChangeStatus hasChanged(BuildContext context) {
				return ChangeStatus.UNCHANGED;
			}
			
			@Override
			public List<BuildTask> getDependencies() {
				return getterDeps;
			}
		});
		return this;
	}
	
	public List<BuildTask> getChangeDependencies() {
		return changeNoticer.getDependencies();
	}
	
	long lastSuccessTimestamp = 0;
	
	public void succeed() {
		finish(true);
	}
	
	public void fail() {
		finish(false);
	}
	
	public void finish(boolean successful) {
		if (getBuildContext().getTimestamp() > lastSuccessTimestamp) {
			lastSuccessTimestamp = getBuildContext().getTimestamp();
			getBuildContext().setTaskSuccess(this, successful);
		}
		
		success.set(successful);
	}
	
	public boolean success() {
		return success.get();
	}
	
	public BuildContext getBuildContext() {
		return buildContext;
	}
	
	public void setBuildContext(BuildContext buildContext) {
		this.buildContext = buildContext;
	}
	
	public String getTag() {
		return tag;
	}
	
	public BuildTask setName(String name) {
		this.name = name;
		return this;
	}
	
	public String getName() {
		return name != null ? name : getTag();
	}
	
	private AtomicLong lastTimestamp = new AtomicLong(-1);
	private ChangeStatus hasChanged = ChangeStatus.CHANGED;
	
	public final ChangeStatus hasChanged(BuildContext context) {
		// Only update if we have a new build context or changed status has not stabilized
		if (context.getTimestamp() > lastTimestamp.get()) {
			for (BuildTask changeDep : getChangeDependencies()) {
				if (!context.isTaskCompleted(changeDep)) {
					// We can't call hasChanged() yet
					return ChangeStatus.UNSTABLE;
				}
			}
			
			if (context.isPreviousFailedTask(this)) {
				hasChanged = ChangeStatus.CHANGED;
				Logger.writeLog("TASK " + getName() + " previously failed", 1);
			} else {
				hasChanged = changeNoticer.hasChanged(context);
				Logger.writeLog("TASK " + getName() + " changed status: " + hasChanged, 1);
			}
			
			if (!hasChanged.unstable()) {
				lastTimestamp.set(context.getTimestamp());
			}
		}
		
		return hasChanged;
	}
	
	public boolean shouldRunIfNotUpdated() {
		return false;
	}
	
	public boolean treeShouldRunIfNotUpdated(BuildContext context) {
		if (shouldRunIfNotUpdated()) {
			return true;
		}
		
		for (BuildTask task : getDependencies(context)) {
			if (task.treeShouldRunIfNotUpdated(context)) {
				return true;
			}
		}
		
		return false;
	}
	
	public void addOnCompleteListener(OnCompleteListener onCompleteListener) {
		onCompleteListeners.add(onCompleteListener);
	}
	
	@Override
	public void start() {
		super.start();
	}
	
	@Override
	public void stop() {
		synchronized (getLock()) {
			boolean successful = success.get();
			for (int i = onCompleteListeners.size() - 1; i >= 0; i--) {
				if (onCompleteListeners.get(i).onComplete(successful)) {
					onCompleteListeners.remove(i);
				}
			}
		}
		
		super.stop();
	}
	
	public Object getLock() {
		return onCompleteListeners;
	}
	
	public enum ChangeStatus {
		CHANGED, UNCHANGED, UNSTABLE;
		
		public boolean changed() {
			return this == CHANGED;
		}
		
		public boolean unchanged() {
			return this == UNCHANGED;
		}
		
		public boolean unstable() {
			return this == UNSTABLE;
		}
		
		public static ChangeStatus or(ChangeStatus a, ChangeStatus b) {
			if (a == CHANGED || b == CHANGED) {
				return CHANGED;
			} else if (a == UNSTABLE || b == UNSTABLE) {
				return UNSTABLE;
			} else {
				return UNCHANGED;
			}
		}
		
		public static ChangeStatus bool(boolean bool) {
			return bool ? CHANGED : UNCHANGED;
		}
	}
	
	public interface ChangeNoticer {
		ChangeStatus hasChanged(BuildContext context);
		
		default List<BuildTask> getDependencies() {
			return Collections.emptyList();
		}
		
		default boolean hasDependencies() {
			return getDependencies().size() > 0;
		}
	}
	
	public interface OnCompleteListener {
		/**
		 * @param success
		 * @return whether or not to remove the listener
		 */
		boolean onComplete(boolean success);
	}
}
