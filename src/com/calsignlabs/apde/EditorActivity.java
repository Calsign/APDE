package com.calsignlabs.apde;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceArray;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import processing.data.XML;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.v4.app.ActionBarDrawerToggle;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBar.Tab;
import android.support.v7.app.ActionBarActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.View;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.AbsListView.OnScrollListener;

import com.calsignlabs.apde.build.Build;
import com.calsignlabs.apde.build.Manifest;
import com.calsignlabs.apde.support.PopupMenu;

public class EditorActivity extends ActionBarActivity implements ActionBar.TabListener {
	private HashMap<String, KeyBinding> keyBindings;
	
	private HashMap<Tab, FileMeta> tabs;
	private boolean saved;
	
	private ActionBarDrawerToggle drawerToggle;
	private boolean drawerOpen;
	
	private boolean keyboardVisible;
	private boolean firstResize = true; //this is a makeshift arrangement
	private int oldCodeHeight = -1;
	
	private int message;
	
	private final static int RENAME_TAB = 0;
	private final static int NEW_TAB = 1;
	
	private MessageTouchListener messageListener;
	
	private PrintStream outStream;
	private PrintStream errStream;
	
	private boolean building;
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_editor);
        
        Manifest.loadPermissions(this);
        
        messageListener = new MessageTouchListener();
        
        findViewById(R.id.message).setOnLongClickListener(messageListener);
        findViewById(R.id.message).setOnTouchListener(messageListener);
        
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
        
        ((EditText) findViewById(R.id.code)).setOnTouchListener(new EditText.OnTouchListener() {
        	@Override
        	public boolean onTouch(View v, MotionEvent event) {
        		//Disable the soft keyboard
        		if(PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getBoolean("use_hardware_keyboard", false))
        			getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        		
        		return false;

        	}
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
//                    	View view = listView.getChildAt(getGlobalState().getSelectedSketch());
//                    	if(view != null)
//                    		view.setSelected(true);
                    	drawerList.setItemChecked(getGlobalState().getSelectedSketch(), true);
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
				//view.setSelected(true);
				drawerList.setItemChecked(position, true);
				
				getGlobalState().setSelectedSketch(position);
				
				drawer.closeDrawers();
		}});
        
        getSupportActionBar().setHomeButtonEnabled(true);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        
        //Detect software keyboard open / close events
        //StackOverflow: http://stackoverflow.com/questions/2150078/how-to-check-visibility-of-software-keyboard-in-android
        final View activityRootView = findViewById(R.id.content);
        activityRootView.getViewTreeObserver().addOnGlobalLayoutListener(new OnGlobalLayoutListener() {
        	@SuppressWarnings("deprecation")
			@Override
        	public void onGlobalLayout() {
        		Rect r = new Rect();
        		activityRootView.getWindowVisibleDisplayFrame(r);
        		int heightDiff = activityRootView.getRootView().getHeight() - (r.bottom - r.top);
        		
        		//This is hacky...
        		if(PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getBoolean("use_hardware_keyboard", false)) {
        			InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        			imm.hideSoftInputFromWindow(activityRootView.getWindowToken(), 0);
        			return;
        		}
        		
        		if(oldCodeHeight == -1)
        			oldCodeHeight = findViewById(R.id.code_scroller).getHeight();
        		
        		if(heightDiff > 100) { //If the difference is bigger than 100, it's probably the keyboard
        			if(!keyboardVisible) {
	        			keyboardVisible = true;
	        			
	        			if(message == 0)
							message = findViewById(R.id.message).getHeight();
	        			
	        			//Configure layout for keyboard
	        			
	        			View messageArea = findViewById(R.id.message);
        				View console = findViewById(R.id.console_scroller);
        				View code = findViewById(R.id.code_scroller);
	        			View content = findViewById(R.id.content);
	        			
	        			if(firstResize)
	        				firstResize = false;
	        			else
	        				oldCodeHeight = code.getHeight();
	        			
	        			//Start the custom animation TODO make the keyboard appearance prettier
	        			messageArea.startAnimation(new MessageAreaAnimation(code, console, messageArea, oldCodeHeight, content.getHeight() - message, content.getHeight()));
	        			
	        			//Remove the focus from the Message slider if it has it
	        			messageArea.setBackgroundDrawable(getResources().getDrawable(R.drawable.back)); //TODO this is deprecated...
        			}
        		} else {
        			if(keyboardVisible) {
        				View messageArea = findViewById(R.id.message);
        				View codeArea = findViewById(R.id.code_scroller);
        				View consoleArea = findViewById(R.id.console_scroller);
        				
        				//Start the custom animation TODO make the keyboard appearance prettier
        				messageArea.startAnimation(new MessageAreaAnimation(codeArea, consoleArea, messageArea, codeArea.getLayoutParams().height, oldCodeHeight, findViewById(R.id.content).getHeight()));
        				
	        			keyboardVisible = false;
	        			
	        			//Remove any unnecessary focus from the code area
	        			findViewById(R.id.code).clearFocus();
        			}
        		}
        	}
        });
        
        //Detect orientation changes
        (new OrientationEventListener(this) { //TODO make rotation changes prettier
        	@SuppressLint("NewApi")
        	@SuppressWarnings("deprecation")
        	public void onOrientationChanged(int orientation) {
        		//If we don't know what this is, bail out
        		if(orientation == ORIENTATION_UNKNOWN)
        			return;
        		
        		int minWidth;
        		int maxWidth;
        		
        		//Let's try and do things correctly for once
        		if(android.os.Build.VERSION.SDK_INT >= 13) {
        			Point point = new Point();
        			getWindowManager().getDefaultDisplay().getSize(point);
        			maxWidth = point.x;
        		} else {
        			maxWidth = getWindowManager().getDefaultDisplay().getWidth();
        		}

        		//Remove padding
        		minWidth = maxWidth - (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10, getResources().getDisplayMetrics()) * 2;

        		//Make sure that the EditText is wide enough
        		findViewById(R.id.code).setMinimumWidth(minWidth);
        		findViewById(R.id.console).setMinimumWidth(minWidth);

        		findViewById(R.id.code_scroller_x).setLayoutParams(new android.widget.ScrollView.LayoutParams(maxWidth, android.widget.ScrollView.LayoutParams.MATCH_PARENT));
        		findViewById(R.id.console_scroller_x).setLayoutParams(new android.widget.ScrollView.LayoutParams(maxWidth, android.widget.ScrollView.LayoutParams.MATCH_PARENT));
        }}).enable();
        
        //Create output streams for the console
        outStream = new PrintStream(new ConsoleStream());
        errStream = new PrintStream(new ConsoleStream());
        
        System.setOut(outStream);
        System.setErr(errStream);
        
        //Load default key bindings TODO load user's custom key bindings
        //Also, do this after we initialize the console so that we can get error reports
        
        keyBindings = new HashMap<String, KeyBinding>();
        
        try {
        	//Use Processing's XML for simplicity
			loadKeyBindings(new XML(getResources().getAssets().open("default_key_bindings.xml")));
		} catch (IOException e) { //Errors... who cares, anyway?
			e.printStackTrace();
		} catch (ParserConfigurationException e) {
			e.printStackTrace();
		} catch (SAXException e) {
			e.printStackTrace();
		}
    }
    
    public void onResume() {
    	super.onResume();
    	
    	//Reference the SharedPreferences text size value
    	((CodeEditText) findViewById(R.id.code)).refreshTextSize();
		((TextView) findViewById(R.id.console)).setTextSize(Integer.parseInt(PreferenceManager.getDefaultSharedPreferences(this).getString("textsize_console", "14")));
    	
    	//Disable / enable the soft keyboard
        if(PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getBoolean("use_hardware_keyboard", false))
        	getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        else
        	getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
    }
    
    /**
     * Load specified key bindings from the XML resource
     * 
     * @param xml
     */
    public void loadKeyBindings(XML xml) {
    	XML[] bindings = xml.getChildren();
    	for(XML binding : bindings) {
    		//Make sure that this is a "binding" element
    		if(!binding.getName().equals("binding"))
    			continue;
    		
    		//Parse the key binding
    		String name = binding.getContent();
    		int key = binding.getInt("key");
    		
    		String modifiers = binding.getString("mod");
    		String[] mods = modifiers.split("\\|"); // "|" is a REGEX keyword... need to escape it
    		
    		boolean ctrl = false;
    		boolean meta = false;
    		boolean func = false;
    		
    		boolean alt = false;
    		boolean sym = false;
    		boolean shift = false;
    		
    		//This isn't very elegant...
    		for(String mod : mods) {
    			if(mod.equals("ctrl")) ctrl = true;
    			if(mod.equals("meta")) meta = true;
    			if(mod.equals("func")) func = true;
    			
    			if(mod.equals("alt")) alt = true;
    			if(mod.equals("sym")) sym = true;
    			if(mod.equals("shift")) shift = true;
    		}
    		
    		//Build the KeyBinding
    		KeyBinding bind = new KeyBinding(name, key, ctrl, meta, func, alt, sym, shift);
    		
    		//Add the key binding
    		keyBindings.put(name, bind);
    	}
    }
    
    @SuppressLint("NewApi")
	@Override
    public boolean onKeyDown(int key, KeyEvent event) {
    	//Detect keyboard shortcuts
    	
    	// CTRL, META, and FUNCTION were added in Honeycomb...
    	// ...so we can't use them in 2.3
    	
    	// On older devices, we'll map:
    	//   CTRL      -->  ALT
    	//   META      -->  SYMBOL
    	//   FUNCTION  -->  SYMBOL
    	// 
    	// ...and yes, I know that META and FUNCTION map to the same value...
    	// ...but we can't exactly map CTRL+S to SHIFT+S, now can we?
    	
    	boolean ctrl;
		boolean meta;
		boolean func;
		
		boolean alt = event.isAltPressed();
    	boolean sym = event.isSymPressed();
    	boolean shift = event.isShiftPressed();
		
    	if(android.os.Build.VERSION.SDK_INT >= 11) {
    		//Read these values on API 11+
    		
    		ctrl = event.isCtrlPressed();
    		meta = event.isMetaPressed();
    		func = event.isFunctionPressed();
    	} else {
    		//Map these values to other ones on API 10 (app minimum)
    		
    		ctrl = alt;
    		meta = sym;
    		func = sym;
    	}
    	
    	//Check for the key bindings
    	//...this is where functional programming would come in handy
    	
    	if(keyBindings.get("save_sketch").matches(key, ctrl, meta, func, alt, sym, shift)) {
    		saveSketch();
    		return true;
    	}
    	if(keyBindings.get("new_sketch").matches(key, ctrl, meta, func, alt, sym, shift)) {
    		createNewSketch();
    		return true;
    	}
    	if(keyBindings.get("open_sketch").matches(key, ctrl, meta, func, alt, sym, shift)) {
    		loadSketch();
    		return true;
    	}
    	
    	if(keyBindings.get("run_sketch").matches(key, ctrl, meta, func, alt, sym, shift)) {
    		runApplication();
    		return true;
    	}
    	if(keyBindings.get("stop_sketch").matches(key, ctrl, meta, func, alt, sym, shift)) {
    		stopApplication();
    		return true;
    	}
    	
    	//TODO implement these functions... they're place-holders for now
    	//TODO are these actually getting picked up?
    	if(keyBindings.get("comment").matches(key, ctrl, meta, func, alt, sym, shift)) {
    		((CodeEditText) findViewById(R.id.code)).commentSelection();
    		return true;
    	}
    	if(keyBindings.get("shift_left").matches(key, ctrl, meta, func, alt, sym, shift)) {
    		((CodeEditText) findViewById(R.id.code)).shiftLeft();
    		return true;
    	}
    	if(keyBindings.get("shift_right").matches(key, ctrl, meta, func, alt, sym, shift)) {
    		((CodeEditText) findViewById(R.id.code)).shiftRight();
    		return true;
    	}
    	
    	if(keyBindings.get("new_tab").matches(key, ctrl, meta, func, alt, sym, shift)) {
    		addTabWithDialog();
    		return true;
    	}
    	if(keyBindings.get("delete_tab").matches(key, ctrl, meta, func, alt, sym, shift)) {
    		deleteTab();
    		return true;
    	}
    	if(keyBindings.get("rename_tab").matches(key, ctrl, meta, func, alt, sym, shift)) {
    		renameTab();
    		return true;
    	}
    	
		return super.onKeyDown(key, event);
    }
    
    public void createNewSketch() {
    	if(getGlobalState().getSketchName().equals("sketch")) {
			AlertDialog.Builder alert = new AlertDialog.Builder(this);
	    	
	    	alert.setTitle(R.string.save_sketch_dialog_title);
	    	alert.setMessage(R.string.save_sketch_dialog_message);
	    	
	    	alert.setPositiveButton(R.string.save_sketch, new DialogInterface.OnClickListener() {
	    		public void onClick(DialogInterface dialog, int whichButton) {
	    			//Save the sketch
	    			autoSave();
	    			
	    			getGlobalState().setSketchName("sketch");
	    			getGlobalState().setSelectedSketch(-1);
	    			newSketch();
	    			forceDrawerReload();
	    			
	    			getSupportActionBar().setTitle(getGlobalState().getSketchName());
	    	}});
	    	
	    	//TODO neutral and negative seem mixed up, uncertain of correct implementation - current set up is for looks
	    	alert.setNeutralButton(R.string.dont_save_sketch, new DialogInterface.OnClickListener() {
	    		public void onClick(DialogInterface dialog, int whichButton) {
	    			getGlobalState().setSketchName("sketch");
	    			getGlobalState().setSelectedSketch(-1);
	    			getGlobalState().getEditor().newSketch();
	    			forceDrawerReload();
	    			
	    			getSupportActionBar().setTitle(getGlobalState().getSketchName());
	    	}});
	    	
	    	alert.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
	    		public void onClick(DialogInterface dialog, int whichButton) {
	    	}});
	    	
	    	//Show the soft keyboard if the hardware keyboard is unavailable (hopefully)
	    	AlertDialog dialog = alert.create();
	    	if(!PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getBoolean("use_hardware_keyboard", false))
	    		dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
	    	dialog.show();
		} else {
			//Save the sketch
			autoSave();
			
			getGlobalState().setSketchName("sketch");
			getGlobalState().setSelectedSketch(-1);
			newSketch();
			forceDrawerReload();
			
			getSupportActionBar().setTitle(getGlobalState().getSketchName());
		}
    }
    
    private void loadSketch() {
		DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer);
		LinearLayout drawerLayout = (LinearLayout) findViewById(R.id.drawer_wrapper);
		
		drawer.openDrawer(drawerLayout);
	}
    
    protected void populateWithSketches(ArrayAdapter<String> items) {
    	//The location of the sketchbook
    	File sketchbookLoc = getGlobalState().getSketchbookFolder();
    	
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
		try {
			String sketchName = readTempFile("sketchName.txt");
			int selectedSketch = Integer.parseInt(readTempFile("sketchNum.txt"));
			
			if(getSketchLoc(sketchName).exists()) {
				getGlobalState().setSelectedSketch(selectedSketch);
				return loadSketch(sketchName);
			} else {
				return loadSketchTemp();
			}
		} catch(Exception e) {
			return false;
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
    	//The location of the sketchbook
    	File sketchbookLoc = getGlobalState().getSketchbookFolder();
    	
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
        getMenuInflater().inflate(R.menu.activity_editor, menu);
        
        if(drawerOpen) {
        	menu.findItem(R.id.menu_run).setVisible(false);
        	menu.findItem(R.id.menu_stop).setVisible(false);
        	menu.findItem(R.id.menu_tab_delete).setVisible(false);
        	menu.findItem(R.id.menu_tab_rename).setVisible(false);
        	menu.findItem(R.id.menu_save).setVisible(false);
        	menu.findItem(R.id.menu_new).setVisible(false);
        	menu.findItem(R.id.menu_load).setVisible(false);
        	menu.findItem(R.id.menu_tab_new).setVisible(false);
        	menu.findItem(R.id.menu_sketch_properties).setVisible(false);
        	
        	getSupportActionBar().setTitle(R.string.app_name);
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
        	menu.findItem(R.id.menu_new).setVisible(true);
        	menu.findItem(R.id.menu_load).setVisible(true);
        	menu.findItem(R.id.menu_tab_new).setVisible(true);
        	menu.findItem(R.id.menu_sketch_properties).setVisible(true);
        	
        	getSupportActionBar().setTitle(getGlobalState().getSketchName());
        }
        
        //Disable these buttons because they appear when the tab is pressed
        //Not getting rid of them completely in case we want to change things in the future
        menu.findItem(R.id.menu_tab_new).setVisible(false);
        menu.findItem(R.id.menu_tab_delete).setVisible(false);
    	menu.findItem(R.id.menu_tab_rename).setVisible(false);
        
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
            case R.id.menu_new:
            	createNewSketch();
            	return true;
            case R.id.menu_load:
            	loadSketch();
            	return true;
            case R.id.menu_settings:
            	launchSettings();
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
    
    private void runApplication() {
    	//Save the sketch
    	if(getSketchLoc(getSupportActionBar().getTitle().toString()).exists())
    		saveSketch();
    	else {
    		//If the sketch has yet to be saved, inform the user
    		AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(getResources().getText(R.string.save_sketch_before_run_dialog_title))
            	.setMessage(getResources().getText(R.string.save_sketch_before_run_dialog_message)).setCancelable(false)
            	.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            	
            	@Override
                public void onClick(DialogInterface dialog, int which) {}
            }).show();
            
            return;
    	}
    	
    	//In case the user presses the button twice, we don't want any errors
    	if(building)
    		return;
    	
    	//Clear the console
    	((TextView) findViewById(R.id.console)).setText("");
    	
    	SharedPreferences prefs = getSharedPreferences(getGlobalState().getSketchName(), 0);
    	
    	final Build builder = new Build(getGlobalState());
    	Build.customManifest = new AtomicBoolean(prefs.getBoolean("use_custom_manifest", false));
    	Build.prettyName = new AtomicReference<String>(prefs.getString("prop_pretty_name", getGlobalState().getSketchName()));
    	
    	String[] perms = prefs.getString("permissions", "").split(",");
    	Build.perms = new AtomicReferenceArray<String>(perms.length);
    	for(int i = 0; i < perms.length; i ++)
    		Build.perms.set(i, perms[i]);
    	
    	Build.targetSdk = new AtomicInteger(Integer.parseInt(prefs.getString("prop_target_sdk", getResources().getString(R.string.prop_target_sdk_default))));
    	Build.orientation = new AtomicReference<String>(prefs.getString("prop_orientation", getResources().getString(R.string.prop_orientation_default)));
    	
    	//Build the sketch in a separate thread
    	Thread buildThread = new Thread(new Runnable() {
    		@Override
    		public void run() {
    			building = true;
    			builder.build("debug");
    			building = false;
    	}});
    	buildThread.start();
    }
    
    private void stopApplication() {
    	//This will stop the current build process
    	//I don't think we can stop a running app...
    	//...that's what the BACK button is for
    	
    	if(building)
    		Build.halt();
    }
    
    public void message(String msg) {
    	((TextView) findViewById(R.id.message)).setText(msg);
    	((TextView) findViewById(R.id.message)).setBackgroundColor(getResources().getColor(R.color.message_back));
    	((TextView) findViewById(R.id.message)).setTextColor(getResources().getColor(R.color.message_text));
    }
    
    public void messageExt(final String msg) {
    	runOnUiThread(new Runnable() {
    		public void run() {
    			((TextView) findViewById(R.id.message)).setText(msg);
    			((TextView) findViewById(R.id.message)).setBackgroundColor(getResources().getColor(R.color.message_back));
    			((TextView) findViewById(R.id.message)).setTextColor(getResources().getColor(R.color.message_text));
    		}
    	});
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
    
    public void errorExt(final String msg) {
    	runOnUiThread(new Runnable() {
    		public void run() {
    			((TextView) findViewById(R.id.message)).setText(msg);
    	    	((TextView) findViewById(R.id.message)).setBackgroundColor(getResources().getColor(R.color.error_back));
    	    	((TextView) findViewById(R.id.message)).setTextColor(getResources().getColor(R.color.error_text));
    		}
    	});
    }
    
    public void error(CharSequence msg) {
    	((TextView) findViewById(R.id.message)).setText(msg);
    	((TextView) findViewById(R.id.message)).setBackgroundColor(getResources().getColor(R.color.error_back));
    	((TextView) findViewById(R.id.message)).setTextColor(getResources().getColor(R.color.error_text));
    }
    
    public void highlightLineExt(final int tab, final int line) {
    	runOnUiThread(new Runnable() {
    		public void run() {
    			//Switch to the tab with the error
    			getSupportActionBar().selectTab(getSupportActionBar().getTabAt(tab));
    			
    			CodeEditText code = (CodeEditText) findViewById(R.id.code);
    			
    			int start = code.offsetForLine(line);
    			int stop = code.offsetForLineEnd(line);
    			
    			if(!PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getBoolean("use_hardware_keyboard", false)) {
    			//Hacky way of focusing the code area
    				MotionEvent me = MotionEvent.obtain(100, 0, 0, 0, 0, 0);
    				code.dispatchTouchEvent(me);
    				me.recycle();
    			}
    			
    			code.setSelection(start, stop);
    		}
    	});
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
    	
    	customizeTab(tab, title);
    }
    
	private void addTab(String title) {
    	ActionBar.Tab tab = getSupportActionBar().newTab();
    	tab.setText(title);
    	tab.setTabListener(this);
    	getSupportActionBar().addTab(tab, getSupportActionBar().getTabCount());
    	
    	tabs.put(tab, new FileMeta(title, "", 0, 0));
    	
    	getSupportActionBar().selectTab(tab);
    	
    	customizeTab(tab, title);
	}
	
	//Hacky way of making the tabs lowercase and uppercase
	//Theoretically, we could do some other stuff here, too...
	private void customizeTab(Tab tab, String title) {
    	TextView view = new TextView(this);
    	view.setText(title);
    	view.setTextColor(getResources().getColor(R.color.tab_text_color));
    	view.setGravity(Gravity.CENTER);
    	view.setTypeface(Typeface.DEFAULT_BOLD);
    	view.setTextSize(12);
    	view.setLines(2);
    	view.setText("\n" + view.getText());
    	
    	//Long clicks aren't detected; instead, we use onTabReselected() to detect the selection of an already-selected tab
    	
    	tab.setCustomView(view);
    }
	
	private void addTab(String title, FileMeta meta) {
    	ActionBar.Tab tab = getSupportActionBar().newTab();
    	tab.setText(title);
    	tab.setTabListener(this);
    	getSupportActionBar().addTab(tab, getSupportActionBar().getTabCount());
    	
    	tabs.put(tab, meta);
    	
    	customizeTab(tab, title);
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
    	File sketchLoc = getSketchLoc(getGlobalState().getSketchName());
    	
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
    	if(!PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getBoolean("use_hardware_keyboard", false))
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
	public void onTabReselected(Tab tab, FragmentTransaction ft) {
		if(!drawerOpen) {
			View anchorView = findViewById(R.id.tab_buffer);

			//Create a PopupMenu anchored to a 0dp height "fake" view at the top if the display
			//This is a custom implementation, designed to support API level 10+ (Android's PopupMenu is 11+)
			PopupMenu popup = new PopupMenu(getGlobalState().getEditor(), anchorView);

			//Populate the actions
			MenuInflater inflater = getMenuInflater(); //TODO mixed some things up when switching to AppCompat?
			inflater.inflate(R.menu.tab_actions, popup.getMenu());
			
			//Detect presses
			popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
				@Override
				public boolean onMenuItemClick(MenuItem item) {
					switch(item.getItemId()) {
					case R.id.menu_tab_new:
						addTabWithDialog();
						return true;
					case R.id.menu_tab_rename:
						renameTab();
						return true;
					case R.id.menu_tab_delete:
						deleteTab();
						return true;
					}

					return false;
				}
			});
			popup.show();
		}
	}
	
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
	
	private void launchSettings() {
		Intent intent = new Intent(this, SettingsActivity.class);
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
	
	/**
	 * Add a message to the console and automatically scroll to the bottom (if the user has this feature turned on)
	 * 
	 * @param msg
	 */
	public void postConsole(String msg) {
		final TextView tv = (TextView) findViewById(R.id.console);
		
		//Add the text
		tv.append(msg);
		
		final ScrollView scroll = ((ScrollView) findViewById(R.id.console_scroller));
		
		//Scroll to the bottom (if the user has this feature enabled)
		if(PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getBoolean("pref_scroll_lock", true))
			scroll.post(new Runnable() {
				@Override
				public void run() {
					scroll.fullScroll(ScrollView.FOCUS_DOWN);
			}});
	}
	
	//Listener class for managing message area drag events
	public class MessageTouchListener implements android.view.View.OnLongClickListener, android.view.View.OnTouchListener {
		private boolean pressed;
		private int touchOff;
		
		private View code;
		private View console;
		private View content;
		
		public MessageTouchListener() {
			super();
			
			pressed = false;
			
			//Store necessary views globally
			code = findViewById(R.id.code_scroller);
			console = findViewById(R.id.console_scroller);
			content = findViewById(R.id.content);
		}
		
		@SuppressWarnings("deprecation")
		@Override
		public boolean onTouch(View view, MotionEvent event) {
			//Don't resize the console if there is no console to speak of
			if(keyboardVisible)
				return false;
			
			//Get the offset relative to the touch
			if(event.getAction() == MotionEvent.ACTION_DOWN)
				touchOff = (int) event.getY();
			
			if(pressed) {
				switch(event.getAction()) {
				case MotionEvent.ACTION_MOVE:
					//This doesn't work in the constructor for some reason
					if(message == 0)
						message = findViewById(R.id.message).getHeight();
					
					//Calculate maximum possible code view height
					int maxCode = content.getHeight() - message;
					
					//Find relative movement for this event
					int y = (int) event.getY() - touchOff;
					
					//Calculate the new dimensions of the console
					int consoleDim = console.getHeight() - y;
					if(consoleDim < 0)
						consoleDim = 0;
					if(consoleDim > maxCode)
						consoleDim = maxCode;
					
					//Calculate the new dimensions of the code view
					int codeDim = maxCode - consoleDim;
					
					//Set the new dimensions
					code.setLayoutParams(new android.widget.LinearLayout.LayoutParams(android.widget.LinearLayout.LayoutParams.MATCH_PARENT, codeDim));
					console.setLayoutParams(new android.widget.LinearLayout.LayoutParams(android.widget.LinearLayout.LayoutParams.MATCH_PARENT, consoleDim));
					
					firstResize = false;
					
					return true;
				case MotionEvent.ACTION_UP:
					pressed = false;
					findViewById(R.id.message).setBackgroundDrawable(getResources().getDrawable(R.drawable.back)); //TODO this is deprecated...
					
					return true;
				}
			}
			
			return false;
		}
		
		@SuppressWarnings("deprecation")
		@Override
		public boolean onLongClick(View view) {
			//Don't resize the console if there is no console to speak of
			if(keyboardVisible)
				return false;
			
			pressed = true;
			findViewById(R.id.message).setBackgroundDrawable(getResources().getDrawable(R.drawable.back_selected)); //TODO this is deprecated...
			
			//Provide haptic feedback (if the user has vibrations enabled)
			if(PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getBoolean("pref_vibrate", true))
				((android.os.Vibrator) getSystemService(VIBRATOR_SERVICE)).vibrate(200); //200 millis
			
			return true;
		}
	}
	
	private class ConsoleStream extends OutputStream {
		final byte single[] = new byte[1];
		
		public ConsoleStream() {
		}
		
		public void close() { }
		
		public void flush() { }
		
		public void write(byte b[]) {
			write(b, 0, b.length);
		}
		
		@Override
		public void write(byte b[], int offset, int length) {
			final String value = new String(b, offset, length);
			
			runOnUiThread(new Runnable() {
				public void run() {
					//Write the value to the console
					postConsole(value);
			}});
		}
		
		public void write(int b) {
			single[0] = (byte) b;
			write(single, 0, 1);
		}
	}
}