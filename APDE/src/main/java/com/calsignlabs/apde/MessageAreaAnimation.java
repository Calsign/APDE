package com.calsignlabs.apde;

import android.view.View;
import android.widget.LinearLayout.LayoutParams;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.Transformation;

/**
 * Custom Animation class for animating the Message area transitions
 */
public class MessageAreaAnimation extends Animation {
	private static final float SPEED = 1.0f;
	
	private View code;
	private View console;
	private View message;
	
	private float mStart;
	private float mEnd;
	
	private float maxHeight;
	
	public MessageAreaAnimation(View code, View console, View message, float fromX, float toX, int maxHeight) {
		this.code = code;
		this.console = console;
		this.message = message;
		
		mStart = fromX;
		mEnd = toX;
		
		this.maxHeight = maxHeight;
		
		setInterpolator(new AccelerateDecelerateInterpolator());
		
		float duration = Math.abs(mEnd - mStart) / SPEED;
		setDuration((long) duration);
	}
	
	@Override
	protected void applyTransformation(float interpolatedTime, Transformation transform) {
		float offset = (mEnd - mStart) * interpolatedTime + mStart;
		int maxCode = (int) (maxHeight - message.getHeight());
		
		//Calculate the new dimensions
		int codeDim = (int) offset;
		int consoleDim = maxCode - codeDim;
		
		//Set the new dimensions
		code.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, codeDim));
		console.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, consoleDim));
		
		code.postInvalidate();
		console.postInvalidate();
		message.postInvalidate();
		
		//postInvalidate();
	}
}