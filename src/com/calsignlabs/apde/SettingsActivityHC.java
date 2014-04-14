package com.calsignlabs.apde;

import java.util.List;

import com.calsignlabs.apde.support.StockPreferenceFragment;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.Vibrator;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.view.MenuItem;
import android.view.WindowManager;

/**
 * Settings activity for API level 11+
 */
public class SettingsActivityHC extends PreferenceActivity {
	@SuppressLint("NewApi")
	@Override
	protected void onPostCreate(Bundle savedInstanceState) {
		super.onPostCreate(savedInstanceState);
		
		getWindow().getDecorView().setBackgroundColor(getResources().getColor(R.color.activity_background));
		
	}
	
	@SuppressLint("NewApi")
	public void checkPreferences(PreferenceFragment frag) {
		CheckBoxPreference hardwareKeyboard = ((CheckBoxPreference) frag.findPreference("use_hardware_keyboard"));
		
		if(hardwareKeyboard != null) {
			hardwareKeyboard.setOnPreferenceChangeListener(new CheckBoxPreference.OnPreferenceChangeListener() {
				@Override
				public boolean onPreferenceChange(Preference preference, Object newValue) {
					if(PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getBoolean("use_hardware_keyboard", false))
						getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
					else
						getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);

					return true;
				}
			});
		}
		
		Preference vibrator = frag.findPreference("pref_vibrate");
		
		if(vibrator != null) {
			//Hide the "Enable Vibration" preference if the vibrator isn't available
			Vibrator vibrate = (Vibrator) getSystemService(VIBRATOR_SERVICE);
			if(!vibrate.hasVibrator())
				((PreferenceCategory) frag.findPreference("pref_general_settings")).removePreference(vibrator);
		}
		
		bindPreferenceSummaryToValue(frag.findPreference("textsize"));
		bindPreferenceSummaryToValue(frag.findPreference("textsize_console"));
		bindPreferenceSummaryToValue(frag.findPreference("pref_sketchbook_location"));
	}
	
	private static void bindPreferenceSummaryToValue(Preference preference) {
		//Don't bind the preference if it doesn't appear in this fragment
		if(preference != null) {
			// Set the listener to watch for value changes.
			preference.setOnPreferenceChangeListener(sBindPreferenceSummaryToValueListener);
			
			// Trigger the listener immediately with the preference's
			// current value.
			sBindPreferenceSummaryToValueListener.onPreferenceChange(
					preference,
					PreferenceManager.getDefaultSharedPreferences(
							preference.getContext()).getString(preference.getKey(), ""));
		}
	}
	
	/**
	 * A preference value change listener that updates the preference's summary
	 * to reflect its new value.
	 */
	private static Preference.OnPreferenceChangeListener sBindPreferenceSummaryToValueListener = new Preference.OnPreferenceChangeListener() {
		@Override
		public boolean onPreferenceChange(Preference preference, Object value) {
			String stringValue = value.toString();

			if (preference instanceof ListPreference) {
				// For list preferences, look up the correct display value in
				// the preference's 'entries' list.
				ListPreference listPreference = (ListPreference) preference;
				int index = listPreference.findIndexOfValue(stringValue);
				
				// Set the summary to reflect the new value.
				preference.setSummary(index >= 0 ? listPreference.getEntries()[index] : null);
			} else {
				// For all other preferences, set the summary to the value's
				// simple string representation.
				preference.setSummary(stringValue);
			}
			return true;
		}
	};
	
	@SuppressLint("NewApi")
	@Override
	public void onBuildHeaders(List<Header> target) {
		loadHeadersFromResource(R.xml.pref_headers, target);
	}
	
	//StackOverflow: http://stackoverflow.com/questions/19973034/isvalidfragment-android-api-19
	public boolean isValidFragment(String fragmentName) {
		return StockPreferenceFragment.class.getName().equals(fragmentName);
	}
	
	@Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case android.R.id.home:
            	finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
}