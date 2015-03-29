package com.calsignlabs.apde;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Vibrator;
import android.preference.ListPreference;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.view.MenuItem;
import android.view.WindowManager;

/**
 * Settings activity for API level 10
 */

/**
 * A {@link PreferenceActivity} that presents a set of application settings. On
 * handset devices, settings are presented as a single list. On tablets,
 * settings are split by category, with category headers shown to the left of
 * the list of settings.
 * <p>
 * See <a href="http://developer.android.com/design/patterns/settings.html">
 * Android Design: Settings</a> for design guidelines and the <a
 * href="http://developer.android.com/guide/topics/ui/settings.html">Settings
 * API Guide</a> for more information on developing a Settings UI.
 */
public class SettingsActivity extends PreferenceActivity {
	/**
	 * Determines whether to always show the simplified settings UI, where
	 * settings are presented in a single list. When false, settings are shown
	 * as a master/detail two-pane view on tablets. When true, a single pane is
	 * shown on tablets.
	 */
	private static final boolean ALWAYS_SIMPLE_PREFS = true;
	
	@SuppressLint("NewApi")
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.activity_settings);
		
		if(android.os.Build.VERSION.SDK_INT >= 11) { //Yet another unfortunate casualty of AppCompat
			getActionBar().setDisplayHomeAsUpEnabled(true);
		}
	}
	
	@SuppressLint("NewApi")
	@Override
	protected void onPostCreate(Bundle savedInstanceState) {
		super.onPostCreate(savedInstanceState);
		
		setupSimplePreferencesScreen();
		
		//This is an unfortunate casualty of the switch to AppCompat...
		if(android.os.Build.VERSION.SDK_INT >= 11)
			getActionBar().setDisplayHomeAsUpEnabled(true);
		
		getWindow().getDecorView().setBackgroundColor(getResources().getColor(R.color.activity_background));
	}
	
	/**
	 * Shows the simplified settings UI if the device configuration if the
	 * device configuration dictates that a simplified, single-pane UI should be
	 * shown.
	 */
	@SuppressLint("NewApi")
	@SuppressWarnings({ "deprecation", "unused" })
	private void setupSimplePreferencesScreen() {
		if(!isSimplePreferences(this))
			return;
		
		// In the simplified UI, fragments are not used at all and we instead
		// use the older PreferenceActivity APIs.
		
		// Add 'general' preferences
		addPreferencesFromResource(R.xml.pref_general);
		// Add 'examples' preferences
		addPreferencesFromResource(R.xml.pref_examples);
		// Add 'editor' preferences
		addPreferencesFromResource(R.xml.pref_editor);
		// Add 'build' preferences
		addPreferencesFromResource(R.xml.pref_build);
		// Add 'coding assistance' preferences
		addPreferencesFromResource(R.xml.pref_code_assistance);
		// Add 'about' preferences
		addPreferencesFromResource(R.xml.pref_about);
		
		// Bind the summaries of EditText/List/Dialog/Ringtone preferences to
		// their values. When their values change, their summaries are updated
		// to reflect the new value, per the Android Design guidelines.
		bindPreferenceSummaryToValue(findPreference("textsize"));
		bindPreferenceSummaryToValue(findPreference("textsize_console"));
		bindPreferenceSummaryToValue(findPreference("pref_sketchbook"));
		bindPreferenceSummaryToValue(findPreference("pref_key_undo_redo_keep"));
		
		((CheckBoxPreference) findPreference("use_hardware_keyboard")).setOnPreferenceChangeListener(new CheckBoxPreference.OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
			    if(PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getBoolean("use_hardware_keyboard", false))
			    	getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
			    else
			    	getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
				
				return true;
			}
		});
		
		//Hide the "Enable Vibration" preference if the vibrator isn't available
		
		Vibrator vibrate = (Vibrator) getSystemService(VIBRATOR_SERVICE);
		if(Build.VERSION.SDK_INT >= 11)
			//This only works on API >= 11
			if(!vibrate.hasVibrator())
				getPreferenceScreen().removePreference(findPreference("pref_vibrate"));
		else
			// getSystemService(VIBRATOR_SERVICE) on API < 11 returns null if the vibrator isn't available
			//...maybe this doesn't work...
			if(vibrate == null)
				getPreferenceScreen().removePreference(findPreference("pref_vibrate"));
		
		final CheckBoxPreference enableUndoRedo = (CheckBoxPreference) findPreference("pref_key_undo_redo");
		
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
		
		Preference version = findPreference("pref_about_version");
		
		if (version != null) {
			try {
				version.setSummary(getPackageManager().getPackageInfo(getPackageName(), 0).versionName);
			} catch (PackageManager.NameNotFoundException e) {
				e.printStackTrace();
			}
		}
		
		Preference licenses = findPreference("pref_about_licenses");
		
		if (licenses != null) {
			licenses.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
				@Override
				public boolean onPreferenceClick(Preference preference) {
					launchLicenses();
					
					return true;
				}
			});
		}
		
		Preference googlePlay = findPreference("pref_about_google_play");
		
		if (googlePlay != null) {
			googlePlay.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
				@Override
				public boolean onPreferenceClick(Preference preference) {
					launchGooglePlay();
					
					return true;
				}
			});
		}
		
		Preference github = findPreference("pref_about_github");
		
		if (github != null) {
			github.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
				@Override
				public boolean onPreferenceClick(Preference preference) {
					launchGitHub();
					
					return true;
				}
			});
		}
		
		Preference emailDev = findPreference("pref_about_email_dev");
		
		if (emailDev != null) {
			emailDev.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
				@Override
				public boolean onPreferenceClick(Preference preference) {
					launchEmailDev();
					
					return true;
				}
			});
		}
		
		Preference updateExamplesNow = findPreference("update_examples_download_now");
		
		if (updateExamplesNow != null) {
			updateExamplesNow.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
				@Override
				public boolean onPreferenceClick(Preference preference) {
					launchUpdateExamplesNow();
					
					return true;
				}
			});
		}
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
	
	protected void launchUpdateExamplesNow() {
		((APDE) getApplication()).redownloadExamplesNow(SettingsActivity.this);
	}
	
	/** {@inheritDoc} */
	@Override
	public boolean onIsMultiPane() {
		return isXLargeTablet(this) && !isSimplePreferences(this);
	}

	/**
	 * Helper method to determine if the device has an extra-large screen. For
	 * example, 10" tablets are extra-large.
	 */
	private static boolean isXLargeTablet(Context context) {
		return (context.getResources().getConfiguration().screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_XLARGE;
	}

	/**
	 * Determines whether the simplified settings UI should be shown. This is
	 * true if this is forced via {@link #ALWAYS_SIMPLE_PREFS}, or the device
	 * doesn't have newer APIs like {@link PreferenceFragment}, or the device
	 * doesn't have an extra-large screen. In these cases, a single-pane
	 * "simplified" settings UI should be shown.
	 */
	@SuppressWarnings("unused")
	private static boolean isSimplePreferences(Context context) {
		return ALWAYS_SIMPLE_PREFS
				|| Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB
				|| !isXLargeTablet(context);
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

	/**
	 * Binds a preference's summary to its value. More specifically, when the
	 * preference's value is changed, its summary (line of text below the
	 * preference title) is updated to reflect the value. The summary is also
	 * immediately updated upon calling this method. The exact display format is
	 * dependent on the type of preference.
	 * 
	 * @see #sBindPreferenceSummaryToValueListener
	 */
	private static void bindPreferenceSummaryToValue(Preference preference) {
		// Set the listener to watch for value changes.
		preference.setOnPreferenceChangeListener(sBindPreferenceSummaryToValueListener);
		
		// Trigger the listener immediately with the preference's
		// current value.
		sBindPreferenceSummaryToValueListener.onPreferenceChange(
				preference,
				PreferenceManager.getDefaultSharedPreferences(
						preference.getContext()).getString(preference.getKey(), ""));
	}

	/**
	 * This fragment shows general preferences only. It is used when the
	 * activity is showing a two-pane settings UI.
	 */
	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	public static class GeneralPreferenceFragment extends PreferenceFragment {
		@Override
		public void onCreate(Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);
			addPreferencesFromResource(R.xml.pref_general);

			// Bind the summaries of EditText/List/Dialog/Ringtone preferences
			// to their values. When their values change, their summaries are
			// updated to reflect the new value, per the Android Design
			// guidelines.
		}
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
