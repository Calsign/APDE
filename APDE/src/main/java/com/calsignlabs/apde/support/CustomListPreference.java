package com.calsignlabs.apde.support;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.preference.ListPreference;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.RadioButton;
import android.widget.RelativeLayout;

import com.calsignlabs.apde.R;

import java.util.ArrayList;

public class CustomListPreference extends ListPreference {
	protected Context context;
	protected CharSequence[] entries, entryValues;
	protected int customLayoutResId;
	protected Populator populator;
	protected ArrayList<RadioButton> radios;
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
	
	@Override
	protected void onPrepareDialogBuilder(AlertDialog.Builder builder) {
		entries = getEntries();
		entryValues = getEntryValues();
		
		if (entries == null || entryValues == null || entries.length != entryValues.length) {
			throw new IllegalStateException("ListPreference requires an entries array and an entryValues array which are both the same length");
		}
		
		if (populator == null) {
			throw new IllegalStateException("CustomListPreference has not been initialized properly");
		}
		
		radios = new ArrayList<RadioButton>();
		
		builder.setAdapter(new BaseAdapter() {
			@Override
			public int getCount() {
				return entries.length;
			}
			
			@Override
			public Object getItem(int position) {
				return entryValues[position];
			}
			
			@Override
			public long getItemId(int position) {
				return position;
			}
			
			@Override
			public View getView(final int position, View convertView, ViewGroup parent) {
				final RelativeLayout item;
				final View content;
				final RadioButton radio;
				
				if (convertView == null) {
					item = (RelativeLayout) ((LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE)).inflate(R.layout.custom_list_preference_item, null);
					FrameLayout contentHolder = (FrameLayout) item.findViewById(R.id.custom_list_preference_item_content);
					content = ((LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE)).inflate(customLayoutResId, null);
					
					contentHolder.addView(content);
				} else {
					item = (RelativeLayout) convertView;
					FrameLayout contentHolder = (FrameLayout) item.findViewById(R.id.custom_list_preference_item_content);
					content = contentHolder.getChildAt(0);
				}
				
				radio = (RadioButton) item.findViewById(R.id.custom_list_preference_item_radio);
				radios.add(radio);
				
				View.OnClickListener onClickListener = new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						for (RadioButton radioButton : radios) {
							if (radioButton != null) {
								radioButton.setChecked(false);
							}
						}
						
						radio.setChecked(true);
						
						setValue(entryValues[position].toString());
						
						preferenceUpdate.run();
						
						getDialog().dismiss();
					}
				};
				
				radio.setOnClickListener(onClickListener);
				radio.setChecked(position == getSelectedItemIndex());
				
				populator.populate(content, position, entries);
				
				item.setClickable(true);
				item.setOnClickListener(onClickListener);
				
				return item;
			}
		}, null);
		
		builder.setPositiveButton(null, null);
	}
	
	public int getSelectedItemIndex() {
		return findIndexOfValue(getValue());
	}
	
	public interface Populator {
		void populate (View view, int position, CharSequence[] entries);
	}
}