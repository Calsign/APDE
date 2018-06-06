package com.calsignlabs.apde.wearcompanion.watchface;

import com.calsignlabs.apde.wearcompanion.R;

import processing.core.PApplet;

public class DefaultSketchWatchFace extends PApplet {
	private String message;
	private boolean gles;
	
	public DefaultSketchWatchFace(boolean gles) {
		super();
		this.gles = gles;
	}
	
	@Override
	public void settings() {
		fullScreen(gles ? P2D : JAVA2D);
	}
	
	@Override
	public void setup() {
		textFont(createFont("Arial", 24));
		textAlign(CENTER, CENTER);
		
		message = getContext().getResources().getString(R.string.watchface_default_message);
	}
	
	@Override
	public void draw() {
		background(0);
		
		text(hour() + ":" + nf(minute(), 2)
				+ "\n\n" + message,
				width / 2, height / 2);
	}
}
