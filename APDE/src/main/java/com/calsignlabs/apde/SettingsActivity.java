package com.calsignlabs.apde;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Vibrator;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.preference.CheckBoxPreference;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceCategory;
import android.support.v7.preference.PreferenceManager;
import android.support.v7.preference.PreferenceScreen;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.calsignlabs.apde.build.CopyAndroidJarTask;
import com.calsignlabs.apde.support.CustomListPreference;
import com.calsignlabs.apde.support.StockPreferenceFragment;
import com.takisoft.fix.support.v7.preference.PreferenceFragmentCompat;
import com.takisoft.fix.support.v7.preference.SwitchPreferenceCompat;

import java.util.ArrayList;
import java.util.Stack;

public class SettingsActivity extends AppCompatActivity implements PreferenceFragmentCompat.OnPreferenceStartFragmentCallback, PreferenceFragmentCompat.OnPreferenceStartScreenCallback {
	private static final String STATE_MULTI_PANE = "stateIsMultiPane";
	
	private static boolean wasMultiPaneAtStart;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.activity_settings);
		
		if (savedInstanceState == null) {
			addHeadersFragment();
			if (isMultiPane()) {
				addFirstSettingsFragment();
			}
			
			Log.d("testing... ", "no icicle");
		} else {
			boolean wasMultiPane = savedInstanceState.getBoolean(STATE_MULTI_PANE);
			
			// I tried this, but I can't remove the headers fragment from the backstack when
			// switching from multi-pane to single-pane... so we're just resetting to the beginning
			
//			if (isMultiPane() && !wasMultiPane) {
//				addHeadersFragment();
//				
//				if (getSupportFragmentManager().findFragmentById(R.id.settings_fragment_container) instanceof SettingsHeadersFragment) {
//					addFirstSettingsFragment();
//				}
//			} else if (!isMultiPane() && wasMultiPane) {
//				clearFragmentBackstack();
//				addHeadersFragment();
//			}
			
			// Orientation change
			if (isMultiPane() != wasMultiPane) {
				clearFragmentBackstack();
				addHeadersFragment();
				if (isMultiPane()) {
					addFirstSettingsFragment();
				}
			}
		}
		
		wasMultiPaneAtStart = isMultiPane();
	}
	
	@Override
	public void onSaveInstanceState(Bundle icicle) {
		// By the time we execute this, the orientation change has already happened...
		// so we need to use the state from when the activity was initialized
		icicle.putBoolean(STATE_MULTI_PANE, wasMultiPaneAtStart);
		
		super.onSaveInstanceState(icicle);
	}
	
	protected void clearFragmentBackstack() {
		getSupportFragmentManager().popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
		getSupportFragmentManager().executePendingTransactions();
	}
	
	@Override
	protected void onPostCreate(Bundle savedInstanceState) {
		super.onPostCreate(savedInstanceState);
		
		((Toolbar) findViewById(R.id.toolbar)).setNavigationOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
					getSupportFragmentManager().popBackStack();
				} else {
					finish();
				}
			}
		});
		
		getWindow().getDecorView().setBackgroundColor(getResources().getColor(R.color.activity_background));
	}
	
	public boolean isMultiPane() {
		return getResources().getBoolean(R.bool.tablet_multi_pane);
	}
	
	@SuppressLint("NewApi")
	public void checkPreferences(PreferenceFragmentCompat frag) {
		SwitchPreferenceCompat hardwareKeyboard = ((SwitchPreferenceCompat) frag.findPreference("use_hardware_keyboard"));
		
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
		
		final SwitchPreferenceCompat enableUndoRedo = (SwitchPreferenceCompat) frag.findPreference("pref_key_undo_redo");
		
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
		
		Preference useOldAaptBinary = frag.findPreference("pref_build_aapt_binary");
		
		if (useOldAaptBinary != null) {
			boolean usePie = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN;
			boolean isArm = android.os.Build.CPU_ABI.startsWith("arm");
			
			// Disable the "Use pre-0.3.3 AAPT Binary" debug preference on devices that don't seem to have these types of issues
			if (!(usePie && isArm)) {
				((PreferenceCategory) frag.findPreference("pref_build_debug")).removePreference(useOldAaptBinary);
			}
		}
		
		Preference recopyAndroidJar = frag.findPreference("pref_build_recopy_android_jar");
		
		if (recopyAndroidJar != null) {
			recopyAndroidJar.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
				@Override
				public boolean onPreferenceClick(Preference preference) {
					((APDE) getApplication()).getTaskManager().launchTask("recopyAndroidJarTask", false, null, false, new CopyAndroidJarTask());
					
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
					launchDisplayRecentChanges(SettingsActivity.this);
					
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
			
			sketchbookDrive.setValue(PreferenceManager.getDefaultSharedPreferences(SettingsActivity.this).getString("pref_sketchbook_drive", ""));
			
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
		bindPreferenceSummaryToValue(frag.findPreference("pref_key_autosave_timeout"));
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
		((APDE) getApplication()).redownloadExamplesNow(SettingsActivity.this);
	}
	
	protected static void launchDisplayRecentChanges(Activity activity) {
		final Stack<String> releaseNotesStack = EditorActivity.getReleaseNotesStack(activity);
		
		AlertDialog.Builder builder = new AlertDialog.Builder(activity);
		builder.setTitle(R.string.pref_whats_new);
		
		RelativeLayout layout;
		
		layout = (RelativeLayout) View.inflate(new ContextThemeWrapper(activity, R.style.Theme_AppCompat_Dialog), R.layout.whats_new, null);
		
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
	
	protected void addHeadersFragment() {
		Fragment existingFragment = getSupportFragmentManager().findFragmentById(R.id.settings_fragment_container);
		
		SettingsHeadersFragment newFragment = new SettingsHeadersFragment();
		FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
		transaction.add(isMultiPane() ? R.id.settings_list_fragment_container : R.id.settings_fragment_container, newFragment);
		
		if (!isMultiPane() && existingFragment != null) {
			transaction.remove(existingFragment);
		}
		
		transaction.commit();
	}
	
	protected void addFirstSettingsFragment() {
		Bundle args = new Bundle(1);
		args.putString("resource", getResources().getStringArray(R.array.settings_headers_fragments)[0]);
		
		Fragment existingFragment = getSupportFragmentManager().findFragmentById(R.id.settings_fragment_container);
		
		StockPreferenceFragment newFragment = new StockPreferenceFragment();
		newFragment.setArguments(args);
		
		FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
		
		if (existingFragment != null) {
			transaction.remove(existingFragment);
		}
		
		transaction.add(R.id.settings_fragment_container, newFragment);
		transaction.commit();
	}
	
	@Override
	public boolean onPreferenceStartFragment(android.support.v7.preference.PreferenceFragmentCompat preferenceFragmentCompat, Preference preference) {
		// This is only ever called from a settings screen, not from the headers fragment
		
		StockPreferenceFragment newFragment = new StockPreferenceFragment();
		newFragment.setArguments(preference.getExtras());
		
		FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
		transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
		
		transaction.remove(preferenceFragmentCompat);
		
		transaction.add(R.id.settings_fragment_container, newFragment);
		transaction.addToBackStack(null);
		
		transaction.commit();
		
		return true;
	}
	
	public void selectSettingsFragment(String preferencesXml) {
		Bundle args = new Bundle(1);
		args.putString("resource", preferencesXml);
		
		StockPreferenceFragment newFragment = new StockPreferenceFragment();
		newFragment.setArguments(args);
		
		FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
		transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
		
		transaction.remove(getSupportFragmentManager().findFragmentById(R.id.settings_fragment_container));
		
		transaction.add(R.id.settings_fragment_container, newFragment);
		if (!isMultiPane()) {
			transaction.addToBackStack(null);
		}
		
		transaction.commit();
	}
	
	@Override
	public boolean onPreferenceStartScreen(android.support.v7.preference.PreferenceFragmentCompat preferenceFragmentCompat, PreferenceScreen preferenceScreen) {
		return false;
	}
	
	public static class SettingsHeadersFragment extends Fragment {
		private View rootView;
		
		@Override
		public void onCreate(Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);
		}
		
		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
			if (rootView == null) {
				rootView = inflater.inflate(R.layout.fragment_settings_headers, container, false);
			}
			
			return rootView;
		}
		
		public ListView getListView() {
			return (ListView) rootView;
		}
		
		public SettingsActivity getSettingsActivity() {
			return (SettingsActivity) getActivity();
		}
		
		public void onStart() {
			super.onStart();
			
			final String[] headers = getResources().getStringArray(R.array.settings_headers);
			final String[] headersFragments = getResources().getStringArray(R.array.settings_headers_fragments);
			
			getListView().setAdapter(new BaseAdapter() {
				@Override
				public int getCount() {
					return headers.length;
				}
				
				@Override
				public Object getItem(int position) {
					return headers[position];
				}
				
				@Override
				public long getItemId(int position) {
					return position;
				}
				
				@Override
				public View getView(int position, View convertView, ViewGroup parent) {
					if (convertView == null) {
						LayoutInflater inflater = (LayoutInflater) getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
						convertView = inflater.inflate(R.layout.settings_headers_list_item, parent, false);
					}
					
					((TextView) convertView).setText(headers[position]);
					
					return convertView;
				}
			});
			
			getListView().setOnItemClickListener(new AdapterView.OnItemClickListener() {
				@Override
				public void onItemClick(AdapterView<?> adapterView, View view, int position, long l) {
					if (getSettingsActivity().isMultiPane()) {
						view.setSelected(true);
					}
					
					getSettingsActivity().selectSettingsFragment(headersFragments[position]);
				}
			});
			
			if (getSettingsActivity().isMultiPane()) {
				getListView().setChoiceMode(ListView.CHOICE_MODE_SINGLE);
				getListView().setSelection(0);
				getListView().setItemChecked(0, true);
			}
		}
	}
}