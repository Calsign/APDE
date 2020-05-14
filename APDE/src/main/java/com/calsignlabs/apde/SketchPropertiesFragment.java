package com.calsignlabs.apde;

import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import com.calsignlabs.apde.build.SketchProperties;
import com.calsignlabs.apde.support.FileSelection;
import com.takisoft.preferencex.EditTextPreference;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Objects;

public class SketchPropertiesFragment extends PreferenceFragmentCompat {
	@SuppressWarnings("FieldCanBeLocal")
	private SharedPreferences.OnSharedPreferenceChangeListener prefListener;
	
	// The change icon dialog layout
	private ScrollView changeIconLayout;
	// This is used in the change icon dialog
	protected EditText iconFile;
	// The change icon dialog "OK" button
	private Button changeIconOK;
	
	private SketchPropertiesActivity getSketchPropertiesActivity() {
		return (SketchPropertiesActivity) getActivity();
	}
	
	private APDE getGlobalState() {
		return getSketchPropertiesActivity().getGlobalState();
	}
	
	public static void updatePrefs(APDE global) {
		SketchProperties properties = global.getProperties();
		
		SharedPreferences.Editor edit = global.getSharedPreferences(global.getSketchName(), 0).edit();
		edit.putString("prop_pretty_name", properties.getDisplayName(global.getSketchName()));
		edit.putString("prop_package_name", properties.getPackageName(global.getSketchName()));
		edit.putString("permissions", properties.getPermissionsString());
		edit.putString("prop_target_sdk", Integer.toString(properties.getTargetSdk()));
		edit.putString("prop_min_sdk", Integer.toString(properties.getMinSdk()));
		edit.putString("prop_orientation", properties.getOrientation());
		edit.putString("prop_version_code", Integer.toString(properties.getVersionCode()));
		edit.putString("prop_pretty_version", properties.getVersionName());
		edit.apply();
	}
	
	@Override
	public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
		//Switch to the preferences for the current sketch
		getPreferenceManager().setSharedPreferencesName(getGlobalState().getSketchName());
		
		updatePrefs(getGlobalState());
		
		// In the simplified UI, fragments are not used at all and we instead
		// use the older PreferenceActivity APIs.
		
		// Add preferences.
		addPreferencesFromResource(R.xml.sketch_properties);
		
		EditTextPreference prettyName = Objects.requireNonNull(findPreference("prop_pretty_name"));
		EditTextPreference packageName = Objects.requireNonNull(findPreference("prop_package_name"));
		EditTextPreference versionCode = Objects.requireNonNull(findPreference("prop_version_code"));
		EditTextPreference prettyVersion = Objects.requireNonNull(findPreference("prop_pretty_version"));
		EditTextPreference minSdk = Objects.requireNonNull(findPreference("prop_min_sdk"));
		EditTextPreference targetSdk = Objects.requireNonNull(findPreference("prop_target_sdk"));
		ListPreference orientation = Objects.requireNonNull(findPreference("prop_orientation"));
		
		// Bind the summaries of EditText/List/Dialog/Ringtone preferences to
		// their values. When their values change, their summaries are updated
		// to reflect the new value, per the Android Design guidelines.
		bindPreferenceSummaryToValue(prettyName);
		bindPreferenceSummaryToValue(packageName);
		bindPreferenceSummaryToValue(versionCode);
		bindPreferenceSummaryToValue(prettyVersion);
		bindPreferenceSummaryToValue(minSdk);
		bindPreferenceSummaryToValue(targetSdk);
		bindPreferenceSummaryToValue(orientation);
		
		//Hacky way of setting up the summaries initially
		prettyName.setSummary(prettyName.getText());
		packageName.setSummary(packageName.getText());
		versionCode.setSummary(versionCode.getText());
		prettyVersion.setSummary(prettyVersion.getText());
		minSdk.setSummary(minSdk.getText());
		targetSdk.setSummary(targetSdk.getText());
		orientation.setSummary(orientation.getEntry());
		
		configureEditTextPreference(prettyName, InputType.TYPE_CLASS_TEXT);
		configureEditTextPreference(packageName, InputType.TYPE_CLASS_TEXT);
		configureEditTextPreference(versionCode, InputType.TYPE_CLASS_NUMBER);
		configureEditTextPreference(prettyVersion, InputType.TYPE_CLASS_TEXT);
		configureEditTextPreference(minSdk, InputType.TYPE_CLASS_NUMBER, 2);
		configureEditTextPreference(targetSdk, InputType.TYPE_CLASS_NUMBER, 2);
		
		Preference launchPermissions = findPreference("prop_permissions");
		launchPermissions.setOnPreferenceClickListener(preference -> {
			launchPermissions();
			return true;
		});
		
		Preference launchAddFile = findPreference("prop_add_file");
		launchAddFile.setOnPreferenceClickListener(preference -> {
			launchAddFile();
			return true;
		});
		
		Preference launchSketchFolder = findPreference("prop_show_sketch_folder");
		launchSketchFolder.setOnPreferenceClickListener(preference -> {
			getGlobalState().launchSketchFolder(getSketchPropertiesActivity());
			return true;
		});
		
		if (getGlobalState().isTemp() || getGlobalState().isSketchbook() &&
				getGlobalState().getSketchbookDrive().type.equals(APDE.StorageDrive.StorageDriveType.INTERNAL)) {
			
			// We can't show the sketch folder of a temp sketch because it's in the internal storage
			// And we can't show sketches when the drive is set to internal
			launchSketchFolder.setEnabled(false);
		}
		
		Preference launchChangeIcon = findPreference("prop_change_icon");
		launchChangeIcon.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				launchChangeIcon();
				return true;
			}
		});
		
		//If this is an example...
		if(getGlobalState().isExample()) {
			//...disable all of the preferences
			findPreference("prop_manifest").setEnabled(false);
			findPreference("prop_sketch_folder").setEnabled(false);
		}
		
		// This can't be an anonymous class because SharedPreferences keeps listeners in a WeakHashMap...
		// ...or a local instance, for that matter
		// StackOverflow: http://stackoverflow.com/questions/2542938/sharedpreferences-onsharedpreferencechangelistener-not-being-called-consistently
		prefListener = (pref, key) -> {
			SketchProperties properties = getGlobalState().getProperties();
			
			if (key.equals("prop_pretty_name"))
				properties.setDisplayName(pref.getString(key, ""));
			if (key.equals("prop_package_name"))
				properties.setPackageName(pref.getString(key, ""));
			if (key.equals("prop_version_code"))
				properties.setVersionCode(Integer.parseInt(pref.getString(key, getResources().getString(R.string.prop_version_code_default))));
			if (key.equals("prop_pretty_version"))
				properties.setVersionName(pref.getString(key, getResources().getString(R.string.prop_pretty_version_default)));
			if (key.equals("permissions"))
				properties.setPermissionsString(pref.getString(key, ""));
			if (key.equals("prop_target_sdk"))
				properties.setTargetSdk(Integer.parseInt(pref.getString("prop_target_sdk", getResources().getString(R.string.prop_target_sdk_default))));
			if (key.equals("prop_min_sdk"))
				properties.setMinSdk(Integer.parseInt(pref.getString("prop_min_sdk", getResources().getString(R.string.prop_min_sdk_default))));
			if (key.equals("prop_orientation"))
				properties.setOrientation(pref.getString("prop_orientation", getResources().getString(R.string.prop_orientation_default)));
			
			properties.save(getGlobalState().getSketchPropertiesFile());
		};
		
		if (!getGlobalState().isExample() && !getGlobalState().isTemp()) {
			// Set "Show Sketch Folder"'s summary to be the absolute path of the sketch
			findPreference("prop_show_sketch_folder").setSummary(getGlobalState().getSketchLocation().getAbsolutePath());
		}
		
		// Detect changes to the preferences so that we can save them to the manifest file directly
		// TODO This isn't an optimal solution - we still use SharedPreferences
		getSketchPropertiesActivity().getSharedPreferences(getGlobalState().getSketchName(), 0).registerOnSharedPreferenceChangeListener(prefListener);
	}
	
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
	
	private void configureEditTextPreference(EditTextPreference pref, final int inputType, final int maxLength) {
		pref.setOnBindEditTextListener(editText -> {
			editText.setMaxLines(1);
			editText.setSingleLine();
			editText.setSelectAllOnFocus(true);
			editText.setInputType(inputType);
			if (maxLength >= 0) {
				editText.setFilters(new InputFilter[] {new InputFilter.LengthFilter(maxLength)});
			}
		});
	}
	
	private void configureEditTextPreference(EditTextPreference pref, int inputType) {
		configureEditTextPreference(pref, inputType, -1);
	}
	
	private void launchPermissions() {
		Intent intent = new Intent(getSketchPropertiesActivity(), PermissionsActivity.class);
		startActivity(intent);
	}
	
	public void launchAddFile() {
		Intent intent = FileSelection.createFileSelectorIntent(true, null);
		getSketchPropertiesActivity().startActivityForResult(intent, SketchPropertiesActivity.REQUEST_CHOOSER);
	}
	
	@SuppressLint({ "InlinedApi", "NewApi" })
	public void launchChangeIcon() {
		AlertDialog.Builder builder = new AlertDialog.Builder(getSketchPropertiesActivity());
		builder.setTitle(R.string.prop_change_icon);
		
		changeIconLayout = (ScrollView) View.inflate(new ContextThemeWrapper(getSketchPropertiesActivity(), R.style.Theme_AppCompat_Dialog), R.layout.change_icon, null);
		
		iconFile = (EditText) changeIconLayout.findViewById(R.id.change_icon_file);
		final ImageButton iconFileSelect = (ImageButton) changeIconLayout.findViewById(R.id.change_icon_file_select);
		
		//Scale format radio group
		final RadioGroup scaleFormat = (RadioGroup) changeIconLayout.findViewById(R.id.format_scale);
		
		for (int i = 0; i < scaleFormat.getChildCount(); i ++) {
			((RadioButton) scaleFormat.getChildAt(i)).setEnabled(false);
		}
		
		scaleFormat.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(RadioGroup group, int checkedId) {
				rebuildIconChange();
			}
		});
		
		//Alt text for the big icon
		final TextView bigIconAltText = (TextView) changeIconLayout.findViewById(R.id.big_icon_alt_text);
		
		bigIconAltText.setAllCaps(true);
		
		builder.setView(changeIconLayout);
		
		builder.setNegativeButton(R.string.cancel, (dialog, which) -> {});
		
		builder.setPositiveButton(R.string.ok, (dialog, which) -> {
			Bitmap bitmap = loadBitmap();
			
			if (bitmap != null) {
				int minDim = Math.min(bitmap.getWidth(), bitmap.getHeight());
				
				int[] dims = {36, 48, 72, 96, 144, 192};
				
				for (int dim : dims) {
					File out = new File(getGlobalState().getSketchLocation(), "icon-" + dim + ".png");
					
					//Don't scale the image up
					if (dim <= minDim) {
						FileOutputStream stream = null;
						
						try {
							stream = new FileOutputStream(out);
							
							//Replace the old icon
							formatIcon(bitmap, dim, getScaleFormat(scaleFormat)).compress(Bitmap.CompressFormat.PNG, 100, stream);
						} catch (FileNotFoundException e) {
							e.printStackTrace();
							//...
						} finally {
							//Always close the stream...
							if (stream != null) {
								try {
									stream.close();
								} catch (IOException e) {
									e.printStackTrace();
									//Whatever at this point...
								}
							}
						}
					} else {
						//Get rid of the old icon...
						out.delete();
					}
				}
			}
		});
		
		AlertDialog dialog = builder.create();
		dialog.show();
		
		changeIconOK = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
		changeIconOK.setEnabled(false);
		
		iconFileSelect.setOnClickListener(view -> {
			Intent intent = FileSelection.createFileSelectorIntent(false, new String[] {"image/*"});
			getSketchPropertiesActivity().startActivityForResult(intent, SketchPropertiesActivity.REQUEST_ICON_FILE);
		});
		
		iconFile.addTextChangedListener(new TextWatcher() {
			@Override
			public void afterTextChanged(Editable arg0) {
				rebuildIconChange();
			}
			
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
			
			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {}
		});
		
		rebuildIconChange();
	}
	
	private Bitmap loadBitmap() {
		ParcelFileDescriptor fd = FileSelection.openUri(getSketchPropertiesActivity(),
				Uri.parse(iconFile.getText().toString()), FileSelection.Mode.READ, true);
		
		if (fd == null) {
			// Technically this check shouldn't be necessary, but there's some kind of stupid
			// issue on 5.1 that necessitates this check
			File file = new File(iconFile.getText().toString());
			if (file.exists() && file.isFile()) {
				// Let the user type in a path manually
				fd = FileSelection.openUri(getSketchPropertiesActivity(),
						FileSelection.pathToUri(iconFile.getText().toString()), FileSelection.Mode.READ, true);
			}
		}
		
		if (fd == null) {
			return null;
		}
		
		Bitmap bitmap = BitmapFactory.decodeFileDescriptor(fd.getFileDescriptor());
		FileSelection.closeFd(fd);
		
		return bitmap;
	}
	
	public void rebuildIconChange() {
		//Original image
		final ImageView bigIcon = changeIconLayout.findViewById(R.id.big_icon);
		//Image after cropping and scaling
		final ImageView smallIcon = changeIconLayout.findViewById(R.id.small_icon);
		
		//Alt text for the big icon
		final TextView bigIconAltText = changeIconLayout.findViewById(R.id.big_icon_alt_text);
		
		//Scale format radio group
		final RadioGroup scaleFormat = changeIconLayout.findViewById(R.id.format_scale);
		
		Bitmap bitmap = loadBitmap();
		
		if (bitmap != null) {
			int w = bitmap.getWidth();
			int h = bitmap.getHeight();
			
			int dim = changeIconLayout.getWidth();
			
			if (w > dim || h > dim) {
				//Resize the bitmap to fit the dialog
				
				if (Math.min(w, h) == w) {
					int scaleW = Math.round(w / (((float) h) / dim));
					
					bigIcon.setImageBitmap(Bitmap.createScaledBitmap(bitmap, scaleW, dim, false));
				} else {
					int scaleH = Math.round(h / (((float) w) / dim));
					
					bigIcon.setImageBitmap(Bitmap.createScaledBitmap(bitmap, dim, scaleH, false));
				}
			} else {
				bigIcon.setImageBitmap(bitmap);
			}
			
			int iconSize = Math.round(36 * getResources().getDisplayMetrics().density);
			
			smallIcon.setImageBitmap(formatIcon(bitmap, iconSize, getScaleFormat(scaleFormat)));
			
			bigIcon.setVisibility(View.VISIBLE);
			bigIconAltText.setVisibility(View.GONE);
			
			changeIconOK.setEnabled(true);
			for (int i = 0; i < scaleFormat.getChildCount(); i++) {
				((RadioButton) scaleFormat.getChildAt(i)).setEnabled(true);
			}
			
			return;
		}
		
		//If we were unable to load the image...
		
		bigIcon.setImageBitmap(null);
		
		//Load the old icon for the current sketch
		
		File sketchFolder = getGlobalState().getSketchLocation();
		String[] iconTitles = com.calsignlabs.apde.build.Build.ICON_LIST;
		
		String iconPath = "";
		
		for (String iconTitle : iconTitles) {
			File icon = new File(sketchFolder, iconTitle);
			
			if (icon.exists()) {
				iconPath = icon.getAbsolutePath();
				break;
			}
		}
		
		if (!iconPath.equals("")) {
			Bitmap oldIcon = BitmapFactory.decodeFile(iconPath);
			
			if (oldIcon != null) {
				smallIcon.setImageBitmap(oldIcon);
			} else {
				//Uh-oh, some error occurred...
			}
		} else {
			smallIcon.setImageDrawable(getResources().getDrawable(R.drawable.default_icon));
		}
		
		bigIcon.setVisibility(View.GONE);
		bigIconAltText.setVisibility(View.VISIBLE);
		
		changeIconOK.setEnabled(false);
		for (int i = 0; i < scaleFormat.getChildCount(); i ++) {
			((RadioButton) scaleFormat.getChildAt(i)).setEnabled(false);
		}
	}
	
	private static final int FORMAT_SCALE_MASK = 0x0000000F;
	
	private static final int FORMAT_SCALE_CROP = 0x00000001;
	private static final int FORMAT_SCALE_CENTER = 0x00000002;
	private static final int FORMAT_SCALE_RESIZE = 0x00000003;
	
	private Bitmap formatIcon(Bitmap bitmap, int dim, int options) {
		Bitmap working;
		
		int w = bitmap.getWidth();
		int h = bitmap.getHeight();
		
		switch (options & FORMAT_SCALE_MASK) {
			case FORMAT_SCALE_CROP:
				if (Math.min(w, h) == w) {
					int scaleH = Math.round(h / (((float) w) / dim));
					int dif = scaleH - dim;
					
					working = Bitmap.createBitmap(Bitmap.createBitmap(Bitmap.createScaledBitmap(bitmap, dim, scaleH, false), 0, dif / 2, dim, dim));
				} else {
					int scaleW = Math.round(w / (((float) h) / dim));
					int dif = scaleW - dim;
					
					working = Bitmap.createBitmap(Bitmap.createBitmap(Bitmap.createScaledBitmap(bitmap, scaleW, dim, false), dif / 2, 0, dim, dim));
				}
				
				break;
			case FORMAT_SCALE_CENTER:
				//Scale down and pad the image
				
				working = Bitmap.createBitmap(dim, dim, Bitmap.Config.ARGB_8888);
				Canvas canvas = new Canvas(working);
				canvas.drawARGB(0, 255, 255, 255); //Make the background transparent
				
				if (Math.min(w, h) == w) {
					int scaleW = Math.round(w / (((float) h) / dim));
					int dif = dim - scaleW;
					
					canvas.drawBitmap(Bitmap.createBitmap(Bitmap.createScaledBitmap(bitmap, scaleW, dim, false)), dif / 2, 0, null);
				} else {
					int scaleH = Math.round(h / (((float) w) / dim));
					int dif = dim - scaleH;
					
					canvas.drawBitmap(Bitmap.createBitmap(Bitmap.createScaledBitmap(bitmap, dim, scaleH, false)), 0, dif / 2, null);
				}
				
				break;
			case FORMAT_SCALE_RESIZE:
			default:
				working = Bitmap.createBitmap(Bitmap.createScaledBitmap(bitmap, dim, dim, false));
				
				break;
		}
		
		return working;
	}
	
	private int getScaleFormat(RadioGroup scaleFormat) {
		//Yes, this is incredibly redundant...
		switch (scaleFormat.getCheckedRadioButtonId()) {
			case R.id.format_scale_crop:
				return FORMAT_SCALE_CROP;
			case R.id.format_scale_center:
				return FORMAT_SCALE_CENTER;
			case R.id.format_scale_resize:
				return FORMAT_SCALE_RESIZE;
			default:
				return -1;
		}
	}
}
