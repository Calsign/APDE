package com.calsignlabs.apde.build.dag;

public	class ChangeNoticerWrapper<T> extends BuildTask {
	public ChangeNoticerWrapper(MemoryChangeNoticer<T> changeNoticer, BuildTask... deps) {
		super(deps);
		orChangeNoticer(context -> changeNoticer.hasChanged(this, context));
	}
	
	public ChangeNoticerWrapper(ChangeNoticer changeNoticer, BuildTask... deps) {
		super(deps);
		orChangeNoticer(changeNoticer);
	}
	
	@Override
	public void run() throws InterruptedException {
		succeed();
	}
	
	@Override
	public CharSequence getTitle() {
		return "";
	}
	
	private T t = null;
	
	public void store(T t) {
		this.t = t;
	}
	
	public boolean stored() {
		return t != null;
	}
	
	public T retrieve() {
		return t;
	}
	
	public interface MemoryChangeNoticer<T> {
		boolean hasChanged(ChangeNoticerWrapper<T> ref, BuildContext context);
	}
}
