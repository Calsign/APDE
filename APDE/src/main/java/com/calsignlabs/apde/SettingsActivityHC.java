package com.calsignlabs.apde;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
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
import android.view.ContextThemeWrapper;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.calsignlabs.apde.support.CustomListPreference;
import com.calsignlabs.apde.support.StockPreferenceFragment;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

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
		
		Preference previewChannel = frag.findPreference("pref_about_preview_channel");
		
		if (previewChannel != null) {
			previewChannel.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
				@Override
				public boolean onPreferenceClick(Preference preference) {
					launchPreviewChannel();
					
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
		
		Preference updateExamplesNow = frag.findPreference("update_examples_download_now");
		
		if (updateExamplesNow != null) {
			updateExamplesNow.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
				@Override
				public boolean onPreferenceClick(Preference preference) {
					launchUpdateExamplesNow();
					
					return true;
				}
			});
		}
		
		Preference displayRecentChanges = frag.findPreference("pref_whats_new_display");
		
		if (displayRecentChanges != null) {
			displayRecentChanges.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
				@Override
				public boolean onPreferenceClick(Preference preference) {
					launchDisplayRecentChanges(SettingsActivityHC.this);
					
					return true;
				}
			});
		}
		
		Preference sketchbookDrivePref = frag.findPreference("pref_sketchbook_drive");
		
		if (sketchbookDrivePref != null) {
			//Set up the list of available storage drives
			
			final CustomListPreference sketchbookDrive = (CustomListPreference) sketchbookDrivePref;
			final Preference sketchbookLocation = frag.findPreference("pref_sketchbook_location");
			
			final ArrayList<APDE.StorageDrive> drives = ((APDE) getApplication()).getStorageLocations();
			
			final CharSequence[] readables = new CharSequence[drives.size()];
			final CharSequence[] paths = new CharSequence[drives.size()];
			
			for (int i = 0; i < drives.size(); i ++) {
				APDE.StorageDrive drive = drives.get(i);
				
				readables[i] = drive.space + " " + drive.type.title + "\n" + drive.root.getAbsolutePath();
				paths[i] = drive.root.getAbsolutePath();
			}
			
			sketchbookDrive.setEntries(readables);
			sketchbookDrive.setEntryValues(paths);
			
			sketchbookDrive.setValue(PreferenceManager.getDefaultSharedPreferences(SettingsActivityHC.this).getString("pref_sketchbook_drive", ""));
			
			sketchbookDrive.init(R.layout.pref_sketchbook_drive_list_item, new CustomListPreference.Populator() {
				@Override
				public void populate(View view, int position, CharSequence[] entries) {
					LinearLayout layout = (LinearLayout) view;
					
					APDE.StorageDrive drive = drives.get(position);
					
					((TextView) layout.findViewById(R.id.pref_sketchbook_drive_list_item_text_type)).setText(drive.type.title);
					((TextView) layout.findViewById(R.id.pref_sketchbook_drive_list_item_text_space)).setText(drive.space);
					((TextView) layout.findViewById(R.id.pref_sketchbook_drive_list_item_text_root)).setText(drive.root.getAbsolutePath());
				}
			}, new Runnable() {
				@Override
				public void run() {
					updateSketchbookDrivePref(sketchbookDrive, sketchbookLocation, drives);
				}
			});
			
			updateSketchbookDrivePref(sketchbookDrive, sketchbookLocation, drives);
		}
		
		bindPreferenceSummaryToValue(frag.findPreference("textsize"));
		bindPreferenceSummaryToValue(frag.findPreference("textsize_console"));
		bindPreferenceSummaryToValue(frag.findPreference("pref_sketchbook_location"));
		bindPreferenceSummaryToValue(frag.findPreference("pref_key_undo_redo_keep"));
	}
	
	protected void updateSketchbookDrivePref(ListPreference sketchbookDrive, Preference sketchbookLocation, ArrayList<APDE.StorageDrive> drives) {
		int selectedIndex = sketchbookDrive.findIndexOfValue(sketchbookDrive.getValue());
		
		if (selectedIndex == -1) {
			//Uh-oh
			return;
		}
		
		APDE.StorageDrive selected = drives.get(selectedIndex);
		
		sketchbookLocation.setEnabled(!(selected.type.equals(APDE.StorageDrive.StorageDriveType.INTERNAL) || selected.type.equals(APDE.StorageDrive.StorageDriveType.SECONDARY_EXTERNAL)));
		sketchbookDrive.setSummary(selected.space + " " + selected.type.title);
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
	
	protected void launchPreviewChannel() {
		startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(getResources().getString(R.string.pref_about_preview_channel_uri))));
	}
	
	protected void launchEmailDev() {
		Intent emailIntent = new Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", getResources().getString(R.string.pref_about_email_address), null));
		emailIntent.putExtra(Intent.EXTRA_SUBJECT, getResources().getString(R.string.pref_about_email_subject));
		startActivity(emailIntent);
	}
	
	protected void launchUpdateExamplesNow() {
		((APDE) getApplication()).redownloadExamplesNow(SettingsActivityHC.this);
	}
	
	protected static void launchDisplayRecentChanges(Activity activity) {
		final Stack<String> releaseNotesStack = EditorActivity.getReleaseNotesStack(activity);
		
		AlertDialog.Builder builder = new AlertDialog.Builder(activity);
		builder.setTitle(R.string.pref_whats_new);
		
		RelativeLayout layout;
		
		if (android.os.Build.VERSION.SDK_INT >= 11) {
			layout = (RelativeLayout) View.inflate(new ContextThemeWrapper(activity, android.R.style.Theme_Holo_Dialog), R.layout.whats_new, null);
		} else {
			layout = (RelativeLayout) View.inflate(new ContextThemeWrapper(activity, android.R.style.Theme_Dialog), R.layout.whats_new, null);
		}
		
		final ListView list = (ListView) layout.findViewById(R.id.whats_new_list);
		final Button loadMore = (Button) layout.findViewById(R.id.whats_new_more);
		final CheckBox keepShowing = (CheckBox) layout.findViewById(R.id.whats_new_keep_showing);
		
		//Hide this for this view...
		keepShowing.setVisibility(View.GONE);
		
		final ArrayAdapter<String> listAdapter = new ArrayAdapter<String>(activity, R.layout.whats_new_list_item, R.id.whats_new_list_item_text);
		list.setAdapter(listAdapter);
		
		//Load five to start
		for (int i = 0; i < 5; i ++) {
			EditorActivity.addWhatsNewItem(list, listAdapter, releaseNotesStack, loadMore, false);
		}
		
		loadMore.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				//Load five at once
				for (int i = 0; i < 5; i++) {
					//Stop if we can't add any more
					if (!EditorActivity.addWhatsNewItem(list, listAdapter, releaseNotesStack, loadMore, true)) {
						break;
					}
				}
			}
		});
		
		builder.setView(layout);
		builder.setNeutralButton(R.string.ok, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {}
		});
		
		builder.create().show();
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