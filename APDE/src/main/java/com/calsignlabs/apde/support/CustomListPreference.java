package com.calsignlabs.apde.support;

import android.content.Context;
import android.support.v7.preference.ListPreference;
import android.util.AttributeSet;
import android.view.View;

public class CustomListPreference extends ListPreference {
	protected Context context;
	protected int customLayoutResId;
	protected Populator populator;
	protected Runnable preferenceUpdate;
	
	public CustomListPreference(Context context, AttributeSet attrs) {
		super(context, attrs);
		
		this.context = context;
	}
	
	public void init(int resId, Populator populator, Runnable preferenceUpdate) {
		customLayoutResId = resId;
		this.populator = populator;
		this.preferenceUpdate = preferenceUpdate;
	}
	
	public int getSelectedItemIndex() {
		return findIndexOfValue(getValue());
	}
	
	public interface Populator {
		void populate (View view, int position, CharSequence[] entries);
	}
}
