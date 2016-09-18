package com.calsignlabs.apde.learning;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ScrollView;

import java.util.ArrayList;

// TODO make code area sticky
// This class isn't implemented yet!!!
public class StickyViewScrollView extends ScrollView {
	private ArrayList<View> stickyViews;
	
	public StickyViewScrollView(Context context) {
		this(context, null);
	}
	
	public StickyViewScrollView(Context context, AttributeSet attrs) {
		this(context, attrs, android.R.attr.scrollViewStyle);
	}
	
	public StickyViewScrollView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		
		stickyViews = new ArrayList<>();
	}
}
