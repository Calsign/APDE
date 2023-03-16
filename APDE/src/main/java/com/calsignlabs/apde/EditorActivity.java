package com.calsignlabs.apde;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.SystemClock;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
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
import android.view.animation.AccelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.DecelerateInterpolator;
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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;
import com.calsignlabs.apde.support.documentfile.DocumentFile;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.test.espresso.idling.concurrent.IdlingScheduledThreadPoolExecutor;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.calsignlabs.apde.build.Build;
import com.calsignlabs.apde.build.CompilerProblem;
import com.calsignlabs.apde.build.ComponentTarget;
import com.calsignlabs.apde.build.ExtractStaticBuildResources;
import com.calsignlabs.apde.build.Manifest;
import com.calsignlabs.apde.build.SketchPreviewerBuilder;
import com.calsignlabs.apde.build.dag.BuildContext;
import com.calsignlabs.apde.build.dag.ModularBuild;
import com.calsignlabs.apde.support.MaybeDocumentFile;
import com.calsignlabs.apde.support.ResizeAnimation;
import com.calsignlabs.apde.tool.FindReplace;
import com.calsignlabs.apde.tool.Tool;
import com.google.android.gms.wearable.MessageClient;
import com.google.android.gms.wearable.Wearable;
import com.google.android.material.tabs.TabLayout;

import org.json.JSONException;
import org.json.JSONObject;
import org.xml.sax.SAXException;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map.Entry;
import java.util.Stack;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
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
	protected ViewGroup tabBarContainer;
	
	protected ImageButton undoButton;
	protected ImageButton redoButton;
	
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
	
	private ViewPager consoleWrapperPager;
	
	public ArrayList<CompilerProblem> compilerProblems;
	private ListView problemOverviewList;
	private ProblemOverviewListAdapter problemOverviewListAdapter;
	
	private View extraHeaderView;
	
	//Possible dialog results
	private final static int RENAME_TAB = 0;
	private final static int NEW_TAB = 1;
	
	//Listener for managing the sliding message area
	private MessageTouchListener messageListener;
	//The height of the message area...
	private int message = -1;
	
	//Custom output / error streams for printing to the console
	private ConsoleStream outStream;
	private ConsoleStream errStream;
	protected AtomicBoolean FLAG_SUSPEND_OUT_STREAM = new AtomicBoolean(false);
	
	//Recieve log / console output from sketches
	private BroadcastReceiver consoleBroadcastReceiver;
	private MessageClient.OnMessageReceivedListener wearConsoleReceiver;
	
	//Whether or not we are currently building a sketch
	private boolean building;
	
	/**
	 * Type of message displayed in the message bar - message, error, or warning.
	 */
	protected enum MessageType {
		MESSAGE, ERROR, WARNING;
		
		public String serialize() {
			return toString();
		}
		
		public static MessageType deserialize(String serialized) {
			switch (serialized) {
				case "MESSAGE":
					return MESSAGE;
				case "ERROR":
					return ERROR;
				case "WARNING":
					return WARNING;
				// Compatibility - fixes problems when upgrading from older versions of APDE
				case "true":
					return ERROR;
				case "false":
					return MESSAGE;
				default:
					return MESSAGE;
			}
		}
	}
	
	// Whether or not the message area is currently displaying an error message
	private MessageType messageType = MessageType.MESSAGE;
	
	// Whether or not the special character inserts tray is currently visible
	private boolean charInserts = false;
	private boolean problemOverview = false;
	private ImageButton toggleCharInserts;
	private ImageButton toggleProblemOverview;
	
	// Intent flag to delete the old just-installed APK file
	public static final int FLAG_DELETE_APK = 5;
	// Intent flag to launch the just-installed sketch
	public static final int FLAG_LAUNCH_SKETCH = 6;
	// Intent flag to set the just-installed wallpaper
	public static final int FLAG_SET_WALLPAPER = 7;
	// Intent flag to start the preview after installing the sketch previewer APK
	public static final int FLAG_RUN_PREVIEW = 8;
	
	public ScheduledThreadPoolExecutor autoSaveTimer;
	public ScheduledFuture<?> autoSaveTimerTask;
	public Runnable autoSaveTimerAction = () -> {
		// This looks really messy, but we need to run on the UI thread in order to display
		// the "sketch has been saved" message in the message area
		runOnUiThread(this::autoSave);
	};
	
	public ScheduledThreadPoolExecutor autoCompileTimer;
	public ScheduledFuture<?> autoCompileTask;
	public Runnable autoCompileAction = this::autoCompile;
	
	protected ComponentTarget componentTarget;
	
	private boolean FLAG_SCREEN_OVERLAY_INSTALL_ANYWAY = false;
	private boolean FLAG_PREVIEW_COMPONENT_TARGET_NEWLY_UPDATED = false;
	private boolean FLAG_FIRST_AUTO_COMPILE = true;
	
    @SuppressLint("NewApi")
	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_editor);
		
		toolbar = (Toolbar) findViewById(R.id.toolbar);
		toolbar.setBackgroundColor(getResources().getColor(R.color.bar_overlay));
		setSupportActionBar(toolbar);
		
		// Create custom output / error streams for the console
		outStream = new ConsoleStream(System.out);
		errStream = new ConsoleStream(System.err);
		
		// Set the custom output / error streams
		System.setOut(new PrintStream(outStream));
		System.setErr(new PrintStream(errStream));
		
		// Initialize log / console receiver
		consoleBroadcastReceiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				char severity = intent.getCharExtra("com.calsignlabs.apde.LogSeverity", 'o');
				String message = intent.getStringExtra("com.calsignlabs.apde.LogMessage");
				String exception = intent.getStringExtra("com.calsignlabs.apde.LogException");
				
				handleSketchConsoleLog(severity, message, exception);
			}
		};
		
		// Register receiver for sketch logs / console output
		registerReceiver(consoleBroadcastReceiver, new IntentFilter("com.calsignlabs.apde.LogBroadcast"));
	
		wearConsoleReceiver = messageEvent -> {
			if (messageEvent.getPath().equals("/apde_receive_logs")) {
				try {
					JSONObject json = new JSONObject(new String(messageEvent.getData()));
					String severityStr = json.getString("severity");
					
					if (severityStr.length() != 1) {
						System.err.println("Wear console receiver - invalid severity: \"" + severityStr + "\"");
						return;
					}
					
					char severity = severityStr.charAt(0);
					String message = json.getString("message");
					String exception = json.getString("exception");
					
					handleSketchConsoleLog(severity, message, exception);
				} catch (JSONException e) {
					e.printStackTrace();
				}
			}
		};
	
		Wearable.getMessageClient(this).addListener(wearConsoleReceiver);
		
		getGlobalState().initTaskManager();
		
		// Make sure that we have a good sketchbook folder to use
		getGlobalState().getSketchbookDrive();
		
		//Initialize the list of tabs
		tabs = new ArrayList<SketchFile>();
        
		codePager = findViewById(R.id.code_pager);
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
		
		codeTabStrip = findViewById(R.id.code_pager_tabs);
		codeTabStrip.setBackgroundColor(getResources().getColor(R.color.bar_overlay));
		codeTabStrip.setSelectedTabIndicatorColor(getResources().getColor(R.color.holo_select));
		codeTabStrip.setSelectedTabIndicatorHeight((int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 5, getResources().getDisplayMetrics()));
		codeTabStrip.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
			@Override
			public void onTabSelected(TabLayout.Tab tab) {
				// If there are problems displayed, then we might want to switch the one visible in
				// the message area when we switch tabs
				if (getSelectedCodeArea() != null) {
					getSelectedCodeArea().updateCursorCompilerProblem();
				}
				
				// Different undo/redo history for different tabs
				correctUndoRedoEnabled();
			}
			
			@Override
			public void onTabUnselected(TabLayout.Tab tab) {}
			
			@Override
			public void onTabReselected(TabLayout.Tab tab) {
				View anchor = ((LinearLayout) codeTabStrip.getChildAt(0)).getChildAt(tab.getPosition());
				EditorActivity.this.onTabReselected(anchor);
			}
		});
		
		tabBarContainer = findViewById(R.id.tab_bar_container);
		
		undoButton = findViewById(R.id.undo_redo_undo);
		redoButton = findViewById(R.id.undo_redo_redo);
		
		getGlobalState().assignLongPressDescription(undoButton, R.string.editor_menu_undo);
		getGlobalState().assignLongPressDescription(redoButton, R.string.editor_menu_redo);
		
		undoButton.setOnClickListener(view -> undo());
		redoButton.setOnClickListener(view -> redo());
		
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
        drawerToggle = new ActionBarDrawerToggle(this, drawer, toolbar, R.string.drawer_open_accessibility_text, R.string.drawer_close_accessibility_text) {
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
    				} else if (drawerRecentSketch) {
						getSupportActionBar().setSubtitle(getResources().getString(R.string.drawer_folder_recent) + "/");
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
        drawer.addDrawerListener(drawerToggle);
        
        // Detect drawer sketch selection events
        drawerList.setOnItemClickListener((parent, view, position, id) -> {
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
					drawerSketchLocationType = null;
					drawerSketchPath = "";
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
					
					try {
						if (drawerRecentSketch) {
							APDE.SketchMeta sketch = getGlobalState().getRecentSketches().get(position - 1); // "position - 1" because the first item is the UP button
							
							loadSketch(sketch.getPath(), sketch.getLocation());
						} else {
							loadSketch(drawerSketchPath + "/" + item.getText(), drawerSketchLocationType);
						}
					} catch (MaybeDocumentFile.MaybeDocumentFileException e) {
						e.printStackTrace();
					}
					
					drawer.closeDrawers();
					
					break;
				}
			}
			
			forceDrawerReload();
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
			private View buffer = findViewById(R.id.buffer);
			private TextView messageArea = findViewById(R.id.message);
			private View console = findViewById(R.id.console_wrapper);
			private View content = findViewById(R.id.content);
			private FrameLayout autoCompileProgress = findViewById(R.id.auto_compile_progress_wrapper);
			
			private int previousVisibleHeight = -1;
   
			@SuppressWarnings("deprecation")
			@Override
			public void onGlobalLayout() {
				// We only want to update if the activity has changed size
				Rect r = new Rect();
				activityRootView.getWindowVisibleDisplayFrame(r);
				int visibleHeight = r.bottom - r.top;
				if (visibleHeight == previousVisibleHeight) {
					return;
				}
				previousVisibleHeight = visibleHeight;
				
				// Calculate the difference in height
				int heightDiff = activityRootView.getRootView().getHeight() - visibleHeight;
				
				if (oldCodeHeight == -1) {
					oldCodeHeight = codePager.getHeight();
				}
				
				// An important note for understanding the following code:
				// The tab bar is actually inside the code area pager, so the height of "code"
				// includes the height of the tab bar
				
				if (message == -1) {
					message = buffer.getHeight();
				}
				
				int totalHeight = content.getHeight() - message - (extraHeaderView != null ? extraHeaderView.getHeight() : 0) - tabBarContainer.getHeight() - autoCompileProgress.getHeight();
				// If the difference is bigger than 160dp, it's probably the keyboard
				boolean keyboardCoveringScreen = heightDiff > getResources().getDimension(R.dimen.keyboard_visibility_change_threshold);
				boolean allowSoftKeyboard = !PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getBoolean("use_hardware_keyboard", false);
				
				if (keyboardCoveringScreen && !keyboardVisible) {
					// The keyboard just appeared
					
					// Hide the soft keyboard if it's trying to show its dirty face...
					// ...and the user doesn't want it
					if (!allowSoftKeyboard) {
						InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
						imm.hideSoftInputFromWindow(activityRootView.getWindowToken(), 0);
						return;
					}
					
					keyboardVisible = true;
					
					// Configure the layout for the keyboard
					
					if (firstResize) {
						firstResize = false;
					} else {
						oldCodeHeight = codePager.getHeight();
					}
					
					if (totalHeight > oldCodeHeight) {
						codePager.startAnimation(new ResizeAnimation<LinearLayout>(codePager, ResizeAnimation.DEFAULT, ResizeAnimation.DEFAULT, ResizeAnimation.DEFAULT, totalHeight));
						console.startAnimation(new ResizeAnimation<LinearLayout>(console, ResizeAnimation.DEFAULT, ResizeAnimation.DEFAULT, ResizeAnimation.DEFAULT, 0));
					} else {
						codePager.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, totalHeight));
						console.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0));
					}
					
					// Remove the focus from the Message slider if it has it and maintain styling
					switch (messageType) {
						case MESSAGE:
							buffer.setBackgroundDrawable(getResources().getDrawable(R.drawable.back));
							buffer.setBackgroundColor(getResources().getColor(R.color.message_back));
							messageArea.setTextColor(getResources().getColor(R.color.message_text));
							break;
						case ERROR:
							buffer.setBackgroundDrawable(getResources().getDrawable(R.drawable.back_error));
							buffer.setBackgroundColor(getResources().getColor(R.color.error_back));
							messageArea.setTextColor(getResources().getColor(R.color.error_text));
							break;
						case WARNING:
							buffer.setBackgroundDrawable(getResources().getDrawable(R.drawable.back_warning));
							buffer.setBackgroundColor(getResources().getColor(R.color.warning_back));
							messageArea.setTextColor(getResources().getColor(R.color.warning_text));
							break;
					}
					CodeEditText codeArea = getSelectedCodeArea();
					
					if (codeArea != null) {
						codeArea.updateBracketMatch();
					}
					
					// Don't do anything if the user has disabled the character insert tray
					if (PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getBoolean("char_inserts", true)) {
						//Update the character insert tray
						toggleCharInsertsProblemOverviewButton(false, true);
						
						if (charInserts) {
							View charInsertTray = findViewById(R.id.char_insert_tray);
							
							correctMessageAreaHeight();
							toggleCharInserts.setImageResource(messageType != MessageType.MESSAGE ? R.drawable.ic_caret_right_white : R.drawable.ic_caret_right_black);
							
							// This is really screwy, but it gets the job done
							messageArea.getLayoutParams().width = 0;
							charInsertTray.setVisibility(View.VISIBLE);
							charInsertTray.getLayoutParams().width = findViewById(R.id.message_char_insert_wrapper).getWidth();
							
							messageArea.requestLayout();
							charInsertTray.requestLayout();
						}
					}
				} else if (!keyboardCoveringScreen && keyboardVisible) {
					// The keyboard just disappeared
					
					codePager.startAnimation(new ResizeAnimation<LinearLayout>(codePager, ResizeAnimation.DEFAULT, ResizeAnimation.DEFAULT, ResizeAnimation.DEFAULT, oldCodeHeight, false));
					console.startAnimation(new ResizeAnimation<LinearLayout>(console, ResizeAnimation.DEFAULT, totalHeight - codePager.getHeight(), ResizeAnimation.DEFAULT, totalHeight - oldCodeHeight, false));
					
					keyboardVisible = false;
					
					if (oldCodeHeight > 0) {
						consoleWasHidden = false;
					}
					
					if (getSelectedCodeArea() != null) {
						// Remove any unnecessary focus from the code area
						getSelectedCodeArea().clearFocus();
						getSelectedCodeArea().matchingBracket = -1;
					}
					
					// Update the character insert tray
					toggleCharInsertsProblemOverviewButton(true, true);
					
					findViewById(R.id.message).setVisibility(View.VISIBLE);
					findViewById(R.id.char_insert_tray).setVisibility(View.GONE);
					
					hideCharInsertsNoAnimation(false);
				} else if (keyboardVisible) {
					// Minor adjustment because the window size changed for some reason
					
					codePager.getLayoutParams().height = totalHeight;
					console.getLayoutParams().height = 0;
					
					codePager.requestLayout();
					console.requestLayout();
				} else {
					// Minor adjustment because the window size changed for some reason
					
					codePager.getLayoutParams().height = Math.min(totalHeight, codePager.getHeight());
					console.getLayoutParams().height = Math.max(totalHeight - codePager.getHeight(), 0);
					
					codePager.requestLayout();
					console.requestLayout();
				}
			}
		});
		
		// Set up character insert tray toggle
		toggleCharInserts = findViewById(R.id.toggle_char_inserts);
		toggleCharInserts.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View view) {
				toggleCharInserts();
			}
		});
		toggleProblemOverview = findViewById(R.id.toggle_problem_overview);
		toggleProblemOverview.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View view) {
				toggleProblemOverview();
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
		} else if (savedInstanceState == null) {
			// If the "What's New" screen is visible, wait to show the examples updates screen
			
			// Update examples repository
			getGlobalState().initExamplesRepo();
		}
		
		// Disable SSL3
		// Only needed on Android 4.4 and below
		// And only affects Git actions at present
		// But might as well stick it here
		getGlobalState().disableSsl3();
		
		codePagerAdapter.notifyDataSetChanged();
		codeTabStrip.setupWithViewPager(codePager);
		
		compilerProblems = new ArrayList<>();
		problemOverviewList = findViewById(R.id.problem_overview_list);
		problemOverviewListAdapter = new ProblemOverviewListAdapter(this, R.layout.problem_overview_list_item, compilerProblems);
		
		// Don't steal click events from the problem overview list
		getConsoleWrapper().setClickable(false);
		
		problemOverviewList.setAdapter(problemOverviewListAdapter);
		
		problemOverviewList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
				CompilerProblem problem = problemOverviewListAdapter.getItem(i);
				if (problem != null) {
					if (problem.sketchFile != null) {
						highlightTextExt(problem.sketchFile.getIndex(), problem.line, problem.start, problem.length);
					}
					if (problem.isError()) {
						errorExt(problem.getMessage());
					} else {
						warningExt(problem.getMessage());
					}
				}
			}
		});
		
		// Copy the problem description to the clipboard when the user long-presses
		problemOverviewList.setOnItemLongClickListener((adapterView, view, i, l) -> {
			ClipboardManager clipboardManager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
			if (clipboardManager != null) {
				CompilerProblem problem = problemOverviewListAdapter.getItem(i);
				if (problem != null) {
					String text = getProblemOverviewDescription(EditorActivity.this, problem).toString();
					ClipData clipData = ClipData.newPlainText(getResources().getString(R.string.problem_overview_list_copy_description), text);
					clipboardManager.setPrimaryClip(clipData);
					
					Toast.makeText(EditorActivity.this, R.string.problem_overview_list_copy_toast_message, Toast.LENGTH_SHORT).show();
				}
				
				return true;
			} else {
				return false;
			}
		});
		
		// We have both pages defined in layout, but ViewPager needs this to do things correctly
		// We don't actually instantiate either page, we just pass references to the existing ones
		consoleWrapperPager = findViewById(R.id.console_wrapper_pager);
		consoleWrapperPager.setAdapter(new PagerAdapter() {
			@Override
			public int getCount() {
				return 2;
			}
			
			@Override
			public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
				return view == object;
			}
			
			@Override
			public Object instantiateItem(ViewGroup container, int position) {
				switch (position) {
					case 0:
						return findViewById(R.id.console_scroller);
					case 1:
						return findViewById(R.id.problem_overview_wrapper);
					default:
						// Complains about null...
						return new Object();
				}
			}
			
			@Override
			public void destroyItem(ViewGroup container, int position, Object object) {
				// Do nothing
			}
		});
		
		if (false) {
			autoSaveTimer = new IdlingScheduledThreadPoolExecutor("autoSaveTimer", 1, Executors.defaultThreadFactory());
			autoCompileTimer = new IdlingScheduledThreadPoolExecutor("autoCompileTimer", 1, Executors.defaultThreadFactory());
		} else {
			autoSaveTimer = new ScheduledThreadPoolExecutor(1);
			autoCompileTimer = new ScheduledThreadPoolExecutor(1);
		}
		
		// Fallback component target
		setComponentTarget(ComponentTarget.PREVIEW);
		
		try {
			// Try to load the auto-save sketch, otherwise set the editor up as a new sketch
			if (!loadSketchStart()) {
				getGlobalState().selectNewTempSketch();
				addDefaultTab(APDE.DEFAULT_SKETCH_TAB);
				autoSave();
			}
			
			getGlobalState().writeCodeDeletionDebugStatus("onCreate() after loadSketchStart()");
		} catch (Exception e) {
			// Who knows really...
			e.printStackTrace();
		}
		
		// On first run, we want to switch to the preview component target to demo the new feature
		if (FLAG_PREVIEW_COMPONENT_TARGET_NEWLY_UPDATED) {
			setComponentTarget(ComponentTarget.PREVIEW);
			FLAG_PREVIEW_COMPONENT_TARGET_NEWLY_UPDATED = false;
		}
		
		toggleAutoCompileIndicator(false);
    }
	
	@Override
	public void onStart() {
		super.onStart();
		
		getGlobalState().writeCodeDeletionDebugStatus("onStart()");
		
		APDE.StorageDrive.StorageDriveType storageDriveType =
				getGlobalState().getSketchbookStorageDrive().type;
		boolean isExternal =
				storageDriveType.equals(APDE.StorageDrive.StorageDriveType.PRIMARY_EXTERNAL)
						|| storageDriveType.equals(APDE.StorageDrive.StorageDriveType.EXTERNAL);
		
		if (isExternal) {
			// Make sure we have WRITE_EXTERNAL_STORAGE
			if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
				AlertDialog.Builder builder = new AlertDialog.Builder(this);
				builder.setTitle(R.string.external_storage_dialog_title);
				builder.setMessage(R.string.external_storage_dialog_message);
				builder.setPositiveButton(R.string.external_storage_dialog_grant_permission_button, (dialog, which) -> {
					ActivityCompat.requestPermissions(this, new String[] {android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSIONS_REQUEST_CODE);
					dialog.cancel();
					
					getGlobalState().checkAndroid11SketchbookAccess(this, false);
				});
				builder.setNeutralButton(R.string.external_storage_dialog_use_internal_storage_button,
						(dialog, which) -> {
							getGlobalState().useInternalStorageDrive();
							dialog.cancel();
						});
				
				builder.show();
			} else {
				getGlobalState().checkAndroid11SketchbookAccess(this, false);
			}
		}
	}
	
	protected void handleSketchConsoleLog(char severity, String message, String exception) {
		// We can show different colors for different severities if we want to... later...
		switch (severity) {
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
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);
	}
	
	public ComponentTarget getComponentTarget() {
		return componentTarget;
	}
	
	public void setComponentTarget(ComponentTarget componentTarget) {
		this.componentTarget = componentTarget;
		invalidateOptionsMenu();
		if (!FLAG_FIRST_AUTO_COMPILE) {
			scheduleAutoCompile(true);
		}
	}
	
	public int getSelectedCodeIndex() {
		return codePager.getCurrentItem();
	}
	
	public void selectCode(int index) {
		codePager.setCurrentItem(index);
	}
	
	public int getSketchFileIndex(SketchFile sketchFile) {
		return tabs.indexOf(sketchFile);
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
		return tabs.size() > 0 && getSelectedCodeIndex() < tabs.size() ? tabs.get(getSelectedCodeIndex()) : null;
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
	
	public View getConsoleWrapper() {
		return findViewById(R.id.console_wrapper);
	}
	
	public void setExtraHeaderView(View headerView) {
		extraHeaderView = headerView;
	}
	
	public View getExtraHeaderView() {
		return extraHeaderView;
	}
	
	@Override
	public void onSaveInstanceState(Bundle outState) {
		getGlobalState().writeCodeDeletionDebugStatus("onSaveInstanceState()");
		
		try {
			TextView messageArea = (TextView) findViewById(R.id.message);
			TextView console = (TextView) findViewById(R.id.console);
			ScrollView consoleScroller = (ScrollView) findViewById(R.id.console_scroller);
			HorizontalScrollView consoleScrollerX = (HorizontalScrollView) findViewById(R.id.console_scroller_x);
			
			outState.putString("consoleText", console.getText().toString());
			outState.putInt("consoleScrollPos", consoleScroller.getScrollY());
			outState.putInt("consoleScrollPosX", consoleScrollerX.getScrollX());
			outState.putString("messageText", messageArea.getText().toString());
			outState.putString("messageIsError", messageType.serialize());
		} catch (Exception e) {
			// Just to be safe
			e.printStackTrace();
		}
		
		super.onSaveInstanceState(outState);
	}
	
	@Override
	public void onRestoreInstanceState(Bundle savedInstanceState) {
		try {
			// We're going to re-add all of the fragments, so get rid of the old ones
			List<Fragment> fragments = getSupportFragmentManager().getFragments();
			if (fragments != null) {
				for (Fragment fragment : fragments) {
					if (fragment != null) {
						getSupportFragmentManager().beginTransaction().remove(fragment).commit();
					}
				}
			}
			
			if (savedInstanceState != null) {
				String consoleText = savedInstanceState.getString("consoleText");
				final int consoleScrollPos = savedInstanceState.getInt("consoleScrollPos");
				final int consoleScrollPosX = savedInstanceState.getInt("consoleScrollPosX");
				String messageText = savedInstanceState.getString("messageText");
				MessageType msgType = MessageType.deserialize(savedInstanceState.getString("messageIsError"));
				
				TextView console = (TextView) findViewById(R.id.console);
				final ScrollView consoleScroller = (ScrollView) findViewById(R.id.console_scroller);
				final HorizontalScrollView consoleScrollerX = (HorizontalScrollView) findViewById(R.id.console_scroller_x);
				
				if (consoleText != null) {
					// Assume that they're all there...
					
					console.setText(consoleText);
					
					// This doesn't actually work in practice because the text is always
					// replaced with "The sketch has been saved"...
					switch (msgType) {
						case MESSAGE:
							message(messageText);
							break;
						case ERROR:
							error(messageText);
							break;
						case WARNING:
							warning(messageText);
							break;
					}
					
					console.post(new Runnable() {
						@Override
						public void run() {
							consoleScroller.scrollTo(0, consoleScrollPos);
							consoleScrollerX.scrollTo(consoleScrollPosX, 0);
						}
					});
				}
			}
		} catch (Exception e) {
			// Who knows really...
			e.printStackTrace();
		}
		
		getGlobalState().writeCodeDeletionDebugStatus("onRestoreInstanceState()");
	}
    
    private static SparseArray<ActivityResultCallback> activityResultCodes = new SparseArray<ActivityResultCallback>();
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		getGlobalState().writeCodeDeletionDebugStatus("onActivityResult()");
		
    	// We want to see the sketch right away
    	if (requestCode == FLAG_LAUNCH_SKETCH) {
			// Note: we only get the result code if we pass it as an extra
			if (resultCode == RESULT_OK) {
				// The user installed the sketch, so launch the sketch
				if (getGlobalState().getPref("pref_debug_delay_before_run_sketch", false)) {
					// For some reason, on my phone I need a small delay before running the sketch.
					// This is probably a Samsung-specific thing because it seems to work fine
					// in the emulator.
					Handler handler = new Handler(getMainLooper());
					handler.postDelayed(() -> runOnUiThread((() ->
							Build.launchSketchPostLaunch(this))), 1000);
				} else {
					Build.launchSketchPostLaunch(this);
				}
			}
    		Build.cleanUpPostLaunch(this);
    	} else if (requestCode == FLAG_SET_WALLPAPER) {
    		// Note: we only get the result code if we pass it as an extra
    		if (resultCode == RESULT_OK) {
    			// The user installed the wallpaper, so launch wallpaper selector
				Build.setWallpaperPostLaunch(this);
			}
			Build.cleanUpPostLaunch(this);
		} else if (requestCode == FLAG_RUN_PREVIEW) {
    		// We just installed the sketch previewer, so now run the sketch again
			// This makes the whole experience as seamless as possible for the user
    		if (resultCode == RESULT_OK) {
    			if (getComponentTarget() == ComponentTarget.PREVIEW) {
					runApplication();
				}
			}
		} else if (requestCode == APDE.FLAG_SELECT_SKETCHBOOK_FOLDER) {
    		if (resultCode == RESULT_OK && data != null && data.getData() != null) {
    			getGlobalState().setSafStorageDrive(data.getData());
		    } else {
    			// TODO: how to handle errors?
		    }
	    }
    	
    	ActivityResultCallback action = activityResultCodes.get(requestCode);
    	
    	if (action != null) {
    		action.onActivityResult(requestCode, resultCode, data);
    		
    		activityResultCodes.remove(requestCode);
    	}
    	
    	super.onActivityResult(requestCode, resultCode, data);
    }
    
    public void selectFile(Intent intent, int requestCode, ActivityResultCallback callback) {
    	activityResultCodes.put(requestCode, callback);
		startActivityForResult(intent, requestCode);
    }
    
    public interface ActivityResultCallback {
    	void onActivityResult(int requestCode, int resultCode, Intent data);
    }
    
    @SuppressLint("NewApi")
	public void onResume() {
    	super.onResume();
		
		getGlobalState().writeCodeDeletionDebugStatus("onResume()");
		
    	//Reference the SharedPreferences text size value
//    	((CodeEditText) findViewById(R.id.code)).refreshTextSize();
		((TextView) findViewById(R.id.console)).setTextSize(Integer.parseInt(PreferenceManager.getDefaultSharedPreferences(this).getString("textsize_console", "14")));
    	
    	//Disable / enable the soft keyboard
        if(PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getBoolean("use_hardware_keyboard", false)) {
			getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
		} else {
			getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
		}
		
		// Correct dimensions of various things
		initCodeAreaAndConsoleDimensions();
		correctMessageAreaHeight();
        
        // Correctly size the code and console areas
        
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
		
		// Let's see if the user is trying to open a .PDE file...
		
		Intent intent = getIntent();
		
		if (intent.getAction() != null && (intent.getAction().equals(Intent.ACTION_VIEW) || intent.getAction().equals(Intent.ACTION_EDIT)) && intent.getData() != null) {
			String scheme = intent.getData().getScheme();
			String filePath = intent.getData().getPath();
			
			// Let's make sure we don't have any bad data...
			if (scheme != null && (scheme.equalsIgnoreCase("file") || scheme.equalsIgnoreCase("content")) && filePath != null && filePath.length() != 0) {
				// There are many different formats that are caught by the below if-else branching.
				// I have tested numerous file managers and they produce things like:
				//  - /sdcard/path/to/sketch.pde (normal, good)
				//  - /external_file/sdcard/path/to/sketch.pde (weird prefix)
				//  - /path/to/sketch.pde (missing root)
				
				// Try like normal
				if (!loadExternalSketch(filePath)) {
					// Sometimes file managers give us weird prefixes
					String crop = filePath;
					// Trim all beginning slashes
					if (crop.length() > 1) {
						while (crop.charAt(0) == '/') crop = crop.substring(1);
					}
					// Trim everything leading up to the next slash
					int slashIndex = crop.indexOf('/');
					if (slashIndex != -1 && crop.length() > slashIndex + 1) {
						crop = crop.substring(slashIndex);
						// Get rid of extra slashes, leaving only one behind
						while (crop.length() > 2 && crop.charAt(1) == '/') crop = crop.substring(1);
					}
					if (!loadExternalSketch(crop)) {
						// Sometimes file managers give us paths starting in /sdcard/
						loadExternalSketch(Environment.getExternalStorageDirectory().getPath() + filePath);
					}
				}
			}
		}
        
        //Make Processing 3.0 behave properly
        getGlobalState().initProcessingPrefs();
        
        //Register receiver for sketch logs / console output
        registerReceiver(consoleBroadcastReceiver, new IntentFilter("com.calsignlabs.apde.LogBroadcast"));
		
        //In case the user has enabled / disabled undo / redo in settings
        supportInvalidateOptionsMenu();
        
        correctUndoRedoEnabled();
        
        scheduleAutoCompile(true);
        // We schedule three times when we start up (loading the sketch, changing component target,
		// and here). So this flag makes it only happen once.
        FLAG_FIRST_AUTO_COMPILE = false;
	}
	
	public void correctUndoRedoEnabled() {
    	boolean settingsEnabled = getGlobalState().getPref("pref_key_undo_redo", true);
    	
		// Hide undo/redo if user has disabled undo/redo or if we're in an example
		findViewById(R.id.undo_redo_container).setVisibility(settingsEnabled && !getGlobalState().isExample() ? View.VISIBLE : View.GONE);
    	
    	SketchFile sketchFile = getSelectedSketchFile();
    	boolean canUndo = sketchFile != null && sketchFile.canUndo();
    	boolean canRedo = sketchFile != null && sketchFile.canRedo();
    	
    	undoButton.setEnabled(canUndo);
    	redoButton.setEnabled(canRedo);
    	undoButton.setClickable(canUndo);
    	redoButton.setClickable(canRedo);
		
		int alphaEnabled = getResources().getInteger(R.integer.prop_menu_comp_select_alpha_selected);
		int alphaDisabled = getResources().getInteger(R.integer.prop_menu_comp_select_alpha_unselected);
		
		undoButton.setImageAlpha(canUndo ? alphaEnabled : alphaDisabled);
		redoButton.setImageAlpha(canRedo ? alphaEnabled : alphaDisabled);
	}
	
	public void undo() {
    	if (getSelectedCodeIndex() >= 0 && getSelectedCodeIndex() < tabs.size()) {
			tabs.get(getSelectedCodeIndex()).undo(this);
			correctUndoRedoEnabled();
		}
	}
	
	public void redo() {
		if (getSelectedCodeIndex() >= 0 && getSelectedCodeIndex() < tabs.size()) {
			tabs.get(getSelectedCodeIndex()).redo(this);
			correctUndoRedoEnabled();
		}
	}
	
	private boolean loadExternalSketch(String filePath) {
		// TODO: update to use SAF
		
		// Try to get the file...
		File file = new File(filePath);
		
		String ext = "";
		int lastDot = filePath.lastIndexOf('.');
		if (lastDot != -1) {
			ext = filePath.substring(lastDot);
		}
		
		// Is this a good file?
		if (ext.equalsIgnoreCase(".pde") && file.exists() && !file.isDirectory()) {
			// Let's get the sketch folder...
			File sketchFolder = file.getParentFile();
			// Here goes...
			try {
				loadSketch(sketchFolder.getAbsolutePath(), APDE.SketchLocation.EXTERNAL);
				message(getGlobalState().getString(R.string.sketch_load_external_success));
				return true;
			} catch (MaybeDocumentFile.MaybeDocumentFileException e) {
				e.printStackTrace();
				return false;
			}
		}
		
		return false;
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
    	
    	if (keyBindings.get("save_sketch").matches(key, ctrl, meta, func, alt, sym, shift)) {
    		saveSketch();
    		return true;
    	}
    	if (keyBindings.get("new_sketch").matches(key, ctrl, meta, func, alt, sym, shift)) {
    		createNewSketch();
    		return true;
    	}
    	if (keyBindings.get("open_sketch").matches(key, ctrl, meta, func, alt, sym, shift)) {
    		loadSketch();
    		return true;
    	}
    	
    	if (keyBindings.get("run_sketch").matches(key, ctrl, meta, func, alt, sym, shift)) {
    		runApplication();
    		return true;
    	}
    	if (keyBindings.get("stop_sketch").matches(key, ctrl, meta, func, alt, sym, shift)) {
    		stopApplication();
    		return true;
    	}
    	
    	if (keyBindings.get("new_tab").matches(key, ctrl, meta, func, alt, sym, shift)) {
    		if (!getGlobalState().isExample())
    			addTabWithDialog();
    		return true;
    	}
    	if (keyBindings.get("delete_tab").matches(key, ctrl, meta, func, alt, sym, shift)) {
    		if (!getGlobalState().isExample())
    			deleteTab();
    		return true;
    	}
    	if (keyBindings.get("rename_tab").matches(key, ctrl, meta, func, alt, sym, shift)) {
    		if (!getGlobalState().isExample())
    			renameTab();
    		return true;
    	}
    	
    	if (keyBindings.get("undo").matches(key, ctrl, meta, func, alt, sym, shift)) {
    		if (!getGlobalState().isExample() && getCodeCount() > 0 && PreferenceManager.getDefaultSharedPreferences(this).getBoolean("pref_key_undo_redo", true)) {
    			undo();
    		}
    		return true;
    	}
    	if (keyBindings.get("redo").matches(key, ctrl, meta, func, alt, sym, shift)) {
    		if (!getGlobalState().isExample() && getCodeCount() > 0 && PreferenceManager.getDefaultSharedPreferences(this).getBoolean("pref_key_undo_redo", true)) {
    			redo();
    		}
    		
    		return true;
    	}
		
		if (keyBindings.get("view_sketches").matches(key, ctrl, meta, func, alt, sym, shift)) {
			loadSketch();
			selectDrawerFolder(APDE.SketchLocation.SKETCHBOOK);
			return true;
		}
		if (keyBindings.get("view_examples").matches(key, ctrl, meta, func, alt, sym, shift)) {
			loadSketch();
			selectDrawerFolder(APDE.SketchLocation.EXAMPLE);
			return true;
		}
		if (keyBindings.get("view_recent").matches(key, ctrl, meta, func, alt, sym, shift)) {
			loadSketch();
			selectDrawerRecent();
			return true;
		}
		
		if (keyBindings.get("settings").matches(key, ctrl, meta, func, alt, sym, shift)) {
			launchSettings();
			return true;
		}
		if (keyBindings.get("sketch_properties").matches(key, ctrl, meta, func, alt, sym, shift)) {
			launchSketchProperties();
			return true;
		}
		if (keyBindings.get("sketch_permissions").matches(key, ctrl, meta, func, alt, sym, shift)) {
			// TODO Sketch permissions
			return true;
		}
		
		if (keyBindings.get("show_sketch_folder").matches(key, ctrl, meta, func, alt, sym, shift)) {
			getGlobalState().launchSketchFolder(this);
			return true;
		}
		if (keyBindings.get("add_file").matches(key, ctrl, meta, func, alt, sym, shift)) {
			// TODO Add file
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
		try {
			// Save the sketch
			autoSave();
			
			// Update the global state
			getGlobalState().selectNewTempSketch();
			
			// Set up for a new sketch
			newSketch();
			// Reload the navigation drawer
			forceDrawerReload();
			
			// Update the action bar title
			getSupportActionBar().setTitle(getGlobalState().getSketchName());
		} catch (MaybeDocumentFile.MaybeDocumentFileException e) {
			e.printStackTrace();
		}
    }
	
	/**
	 * Open the rename sketch AlertDialog
	 */
	public void launchRenameSketch() {
		AlertDialog.Builder alert = new AlertDialog.Builder(this);
		
		alert.setTitle(String.format(Locale.US, getResources().getString(R.string.rename_sketch_title), getGlobalState().getSketchName()));
		alert.setMessage(R.string.rename_sketch_message);
		
		final EditText input = getGlobalState().createAlertDialogEditText(this, alert, getGlobalState().getSketchName(), true);
		
		alert.setPositiveButton(R.string.rename_sketch_button, (dialog, whichButton) -> {
			String sketchName = input.getText().toString();
			
			try {
				if (validateSketchName(sketchName) && !sketchName.equals(getGlobalState().getSketchName())) {
					APDE.SketchMeta source = new APDE.SketchMeta(getGlobalState().getSketchLocationType(), getGlobalState().getSketchPath());
					APDE.SketchMeta dest = new APDE.SketchMeta(source.getLocation(), source.getParent() + "/" + sketchName);
					
					getGlobalState().moveFolder(source, dest, EditorActivity.this);
					
					getGlobalState().setSketchName(sketchName);
					updateSketchPath("/" + sketchName);
				}
				
				if (!PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getBoolean("use_hardware_keyboard", false)) {
					((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(input.getWindowToken(), 0);
				}
			} catch (MaybeDocumentFile.MaybeDocumentFileException | FileNotFoundException e) {
				e.printStackTrace();
			}
		});
		
		alert.setNegativeButton(R.string.cancel, (dialog, whichButton) -> {
			if (!PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getBoolean("use_hardware_keyboard", false)) {
				((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(input.getWindowToken(), 0);
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
	 * Open the duplicate sketch AlertDialog
	 */
	public void launchDuplicateSketch() {
		AlertDialog.Builder alert = new AlertDialog.Builder(this);
		
		alert.setTitle(String.format(Locale.US, getResources().getString(R.string.duplicate_sketch_title), getGlobalState().getSketchName()));
		alert.setMessage(R.string.duplicate_sketch_message);
		
		final EditText input = getGlobalState().createAlertDialogEditText(this, alert, getGlobalState().getSketchName(), true);
		
		alert.setPositiveButton(R.string.duplicate_sketch_button, (dialog, whichButton) -> {
			String sketchName = input.getText().toString();
			
			try {
				if (validateSketchName(sketchName) && !sketchName.equals(getGlobalState().getSketchName())) {
					APDE.SketchMeta source = new APDE.SketchMeta(getGlobalState().getSketchLocationType(), getGlobalState().getSketchPath());
					APDE.SketchMeta dest = new APDE.SketchMeta(source.getLocation(), source.getParent() + "/" + sketchName);
					
					getGlobalState().copyFolder(source, dest, EditorActivity.this);
					
					getGlobalState().setSketchName(sketchName);
					updateSketchPath("/" + sketchName);
				}
				
				if (!PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getBoolean("use_hardware_keyboard", false)) {
					((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(input.getWindowToken(), 0);
				}
			} catch (MaybeDocumentFile.MaybeDocumentFileException | FileNotFoundException e) {
				e.printStackTrace();
			}
		});
		
		alert.setNegativeButton(R.string.cancel, (dialog, whichButton) -> {
			if (!PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getBoolean("use_hardware_keyboard", false)) {
				((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(input.getWindowToken(), 0);
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
		// Disable this. For Science!!
//		// Make sure to save the sketch
//		saveSketchForStop();
		
		getGlobalState().writeCodeDeletionDebugStatus("onPause()");
		
		// We do this to avoid messing up the *very* delicate console/code area resizing stuff
		getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
		
		super.onPause();
	}
	
	@Override
	public void onStop() {
		getGlobalState().writeCodeDeletionDebugStatus("onStop()");
		
		// Make sure to save the sketch
		saveSketchForStop();
		
		getGlobalState().writeCodeDeletionDebugStatus("onStop() after saveSketchForStop");
		
		super.onStop();
	}
	
	@Override
	public void onDestroy() {
		// Unregister the log / console receiver
		unregisterReceiver(consoleBroadcastReceiver);
		
		// Stop the print streams. It seems like sometimes the app gets destroyed without restarting
		// the JVM, and in this situation we start piping the new console streams into the old one;
		// the result is duplicated messages in the console. We avoid this by preventing console
		// streams in destroyed activity from displaying their output.
		outStream.disable();
		errStream.disable();
		
		getGlobalState().writeCodeDeletionDebugStatus("onDestroy()");
		
    	super.onDestroy();
	}
	
	/**
	 * Saves the sketch for when the activity is closing
	 */
	public void saveSketchForStop() {
		getGlobalState().writeCodeDeletionDebugStatus("begin saveSketchForStop()");
		
		//Automatically save
		autoSave();
    	
		//Store sketch info in private storage TODO make this SharedPreferences instead
		
		//Save the relative path to the current sketch
		String sketchPath = getGlobalState().getSketchPath();
    	
    	//Save the location of the current sketch
		String sketchLocation = getGlobalState().getSketchLocationType().toString();
		
		StringBuilder sketchData = new StringBuilder();
		sketchData.append(sketchPath);
		sketchData.append(';');
		sketchData.append(sketchLocation);
		sketchData.append(';');
		sketchData.append(getSelectedCodeIndex());
		sketchData.append(';');
		sketchData.append(getComponentTarget().serialize());
		sketchData.append(';');
		
		JSONObject undoRedoHistories = new JSONObject();
		
		for (int i = 0; i < tabs.size(); i ++) {
			SketchFile sketchFile = tabs.get(i);
			
			if (sketchFile.getFragment() != null && sketchFile.getFragment().isInitialized()) {
				sketchData.append(sketchFile.getFragment().getCodeEditText().getSelectionStart());
				sketchData.append(',');
				sketchData.append(sketchFile.getFragment().getCodeEditText().getSelectionEnd());
				sketchData.append(',');
				sketchData.append(sketchFile.getFragment().getCodeScroller().getScrollX());
				sketchData.append(',');
				sketchData.append(sketchFile.getFragment().getCodeScroller().getScrollY());
				sketchData.append(';');
			} else {
				// The fragment is unloaded if it isn't visible or adjacent to the visible tab, but
				// the data is stored on the SketchFile
				
				sketchData.append(sketchFile.getSelectionStart());
				sketchData.append(',');
				sketchData.append(sketchFile.getSelectionEnd());
				sketchData.append(',');
				sketchData.append(sketchFile.getScrollX());
				sketchData.append(',');
				sketchData.append(sketchFile.getScrollY());
				sketchData.append(';');
			}
			
			try {
				undoRedoHistories.put(sketchFile.getFilename(), sketchFile.getUndoRedoHistory());
			} catch (JSONException | NoSuchAlgorithmException e) {
				e.printStackTrace();
			}
		}
		
		writeTempFile("sketchData.txt", sketchData.toString());
		writeTempFile("sketchUndoRedoHistory.json", undoRedoHistories.toString());
		
		getGlobalState().writeCodeDeletionDebugStatus("end saveSketchForStop()");
	}
	
	/**
	 * Loads the temporary sketch for the start of the app
	 * 
	 * @return success
	 */
	public boolean loadSketchStart() {
		try {
			String sketchData = readTempFile("sketchData.txt");
			String[] data = sketchData.split(";");
			String jsonData = readTempFile("sketchUndoRedoHistory.json");
			
			if (data.length < 4 || jsonData.length() == 0) {
				// On clean installs and after updating
				return false;
			}
			
			JSONObject undoRedoHistories = new JSONObject(jsonData);
			
			String sketchPath = data[0];
			APDE.SketchLocation sketchLocation = APDE.SketchLocation.fromString(data[1]);
			
			boolean success = loadSketch(sketchPath, sketchLocation);
			
			selectCode(Integer.parseInt(data[2]));
			
			setComponentTarget(ComponentTarget.deserialize(Integer.parseInt(data[3])));
			
			if (success && tabs.size() == data.length - 4) {
				for (int i = 4; i < data.length; i ++) {
					String[] sketchFileData = data[i].split(",");
					
					if (sketchFileData.length > 0) {
						tabs.get(i - 4).selectionStart = Integer.parseInt(sketchFileData[0]);
						tabs.get(i - 4).selectionEnd = Integer.parseInt(sketchFileData[1]);
						tabs.get(i - 4).scrollX = Integer.parseInt(sketchFileData[2]);
						tabs.get(i - 4).scrollY = Integer.parseInt(sketchFileData[3]);
					}
				}
			}
			
			if (success) {
				for (SketchFile sketchFile : tabs) {
					try {
						sketchFile.populateUndoRedoHistory(undoRedoHistories.getJSONObject(sketchFile.getFilename()));
					} catch (Exception e) {
						/* If an exception gets through, then this function reports that it was
						 * not successful. The problem with that is that it will then automatically
						 * create a default (empty) 'sketch' tab. Even if we already loaded one.
						 * Which means that data can get overwritten. So we want to ignore any
						 * error caused by trying to load the undo/redo history.
						 */
						e.printStackTrace();
					}
				}
			}
			
			return success;
		} catch(Exception e) { //Meh...
			e.printStackTrace();
			return false;
		}
	}
	
	/**
	 * Reload the current sketch... without saving.
	 * Useful for updating files that have been changed outside of the editor.
	 */
	public void reloadSketch() {
		getGlobalState().writeCodeDeletionDebugStatus("before reloadSketch()");
		
		try {
			loadSketch(getGlobalState().getSketchPath(), getGlobalState().getSketchLocationType());
		} catch (MaybeDocumentFile.MaybeDocumentFileException e) {
			e.printStackTrace();
		}
		
		getGlobalState().writeCodeDeletionDebugStatus("after reloadSketch()");
	}
	
	/**
	 * Automatically save the sketch, whether it is to the sketchbook folder or to the temp folder
	 */
	public void autoSave() {
		getGlobalState().writeCodeDeletionDebugStatus("before autoSave()");
		
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
		
		getGlobalState().writeCodeDeletionDebugStatus("end autoSave()");
		
		// In case there is still an autosave task in the queue
		cancelAutoSave();
	}
	
	public void cancelAutoSave() {
		if (autoSaveTimerTask != null && !autoSaveTimerTask.isDone() && !autoSaveTimerTask.isCancelled()) {
			autoSaveTimerTask.cancel(false);
		}
	}
	
	public void scheduleAutoSave() {
		// By default autosave after 30 seconds of inactivity
		// But the user can change the timeout in settings
		
		cancelAutoSave();
		
		long timeout = Long.parseLong(getGlobalState().getPref("pref_key_autosave_timeout", getGlobalState().getString(R.string.pref_autosave_timeout_default_value)));
		
		if (timeout != -1L) {
			autoSaveTimerTask = autoSaveTimer.schedule(autoSaveTimerAction, timeout, TimeUnit.SECONDS);
		}
	}
	
	public boolean cancelAutoCompile() {
		if (building) {
			return false;
		}
		if (autoCompileTask != null && !autoCompileTask.isDone() && !autoCompileTask.isCancelled()) {
			autoCompileTask.cancel(true);
		}
		return true;
	}
	
	public void scheduleAutoCompile(boolean immediate) {
		if (!cancelAutoCompile()) {
			// We're currently building
			return;
		}
		
		long timeout = Long.parseLong(getGlobalState().getPref("pref_key_build_compile_timeout", getGlobalState().getString(R.string.pref_build_compile_timeout_default_value)));
		
		if (timeout != -1L) {
			findViewById(R.id.auto_compile_placeholder).setBackgroundColor(getResources().getColor(R.color.bar_overlay));
			autoCompileTask = autoCompileTimer.schedule(autoCompileAction, immediate ? 0 : timeout, TimeUnit.SECONDS);
		}
	}
	
	public void autoCompile() {
		runOnUiThread(() -> {
			if (isOldBuild()) {
				final BuildContext context;
				try {
					context = BuildContext.create(getGlobalState());
				} catch (MaybeDocumentFile.MaybeDocumentFileException e) {
					e.printStackTrace();
					return;
				}
				final Build builder = new Build(getGlobalState(), context);
				
				autoCompileTimer.schedule(() -> {
					long start = System.currentTimeMillis();
					
					toggleAutoCompileIndicator(true);
					
					runOnUiThread(() -> {
						findViewById(R.id.auto_compile_placeholder).setBackgroundColor(getResources().getColor(R.color.message_back));
					});
					builder.build("debug", getComponentTarget(), true);
					
					toggleAutoCompileIndicator(false);
					
					if (getGlobalState().getPref("build_output_verbose", false)) {
						System.out.println(String.format(Locale.US, "Finished in %1$dms",
								System.currentTimeMillis() - start));
					}
				}, 0, TimeUnit.SECONDS);
			} else {
				getGlobalState().getModularBuild().compile();
			}
			
			cancelAutoCompile();
		});
	}
	
	public void toggleAutoCompileIndicator(boolean enable) {
		runOnUiThread(() -> {
			ProgressBar progress = findViewById(R.id.auto_compile_progress);
			FrameLayout placeholder = findViewById(R.id.auto_compile_placeholder);
			
			progress.setVisibility(enable ? View.VISIBLE : View.GONE);
			placeholder.setVisibility(enable ? View.GONE : View.VISIBLE);
			
			if (android.os.Build.VERSION.SDK_INT >= 21) {
				progress.setProgressBackgroundTintList(ColorStateList.valueOf(getResources().getColor(android.R.color.transparent)));
				progress.setIndeterminateTintList(ColorStateList.valueOf(getResources().getColor(R.color.bar_overlay)));
			}
		});
	}
	
	/**
	 * Sets up the editor for a new sketch
	 */
	public void newSketch() throws MaybeDocumentFile.MaybeDocumentFileException {
		// Set the title of the action bar
		getSupportActionBar().setTitle(getGlobalState().getSketchName());
		// Reload the action bar actions and overflow
		supportInvalidateOptionsMenu();
		
		// Get rid of any existing tabs
		tabs.clear();
		codePagerAdapter.notifyDataSetChanged();
		
		// Add the default "sketch" tab
		addDefaultTab(APDE.DEFAULT_SKETCH_TAB);
		
		// Select the new tab
		selectCode(0);
		
		// Fix a strange bug...
		getSelectedSketchFile().clearUndoRedo();
		
		// Save the new sketch
		autoSave();
		// Add to recents
		getGlobalState().putRecentSketch(getGlobalState().getSketchLocationType(), getGlobalState().getSketchPath());
		
		// Reload the navigation drawer
		forceDrawerReload();
		
		scheduleAutoCompile(true);
	}
    
	/**
	 * Loads a sketch
	 * 
	 * @param sketchPath
	 * @param sketchLocation
	 * @return success
	 */
	public boolean loadSketch(String sketchPath, APDE.SketchLocation sketchLocation) throws MaybeDocumentFile.MaybeDocumentFileException {
		if (sketchLocation == null) {
			// Something bad happened
			return false;
		}
		
		// Get the sketch location
    	MaybeDocumentFile sketchLoc = getGlobalState().getSketchLocation(sketchPath, sketchLocation);
    	boolean success;
    	
    	// Ensure that the sketch folder exists and is a directory
    	if (sketchLoc.exists() && sketchLoc.isDirectory()) {
    		getGlobalState().selectSketch(sketchPath, sketchLocation);
    		
    		// Get all the files in the directory
    		DocumentFile[] files = sketchLoc.resolve().listFiles();
    		
    		// Why do we need this...?
    		for (SketchFile meta : tabs) {
				meta.disable();
			}
    		
    		// Get rid of any tabs
    		tabs.clear();
			
    		// Cycle through the files
    		for (DocumentFile file : files) {
    			String name = file.getName();
    			if (name == null) {
    				continue;
			    }
    			
    			String[] parts = name.split("\\.");
    			if (parts.length != 2) {
    				continue;
			    }
    			String title = parts[0];
    			String ext = parts[1];
    			
				if (ext.equals("pde") || ext.equals("java")) {
					// Build a SketchFile object
					SketchFile meta = new SketchFile(title);
					meta.readData(file, getContentResolver());
					meta.setExample(getGlobalState().isExample());
					meta.setSuffix("." + ext);
					
					addTabWithoutPagerUpdate(meta);
					meta.getFragment().setSketchFile(meta);
				}
    		}
		
			codePagerAdapter.notifyDataSetChanged();
    		
    		success = true;
    		
    		// Automatically selects and loads the new tab
			selectCode(0);
    	} else {
    		success = false;
    	}
    	
    	if (success) {
			// Close Find/Replace (if it's open)
			((FindReplace) getGlobalState().getPackageToToolTable().get(FindReplace.PACKAGE_NAME)).close();
			
			// We hide undo/redo for examples
			correctUndoRedoEnabled();
			
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
    		
    		// Add this to the recent sketches
    		getGlobalState().putRecentSketch(sketchLocation, sketchPath);
    		
    		if (!FLAG_FIRST_AUTO_COMPILE) {
				scheduleAutoCompile(true);
			}
    	}
		
		getGlobalState().writeCodeDeletionDebugStatus("after loadSketch()");
    	
    	return success;
	}
    
    /**
     * Saves the sketch to the sketchbook folder, creating a new subdirectory if necessary
     */
    public void saveSketch() {
    	// If we cannot write to the external storage (and the user wants to), make sure to inform the user
	    if (!externalStorageWritable() && getGlobalState().getSketchbookDrive().type.requiresExternalStoragePermission()) {
    		AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(getResources().getText(R.string.external_storage_unavailable_dialog_title))
            	.setMessage(getResources().getText(R.string.external_storage_unavailable_dialog_message)).setCancelable(false)
            	.setPositiveButton(R.string.ok, (dialog, which) -> {}).show();
            
    		return;
    	}
	
		getGlobalState().writeCodeDeletionDebugStatus("begin saveSketch()");
    	
    	boolean success = true;
    	
    	String sketchPath = getGlobalState().getSketchPath();
	
	    MaybeDocumentFile sketchLoc;
		try {
			//Obtain the location of the sketch
			sketchLoc = getGlobalState().getSketchLocation(sketchPath, getGlobalState().getSketchLocationType());
			sketchLoc.resolve();
		} catch (MaybeDocumentFile.MaybeDocumentFileException e) {
			message(getGlobalState().getString(R.string.save_sketch_create_dir_error));
			e.printStackTrace();
			return;
		}
	
	    if (getCodeCount() > 0) {
			// Update all tabs
			for (int i = 0; i < tabs.size(); i ++) {
				// Not all of the tabs are loaded at once
				if (tabs.get(i).getFragment().getCodeEditText() != null) {
					if (tabs.get(i).getFragment().getCodeEditText().getText().length() == 0 && tabs.get(i).getText().length() > 0) {
						// This condition is where we overwrite code with the code deletion bug
						getGlobalState().writeDebugLog("saveSketch", "Detected code deletion in tab '" + tabs.get(i).getTitle() + "', not overwriting.");
					} else {
						tabs.get(i).update(this, getGlobalState().getPref("pref_key_undo_redo", true));
					}
				}
			}
			
	    	// Iterate through the SketchFiles...
	    	for (SketchFile meta : tabs) {
	    		if (meta.enabled()) {
	    			// ...and write them to the sketch folder
				    try {
					    if (!meta.writeData(sketchLoc, getContentResolver())) {
						    success = false;
					    }
				    } catch (MaybeDocumentFile.MaybeDocumentFileException e) {
						message(getGlobalState().getString(R.string.save_sketch_save_file_error, meta.getFilename()));
						e.printStackTrace();
						success = false;
				    }
	    		}
	    	}
			
	    	if (success) {
				try {
					getGlobalState().selectSketch(sketchPath, getGlobalState().getSketchLocationType());
				} catch (MaybeDocumentFile.MaybeDocumentFileException e) {
					e.printStackTrace();
				}
	    		
	    		// Force the drawer to reload
	    		forceDrawerReload();
	    		
	    		supportInvalidateOptionsMenu();
	            
	            // Inform the user of success
	    		message(getResources().getText(R.string.message_sketch_save_success));
	    		setSaved(true);
	    	} else {
	    		// Inform the user of failure
	    		error(getResources().getText(R.string.message_sketch_save_failure));
	    	}
    	} else {
    		// If there are no tabs
    		// TODO is this right?
    		
    		// Force the drawer to reload
    		forceDrawerReload();
    		
            // Inform the user
    		message(getResources().getText(R.string.message_sketch_save_success));
    		setSaved(true);
    	}
	
		getGlobalState().writeCodeDeletionDebugStatus("after saveSketch()");
    }
    
    public void copyToSketchbook() {
    	// If we cannot write to the external storage (and the user wants to), make sure to inform the user
	    if (!externalStorageWritable() && getGlobalState().getSketchbookDrive().type.requiresExternalStoragePermission()) {
    		AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(getResources().getText(R.string.external_storage_unavailable_dialog_title))
            	.setMessage(getResources().getText(R.string.external_storage_unavailable_dialog_message)).setCancelable(false)
            	.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            		@Override
            		public void onClick(DialogInterface dialog, int which) {}
            }).show();
            
    		return;
    	}
    	
    	try {
		    // The old example location
		    MaybeDocumentFile oldLoc = getGlobalState().getSketchLocation();
		
		    // Get the sketch name so that we place the copied example at the root of sketchbook
		    String sketchPath = "/" + getGlobalState().getSketchName();
		
		    // Obtain the location of the sketch
		    // Save examples to the sketchbook so that the user can copy examples to the sketchbook
		    MaybeDocumentFile sketchLoc = getGlobalState().getSketchLocation(sketchPath, APDE.SketchLocation.SKETCHBOOK);
		
		    // Ensure that the sketch folder exists
		    sketchLoc.resolve();
			
    		APDE.copyDocumentFile(oldLoc.resolve(), sketchLoc, getContentResolver());
			
			getGlobalState().selectSketch(sketchPath, APDE.SketchLocation.SKETCHBOOK);
			updateSketchPath(sketchPath);
			
			updateCodeAreaFocusable();
            
            // Inform the user of success
    		message(getResources().getText(R.string.message_sketch_save_success));
    		setSaved(true);
    	} catch (IOException e) {
    		// Inform the user of failure
    		error(getResources().getText(R.string.message_sketch_save_failure));
    	} catch (MaybeDocumentFile.MaybeDocumentFileException e) {
		    error(getResources().getText(R.string.message_sketch_save_failure));
		    e.printStackTrace();
	    }
    }
	
	public void updateCodeAreaFocusable() {
		for (SketchFile sketchFile : tabs) {
			sketchFile.setExample(false);
			sketchFile.forceReloadTextIfInitialized();
		}
	}
	
	private void updateSketchPath(String sketchPath) throws MaybeDocumentFile.MaybeDocumentFileException {
		APDE.SketchLocation sketchLocation = getGlobalState().getSketchLocationType();
		
		getGlobalState().putRecentSketch(sketchLocation, sketchPath);
		getGlobalState().selectSketch(sketchPath, sketchLocation);
		
		forceDrawerReload();
		supportInvalidateOptionsMenu();
	}
	
	public void moveToSketchbook() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		
		builder.setTitle(R.string.move_temp_to_sketchbook_title);
		builder.setMessage(String.format(Locale.US, getResources().getString(R.string.move_temp_to_sketchbook_message), getGlobalState().getSketchName()));
		
		builder.setPositiveButton(R.string.move_temp_to_sketchbook_button, (dialog, which) -> {
			try {
				APDE.SketchMeta source = new APDE.SketchMeta(getGlobalState().getSketchLocationType(), getGlobalState().getSketchPath());
				APDE.SketchMeta dest = new APDE.SketchMeta(APDE.SketchLocation.SKETCHBOOK, "/" + source.getName());
				
				// Let's not overwrite anything...
				// TODO Maybe give the user options to replace / keep both in the new location?
				// We don't need that much right now, they can deal with things manually...
				if (getGlobalState().getSketchLocation(dest.getPath(), dest.getLocation()).exists()) {
					AlertDialog.Builder builder1 = new AlertDialog.Builder(EditorActivity.this);
					
					builder1.setTitle(R.string.rename_sketch_failure_title);
					builder1.setMessage(R.string.rename_move_folder_failure_message);
					
					builder1.setPositiveButton(getResources().getString(R.string.ok), (dialog1, which1) -> {
					});
					
					builder1.create().show();
					
					return;
				}
				
				getGlobalState().moveFolder(source, dest, EditorActivity.this);
				
				String sketchPath = "/" + getGlobalState().getSketchName();
				getGlobalState().selectSketch(sketchPath, APDE.SketchLocation.SKETCHBOOK);
				updateSketchPath(sketchPath);
				
				message(getResources().getText(R.string.message_sketch_save_success));
				setSaved(true);
			} catch (MaybeDocumentFile.MaybeDocumentFileException | FileNotFoundException e) {
				e.printStackTrace();
			}
		});
		
		builder.setNegativeButton(R.string.cancel, (dialog, whichButton) -> {});
		
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
		
		alert.setPositiveButton(R.string.delete, (dialog, whichButton) -> {
			deleteSketch();
			
			try {
				getGlobalState().selectNewTempSketch();
				newSketch();
				
				toolbar.setTitle(getGlobalState().getSketchName());
			} catch (MaybeDocumentFile.MaybeDocumentFileException e) {
				e.printStackTrace();
			}
		});
		
		alert.setNegativeButton(R.string.cancel, (dialog, whichButton) -> {});
		
		alert.create().show();
	}
    
    /**
     * Deletes the current sketch
     */
    public void deleteSketch() {
		try {
			MaybeDocumentFile sketchFolder = getGlobalState().getSketchLocation();
			sketchFolder.delete();
		} catch (MaybeDocumentFile.MaybeDocumentFileException e) {
			e.printStackTrace();
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
			while((bytesRead = inputStream.read(contents)) != -1) {
				output += new String(contents, 0, bytesRead);
			}
		} catch(Exception e) {
			//... nothing much to do here
		} finally {
			//Make sure to close the stream
			try {
				if(inputStream != null) {
					inputStream.close();
				}
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
		try {
			if (drawerOpen) {
				if (drawerSketchLocationType != null) {
					getSupportActionBar().setSubtitle(drawerSketchLocationType.toReadableString(getGlobalState()) + drawerSketchPath + "/");
				} else if (drawerRecentSketch) {
					getSupportActionBar().setSubtitle(getResources().getString(R.string.drawer_folder_recent) + "/");
				} else {
					getSupportActionBar().setSubtitle(null);
				}
			} else {
				getSupportActionBar().setSubtitle(null);
			}
			
			final ListView drawerList = (ListView) findViewById(R.id.drawer_list);
			
			ArrayList<FileNavigatorAdapter.FileItem> items;
			
			if (drawerSketchLocationType != null) {
				items = getGlobalState().listSketchContainingFolders(getGlobalState().getSketchLocation(drawerSketchPath, drawerSketchLocationType), new String[]{APDE.LIBRARIES_FOLDER},
						drawerSketchPath.length() > 0, drawerSketchLocationType.equals(APDE.SketchLocation.SKETCHBOOK) || drawerSketchLocationType.equals(APDE.SketchLocation.TEMPORARY),
						new APDE.SketchMeta(drawerSketchLocationType, drawerSketchPath));
			} else {
				if (drawerRecentSketch) {
					items = getGlobalState().listRecentSketches();
				} else {
					items = new ArrayList<FileNavigatorAdapter.FileItem>();
					items.add(new FileNavigatorAdapter.FileItem(getResources().getString(R.string.drawer_folder_sketches), FileNavigatorAdapter.FileItemType.FOLDER));
					items.add(new FileNavigatorAdapter.FileItem(getResources().getString(R.string.drawer_folder_examples), FileNavigatorAdapter.FileItemType.FOLDER));
					items.add(new FileNavigatorAdapter.FileItem(getResources().getString(R.string.drawer_folder_library_examples), FileNavigatorAdapter.FileItemType.FOLDER));
					items.add(new FileNavigatorAdapter.FileItem(getResources().getString(R.string.drawer_folder_temporary), FileNavigatorAdapter.FileItemType.FOLDER));
					items.add(new FileNavigatorAdapter.FileItem(getResources().getString(R.string.drawer_folder_recent), FileNavigatorAdapter.FileItemType.FOLDER));
				}
			}
			
			int selected = -1;
			
			if (drawerSketchLocationType != null && drawerSketchLocationType.equals(getGlobalState().getSketchLocationType())
					&& getGlobalState().getSketchPath().equals(drawerSketchPath + "/" + getGlobalState().getSketchName())) {
				
				selected = FileNavigatorAdapter.indexOfSketch(items, getGlobalState().getSketchName());
			} else if (drawerRecentSketch && !(getGlobalState().getRecentSketches().size() == 0)) {
				// In the recent screen, the top-most sketch is always currently selected...
				// It's not "0" because that's the UP button
				selected = 1;
			}
			
			final FileNavigatorAdapter fileAdapter = new FileNavigatorAdapter(this, items, selected);
			
			// Load the list of sketches into the drawer
			try {
				drawerList.setAdapter(fileAdapter);
			} catch (SecurityException e) {
				// Nada
				// For some reason we get security exceptions sometimes
			}
			drawerList.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
			
			// Let the adapter handle long click events
			// Used for drag and drop to move files...
			drawerList.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
				@Override
				public boolean onItemLongClick(AdapterView<?> adapterView, View view, int position, long id) {
					return fileAdapter.onLongClickItem(view, position);
				}
			});
		} catch (MaybeDocumentFile.MaybeDocumentFileException e) {
			e.printStackTrace();
		}
    }
    
    public void selectDrawerFolder(APDE.SketchLocation location) {
    	drawerSketchLocationType = location;
    	drawerSketchPath = "";
    	drawerRecentSketch = false;
    	
    	forceDrawerReload();
    }
    
    public void selectDrawerRecent() {
    	drawerSketchLocationType = null;
    	drawerSketchPath = "";
    	drawerRecentSketch = true;
    	
    	forceDrawerReload();
	}
    
    /**
     * @return the APDE application global state
     */
    public APDE getGlobalState() {
    	return (APDE) getApplication();
    }
	
	protected ImageButton runStopMenuButton = null;
	protected boolean runStopMenuButtonAnimating = false;
    
    @Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.activity_editor, menu);
		prepareOptionsMenu(menu);
		return true;
	}
	
	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
    	prepareOptionsMenu(menu);
    	return true;
	}
	
	public void prepareOptionsMenu(Menu menu) {
		// We're using an action view because apparently this is the only way to animate the icon
		runStopMenuButton = (ImageButton) menu.findItem(R.id.menu_run).getActionView();
		
		runStopMenuButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View view) {
				if (runStopMenuButtonAnimating) {
					return;
				}
				
				// Run turns into stop when building
				if (!building) {
					runApplication();
				} else {
					stopApplication();
				}
			}
		});
		
		runStopMenuButton.setOnLongClickListener(new ImageButton.OnLongClickListener() {
			@Override
			public boolean onLongClick(View v) {
				Toast toast = Toast.makeText(EditorActivity.this, building ? R.string.editor_menu_stop_sketch : R.string.editor_menu_run_sketch, Toast.LENGTH_SHORT);
				APDE.positionToast(toast, runStopMenuButton, getWindow(), 0, 0);
				toast.show();
				
				return true;
			}
		});
		
		if (android.os.Build.VERSION.SDK_INT >= 21) {
			runStopMenuButton.setImageDrawable(getResources().getDrawable(building & !runStopMenuButtonAnimating ? R.drawable.ic_stop_vector : R.drawable.ic_run_vector));
		} else {
			runStopMenuButton.setImageDrawable(getResources().getDrawable(building ? R.drawable.ic_stop_white : R.drawable.ic_run_white));
		}
    	
        if(drawerOpen) {
        	// If the drawer is visible
        	
        	// Make sure to hide all of the sketch-specific action items
        	menu.findItem(R.id.menu_run).setVisible(false);
        	menu.findItem(R.id.menu_comp_select).setVisible(false);
        	menu.findItem(R.id.menu_undo).setVisible(false);
        	menu.findItem(R.id.menu_redo).setVisible(false);
        	menu.findItem(R.id.menu_tab_delete).setVisible(false);
        	menu.findItem(R.id.menu_tab_rename).setVisible(false);
        	menu.findItem(R.id.menu_tab_duplicate).setVisible(false);
        	menu.findItem(R.id.menu_save).setVisible(false);
			menu.findItem(R.id.menu_delete).setVisible(false);
			menu.findItem(R.id.menu_rename).setVisible(false);
			menu.findItem(R.id.menu_duplicate).setVisible(false);
        	menu.findItem(R.id.menu_copy_to_sketchbook).setVisible(false);
			menu.findItem(R.id.menu_move_to_sketchbook).setVisible(false);
        	menu.findItem(R.id.menu_new).setVisible(false);
        	menu.findItem(R.id.menu_load).setVisible(false);
        	menu.findItem(R.id.menu_tab_new).setVisible(false);
        	menu.findItem(R.id.menu_tools).setVisible(false);
        	menu.findItem(R.id.menu_sketch_properties).setVisible(false);
        	
        	// Make sure to hide the sketch name
        	getSupportActionBar().setTitle(R.string.apde_app_title);
        } else {
        	if (getCodeCount() > 0) {
        		// If the drawer is closed and there are tabs
        		
        		// Make sure to make the tab actions visible
            	menu.findItem(R.id.menu_run).setVisible(true);
            	menu.findItem(R.id.menu_tab_delete).setVisible(true);
            	menu.findItem(R.id.menu_tab_rename).setVisible(true);
            	menu.findItem(R.id.menu_tab_duplicate).setVisible(true);
            	
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
				menu.findItem(R.id.menu_comp_select).setVisible(false);
    	    	menu.findItem(R.id.menu_undo).setVisible(false);
            	menu.findItem(R.id.menu_redo).setVisible(false);
    	    	menu.findItem(R.id.menu_tab_delete).setVisible(false);
            	menu.findItem(R.id.menu_tab_rename).setVisible(false);
            	menu.findItem(R.id.menu_tab_duplicate).setVisible(false);
            	menu.findItem(R.id.menu_tools).setVisible(false);
            }
        	
        	// Enable/disable undo/redo buttons
        	SketchFile meta = getSelectedSketchFile();
        	menu.findItem(R.id.menu_undo).setEnabled(meta != null && meta.canUndo());
        	menu.findItem(R.id.menu_redo).setEnabled(meta != null && meta.canRedo());
        	
        	// Make sure to make all of the sketch-specific actions visible
        	
        	switch (getGlobalState().getSketchLocationType()) {
        	case SKETCHBOOK:
				menu.findItem(R.id.menu_save).setVisible(true);
				menu.findItem(R.id.menu_delete).setVisible(true);
				menu.findItem(R.id.menu_rename).setVisible(true);
				menu.findItem(R.id.menu_duplicate).setVisible(true);
				menu.findItem(R.id.menu_copy_to_sketchbook).setVisible(false);
				menu.findItem(R.id.menu_move_to_sketchbook).setVisible(false);
				break;
        	case TEMPORARY:
        		menu.findItem(R.id.menu_save).setVisible(true);
				menu.findItem(R.id.menu_delete).setVisible(true);
				menu.findItem(R.id.menu_rename).setVisible(false);
				menu.findItem(R.id.menu_duplicate).setVisible(false);
        		menu.findItem(R.id.menu_copy_to_sketchbook).setVisible(false);
				menu.findItem(R.id.menu_move_to_sketchbook).setVisible(true);
        		break;
        	case EXTERNAL:
        		menu.findItem(R.id.menu_save).setVisible(true);
				menu.findItem(R.id.menu_delete).setVisible(true);
				menu.findItem(R.id.menu_rename).setVisible(true);
				menu.findItem(R.id.menu_duplicate).setVisible(true);
        		menu.findItem(R.id.menu_copy_to_sketchbook).setVisible(true);
				menu.findItem(R.id.menu_move_to_sketchbook).setVisible(false);
        		break;
        	case EXAMPLE:
        	case LIBRARY_EXAMPLE:
        		menu.findItem(R.id.menu_save).setVisible(false);
				menu.findItem(R.id.menu_delete).setVisible(false);
				menu.findItem(R.id.menu_rename).setVisible(false);
				menu.findItem(R.id.menu_duplicate).setVisible(false);
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
    	menu.findItem(R.id.menu_tab_duplicate).setVisible(false);
		
		// With auto-saving, we don't actually need to let the user save the sketch manually
		// However, the keyboard shortcut will still be available
		menu.findItem(R.id.menu_save).setVisible(false);
    	
    	// So that the user can add a tab if there are none
    	if (getCodeCount() <= 0 && !getGlobalState().isExample()) {
			menu.findItem(R.id.menu_tab_new).setVisible(true);
		}
		
		// We are now using the combined run-stop button
		menu.findItem(R.id.menu_stop).setVisible(false);
    	
    	// We have moved undo/redo to the tab bar to make it more accessible
    	menu.findItem(R.id.menu_undo).setVisible(false);
    	menu.findItem(R.id.menu_redo).setVisible(false);
		
		menu.findItem(R.id.menu_comp_select).setIcon(getComponentTarget().getIconId());
    	menu.findItem(R.id.menu_comp_select).setTitle(getComponentTarget().getNameId());
    	
    	int alphaSelected = getResources().getInteger(R.integer.prop_menu_comp_select_alpha_selected);
		int alphaUnelected = getResources().getInteger(R.integer.prop_menu_comp_select_alpha_unselected);
    	
    	// Dim the comps that aren't selected
    	menu.findItem(R.id.menu_comp_select_app).getIcon().setAlpha(getComponentTarget() == ComponentTarget.APP ? alphaSelected : alphaUnelected);
		menu.findItem(R.id.menu_comp_select_wallpaper).getIcon().setAlpha(getComponentTarget() == ComponentTarget.WALLPAPER ? alphaSelected : alphaUnelected);
		menu.findItem(R.id.menu_comp_select_watchface).getIcon().setAlpha(getComponentTarget() == ComponentTarget.WATCHFACE ? alphaSelected : alphaUnelected);
		menu.findItem(R.id.menu_comp_select_vr).getIcon().setAlpha(getComponentTarget() == ComponentTarget.VR ? alphaSelected : alphaUnelected);
		menu.findItem(R.id.menu_comp_select_preview).getIcon().setAlpha(getComponentTarget() == ComponentTarget.PREVIEW ? alphaSelected : alphaUnelected);
    }
    
    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        drawerToggle.syncState(); //The stranger aspects of having a navigation drawer...
	
		getGlobalState().writeCodeDeletionDebugStatus("onPostCreate()");
    }
    
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        drawerToggle.onConfigurationChanged(newConfig); //The stranger aspects of having a navigation drawer...
	
		getGlobalState().writeCodeDeletionDebugStatus("onConfigurationChanged()");
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
			case R.id.menu_comp_select_app:
				setComponentTarget(ComponentTarget.APP);
				return true;
			case R.id.menu_comp_select_wallpaper:
				setComponentTarget(ComponentTarget.WALLPAPER);
				return true;
			case R.id.menu_comp_select_watchface:
				setComponentTarget(ComponentTarget.WATCHFACE);
				return true;
			case R.id.menu_comp_select_vr:
				setComponentTarget(ComponentTarget.VR);
				return true;
			case R.id.menu_comp_select_preview:
				setComponentTarget(ComponentTarget.PREVIEW);
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
			case R.id.menu_duplicate:
				launchDuplicateSketch();
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
            case R.id.menu_tab_duplicate:
            	duplicateTab();
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
			// Build the character inserts tray
			reloadCharInserts();
			
			showCharInserts();
    	}
    }
    
    public void toggleProblemOverview() {
    	if (keyboardVisible) {
    		return;
		}
		
		if (problemOverview) {
			consoleWrapperPager.setCurrentItem(0);
			problemOverview = false;
		} else {
			consoleWrapperPager.setCurrentItem(1);
			problemOverview = true;
		}
	}
    
    protected void showCharInserts() {
		final TextView messageView = findViewById(R.id.message);
		final HorizontalScrollView charInsertTray = findViewById(R.id.char_insert_tray);
		
		View buffer = findViewById(R.id.buffer);
		View sep = findViewById(R.id.toggle_char_inserts_separator);
		View wrapper = findViewById(R.id.toggle_wrapper);
		
		toggleCharInserts.setImageResource(messageType != MessageType.MESSAGE ? R.drawable.ic_caret_right_white : R.drawable.ic_caret_right_black);
		toggleProblemOverview.setImageResource(messageType != MessageType.MESSAGE ? R.drawable.problem_overview_white_unfilled : R.drawable.problem_overview_black_unfilled);
		
		int total = buffer.getWidth() - sep.getWidth() - wrapper.getWidth();
		
		RotateAnimation rotate = new RotateAnimation(180f, 360f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
		rotate.setInterpolator(new AccelerateDecelerateInterpolator());
		rotate.setRepeatCount(0);
		rotate.setDuration(200);
		
		ResizeAnimation messageAnimation = new ResizeAnimation<LinearLayout>(messageView, ResizeAnimation.DEFAULT, ResizeAnimation.DEFAULT, 0, ResizeAnimation.DEFAULT, true, false);
		ResizeAnimation<LinearLayout> charInsertAnimation = new ResizeAnimation<>(charInsertTray, 0, buffer.getHeight(), total, buffer.getHeight());
		
		Animation.AnimationListener listener = new Animation.AnimationListener() {
			@Override
			public void onAnimationStart(Animation animation) {}
			
			@Override
			public void onAnimationEnd(Animation animation) {
				if (!keyboardVisible) {
					// If the keyboard has closed since starting the animation, hide the char inserts tray
					// TODO still some problems
					// The delay is really hacky and probably won't work on slower devices
					// I am still able to reproduce this on occasion
					//
					// For most users this should not be an issue
//					messageView.postDelayed(new Runnable() {
//						@Override
//						public void run() {
//							messageView.setVisibility(View.VISIBLE);
//							charInsertTray.setVisibility(View.GONE);
//
//							messageView.getLayoutParams().width = findViewById(R.id.message_char_insert_wrapper).getWidth();
//							charInsertTray.getLayoutParams().width = 0;
//						}
//					}, 100);
				}
			}
			
			@Override
			public void onAnimationRepeat(Animation animation) {}
		};
		
		messageAnimation.setAnimationListener(listener);
		charInsertAnimation.setAnimationListener(listener);
		
		messageView.startAnimation(messageAnimation);
		charInsertTray.startAnimation(charInsertAnimation);
		
		toggleCharInserts.startAnimation(rotate);
		
		charInserts = true;
    }
	
    protected void hideCharInserts() {
		if (!(keyboardVisible && charInserts)) {
			// No need to hide them if they're already hidden
			return;
		}
		
		TextView messageView = findViewById(R.id.message);
		HorizontalScrollView charInsertTray = findViewById(R.id.char_insert_tray);
		
		View buffer = findViewById(R.id.buffer);
		View sep = findViewById(R.id.toggle_char_inserts_separator);
		View wrapper = findViewById(R.id.toggle_wrapper);
		
		toggleCharInserts.setImageResource(messageType != MessageType.MESSAGE ? R.drawable.ic_caret_left_white : R.drawable.ic_caret_left_black);
		toggleProblemOverview.setImageResource(messageType != MessageType.MESSAGE ? R.drawable.problem_overview_white_unfilled : R.drawable.problem_overview_black_unfilled);
		
		int total = buffer.getWidth() - sep.getWidth() - wrapper.getWidth();
		
		RotateAnimation rotate = new RotateAnimation(180f, 360f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
		rotate.setInterpolator(new AccelerateDecelerateInterpolator());
		rotate.setRepeatCount(0);
		rotate.setDuration(200);
		
		messageView.startAnimation(new ResizeAnimation<LinearLayout>(messageView, 0, buffer.getHeight(), total, buffer.getHeight()));
		charInsertTray.startAnimation(new ResizeAnimation<LinearLayout>(charInsertTray, ResizeAnimation.DEFAULT, ResizeAnimation.DEFAULT, 0, ResizeAnimation.DEFAULT));
		toggleCharInserts.startAnimation(rotate);
		
		charInserts = false;
    }
	
	public void hideCharInsertsNoAnimation(boolean disable) {
		final View messageView = findViewById(R.id.message);
		final View charInsertTray = findViewById(R.id.char_insert_tray);
		
		toggleCharInserts.setImageResource(messageType != MessageType.MESSAGE ? R.drawable.ic_caret_left_white : R.drawable.ic_caret_left_black);
		toggleProblemOverview.setImageResource(messageType != MessageType.MESSAGE ? R.drawable.problem_overview_white_unfilled : R.drawable.problem_overview_black_unfilled);
		messageView.setVisibility(View.VISIBLE);
		charInsertTray.setVisibility(View.GONE);
		
		messageView.getLayoutParams().width = -1;
		messageView.requestLayout();
		
		if (disable) {
			charInserts = false;
		}
	}
    
    /**
     * Set up the character inserts tray.
     */
	public void reloadCharInserts() {
		if (!keyboardVisible) {
			return;
		}
		
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
    		button.setTextColor(getResources().getColor(messageType != MessageType.MESSAGE ? R.color.char_insert_button_light : R.color.char_insert_button));
    		button.setLayoutParams(new LinearLayout.LayoutParams((int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 25, getResources().getDisplayMetrics()), message));
    		button.setPadding(0, 0, 0, 0);
    		
    		// Disable key press sounds if the user wants them disabled
    		button.setSoundEffectsEnabled(getGlobalState().getPref("char_inserts_key_press_sound", false));
    		
    		//Maybe we'll want these at some point in time... but for now, they just cause more problems...
    		//...and the user won't be dragging the divider around if the keyboard is open (which it really should be)
//    		//Still let the user drag the message area
//    		button.setOnLongClickListener(messageListener);
//    		button.setOnTouchListener(messageListener);
    		
    		container.addView(button);
    		
    		button.setOnClickListener(view -> {
				// A special check for the tab key... making special exceptions aren't exactly ideal, but this is probably the most concise solution (for now)...
				KeyEvent event = c.equals("\u2192") ? new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_TAB) : new KeyEvent(SystemClock.uptimeMillis(), c, keyboardID, 0);
				
				boolean dispatched = false;
				
				if (extraHeaderView != null) {
					// If the find/replace toolbar is open
					
					EditText findTextField = extraHeaderView.findViewById(R.id.find_replace_find_text);
					EditText replaceTextField = extraHeaderView.findViewById(R.id.find_replace_replace_text);
					
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
					((Vibrator) getSystemService(VIBRATOR_SERVICE)).vibrate(10); //10 millis
			});
    	}
    }
	
	public static class ProblemOverviewListAdapter extends ArrayAdapter<CompilerProblem> {
		public ProblemOverviewListAdapter(@NonNull Context context, int resource, List<CompilerProblem> items) {
			super(context, resource, items);
		}
		
		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			View view = convertView;
			
			if (view == null) {
				view = LayoutInflater.from(getContext()).inflate(R.layout.problem_overview_list_item, null);
			}
			
			final CompilerProblem problem = getItem(position);
			
			if (view != null && view instanceof LinearLayout && problem != null) {
				LinearLayout container = (LinearLayout) view;
				TextView problemText = container.findViewById(R.id.problem_overview_list_item_problem_text);
				ImageView problemIcon = container.findViewById(R.id.problem_overview_list_item_problem_icon);
				
				problemText.setText(getProblemOverviewDescription(getContext(), problem));
				
				// Color the problem icon red (error) or yellow (warning)
				ImageViewCompat.setImageTintList(problemIcon, ColorStateList.valueOf(ContextCompat.getColor(getContext(), problem.isError() ? R.color.error_back : R.color.warning_back)));
			}
			
			return view;
		}
	}
	
	/**
	 * Build the problem overview description from a compiler problem. This is used in the problem
	 * overview and in the message area when the problem is shown there.
	 *
	 * @param context
	 * @param problem
	 * @return
	 */
	protected static SpannableStringBuilder getProblemOverviewDescription(Context context, CompilerProblem problem) {
		SpannableStringBuilder text = new SpannableStringBuilder();
		
		// This might be null for bad imports in the sketch, for example, because the corresponding line is off-screen
		if (problem.sketchFile != null) {
			text.append(problem.sketchFile.getTitle());
			text.append(" [");
			text.append(Integer.toString(problem.line + 1)); // add one because lines are zero-indexed
			text.append("]: ");
			text.setSpan(new ForegroundColorSpan(ContextCompat.getColor(context, android.R.color.darker_gray)), 0, text.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
		}
		
		text.append(problem.message);
		
		return text;
	}
	
	private boolean isOldBuild() {
		// TODO modular build is fully disabled for now
		//return !getGlobalState().getPref("pref_build_modular_enable", true);
		return true;
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
    	
    	// In case the user presses the button twice, we don't want any errors
    	if (building) {
    		return;
    	}
		
    	// Don't check for screen overlay in these cases:
		//  - user has explicitly disabled check
		//  - user has opted to build despite the warning
		//  - component target is set to preview, which has no installation to worry about, and the
		//    sketch previewer is already installed
		if (getGlobalState().getPref("pref_build_check_screen_overlay", true)
				&& !FLAG_SCREEN_OVERLAY_INSTALL_ANYWAY
				&& !(getComponentTarget() == ComponentTarget.PREVIEW && SketchPreviewerBuilder.isPreviewerInstalled(this))
				&& !checkScreenOverlay()) {
			return;
		}
		
		// Hide the soft keyboard
		InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
    	// For some reason, lint screams at us when we don't cast to LinearLayout here
		// Even though the cast actually does nothing
		// And it only screams at us when we do a release build - very strange
		imm.hideSoftInputFromWindow((findViewById(R.id.content)).getWindowToken(), 0);
    	
    	// Clear the console
    	((TextView) findViewById(R.id.console)).setText("");
    	
    	if (isOldBuild()) {
		    final BuildContext context;
		    try {
			    context = BuildContext.create(getGlobalState());
		    } catch (MaybeDocumentFile.MaybeDocumentFileException e) {
			    e.printStackTrace();
				return;
		    }
		    final Build builder = new Build(getGlobalState(), context);
	
			//Build the sketch in a separate thread
			Runnable task = () -> {
				building = true;
				changeRunStopIcon(true);
				
				long start = System.currentTimeMillis();
				
				toggleAutoCompileIndicator(true);
				
				builder.build("debug", getComponentTarget());
				
				toggleAutoCompileIndicator(false);
				
				System.out.println(String.format(Locale.US, "Finished in %1$dms",
						System.currentTimeMillis() - start));
				
				// Make some space in the console
				for (int i = 0; i < 10; i++) {
					System.out.println("");
				}
				
				changeRunStopIcon(false);
				building = false;
			};
		
			cancelAutoCompile();
			autoCompileTask = autoCompileTimer.schedule(task, 0, TimeUnit.SECONDS);
		} else {
			building = true;
			changeRunStopIcon(true);
		
			getGlobalState().getModularBuild().build(new ModularBuild.ContextualizedOnCompleteListener() {
				@Override
				public boolean onComplete(boolean success) {
					runOnUiThread(() -> {
						changeRunStopIcon(false);
						building = false;
					});
				
					return true;
				}
			});
		}
	
		if (android.os.Build.VERSION.SDK_INT >= 21) {
			runStopMenuButtonAnimating = true;
		}
    }
    
	/**
	 * Stops the current sketch's build process
	 * This CAN be called multiple times without breaking anything
	 */
	private void stopApplication() {
		// This will stop the current build process
		// I don't think we can stop a running app...
		// ...that's what the BACK button is for
		
		if (building) {
			// Not using modular build
			//getGlobalState().getModularBuild().halt();
		    
			Build.halt();
		}
	}
	
	public void changeRunStopIcon(final boolean run) {
		runStopMenuButton.post(() -> {
			if (android.os.Build.VERSION.SDK_INT >= 21) {
				AnimatedVectorDrawable anim = (AnimatedVectorDrawable) getDrawable(run ? R.drawable.run_to_stop : R.drawable.stop_to_run);
				runStopMenuButton.setImageDrawable(anim);
				anim.start();
				runStopMenuButtonAnimating = true;
				
				runStopMenuButton.postDelayed(() -> {
					supportInvalidateOptionsMenu();
					runStopMenuButtonAnimating = false;
				}, getResources().getInteger(R.integer.run_stop_animation_duration));
			} else {
				supportInvalidateOptionsMenu();
			}
		});
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
			
			LinearLayout layout = (LinearLayout) View.inflate(new ContextThemeWrapper(this, R.style.Theme_AppCompat_Dialog), R.layout.screen_overlay_dialog, null);
			final CheckBox dontShowAgain = (CheckBox) layout.findViewById(R.id.screen_overlay_dialog_dont_show_again);
			builder.setView(layout);
			
			builder.setPositiveButton(R.string.screen_overlay_dialog_install_anyway, (dialogInterface, i) ->
					{
						if (dontShowAgain.isChecked()) {
							// Change the setting to prevent dialog from appearing in the future
							getGlobalState().putPref("pref_build_check_screen_overlay", false);
						} else {
							// This will last until the activity gets destroyed. If the user wants to
							// make this permanent, then they should check "don't show again".
							FLAG_SCREEN_OVERLAY_INSTALL_ANYWAY = true;
						}
						
						// Run again
						runApplication();
					});
			
			builder.setNegativeButton(R.string.cancel, (dialogInterface, i) -> {});
			
			builder.setNeutralButton(R.string.export_signed_package_long_info_button, (dialogInterface, i) ->
					startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(getResources().getString(R.string.screen_overlay_info_wiki_url)))));
			
			builder.show();
			
			return false;
		} else {
			return true;
		}
	}
	
	private void updateMessageArea(String msg, MessageType type) {
		// Write the message
    	((TextView) findViewById(R.id.message)).setText(msg);
    	switch (type) {
			case MESSAGE:
				colorMessageAreaMessage();
				break;
			case ERROR:
				colorMessageAreaError();
				break;
			case WARNING:
				colorMessageAreaWarning();
				break;
		}
    	messageType = type;
    	// Update message area height
    	correctMessageAreaHeight();
	}
    
    /**
     * Writes a message to the message area
     * 
     * @param msg
     */
    public void message(String msg) {
    	updateMessageArea(msg, MessageType.MESSAGE);
    }
    
    /**
     * Writes a message to the message area from a non-UI thread
     * (this is used primarily from the build thread)
     * 
     * @param msg
     */
    public void messageExt(final String msg) {
    	runOnUiThread(() -> message(msg));
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
    	updateMessageArea(msg, MessageType.ERROR);
    }
    
    /**
     * Writes an error message to the message area from a non-UI thread
     * (this is used primarily from the build thread)
     * 
     * @param msg
     */
    public void errorExt(final String msg) {
    	runOnUiThread(() -> error(msg));
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
    
    public void warning(String msg) {
    	updateMessageArea(msg, MessageType.WARNING);
	}
	
	public void warningExt(final String msg) {
    	runOnUiThread(() -> warning(msg));
	}
    
    //Utility function for switching to message-style message area
	protected void colorMessageAreaMessage() {
    	//Change the message area style
    	findViewById(R.id.buffer).setBackgroundColor(getResources().getColor(R.color.message_back));
    	((TextView) findViewById(R.id.message)).setTextColor(getResources().getColor(R.color.message_text));
    	
    	//Update the toggle button
    	toggleCharInserts.setImageResource(charInserts ? R.drawable.ic_caret_right_black : R.drawable.ic_caret_left_black);
    	toggleProblemOverview.setImageResource(charInserts ? R.drawable.problem_overview_black_unfilled : R.drawable.problem_overview_black_unfilled);
    	
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
    	findViewById(R.id.buffer).setBackgroundColor(getResources().getColor(R.color.error_back));
    	((TextView) findViewById(R.id.message)).setTextColor(getResources().getColor(R.color.error_text));
    	
    	//Update the toggle button
    	toggleCharInserts.setImageResource(charInserts ? R.drawable.ic_caret_right_white : R.drawable.ic_caret_left_white);
		toggleProblemOverview.setImageResource(charInserts ? R.drawable.problem_overview_white_unfilled : R.drawable.problem_overview_white_unfilled);
    	
    	//Update the separator line
    	findViewById(R.id.toggle_char_inserts_separator).setBackgroundColor(getResources().getColor(R.color.toggle_char_inserts_separator_light));
    	
    	//Update the buttons in the character insert tray
    	LinearLayout charInsertTrayList = (LinearLayout) findViewById(R.id.char_insert_tray_list);
    	for(int i = 0; i < charInsertTrayList.getChildCount(); i ++) {
    		((Button) charInsertTrayList.getChildAt(i)).setTextColor(getResources().getColor(R.color.char_insert_button_light));
    	}
    }
	
	//Utility function for switching to warning-style message area
	protected void colorMessageAreaWarning() {
		//Change the message area style
		findViewById(R.id.buffer).setBackgroundColor(getResources().getColor(R.color.warning_back));
		((TextView) findViewById(R.id.message)).setTextColor(getResources().getColor(R.color.warning_text));
		
		//Update the toggle button
		toggleCharInserts.setImageResource(charInserts ? R.drawable.ic_caret_right_white : R.drawable.ic_caret_left_white);
		toggleProblemOverview.setImageResource(charInserts ? R.drawable.problem_overview_white_unfilled : R.drawable.problem_overview_white_unfilled);
		
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
    	final TextView messageArea = findViewById(R.id.message);
    	final View buffer = findViewById(R.id.buffer);
    	
    	// Update the message area's height
    	buffer.requestLayout();
    	
    	// Check back in later when the height has updated...
    	buffer.post(() -> {
			// ...and update the console's height...
			
			int totalWidth = findViewById(R.id.message_char_insert_wrapper).getWidth();
			
			// We need to use this in case the message area is partially off the screen
			// This is the DESIRED height, not the ACTUAL height
			message = getTextViewHeight(getApplicationContext(), messageArea.getText().toString(), messageArea.getTextSize(), totalWidth, messageArea.getPaddingTop());
			
			// The height the text view would be if it were just one line
			int singleLineHeight = getTextViewHeight(getApplicationContext(), "", messageArea.getTextSize(), totalWidth, messageArea.getPaddingTop());
			
			buffer.getLayoutParams().height = message;
			
			// Obtain some references
			View console = findViewById(R.id.console_wrapper);
			View content = findViewById(R.id.content);
			FrameLayout autoCompileProgress = findViewById(R.id.auto_compile_progress_wrapper);
			
			if (isSelectedCodeAreaInitialized()) {
				int consoleCodeHeight = content.getHeight() - message - (extraHeaderView != null ? extraHeaderView.getHeight() : 0) - tabBarContainer.getHeight() - autoCompileProgress.getHeight();
				int consoleHeight = consoleCodeHeight - codePager.getHeight();
				
				// We can't shrink the console if it's hidden (like when the keyboard is visible)...
				// ...so shrink the code area instead
				if (consoleHeight < 0 || keyboardVisible) {
					codePager.setLayoutParams(new LinearLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
							content.getHeight() - message - (extraHeaderView != null ? extraHeaderView.getHeight() : 0) - tabBarContainer.getHeight() - autoCompileProgress.getHeight()));
					console.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0));
				} else {
					console.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
							content.getHeight() - codePager.getHeight() - message - (extraHeaderView != null ? extraHeaderView.getHeight() : 0) - tabBarContainer.getHeight() - autoCompileProgress.getHeight()));
				}
			}
			
			// Note: For some reason modifying the LayoutParams directly is not working.
			// That's why we're re-setting the LayoutParams every time. Perhaps worth
			// looking into later.
			
			buffer.getLayoutParams().height = message;
			messageArea.getLayoutParams().height = message;
			
			//noinspection SuspiciousNameCombination
			setViewLayoutParams(toggleCharInserts, singleLineHeight, message);
			//noinspection SuspiciousNameCombination
			setViewLayoutParams(toggleProblemOverview, singleLineHeight, message);
			//noinspection SuspiciousNameCombination
			setViewLayoutParams(findViewById(R.id.toggle_wrapper), singleLineHeight, message);
			
			if (charInserts) {
				// Correct height of all of the char insert buttons
				findViewById(R.id.char_insert_tray).getLayoutParams().height = message;
				LinearLayout charInsertContainer = findViewById(R.id.char_insert_tray_list);
				charInsertContainer.getLayoutParams().height = message;
				for (int i = 0; i < charInsertContainer.getChildCount(); i++) {
					View view = charInsertContainer.getChildAt(i);
					LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) view.getLayoutParams();
					params.height = message;
					view.setLayoutParams(params);
				}
			}
		
			buffer.requestLayout();
		});
    }
    
    private void setViewLayoutParams(View view, int width, int height) {
    	view.getLayoutParams().width = width;
    	view.getLayoutParams().height = height;
    	view.requestLayout();
	}
	
	public void initCodeAreaAndConsoleDimensions() {
		// Initialize in case we have the layout weights instead of actual values
		codePager.getLayoutParams().height = codePager.getHeight();
		getConsoleWrapper().getLayoutParams().height = getConsoleWrapper().getHeight();
		codePager.requestLayout();
		getConsoleWrapper().requestLayout();
	}
	
	/**
	 * Fix inconsistencies in the vertical distribution of the content area views
	 */
	public void refreshMessageAreaLocation() {
		//Obtain some references
		final View content = findViewById(R.id.content);
		final View console = findViewById(R.id.console_wrapper);
		final View code = getSelectedCodeAreaScroller();
		final TextView messageArea = (TextView) findViewById(R.id.message);
		final FrameLayout autoCompileProgress = findViewById(R.id.auto_compile_progress_wrapper);
		
		if (firstResize) {
			//Use some better layout parameters - this switches from fractions/layout weights to absolute values
			code.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, code.getHeight()));
			console.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, console.getHeight()));

			firstResize = false;
		}
		
		messageArea.requestLayout();
		
		messageArea.post(() -> {
			//We need to use this in case the message area is partially off the screen
			//This is the DESIRED height, not the ACTUAL height
			message = getTextViewHeight(getApplicationContext(), messageArea.getText().toString(), messageArea.getTextSize(), findViewById(R.id.message_char_insert_wrapper).getWidth(), messageArea.getPaddingTop());

			int consoleSize = content.getHeight() - code.getHeight() - message - (extraHeaderView != null ? extraHeaderView.getHeight() : 0) - tabBarContainer.getHeight() - autoCompileProgress.getHeight();

			//We can't shrink the console if it's hidden (like when the keyboard is visible)...
			//...so shrink the code area instead
			if (consoleSize < 0 || consoleWasHidden || keyboardVisible) {
				console.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0));
				code.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
						content.getHeight() - message - (extraHeaderView != null ? extraHeaderView.getHeight() : 0) - tabBarContainer.getHeight() - autoCompileProgress.getHeight()));

				consoleWasHidden = true;
			} else {
				console.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, consoleSize));

				consoleWasHidden = false;
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
		highlightTextExt(tab, line, 0, -1);
	}
	
	public void highlightTextExt(final int tab, final int line, final int pos, final int length) {
    	runOnUiThread(new Runnable() {
			public void run() {
				// Switch to the tab with the error if we have one to switch to
				if (tab != -1 && tab < tabs.size()) {
					selectCode(tab);
				}
				
				// Get a reference to the code area
				final CodeEditText code = getSelectedCodeArea();
				
				// We can't highlight code if there is no tab selected
				if (code == null) {
					return;
				}
				
				// Calculate the beginning and ending of the line
				int lineStart = code.offsetForLine(line);
				int lineStop = code.offsetForLineEnd(line);
				
				final int start = Math.max(lineStart + pos, lineStart);
				final int stop = length == -1 ? lineStop : Math.min(start + length, lineStop);
				
				code.requestFocus();
				
				// This selects the code area, but this is not the behavior that we want anymore
				// because we want to be able to build in real-time and not steal focus from the
				// code area because the user might be typing
//				if (!PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getBoolean("use_hardware_keyboard", false)) {
//					//Check to see if the user wants to use the hardware keyboard
//
//					//Hacky way of focusing the code area - dispatches a touch event
//					MotionEvent me = MotionEvent.obtain(100, 0, 0, 0, 0, 0);
//					code.dispatchTouchEvent(me);
//					me.recycle();
//				}
				
				code.post(() -> {
					if (start >= 0 && start < code.length() && stop >= 0 && stop < code.length() && stop >= start) {
						// Select the text in the code area
						code.setSelection(start, stop);
					}
				});
			}
		});
    }
    
    protected void toggleCharInsertsProblemOverviewButton(boolean problemOverview, boolean animate) {
    	final View fadeIn = problemOverview ? toggleProblemOverview : toggleCharInserts;
    	final View fadeOut = problemOverview ? toggleCharInserts : toggleProblemOverview;
    	
    	// We are not using this animation for now
    	animate = false;
    	
    	// This is a rotate/crossfade animation. It works, but it isn't super-aesthetically
		// pleasing. Might want a better animation (using vector drawables) in the future.
    	if (animate) {
			int animTime = getResources().getInteger(android.R.integer.config_shortAnimTime);
			int theta = 90;
			
			fadeIn.setAlpha(0.0f);
			fadeIn.setVisibility(View.VISIBLE);
			
			fadeIn.setRotation(-theta);
			
			fadeIn.animate().alpha(1.0f).rotationBy(theta).setDuration(animTime)
					.setInterpolator(new DecelerateInterpolator()).setListener(null);
			fadeOut.animate().alpha(0.0f).rotationBy(theta).setDuration(animTime)
					.setInterpolator(new AccelerateInterpolator()).setListener(new AnimatorListenerAdapter() {
				@Override
				public void onAnimationEnd(Animator animator) {
					fadeOut.setVisibility(View.GONE);
					fadeOut.setRotation(0);
				}
			});
		} else {
    		fadeOut.setVisibility(View.GONE);
			fadeIn.setVisibility(View.VISIBLE);
		}
	}
	
	/**
	 * Update the list of compiler problems displayed for the current sketch. Called by build upon
	 * the completion of ECJ.
	 */
	public void showProblems(List<CompilerProblem> problems) {
		compilerProblems.clear();
		compilerProblems.addAll(problems);
		
		// Give the problems to all of the sketch files
		int i = 0;
		for (SketchFile sketchFile : getSketchFiles()) {
			// Each SketchFile figures out which problems it needs, so just give them every problem
			sketchFile.setCompilerProblems(compilerProblems, i);
			if (sketchFile.getFragment() != null && sketchFile.getFragment().getCodeEditText() != null) {
				runOnUiThread(sketchFile.getFragment().getCodeEditText()::invalidate);
			}
			i++;
		}
		
		// Put problems before warnings
		// TODO sort by position (need to sort by file first)
		Collections.sort(compilerProblems, (a, b) -> {
			if (a.isError() == b.isError()) {
				return 0;
			} else {
				return a.isError() ? -1 : 1;
			}
		});
    	
    	// Show the first problem or warning in the message bar
    	if (compilerProblems.size() > 0 && !getGlobalState().isExample()) {
    		if (compilerProblems.get(0).isError()) {
				// We have an error
				errorExt(compilerProblems.get(0).getMessage());
			} else {
				// We have at least one warning
				warningExt(compilerProblems.get(0).getMessage());
			}
		}
    	
    	if (compilerProblems.isEmpty() && messageType != MessageType.MESSAGE) {
    		// Clear the previous error/warning
    		messageExt("");
		}
		
		runOnUiThread(() -> {
			problemOverviewListAdapter.notifyDataSetChanged();
			
			// If there are no problems, leave the user a nice message
			boolean hasItems = compilerProblems.size() > 0;
			problemOverviewList.setVisibility(hasItems ? View.VISIBLE : View.GONE);
			findViewById(R.id.problem_overview_list_empty_message).setVisibility(hasItems ? View.GONE : View.VISIBLE);
		});
	}
	
	protected void addTabWithoutPagerUpdate(SketchFile sketchFile) {
		tabs.add(sketchFile);
	}
	
	public void addTab(SketchFile sketchFile) {
		tabs.add(sketchFile);
		codePagerAdapter.notifyDataSetChanged();
		scheduleAutoCompile(true);
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
					case R.id.menu_tab_duplicate:
						duplicateTab();
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
	 * Creates a user input dialog for duplicating the current tab
	 */
    private void duplicateTab() {
    	if(tabs.size() > 0 && !getGlobalState().isExample())
    		createInputDialog(getResources().getString(R.string.tab_duplicate_dialog_title), getResources().getString(R.string.tab_duplicate_dialog_message), getSelectedSketchFile().getTitle(), DUPLICATE_TAB);
    }
    
    /**
     * Creates a user input dialog for deleting the current tab
     */
    private void deleteTab() {
    	if(getGlobalState().isExample())
    		return;
    	
    	AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.tab_delete_dialog_title)
        	.setMessage(R.string.tab_delete_dialog_message)
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
    private boolean deleteLocalFile(String filename) throws MaybeDocumentFile.MaybeDocumentFileException {
    	MaybeDocumentFile sketchLoc = getGlobalState().getSketchLocation();
		MaybeDocumentFile file = sketchLoc.child(filename, SketchFile.PDE_MIME_TYPE);
    	
    	//Delete the file
    	if (file.exists()) {
    		file.delete();
			
    		return true;
    	}
    	
    	return false;
    }
    
    /**
     * Called internally from delete tab dialog
     */
    private void deleteTabContinue() {
		try {
			if (tabs.size() > 0) {
				// Delete the tab from the sketch folder
				deleteLocalFile(getSelectedSketchFile().getFilename());
				// Disable the tab (prevents it from being saved if we don't do things quite right)
				getSelectedSketchFile().disable();
				
				// Get the index before we delete the tab and
				int selectedCodeIndex = getSelectedCodeIndex();
				
				tabs.remove(selectedCodeIndex);
				
				// If there are no more tabs
				if (getCodeCount() <= 0) {
					// Force remove all tabs
					tabs.clear();
					
					// Force action menu refresh
					supportInvalidateOptionsMenu();
				}
				
				codePagerAdapter.notifyDataSetChanged();
				
				if (selectedCodeIndex == 0) {
					selectCode(0);
				} else if (selectedCodeIndex >= tabs.size() && tabs.size() > 0) {
					selectCode(tabs.size() - 1);
				}
				
				scheduleAutoCompile(true);
				
				// Inform the user in the message area
				message(getResources().getText(R.string.tab_delete_success));
			}
		} catch (MaybeDocumentFile.MaybeDocumentFileException e) {
			e.printStackTrace();
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
    
    // Called internally when the add or rename tab dialog is closed
    private void checkInputDialog(int key, boolean completed, String value) {
    	if (completed) {
    		// Make sure that this is a valid name for a tab
		    if (!(validateFileName(value) || (value.endsWith(".java") && value.length() > 5 && validateFileName(value.substring(0, value.length() - 5))))) {
    			return;
    		}
    		
    		switch(key) {
    		case RENAME_TAB:
				try {
					// Delete the tab from the sketch folder
					deleteLocalFile(getSelectedSketchFile().getFilename());
					
					// Change the tab as it is displayed
					getSelectedSketchFile().setTitle(value);
					codePagerAdapter.notifyDataSetChanged();
					
					scheduleAutoCompile(true);
					
					// Notify the user of success
					message(getResources().getText(R.string.tab_rename_success));
				} catch (MaybeDocumentFile.MaybeDocumentFileException e) {
					e.printStackTrace();
				}
			
			    break;
    		case NEW_TAB:
    			if (value.endsWith(".java")) {
    		    	// Correctly instantiate with a .JAVA suffix
    		    	SketchFile meta = new SketchFile(value.substring(0, value.length() - 5));
    		    	meta.setSuffix(".java");
    		    	
					addTab(meta);
					selectCode(getCodeCount() - 1);
    			} else {
    				// Add the tab
    				addTab(value);
					selectCode(getCodeCount() - 1);
    			}
    			
    			// Refresh options menu to remove "New Tab" button
				if (getCodeCount() == 1) {
					supportInvalidateOptionsMenu();
				}
				
				// Make sure that the code area is editable
//				((CodeEditText) findViewById(R.id.code)).setFocusable(true);
//				((CodeEditText) findViewById(R.id.code)).setFocusableInTouchMode(true);
				
				// Notify the user of success
				message(getResources().getText(R.string.tab_new_success));
				
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
			error(getResources().getText(R.string.tab_name_invalid_no_char));
			return false;
		}
		
		//Check to make sure that the first character isn't a number and isn't an underscore
		char first = title.charAt(0);
		if ((first >= '0' && first <= '9') || first == '_') {
			error(getResources().getText(R.string.tab_name_invalid_first_char));
			return false;
		}
		
		//Check all of the characters
		for (int i = 0; i < title.length(); i ++) {
			char c = title.charAt(i);
			if (c >= '0' && c <= '9') continue;
			if (c >= 'a' && c <= 'z') continue;
			if (c >= 'A' && c <= 'Z') continue;
			if (c == '_') continue;
			
			error(getResources().getText(R.string.tab_name_invalid_char));
			return false;
		}
		
		//Make sure that this file / tab doesn't already exist
		for (SketchFile meta : tabs) {
			if (meta.getTitle().equals(title)) {
				error(getResources().getText(R.string.tab_name_invalid_same_title));
				return false;
			}
		}
		
		return true;
	}
	
	private void launchTools() {
		final ArrayList<Tool> toolList = getGlobalState().getToolsInList();
		
		//Display a dialog containing the list of tools
		
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(R.string.editor_menu_tools);
		if(toolList.size() > 0) {
			//Populate the list
			builder.setItems(getGlobalState().listToolsInList(), new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					runOnUiThread(toolList.get(which));
				}
			});
		} else {
			//Eh... there should ALWAYS be tools, unless something funky is going on
			System.err.println(getResources().getString(R.string.tools_empty));
		}
		
		final AlertDialog dialog = builder.create();
		
		dialog.setCanceledOnTouchOutside(true);
		dialog.show();
	}
	
	public void launchImportLibrary() {
		try {
			getGlobalState().rebuildLibraryList();
			final String[] libList = getGlobalState().listLibraries();
			
			//Display a dialog containing the list of libraries
			
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setTitle(R.string.tool_import_library);
			if (libList.length > 0) {
				//Populate the list
				builder.setItems(libList, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						try {
							addImports(getGlobalState().getLibraryByName(libList[which]).getPackageList(getGlobalState()));
						} catch (MaybeDocumentFile.MaybeDocumentFileException e) {
							e.printStackTrace();
						}
					}
				});
			} else {
				//Show a message to the user
				//This is a real hack... and a strong argument for using XML / layout inflaters
				
				TextView content = new TextView(this);
				content.setText(R.string.library_manager_no_contributed_libraries); //The text we want
				content.setTextColor(getResources().getColor(R.color.grayed_out)); //The color we want
				content.setGravity(Gravity.CENTER); //Centered
				content.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)); //Centered...
				content.setPadding(60, 60, 60, 60); //... CENTERED!!!
				content.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
				
				builder.setView(content);
			}
			
			//The "Manage Libraries" button - null so that it won't automatically close itself
			builder.setNeutralButton(R.string.library_manager_open, null);
			final AlertDialog dialog = builder.create();
			
			//Fancy stuff...
			//StackOverflow: http://stackoverflow.com/questions/2620444/how-to-prevent-a-dialog-from-closing-when-a-button-is-clicked
			dialog.setOnShowListener(dialog1 -> {
				Button b = ((AlertDialog) dialog1).getButton(AlertDialog.BUTTON_NEUTRAL);
				b.setOnClickListener(view -> {
					launchManageLibraries();
					//It would be better if the dialog didn't fade out when we pressed the button... but this will have to do
					//...it's better than having it reappear when we back out of the library manager
					dialog1.dismiss();
				});
			});
			
			dialog.setCanceledOnTouchOutside(true);
			dialog.show();
		} catch (MaybeDocumentFile.MaybeDocumentFileException e) {
			e.printStackTrace();
		}
	}
	
	public void launchManageLibraries() {
		try {
			getGlobalState().rebuildLibraryList();
			
			Intent intent = new Intent(this, LibraryManagerActivity.class);
			startActivity(intent);
		} catch (MaybeDocumentFile.MaybeDocumentFileException e) {
			e.printStackTrace();
		}
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
	public void addImports(List<String> imports) {
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
		
		final TextView tv = findViewById(R.id.console);
		
		// Add the text
		tv.append(msg);
		
		final ScrollView scroll = findViewById(R.id.console_scroller);
		final HorizontalScrollView scrollX = findViewById(R.id.console_scroller_x);
		
		// Scroll to the bottom (if the user has this feature enabled)
		if(PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getBoolean("pref_scroll_lock", true))
			scroll.post(() -> {
				// Scroll to the bottom
				//scroll.fullScroll(ScrollView.FOCUS_DOWN);
				scroll.scrollTo(0, scroll.getHeight());
				
				scrollX.post(() -> {
					// Don't scroll horizontally at all...
					// TODO This doesn't really work
					scrollX.scrollTo(0, 0);
				});
		});
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
			console = findViewById(R.id.console_wrapper);
			content = findViewById(R.id.content);
		}
		
		@Override
		public boolean onTouch(View view, MotionEvent event) {
			view.performClick();
			
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
					
					FrameLayout autoCompileProgress = findViewById(R.id.auto_compile_progress_wrapper);
					
					// An important note for understanding the following code:
					// The tab bar is actually inside the code area pager, so the height of "code"
					// includes the height of the tab bar
					
					// Calculate maximum possible code view height
					int maxCode = content.getHeight() - message - (extraHeaderView != null ? extraHeaderView.getHeight() : 0) - tabBarContainer.getHeight() - autoCompileProgress.getHeight();
					
					// Find relative movement for this event
					int y = (int) event.getY() - touchOff;
					
					// Calculate the new dimensions of the console
					int consoleDim = Math.max(Math.min(console.getHeight() - y, maxCode), 0);
					
					// Calculate the new dimensions of the code view
					int codeDim = maxCode - consoleDim;
					
					// Set the new dimensions
					codePager.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, codeDim));
					console.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, consoleDim));
					
					firstResize = false;
					
					if (consoleDim > 0) {
						consoleWasHidden = false;
					}
					
					return true;
				case MotionEvent.ACTION_UP:
					pressed = false;
					
					View buffer = findViewById(R.id.buffer);
					TextView messageArea = findViewById(R.id.message);
					
					switch (messageType) {
						case MESSAGE:
							buffer.setBackgroundDrawable(getResources().getDrawable(R.drawable.back));
							messageArea.setTextColor(getResources().getColor(R.color.message_text));
							break;
						case ERROR:
							buffer.setBackgroundDrawable(getResources().getDrawable(R.drawable.back_error));
							messageArea.setTextColor(getResources().getColor(R.color.error_text));
							break;
						case WARNING:
							buffer.setBackgroundDrawable(getResources().getDrawable(R.drawable.back_warning));
							messageArea.setTextColor(getResources().getColor(R.color.warning_text));
							break;
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
			
			View buffer = findViewById(R.id.buffer);
			TextView messageArea = findViewById(R.id.message);
			
			// Change the message area drawable and maintain styling
			switch (messageType) {
				case MESSAGE:
					buffer.setBackgroundDrawable(getResources().getDrawable(R.drawable.back_selected));
					messageArea.setTextColor(getResources().getColor(R.color.message_text));
					break;
				case ERROR:
					buffer.setBackgroundDrawable(getResources().getDrawable(R.drawable.back_error_selected));
					messageArea.setTextColor(getResources().getColor(R.color.error_text));
					break;
				case WARNING:
					buffer.setBackgroundDrawable(getResources().getDrawable(R.drawable.back_warning_selected));
					messageArea.setTextColor(getResources().getColor(R.color.warning_text));
					break;
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
		private OutputStream pipeTo;
		private boolean enabled;
		
		public ConsoleStream(OutputStream pipeTo) {
			this.pipeTo = pipeTo;
			this.enabled = true;
		}
		
		public void disable() {
			enabled = false;
		}
		
		public void close() { }
		
		public void flush() { }
		
		public void write(byte b[]) {
			write(b, 0, b.length);
		}
		
		@Override
		public void write(byte b[], int offset, int length) {
			if (enabled) {
				// Also pipe output
				try {
					pipeTo.write(b, offset, length);
				} catch (IOException e) {
					// shrug
				}
				
				final String value = new String(b, offset, length);
				
				if (!(FLAG_SUSPEND_OUT_STREAM.get() &&
						!PreferenceManager.getDefaultSharedPreferences(EditorActivity.this)
								.getBoolean("pref_debug_global_verbose_output", false))) {
					
					runOnUiThread(() -> {
						//Write the value to the console
						postConsole(value);
					});
				}
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
				new Thread(() -> copyAssetFolder(getAssets(), "examples", getGlobalState().getStarterExamplesFolder().getAbsolutePath())).start();
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
		// Removed - moved down below
		
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
		
		// v0.5.0 Alpha-pre1 (branch android-mode-4)
		
		// Upgrade android.jar to latest version (API level 27)
		upgradeChanges.add(new UpgradeChange(22) {
			@Override
			public void run() {
				getGlobalState().getTaskManager().launchTask("recopyAndroidJarTask", false, null, false, new ExtractStaticBuildResources());
			}
		});
		
		// Upgrade sketchData file to include component target
		upgradeChanges.add(new UpgradeChange(22) {
			@Override
			public void run() {
				try {
					String oldSketchDataStr = readTempFile("sketchData.txt");
					String[] oldSketchData = oldSketchDataStr.split(";");
					if (oldSketchData.length >= 3) {
						StringBuilder newSketchData = new StringBuilder();
						for (int i = 0; i < 3; i++) {
							newSketchData.append(oldSketchData[i]);
							newSketchData.append(';');
						}
						newSketchData.append("0;");
						for (int i = 3; i < oldSketchData.length; i++) {
							newSketchData.append(oldSketchData[i]);
							newSketchData.append(';');
						}
						writeTempFile("sketchData.txt", newSketchData.toString());
					}
				} catch (Exception e) {
					e.printStackTrace();
					System.err.println(getResources().getString(R.string.apde_0_5_upgrade_error));
				}
			}
		});
		
		// v0.5.1 Alpha-pre4 (branch preview)
		
		// Change to preview component target for first run
		upgradeChanges.add(new UpgradeChange(30) {
			@Override
			public void run() {
				FLAG_PREVIEW_COMPONENT_TARGET_NEWLY_UPDATED = true;
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
