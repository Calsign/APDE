package com.calsignlabs.apde.support;

import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.Transformation;

// Modified from StackOverflow: http://stackoverflow.com/a/8162779/1628609
public class ResizeAnimation<T extends ViewGroup> extends Animation {
	private View mView;
	
	private float mToHeight;
	private float mFromHeight;
	
	private float mToWidth;
	private float mFromWidth;
	
	private boolean allowGone;
	
	public static final int DEFAULT = -42;
	
	/**
	 * Specify DEFAULT for any value to use defaults.
	 * 
	 * @param v
	 * @param fromWidth
	 * @param fromHeight
	 * @param toWidth
	 * @param toHeight
	 */
	public ResizeAnimation(View v, float fromWidth, float fromHeight, float toWidth, float toHeight) {
		this(v, fromWidth, fromHeight, toWidth, toHeight, true);
	}
	
	/**
	 * Specify DEFAULT for any value to use defaults.
	 *
	 * @param v
	 * @param fromWidth
	 * @param fromHeight
	 * @param toWidth
	 * @param toHeight
	 * @param allowGone
	 */
	public ResizeAnimation(View v, float fromWidth, float fromHeight, float toWidth, float toHeight, boolean allowGone) {
		mToHeight = toHeight;
		mToWidth = toWidth;
		mFromHeight = fromHeight;
		mFromWidth = fromWidth;
		mView = v;
		
		this.allowGone = allowGone;
		
		setDuration(200);
		
		// Load defaults
		if (mToHeight == DEFAULT) mToHeight = v.getHeight();
		if (mToWidth == DEFAULT) mToWidth = v.getWidth();
		if (mFromHeight == DEFAULT) mFromHeight = v.getHeight();
		if (mFromWidth == DEFAULT) mFromWidth = v.getWidth();
	}
	
	@Override
	protected void applyTransformation(float interpolatedTime, Transformation t) {
		float height = (mToHeight - mFromHeight) * interpolatedTime + mFromHeight;
		float width = (mToWidth - mFromWidth) * interpolatedTime + mFromWidth;
		
		if (mView.getVisibility() == View.VISIBLE && (width == 0 || height == 0)) {
			if (allowGone) {
				// Hide the view if it's effectively hidden...
				mView.setVisibility(View.GONE);
			}
		} else if (mView.getVisibility() == View.GONE && !(width == 0 || height == 0)) {
			// ...and make it visible if it isn't
			mView.setVisibility(View.VISIBLE);
		}
		
		T.LayoutParams params = mView.getLayoutParams();
		params.height = (int) height;
		params.width = (int) width;
		
		mView.requestLayout();
	}
}
