package com.calsignlabs.apde.build;

/**
 * Immutable class that represents an Android permission
 */
public class Permission implements Comparable<Permission> {
	private String prefix;
	private String name;
	private String desc;
	
	private boolean custom;
	
	public Permission(String prefix, String name, String desc) {
		this.prefix = prefix;
		this.name = name;
		this.desc = desc;
		
		custom = false;
	}
	
	
	public Permission(String prefix, String name, String desc, boolean custom) {
		this.prefix = prefix;
		this.name = name;
		this.desc = desc;
		
		this.custom = custom;
	}
	
	public String prefix() {
		return prefix;
	}
	
	public String name() {
		return name;
	}
	
	public String desc() {
		return desc;
	}
	
	public boolean custom() {
		return custom;
	}

	@Override
	public int compareTo(Permission compare) {
		//So that we can sort a list of Permissions
		return ((Permission) compare).name().compareTo(name());
	}
}