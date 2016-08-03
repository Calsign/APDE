package com.calsignlabs.apde;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.graphics.Point;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.support.design.widget.TabLayout;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.ViewPager;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.PopupMenu;
import android.support.v7.widget.Toolbar;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.MeasureSpec;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.calsignlabs.apde.build.Build;
import com.calsignlabs.apde.build.CopyAndroidJarTask;
import com.calsignlabs.apde.build.Manifest;
import com.calsignlabs.apde.support.ResizeAnimation;
import com.calsignlabs.apde.tool.FindReplace;
import com.calsignlabs.apde.tool.Tool;
import com.ipaulpro.afilechooser.utils.FileUtils;

import org.xml.sax.SAXException;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map.Entry;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.xml.parsers.ParserConfigurationException;

import processing.data.XML;

/**
 * This is the editor, or the main activity of APDE
 */
public class EditorActivity extends AppCompatActivity {
	//List of key bindings for hardware / bluetooth keyboards
	private HashMap<String, KeyBinding> keyBindings;
	
	public Toolbar toolbar;
	
	public ViewPager codePager;
	public FragmentStatePagerAdapter codePagerAdapter;
	
	protected TabLayout codeTabStrip;
	
	//List of tabs
	protected ArrayList<SketchFile> tabs;
	
	//Whether or not the sketch has been saved TODO this isn't even being used right now
	private boolean saved;
	
	//Used for accessing the sliding drawer that contains the "Sketchbook"
	private ActionBarDrawerToggle drawerToggle;
	private boolean drawerOpen;
	
	private APDE.SketchLocation drawerSketchLocationType;
	private String drawerSketchPath;
	private boolean drawerRecentSketch;
	
	//Used for adjusting the display to accomodate for the keyboard
	protected boolean keyboardVisible;
	private boolean firstResize = true; //this is a makeshift arrangement (hopefully)
	private int oldCodeHeight = -1;
	private boolean consoleWasHidden = false;
	
	private View extraHeaderView;
	
	//Possible dialog results
	private final static int RENAME_TAB = 0;
	private final static int NEW_TAB = 1;
	
	//Listener for managing the sliding message area
	private MessageTouchListener messageListener;
	//The height of the message area...
	private int message = -1;
	
	//Custom output / error streams for printing to the console
	private PrintStream outStream;
	private PrintStream errStream;
	protected AtomicBoolean FLAG_SUSPEND_OUT_STREAM = new AtomicBoolean(false);
	
	//Recieve log / console output from sketches
	private BroadcastReceiver consoleBroadcastReceiver;
	
	//Whether or not we are currently building a sketch
	private boolean building;
	
	//Whether or not the message area is currently displaying an error message
	private boolean errorMessage = false;
	
	//Whether or not the special character inserts tray is currently visible
	private boolean charInserts = false;
	//A reference to the toggle char inserts button... and why do we need this?
	//It's because adding views to the char insert tray is somehow breaking the retrieval of this view by ID...
	private ImageButton toggleCharInserts;
	
	private boolean twoFingerSwipe = false;
	
	//Intent flag to delete the old just-installed APK file
	public static final int FLAG_DELETE_APK = 5;
	
    @SuppressLint("NewApi")
	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_editor);
		
		toolbar = (Toolbar) findViewById(R.id.toolbar);
		toolbar.setBackgroundColor(getResources().getColor(R.color.bar_overlay));
		setSupportActionBar(toolbar);
		
		// Create custom output / error streams for the console
		outStream = new PrintStream(new ConsoleStream());
		errStream = new PrintStream(new ConsoleStream());
		
		// Set the custom output / error streams
		System.setOut(outStream);
		System.setErr(errStream);
		
		// Initialize log / console receiver
		consoleBroadcastReceiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				String message = intent.getStringExtra("com.calsignlabs.apde.LogMessage");
				String exception = intent.getStringExtra("com.calsignlabs.apde.LogException");
				
				// We can show different colors for different severities if we want to... later...
				switch (intent.getCharExtra("com.calsignlabs.apde.LogSeverity", 'o')) {
				case 'o':
					postConsole(message);
					break;
				case 'e':
					postConsole(message);
					break;
				case 'x':
					errorExt(message != null ? exception.concat(": ").concat(message) : exception);
					break;
				}
			}
		};
		
		// Register receiver for sketch logs / console output
		registerReceiver(consoleBroadcastReceiver, new IntentFilter("com.calsignlabs.apde.LogBroadcast"));
		
		getGlobalState().initTaskManager();
		
		// Make sure that we have a good sketchbook folder to use
		getGlobalState().getSketchbookDrive();
		
		//Initialize the list of tabs
		tabs = new ArrayList<SketchFile>();
        
		codePager = (ViewPager) findViewById(R.id.code_pager);
		codePagerAdapter = new FragmentStatePagerAdapter(getSupportFragmentManager()) {
			@Override
			public int getCount() {
				return tabs.size();
			}
			
			@Override
			public CharSequence getPageTitle(int position) {
				return tabs.get(position).getTitle();
			}
			
			@Override
			public Fragment getItem(int position) {
				return tabs.get(position).getFragment();
			}
			
			@Override
			public int getItemPosition(Object object) {
				// This forces the ViewPager to get new fragments every time we call notifyDataSetChanged()
				// This is necessary so that we don't reuse the same fragments, because reusing the
				// same fragments means that they will contain the same code which is... not good
				return POSITION_NONE;
			}
		};
		codePager.setAdapter(codePagerAdapter);
		
		codeTabStrip = (TabLayout) findViewById(R.id.code_pager_tabs);
		codeTabStrip.setBackgroundColor(getResources().getColor(R.color.bar_overlay));
		codeTabStrip.setSelectedTabIndicatorColor(getResources().getColor(R.color.holo_select));
		codeTabStrip.setSelectedTabIndicatorHeight((int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 5, getResources().getDisplayMetrics()));
		codeTabStrip.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
			@Override
			public void onTabSelected(TabLayout.Tab tab) {}
			
			@Override
			public void onTabUnselected(TabLayout.Tab tab) {}
			
			@Override
			public void onTabReselected(TabLayout.Tab tab) {
				View anchor = ((LinearLayout) codeTabStrip.getChildAt(0)).getChildAt(tab.getPosition());
				EditorActivity.this.onTabReselected(anchor);
			}
		});
		
        // Load all of the permissions
        Manifest.loadPermissions(this);
        
        // Initialize the sliding message area listener
        messageListener = new MessageTouchListener();
        
        // Enable the message area listener
        findViewById(R.id.buffer).setOnLongClickListener(messageListener);
        findViewById(R.id.buffer).setOnTouchListener(messageListener);
        
        // The sketch is not saved TODO what's going on here, really?
        setSaved(false);

		getGlobalState().rebuildToolList();
		
        // Initialize the global APDE application object
        getGlobalState().setEditor(this);
        
        // Initialize the action bar title
        getSupportActionBar().setTitle(getGlobalState().getSketchName());
        
        // Check for an app update
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        int oldVersionNum = prefs.getInt("version_num", -1);
        int realVersionNum = getGlobalState().appVersionCode();
		
		boolean justUpdated = realVersionNum > oldVersionNum;
        
        if (justUpdated) {
        	// Do anything that needs to be done for this upgrade
			runUpgradeChanges(oldVersionNum, realVersionNum);
        	
        	// Make sure to update the value so we don't do this again
        	SharedPreferences.Editor edit = prefs.edit();
        	edit.putInt("version_num", realVersionNum);
        	edit.apply();
        }
        
        // Initialize the navigation drawer
        final DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer);
        final ListView drawerList = (ListView) findViewById(R.id.drawer_list);
        
        drawerSketchLocationType = null;
        drawerSketchPath = "";
        
        // Populate the drawer
        forceDrawerReload();
        
        // Initialize the drawer drawer toggler
        drawerToggle = new ActionBarDrawerToggle(this, drawer, toolbar, R.string.nav_drawer_open, R.string.nav_drawer_close) {
            @Override
        	public void onDrawerClosed(View view) {
				if (isSelectedCodeAreaInitialized()) {
					getSelectedCodeArea().setEnabled(true);
				}
                supportInvalidateOptionsMenu();
            }
            
            @Override
            public void onDrawerSlide(View drawer, float slide) {
            	super.onDrawerSlide(drawer, slide);
            	
            	if(slide > 0) { // Detect an open event
					if (isSelectedCodeAreaInitialized()) {
						getSelectedCodeArea().setEnabled(false);
					}
                    supportInvalidateOptionsMenu();
                    drawerOpen = true;
                    
                    // Display the relative path in the action bar
                    if(drawerSketchLocationType != null) {
    					getSupportActionBar().setSubtitle(drawerSketchLocationType.toReadableString(getGlobalState()) + drawerSketchPath + "/");
    				} else {
    					getSupportActionBar().setSubtitle(null);
    				}
            	} else { // Detect a close event
            		// Re-enable the code area
					if (isSelectedCodeAreaInitialized()) {
						getSelectedCodeArea().setEnabled(true);
					}
                    supportInvalidateOptionsMenu();
                    drawerOpen = false;
                    
                    //Hide the relative path
    				getSupportActionBar().setSubtitle(null);
            	}
            }
            
            @Override
            public void onDrawerOpened(View drawerView) {
				if (isSelectedCodeAreaInitialized()) {
					getSelectedCodeArea().setEnabled(false);
				}
                supportInvalidateOptionsMenu();
        }};
        drawer.setDrawerListener(drawerToggle);
        
        // Detect drawer sketch selection events
        drawerList.setOnItemClickListener(new ListView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				FileNavigatorAdapter.FileItem item = ((FileNavigatorAdapter) drawerList.getAdapter()).getItem(position);
				
				if (drawerSketchLocationType == null && !drawerRecentSketch) {
					switch (position) {
					case 0:
						drawerSketchLocationType = APDE.SketchLocation.SKETCHBOOK;
						break;
					case 1:
						drawerSketchLocationType = APDE.SketchLocation.EXAMPLE;
						break;
					case 2:
						drawerSketchLocationType = APDE.SketchLocation.LIBRARY_EXAMPLE;
						break;
					case 3:
						drawerSketchLocationType = APDE.SketchLocation.TEMPORARY;
						break;
					case 4:
						drawerRecentSketch = true;
						break;
					}
				} else {
					switch (item.getType()) {
					case NAVIGATE_UP:
						int lastSlash = drawerSketchPath.lastIndexOf('/');
						if (lastSlash > 0) {
							drawerSketchPath = drawerSketchPath.substring(0, lastSlash);
						} else if(drawerSketchPath.length() > 0) {
							drawerSketchPath = "";
						} else {
							drawerSketchLocationType = null;
						}
						
						if (drawerRecentSketch) {
							drawerRecentSketch = false;
						}
						
						break;
					case MESSAGE:
						break;
					case FOLDER:
						drawerSketchPath += "/" + item.getText();
						
						break;
					case SKETCH:
						// Save the current sketch...
						autoSave();
						
						if (drawerRecentSketch) {
							APDE.SketchMeta sketch = getGlobalState().getRecentSketches().get(position - 1); // "position - 1" because the first item is the UP button
							
							loadSketch(sketch.getPath(), sketch.getLocation());
						} else {
							loadSketch(drawerSketchPath + "/" + item.getText(), drawerSketchLocationType);
						}
						
						drawer.closeDrawers();
						
						break;
					}
				}
				
				if (drawerSketchLocationType != null) {
					getSupportActionBar().setSubtitle(drawerSketchLocationType.toReadableString(getGlobalState()) + drawerSketchPath + "/");
				} else if (drawerRecentSketch) {
					getSupportActionBar().setSubtitle(getResources().getString(R.string.recent) + "/");
				} else {
					getSupportActionBar().setSubtitle(null);
				}
				
				forceDrawerReload();
			}
		});
        
        // TODO This scrolling is currently somewhat choppy because we have a drag listener for every item to deal with ListView item recycling
		drawerList.setOnDragListener(new View.OnDragListener() {
			final float THRESHOLD = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 100, getResources().getDisplayMetrics());
			
			@Override
			public boolean onDrag(View view, DragEvent event) {
				// If the dragged item is nearing the edges, scroll to see more content
				// Dragged items will be sketches / folders
				
				switch(event.getAction()) {
					case DragEvent.ACTION_DRAG_LOCATION:
						float y = event.getY();
						float h = drawerList.getHeight();
						
						float upDif = y - THRESHOLD;
						float downDif = y - (h - THRESHOLD);
						
						if(upDif < 0) {
							drawerList.smoothScrollBy((int) upDif, 300);
						}
						if(downDif > 0) {
							drawerList.smoothScrollBy((int) upDif, 300);
						}
						
						break;
				}
				
				return true;
			}
		});
        
        // Enable the home button, the "home as up" will actually get replaced by the drawer toggle button
        getSupportActionBar().setHomeButtonEnabled(true);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        
        // Obtain the root view
        final View activityRootView = findViewById(R.id.content);
        
        // Detect software keyboard open / close events
        // StackOverflow: http://stackoverflow.com/questions/2150078/how-to-check-visibility-of-software-keyboard-in-android
        activityRootView.getViewTreeObserver().addOnGlobalLayoutListener(new OnGlobalLayoutListener() {
			@SuppressWarnings("deprecation")
			@Override
			public void onGlobalLayout() {
				// Calculate the difference in height
				Rect r = new Rect();
				activityRootView.getWindowVisibleDisplayFrame(r);
				int heightDiff = activityRootView.getRootView().getHeight() - (r.bottom - r.top);
				
				// Hide the soft keyboard if it's trying to show its dirty face...
				// ...and the user doesn't want it
				if (PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getBoolean("use_hardware_keyboard", false)) {
					InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
					imm.hideSoftInputFromWindow(activityRootView.getWindowToken(), 0);
					return;
				}
				
				if (oldCodeHeight == -1) {
					oldCodeHeight = codePager.getHeight();
				}
				
				if (heightDiff > getResources().getDimension(R.dimen.keyboard_visibility_change_threshold)) { //If the difference is bigger than 200dp, it's probably the keyboard
					
					// An important note for understanding the following code:
					// The tab bar is actually inside the code area pager, so the height of "code"
					// includes the height of the tab bar
					
					if (!keyboardVisible) {
						keyboardVisible = true;
						
						if (message == -1) {
							message = findViewById(R.id.buffer).getHeight();
						}
						
						// Configure the layout for the keyboard

						LinearLayout buffer = (LinearLayout) findViewById(R.id.buffer);
						TextView messageArea = (TextView) findViewById(R.id.message);
						View console = findViewById(R.id.console_scroller);
						View content = findViewById(R.id.content);
						
						if (firstResize) {
							firstResize = false;
						} else {
							oldCodeHeight = codePager.getHeight();
						}
						
						int totalHeight = content.getHeight() - message - (extraHeaderView != null ? extraHeaderView.getHeight() : 0);
						
						if (totalHeight > oldCodeHeight) {
							codePager.startAnimation(new ResizeAnimation<LinearLayout>(codePager, ResizeAnimation.DEFAULT, ResizeAnimation.DEFAULT, ResizeAnimation.DEFAULT, totalHeight));
							console.startAnimation(new ResizeAnimation<LinearLayout>(console, ResizeAnimation.DEFAULT, ResizeAnimation.DEFAULT, ResizeAnimation.DEFAULT, 0));
						} else {
							codePager.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, totalHeight));
							console.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0));
						}
						
						//Remove the focus from the Message slider if it has it and maintain styling
						if (errorMessage) {
							buffer.setBackgroundDrawable(getResources().getDrawable(R.drawable.back_error));
							buffer.setBackgroundColor(getResources().getColor(R.color.error_back));
							messageArea.setTextColor(getResources().getColor(R.color.error_text));
						} else {
							buffer.setBackgroundDrawable(getResources().getDrawable(R.drawable.back));
							buffer.setBackgroundColor(getResources().getColor(R.color.message_back));
							messageArea.setTextColor(getResources().getColor(R.color.message_text));
						}
						
						CodeEditText codeArea = getSelectedCodeArea();
						
						if (codeArea != null) {
							codeArea.updateBracketMatch();
						}
						
						// Don't do anything if the user has disabled the character insert tray
						if (PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getBoolean("char_inserts", true)) {
							//Update the character insert tray
							toggleCharInserts.setVisibility(View.VISIBLE);
							findViewById(R.id.toggle_char_inserts_separator).setVisibility(View.VISIBLE);
							
							if (charInserts) {
								findViewById(R.id.message).setVisibility(View.GONE);
								findViewById(R.id.char_insert_tray).setVisibility(View.VISIBLE);
							}
						}
					}
				} else {
					if (keyboardVisible) {
						//Configure the layout for the absence of the keyboard
						
						View codeArea = getSelectedCodeAreaScroller();
						View consoleArea = findViewById(R.id.console_scroller);
						
						ViewGroup content = (ViewGroup) findViewById(R.id.content);
						
						int totalHeight = content.getHeight() - message - (extraHeaderView != null ? extraHeaderView.getHeight() : 0);
						
						codePager.startAnimation(new ResizeAnimation<LinearLayout>(codePager, ResizeAnimation.DEFAULT, ResizeAnimation.DEFAULT, ResizeAnimation.DEFAULT, oldCodeHeight, false));
						consoleArea.startAnimation(new ResizeAnimation<LinearLayout>(consoleArea, ResizeAnimation.DEFAULT, codeArea.getHeight(), ResizeAnimation.DEFAULT, totalHeight - oldCodeHeight, false));
						
						keyboardVisible = false;
						
						if (oldCodeHeight > 0) {
							consoleWasHidden = false;
						}
						
						// Remove any unnecessary focus from the code area
						getSelectedCodeArea().clearFocus();
						getSelectedCodeArea().matchingBracket = -1;
						
						// Update the character insert tray
						toggleCharInserts.setVisibility(View.GONE);
						findViewById(R.id.toggle_char_inserts_separator).setVisibility(View.GONE);
						
						findViewById(R.id.message).setVisibility(View.VISIBLE);
						findViewById(R.id.char_insert_tray).setVisibility(View.GONE);
					}
				}
			}
		});
		
		// Set up character insert tray toggle
		toggleCharInserts = (ImageButton) findViewById(R.id.toggle_char_inserts);
		toggleCharInserts.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View view) {
				toggleCharInserts();
			}
		});
        
        // Load default key bindings TODO load user's custom key bindings
        // Also, do this after we initialize the console so that we can get error reports
        
        keyBindings = new HashMap<String, KeyBinding>();
        
        try {
        	// Use Processing's XML for simplicity
			loadKeyBindings(new XML(getResources().getAssets().open("default_key_bindings.xml")));
		} catch (IOException e) { // Errors... who cares, anyway?
			e.printStackTrace();
		} catch (ParserConfigurationException e) {
			e.printStackTrace();
		} catch (SAXException e) {
			e.printStackTrace();
		}
        
        // Initialize the reference to the toggle char inserts button
        toggleCharInserts = (ImageButton) findViewById(R.id.toggle_char_inserts);
		
		// Show "What's New" screen if this is an update
		
		if (justUpdated && PreferenceManager.getDefaultSharedPreferences(this).getBoolean("pref_whats_new_enable", true)) {
			final Stack<String> releaseNotesStack = getReleaseNotesStack(this);
			
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setTitle(R.string.whats_new);
			
			final RelativeLayout layout;
			
			layout = (RelativeLayout) View.inflate(new ContextThemeWrapper(this, R.style.Theme_AppCompat_Dialog), R.layout.whats_new, null);
			
			final ListView list = (ListView) layout.findViewById(R.id.whats_new_list);
			final Button loadMore = (Button) layout.findViewById(R.id.whats_new_more);
			final CheckBox keepShowing = (CheckBox) layout.findViewById(R.id.whats_new_keep_showing);
			
			final ArrayAdapter<String> listAdapter = new ArrayAdapter<String>(this, R.layout.whats_new_list_item, R.id.whats_new_list_item_text);
			list.setAdapter(listAdapter);
			
			addWhatsNewItem(list, listAdapter, releaseNotesStack, loadMore, false);
			
			loadMore.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					// Load five at once
					for (int i = 0; i < 5; i++) {
						// Stop if we can't add any more
						if (!addWhatsNewItem(list, listAdapter, releaseNotesStack, loadMore, true)) {
							break;
						}
					}
					
					// Make the dialog big enough to hold all of them
					int w = FrameLayout.LayoutParams.MATCH_PARENT;
					int h = FrameLayout.LayoutParams.WRAP_CONTENT;
							
					layout.setLayoutParams(new FrameLayout.LayoutParams(w, h));
				}
			});
			
			builder.setView(layout);
			builder.setNeutralButton(R.string.ok, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
				}
			});
			
			AlertDialog dialog = builder.create();
			dialog.show();
			
			dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
				@Override
				public void onDismiss(DialogInterface dialog) {
					// Let the user disable the "What's New" screen functionality
					SharedPreferences.Editor edit = PreferenceManager.getDefaultSharedPreferences(EditorActivity.this).edit();
					edit.putBoolean("pref_whats_new_enable", keepShowing.isChecked());
					edit.apply();
					
					// If the "What's New" screen is visible, wait to show the examples updates screen
					
					// Update examples repository
					getGlobalState().initExamplesRepo();
				}
			});
			
			layout.requestLayout();
			
			layout.post(new Runnable() {
				@Override
				public void run() {
					//Force the dialog to be the right size...
					
					int w = FrameLayout.LayoutParams.MATCH_PARENT;
					int h = list.getChildAt(0).getHeight()
							+ loadMore.getHeight() + keepShowing.getHeight()
							+ Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 24, getResources().getDisplayMetrics()));
					
					layout.setLayoutParams(new FrameLayout.LayoutParams(w, h));
				}
			});
		} else {
			// If the "What's New" screen is visible, wait to show the examples updates screen
			
			// Update examples repository
			getGlobalState().initExamplesRepo();
		}
		
		codePagerAdapter.notifyDataSetChanged();
		codeTabStrip.setupWithViewPager(codePager);
	
		// Try to load the auto-save sketch, otherwise set the editor up as a new sketch
		if (!loadSketchStart()) {
			getGlobalState().selectNewTempSketch();
			addDefaultTab(APDE.DEFAULT_SKETCH_TAB);
			autoSave();
		}
    }
	
	@Override
	public void onStart() {
		super.onStart();
		
		APDE.StorageDrive.StorageDriveType storageDriveType = getGlobalState().getSketchbookStorageDrive().type;
		
		if (storageDriveType.equals(APDE.StorageDrive.StorageDriveType.PRIMARY_EXTERNAL) || storageDriveType.equals(APDE.StorageDrive.StorageDriveType.EXTERNAL)) {
			// Make sure we have WRITE_EXTERNAL_STORAGE
			if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
				// We have to request it...
				// TODO Explain why to the user?
				ActivityCompat.requestPermissions(this, new String[] {android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSIONS_REQUEST_CODE);
			}
		}
	}
	
	protected final int PERMISSIONS_REQUEST_CODE = 42;
	
	@Override
	public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
		switch (requestCode) {
		case PERMISSIONS_REQUEST_CODE:
			if (grantResults.length == 0 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
				// TODO Explain that we NEED this permission!
			}
			break;
		}
	}
	
	public int getSelectedCodeIndex() {
		return codePager.getCurrentItem();
	}
	
	public void selectCode(int index) {
		codePager.setCurrentItem(index);
	}
	
	public int getCodeCount() {
		return tabs.size();
	}
	
	public boolean isSelectedCodeAreaInitialized() {
		return getSelectedSketchFile() != null && getSelectedSketchFile().getFragment().getCodeEditText() != null;
	}
	
	public CodeEditText getSelectedCodeArea() {
		return getSelectedSketchFile() != null ? getSelectedSketchFile().getFragment().getCodeEditText() : null;
	}
	
	public ScrollView getSelectedCodeAreaScroller() {
		return getSelectedSketchFile() != null ? getSelectedSketchFile().getFragment().getCodeScroller() : null;
	}
	
	public SketchFile getSelectedSketchFile() {
		return tabs.size() > 0 ? tabs.get(getSelectedCodeIndex()) : null;
	}
	
	/**
	 * Used internally to manage the "What's New" screen
	 * 
	 * @param adapter
	 * @param items
	 * @param more
	 * @return whether or not more items can be added
	 */
	protected static boolean addWhatsNewItem(ListView list, ArrayAdapter<String> adapter, Stack<String> items, Button more, boolean fullScroll) {
		//Don't try if we're out of items
		if (items.empty()) {
			more.setVisibility(View.GONE);
			return false;
		}
		
		//Add another items
		adapter.add(items.pop());
		
		if (fullScroll) {
			//Scroll all the way down
			list.smoothScrollToPosition(adapter.getCount());
		}
		
		//Are there more items to add?
		if (items.empty()) {
			more.setVisibility(View.GONE);
			return false;
		} else {
			more.setVisibility(View.VISIBLE);
			return true;
		}
	}
	
	protected static Stack<String> getReleaseNotesStack(Context context) {
		String fullText = APDE.readAssetFile(context, "whatsnew.txt");
		// File is seperated human-readably...
		List<String> releaseNotes = Arrays.asList(
		fullText.split("(\\r\\n|\\n|\\r)------------------------------------------------------------------------(\\r\\n|\\n|\\r)"));
		// Remove newlines at beginning and end
		for (int i = 0; i < releaseNotes.size(); i ++) {
			releaseNotes.set(i, releaseNotes.get(i).trim());
		}
		
		// Read most recent first
		Collections.reverse(releaseNotes);
		// Make into a stack so that we can peel off items one at a time
		final Stack<String> releaseNotesStack = new Stack<String>();
		releaseNotesStack.addAll(releaseNotes);
		
		return releaseNotesStack;
	}
	
	public TabLayout getCodeTabStrip() {
		return codeTabStrip;
	}
	
	public ViewPager getCodePager() {
		return codePager;
	}
	
	public ScrollView getConsoleScroller() {
		return (ScrollView) findViewById(R.id.console_scroller);
	}
	
	public void setExtraHeaderView(View headerView) {
		extraHeaderView = headerView;
	}
	
	public View getExtraHeaderView() {
		return extraHeaderView;
	}
    
    @Override
    protected void onSaveInstanceState(Bundle icicle) {
    	//Save the selected tab
    	icicle.putInt("selected_tab", getSelectedCodeIndex());
    	
    	SketchFile[] tabMetas = new SketchFile[tabs.size()];
//    	int count = 0;
    	
//    	for (Map.Entry<ActionBar.Tab, SketchFile> entry : tabs.entrySet()) {
//    		int num = tabBar.indexOfTab(entry.getKey());
//    		SketchFile meta = entry.getValue();
//    		
//    		meta.tabNum = num;
//    		
//    		tabMetas[count] = meta;
//    		count ++;
//    	}
		
		for (int i = 0; i < tabs.size(); i ++) {
			SketchFile sketchFile = tabs.get(i);
			sketchFile.tabNum = i;
			tabMetas[i] = sketchFile;
		}
    	
    	//We have to right a map... this seems to be unnecessarily difficult
    	icicle.putParcelableArray("tabs", getTabMetas());
    }
    
    @Override
    protected void onRestoreInstanceState(Bundle icicle) {
    	if (icicle != null) {
    		//Restore the selected tab (this should only happen when the screen rotates)
    		selectCode(icicle.getInt("selected_tab"));
    		
    		//Refresh the syntax highlighter AGAIN so that it can take into account the restored selected tab
    		//The tokens end up getting refreshed 3+ times on a rotation... but it doesn't seem to have much of an impact on performance, so it's fine for now
//			getSelectedCodeArea().flagRefreshTokens();
			
			Parcelable[] tabMetaParcels = icicle.getParcelableArray("tabs");
			
			//Fix an all-too-common crash report that hides the stack trace that we really want
			loadTabs:
			if (tabMetaParcels instanceof SketchFile[]) {
				SketchFile[] tabMetas = (SketchFile[]) tabMetaParcels;
				
				if (tabs.size() > 0 && tabMetas[0].equals(tabs.get(0))) {
					break loadTabs;
				}
				
				for (SketchFile tabMeta : tabMetas) {
//					tabs.put(tabBar.getTab(tabMeta.tabNum), tabMeta);
					addTab(tabMeta);
				}
			} else {
				System.err.println("Error occurred restoring state, likely caused by\na crash in an activity further down the hierarchy");
			}
    	}
    }
    
    private static SparseArray<ActivityResultCallback> activityResultCodes = new SparseArray<ActivityResultCallback>();
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    	//This is the code to delete the old APK file
    	if (requestCode == FLAG_DELETE_APK) {
    		Build.cleanUpPostLaunch(this);
    	}
    	
    	ActivityResultCallback action = activityResultCodes.get(requestCode);
    	
    	if (action != null) {
    		action.onActivityResult(requestCode, resultCode, data);
    		
    		activityResultCodes.remove(requestCode);
    	}
    }
    
    public void selectFile(int titleResId, int requestCode, ActivityResultCallback callback) {
    	selectFile(getResources().getString(titleResId), requestCode, callback);
    }
    
    public void selectFile(String title, int requestCode, ActivityResultCallback callback) {
    	activityResultCodes.put(requestCode, callback);
    	
    	Intent intent = Intent.createChooser(FileUtils.createGetContentIntent(), title);
	    startActivityForResult(intent, requestCode);
    }
    
    public interface ActivityResultCallback {
    	void onActivityResult(int requestCode, int resultCode, Intent data);
    }
    
    @SuppressWarnings("deprecation")
	@SuppressLint("NewApi")
	public void onResume() {
    	super.onResume();
		
    	//Reference the SharedPreferences text size value
//    	((CodeEditText) findViewById(R.id.code)).refreshTextSize();
		((TextView) findViewById(R.id.console)).setTextSize(Integer.parseInt(PreferenceManager.getDefaultSharedPreferences(this).getString("textsize_console", "14")));
    	
    	//Disable / enable the soft keyboard
        if(PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getBoolean("use_hardware_keyboard", false))
        	getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        else
        	getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
        
        //Update the syntax highlighter
//		getSelectedCodeArea().updateTokens();
        
		//Make the character insert toggle button square
        final View charInsertToggle = findViewById(R.id.toggle_char_inserts);
        charInsertToggle.setPadding(0, 0, 0, 0);
        charInsertToggle.requestLayout();
        charInsertToggle.post(new Runnable() {
        	public void run() {
        		charInsertToggle.setLayoutParams(new LinearLayout.LayoutParams(charInsertToggle.getHeight(), charInsertToggle.getHeight()));
        		
        		//Hide the button (default, keyboard not visible...)
                charInsertToggle.setVisibility(View.GONE);
        	}
        });
        
        //Correctly size the code and console areas
        
        int minWidth;
		int maxWidth;
		
		Point point = new Point();
		getWindowManager().getDefaultDisplay().getSize(point);
		maxWidth = point.x;
		
		//Remove padding
		minWidth = maxWidth - (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10, getResources().getDisplayMetrics()) * 2;
		
		//Make sure that the EditText is wide enough
//		findViewById(R.id.code).setMinimumWidth(minWidth);
		findViewById(R.id.console).setMinimumWidth(minWidth);
		
//		findViewById(R.id.code_scroller_x).setLayoutParams(new android.widget.ScrollView.LayoutParams(maxWidth, android.widget.ScrollView.LayoutParams.MATCH_PARENT));
//		findViewById(R.id.console_scroller_x).setLayoutParams(new android.widget.ScrollView.LayoutParams(maxWidth, android.widget.ScrollView.LayoutParams.MATCH_PARENT));
		
		//Let's see if the user is trying to open a .PDE file...
		
		Intent intent = getIntent();
        
        if(intent.getAction().equals(Intent.ACTION_VIEW) && intent.getType() != null) {
        	String scheme = intent.getData().getScheme();
        	String filePath = intent.getData().getPath();
        	
        	//Let's make sure we don't have any bad data...
        	if(scheme != null && scheme.equalsIgnoreCase("file") && filePath != null) {
        		//Try to get the file...
        		File file = new File(filePath);
        		
        		String ext = "";
        		int lastDot = filePath.lastIndexOf('.');
    			if(lastDot != -1) {
    				ext = filePath.substring(lastDot);
    			}
    			
        		//Is this a good file?
        		if(file.exists() && !file.isDirectory() && ext.equalsIgnoreCase(".pde")) {
        			//Let's get the sketch folder...
        			File sketchFolder = file.getParentFile();
        			
        			//Here goes...
        			loadSketch(sketchFolder.getAbsolutePath(), APDE.SketchLocation.EXTERNAL);
        			
        			message("Loaded external sketch.");
        		}
        	}
        }
        
        //Make Processing 3.0 behave properly
        getGlobalState().initProcessingPrefs();
        
        //Register receiver for sketch logs / console output
        registerReceiver(consoleBroadcastReceiver, new IntentFilter("com.calsignlabs.apde.LogBroadcast"));
        
        //In case the user has enabled / disabled undo / redo in settings
        supportInvalidateOptionsMenu();
	}
    
    public HashMap<String, KeyBinding> getKeyBindings() {
    	return keyBindings;
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
    		
    		//Load the list of possible key modifiers
    		String modifiers = binding.getString("mod");
    		String[] mods = modifiers.split("\\|"); // "|" is a REGEX keyword... need to escape it
    		
    		//The possible modifiers (these are available in API level 11+)
    		boolean ctrl = false;
    		boolean meta = false;
    		boolean func = false;
    		
    		// (and these are available across all API levels)
    		boolean alt = false;
    		boolean sym = false;
    		boolean shift = false;
    		
    		//This isn't very elegant... but it gets the job done
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
    	
    	boolean ctrl = event.isCtrlPressed();
		boolean meta = event.isMetaPressed();
		boolean func = event.isFunctionPressed();
		
		boolean alt = event.isAltPressed();
    	boolean sym = event.isSymPressed();
    	boolean shift = event.isShiftPressed();
    	
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
    	
    	if(keyBindings.get("new_tab").matches(key, ctrl, meta, func, alt, sym, shift)) {
    		if(!getGlobalState().isExample())
    			addTabWithDialog();
    		return true;
    	}
    	if(keyBindings.get("delete_tab").matches(key, ctrl, meta, func, alt, sym, shift)) {
    		if(!getGlobalState().isExample())
    			deleteTab();
    		return true;
    	}
    	if(keyBindings.get("rename_tab").matches(key, ctrl, meta, func, alt, sym, shift)) {
    		if(!getGlobalState().isExample())
    			renameTab();
    		return true;
    	}
    	
    	if(keyBindings.get("undo").matches(key, ctrl, meta, func, alt, sym, shift)) {
    		if (!getGlobalState().isExample() && getCodeCount() > 0 && PreferenceManager.getDefaultSharedPreferences(this).getBoolean("pref_key_undo_redo", true)) {
    			tabs.get(getSelectedCodeIndex()).undo(this);
    		}
    		return true;
    	}
    	if(keyBindings.get("redo").matches(key, ctrl, meta, func, alt, sym, shift)) {
    		if (!getGlobalState().isExample() && getCodeCount() > 0 && PreferenceManager.getDefaultSharedPreferences(this).getBoolean("pref_key_undo_redo", true)) {
    			tabs.get(getSelectedCodeIndex()).redo(this);
    		}
    		
    		return true;
    	}
    	
    	//Handle tool keyboard shortcuts
    	//TODO Potential conflicts... for now, two tools with the same shortcut will both run
    	
    	KeyBinding press = new KeyBinding("press", key, ctrl, meta, func, alt, sym, shift);
    	boolean toolShortcut = false;
    	
    	for(Tool tool : getGlobalState().getTools()) {
    		KeyBinding toolBinding = tool.getKeyBinding();
    		
    		if(toolBinding != null && toolBinding.matches(press)) {
    			tool.run();
    			toolShortcut = true;
    		}
    	}
    	
    	if(toolShortcut) {
    		return true;
    	}
    	
		return super.onKeyDown(key, event);
    }
    
    /**
     * Saves the current sketch and sets up the editor with a blank sketch, from the context of the editor.
     */
    public void createNewSketch() {
		//Save the sketch
		autoSave();
		
		//Update the global state
		getGlobalState().selectNewTempSketch();
		
		//Set up for a new sketch
		newSketch();
		//Reload the navigation drawer
		forceDrawerReload();
		
		//Update the action bar title
		getSupportActionBar().setTitle(getGlobalState().getSketchName());
    }
	
	/**
	 * Open the rename sketch AlertDialog
	 */
	public void launchRenameSketch() {
		AlertDialog.Builder alert = new AlertDialog.Builder(this);
		
		alert.setTitle(String.format(Locale.US, getResources().getString(R.string.rename_sketch_title), getGlobalState().getSketchName()));
		alert.setMessage(R.string.rename_sketch_message);
		
		final EditText input = getGlobalState().createAlertDialogEditText(this, alert, getGlobalState().getSketchName(), true);
		
		alert.setPositiveButton(R.string.rename, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton) {
				String sketchName = input.getText().toString();
				
				if (validateSketchName(sketchName) && !sketchName.equals(getGlobalState().getSketchName())) {
					getGlobalState().setSketchName(sketchName);
					
					APDE.SketchMeta source = new APDE.SketchMeta(getGlobalState().getSketchLocationType(), getGlobalState().getSketchPath());
					APDE.SketchMeta dest = new APDE.SketchMeta(source.getLocation(), source.getParent() + "/" + sketchName);
					
					getGlobalState().moveFolder(source, dest, EditorActivity.this);
				}
				
				if (!PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getBoolean("use_hardware_keyboard", false)) {
					((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(input.getWindowToken(), 0);
				}
			}
		});
		
		alert.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton) {
				if (!PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getBoolean("use_hardware_keyboard", false)) {
					((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(input.getWindowToken(), 0);
				}
			}
		});
		
		// Show the soft keyboard if the hardware keyboard is unavailable (hopefully)
		AlertDialog dialog = alert.create();
		if (!PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getBoolean("use_hardware_keyboard", false)) {
			dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
		}
		dialog.show();
	}
    
    /**
     * Called when the user selects "Load Sketch" - this will open the navigation drawer
     */
    private void loadSketch() {
    	//Opens the navigation drawer
    	
		DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer);
		RelativeLayout drawerLayout = (RelativeLayout) findViewById(R.id.drawer_wrapper);
		
		drawer.openDrawer(drawerLayout);
	}
    
    public int getSketchCount() {
    	//Get the location of the sketchbook
    	File sketchbookLoc = getGlobalState().getSketchbookFolder();
    	
    	int count = 0;
    	
    	//If the sketchbook exists
    	if(sketchbookLoc.exists()) {
    		//Get a list of sketches
	    	File[] folders = sketchbookLoc.listFiles();
	    	for(File folder : folders)
	    		if(folder.isDirectory())
	    			//Count this sketch
	    			count ++;
    	}
    	
    	return count;
    }
    
    protected int drawerIndexOfSketch(String name) {
    	//Get the location of the sketchbook
    	File sketchbookLoc = getGlobalState().getSketchbookFolder();
    	
    	int index = 1;
    	
    	//If the sketchbook exists
    	if(sketchbookLoc.exists()) {
    		//Get a list of sketches
	    	File[] folders = sketchbookLoc.listFiles();
	    	//Sort the list of sketches alphabetically
	    	Arrays.sort(folders);
	    	
	    	for(File folder : folders) {
	    		if(folder.isDirectory()) {
	    			//Check to see if this is the sketch
	    			if(folder.getName().equals(name))
	    				return index;
	    			
	    			//Increment the counter
	    			index ++;
	    		}
	    	}
    	}
    	
    	return -1;
    }
    
	//http://stackoverflow.com/questions/16983989/copy-directory-from-assets-to-data-folder
    protected static boolean copyAssetFolder(AssetManager assetManager, String fromAssetPath, String toPath) {
    	try {
    		String[] files = assetManager.list(fromAssetPath);
    		new File(toPath).mkdirs();
    		boolean res = true;
    		for(String file : files)
    			if(file.contains("."))
    				res &= copyAsset(assetManager, fromAssetPath + "/" + file, toPath + "/" + file);
    			else 
    				res &= copyAssetFolder(assetManager, fromAssetPath + "/" + file, toPath + "/" + file);

    		return res;
    	} catch (Exception e) {
    		e.printStackTrace();
    		return false;
    	}
    }
    
	private static boolean copyAsset(AssetManager assetManager, String fromAssetPath, String toPath) {
		InputStream in = null;
		OutputStream out = null;
		try {
			in = assetManager.open(fromAssetPath);
			new File(toPath).createNewFile();
			out = new FileOutputStream(toPath);
			copyFile(in, out);
			in.close();
			in = null;
			out.flush();
			out.close();
			out = null;

			return true;
		} catch(Exception e) {
			e.printStackTrace();
			return false;
		}
	}
	
	private static void copyFile(InputStream in, OutputStream out) throws IOException {
		byte[] buffer = new byte[1024];
		int read;
		while((read = in.read(buffer)) != -1)
			out.write(buffer, 0, read);
	}
	
	@Override
	public void onPause() {
		// Make sure to save the sketch
		saveSketchForStop();
		
		// We do this to avoid messing up the *very* delicate console/code area resizing stuff
		getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
		
		super.onPause();
	}
	
	@Override
	public void onStop() {
		// Make sure to save the sketch
		saveSketchForStop();
		
		super.onStop();
	}
	
	@Override
	public void onDestroy() {
		//Unregister the log / console receiver
    	unregisterReceiver(consoleBroadcastReceiver);
    	
    	super.onDestroy();
	}
	
	/**
	 * Saves the sketch for when the activity is closing
	 */
	public void saveSketchForStop() {
		//Automatically save
		autoSave();
    	
		//Store sketch info in private storage TODO make this SharedPreferences instead
		
		//Save the relative path to the current sketch
		String sketchPath = getGlobalState().getSketchPath();
		writeTempFile("sketchPath.txt", sketchPath);
    	
    	//Save the location of the current sketch
		String sketchLocation = getGlobalState().getSketchLocationType().toString();
		writeTempFile("sketchLocation.txt", sketchLocation);
	}
	
	/**
	 * Loads the temporary sketch for the start of the app
	 * 
	 * @return success
	 */
	public boolean loadSketchStart() {
		try {
			//Load the temp files
			String sketchPath = readTempFile("sketchPath.txt");
			APDE.SketchLocation sketchLocation = APDE.SketchLocation.fromString(readTempFile("sketchLocation.txt"));
			
			return loadSketch(sketchPath, sketchLocation);
		} catch(Exception e) { //Meh...
			return false;
		}
	}
	
	/**
	 * Reload the current sketch... without saving.
	 * Useful for updating files that have been changed outside of the editor.
	 */
	public void reloadSketch() {
		loadSketch(getGlobalState().getSketchPath(), getGlobalState().getSketchLocationType());
	}
	
	/**
	 * Automatically save the sketch, whether it is to the sketchbook folder or to the temp folder
	 */
	public void autoSave() {
		switch(getGlobalState().getSketchLocationType()) {
		case EXAMPLE:
		case LIBRARY_EXAMPLE:
			//Don't need to save examples...
			break;
		case SKETCHBOOK:
		case EXTERNAL:
		case TEMPORARY:
			saveSketch();
			break;
		}
	}
	
	/**
	 * Sets up the editor for a new sketch
	 */
	public void newSketch() {
		//Set the title of the action bar
		getSupportActionBar().setTitle(getGlobalState().getSketchName());
		//Reload the action bar actions and overflow
		supportInvalidateOptionsMenu();
		
		//Get rid of any existing tabs
//		tabBar.removeAllTabs();
		tabs.clear();
		codePagerAdapter.notifyDataSetChanged();
		
		//Add the default "sketch" tab
		addDefaultTab(APDE.DEFAULT_SKETCH_TAB);
		
		//Select the new tab
		selectCode(0);
		
		//Clear the code area
//		CodeEditText code = ((CodeEditText) findViewById(R.id.code));
//		code.setText(""); //We don't use setNoUndoText() because it screws up the undo history...
		
		//Fix a strange bug...
		getSelectedSketchFile().clearUndoRedo();
		
		//Get rid of previous syntax highlighter data
//		code.clearTokens();
//		
//		//Make sure the code area is editable
//		code.setFocusable(true);
//		code.setFocusableInTouchMode(true);
		
		// Save the new sketch
		autoSave();
		// Add to recents
		getGlobalState().putRecentSketch(getGlobalState().getSketchLocationType(), getGlobalState().getSketchPath());
		
		// Reload the navigation drawer
		forceDrawerReload();
	}
    
	/**
	 * Loads a sketch
	 * 
	 * @param sketchPath
	 * @param sketchLocation
	 * @return success
	 */
	public boolean loadSketch(String sketchPath, APDE.SketchLocation sketchLocation) {
		if (sketchLocation == null) {
			// Something bad happened
			return false;
		}
		
		// Get the sketch location
    	File sketchLoc = getGlobalState().getSketchLocation(sketchPath, sketchLocation);
    	boolean success;
    	
    	// Ensure that the sketch folder exists and is a directory
    	if (sketchLoc.exists() && sketchLoc.isDirectory()) {
    		getGlobalState().selectSketch(sketchPath, sketchLocation);
    		
    		// Get all the files in the directory
    		File[] files = sketchLoc.listFiles();
    		
    		// Why do we need this...?
    		for (SketchFile meta : tabs) {
				meta.disable();
			}
    		
    		// Get rid of any tabs
    		tabs.clear();
//    		tabBar.removeAllTabs();
    		// This method is necessary, too
			
    		// Cycle through the files
    		for (File file : files) {
    			// Split the filename into prefix and suffix
    			String[] folders = file.getPath().split("/");
    			String[] parts = folders[folders.length - 1].split("\\.");
    			// If the filename isn't formatted properly, skip this file
    			if(parts.length != 2)
    				continue;
    			
    			// Retrieve the prefix and suffix
    			String prefix = parts[parts.length - 2];
    			String suffix = parts[parts.length - 1];
    			
    			// Check to see if it's a .PDE file
    			if (suffix.equals("pde")) {
    				// Build a Tab Meta object
    				SketchFile meta = new SketchFile("");
    				meta.readData(file.getAbsolutePath());
    				meta.setTitle(prefix);
					meta.setExample(getGlobalState().isExample());
					
					// Add the tab
					addTabWithoutPagerUpdate(meta);
					
					meta.getFragment().setSketchFile(meta);
    			} else if (suffix.equals("java")) {
    				// Build a Tab Meta object
    				SketchFile meta = new SketchFile("");
    				meta.readData(file.getAbsolutePath());
    				meta.setTitle(prefix);
    				meta.setSuffix(".java");
					meta.setExample(getGlobalState().isExample());
				
					// Add the tab
					addTabWithoutPagerUpdate(meta);
				
					meta.getFragment().setSketchFile(meta);
    			}
    		}
		
			codePagerAdapter.notifyDataSetChanged();
    		
//    		// Update the code area
//    		if(getCodeCount() > 0)
//    			((CodeEditText) findViewById(R.id.code)).setNoUndoText(tabs.get(getSelectedCodeIndex()).getText());
//    		else
//    			((CodeEditText) findViewById(R.id.code)).setNoUndoText("");
    		
			
    		// Get rid of previous syntax highlighter data
//    		((CodeEditText) findViewById(R.id.code)).clearTokens();
    		
    		success = true;
    		
    		// Automatically selects and loads the new tab
//    		tabBar.selectLoadDefaultTab();
			selectCode(0);
    	} else {
    		success = false;
    	}
    	
    	if(success) {
			// Close Find/Replace (if it's open)
			((FindReplace) getGlobalState().getPackageToToolTable().get(FindReplace.PACKAGE_NAME)).close();
			
//    		if(getGlobalState().isExample()) {
//    			//Make sure the code area isn't editable
//        		((CodeEditText) findViewById(R.id.code)).setFocusable(false);
//        		((CodeEditText) findViewById(R.id.code)).setFocusableInTouchMode(false);
//    		} else {
//    			if(getCodeCount() > 0) {
//    				//Make sure the code area is editable
//    				((CodeEditText) findViewById(R.id.code)).setFocusable(true);
//    				((CodeEditText) findViewById(R.id.code)).setFocusableInTouchMode(true);
//    			} else {
//    				//Make sure that the code area isn't editable
//    				((CodeEditText) findViewById(R.id.code)).setFocusable(false);
//    				((CodeEditText) findViewById(R.id.code)).setFocusableInTouchMode(false);
//    			}
//    		}
    		
    		//Add this to the recent sketches
    		getGlobalState().putRecentSketch(sketchLocation, sketchPath);
    	}
    	
    	return success;
	}
    
    /**
     * Saves the sketch to the sketchbook folder, creating a new subdirectory if necessary
     */
    public void saveSketch() {
    	//If we cannot write to the external storage (and the user wants to), make sure to inform the user
    	if(!externalStorageWritable() && (getGlobalState().getSketchbookDrive().type.equals(APDE.StorageDrive.StorageDriveType.EXTERNAL)
				|| getGlobalState().getSketchbookDrive().type.equals(APDE.StorageDrive.StorageDriveType.PRIMARY_EXTERNAL))) {
    		AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(getResources().getText(R.string.external_storage_dialog_title))
            	.setMessage(getResources().getText(R.string.external_storage_dialog_message)).setCancelable(false)
            	.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            		@Override
            		public void onClick(DialogInterface dialog, int which) {}
            }).show();
            
    		return;
    	}
    	
    	boolean success = true;
    	
    	String sketchPath = getGlobalState().getSketchPath();
    	
    	//Obtain the location of the sketch
    	File sketchLoc = getGlobalState().getSketchLocation(sketchPath, getGlobalState().getSketchLocationType());
    	
    	//Ensure that the sketch folder exists
    	sketchLoc.mkdirs();
    	
    	if (getCodeCount() > 0) {
//	    	//Update the current tab
//	    	tabs.put(tabBar.getSelectedTab(), new SketchFile(tabBar.getSelectedTab().getText().toString(), this));
//	    	tabs.get(getSelectedCodeIndex()).update(this, PreferenceManager.getDefaultSharedPreferences(this).getBoolean("pref_key_undo_redo", true));
	    	
			// Update all tabs
			for (int i = 0; i < tabs.size(); i ++) {
				// Not all of the tabs are loaded at once
				if (tabs.get(i).getFragment().getCodeEditText() != null) {
					tabs.get(i).update(this, getGlobalState().getPref("pref_key_undo_redo", true));
				}
			}
			
	    	// Iterate through the SketchFiles...
	    	for(SketchFile meta : tabs) {
	    		if (meta.enabled()) {
	    			// ...and write them to the sketch folder
	    			if (!meta.writeData(sketchLoc.getPath() + "/")) {
	    				success = false;
	    			}
	    		}
	    	}
	    	
	    	if (success) {
	    		getGlobalState().selectSketch(sketchPath, getGlobalState().getSketchLocationType());
	    		
	    		// Force the drawer to reload
	    		forceDrawerReload();
	    		
	    		supportInvalidateOptionsMenu();
	            
	            // Inform the user of success
	    		message(getResources().getText(R.string.sketch_saved));
	    		setSaved(true);
	    	} else {
	    		// Inform the user of failure
	    		error(getResources().getText(R.string.sketch_save_failure));
	    	}
    	} else {
    		// If there are no tabs
    		// TODO is this right?
    		
    		// Force the drawer to reload
    		forceDrawerReload();
    		
            // Inform the user
    		message(getResources().getText(R.string.sketch_saved));
    		setSaved(true);
    	}
    }
    
    public void copyToSketchbook() {
    	//If we cannot write to the external storage (and the user wants to), make sure to inform the user
		if(!externalStorageWritable() && (getGlobalState().getSketchbookDrive().type.equals(APDE.StorageDrive.StorageDriveType.EXTERNAL)
				|| getGlobalState().getSketchbookDrive().type.equals(APDE.StorageDrive.StorageDriveType.PRIMARY_EXTERNAL))) {
    		AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(getResources().getText(R.string.external_storage_dialog_title))
            	.setMessage(getResources().getText(R.string.external_storage_dialog_message)).setCancelable(false)
            	.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            		@Override
            		public void onClick(DialogInterface dialog, int which) {}
            }).show();
            
    		return;
    	}
    	
    	//The old example location
    	File oldLoc = getGlobalState().getSketchLocation();
    	
    	//Get the sketch name so that we place the copied example at the root of sketchbook
    	String sketchPath = "/" + getGlobalState().getSketchName();
    	
    	//Obtain the location of the sketch
    	//Save examples to the sketchbook so that the user can copy examples to the sketchbook
    	File sketchLoc = getGlobalState().getSketchLocation(sketchPath, APDE.SketchLocation.SKETCHBOOK);
    	
    	//Ensure that the sketch folder exists
    	sketchLoc.mkdirs();
    	
    	try {
    		APDE.copyFile(oldLoc, sketchLoc);
    		
    		//We need to add it to the recent list
    		getGlobalState().putRecentSketch(APDE.SketchLocation.SKETCHBOOK, sketchPath);
    		
    		getGlobalState().selectSketch(sketchPath, APDE.SketchLocation.SKETCHBOOK);
    		
    		//Make sure the code area is editable
//			((CodeEditText) findViewById(R.id.code)).setFocusable(true);
//			((CodeEditText) findViewById(R.id.code)).setFocusableInTouchMode(true);
    		
    		//Force the drawer to reload
    		forceDrawerReload();
    		
    		supportInvalidateOptionsMenu();
			
			updateCodeAreaFocusable();
            
            //Inform the user of success
    		message(getResources().getText(R.string.sketch_saved));
    		setSaved(true);
    	} catch (IOException e) {
    		//Inform the user of failure
    		error(getResources().getText(R.string.sketch_save_failure));
    	}
    }
	
	public void updateCodeAreaFocusable() {
		for (SketchFile sketchFile : tabs) {
			sketchFile.setExample(false);
			sketchFile.forceReloadTextIfInitialized();
		}
	}
	
	public void moveToSketchbook() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		
		builder.setTitle(R.string.move_temp_to_sketchbook_title);
		builder.setMessage(String.format(Locale.US, getResources().getString(R.string.move_temp_to_sketchbook_message), getGlobalState().getSketchName()));
		
		builder.setPositiveButton(R.string.move, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				APDE.SketchMeta source = new APDE.SketchMeta(getGlobalState().getSketchLocationType(), getGlobalState().getSketchPath());
				APDE.SketchMeta dest = new APDE.SketchMeta(APDE.SketchLocation.SKETCHBOOK, "/" + source.getName());
				
				// Let's not overwrite anything...
				// TODO Maybe give the user options to replace / keep both in the new location?
				// We don't need that much right now, they can deal with things manually...
				if (getGlobalState().getSketchLocation(dest.getPath(), dest.getLocation()).exists()) {
					AlertDialog.Builder builder = new AlertDialog.Builder(EditorActivity.this);
					
					builder.setTitle(R.string.cannot_move_sketch_title);
					builder.setMessage(R.string.cannot_move_folder_message);
					
					builder.setPositiveButton(getResources().getString(R.string.ok), new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {}
					});
					
					builder.create().show();
					
					return;
				}
				
				getGlobalState().moveFolder(source, dest, EditorActivity.this);
				supportInvalidateOptionsMenu();
			}
		});
		
		builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton) {}
		});
		
		builder.create().show();
	}
    
    //Copy all of the old preferences over to the new SharedPreferences and delete the old ones
  	public void copyPrefs(String before, String after) {
  		SharedPreferences old = getSharedPreferences(getGlobalState().getSketchName(), 0);
  		SharedPreferences prefs = getSharedPreferences(after, 0);
  		SharedPreferences.Editor ed = prefs.edit();
  		
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
    
    private boolean validateSketchName(String name) {
    	//Make sure that the name contains text
		if(name.length() <= 0) {
			error(getResources().getText(R.string.sketch_name_invalid_no_char));
			return false;
		}
		
		//Check to make sure that the first character isn't a number and isn't an underscore
		char first = name.charAt(0);
		if((first >= '0' && first <= '9') || first == '_') {
			error(getResources().getText(R.string.sketch_name_invalid_first_char));
			return false;
		}
		
		//Check all of the characters
		for(int i = 0; i < name.length(); i ++) {
			char c = name.charAt(i);
			if(c >= '0' && c <= '9') continue;
			if(c >= 'a' && c <= 'z') continue;
			if(c >= 'A' && c <= 'Z') continue;
			if(c == '_') continue;
			
			error(getResources().getText(R.string.sketch_name_invalid_char));
			return false;
		}
		
		return true;
	}
	
	public void launchDeleteSketchConfirmationDialog() {
		AlertDialog.Builder alert = new AlertDialog.Builder(this);
		
		alert.setTitle(String.format(Locale.US, getResources().getString(R.string.delete_sketch_dialog_title), getGlobalState().getSketchName()));
		alert.setMessage(String.format(Locale.US, getResources().getString(R.string.delete_sketch_dialog_message), getGlobalState().getSketchName()));
		
		alert.setPositiveButton(R.string.delete, new DialogInterface.OnClickListener() {
			@SuppressLint("NewApi")
			public void onClick(DialogInterface dialog, int whichButton) {
				deleteSketch();
				
				getGlobalState().selectNewTempSketch();
				newSketch();
				
				toolbar.setTitle(getGlobalState().getSketchName());
			}
		});
		
		alert.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton) {}
		});
		
		alert.create().show();
	}
    
    /**
     * Deletes the current sketch
     */
    public void deleteSketch() {
    	//Obtain the location of the sketch folder
    	File sketchFolder = getGlobalState().getSketchLocation();
    	if(sketchFolder.isDirectory()) {
    		try {
    			//Perform recursive file deletion on the sketch folder
				APDE.deleteFile(sketchFolder);
			} catch (IOException e) {
				//Catch stuff, shouldn't be any problems, though
				e.printStackTrace();
			}
    	}
    }
    
    /**
     * Write text to a temp file
     * 
     * @param filename
     * @param text
     * @return success
     */
    public boolean writeTempFile(String filename, String text) {
    	BufferedOutputStream outputStream = null;
		boolean success;
		
		try {
			//Open the output stream
			outputStream = new BufferedOutputStream(openFileOutput(filename, Context.MODE_PRIVATE));
			//Write the data to the output stream
			outputStream.write(text.getBytes());
			
			success = true;
		} catch(Exception e) {
			e.printStackTrace();
			
			success = false;
			
		} finally {
			//Make sure to close the stream
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
    
    /**
     * Reads text from a temp file
     * 
     * @param filename
     * @return the text
     */
    public String readTempFile(String filename) {
    	BufferedInputStream inputStream = null;
    	String output = "";
    	
		try {
			//Open the input stream
			inputStream = new BufferedInputStream(openFileInput(filename));
			
			byte[] contents = new byte[1024];
			int bytesRead = 0;
			
			//Read the contents of the file
			while((bytesRead = inputStream.read(contents)) != -1)
				output += new String(contents, 0, bytesRead);
		} catch(Exception e) {
			//... nothing much to do here
		} finally {
			//Make sure to close the stream
			try {
				if(inputStream != null)
					inputStream.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
    	
    	return output;
    }
    
    /**
     * Reloads the navigation drawer
     */
    public void forceDrawerReload() {
        final ListView drawerList = (ListView) findViewById(R.id.drawer_list);
        
        ArrayList<FileNavigatorAdapter.FileItem> items;
        
        if(drawerSketchLocationType != null) {
        	items = getGlobalState().listSketchContainingFolders(getGlobalState().getSketchLocation(drawerSketchPath, drawerSketchLocationType), new String[] {APDE.LIBRARIES_FOLDER},
        			drawerSketchPath.length() > 0, drawerSketchLocationType.equals(APDE.SketchLocation.SKETCHBOOK) || drawerSketchLocationType.equals(APDE.SketchLocation.TEMPORARY),
					new APDE.SketchMeta(drawerSketchLocationType, drawerSketchPath));
        } else {
        	if(drawerRecentSketch) {
        		items = getGlobalState().listRecentSketches();
        	} else {
        		items = new ArrayList<FileNavigatorAdapter.FileItem>();
        		items.add(new FileNavigatorAdapter.FileItem(getResources().getString(R.string.sketches), FileNavigatorAdapter.FileItemType.FOLDER));
        		items.add(new FileNavigatorAdapter.FileItem(getResources().getString(R.string.examples), FileNavigatorAdapter.FileItemType.FOLDER));
        		items.add(new FileNavigatorAdapter.FileItem(getResources().getString(R.string.library_examples), FileNavigatorAdapter.FileItemType.FOLDER));
				items.add(new FileNavigatorAdapter.FileItem(getResources().getString(R.string.temporary), FileNavigatorAdapter.FileItemType.FOLDER));
        		items.add(new FileNavigatorAdapter.FileItem(getResources().getString(R.string.recent), FileNavigatorAdapter.FileItemType.FOLDER));
        	}
        }
        
        int selected = -1;
		
        if(drawerSketchLocationType != null && drawerSketchLocationType.equals(getGlobalState().getSketchLocationType())
        		&& getGlobalState().getSketchPath().equals(drawerSketchPath + "/" + getGlobalState().getSketchName())) {
        	
        	selected = FileNavigatorAdapter.indexOfSketch(items, getGlobalState().getSketchName());
        } else if(drawerRecentSketch && !(getGlobalState().getRecentSketches().size() == 0)) {
        	// In the recent screen, the top-most sketch is always currently selected...
        	// It's not "0" because that's the UP button
        	selected = 1;
        }
        
        final FileNavigatorAdapter fileAdapter = new FileNavigatorAdapter(this, items, selected);
        
        // Load the list of sketches into the drawer
        drawerList.setAdapter(fileAdapter);
        drawerList.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        
        // Let the adapter handle long click events
        // Used for drag and drop to move files...
        drawerList.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
			@Override
			public boolean onItemLongClick(AdapterView<?> adapterView, View view, int position, long id) {
				return fileAdapter.onLongClickItem(view, position);
			}
		});
    }
    
    public void setDrawerLocation(APDE.SketchMeta folder) {
    	drawerSketchLocationType = folder.getLocation();
    	drawerSketchPath = folder.getPath();
    	
    	forceDrawerReload();
    }
    
    /**
     * @return the APDE application global state
     */
    public APDE getGlobalState() {
    	return (APDE) getApplication();
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_editor, menu);
        
        if(drawerOpen) {
        	// If the drawer is visible
        	
        	// Make sure to hide all of the sketch-specific action items
        	menu.findItem(R.id.menu_run).setVisible(false);
        	menu.findItem(R.id.menu_stop).setVisible(false);
        	menu.findItem(R.id.menu_undo).setVisible(false);
        	menu.findItem(R.id.menu_redo).setVisible(false);
        	menu.findItem(R.id.menu_tab_delete).setVisible(false);
        	menu.findItem(R.id.menu_tab_rename).setVisible(false);
        	menu.findItem(R.id.menu_save).setVisible(false);
			menu.findItem(R.id.menu_delete).setVisible(false);
			menu.findItem(R.id.menu_rename).setVisible(false);
        	menu.findItem(R.id.menu_copy_to_sketchbook).setVisible(false);
			menu.findItem(R.id.menu_move_to_sketchbook).setVisible(false);
        	menu.findItem(R.id.menu_new).setVisible(false);
        	menu.findItem(R.id.menu_load).setVisible(false);
        	menu.findItem(R.id.menu_tab_new).setVisible(false);
        	menu.findItem(R.id.menu_tools).setVisible(false);
        	menu.findItem(R.id.menu_sketch_properties).setVisible(false);
        	
        	// Make sure to hide the sketch name
        	getSupportActionBar().setTitle(R.string.app_name);
        } else {
        	if (getCodeCount() > 0) {
        		// If the drawer is closed and there are tabs
        		
        		// Make sure to make the tab actions visible
            	menu.findItem(R.id.menu_run).setVisible(true);
            	menu.findItem(R.id.menu_stop).setVisible(true);
            	menu.findItem(R.id.menu_tab_delete).setVisible(true);
            	menu.findItem(R.id.menu_tab_rename).setVisible(true);
            	
            	menu.findItem(R.id.menu_tools).setVisible(true);
            	
            	if (getGlobalState().isExample()) {
            		menu.findItem(R.id.menu_undo).setVisible(false);
                	menu.findItem(R.id.menu_redo).setVisible(false);
            	} else {
            		if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean("pref_key_undo_redo", true)) {
            			menu.findItem(R.id.menu_undo).setVisible(true);
            			menu.findItem(R.id.menu_redo).setVisible(true);
            		} else {
            			menu.findItem(R.id.menu_undo).setVisible(false);
                    	menu.findItem(R.id.menu_redo).setVisible(false);
            		}
            	}
            } else {
            	// If the drawer is closed and there are no tabs
            	
            	// Make sure to make the tab actions invisible
            	menu.findItem(R.id.menu_run).setVisible(false);
    	    	menu.findItem(R.id.menu_stop).setVisible(false);
    	    	menu.findItem(R.id.menu_undo).setVisible(false);
            	menu.findItem(R.id.menu_redo).setVisible(false);
    	    	menu.findItem(R.id.menu_tab_delete).setVisible(false);
            	menu.findItem(R.id.menu_tab_rename).setVisible(false);
            	menu.findItem(R.id.menu_tools).setVisible(false);
            }
        	
        	// Enable/disable undo/redo buttons
        	SketchFile meta = getSelectedSketchFile();
        	menu.findItem(R.id.menu_undo).setEnabled(meta != null ? meta.canUndo() : false);
        	menu.findItem(R.id.menu_redo).setEnabled(meta != null ? meta.canRedo() : false);
        	
        	// Make sure to make all of the sketch-specific actions visible
        	
        	switch (getGlobalState().getSketchLocationType()) {
        	case SKETCHBOOK:
				menu.findItem(R.id.menu_save).setVisible(true);
				menu.findItem(R.id.menu_delete).setVisible(true);
				menu.findItem(R.id.menu_rename).setVisible(true);
				menu.findItem(R.id.menu_copy_to_sketchbook).setVisible(false);
				menu.findItem(R.id.menu_move_to_sketchbook).setVisible(false);
				break;
        	case TEMPORARY:
        		menu.findItem(R.id.menu_save).setVisible(true);
				menu.findItem(R.id.menu_delete).setVisible(true);
				menu.findItem(R.id.menu_rename).setVisible(false);
        		menu.findItem(R.id.menu_copy_to_sketchbook).setVisible(false);
				menu.findItem(R.id.menu_move_to_sketchbook).setVisible(true);
        		break;
        	case EXTERNAL:
        		menu.findItem(R.id.menu_save).setVisible(true);
				menu.findItem(R.id.menu_delete).setVisible(true);
				menu.findItem(R.id.menu_rename).setVisible(true);
        		menu.findItem(R.id.menu_copy_to_sketchbook).setVisible(true);
				menu.findItem(R.id.menu_move_to_sketchbook).setVisible(false);
        		break;
        	case EXAMPLE:
        	case LIBRARY_EXAMPLE:
        		menu.findItem(R.id.menu_save).setVisible(false);
				menu.findItem(R.id.menu_delete).setVisible(false);
				menu.findItem(R.id.menu_rename).setVisible(false);
        		menu.findItem(R.id.menu_copy_to_sketchbook).setVisible(true);
				menu.findItem(R.id.menu_move_to_sketchbook).setVisible(false);
        		break;
        	}
        	
        	menu.findItem(R.id.menu_new).setVisible(true);
        	menu.findItem(R.id.menu_load).setVisible(true);
        	menu.findItem(R.id.menu_tab_new).setVisible(true);
        	menu.findItem(R.id.menu_sketch_properties).setVisible(true);
        	
        	//Make sure to make the sketch name visible
        	getSupportActionBar().setTitle(getGlobalState().getSketchName());
        }
        
        // Disable these buttons because they appear when the tab is pressed
        // Not getting rid of them completely in case we want to change things in the future
        menu.findItem(R.id.menu_tab_new).setVisible(false);
        menu.findItem(R.id.menu_tab_delete).setVisible(false);
    	menu.findItem(R.id.menu_tab_rename).setVisible(false);
		
		// With auto-saving, we don't actually need to let the user save the sketch manually
		// However, the keyboard shortcut will still be available
		menu.findItem(R.id.menu_save).setVisible(false);
    	
    	// So that the user can add a tab if there are none
    	if (getCodeCount() <= 0 && !getGlobalState().isExample()) {
			menu.findItem(R.id.menu_tab_new).setVisible(true);
		}
    	
        return true;
    }
    
    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        drawerToggle.syncState(); //The stranger aspects of having a navigation drawer...
    }
    
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        drawerToggle.onConfigurationChanged(newConfig); //The stranger aspects of having a navigation drawer...
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
    	//When an action bar (or action bar overflow) action has been selected
    	
        switch(item.getItemId()) {
        	case android.R.id.home:
        		//The drawer toggle button
        		
        		//Get a reference to the drawer views
        		DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer);
        		RelativeLayout drawerLayout = (RelativeLayout) findViewById(R.id.drawer_wrapper);
        		
        		if(drawer.isDrawerOpen(drawerLayout)) {
        			//If the drawer is open, close it
                    drawer.closeDrawer(drawerLayout);
        		} else {
        			//If the drawer is closed, open it
                    drawer.openDrawer(drawerLayout);
                    
                    //Turn of the code area so that it cannot be focused (and so that the soft keyboard is hidden)
					if (isSelectedCodeAreaInitialized()) {
						getSelectedCodeArea().setEnabled(false);
					}
                    supportInvalidateOptionsMenu();
                }
        		return true;
            case R.id.menu_run:
            	runApplication();
            	return true;
            case R.id.menu_stop:
            	stopApplication();
            	return true;
            case R.id.menu_undo:
        		tabs.get(getSelectedCodeIndex()).undo(this);
            	return true;
            case R.id.menu_redo:
            	tabs.get(getSelectedCodeIndex()).redo(this);
            	return true;
            case R.id.menu_save:
            	saveSketch();
            	return true;
			case R.id.menu_rename:
				launchRenameSketch();
				return true;
            case R.id.menu_copy_to_sketchbook:
            	copyToSketchbook();
            	return true;
			case R.id.menu_move_to_sketchbook:
				moveToSketchbook();
				return true;
            case R.id.menu_new:
            	createNewSketch();
            	return true;
            case R.id.menu_load:
            	loadSketch();
            	return true;
			case R.id.menu_delete:
				launchDeleteSketchConfirmationDialog();
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
            case R.id.menu_tools:
            	launchTools();
        		return true;
            case R.id.menu_sketch_properties:
            	launchSketchProperties();
            	return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
	
    public void toggleCharInserts() {
    	// Don't do anything if the user has disabled the character insert tray
		if (!PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getBoolean("char_inserts", true)) {
			return;
		}
    	
    	if (!keyboardVisible) {
			return;
		}
    	
    	if (charInserts) {
			hideCharInserts();
    	} else {
			//Build the character inserts tray
			reloadCharInserts();
		
			showCharInserts();
    	}
    }
    
    protected void showCharInserts() {
		TextView messageView = (TextView) findViewById(R.id.message);
		HorizontalScrollView charInsertTray = (HorizontalScrollView) findViewById(R.id.char_insert_tray);
		
		View buffer = findViewById(R.id.buffer);
		
		View sep = findViewById(R.id.toggle_char_inserts_separator);
		
		toggleCharInserts.setImageResource(errorMessage ? R.drawable.ic_caret_right_white : R.drawable.ic_caret_right_black);
//		((TextView) findViewById(R.id.message)).setVisibility(View.GONE);
//		((HorizontalScrollView) findViewById(R.id.char_insert_tray)).setVisibility(View.VISIBLE);
		
		int total = buffer.getWidth() - sep.getWidth() - toggleCharInserts.getWidth();
		
		RotateAnimation rotate = new RotateAnimation(180f, 360f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
		rotate.setInterpolator(new AccelerateDecelerateInterpolator());
		rotate.setRepeatCount(0);
		rotate.setDuration(200);
		
		messageView.startAnimation(new ResizeAnimation<LinearLayout>(messageView, ResizeAnimation.DEFAULT, ResizeAnimation.DEFAULT, 0, ResizeAnimation.DEFAULT));
		charInsertTray.startAnimation(new ResizeAnimation<LinearLayout>(charInsertTray, 0, buffer.getHeight(), total, buffer.getHeight()));
		toggleCharInserts.startAnimation(rotate);
		
		charInserts = true;
    }
	
    protected void hideCharInserts() {
		if (!(keyboardVisible && charInserts)) {
			// No need to hide them if they're already hidden
			return;
		}
		
		TextView messageView = (TextView) findViewById(R.id.message);
		HorizontalScrollView charInsertTray = (HorizontalScrollView) findViewById(R.id.char_insert_tray);
		
		View buffer = findViewById(R.id.buffer);
		
		View sep = findViewById(R.id.toggle_char_inserts_separator);
	
		toggleCharInserts.setImageResource(errorMessage ? R.drawable.ic_caret_left_white : R.drawable.ic_caret_left_black);
		
		int total = buffer.getWidth() - sep.getWidth() - toggleCharInserts.getWidth();
		
		RotateAnimation rotate = new RotateAnimation(180f, 360f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
		rotate.setInterpolator(new AccelerateDecelerateInterpolator());
		rotate.setRepeatCount(0);
		rotate.setDuration(200);
		
		messageView.startAnimation(new ResizeAnimation<LinearLayout>(messageView, 0, buffer.getHeight(), total, buffer.getHeight()));
		charInsertTray.startAnimation(new ResizeAnimation<LinearLayout>(charInsertTray, ResizeAnimation.DEFAULT, ResizeAnimation.DEFAULT, 0, ResizeAnimation.DEFAULT));
		toggleCharInserts.startAnimation(rotate);
		
		charInserts = false;
    }
	
	public void hideCharInsertsNoAnimation() {
		TextView messageView = (TextView) findViewById(R.id.message);
		HorizontalScrollView charInsertTray = (HorizontalScrollView) findViewById(R.id.char_insert_tray);
		
		toggleCharInserts.setImageResource(errorMessage ? R.drawable.ic_caret_left_white : R.drawable.ic_caret_left_black);
		messageView.setVisibility(View.VISIBLE);
		charInsertTray.setVisibility(View.GONE);
		
		charInserts = false;
	}
    
    /**
     * Set up the character inserts tray.
     */
	public void reloadCharInserts() {
		if (!keyboardVisible)
			return;
		
    	if (message == -1) {
			message = findViewById(R.id.buffer).getHeight();
		}
    	
    	//Get a reference to the button container
    	LinearLayout container = ((LinearLayout) findViewById(R.id.char_insert_tray_list));
    	//Clear any buttons from before
    	container.removeAllViews();
    	
    	//The (temporary) list of character inserts TODO make this list configurable
    	//"\u2192" is Unicode for the right arrow (like "->") - this is a graphical representation of the TAB key
    	String[] chars;
    	//This branch isn't very elegant... but it will work for now...
    	if(PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getBoolean("char_inserts_include_numbers", true))
    		chars = new String[] {"\u2192", ";", ".", ",", "{", "}", "(", ")", "=", "*", "/", "+", "-", "&", "|", "!", "[", "]", "<", ">", "\"", "'", "\\", "_", "?", ":", "%", "@", "#", "1", "2", "3", "4", "5", "6", "7", "8", "9", "0"};
    	else
    		chars = new String[] {"\u2192", ";", ".", ",", "{", "}", "(", ")", "=", "*", "/", "+", "-", "&", "|", "!", "[", "]", "<", ">", "\"", "'", "\\", "_", "?", ":", "%", "@", "#"};
    	
    	//This works for now... as far as I can tell
    	final int keyboardID = 0;
		
    	//Add each button to the container
    	for(final String c : chars) {
    		Button button = (Button) LayoutInflater.from(this).inflate(R.layout.char_insert_button, null);
    		button.setText(c);
    		button.setTextColor(getResources().getColor(errorMessage ? R.color.char_insert_button_light : R.color.char_insert_button));
    		button.setLayoutParams(new LinearLayout.LayoutParams((int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 25, getResources().getDisplayMetrics()), message));
    		button.setPadding(0, 0, 0, 0);
    		
    		//Maybe we'll want these at some point in time... but for now, they just cause more problems...
    		//...and the user won't be dragging the divider around if the keyboard is open (which it really should be)
//    		//Still let the user drag the message area
//    		button.setOnLongClickListener(messageListener);
//    		button.setOnTouchListener(messageListener);
    		
    		container.addView(button);
    		
    		button.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View view) {
					// A special check for the tab key... making special exceptions aren't exactly ideal, but this is probably the most concise solution (for now)...
					KeyEvent event = c.equals("\u2192") ? new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_TAB) : new KeyEvent(android.os.SystemClock.uptimeMillis(), c, keyboardID, 0);
					
					boolean dispatched = false;
					
					if (extraHeaderView != null) {
						// If the find/replace toolbar is open
						
						EditText findTextField = (EditText) extraHeaderView.findViewById(R.id.find_replace_find_text);
						EditText replaceTextField = (EditText) extraHeaderView.findViewById(R.id.find_replace_replace_text);
						
						if (findTextField != null) {
							if (findTextField.hasFocus()) {
								findTextField.dispatchKeyEvent(event);
								dispatched = true;
							} else {
								if (replaceTextField != null && replaceTextField.hasFocus()) {
									replaceTextField.dispatchKeyEvent(event);
									dispatched = true;
								}
							}
						}
					}
					
					if (!dispatched) {
						getSelectedCodeArea().dispatchKeyEvent(event);
					}
					
					// Provide haptic feedback (if the user has vibrations enabled)
					if(PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getBoolean("pref_vibrate", true))
						((android.os.Vibrator) getSystemService(VIBRATOR_SERVICE)).vibrate(10); //10 millis
				}
    		});
    	}
    }
    
    /**
     * Builds and launches the current sketch
     * This CAN be called multiple times without breaking anything
     */
    private void runApplication() {
    	switch(getGlobalState().getSketchLocationType()) {
    	case EXAMPLE:
    	case LIBRARY_EXAMPLE:
    		break;
    	case SKETCHBOOK:
    	case EXTERNAL:
		case TEMPORARY:
    		saveSketch();
    		break;
    	}
    	
    	//In case the user presses the button twice, we don't want any errors
    	if (building) {
    		return;
    	}
		
		if (!checkScreenOverlay()) {
			return;
		}
    	
    	//Clear the console
    	((TextView) findViewById(R.id.console)).setText("");
    	
    	final Build builder = new Build(getGlobalState());
    	
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
    
    /**
     * Stops the current sketch's build process
     * This CAN be called multiple times without breaking anything
     */
    private void stopApplication() {
    	//This will stop the current build process
    	//I don't think we can stop a running app...
    	//...that's what the BACK button is for
    	
    	if(building)
    		Build.halt();
    }
	
	/**
	 * Set to the most recent value of MotionEvent.FLAG_WINDOW_IS_OBSCURED
	 * 
	 * This will be true if a screen overlay (e.g. Twilight, Lux, or similar app) is currently
	 * being drawn over the screen. Android security measures prevent app installation if a screen
	 * overlay is being drawn, so we need to let the user know and stop the build so that we don't
	 * get "I can't press the install button!" emails.
	 * 
	 * This is the exact same detection method used by the Android system, so there are no corner
	 * cases. See the link below for the source code of Android's implementation:
	 * 
	 * http://androidxref.com/6.0.1_r10/xref/packages/apps/PackageInstaller/src/com/android/packageinstaller/permission/ui/OverlayTouchActivity.java
	 */
	private boolean isTouchObscured = false;
	
	@Override
	public boolean dispatchTouchEvent(MotionEvent event) {
		/*
		 * See comments for isTouchObscured above.
		 */
		
		isTouchObscured = (event.getFlags() & MotionEvent.FLAG_WINDOW_IS_OBSCURED) != 0;
		return super.dispatchTouchEvent(event);
	}
	
	/**
	 * Check to see if we can proceed with the build. If a screen overlay is in place, then the
	 * Android system will prevent the package manager from installing the sketch, so show a
	 * warning message to the user.
	 * 
	 * @return whether or not to proceed with the build
	 */
	public boolean checkScreenOverlay() {
		if (isTouchObscured) {
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			
			builder.setTitle(R.string.screen_overlay_dialog_title);
			builder.setMessage(R.string.screen_overlay_dialog_message);
			
			builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialogInterface, int i) {}
			});
			
			builder.setNeutralButton(R.string.info, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialogInterface, int i) {
					startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(getResources().getString(R.string.screen_overlay_info_wiki_url))));
				}
			});
			
			builder.show();
			
			return false;
		} else {
			return true;
		}
	}
    
    /**
     * Writes a message to the message area
     * 
     * @param msg
     */
    public void message(String msg) {
    	//Write the message
    	((TextView) findViewById(R.id.message)).setText(msg);
    	
    	colorMessageAreaMessage();
    	
    	errorMessage = false;
    	
    	//Update message area height
    	correctMessageAreaHeight();
    	
    	hideCharInsertsNoAnimation();
    }
    
    /**
     * Writes a message to the message area from a non-UI thread
     * (this is used primarily from the build thread)
     * 
     * @param msg
     */
    public void messageExt(final String msg) {
    	runOnUiThread(new Runnable() {
			public void run() {
				//Write the message
				((TextView) findViewById(R.id.message)).setText(msg);

				colorMessageAreaMessage();

				errorMessage = false;

				//Update message area height
				correctMessageAreaHeight();

				hideCharInsertsNoAnimation();
			}
		});
    }
    
    /**
     * Writes a message to the message area
     * This is a convenience method for message(String)
     * 
     * @param msg
     */
    public void message(CharSequence msg) {
    	message(msg.toString());
    }
    
    /**
     * Writes an error message to the message area
     * 
     * @param msg
     */
    public void error(String msg) {
    	//Write the error message
    	((TextView) findViewById(R.id.message)).setText(msg);
    	
    	colorMessageAreaError();
    	
    	errorMessage = true;
    	
    	//Update message area height
    	correctMessageAreaHeight();
    	
    	hideCharInsertsNoAnimation();
    }
    
    /**
     * Writes an error message to the message area from a non-UI thread
     * (this is used primarily from the build thread)
     * 
     * @param msg
     */
    public void errorExt(final String msg) {
    	runOnUiThread(new Runnable() {
			public void run() {
				//Write the error message
				((TextView) findViewById(R.id.message)).setText(msg);

				colorMessageAreaError();

				errorMessage = true;

				//Update message area height
				correctMessageAreaHeight();

				hideCharInsertsNoAnimation();
			}
		});
    }
    
    /**
     * Writes an error message to the message area
     * This is a convenience method for error(String)
     * 
     * @param msg
     */
    public void error(CharSequence msg) {
    	error(msg.toString());
    }
    
    //Utility function for switching to message-style message area
	protected void colorMessageAreaMessage() {
    	//Change the message area style
    	((LinearLayout) findViewById(R.id.buffer)).setBackgroundColor(getResources().getColor(R.color.message_back));
    	((TextView) findViewById(R.id.message)).setTextColor(getResources().getColor(R.color.message_text));
    	
    	//Update the toggle button
    	toggleCharInserts.setImageResource(charInserts ? R.drawable.ic_caret_right_black : R.drawable.ic_caret_left_black);
    	
    	//Update the separator line
    	findViewById(R.id.toggle_char_inserts_separator).setBackgroundColor(getResources().getColor(R.color.toggle_char_inserts_separator));
    	
    	//Update the buttons in the character insert tray
    	LinearLayout charInsertTrayList = (LinearLayout) findViewById(R.id.char_insert_tray_list);
    	for(int i = 0; i < charInsertTrayList.getChildCount(); i ++) {
    		((Button) charInsertTrayList.getChildAt(i)).setTextColor(getResources().getColor(R.color.char_insert_button));
    	}
    }
    
    //Utility function for switching to error-style message area
	protected void colorMessageAreaError() {
    	//Change the message area style
    	((LinearLayout) findViewById(R.id.buffer)).setBackgroundColor(getResources().getColor(R.color.error_back));
    	((TextView) findViewById(R.id.message)).setTextColor(getResources().getColor(R.color.error_text));
    	
    	//Update the toggle button
    	toggleCharInserts.setImageResource(charInserts ? R.drawable.ic_caret_right_white : R.drawable.ic_caret_left_white);
    	
    	//Update the separator line
    	findViewById(R.id.toggle_char_inserts_separator).setBackgroundColor(getResources().getColor(R.color.toggle_char_inserts_separator_light));
    	
    	//Update the buttons in the character insert tray
    	LinearLayout charInsertTrayList = (LinearLayout) findViewById(R.id.char_insert_tray_list);
    	for(int i = 0; i < charInsertTrayList.getChildCount(); i ++) {
    		((Button) charInsertTrayList.getChildAt(i)).setTextColor(getResources().getColor(R.color.char_insert_button_light));
    	}
    }
    
    // Called internally to correct issues with 2-line messages vs 1-line messages (and maybe some other issues)
    protected void correctMessageAreaHeight() {
    	final TextView messageArea = (TextView) findViewById(R.id.message);
    	
    	// Update the message area's height
    	messageArea.requestLayout();
    	
    	// Check back in later when the height has updated...
    	messageArea.post(new Runnable() {
    		public void run() {
    			// ...and update the console's height...
    			
    			// We need to use this in case the message area is partially off the screen
    			// This is the DESIRED height, not the ACTUAL height
    			message = getTextViewHeight(getApplicationContext(), messageArea.getText().toString(), messageArea.getTextSize(), messageArea.getWidth(), messageArea.getPaddingTop());
    			
				findViewById(R.id.buffer).getLayoutParams().height = message;
				
    			// Obtain some references
    			View console = findViewById(R.id.console_scroller);
    			View content = findViewById(R.id.content);
				
				if (isSelectedCodeAreaInitialized()) {
					// We can't shrink the console if it's hidden (like when the keyboard is visible)...
					// ...so shrink the code area instead
					if (console.getHeight() <= 0) {
						codePager.setLayoutParams(new LinearLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
								content.getHeight() - message - (extraHeaderView != null ? extraHeaderView.getHeight() : 0)));
					} else {
						console.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
								content.getHeight() - codePager.getHeight() - message - (extraHeaderView != null ? extraHeaderView.getHeight() : 0)));
					}
				}
    		}
    	});
    }
	
	public void initCodeAreaAndConsoleDimensions() {
		ScrollView console = getConsoleScroller();
		
		// Initialize in case we have the layout weights instead of actual values
		codePager.getLayoutParams().height = codePager.getHeight();
		console.getLayoutParams().height = console.getHeight();
	}
	
	/**
	 * Fix inconsistencies in the vertical distribution of the content area views
	 */
	public void refreshMessageAreaLocation() {
		//Obtain some references
		final View content = findViewById(R.id.content);
		final View console = findViewById(R.id.console_scroller);
		final View code = getSelectedCodeAreaScroller();
		final TextView messageArea = (TextView) findViewById(R.id.message);
		
		if (firstResize) {
			//Use some better layout parameters - this switches from fractions/layout weights to absolute values
			code.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, code.getHeight()));
			console.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, console.getHeight()));

			firstResize = false;
		}
		
		messageArea.requestLayout();
		
		messageArea.post(new Runnable() {
			public void run() {
				//We need to use this in case the message area is partially off the screen
				//This is the DESIRED height, not the ACTUAL height
				message = getTextViewHeight(getApplicationContext(), messageArea.getText().toString(), messageArea.getTextSize(), messageArea.getWidth(), messageArea.getPaddingTop());

				int consoleSize = content.getHeight() - code.getHeight() - codeTabStrip.getHeight() - message - (extraHeaderView != null ? extraHeaderView.getHeight() : 0);

				//We can't shrink the console if it's hidden (like when the keyboard is visible)...
				//...so shrink the code area instead
				if (consoleSize < 0 || consoleWasHidden || keyboardVisible) {
					console.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0));
					code.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
							content.getHeight() - codeTabStrip.getHeight() - message - (extraHeaderView != null ? extraHeaderView.getHeight() : 0)));

					consoleWasHidden = true;
				} else {
					console.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, consoleSize));

					consoleWasHidden = false;
				}
			}
		});
	}
    
    //Calculates the height of a TextView
    //StackOverflow: http://stackoverflow.com/questions/14276853/how-to-measure-textview-height-based-on-device-width-and-font-size
    private static int getTextViewHeight(Context context, String text, float textSize, int deviceWidth, int padding) {
    	TextView textView = new TextView(context);
    	textView.setText(text);
    	textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize);
    	textView.setPadding(padding, padding, padding, padding);
    	
    	int widthMeasureSpec = MeasureSpec.makeMeasureSpec(deviceWidth, MeasureSpec.AT_MOST);
    	int heightMeasureSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
    	
    	textView.measure(widthMeasureSpec, heightMeasureSpec);
    	return textView.getMeasuredHeight();
    }
    
    /**
     * Highlights a code line (and opens the corresponding tab) in the code area from a non-UI thread
     * (this is used primarily from the build thread)
     * 
     * @param tab
     * @param line
     */
    public void highlightLineExt(final int tab, final int line) {
    	runOnUiThread(new Runnable() {
			public void run() {
				//Switch to the tab with the error if we have one to switch to
				if (tab != -1 && tab < tabs.size()) {
//					tabBar.selectTab(tab);
					selectCode(tab);
				}

				//Get a reference to the code area
				CodeEditText code = getSelectedCodeArea();

				//Calculate the beginning and ending of the line
				int start = code.offsetForLine(line);
				int stop = code.offsetForLineEnd(line);

				if (!PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getBoolean("use_hardware_keyboard", false)) {
					//Check to see if the user wants to use the hardware keyboard

					//Hacky way of focusing the code area - dispatches a touch event
					MotionEvent me = MotionEvent.obtain(100, 0, 0, 0, 0, 0);
					code.dispatchTouchEvent(me);
					me.recycle();
				}

				//Select the text in the code area
				code.setSelection(start, stop);
			}
		});
    }
	
	protected void addTabWithoutPagerUpdate(SketchFile sketchFile) {
		tabs.add(sketchFile);
	}
	
	public void addTab(SketchFile sketchFile) {
		tabs.add(sketchFile);
		codePagerAdapter.notifyDataSetChanged();
	}
	
	public void onTabReselected(View view) {
		if(!drawerOpen && !getGlobalState().isExample()) {
			PopupMenu popup = new PopupMenu(getGlobalState().getEditor(), view);
			
			//Populate the actions
			MenuInflater inflater = getMenuInflater();
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
    
    /**
     * Creates a user input dialog for adding a new tab
     */
    private void addTabWithDialog() {
    	createInputDialog(getResources().getString(R.string.tab_new_dialog_title), getResources().getString(R.string.tab_new_dialog_message), "", NEW_TAB);
    }
    
    /**
     * Adds a default tab to the tab bar
     * 
     * @param title
     */
    private void addDefaultTab(String title) {
    	//Obtain a tab
//    	ActionBar.Tab tab = getSupportActionBar().newTab();
    	//Set the tab text
//    	tab.setText(title);
    	
    	//Add the tab
//    	tabBar.addTab(tab);
//    	tabs.put(tab, new SketchFile(title));
    	addTab(new SketchFile(title));
		
    	//Make the tab non-all-caps
//    	customizeTab(tab, title);
    }
    
    /**
     * Adds a tab to the tab bar
     * 
     * @param title
     */
	private void addTab(String title) {
		//Obtain a tab
//    	ActionBar.Tab tab = getSupportActionBar().newTab();
    	//Set the tab text
//    	tab.setText(title);
    	
    	//Add the tab
//    	tabBar.addSelectTab(tab);
//    	tabs.put(tab, new SketchFile(title));
    	addTab(new SketchFile(title));
		
		selectCode(getCodeCount() - 1);
		
    	//Clear the code area
//    	((CodeEditText) findViewById(R.id.code)).setNoUndoText("");
//    	((CodeEditText) findViewById(R.id.code)).clearTokens();
    	
    	//Make the tab non-all-caps
//    	customizeTab(tab, title);
	}
	
//	/**
//	 * Makes the tab non-all-caps (very hacky)
//	 * 
//	 * @param tab
//	 * @param title
//	 */
//	@SuppressWarnings("deprecation")
//	private void customizeTab(Tab tab, String title) {
//		//Hacky way of making the tabs non-all-caps
//		//Theoretically, we could do some other stuff here, too...
//		
//		//Get the tab view
//		LinearLayoutCompat view = (LinearLayoutCompat) tabBar.getNewTabView();
//    	
//    	//Initialize some basic properties
//    	view.setGravity(Gravity.CENTER);
//    	view.setBackgroundColor(getResources().getColor(R.color.activity_background));
//    	view.setBackgroundDrawable(getResources().getDrawable(R.drawable.abc_tab_indicator_mtrl_alpha));
//    	
//    	//Get the text view
//    	TextView textView = (TextView) view.getChildAt(0);
//    	
//    	//Set up the text view
//    	textView.setText(title);
//    	textView.setTextColor(getResources().getColor(R.color.tab_text_color));
//    	textView.setTypeface(Typeface.DEFAULT_BOLD);
//    	textView.setTextSize(12);
//    	
//    	//Ensure that the tabs automatically scroll at some point
//    	view.setMinimumWidth((int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 80, getResources().getDisplayMetrics()));
//    	
//    	//Make sure that we can detect tab selection events
//    	view.setOnClickListener(tabBar.getTabClickListener());
//    }
    
	/**
	 * Creates a user input dialog for renaming the current tab
	 */
    private void renameTab() {
    	if(tabs.size() > 0 && !getGlobalState().isExample())
    		createInputDialog(getResources().getString(R.string.tab_rename_dialog_title), getResources().getString(R.string.tab_rename_dialog_message), getSelectedSketchFile().getTitle(), RENAME_TAB);
    }
    
    /**
     * Creates a user input dialog for deleting the current tab
     */
    private void deleteTab() {
    	if(getGlobalState().isExample())
    		return;
    	
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
    
    /**
     * Deletes a file in the sketch folder
     * 
     * @param filename
     * @return success
     */
    private boolean deleteLocalFile(String filename) {
    	//Get the sketch folder location
    	File sketchLoc = getGlobalState().getSketchLocation();
    	//Get the file location
    	File file = new File(sketchLoc + "/", filename);
    	
    	//Delete the file
    	if (file.exists()) {
    		file.delete();
			
    		return true;
    	}
    	
    	return false;
    }
    
    //Called internally from delete tab dialog
    private void deleteTabContinue() {
    	if(tabs.size() > 0) {
    		//Get the tab
//    		Tab cur = tabBar.getSelectedTab(); //TODO there'll be problems here for sure
    		//Delete the tab from the sketch folder
    		deleteLocalFile(getSelectedSketchFile().getFilename());
			//Disable the tab
//    		tabs.get(cur).disable();
			getSelectedSketchFile().disable();
    		//Remove the tab
//    		tabBar.removeSelectedTab();
			
			// We have to do this whole hop-skip thing because of peculiarities in the PagerSlidingTabStrip library...
			selectCode(getSelectedCodeIndex() - 1);
			tabs.remove(getSelectedCodeIndex() + 1);
//	    	tabs.remove(cur);
	    	
	    	//If there are no more tabs
	    	if(getCodeCount() <= 0) {
//	    		//Clear the code text area
//		    	CodeEditText code = ((CodeEditText) findViewById(R.id.code));
//		    	code.setNoUndoText("");
//		    	code.setSelection(0);
//		    	
//		    	//Get rid of previous syntax highlighter data
//	    		code.clearTokens();
//	    		
//	    		//Disable the code text area if there is no selected tab
//		    	code.setFocusable(false);
//	    		code.setFocusableInTouchMode(false);
	    		
	    		//Force remove all tabs
//	    		tabBar.removeAllTabs();
	    		tabs.clear();
	    		
	    		//Force action menu refresh
	    		supportInvalidateOptionsMenu();
	    	}
		
			codePagerAdapter.notifyDataSetChanged();
			
	    	//Inform the user in the message area
	    	message(getResources().getText(R.string.tab_deleted));
    	}
    }
    
    //Called internally to create an input dialog
    private void createInputDialog(String title, String message, String currentName, final int key) {
    	//Get a dialog builder
    	AlertDialog.Builder alert = new AlertDialog.Builder(this);
    	
    	//Set the title and message
    	alert.setTitle(title);
    	alert.setMessage(message);
		
		final EditText input = getGlobalState().createAlertDialogEditText(this, alert, currentName, true);
    	
    	//Add the "OK" button
    	alert.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
    		public void onClick(DialogInterface dialog, int whichButton) {
    			String value = input.getText().toString();
    			checkInputDialog(key, true, value);
				
				if (!PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getBoolean("use_hardware_keyboard", false)) {
					((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(input.getWindowToken(), 0);
				}
    		}
    	});
    	
    	//Add the "Cancel" button
    	alert.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
    		public void onClick(DialogInterface dialog, int whichButton) {
    			checkInputDialog(key, false, "");
				
				if (!PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getBoolean("use_hardware_keyboard", false)) {
					((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(input.getWindowToken(), 0);
				}
    		}
    	});
    	
    	//Show the soft keyboard if the hardware keyboard is unavailable (hopefully)
    	AlertDialog dialog = alert.create();
    	if(!PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getBoolean("use_hardware_keyboard", false)) {
			dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
		}
    	dialog.show();
    }
    
    //Called internally when the add or rename tab dialog is closed
    private void checkInputDialog(int key, boolean completed, String value) {
    	if(completed) {
    		//Make sure that this is a valid name for a tab
    		if(!(validateFileName(value) || (value.endsWith(".java") && value.length() > 5 && validateFileName(value.substring(0, value.length() - 5))))) {
    			return;
    		}
    		
    		switch(key) {
    		case RENAME_TAB:
    			//Get the tab
//    			Tab cur = tabBar.getSelectedTab();
    			//Delete the tab from the sketch folder
    			deleteLocalFile(getSelectedSketchFile().getFilename());
    			
    			//Change the tab as it is displayed
//    			((TextView) ((LinearLayoutCompat) tabBar.getTabView(tabBar.getSelectedTab())).getChildAt(0)).setText(value); //This seems more complicated than it needs to be... but it's necessary to change the appearance of the tab name
//    			tabBar.getSelectedTab().setText(value);
//    			tabs.get(tabBar.getSelectedTab()).setTitle(value);
    	    	getSelectedSketchFile().setTitle(value);
				codePagerAdapter.notifyDataSetChanged();
				
    	    	//Notify the user of success
    			message(getResources().getText(R.string.tab_renamed));
    			
    			break;
    		case NEW_TAB:
    			if (value.endsWith(".java")) {
    				//Obtain a tab
//    		    	ActionBar.Tab tab = getSupportActionBar().newTab();
    		    	//Set the tab text
//    		    	tab.setText(value);
    		    	
    		    	//Add the tab
//    		    	tabBar.addSelectTab(tab);
    		    	
    		    	//Correctly instantiate with a .JAVA suffix
    		    	SketchFile meta = new SketchFile(value.substring(0, value.length() - 5));
    		    	meta.setSuffix(".java");
    		    	
//    		    	tabs.put(tab, meta);
					addTab(meta);
					selectCode(getCodeCount() - 1);
    		    	
    		    	//Clear the code area
//    		    	((CodeEditText) findViewById(R.id.code)).setNoUndoText("");
//    		    	((CodeEditText) findViewById(R.id.code)).clearTokens();
    		    	
    		    	//Make the tab non-all-caps
//    		    	customizeTab(tab, value);
    				
    				//Select the new tab
//    				tabBar.selectLastTab();
//					selectCode(getCodeCount() - 1);
    				//Perform a selection action
//    				onTabSelected(tabBar.getSelectedTab());
    			} else {
    				//Add the tab
    				addTab(value);
    				//Select the new tab
//    				tabBar.selectLastTab();
					selectCode(getCodeCount() - 1);
    				//Perform a selection action
//    				onTabSelected(tabBar.getSelectedTab());
    			}
    			
    			//Refresh options menu to remove "New Tab" button
				if (getCodeCount() == 1) {
					supportInvalidateOptionsMenu();
				}
				
				//Make sure that the code area is editable
//				((CodeEditText) findViewById(R.id.code)).setFocusable(true);
//				((CodeEditText) findViewById(R.id.code)).setFocusableInTouchMode(true);
				
				//Notify the user of success
				message(getResources().getText(R.string.tab_created));
				
				break;
    		}
    	}
    }
    
//    public SketchFile getCurrentFileMeta() {
//    	return tabs.get(tabBar.getSelectedTab());
//    }
    
    public void clearUndoRedoHistory() {
    	for (SketchFile meta : tabs) {
    		meta.clearUndoRedo();
    	}
    }
    
//	@Override
//	public void onTabSelected(Tab tab) {
//		//Get a reference to the code area
//		final CodeEditText code = ((CodeEditText) findViewById(R.id.code));
//		final HorizontalScrollView scrollerX = ((HorizontalScrollView) findViewById(R.id.code_scroller_x));
//		final ScrollView scrollerY = ((ScrollView) findViewById(R.id.code_scroller));
//		//Get a reference to the current tab's meta
//		final SketchFile meta = tabs.get(tab);
//		
//		if(getCodeCount() > 0 && meta != null) {
//			//If there are already tabs
//			
//			//Update the code area text
//			code.setNoUndoText(meta.getText());
//			//Update the code area selection
//			code.setSelection(meta.getSelectionStart(), meta.getSelectionEnd());
//			code.updateBracketMatch();
//			
//			scrollerX.post(new Runnable() {
//				public void run() {
//					scrollerX.scrollTo(meta.getScrollX(), 0);
//				}
//			});
//			
//			scrollerY.post(new Runnable() {
//				public void run() {
//					scrollerY.scrollTo(0, meta.getScrollY());
//				}
//			});
//			
////			System.out.println("scrolling tab " + meta.getTitle() + ", scrollX: " + meta.getScrollX() + ", scrollY: " + meta.getScrollY());
//			
//			//Get rid of previous syntax highlighter data
//    		code.clearTokens();
//		} else {
//			//If this is selecting the first added tab
//			
//			//Re-enable the code text area
//	    	code.setEnabled(true);
//		}
//		
//		//Force action menu refresh
//		supportInvalidateOptionsMenu();
//	}
//	
//	@Override
//	public void onTabUnselected(Tab tab) {
////		tabs.put(tab, new SketchFile(tab.getText().toString(), this));
//		getCurrentFileMeta().update(this, PreferenceManager.getDefaultSharedPreferences(this).getBoolean("pref_key_undo_redo", true));
//	}
//	
//	@Override
//	public void onTabReselected(Tab tab) {
//		if(!drawerOpen && !getGlobalState().isExample()) {
//			//Get a reference to the anchor view for the popup window
//			View anchorView = (View) tabBar.getTabView(tabBar.getSelectedTab());
//			
//			//Create a PopupMenu anchored to a 0dp height "fake" view at the top if the display
//			//This is a custom implementation, designed to support API level 10+ (Android's PopupMenu is 11+)
//			PopupMenu popup = new PopupMenu(getGlobalState().getEditor(), anchorView);
//			
//			//Populate the actions
//			MenuInflater inflater = getMenuInflater();
//			inflater.inflate(R.menu.tab_actions, popup.getMenu());
//			
//			//Detect presses
//			popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
//				@Override
//				public boolean onMenuItemClick(MenuItem item) {
//					switch(item.getItemId()) {
//					case R.id.menu_tab_new:
//						addTabWithDialog();
//						return true;
//					case R.id.menu_tab_rename:
//						renameTab();
//						return true;
//					case R.id.menu_tab_delete:
//						deleteTab();
//						return true;
//					}
//
//					return false;
//				}
//			});
//			popup.show();
//		}
//	}
	
	/**
	 * @return whether or not we can write to the external storage
	 */
	private boolean externalStorageWritable() {
		String state = Environment.getExternalStorageState();
		if (Environment.MEDIA_MOUNTED.equals(state)) return true;
		else return false;
	}
	
	//Called internally to determine if a file / tab name is valid
	protected boolean validateFileName(String title) {
		//Make sure that the tab name's length > 0
		if (title.length() <= 0) {
			error(getResources().getText(R.string.name_invalid_no_char));
			return false;
		}
		
		//Check to make sure that the first character isn't a number and isn't an underscore
		char first = title.charAt(0);
		if ((first >= '0' && first <= '9') || first == '_') {
			error(getResources().getText(R.string.name_invalid_first_char));
			return false;
		}
		
		//Check all of the characters
		for (int i = 0; i < title.length(); i ++) {
			char c = title.charAt(i);
			if (c >= '0' && c <= '9') continue;
			if (c >= 'a' && c <= 'z') continue;
			if (c >= 'A' && c <= 'Z') continue;
			if (c == '_') continue;
			
			error(getResources().getText(R.string.name_invalid_char));
			return false;
		}
		
		//Make sure that this file / tab doesn't already exist
		for (SketchFile meta : tabs) {
			if (meta.getTitle().equals(title)) {
				error(getResources().getText(R.string.name_invalid_same_title));
				return false;
			}
		}
		
		return true;
	}
	
	private void launchTools() {
		final ArrayList<Tool> toolList = getGlobalState().getToolsInList();
		
		//Display a dialog containing the list of tools
		
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(R.string.tools);
		if(toolList.size() > 0) {
			//Populate the list
			builder.setItems(getGlobalState().listToolsInList(), new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					runOnUiThread(toolList.get(which));
				}
			});
		} else {
			//Eh... there should ALWAYS be tools, unless something funky is going on
			System.err.println("Couldn't find any tools... uh-oh...");
		}
		
		final AlertDialog dialog = builder.create();
		
		dialog.setCanceledOnTouchOutside(true);
		dialog.show();
	}
	
	public void launchImportLibrary() {
		getGlobalState().rebuildLibraryList();
		final String[] libList = getGlobalState().listLibraries();
		
		//Display a dialog containing the list of libraries
		
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(R.string.import_library);
		if(libList.length > 0) {
			//Populate the list
			builder.setItems(libList, new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					addImports(getGlobalState().getLibraryByName(libList[which]).getPackageList(getGlobalState()));
				}
			});
		} else {
			//Show a message to the user
			//This is a real hack... and a strong argument for using XML / layout inflaters
			
			TextView content = new TextView(this);
			content.setText(R.string.no_contributed_libraries); //The text we want
			content.setTextColor(getResources().getColor(R.color.grayed_out)); //The color we want
			content.setGravity(Gravity.CENTER); //Centered
			content.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)); //Centered...
			content.setPadding(60, 60, 60, 60); //... CENTERED!!!
			content.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
			
			builder.setView(content);
		}
		
		//The "Manage Libraries" button - null so that it won't automatically close itself
		builder.setNeutralButton(R.string.manage_libraries, null);
		final AlertDialog dialog = builder.create();
		
		//Fancy stuff...
		//StackOverflow: http://stackoverflow.com/questions/2620444/how-to-prevent-a-dialog-from-closing-when-a-button-is-clicked
		dialog.setOnShowListener(new DialogInterface.OnShowListener() {
			@Override
			public void onShow(final DialogInterface dialog) {
				Button b = ((AlertDialog) dialog).getButton(AlertDialog.BUTTON_NEUTRAL);
				b.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View view) {
						launchManageLibraries();
						//It would be better if the dialog didn't fade out when we pressed the button... but this will have to do
						//...it's better than having it reappear when we back out of the library manager
						dialog.dismiss();
					}
				});
			}
		});
		
		dialog.setCanceledOnTouchOutside(true);
		dialog.show();
	}
	
	public void launchManageLibraries() {
		getGlobalState().rebuildLibraryList();
		
		Intent intent = new Intent(this, LibraryManagerActivity.class);
		startActivity(intent);
	}
	
	//Called internally to open the Sketch Properties activity
	private void launchSketchProperties() {
		Intent intent = new Intent(this, SketchPropertiesActivity.class);
		startActivity(intent);
	}
	
	//Called internally to open the Settings activity
	private void launchSettings() {
		startActivity(new Intent(this, SettingsActivity.class));
	}
	
	/**
	 * Adds the imports of the given library to the top of the sketch, selecting the first tab if necessary
	 * 
	 * @param imports
	 */
	public void addImports(String[] imports) {
		//We can't have people messing with examples...
		if(getGlobalState().isExample())
			return;
		
		//NOTE: We don't check to see if the user has already imported this library. The desktop PDE doesn't either, so who cares?
		
		//Build a formatted list of imports
		String importList = "";
		
		//Using "im" - "import" doesn't work for obvious reasons
		for(String im : imports) {
			//Just to be safe, import everything
			importList += "import " + im + ".*;\n";
		}
		
		//Extra newline
		importList += "\n";
		
		//Sanity check
		if(getCodeCount() <= 0)
			return;
		
		//Select the first tab
//		tabBar.selectTab(0);
		selectCode(0);
		
//		CodeEditText code = (CodeEditText) findViewById(R.id.code);
		CodeEditText code = getSelectedCodeArea();
		
		//Select the top
		code.setSelection(0);
		
		//Insert the import statements
		code.setUpdateText(importList + code.getText());
		
		//Update the syntax highlighter
		code.updateTokens();
		code.updateBracketMatch();
	}
	
	/**
	 * NOTE: This is not currently correctly implemented
	 * 
	 * @return whether or not the sketch is saved
	 */
	public boolean isSaved() {
		//TODO make the is-saved functionality work
		
		return saved;
	}
	
	/**
	 * NOTE: This is not currently correctly implemented
	 * 
	 * @param saved
	 */
	public void setSaved(boolean saved) {
		this.saved = saved;
	}
	
	/**
	 * @return an ArrayList of SketchFile tabs
	 */
	public ArrayList<SketchFile> getSketchFiles() {
		return tabs;
	}
	
	/**
	 * @return an array containing all of the tabs' SketchFile objects
	 */
	public SketchFile[] getTabMetas() {
		SketchFile[] metas = new SketchFile[getCodeCount()];
		
		for (int i = 0; i < getCodeCount(); i ++) {
			metas[i] = tabs.get(i);
		}
		
		return metas;
	}
	
	/**
	 * Add a message to the console and automatically scroll to the bottom (if the user has this feature turned on)
	 * 
	 * @param msg
	 */
	public void postConsole(String msg) {
		// Check to see if we've suspended messages
		if (FLAG_SUSPEND_OUT_STREAM.get() && !PreferenceManager.getDefaultSharedPreferences(this).getBoolean("pref_debug_global_verbose_output", false)) {
			return;
		}
		
		final TextView tv = (TextView) findViewById(R.id.console);
		
		// Add the text
		tv.append(msg);
		
		final ScrollView scroll = ((ScrollView) findViewById(R.id.console_scroller));
		final HorizontalScrollView scrollX = ((HorizontalScrollView) findViewById(R.id.console_scroller_x));
		
		// Scroll to the bottom (if the user has this feature enabled)
		if(PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getBoolean("pref_scroll_lock", true))
			scroll.post(new Runnable() {
				@Override
				public void run() {
					// Scroll to the bottom
					scroll.fullScroll(ScrollView.FOCUS_DOWN);
					
					scrollX.post(new Runnable() {
						public void run() {
							// Don't scroll horizontally at all...
							// TODO This doesn't really work
							scrollX.scrollTo(0, 0);
						}
					});
			}});
	}
	
	// Listener class for managing message area drag events
	public class MessageTouchListener implements android.view.View.OnLongClickListener, android.view.View.OnTouchListener {
		private boolean pressed;
		private int touchOff;
		
		private View console;
		private View content;
		
		public MessageTouchListener() {
			super();
			
			pressed = false;
			
			// Store necessary views globally
			console = findViewById(R.id.console_scroller);
			content = findViewById(R.id.content);
		}
		
		@SuppressWarnings("deprecation")
		@Override
		public boolean onTouch(View view, MotionEvent event) {
			// Don't resize the console if there is no console to speak of
			if(keyboardVisible)
				return false;
			
			// Get the offset relative to the touch
			if(event.getAction() == MotionEvent.ACTION_DOWN)
				touchOff = (int) event.getY();
			
			if(pressed) {
				switch(event.getAction()) {
				case MotionEvent.ACTION_MOVE:
					if(message == -1) {
						message = findViewById(R.id.buffer).getHeight();
					}
					
					// An important note for understanding the following code:
					// The tab bar is actually inside the code area pager, so the height of "code"
					// includes the height of the tab bar
					
					// Calculate maximum possible code view height
					int maxCode = content.getHeight() - message - codeTabStrip.getHeight() - (extraHeaderView != null ? extraHeaderView.getHeight() : 0);
					
					// Find relative movement for this event
					int y = (int) event.getY() - touchOff;
					
					//Calculate the new dimensions of the console
					int consoleDim = console.getHeight() - y;
					if (consoleDim < 0) {
						consoleDim = 0;
					}
					if (consoleDim > maxCode) {
						consoleDim = maxCode;
					}
					
					// Calculate the new dimensions of the code view
					int codeDim = maxCode - consoleDim;
					
					// Set the new dimensions
					codePager.setLayoutParams(new android.widget.LinearLayout.LayoutParams(android.widget.LinearLayout.LayoutParams.MATCH_PARENT, codeDim + codeTabStrip.getHeight()));
					console.setLayoutParams(new android.widget.LinearLayout.LayoutParams(android.widget.LinearLayout.LayoutParams.MATCH_PARENT, consoleDim));
					
					firstResize = false;
					
					if (consoleDim > 0) {
						consoleWasHidden = false;
					}
					
					return true;
				case MotionEvent.ACTION_UP:
					pressed = false;
					
					LinearLayout buffer = (LinearLayout) findViewById(R.id.buffer);
					TextView messageArea = (TextView) findViewById(R.id.message);
					
					// Change the message area drawable and maintain styling
					if(errorMessage) {
						buffer.setBackgroundDrawable(getResources().getDrawable(R.drawable.back_error));
						messageArea.setTextColor(getResources().getColor(R.color.error_text));
					} else {
						buffer.setBackgroundDrawable(getResources().getDrawable(R.drawable.back));
						messageArea.setTextColor(getResources().getColor(R.color.message_text));
					}
					
					return true;
				}
			}
			
			return false;
		}
		
		@SuppressWarnings("deprecation")
		@Override
		public boolean onLongClick(View view) {
			// Don't resize the console if there is no console to speak of
			if(keyboardVisible)
				return false;
			
			pressed = true;
			
			LinearLayout buffer = (LinearLayout) findViewById(R.id.buffer);
			TextView messageArea = (TextView) findViewById(R.id.message);
			
			// Change the message area drawable and maintain styling
			if(errorMessage) {
				buffer.setBackgroundDrawable(getResources().getDrawable(R.drawable.back_error_selected));
				messageArea.setTextColor(getResources().getColor(R.color.error_text));
			} else {
				buffer.setBackgroundDrawable(getResources().getDrawable(R.drawable.back_selected));
				messageArea.setTextColor(getResources().getColor(R.color.message_text));
			}
			
			// Provide haptic feedback (if the user has vibrations enabled)
			if(PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getBoolean("pref_vibrate", true))
				((android.os.Vibrator) getSystemService(VIBRATOR_SERVICE)).vibrate(200); //200 millis
			
			return true;
		}
	}
	
	// Custom console output stream, used for System.out and System.err
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
			
			if (!(FLAG_SUSPEND_OUT_STREAM.get() &&
					!PreferenceManager.getDefaultSharedPreferences(EditorActivity.this)
							.getBoolean("pref_debug_global_verbose_output", false))) {
				
				runOnUiThread(new Runnable() {
					public void run() {
						//	Write the value to the console
						postConsole(value);
					}
				});
			}
		}
		
		@Override
		public void write(int b) {
			single[0] = (byte) b;
			write(single, 0, 1);
		}
	}
	
	// Upgrade Changes are used to run specific code that facilitates an app upgrade
	
	public abstract class UpgradeChange implements Runnable {
		public int changeVersion;
		
		public UpgradeChange(int changeVersion) {
			this.changeVersion = changeVersion;
		}
	}
	
	public void runUpgradeChanges(int from, int to) {
		ArrayList<UpgradeChange> upgradeChanges = new ArrayList<UpgradeChange>();
		
		// Hard-coded list of changes that have occurred
		
		// v0.3.3 Alpha-pre1

		// Update some of the default examples
		upgradeChanges.add(new UpgradeChange(13) {
			@Override
			public void run() {
				// Update default examples in a separate thread
				new Thread(new Runnable() {
					public void run() {
						copyAssetFolder(getAssets(), "examples", getGlobalState().getStarterExamplesFolder().getAbsolutePath());
					}
				}).start();
			}
		});
		
		// v0.3.3 Alpha-pre2
		
		// Change preference "Delete old build folder" to "Keep build folder" (invert)
		upgradeChanges.add(new UpgradeChange(14) {
			@Override
			public void run() {
				SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(EditorActivity.this);
				
				if (prefs.contains("pref_build_discard")) {
					SharedPreferences.Editor edit = prefs.edit();
					edit.putBoolean("pref_build_folder_keep", !prefs.getBoolean("pref_build_discard", true));
					edit.apply();
				}
			}
		});
		
		// v0.4.0 Alpha-pre1
		
		// Upgrade to Android Mode 3.0, changes minSdk from API 10 to 15; update android.jar file
		upgradeChanges.add(new UpgradeChange(16) {
			@Override
			public void run() {
				getGlobalState().getTaskManager().launchTask("recopyAndroidJarTask", false, null, false, new CopyAndroidJarTask());
			}
		});
		
		// Switch to the new examples repo
		upgradeChanges.add(new UpgradeChange(16) {
			@Override
			public void run() {
				try {
					// Just delete the old repository, the new one will be downloaded automatically
					
					// But make sure that we don't delete the starter examples (if the user hasn't
					// downloaded the full repo)
					File examplesRepo = getGlobalState().getExamplesRepoFolder();
					if (examplesRepo.exists()) {
						APDE.deleteFile(examplesRepo);
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		});
		
		// Process changes
		
		for (UpgradeChange upgradeChange : upgradeChanges) {
			if (from < upgradeChange.changeVersion && to >= upgradeChange.changeVersion) {
				upgradeChange.run();
			}
		}
	}
}
