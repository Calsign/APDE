package com.calsignlabs.apde;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import com.calsignlabs.apde.build.Manifest;
import com.ipaulpro.afilechooser.utils.FileUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.Map.Entry;

public class SketchPropertiesActivity extends PreferenceActivity implements Toolbar.OnMenuItemClickListener {
	//This is a number, that's all that matters
	private static final int REQUEST_CHOOSER = 6283;
	//This is another number - this time, it's for something else
	private static final int REQUEST_ICON_FILE = 9864;
	
	private static final boolean ALWAYS_SIMPLE_PREFS = true;
	
	private OnSharedPreferenceChangeListener prefListener;
	
	//The change icon dialog layout
	private ScrollView changeIconLayout;
	//This is used in the change icon dialog
	private EditText iconFile;
	//The change icon dialog "OK" button
	private Button changeIconOK;
	
	private Toolbar toolbar;
	
	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.activity_sketch_properties);
		
		// StackOverflow: http://stackoverflow.com/a/27455330/1628609
		LinearLayout root = (LinearLayout) findViewById(android.R.id.list).getParent().getParent().getParent();
		toolbar = (Toolbar) LayoutInflater.from(this).inflate(R.layout.settings_toolbar, root, false);
		root.addView(toolbar, 0);
		
		toolbar.setNavigationOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				finish();
			}
		});
		
		initOptionsMenu(toolbar.getMenu());
		toolbar.setOnMenuItemClickListener(this);
		
		toolbar.setTitle(getGlobalState().getSketchName());
		
		getGlobalState().setProperties(this);
        
        getWindow().getDecorView().setBackgroundColor(getResources().getColor(R.color.activity_background));
	}
	
	@Override
	public void onPostCreate(Bundle savedInstanceState) {
		super.onPostCreate(savedInstanceState);
		
		setupSimplePreferencesScreen();
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch(requestCode) {
		case REQUEST_CHOOSER:
			if(resultCode == RESULT_OK) {
				final Uri uri = data.getData();
				
				// Get the File path from the Uri
				String path = FileUtils.getPath(this, uri);
				
				if(path != null && FileUtils.isLocal(path)) {
					File file = new File(path);
					if(file.exists()) {
						addFile(file);
					}
				}
			}
			
			break;
		case REQUEST_ICON_FILE:
			if (resultCode == RESULT_OK) {
				final Uri uri = data.getData();
				
				// Get the File path from the Uri
				String path = FileUtils.getPath(this, uri);
				
				if(path != null && FileUtils.isLocal(path)) {
					File file = new File(path);
					if(file.exists() && iconFile != null) {
						iconFile.setText(path);
					}
				}
			}
			
			break;
		}
	}
	
	public void addFile(File source) {
		//Get the location of this sketch's data folder
		File dataFolder = new File(getGlobalState().getSketchLocation(), "/data/");
		dataFolder.mkdir();
		
		File dest = new File(dataFolder, source.getName());
		
		try {
			APDE.copyFile(source, dest);
		} catch (IOException e) {
			//Something bad happened
			System.err.println(getResources().getString(R.string.sketch_properties_add_file_failure));
			e.printStackTrace();
		}
	}
	
	public static void updatePrefs(APDE global) {
		Manifest mf = global.getManifest();
		
		SharedPreferences.Editor edit = global.getSharedPreferences(global.getSketchName(), 0).edit();
		edit.putString("prop_pretty_name", mf.getPrettyName());
		edit.putString("permissions", mf.getCustomPermissions());
		edit.putString("prop_target_sdk", Integer.toString(mf.getTargetSdk(global)));
		edit.putString("prop_orientation", mf.getOrientation(global));
		edit.putString("prop_version_code", Integer.toString(mf.getVersionCode(global)));
		edit.putString("prop_pretty_version", mf.getPrettyVersion(global));
		edit.commit();
	}
	
	@SuppressWarnings("deprecation")
	private void setupSimplePreferencesScreen() {
		if(!isSimplePreferences(this))
			return;
		
		//Switch to the preferences for the current sketch
		getPreferenceManager().setSharedPreferencesName(getGlobalState().getSketchName());
		
		updatePrefs(getGlobalState());
		
		// In the simplified UI, fragments are not used at all and we instead
		// use the older PreferenceActivity APIs.
		
		// Add preferences.
		addPreferencesFromResource(R.xml.sketch_properties);
		
		// Bind the summaries of EditText/List/Dialog/Ringtone preferences to
		// their values. When their values change, their summaries are updated
		// to reflect the new value, per the Android Design guidelines.
		bindPreferenceSummaryToValue(findPreference("prop_pretty_name"));
		bindPreferenceSummaryToValue(findPreference("prop_version_code"));
		bindPreferenceSummaryToValue(findPreference("prop_pretty_version"));
//		bindPreferenceSummaryToValue(findPreference("prop_min_sdk"));
		bindPreferenceSummaryToValue(findPreference("prop_target_sdk"));
		bindPreferenceSummaryToValue(findPreference("prop_orientation"));
		
		//Hacky way of setting up the summaries initially
		String prettyName = ((EditTextPreference) findPreference("prop_pretty_name")).getText(); //We check this to initialize the default value with the name of the sketch
		findPreference("prop_pretty_name").setSummary(prettyName.equals(".") ? getGlobalState().getSketchName() : prettyName); //The "." default is because we can't reference this value from XML
		findPreference("prop_version_code").setSummary(((EditTextPreference) findPreference("prop_version_code")).getText());
		findPreference("prop_pretty_version").setSummary(((EditTextPreference) findPreference("prop_pretty_version")).getText());
//		findPreference("prop_min_sdk").setSummary(((EditTextPreference) findPreference("prop_min_sdk")).getText());
		findPreference("prop_target_sdk").setSummary(((EditTextPreference) findPreference("prop_target_sdk")).getText());
		findPreference("prop_orientation").setSummary(((ListPreference) findPreference("prop_orientation")).getEntry());
		
		//Get rid of the default "." (hopefully no one decides to name their sketch "."...)
		if(prettyName.equals("."))
			((EditTextPreference) findPreference("prop_pretty_name")).setText(getGlobalState().getSketchName());
		
		Preference launchPermissions = (Preference) findPreference("prop_permissions");
		launchPermissions.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) { 
				launchPermissions();
				return true;
			}
		});
		
		Preference launchAddFile = (Preference) findPreference("prop_add_file");
		launchAddFile.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) { 
				launchAddFile();
				return true;
			}
		});
		
		Preference launchSketchFolder = (Preference) findPreference("prop_show_sketch_folder");
		launchSketchFolder.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) { 
				getGlobalState().launchSketchFolder(SketchPropertiesActivity.this);
				return true;
			}
		});
		
		if (getGlobalState().isTemp() || getGlobalState().isSketchbook() &&
				getGlobalState().getSketchbookDrive().type.equals(APDE.StorageDrive.StorageDriveType.INTERNAL)) {
			
			// We can't show the sketch folder of a temp sketch because it's in the internal storage
			// And we can't show sketches when the drive is set to internal
			launchSketchFolder.setEnabled(false);
		}
		
		Preference launchChangeIcon = (Preference) findPreference("prop_change_icon");
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
		
		//This can't be an anonymous class because SharedPreferences keeps listeners in a WeakHashMap...
		//...or a local instance, for that matter
		//StackOverflow: http://stackoverflow.com/questions/2542938/sharedpreferences-onsharedpreferencechangelistener-not-being-called-consistently
		prefListener = new OnSharedPreferenceChangeListener() {
			@Override
			public void onSharedPreferenceChanged(SharedPreferences pref, String key) {
				Manifest mf = getGlobalState().getManifest();
				
				if(key.equals("prop_pretty_name"))
					mf.setPrettyName(pref.getString(key, "."));
				if(key.equals("prop_version_code"))
					mf.setVersionCode(Integer.parseInt(pref.getString(key, getResources().getString(R.string.prop_version_code_default))));
				if(key.equals("prop_pretty_version"))
					mf.setPrettyVersion(pref.getString(key, getResources().getString(R.string.prop_pretty_version_default)));
				if(key.equals("permissions"))
					mf.setCustomPermissions(pref.getString(key, "").split(","));
				if(key.equals("prop_target_sdk"))
					mf.setTargetSdk(Integer.parseInt(pref.getString("prop_target_sdk", getResources().getString(R.string.prop_target_sdk_default))));
				if(key.equals("prop_orientation"))
					mf.setOrientation(pref.getString("prop_orientation", getResources().getString(R.string.prop_orientation_default)));
				
				mf.save();
			}
		};
		
		if (!getGlobalState().isExample() && !getGlobalState().isTemp()) {
			// Set "Show Sketch Folder"'s summary to be the absolute path of the sketch
			findPreference("prop_show_sketch_folder").setSummary(getGlobalState().getSketchLocation().getAbsolutePath());
		}
		
		//Detect changes to the preferences so that we can save them to the manifest file directly
		//TODO This isn't an optimal solution - we still use SharedPreferences
		getSharedPreferences(getGlobalState().getSketchName(), 0).registerOnSharedPreferenceChangeListener(prefListener);
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
	
	private static boolean isXLargeTablet(Context context) {
		return (context.getResources().getConfiguration().screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_XLARGE;
	}
	
	@SuppressWarnings("unused")
	private static boolean isSimplePreferences(Context context) {
		return ALWAYS_SIMPLE_PREFS
				|| Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB
				|| !isXLargeTablet(context);
	}
	
	public APDE getGlobalState() {
		return (APDE) getApplication();
	}
	
	@Override
    public boolean onCreateOptionsMenu(Menu menu) {
        initOptionsMenu(menu);
        
        return true;
    }
	
	@Override
    public boolean onOptionsItemSelected(MenuItem item) {
		switch(item.getItemId()) {
		case android.R.id.home:
			finish();
			return true;
		case R.id.menu_change_sketch_name:
			changeSketchName();
			return true;
		case R.id.action_settings:
			launchSettings();
			return true;
		case R.id.menu_copy_to_sketchbook:
			copyToSketchbook();
			return true;
		case R.id.menu_move_to_sketchbook:
			moveToSketchbook();
			return true;
		case R.id.menu_delete:
			deleteSketch();
			return true;
		case R.id.menu_new:
			newSketch();
			return true;
		case R.id.menu_copy_sketch:
			copySketch();
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
    }
	
	private void initOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.activity_sketch_properties, menu);
		
		switch(getGlobalState().getSketchLocationType()) {
		case SKETCHBOOK:
			menu.findItem(R.id.menu_copy_to_sketchbook).setVisible(false);
			menu.findItem(R.id.menu_move_to_sketchbook).setVisible(false);
			menu.findItem(R.id.menu_change_sketch_name).setVisible(true);
			menu.findItem(R.id.menu_delete).setVisible(true);
			menu.findItem(R.id.menu_copy_sketch).setVisible(true);
			break;
		case TEMPORARY:
			menu.findItem(R.id.menu_copy_to_sketchbook).setVisible(false);
			menu.findItem(R.id.menu_move_to_sketchbook).setVisible(true);
			menu.findItem(R.id.menu_change_sketch_name).setVisible(false);
			menu.findItem(R.id.menu_delete).setVisible(true);
			menu.findItem(R.id.menu_copy_sketch).setVisible(false);
			break;
		case EXTERNAL:
			menu.findItem(R.id.menu_copy_to_sketchbook).setVisible(true);
			menu.findItem(R.id.menu_move_to_sketchbook).setVisible(false);
			menu.findItem(R.id.menu_change_sketch_name).setVisible(true);
			menu.findItem(R.id.menu_delete).setVisible(true);
			menu.findItem(R.id.menu_copy_sketch).setVisible(false);
			break;
		case EXAMPLE:
		case LIBRARY_EXAMPLE:
			menu.findItem(R.id.menu_copy_to_sketchbook).setVisible(true);
			menu.findItem(R.id.menu_move_to_sketchbook).setVisible(false);
			menu.findItem(R.id.menu_change_sketch_name).setVisible(false);
			menu.findItem(R.id.menu_delete).setVisible(false);
			menu.findItem(R.id.menu_copy_sketch).setVisible(false);
			break;
		}
	}
	
	@Override
	public boolean onMenuItemClick(MenuItem item) {
		return onOptionsItemSelected(item);
	}
	
	private void launchSettings() {
		startActivity(new Intent(this, SettingsActivity.class));
	}
	
	private void launchPermissions() {
		Intent intent = new Intent(this, PermissionsActivity.class);
		startActivity(intent);
	}
	
	public void launchAddFile() {
		//Launch file selection intent (includes AFileChooser's custom file chooser implementation)
		
		Intent intent = Intent.createChooser(FileUtils.createGetContentIntent(), getResources().getString(R.string.select_file));
	    startActivityForResult(intent, REQUEST_CHOOSER);
	}
	
	@SuppressLint({ "InlinedApi", "NewApi" })
	public void launchChangeIcon() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(R.string.prop_change_icon);

		changeIconLayout = (ScrollView) View.inflate(new ContextThemeWrapper(this, R.style.Theme_AppCompat_Dialog), R.layout.change_icon, null);
		
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
		
		builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {}
		});
		
		builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				Bitmap bitmap = BitmapFactory.decodeFile(iconFile.getText().toString());
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
		
		iconFileSelect.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				Intent intent = Intent.createChooser(FileUtils.createGetContentIntent(), getResources().getString(R.string.select_file));
			    startActivityForResult(intent, REQUEST_ICON_FILE);
			}
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
	
	public void rebuildIconChange() {
		//Original image
		final ImageView bigIcon = (ImageView) changeIconLayout.findViewById(R.id.big_icon);
		//Image after cropping and scaling
		final ImageView smallIcon = (ImageView) changeIconLayout.findViewById(R.id.small_icon);
		
		//Alt text for the big icon
		final TextView bigIconAltText = (TextView) changeIconLayout.findViewById(R.id.big_icon_alt_text);
		
		//Scale format radio group
		final RadioGroup scaleFormat = (RadioGroup) changeIconLayout.findViewById(R.id.format_scale);
		
		String iconFilePath = iconFile.getText().toString();
		File iconFileFile = new File(iconFilePath);
		
		if (iconFileFile.exists() && iconFileFile.isFile()) {
			Bitmap bitmap = BitmapFactory.decodeFile(iconFilePath);
			
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
				for (int i = 0; i < scaleFormat.getChildCount(); i ++) {
					((RadioButton) scaleFormat.getChildAt(i)).setEnabled(true);
				}
				
				return;
			}
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
	
	protected static final int FORMAT_SCALE_MASK = 0x0000000F;
	
	protected static final int FORMAT_SCALE_CROP = 0x00000001;
	protected static final int FORMAT_SCALE_CENTER = 0x00000002;
	protected static final int FORMAT_SCALE_RESIZE = 0x00000003;
	
	protected Bitmap formatIcon(Bitmap bitmap, int dim, int options) {
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
	
	private void saveSketch() {
		//If we cannot write to the external storage (and the user wants to), make sure to inform the user
		if(!externalStorageWritable() && (getGlobalState().getSketchbookDrive().type.equals(APDE.StorageDrive.StorageDriveType.EXTERNAL)
				|| getGlobalState().getSketchbookDrive().type.equals(APDE.StorageDrive.StorageDriveType.PRIMARY_EXTERNAL))) {
    		AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(getResources().getText(R.string.external_storage_unavailable_dialog_title))
            	.setMessage(getResources().getText(R.string.external_storage_unavailable_dialog_message)).setCancelable(false)
            	.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            		@Override
            		public void onClick(DialogInterface dialog, int which) {}
            }).show();
            
    		return;
    	}
		
		getGlobalState().getEditor().saveSketch();
	}
	
	private void copyToSketchbook() {
		//If we cannot write to the external storage (and the user wants to), make sure to inform the user
		if(!externalStorageWritable() && (getGlobalState().getSketchbookDrive().type.equals(APDE.StorageDrive.StorageDriveType.EXTERNAL)
				|| getGlobalState().getSketchbookDrive().type.equals(APDE.StorageDrive.StorageDriveType.PRIMARY_EXTERNAL))) {
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setTitle(getResources().getText(R.string.external_storage_unavailable_dialog_title))
			.setMessage(getResources().getText(R.string.external_storage_unavailable_dialog_message)).setCancelable(false)
			.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {}
			}).show();
			
			return;
		}
		
		getGlobalState().getEditor().copyToSketchbook();
		
		restartActivity();
	}
	
	public void copySketch() {
		//If we cannot write to the external storage (and the user wants to), make sure to inform the user
		if(!externalStorageWritable() && (getGlobalState().getSketchbookDrive().type.equals(APDE.StorageDrive.StorageDriveType.EXTERNAL)
				|| getGlobalState().getSketchbookDrive().type.equals(APDE.StorageDrive.StorageDriveType.PRIMARY_EXTERNAL))) {
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setTitle(getResources().getText(R.string.external_storage_unavailable_dialog_title))
					.setMessage(getResources().getText(R.string.external_storage_unavailable_dialog_message)).setCancelable(false)
					.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
						}
					}).show();

			return;
		}
		
		//The original sketch location
		File oldLoc = getGlobalState().getSketchLocation();
		
		String before = getGlobalState().getSketchName();
		//Append "_copy" to the sketch name so that they don't overlap
		String after = before + getResources().getString(R.string.copy_suffix);
		//Do the same thing to the path
		String sketchPath = getGlobalState().getSketchPath() + getResources().getString(R.string.copy_suffix);
		
		//Obtain the location of the sketch
		File sketchLoc = getGlobalState().getSketchLocation(sketchPath, APDE.SketchLocation.SKETCHBOOK);
		
		//Ensure that the sketch folder exists
		sketchLoc.mkdirs();
		
		try {
			APDE.copyFile(oldLoc, sketchLoc);
			
			//We need to add it to the recent list
			getGlobalState().putRecentSketch(APDE.SketchLocation.SKETCHBOOK, sketchPath);
			
			getGlobalState().selectSketch(sketchPath, APDE.SketchLocation.SKETCHBOOK);
			
			//Make sure the code area is editable
			((CodeEditText) getGlobalState().getEditor().findViewById(R.id.code)).setFocusable(true);
			((CodeEditText) getGlobalState().getEditor().findViewById(R.id.code)).setFocusableInTouchMode(true);
			
			//Force the drawer to reload
			getGlobalState().getEditor().forceDrawerReload();
			getGlobalState().getEditor().supportInvalidateOptionsMenu();
			getGlobalState().setSketchName(after);
			
			getGlobalState().getEditor().setSaved(true);
		} catch (IOException e) {
			//Inform the user of failure
			getGlobalState().getEditor().error(getResources().getText(R.string.message_sketch_save_failure));
		}
	}
	
	private void moveToSketchbook() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		
		builder.setTitle(R.string.move_temp_to_sketchbook_title);
		builder.setMessage(String.format(Locale.US, getResources().getString(R.string.move_temp_to_sketchbook_message), getGlobalState().getSketchName()));
		
		builder.setPositiveButton(R.string.move_temp_to_sketchbook_button, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				APDE.SketchMeta source = new APDE.SketchMeta(getGlobalState().getSketchLocationType(), getGlobalState().getSketchPath());
				APDE.SketchMeta dest = new APDE.SketchMeta(APDE.SketchLocation.SKETCHBOOK, "/" + source.getName());
				
				// Let's not overwrite anything...
				// TODO Maybe give the user options to replace / keep both in the new location?
				// We don't need that much right now, they can deal with things manually...
				if (getGlobalState().getSketchLocation(dest.getPath(), dest.getLocation()).exists()) {
					AlertDialog.Builder builder = new AlertDialog.Builder(SketchPropertiesActivity.this);
					
					builder.setTitle(R.string.rename_sketch_failure_title);
					builder.setMessage(R.string.rename_move_folder_failure_message);
					
					builder.setPositiveButton(getResources().getString(R.string.ok), new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {}
					});
					
					builder.create().show();
					
					return;
				}
				
				getGlobalState().moveFolder(source, dest, SketchPropertiesActivity.this);
				
				restartActivity();
			}
		});
		
		builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton) {}
		});
		
		builder.create().show();
	}
	
	private boolean externalStorageWritable() {
		String state = Environment.getExternalStorageState();
		if (Environment.MEDIA_MOUNTED.equals(state)) return true;
		else return false;
	}
	
	@SuppressLint("NewApi")
	private void newSketch() {
		//Save the sketch
		getGlobalState().getEditor().autoSave();
		
		getGlobalState().selectNewTempSketch();
		getGlobalState().getEditor().newSketch();
		
		toolbar.setTitle(getGlobalState().getSketchName());
		
		finish();
	}
	
	private void deleteSketch() {
		AlertDialog.Builder alert = new AlertDialog.Builder(this);
    	
    	alert.setTitle(String.format(Locale.US, getResources().getString(R.string.delete_sketch_dialog_title), getGlobalState().getSketchName()));
    	alert.setMessage(String.format(Locale.US, getResources().getString(R.string.delete_sketch_dialog_message), getGlobalState().getSketchName()));
    	
    	alert.setPositiveButton(R.string.delete, new DialogInterface.OnClickListener() {
    		@SuppressLint("NewApi")
			public void onClick(DialogInterface dialog, int whichButton) {
    			getGlobalState().getEditor().deleteSketch();
    			
    			getGlobalState().selectNewTempSketch();
    			getGlobalState().getEditor().newSketch();
				
				toolbar.setTitle(getGlobalState().getSketchName());
				
    			finish();
    		}
    	});
    	
    	alert.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
    		public void onClick(DialogInterface dialog, int whichButton) {}
    	});
		
    	alert.create().show();
	}
	
	private void changeSketchName() {
		String sketchPath = getGlobalState().getSketchPath();
		
		int lastSlash = sketchPath.lastIndexOf('/');
		final String sketchPathPrefix;
		
		if(lastSlash != -1) {
			//Include the slash at the end
			sketchPathPrefix = sketchPath.substring(0, lastSlash + 1);
		} else {
			sketchPathPrefix = "";
		}
		
		AlertDialog.Builder alert = new AlertDialog.Builder(this);
    	
    	alert.setTitle(String.format(Locale.US, getResources().getString(R.string.rename_sketch_title), getGlobalState().getSketchName()));
    	alert.setMessage(R.string.rename_sketch_message);
		
		final EditText input = getGlobalState().createAlertDialogEditText(this, alert, getGlobalState().getSketchName(), true);
    	
    	alert.setPositiveButton(R.string.rename_sketch_button, new DialogInterface.OnClickListener() {
    		public void onClick(DialogInterface dialog, int whichButton) {
    			String before = getGlobalState().getSketchName();
    			String after = input.getText().toString();
    			
    			if(FileNavigatorAdapter.validateSketchName(after, SketchPropertiesActivity.this)) {
    				switch(getGlobalState().getSketchLocationType()) {
    				case SKETCHBOOK:
    					getGlobalState().getSketchLocation(sketchPathPrefix + before, APDE.SketchLocation.SKETCHBOOK).renameTo(getGlobalState().getSketchLocation(sketchPathPrefix + after, APDE.SketchLocation.SKETCHBOOK));
    					getGlobalState().selectSketch(sketchPathPrefix + after, APDE.SketchLocation.SKETCHBOOK);
    					
    					//Make sure we save...
    					saveSketch();
    					
    					//Update the recent list
    					getGlobalState().putRecentSketch(APDE.SketchLocation.SKETCHBOOK, sketchPathPrefix + after);
    					
    					//We have to save before we do this... because it reads from the file system
    					getGlobalState().getEditor().forceDrawerReload();
    					
    					break;
    				case EXTERNAL:
    					getGlobalState().getSketchLocation(sketchPathPrefix + before, APDE.SketchLocation.EXTERNAL).renameTo(getGlobalState().getSketchLocation(sketchPathPrefix + after, APDE.SketchLocation.EXTERNAL));
    					getGlobalState().selectSketch(sketchPathPrefix + after, APDE.SketchLocation.EXTERNAL);
    					
    					//Make sure we save...
    					saveSketch();
    					
    					//Update the recent list
    					getGlobalState().putRecentSketch(APDE.SketchLocation.EXTERNAL, sketchPathPrefix + after);
    					
    					//We have to save before we do this... because it reads from the file system
    					getGlobalState().getEditor().forceDrawerReload();
    					
    					break;
					default:
						//Shouldn't happen... but Lint wants this here
						break;
    				}
    				
    				//If the user has set the pretty name to the name of their sketch, they probably want to change the pretty name too
					@SuppressWarnings("deprecation")
					EditTextPreference pref = ((EditTextPreference) findPreference("prop_pretty_name"));
					if(pref.getText().equals(before)) {
						pref.setText(after);
					}
					
					restartActivity();
    			}
    	}});
    	
    	alert.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
    		public void onClick(DialogInterface dialog, int whichButton) {
    	}});
    	
    	//Show the soft keyboard if the hardware keyboard is unavailable (hopefully)
    	AlertDialog dialog = alert.create();
    	if(!PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getBoolean("use_hardware_keyboard", false))
    		dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
    	dialog.show();
    }
    
	//Restart the activity with no animation
    private void restartActivity() {
        Intent intent = getIntent();
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        
        overridePendingTransition(0, 0);
        finish();
        
        overridePendingTransition(0, 0);
        startActivity(intent);
	}
	
	//Copy all of the old preferences over to the new SharedPreferences and delete the old ones
	//TODO is this currently unused?
	@SuppressWarnings("deprecation")
	public void copyPrefs(String before, String after) {
		SharedPreferences old = getPreferenceManager().getSharedPreferences();
		getPreferenceManager().setSharedPreferencesName(getGlobalState().getSketchName());
		SharedPreferences.Editor ed = getPreferenceManager().getSharedPreferences().edit();

		for(Entry<String,?> entry : old.getAll().entrySet()){ 
			Object v = entry.getValue(); 
			String key = entry.getKey();

			if(v instanceof Boolean)
				ed.putBoolean(key, ((Boolean) v).booleanValue());
			else if(v instanceof Float)
				ed.putFloat(key, ((Float) v).floatValue());
			else if(v instanceof Integer)
				ed.putInt(key, ((Integer) v).intValue());
			else if(v instanceof Long)
				ed.putLong(key, ((Long) v).longValue());
			else if(v instanceof String)
				ed.putString(key, ((String) v));         
		}

		ed.commit();
		old.edit().clear().commit();
	}
}