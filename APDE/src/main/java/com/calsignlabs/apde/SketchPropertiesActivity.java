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
import com.calsignlabs.apde.support.documentfile.DocumentFile;

import com.calsignlabs.apde.support.FileSelection;
import com.calsignlabs.apde.support.MaybeDocumentFile;

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
	
	public static final String DEFAULT_ADDED_FILENAME = "new_file";
	
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
			if (resultCode == RESULT_OK) {
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
		try {
			// Get the location of this sketch's data folder
			MaybeDocumentFile dataFolder = getGlobalState().getSketchLocation().childDirectory("data");
			
			DocumentFile sourceFile = DocumentFile.fromSingleUri(this, source);
			if (sourceFile == null) {
				System.err.println("Could not read provided URI: " + source);
				return;
			}
			
			String name = sourceFile.getName();
			if (name == null) {
				// TODO perhaps come up with unique names?
				System.out.println("Could not determine filename, using default name: " + DEFAULT_ADDED_FILENAME);
				name = DEFAULT_ADDED_FILENAME;
			}
			
			String mimeType = sourceFile.getType();
			if (mimeType == null) {
				// pick something; this is just a binary blob
				mimeType = "application/octet-stream";
			}
			
			MaybeDocumentFile dest = dataFolder.child(name, mimeType);
			APDE.copyDocumentFile(sourceFile, dest, getContentResolver());
		} catch (MaybeDocumentFile.MaybeDocumentFileException | IOException e) {
			e.printStackTrace();
		}
	}
	
	public void addNewFile() {
		try {
			// Get the location of this sketch's data folder
			MaybeDocumentFile dataFolder = getGlobalState().getSketchLocation().childDirectory("data");
			
			String name = DEFAULT_ADDED_FILENAME;
			
			String mimeType = "application/octet-stream";
			
			MaybeDocumentFile dest = dataFolder.child(name, mimeType);
                        dataFolder.resolve();
                        dest.resolve();
		} catch (MaybeDocumentFile.MaybeDocumentFileException e) {
			e.printStackTrace();
		}
	}
}
