package com.calsignlabs.apde.build;

/**
 * Immutable class that represents an Android permission
 */
public class Permission {
	private String name;
	private String desc;
	
	public Permission(String name, String desc) {
		this.name = name;
		this.desc = desc;
	}
	
	public String name() {
		return name;
	}
	
	public String desc() {
		return desc;
	}
}