package com.calsignlabs.apde;

import android.text.TextPaint;

public class Keyword {
	private String name;
	private TextPaint paint;
	private boolean function;
	
	public Keyword(String name, TextPaint paint, boolean function) {
		this.name = name;
		this.paint = paint;
		this.function = function;
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
}