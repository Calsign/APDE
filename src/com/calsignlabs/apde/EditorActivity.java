package com.calsignlabs.apde;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.ActionBarDrawerToggle;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.widget.DrawerLayout;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.WindowManager;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.AbsListView.OnScrollListener;

import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.ActionBar.Tab;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;

import com.calsignlabs.apde.build.Build;

public class EditorActivity extends SherlockActivity implements ActionBar.TabListener {
	private HashMap<Tab, FileMeta> tabs;
	private boolean saved;
	
	private ActionBarDrawerToggle drawerToggle;
	private boolean drawerOpen;
	
	private final static int RENAME_TAB = 0;
	private final static int NEW_TAB = 1;
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_editor);
        
        ((TextView) findViewById(R.id.code)).setHorizontallyScrolling(true);
        //((TextView) findViewById(R.id.code)).setHorizontalScrollBarEnabled(true);
        
        tabs = new HashMap<Tab, FileMeta>();
        setSaved(false);
        
        getGlobalState().setEditor(this);
        getGlobalState().setSketchName("sketch");
        getGlobalState().setSelectedSketch(-1);
        
        getSupportActionBar().setTitle(getGlobalState().getSketchName());
        getSupportActionBar().setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
        
        if(!loadSketchStart())
        	addDefaultTab(getGlobalState().getSketchName());
        
        ((EditText) findViewById(R.id.code)).addTextChangedListener(new TextWatcher(){
            public void afterTextChanged(Editable s) {
                if(isSaved()) setSaved(false);
            }
            public void beforeTextChanged(CharSequence s, int start, int count, int after){}
            public void onTextChanged(CharSequence s, int start, int before, int count){}
        });
        
        final DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer);
        final ListView drawerList = (ListView) findViewById(R.id.drawer_list);
        ArrayAdapter<String> items = new ArrayAdapter<String>(this, R.layout.drawer_list_item);
        populateWithSketches(items);
        
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
            	((EditText) findViewById(R.id.code)).setEnabled(true);
                supportInvalidateOptionsMenu();
            }
            
            @Override
            public void onDrawerSlide(View drawer, float slide) {
            	super.onDrawerSlide(drawer, slide);
            	
            	//Detect an initial open event
            	if(slide > 0) {
            		((EditText) findViewById(R.id.code)).setEnabled(false);
                    supportInvalidateOptionsMenu();
                    drawerOpen = true;
                    
                    //Select the current sketch
                    if(getGlobalState().getSelectedSketch() < drawerList.getCount() && getGlobalState().getSelectedSketch() >= 0) {
                    	ListView drawerList = (ListView) findViewById(R.id.drawer_list);
                    	View view = drawerList.getChildAt(getGlobalState().getSelectedSketch());
                    	if(view != null)
                    		view.setSelected(true);
                    }
            	} else {
            		((EditText) findViewById(R.id.code)).setEnabled(true);
                    supportInvalidateOptionsMenu();
                    drawerOpen = false;
            	}
            }
            
            @Override
            public void onDrawerOpened(View drawerView) {
            	((EditText) findViewById(R.id.code)).setEnabled(false);
                supportInvalidateOptionsMenu();
        }};
        drawer.setDrawerListener(drawerToggle);
        
        drawerList.setOnItemClickListener(new ListView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				autoSave();
				
				String sketchName = ((TextView) view).getText().toString();
				loadSketch(sketchName);
				view.setSelected(true);
				
				getGlobalState().setSelectedSketch(position);
				
				drawer.closeDrawers();
		}});
        
        getSupportActionBar().setHomeButtonEnabled(true);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }
    
    protected void populateWithSketches(ArrayAdapter<String> items) {
    	//The public directory
    	File publicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
    	//The location of the sketchbook
    	File sketchbookLoc = new File(publicDir, "Sketchbook");
    	
    	if(sketchbookLoc.exists()) {
	    	File[] folders = sketchbookLoc.listFiles();
	    	for(File folder : folders)
	    		if(folder.isDirectory())
	    			items.add(folder.getName());
    	}
	}
    
	@Override
    public void onStop() {
		saveSketchForStop();
		
    	super.onStop();
    }
	
	public void saveSketchForStop() {
		autoSave();
    	
		//Store sketch info in private storage TODO make this SharedPreferences instead
		String sketchName = getGlobalState().getSketchName();
    	writeTempFile("sketchName.txt", sketchName);
    	
    	String selectedSketch = Integer.toString(getGlobalState().getSelectedSketch());
		writeTempFile("sketchNum.txt", selectedSketch);
	}
	
	public boolean loadSketchStart() {
		String sketchName = readTempFile("sketchName.txt");
		int selectedSketch = Integer.parseInt(readTempFile("sketchNum.txt"));
		
		if(getSketchLoc(sketchName).exists()) {
			getGlobalState().setSelectedSketch(selectedSketch);
			return loadSketch(sketchName);
		} else {
			return loadSketchTemp();
		}
	}
	
	public void autoSave() {
		if(getSketchLoc(getSupportActionBar().getTitle().toString()).exists())
			saveSketch();
		else
			saveSketchTemp();
	}
	
	public void newSketch() {
		getSupportActionBar().setTitle(getGlobalState().getSketchName());
		supportInvalidateOptionsMenu();
		forceDrawerReload();
		
		getSupportActionBar().removeAllTabs();
		tabs.clear();
		
		addDefaultTab("sketch");
		
		EditText code = ((EditText) findViewById(R.id.code));
		code.setText("");
	}
    
    public boolean loadSketch(String sketchName) {
    	//Get the sketch in the sketch folder
    	File sketchLoc = getSketchLoc(sketchName);
    	boolean success;
    	
    	//Ensure that the sketch folder exists and is a directory
    	if(sketchLoc.exists() && sketchLoc.isDirectory()) {
    		getGlobalState().setSketchName(sketchName);
    		
    		//Get all the files in the directory
    		File[] files = sketchLoc.listFiles();
    		
    		for(FileMeta meta : tabs.values())
    			meta.disable();
    		
    		//Get rid of any tabs
    		tabs.clear();
    		getSupportActionBar().removeAllTabs();
    		//This method is necessary, too
    		removeAllTabs();
    		
    		//Cycle through the files
    		for(File file : files) {
    			//Split the filename into prefix and suffix
    			String[] folders = file.getPath().split("/");
    			String[] parts = folders[folders.length - 1].split("\\.");
    			//If the filename isn't formatted properly, skip this file
    			if(parts.length != 2)
    				continue;
    			
    			//Retrieve the prefix and suffix
    			String prefix = parts[parts.length - 2];
    			String suffix = parts[parts.length - 1];
    			
    			//Check to see if it's a .PDE file
    			if(suffix.equals("pde")) {
    				//Build a Tab Meta object
    				FileMeta meta = new FileMeta("", "", 0, 0);
    				meta.readData(this, file.getAbsolutePath());
    				meta.setTitle(prefix);
    				
    				//Add the tab
    				addTab(prefix, meta);
    			}
    		}
    		
    		//Update the code-view
    		if(getSupportActionBar().getTabCount() > 0)
    			((EditText) findViewById(R.id.code)).setText(tabs.get(getSupportActionBar().getSelectedTab()).getText());
    		else
    			((EditText) findViewById(R.id.code)).setText("");
    		
    		success = true;
    	} else {
    		success = false;
    	}
    	
    	return success;
    }
    
    public void saveSketch() {
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
    	
    	boolean success = true;
    	File sketchLoc = getSketchLoc(getGlobalState().getSketchName());
    	
    	sketchLoc.mkdirs();
    	
	    if(getSupportActionBar().getTabCount() > 0) {
	    	//Update the current tab
	    	EditText code = (EditText) findViewById(R.id.code);
	    	tabs.put(getSupportActionBar().getSelectedTab(), new FileMeta(getSupportActionBar().getSelectedTab().getText().toString(), code.getText().toString(), code.getSelectionStart(), code.getSelectionEnd()));
	    	
	    	//Iterate through FileMeta
	    	for(FileMeta meta : tabs.values()) {
	    		if(meta.enabled())
	    			if(!meta.writeData(getApplicationContext(), sketchLoc.getPath() + "/"))
	    				success = false;
	    	}
	    	
	    	//Notify the user whether or not the sketch has been saved properly
	    	if(success) {
	    		//Force the drawer to reload
	    		ListView drawerList = (ListView) findViewById(R.id.drawer_list);
	            ArrayAdapter<String> items = new ArrayAdapter<String>(this, R.layout.drawer_list_item);
	            populateWithSketches(items);
	            drawerList.setAdapter(items);
	            
	            //Select the new sketch
	            if(getGlobalState().getSelectedSketch() < 0)
	            	getGlobalState().setSelectedSketch(drawerList.getCount() - 1);
	            
	    		message(getResources().getText(R.string.sketch_saved));
	    		setSaved(true);
	    	} else
	    		error(getResources().getText(R.string.sketch_save_failure));
    	} else {
    		//Force the drawer to reload
    		ListView drawerList = (ListView) findViewById(R.id.drawer_list);
            ArrayAdapter<String> items = new ArrayAdapter<String>(this, R.layout.drawer_list_item);
            populateWithSketches(items);
            drawerList.setAdapter(items);
    		
            getGlobalState().setSelectedSketch(0);
            
    		message(getResources().getText(R.string.sketch_saved));
    		setSaved(true);
    	}
    }
    
    public void deleteSketch() {
    	File sketchFolder = getSketchLoc(getGlobalState().getSketchName());
    	if(sketchFolder.isDirectory()) {
    		try {
				deleteFile(sketchFolder);
			} catch (IOException e) {
				//catch stuff, shouldn't be any problems, though
				e.printStackTrace();
			}
    	}
    }
    
    //Recursive file deletion
    void deleteFile(File f) throws IOException {
    	if(f.isDirectory())
    		for(File c : f.listFiles())
    			deleteFile(c);
    	
    	if(!f.delete())
    		throw new FileNotFoundException("Failed to delete file: " + f);
    }
    
    //Save the sketch into temp storage
    public void saveSketchTemp() {
    	//Erase previously stored files
    	File[] files = getFilesDir().listFiles();
    	for(File file : files)
    		file.delete();
    	
    	String sketchName = getGlobalState().getSketchName();
    	writeTempFile("sketchName.txt", sketchName);
    	String selectedSketch = Integer.toString(getGlobalState().getSelectedSketch());
    	writeTempFile("sketchNum.txt", selectedSketch);
    	
    	//Preserve tab order upon re-launch
    	String tabList = "";
    	for(int i = 0; i < getSupportActionBar().getTabCount(); i ++) {
    		String tabName = getSupportActionBar().getTabAt(i).getText().toString();
    		//If it's a .PDE file, make sure to add the suffix
    		if(tabName.split(".").length <= 1)
    			tabName += ".pde";
    		tabList += tabName + "\n";
    	}
    	writeTempFile("sketchFileNames", tabList);
    	
    	if(getSupportActionBar().getTabCount() > 0) {
    		//Update the current tab
    		EditText code = (EditText) findViewById(R.id.code);
    		Tab tab = getSupportActionBar().getSelectedTab();
    		tabs.put(tab, new FileMeta(tab.getText().toString(), code.getText().toString(), code.getSelectionStart(), code.getSelectionEnd()));
    	}
    	
    	//Iterate through FileMeta
    	for(FileMeta meta : tabs.values())
    		meta.writeDataTemp(getApplicationContext());
    	
    	setSaved(true);
    }
    
    public boolean loadSketchTemp() {
    	boolean success;
    	
    	try {
    		String sketchName = readTempFile("sketchName.txt");
    		getGlobalState().setSketchName(sketchName);
    		int selectedSketch = Integer.parseInt(readTempFile("sketchNum.txt"));
    		getGlobalState().setSelectedSketch(selectedSketch);
    		
    		String[] files = readTempFile("sketchFileNames").split("\n");
    		
    		for(String filename : files) {
    			File file = new File(filename);
    			
    			//Split the filename into prefix and suffix
    			String[] folders = file.getPath().split("/");
    			String[] parts = folders[folders.length - 1].split("\\.");
    			//If the filename isn't formatted properly, skip this file
    			if(parts.length != 2)
    				continue;
    			
    			//Retrieve the prefix and suffix
    			String prefix = parts[parts.length - 2];
    			String suffix = parts[parts.length - 1];
    			
    			//Check to see if it's a .PDE file
    			if(suffix.equals("pde")) {
    				//Build a Tab Meta object
    				FileMeta meta = new FileMeta("", "", 0, 0);
    				meta.readTempData(this, file.getName());
    				meta.setTitle(prefix);
    				
    				//Add the tab
    				addTab(prefix, meta);
    			}
    		}
    		
    		if(getSupportActionBar().getTabCount() > 0)
    			((EditText) findViewById(R.id.code)).setText(tabs.get(getSupportActionBar().getSelectedTab()).getText());
    		
    		success = true;
    	} catch(Exception e) {
    		e.printStackTrace();
    		
    		success = false;
    	}
    	
    	return success;
    	
    }
    
    //Write text to a temp file
    public boolean writeTempFile(String filename, String text) {
    	BufferedOutputStream outputStream = null;
		boolean success;
		
		try {
			outputStream = new BufferedOutputStream(openFileOutput(filename, Context.MODE_PRIVATE));
			outputStream.write(text.getBytes());
			
			success = true;
		} catch(Exception e) {
			e.printStackTrace();
			
			success = false;
			
		} finally {
			if(outputStream != null) {
				try {
					outputStream.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		
		return success;
    }
    
    public String readTempFile(String filename) {
    	BufferedInputStream inputStream = null;
    	String output = "";
    	
		try {
			inputStream = new BufferedInputStream(openFileInput(filename));
			
			byte[] contents = new byte[1024];
			int bytesRead = 0;
			
			while((bytesRead = inputStream.read(contents)) != -1)
				output += new String(contents, 0, bytesRead);
		} catch(Exception e) {
			e.printStackTrace();
		} finally {
			try {
				if(inputStream != null)
					inputStream.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
    	
    	return output;
    }
    
    public File getSketchLoc(String sketchName) {
    	//The public directory
    	File publicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
    	//The location of the sketchbook
    	File sketchbookLoc = new File(publicDir, "Sketchbook");
    	//The location of the sketch
    	return new File(sketchbookLoc, sketchName);
    }
    
    public void forceDrawerReload() {
    	ListView drawerList = (ListView) findViewById(R.id.drawer_list);
        ArrayAdapter<String> items = new ArrayAdapter<String>(this, R.layout.drawer_list_item);
        populateWithSketches(items);
        drawerList.setAdapter(items);
    }
    
    private APDE getGlobalState() {
    	return (APDE) getApplication();
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getSupportMenuInflater().inflate(R.menu.activity_editor, menu);
        
        if(drawerOpen) {
        	menu.findItem(R.id.menu_run).setVisible(false);
        	menu.findItem(R.id.menu_stop).setVisible(false);
        	menu.findItem(R.id.menu_tab_delete).setVisible(false);
        	menu.findItem(R.id.menu_tab_rename).setVisible(false);
        	menu.findItem(R.id.menu_save).setVisible(false);
        	menu.findItem(R.id.menu_tab_new).setVisible(false);
        	menu.findItem(R.id.menu_sketch_properties).setVisible(false);
        } else {
        	if(getSupportActionBar().getTabCount() > 0) {
            	menu.findItem(R.id.menu_run).setVisible(true);
            	menu.findItem(R.id.menu_stop).setVisible(true);
            	menu.findItem(R.id.menu_tab_delete).setVisible(true);
            	menu.findItem(R.id.menu_tab_rename).setVisible(true);
            } else {
            	menu.findItem(R.id.menu_run).setVisible(false);
    	    	menu.findItem(R.id.menu_stop).setVisible(false);
    	    	menu.findItem(R.id.menu_tab_delete).setVisible(false);
            	menu.findItem(R.id.menu_tab_rename).setVisible(false);
            }
        	
        	menu.findItem(R.id.menu_save).setVisible(true);
        	menu.findItem(R.id.menu_tab_new).setVisible(true);
        	menu.findItem(R.id.menu_sketch_properties).setVisible(true);
        }
        
        return true;
    }
    
    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        drawerToggle.syncState();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        drawerToggle.onConfigurationChanged(newConfig);
    }

    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
        	case android.R.id.home:
        		DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer);
        		LinearLayout drawerLayout = (LinearLayout) findViewById(R.id.drawer_wrapper);
        		
        		if(drawer.isDrawerOpen(drawerLayout)) {
                    drawer.closeDrawer(drawerLayout);
        		} else {
                    drawer.openDrawer(drawerLayout);
                    
                    ((EditText) findViewById(R.id.code)).setEnabled(false);
                    supportInvalidateOptionsMenu();
                }
        		return true;
            case R.id.menu_run:
            	runApplication();
            	return true;
            case R.id.menu_stop:
            	stopApplication();
            	return true;
            case R.id.menu_save:
            	saveSketch();
            	return true;
            case R.id.menu_settings:
            	openSettings();
            	return true;
            case R.id.menu_tab_new:
            	addTabWithDialog();
            	return true;
            case R.id.menu_tab_rename:
            	renameTab();
            	return true;
            case R.id.menu_tab_delete:
            	deleteTab();
            	return true;
            case R.id.menu_sketch_properties:
            	launchSketchProperties();
            	return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
    
    private void openSettings() {
    	
    }
    
    private void runApplication() {
    	//TODO work here going on (runApplication())
    	Build builder = new Build(getGlobalState());
    	builder.build("debug");
    }
    
    private void stopApplication() {
    	
    }
    
    public void message(String msg) {
    	((TextView) findViewById(R.id.message)).setText(msg);
    	((TextView) findViewById(R.id.message)).setBackgroundColor(getResources().getColor(R.color.message_back));
    	((TextView) findViewById(R.id.message)).setTextColor(getResources().getColor(R.color.message_text));
    }
    
    public void message(CharSequence msg) {
    	((TextView) findViewById(R.id.message)).setText(msg);
    	((TextView) findViewById(R.id.message)).setBackgroundColor(getResources().getColor(R.color.message_back));
    	((TextView) findViewById(R.id.message)).setTextColor(getResources().getColor(R.color.message_text));
    }
    
    public void error(String msg) {
    	((TextView) findViewById(R.id.message)).setText(msg);
    	((TextView) findViewById(R.id.message)).setBackgroundColor(getResources().getColor(R.color.error_back));
    	((TextView) findViewById(R.id.message)).setTextColor(getResources().getColor(R.color.error_text));
    }
    
    public void error(CharSequence msg) {
    	((TextView) findViewById(R.id.message)).setText(msg);
    	((TextView) findViewById(R.id.message)).setBackgroundColor(getResources().getColor(R.color.error_back));
    	((TextView) findViewById(R.id.message)).setTextColor(getResources().getColor(R.color.error_text));
    }
    
    private void addTabWithDialog() {
    	createInputDialog(getResources().getString(R.string.tab_new_dialog_title), getResources().getString(R.string.tab_new_dialog_message), "", NEW_TAB);
    }
    
    private void addDefaultTab(String title) {
    	ActionBar.Tab tab = getSupportActionBar().newTab();
    	tab.setText(title);
    	tab.setTabListener(this);
    	getSupportActionBar().addTab(tab);
    	
    	tabs.put(tab, new FileMeta(title, "", 0, 0));
    }
    
	private void addTab(String title) {
    	ActionBar.Tab tab = getSupportActionBar().newTab();
    	tab.setText(title);
    	tab.setTabListener(this);
    	getSupportActionBar().addTab(tab, getSupportActionBar().getTabCount());
    	
    	tabs.put(tab, new FileMeta(title, "", 0, 0));
    	
    	getSupportActionBar().selectTab(tab);
    }
	
	private void addTab(String title, FileMeta meta) {
    	ActionBar.Tab tab = getSupportActionBar().newTab();
    	tab.setText(title);
    	tab.setTabListener(this);
    	getSupportActionBar().addTab(tab, getSupportActionBar().getTabCount());
    	
    	tabs.put(tab, meta);
    }
    
    private void renameTab() {
    	if(tabs.size() > 0)
    		createInputDialog(getResources().getString(R.string.tab_rename_dialog_title), getResources().getString(R.string.tab_rename_dialog_message), getSupportActionBar().getSelectedTab().getText().toString(), RENAME_TAB);
    }
    
    private void deleteTab() {
    	AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.delete_dialog_title)
        	.setMessage(R.string.delete_dialog_message)
        	.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
        	}})
        	.setPositiveButton(R.string.delete, new DialogInterface.OnClickListener() {
        		@Override
        		public void onClick(DialogInterface dialog, int which) {
        			deleteTabContinue();
        	}})
        	.show();
    }
    
    //Deletes a file in the sketch folder
    private boolean deleteLocalFile(String filename) {
    	//The public directory
    	File publicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
    	//The location of the sketchbook
    	File sketchbookLoc = new File(publicDir, "Sketchbook");
    	//The location of the sketch
    	File sketchLoc = new File(sketchbookLoc, getGlobalState().getSketchName());
    	
    	File file = new File(sketchLoc + "/", filename);
    	if(file.exists()) {
    		file.delete();
    		return true;
    	}
    	
    	return false;
    }
    
    //Called from delete tab dialog
    private void deleteTabContinue() {
    	if(tabs.size() > 0) {
    		//Get the tab
    		Tab cur = getSupportActionBar().getSelectedTab();
    		//Delete the tab from the sketch folder
    		deleteLocalFile(tabs.get(cur).getFilename());
    		//Disable the tab
    		tabs.get(cur).disable();
    		//Remove the tab
	    	getSupportActionBar().removeTab(cur);
	    	tabs.remove(cur);
	    	
	    	//If there are no more tabs
	    	if(getSupportActionBar().getTabCount() <= 0) {
	    		//Clear the code text area
		    	EditText code = ((EditText) findViewById(R.id.code));
		    	code.setText("");
		    	code.setSelection(0);
	    		
	    		//Disable the code text area if there is no selected tab
	    		code.setEnabled(false);
	    		
	    		//Force remove all tabs
	    		getSupportActionBar().removeAllTabs();
	    		tabs.clear();
	    		
	    		//Force ActionBar refresh
	    		supportInvalidateOptionsMenu();
	    	}
	    	
	    	//Inform the user in the message area
	    	message(getResources().getText(R.string.tab_deleted));
    	}
    }
    
    //Force remove all tabs... nothing else works
    public void removeAllTabs() {
    	Iterator<Tab> it = tabs.keySet().iterator();
    	while(it.hasNext()) {
    		@SuppressWarnings("unused")
			Tab tab = it.next();
    		it.remove();
    	}
    }
    
    private void createInputDialog(String title, String message, String currentName, final int key) {
    	AlertDialog.Builder alert = new AlertDialog.Builder(this);
    	
    	alert.setTitle(title);
    	alert.setMessage(message);
    	
    	final EditText input = new EditText(this);
    	input.setSingleLine();
    	input.setText(currentName);
    	input.selectAll();
    	alert.setView(input);
    	
    	alert.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
    		public void onClick(DialogInterface dialog, int whichButton) {
    			String value = input.getText().toString();
    			checkInputDialog(key, true, value);
    		}
    	});
    	
    	alert.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
    		public void onClick(DialogInterface dialog, int whichButton) {
    			checkInputDialog(key, false, "");
    		}
    	});
    	
    	//Show the soft keyboard if the hardware keyboard is unavailable (hopefully)
    	AlertDialog dialog = alert.create();
    	dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
    	dialog.show();
    }
    
    //Called when the add or rename tab dialog is closed
    private void checkInputDialog(int key, boolean completed, String value) {
    	if(completed) {
    		if(!validateFileName(value))
    			return;
    		
    		switch(key) {
    		case RENAME_TAB:
    	    	//Get the tab
    	    	Tab cur = getSupportActionBar().getSelectedTab();
    	    	//Delete the tab from the sketch folder
    	    	deleteLocalFile(tabs.get(cur).getFilename());
    			
    			getSupportActionBar().getSelectedTab().setText(value);
    			message(getResources().getText(R.string.tab_renamed));
    			break;
    		case NEW_TAB:
    			addTab(value);
    			message(getResources().getText(R.string.tab_created));
    			break;
    		}
    	}
    }
    
	@Override
	public void onTabSelected(Tab tab, FragmentTransaction ft) {
		EditText code = ((EditText) findViewById(R.id.code));
		
		FileMeta meta = tabs.get(getSupportActionBar().getSelectedTab());
		
		if(getSupportActionBar().getTabCount() > 0 && meta != null) {
			code.setText(meta.getText());
			code.setSelection(meta.getSelectionStart(), meta.getSelectionEnd());
		} else {
			//Re-enable the code text area
	    	code.setEnabled(true);
	    	
	    	//Force ActionBar refresh
    		supportInvalidateOptionsMenu();
		}
	}
	
	@Override
	public void onTabUnselected(Tab tab, FragmentTransaction ft) {
		EditText code = ((EditText) findViewById(R.id.code));
		tabs.put(tab, new FileMeta(tab.getText().toString(), code.getText().toString(), code.getSelectionStart(), code.getSelectionEnd()));
	}
	
	@Override
	public void onTabReselected(Tab tab, FragmentTransaction ft) {}
	
	private boolean externalStorageWritable() {
		String state = Environment.getExternalStorageState();
		if (Environment.MEDIA_MOUNTED.equals(state)) return true;
		else return false;
	}
	
	//Validate file names
	private boolean validateFileName(String title) {
		if(title.length() <= 0) {
			error(getResources().getText(R.string.name_invalid_no_char));
			return false;
		}
		
		//Check the first character
		char first = title.charAt(0);
		if((first >= '0' && first <= '9') || first == '_') {
			error(getResources().getText(R.string.name_invalid_first_char));
			return false;
		}
		
		//Check all of the characters
		for(int i = 0; i < title.length(); i ++) {
			char c = title.charAt(i);
			if(c >= '0' && c <= '9') continue;
			if(c >= 'a' && c <= 'z') continue;
			if(c >= 'A' && c <= 'Z') continue;
			if(c == '_') continue;
			
			error(getResources().getText(R.string.name_invalid_char));
			return false;
		}
		
		for(FileMeta meta : tabs.values()) {
			if(meta.getTitle().equals(title)) {
				error(getResources().getText(R.string.name_invalid_same_title));
				return false;
			}
		}
		
		return true;
	}
	
	private void launchSketchProperties() {
		Intent intent = new Intent(this, SketchPropertiesActivity.class);
		startActivity(intent);
	}

	public boolean isSaved() {
		return saved;
	}

	public void setSaved(boolean saved) {
		this.saved = saved;
	}
	
	public HashMap<Tab, FileMeta> getTabs() {
		return tabs;
	}
}