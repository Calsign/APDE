package com.calsignlabs.apde.support;

import android.content.Context;
import android.os.Bundle;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.ListPreferenceDialogFragmentCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.RadioButton;
import android.widget.RelativeLayout;

import com.calsignlabs.apde.R;

import java.util.ArrayList;

// http://stackoverflow.com/questions/32621403/how-do-i-create-custom-preferences-using-android-support-v7-preference-library
public class CustomListPreferenceDialogFragmentCompat extends ListPreferenceDialogFragmentCompat {
	protected CustomListPreference customListPreference;
	
	protected CharSequence[] entries, entryValues;
	protected ArrayList<RadioButton> radios;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	}
	
	@Override
	protected void onPrepareDialogBuilder(AlertDialog.Builder builder) {
		entries = customListPreference.getEntries();
		entryValues = customListPreference.getEntryValues();
		
		if (entries == null || entryValues == null || entries.length != entryValues.length) {
			throw new IllegalStateException("ListPreference requires an entries array and an entryValues array which are both the same length");
		}
		
		if (customListPreference.populator == null) {
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
					item = (RelativeLayout) ((LayoutInflater) customListPreference.context.getSystemService(Context.LAYOUT_INFLATER_SERVICE)).inflate(R.layout.custom_list_preference_item, null);
					FrameLayout contentHolder = (FrameLayout) item.findViewById(R.id.custom_list_preference_item_content);
					content = ((LayoutInflater) customListPreference.context.getSystemService(Context.LAYOUT_INFLATER_SERVICE)).inflate(customListPreference.customLayoutResId, null);
					
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
						
						customListPreference.setValue(entryValues[position].toString());
						
						customListPreference.preferenceUpdate.run();
						
						getDialog().dismiss();
					}
				};
				
				radio.setOnClickListener(onClickListener);
				radio.setChecked(position == customListPreference.getSelectedItemIndex());
				
				customListPreference.populator.populate(content, position, entries);
				
				item.setClickable(true);
				item.setOnClickListener(onClickListener);
				
				return item;
			}
		}, null);
		
		builder.setPositiveButton(null, null);
	}
	
	public static CustomListPreferenceDialogFragmentCompat newInstance(CustomListPreference preference) {
		CustomListPreferenceDialogFragmentCompat fragment = new CustomListPreferenceDialogFragmentCompat();
		Bundle bundle = new Bundle(1);
		bundle.putString("key", preference.getKey());
		fragment.setArguments(bundle);
		
		// Probably totally illegal, but whatever
		fragment.customListPreference = preference;
		
		return fragment;
	}
}
