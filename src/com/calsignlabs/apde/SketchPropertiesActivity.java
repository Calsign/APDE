package com.calsignlabs.apde;

import java.util.Map.Entry;

import com.actionbarsherlock.app.SherlockPreferenceActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.support.v4.app.ActionBarDrawerToggle;
import android.support.v4.widget.DrawerLayout;
import android.view.View;
import android.view.WindowManager;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

public class SketchPropertiesActivity extends SherlockPreferenceActivity {
	private static final boolean ALWAYS_SIMPLE_PREFS = false;
	
	private ActionBarDrawerToggle drawerToggle;
	@SuppressWarnings("unused")
	private boolean drawerOpen;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_sketch_properties);
		
		getSupportActionBar().setTitle(getGlobalState().getSketchName());
		getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		
		getGlobalState().setProperties(this);
		
		final DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_sketch_properties);
        final ListView drawerList = (ListView) findViewById(R.id.drawer_list_sketch_properties);
        ArrayAdapter<String> items = new ArrayAdapter<String>(this, R.layout.drawer_list_item);
        getGlobalState().getEditor().populateWithSketches(items);
        
        drawerList.setAdapter(items);
        drawerList.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        
        drawerList.setOnScrollListener(new OnScrollListener() {
			@Override
			public void onScroll(AbsListView arg0, int arg1, int arg2, int arg3) {}
			
			@Override
			public void onScrollStateChanged(AbsListView listView, int scrollState) {
				if(scrollState == SCROLL_STATE_IDLE) {
					//Select the current sketch TODO this isn't working yet
                    if(getGlobalState().getSelectedSketch() < drawerList.getCount() && getGlobalState().getSelectedSketch() >= 0) {
                    	View view = listView.getChildAt(getGlobalState().getSelectedSketch());
                    	if(view != null)
                    		view.setSelected(true);
                    }
				}
        }});
        
		drawerToggle = new ActionBarDrawerToggle(this, drawer, R.drawable.ic_navigation_drawer, R.string.nav_drawer_open, R.string.nav_drawer_close) {
            @Override
        	public void onDrawerClosed(View view) {
                supportInvalidateOptionsMenu();
            }
            
            @Override
            public void onDrawerSlide(View drawer, float slide) {
            	super.onDrawerSlide(drawer, slide);
            	
            	//Detect an initial open event
            	if(slide > 0) {
            		supportInvalidateOptionsMenu();
                    drawerOpen = true;
                    
                    //Select the current sketch
                    if(getGlobalState().getSelectedSketch() < drawerList.getCount() && getGlobalState().getSelectedSketch() >= 0) {
                    	ListView drawerList = (ListView) findViewById(R.id.drawer_list_sketch_properties);
                    	View view = drawerList.getChildAt(getGlobalState().getSelectedSketch());
                    	if(view != null)
                    		view.setSelected(true);
                    }
            	} else {
            		supportInvalidateOptionsMenu();
                    drawerOpen = false;
            	}
            }
            
            @Override
            public void onDrawerOpened(View drawerView) {
                supportInvalidateOptionsMenu();
        }};
        drawer.setDrawerListener(drawerToggle);
        
        drawerList.setOnItemClickListener(new ListView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				getGlobalState().getEditor().autoSave();
				
				String sketchName = ((TextView) view).getText().toString();
				getGlobalState().getEditor().loadSketch(sketchName);
				view.setSelected(true);
				
				getGlobalState().setSelectedSketch(position);
				
				getSupportActionBar().setTitle(getGlobalState().getSketchName());
				supportInvalidateOptionsMenu();
				
				drawer.closeDrawers();
				
				forceDrawerReload();
				restartActivity();
		}});
        
        getWindow().getDecorView().setBackgroundColor(getResources().getColor(R.color.activity_background));
	}
	
	@Override
	protected void onPostCreate(Bundle savedInstanceState) {
		super.onPostCreate(savedInstanceState);
		
		setupSimplePreferencesScreen();
	}
	
	@Override
    public void onStop() {
		getGlobalState().getEditor().saveSketchForStop();
    	
    	super.onStop();
    }
	
	@SuppressWarnings("deprecation")
	private void setupSimplePreferencesScreen() {
		if(!isSimplePreferences(this))
			return;
		
		//Switch to the preferences for the current sketch
		getPreferenceManager().setSharedPreferencesName(getGlobalState().getSketchName());
		
		// In the simplified UI, fragments are not used at all and we instead
		// use the older PreferenceActivity APIs.
		
		// Add 'general' preferences.
		addPreferencesFromResource(R.xml.sketch_properties);
		
		// Bind the summaries of EditText/List/Dialog/Ringtone preferences to
		// their values. When their values change, their summaries are updated
		// to reflect the new value, per the Android Design guidelines.
		
		bindPreferenceSummaryToValue(findPreference("prop_min_sdk"));
		bindPreferenceSummaryToValue(findPreference("prop_target_sdk"));
		
		//Hacky way of setting up the summaries initially
		findPreference("prop_min_sdk").setSummary(getPreferenceManager().getSharedPreferences().getString("prop_min_sdk", getResources().getString(R.string.prop_min_sdk_default)));
		findPreference("prop_target_sdk").setSummary(getPreferenceManager().getSharedPreferences().getString("prop_target_sdk", getResources().getString(R.string.prop_target_sdk_default)));
		
		Preference launchPermissions = (Preference) findPreference("prop_permissions");
		launchPermissions.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) { 
				launchPermissions();
				return true;
			}
		});
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
        getSupportMenuInflater().inflate(R.menu.activity_sketch_properties, menu);
        
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
            case R.id.menu_save:
            	saveSketch();
            	return true;
            case R.id.menu_load:
            	loadSketch();
        		return true;
        	case R.id.menu_export:
        		exportSketch();
        		return true;
        	case R.id.menu_delete:
        		deleteSketch();
        		return true;
        	case R.id.menu_new:
        		newSketch();
        		return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
	
	private void launchSettings() {
		Intent intent = new Intent(this, SettingsActivity.class);
		startActivity(intent);
	}
	
	private void launchPermissions() {
		Intent intent = new Intent(this, PermissionsActivity.class);
		startActivity(intent);
	}
	
	private void saveSketch() {
		if(!externalStorageWritable()) {
    		AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(getResources().getText(R.string.external_storage_dialog_title))
            	.setMessage(getResources().getText(R.string.external_storage_dialog_message)).setCancelable(false)
            	.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            	
            	@Override
                public void onClick(DialogInterface dialog, int which) {}
            }).show();
            
    		return;
    	}
		
		if(getGlobalState().getSketchName().equals("sketch")) {
    		AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(getResources().getText(R.string.sketch_name_dialog_title))
            	.setMessage(getResources().getText(R.string.sketch_name_dialog_message)).setCancelable(false)
            	.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            	
            	@Override
                public void onClick(DialogInterface dialog, int which) {}
            }).show();
            
            return;
    	}
		
		getGlobalState().getEditor().saveSketch();
		forceDrawerReload();
	}
	
	private boolean externalStorageWritable() {
		String state = Environment.getExternalStorageState();
		if (Environment.MEDIA_MOUNTED.equals(state)) return true;
		else return false;
	}
	
	private void loadSketch() {
		DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_sketch_properties);
		LinearLayout drawerLayout = (LinearLayout) findViewById(R.id.drawer_wrapper_sketch_properties);
		
		drawer.openDrawer(drawerLayout);
	}
	
	private void newSketch() {
		if(getGlobalState().getSketchName().equals("sketch")) {
			AlertDialog.Builder alert = new AlertDialog.Builder(this);
	    	
	    	alert.setTitle(R.string.save_sketch_dialog_title);
	    	alert.setMessage(R.string.save_sketch_dialog_message);
	    	
	    	alert.setPositiveButton(R.string.save_sketch, new DialogInterface.OnClickListener() {
	    		public void onClick(DialogInterface dialog, int whichButton) {
	    			//Save the sketch
	    			getGlobalState().getEditor().autoSave();
	    			
	    			getGlobalState().setSketchName("sketch");
	    			getGlobalState().setSelectedSketch(-1);
	    			getGlobalState().getEditor().newSketch();
	    			forceDrawerReload();
	    			
	    			getSupportActionBar().setTitle(getGlobalState().getSketchName());
	    			
	    			finish();
	    	}});
	    	
	    	//TODO neutral and negative seem mixed up, uncertain of correct implementation - current set up is for looks
	    	alert.setNeutralButton(R.string.dont_save_sketch, new DialogInterface.OnClickListener() {
	    		public void onClick(DialogInterface dialog, int whichButton) {
	    			getGlobalState().setSketchName("sketch");
	    			getGlobalState().setSelectedSketch(-1);
	    			getGlobalState().getEditor().newSketch();
	    			forceDrawerReload();
	    			
	    			getSupportActionBar().setTitle(getGlobalState().getSketchName());
	    			
	    			finish();
	    	}});
	    	
	    	alert.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
	    		public void onClick(DialogInterface dialog, int whichButton) {
	    	}});
	    	
	    	//Show the soft keyboard if the hardware keyboard is unavailable (hopefully)
	    	AlertDialog dialog = alert.create();
	    	dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
	    	dialog.show();
		} else {
			//Save the sketch
			getGlobalState().getEditor().autoSave();
			
			getGlobalState().setSketchName("sketch");
			getGlobalState().setSelectedSketch(-1);
			getGlobalState().getEditor().newSketch();
			forceDrawerReload();
			
			getSupportActionBar().setTitle(getGlobalState().getSketchName());
			
			finish();
		}
	}
	
	private void exportSketch() {
		
	}
	
	private void deleteSketch() {
		AlertDialog.Builder alert = new AlertDialog.Builder(this);
    	
    	alert.setTitle(R.string.delete_sketch_dialog_title);
    	alert.setMessage(R.string.delete_sketch_dialog_message);
    	
    	alert.setPositiveButton(R.string.delete, new DialogInterface.OnClickListener() {
    		public void onClick(DialogInterface dialog, int whichButton) {
    			getGlobalState().getEditor().deleteSketch();
    			
    			getGlobalState().setSketchName("sketch");
    			getGlobalState().setSelectedSketch(-1);
    			getGlobalState().getEditor().newSketch();
    			forceDrawerReload();
    			
    			getSupportActionBar().setTitle(getGlobalState().getSketchName());
    			
    			finish();
    	}});
    	
    	alert.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
    		public void onClick(DialogInterface dialog, int whichButton) {
    	}});
    	
    	//Show the soft keyboard if the hardware keyboard is unavailable (hopefully)
    	AlertDialog dialog = alert.create();
    	dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
    	dialog.show();
	}
	
	private void changeSketchName() {
		AlertDialog.Builder alert = new AlertDialog.Builder(this);
    	
    	alert.setTitle(R.string.change_sketch_name_dialog_title);
    	alert.setMessage(R.string.change_sketch_name_dialog_message);
    	
    	final EditText input = new EditText(this);
    	input.setSingleLine();
    	input.setText(getGlobalState().getSketchName());
    	input.selectAll();
    	alert.setView(input);
    	
    	alert.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
    		public void onClick(DialogInterface dialog, int whichButton) {
    			String before = getGlobalState().getSketchName();
    			String after = input.getText().toString();
    			
    			if(validateSketchName(after)) {
    				getGlobalState().setSketchName(after);
    				getGlobalState().getEditor().getSketchLoc(before).renameTo(getGlobalState().getEditor().getSketchLoc(after));
    				getGlobalState().getEditor().forceDrawerReload();
    				
    				copyPrefs(before, after);
    				
    				forceDrawerReload();
    				restartActivity();
    			}
    	}});
    	
    	alert.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
    		public void onClick(DialogInterface dialog, int whichButton) {
    	}});
    	
    	//Show the soft keyboard if the hardware keyboard is unavailable (hopefully)
    	AlertDialog dialog = alert.create();
    	dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
    	dialog.show();
    }
	
	protected void forceDrawerReload() {
		ListView drawerList = (ListView) findViewById(R.id.drawer_list_sketch_properties);
		ArrayAdapter<String> items = new ArrayAdapter<String>(this, R.layout.drawer_list_item);
        getGlobalState().getEditor().populateWithSketches(items);
        drawerList.setAdapter(items);
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
	
	private boolean validateSketchName(String name) {
		if(name.length() <= 0)
			return false;
		
		if(name.equals("sketch"))
			return false;
		
		return true;
	}
	
	//Copy all of the old preferences over to the new SharedPreferences and delete the old ones
	@SuppressWarnings("deprecation")
	private void copyPrefs(String before, String after) {
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
		old.edit().clear();
	}
}