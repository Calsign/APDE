package com.calsignlabs.apde;

public class KeyBinding {
	private String value;
	private int key;
	
	//These require API 11
	private boolean ctrl;
	private boolean meta;
	private boolean func;
	
	//These are available on all API levels
	private boolean alt;
	private boolean sym;
	private boolean shift;
	
	public KeyBinding(String value, int key, boolean ctrl, boolean meta, boolean func, boolean alt, boolean sym, boolean shift) {
		this.value = value;
		this.key = key;
		
		this.ctrl = ctrl;
		this.meta = meta;
		this.func = func;
		
		this.alt = alt;
		this.sym = sym;
		this.shift = shift;
	}
	
	public String value() {
		return value;
	}
	
	public int key() {
		return key;
	}
	
	public boolean ctrl() {
		return ctrl;
	}
	
	public boolean meta() {
		return meta;
	}
	
	public boolean func() {
		return func;
	}
	
	public boolean alt() {
		return alt;
	}
	
	public boolean sym() {
		return sym;
	}
	
	public boolean shift() {
		return shift;
	}
	
	/**
	 * Compares a keystroke to this KeyBinding
	 * 
	 * @param ctrl
	 * @param meta
	 * @param func
	 * @param alt
	 * @param sym
	 * @param shift
	 * @return whether or not the keystrokes match
	 */
	public boolean matches(int key, boolean ctrl, boolean meta, boolean func, boolean alt, boolean sym, boolean shift) {
		return key() == key && ctrl() == ctrl && meta() == meta && func() == func && alt() == alt() && sym() == sym && shift() == shift;
	}
	
	/**
	 * Compares another KeyBinding to this KeyBinding
	 * 
	 * @param binding
	 * @return whether or not the keystrokes match
	 */
	public boolean matches(KeyBinding binding) {
		return key() == binding.key() && ctrl() == binding.ctrl() && meta() == binding.meta() && func() == binding.func() && alt() == alt() && sym() == binding.sym() && shift() == binding.shift();
	}
}