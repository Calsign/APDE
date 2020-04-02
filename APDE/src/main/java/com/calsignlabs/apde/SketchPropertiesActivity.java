package com.calsignlabs.apde;

import android.annotation.TargetApi;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.view.Menu;
import android.view.MenuItem;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.calsignlabs.apde.support.FileSelection;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map.Entry;

public class SketchPropertiesActivity extends AppCompatActivity implements Toolbar.OnMenuItemClickListener {
	//This is a number, that's all that matters
	protected static final int REQUEST_CHOOSER = 6283;
	//This is another number - this time, it's for something else
	protected static final int REQUEST_ICON_FILE = 9864;
	
	private static final boolean ALWAYS_SIMPLE_PREFS = true;
	
	private SketchPropertiesFragment fragment;
	
	private Toolbar toolbar;
	
	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.activity_sketch_properties);
		
		fragment = (SketchPropertiesFragment) getSupportFragmentManager().findFragmentById(R.id.sketch_properties_fragment);
		
		toolbar = findViewById(R.id.toolbar);
		
		toolbar.setNavigationOnClickListener(v -> finish());
		
		initOptionsMenu(toolbar.getMenu());
		toolbar.setOnMenuItemClickListener(this);
		
		toolbar.setTitle(getGlobalState().getSketchName());
		
		getGlobalState().setPropertiesActivity(this);
        
        getWindow().getDecorView().setBackgroundColor(getResources().getColor(R.color.activity_background));
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch(requestCode) {
		case REQUEST_CHOOSER:
			if(resultCode == RESULT_OK) {
				for (Uri uri : FileSelection.getSelectedUris(data)) {
					addFile(uri);
				}
			}
			
			break;
		case REQUEST_ICON_FILE:
			if (resultCode == RESULT_OK) {
				Uri uri = FileSelection.getSelectedUri(data);
				if (uri != null) {
					fragment.iconFile.setText(uri.toString());
				}
			}
			
			break;
		}
		super.onActivityResult(requestCode, resultCode, data);
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
		case R.id.action_settings:
			launchSettings();
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
    }
	
	private void initOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.activity_sketch_properties, menu);
	}
	
	@Override
	public boolean onMenuItemClick(MenuItem item) {
		return onOptionsItemSelected(item);
	}
	
	private void launchSettings() {
		startActivity(new Intent(this, SettingsActivity.class));
	}
	
	public void addFile(Uri source) {
		//Get the location of this sketch's data folder
		File dataFolder = new File(getGlobalState().getSketchLocation(), "/data/");
		dataFolder.mkdir();
		
		String title = FileSelection.uriToFilename(this, source);
		if (title == null) {
			// TODO perhaps come up with unique names?
			title = "new_file";
		}
		
		File dest = new File(dataFolder, title);
		
		ParcelFileDescriptor fd = FileSelection.openUri(this, source, FileSelection.Mode.READ, true);
		
		if (fd != null) {
			FileSelection.streamToFile(FileSelection.fdIn(fd), dest);
			FileSelection.closeFd(fd);
		}
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
	
	private boolean externalStorageWritable() {
		String state = Environment.getExternalStorageState();
		return Environment.MEDIA_MOUNTED.equals(state);
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
		SharedPreferences old = fragment.getPreferenceManager().getSharedPreferences();
		fragment.getPreferenceManager().setSharedPreferencesName(getGlobalState().getSketchName());
		SharedPreferences.Editor ed = fragment.getPreferenceManager().getSharedPreferences().edit();

		for(Entry<String,?> entry : old.getAll().entrySet()){ 
			Object v = entry.getValue(); 
			String key = entry.getKey();

			if(v instanceof Boolean)
				ed.putBoolean(key, (Boolean) v);
			else if(v instanceof Float)
				ed.putFloat(key, (Float) v);
			else if(v instanceof Integer)
				ed.putInt(key, (Integer) v);
			else if(v instanceof Long)
				ed.putLong(key, (Long) v);
			else if(v instanceof String)
				ed.putString(key, ((String) v));         
		}

		ed.apply();
		old.edit().clear().apply();
	}
}