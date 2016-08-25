package com.calsignlabs.apde;

import android.text.TextPaint;

public class Keyword {
	private String name;
	private TextPaint paint;
	private boolean function;
	
	private String reference;
	private String referenceTarget;
	
	private String parentClass;
	private boolean staticInParentClass;
	
	public Keyword(String name, TextPaint paint, boolean function, String reference, String referenceTarget, String parentClass, boolean staticInParentClass) {
		this.name = name;
		this.paint = paint;
		this.function = function;
		this.reference = reference;
		this.referenceTarget = referenceTarget;
		this.parentClass = parentClass;
		this.staticInParentClass = staticInParentClass;
	}
	
	public String name() {
		return name;
	}
	
	public TextPaint paint() {
		return paint;
	}
	
	public boolean function() {
		return function;
	}
	
	public String getReferenceType() {
		return reference;
	}
	
	public String getReferenceTarget() {
		return referenceTarget;
	}
	
	public String getParentClass() {
		return parentClass;
	}
	
	public boolean isStatic() {
		return staticInParentClass;
	}
}