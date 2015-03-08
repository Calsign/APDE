package com.calsignlabs.apde;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
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

import com.calsignlabs.apde.support.StockPreferenceFragment;

import java.util.List;

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
		
		final CheckBoxPreference enableUndoRedo = (CheckBoxPreference) frag.findPreference("pref_key_undo_redo");
		
		if (enableUndoRedo != null) {
			enableUndoRedo.setOnPreferenceChangeListener(new CheckBoxPreference.OnPreferenceChangeListener() {
				@Override
				public boolean onPreferenceChange(Preference preference, Object newValue) {
					if (!enableUndoRedo.isChecked()) {
						//If the user disabled undo / redo, clear the undo history to prevent problems
						((APDE) getApplicationContext()).getEditor().clearUndoRedoHistory();
					}
					
					return true;
				}
			});
		}
		
		Preference version = frag.findPreference("pref_about_version");
		
		if (version != null) {
			try {
				version.setSummary(getPackageManager().getPackageInfo(getPackageName(), 0).versionName);
			} catch (PackageManager.NameNotFoundException e) {
				e.printStackTrace();
			}
		}
		
		Preference licenses = frag.findPreference("pref_about_licenses");
		
		if (licenses != null) {
			licenses.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
				@Override
				public boolean onPreferenceClick(Preference preference) {
					launchLicenses();
					
					return true;
				}
			});
		}
		
		Preference googlePlay = frag.findPreference("pref_about_google_play");
		
		if (googlePlay != null) {
			googlePlay.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
				@Override
				public boolean onPreferenceClick(Preference preference) {
					launchGooglePlay();
					
					return true;
				}
			});
		}
		
		Preference github = frag.findPreference("pref_about_github");
		
		if (github != null) {
			github.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
				@Override
				public boolean onPreferenceClick(Preference preference) {
					launchGitHub();
					
					return true;
				}
			});
		}
		
		Preference emailDev = frag.findPreference("pref_about_email_dev");
		
		if (emailDev != null) {
			emailDev.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
				@Override
				public boolean onPreferenceClick(Preference preference) {
					launchEmailDev();
					
					return true;
				}
			});
		}
		
		bindPreferenceSummaryToValue(frag.findPreference("textsize"));
		bindPreferenceSummaryToValue(frag.findPreference("textsize_console"));
		bindPreferenceSummaryToValue(frag.findPreference("pref_sketchbook_location"));
		bindPreferenceSummaryToValue(frag.findPreference("pref_key_undo_redo_keep"));
	}
	
	protected void launchLicenses() {
		startActivity(new Intent(this, LicensesActivity.class));
	}
	
	protected void launchGooglePlay() {
		//StackOverflow: http://stackoverflow.com/a/11753070
		
		final String appPackageName = getPackageName();
		try {
			startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + appPackageName)));
		} catch (android.content.ActivityNotFoundException e) {
			//If this is a non-Google Play device
			startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + appPackageName)));
		}
	}
	
	protected void launchGitHub() {
		startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(getResources().getString(R.string.pref_about_github_uri))));
	}
	
	protected void launchEmailDev() {
		Intent emailIntent = new Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", getResources().getString(R.string.pref_about_email_address), null));
		emailIntent.putExtra(Intent.EXTRA_SUBJECT, getResources().getString(R.string.pref_about_email_subject));
		startActivity(emailIntent);
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