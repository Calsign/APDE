package com.calsignlabs.apde;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Point;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v7.app.AlertDialog;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Locale;

/**
 * An adapter used for the file browser navigation drawer.
 */
public class FileNavigatorAdapter extends BaseAdapter {
	public static final String NAVIGATE_UP_TEXT = "..";
	
	protected static Context context;
	
	private ArrayList<FileItem> items;
	private int selected;
	
	private static float dragPressX, dragPressY;
	private static FileItem dragItem;
	private static View dragView;
	private static boolean draggingIsSelected;
	
	private static LinearLayout folderActions;
	
	public FileNavigatorAdapter(Context context, ArrayList<FileItem> items, int selected) {
		FileNavigatorAdapter.context = context;
		this.items = items;
		this.selected = selected;
	}
	
	@Override
	public FileItem getItem(int position) {
		return items.get(position);
	}
	
	public int indexOfSketch(String sketchName) {
		for(int i = 0; i < items.size(); i ++) {
			FileItem item = items.get(i);
			
			if(item.getType().equals(FileItemType.SKETCH) && item.getText().equals(sketchName)) {
				return i;
			}
		}
		
		return -1;
	}
	
	public static int indexOfSketch(ArrayList<FileItem> items, String sketchName) {
		for(int i = 0; i < items.size(); i ++) {
			FileItem item = items.get(i);
			
			if(item.getType().equals(FileItemType.SKETCH) && item.getText().equals(sketchName)) {
				return i;
			}
		}
		
		return -1;
	}
	
	@Override
	public int getCount() {
		return items.size();
	}
	
	@Override
	public int getViewTypeCount() {
		return 1;
	}
	
	@Override
	public int getItemViewType(int position) {
		return 1;
	}
	
	@Override
	public boolean isEnabled(int position) {
		return !getItem(position).getType().equals(FileItemType.MESSAGE);
	}
	
	@SuppressLint("NewApi")
	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		//Let's see if we can convert the old view - otherwise inflate a new one
		if(convertView == null) {
			LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		    convertView = inflater.inflate(R.layout.drawer_list_item, parent, false);
		}
		
		final FileItem item = getItem(position);
		
		TextView path = ((TextView) convertView.findViewById(R.id.drawer_list_item_path));
		TextView text = ((TextView) convertView.findViewById(R.id.drawer_list_item_text));
		ImageView icon = ((ImageView) convertView.findViewById(R.id.drawer_list_item_icon));
		
		if(item.getPath().length() > 0) {
			path.setVisibility(View.VISIBLE);
			path.setText(item.getPath());
		} else {
			path.setVisibility(View.GONE);
			path.setText("");
		}
		
		text.setText(item.getText());
		
		switch(item.getType()) {
		case FOLDER:
			icon.setImageDrawable(context.getResources().getDrawable(R.drawable.ic_folder_closed));
			break;
		case SKETCH:
			//Try to load the sketch's icon
			
			File sketchFolder = ((APDE) context.getApplicationContext()).getSketchLocation(item.getSketch().getPath(), item.getSketch().getLocation());
			String[] iconTitles = {"icon-96.png", "icon-72.png", "icon-48.png", "icon-36.png"}; //Prefer the higher-resolution icons
			String iconPath = "";
			
			for (String iconTitle : iconTitles) {
				File iconFile = new File(sketchFolder, iconTitle);
				
				if (iconFile.exists()) {
					iconPath = iconFile.getAbsolutePath();
					break;
				}
			}
			
			if (!iconPath.equals("")) {
				Bitmap curIcon = BitmapFactory.decodeFile(iconPath);
				
				if (curIcon != null) {
					int iconSize = Math.round(36 * context.getResources().getDisplayMetrics().density);
					icon.setImageBitmap(Bitmap.createScaledBitmap(curIcon, iconSize, iconSize, false));
				} else {
					//Uh-oh, some error occurred...
				}
			} else {
				icon.setImageDrawable(context.getResources().getDrawable(R.drawable.default_icon));
			}
			break;
		default:
			icon.setImageDrawable(null);
			break;
		}
		
		//Do the selection manually...
		if(position == selected) {
			convertView.setBackgroundColor(context.getResources().getColor(R.color.holo_select));
		} else {
			convertView.setBackgroundResource(R.drawable.bg_key);
		}
		
		if(item.getType().equals(FileItemType.MESSAGE)) {
			text.setTextColor(context.getResources().getColor(R.color.grayed_out));
			text.setTextSize(16);
			((RelativeLayout) convertView).setGravity(Gravity.CENTER);
		} else {
			text.setTextColor(0xFF222222);
			text.setTextSize(18);
			((RelativeLayout) convertView).setGravity(Gravity.CENTER_VERTICAL);
		}
		
		//TODO Find out how to support 2.3.3...
		if(android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.HONEYCOMB) {
			convertView.setTag(item);
			//We need the listener for all of the views because they get mixed up when they get recycled
			//Unfortunately, this means that everything becomes a lot slower...
			convertView.setOnDragListener(folderDragListener);
			
			//Store last touch coordinates
			if(item.isDraggable()) {
				convertView.setOnTouchListener(new View.OnTouchListener() {
					@Override
					public boolean onTouch(View view, MotionEvent event) {
						if(event.getActionMasked() == MotionEvent.ACTION_DOWN) {
							dragPressX = event.getX();
							dragPressY = event.getY();
						}
						
						return false;
					}
				});
			}
		}
		
		return convertView;
	}
	
	@SuppressLint("NewApi")
	public static void startDrag(final FileItem item, final View view) {
		ClipData dragData = new ClipData(new ClipDescription(item.getText(), new String[] {ClipDescription.MIMETYPE_TEXT_PLAIN}), new ClipData.Item(item.toString()));
		
		//Draw relative to the touch
		View.DragShadowBuilder dragShadow = new View.DragShadowBuilder(view) {
			@Override
			public void onProvideShadowMetrics(Point size, Point touch) {
				size.set(view.getWidth(), view.getHeight());
				touch.set((int) dragPressX, (int) dragPressY);
			}
			
			@Override
			public void onDrawShadow(Canvas canvas) {
				super.onDrawShadow(canvas);
			}
		};
		
		view.startDrag(dragData, dragShadow, item.toString(), 0);
		
		//Unfortunately, we have to to all of this every time...
		
		folderActions = (LinearLayout) ((APDE) context.getApplicationContext()).getEditor().findViewById(R.id.folder_actions);
		
		folderActions.findViewById(R.id.new_folder).setOnDragListener(new View.OnDragListener() {
			@Override
			public boolean onDrag(View view, DragEvent event) {
				switch(event.getAction()) {
				case DragEvent.ACTION_DRAG_STARTED:
					if(dragItem != null) {
						return true;
					}
					
					return false;
				case DragEvent.ACTION_DRAG_ENTERED:
					view.setBackgroundColor(context.getResources().getColor(R.color.holo_select));
					view.invalidate();
					return true;
					
				case DragEvent.ACTION_DRAG_LOCATION:
					return true;
					
				case DragEvent.ACTION_DRAG_EXITED:
					view.setBackgroundColor(context.getResources().getColor(R.color.holo_select_light));
					view.invalidate();
					return true;
					
				case DragEvent.ACTION_DROP:
					actionNewFolder();
					return true;
					
				case DragEvent.ACTION_DRAG_ENDED:
					view.setBackgroundColor(context.getResources().getColor(R.color.holo_select_light));
					view.invalidate();
					return true;
				}
				
				return false;
			}
		});
		
		folderActions.findViewById(R.id.edit_folder).setOnDragListener(new View.OnDragListener() {
			@Override
			public boolean onDrag(View view, DragEvent event) {
				switch(event.getAction()) {
				case DragEvent.ACTION_DRAG_STARTED:
					if(dragItem != null) {
						return true;
					}
					
					return false;
				case DragEvent.ACTION_DRAG_ENTERED:
					view.setBackgroundColor(context.getResources().getColor(R.color.holo_select));
					view.invalidate();
					return true;
					
				case DragEvent.ACTION_DRAG_LOCATION:
					return true;
					
				case DragEvent.ACTION_DRAG_EXITED:
					view.setBackgroundColor(context.getResources().getColor(R.color.holo_select_light));
					view.invalidate();
					return true;
					
				case DragEvent.ACTION_DROP:
					actionEditFolder();
					return true;
					
				case DragEvent.ACTION_DRAG_ENDED:
					view.setBackgroundColor(context.getResources().getColor(R.color.holo_select_light));
					view.invalidate();
					return true;
				}
				
				return false;
			}
		});
		
		folderActions.findViewById(R.id.delete_folder).setOnDragListener(new View.OnDragListener() {
			@Override
			public boolean onDrag(View view, DragEvent event) {
				switch(event.getAction()) {
				case DragEvent.ACTION_DRAG_STARTED:
					if(dragItem != null) {
						return true;
					}
					
					return false;
				case DragEvent.ACTION_DRAG_ENTERED:
					view.setBackgroundColor(context.getResources().getColor(R.color.red_select));
					view.invalidate();
					return true;
					
				case DragEvent.ACTION_DRAG_LOCATION:
					return true;
					
				case DragEvent.ACTION_DRAG_EXITED:
					view.setBackgroundColor(context.getResources().getColor(R.color.red_select_light));
					view.invalidate();
					return true;
					
				case DragEvent.ACTION_DROP:
					actionDeleteFolder();
					return true;
					
				case DragEvent.ACTION_DRAG_ENDED:
					view.setBackgroundColor(context.getResources().getColor(R.color.red_select_light));
					view.invalidate();
					return true;
				}
				
				return false;
			}
		});
		
		folderActions.setVisibility(View.VISIBLE);
		
		dragItem = item;
		dragView = view;
	}
	
	private static void actionNewFolder() {
		final APDE global = (APDE) context.getApplicationContext();
		
		AlertDialog.Builder builder = new AlertDialog.Builder(((APDE) context.getApplicationContext()).getEditor());
		
		builder.setTitle(R.string.move_to_new_folder_title);
		builder.setMessage(R.string.move_to_new_folder_descrption);
    	
		final EditText input = global.createAlertDialogEditText(global.getEditor(), builder, "", false);
		
		builder.setPositiveButton(R.string.create, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				String folderName = input.getText().toString();
				
				if(!validateFolderName(folderName, global.getEditor())) {
					if(!PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext()).getBoolean("use_hardware_keyboard", false))
						((InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(input.getWindowToken(), 0);
					
					return;
				}
				
				APDE.SketchMeta source = dragItem.getSketch();
				APDE.SketchMeta dest = new APDE.SketchMeta(source.getLocation(), source.getParent() + "/" + folderName + "/" + source.getName());
				
				File newFolder = global.getSketchLocation(source.getParent() + "/" + folderName, source.getLocation());
				
				//Let's not overwrite anything...
				//TODO Maybe give the user options to replace / keep both in the new location?
				//We don't need that much right now, they can deal with things manually...
				if(newFolder.exists()) {
					if(!PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext()).getBoolean("use_hardware_keyboard", false))
						((InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(input.getWindowToken(), 0);
					
					showDialog(R.string.cannot_move_sketch_title, R.string.cannot_move_folder_message, global.getEditor());
					
					return;
				}
				
				global.moveFolder(source, dest, global.getEditor());
				
				if(!PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext()).getBoolean("use_hardware_keyboard", false))
					((InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(input.getWindowToken(), 0);
			}
		});
		
		builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
    		public void onClick(DialogInterface dialog, int whichButton) {
    			if(!PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext()).getBoolean("use_hardware_keyboard", false))
    				((InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(input.getWindowToken(), 0);
    		}
    	});
		
		AlertDialog dialog = builder.create();
		if(!PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext()).getBoolean("use_hardware_keyboard", false))
    		dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
		dialog.show();
	}
	
	private static void actionEditFolder() {
		final boolean isSketch = dragItem.getType().equals(FileItemType.SKETCH);
		
		final APDE global = (APDE) context.getApplicationContext();
		
		AlertDialog.Builder builder = new AlertDialog.Builder(((APDE) context.getApplicationContext()).getEditor());
		
		builder.setTitle(String.format(Locale.US, context.getResources().getString(isSketch ? R.string.rename_sketch_title : R.string.rename_folder_title), dragItem.getText()));
		builder.setMessage(context.getResources().getString(isSketch ? R.string.rename_sketch_message : R.string.rename_folder_message));
		
		final EditText input = global.createAlertDialogEditText(global.getEditor(), builder, dragItem.getText(), true);
		
		builder.setPositiveButton(R.string.rename, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				String folderName = input.getText().toString();
				
				if(!(isSketch ? validateSketchName(folderName, global.getEditor()) : validateFolderName(folderName, global.getEditor()))) {
					if(!PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext()).getBoolean("use_hardware_keyboard", false))
						((InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(input.getWindowToken(), 0);
					
					return;
				}
				
				APDE.SketchMeta source = dragItem.getSketch();
				APDE.SketchMeta dest = new APDE.SketchMeta(source.getLocation(), source.getParent() + "/" + folderName);
				
				global.moveFolder(source, dest, global.getEditor());
				
				if(!PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext()).getBoolean("use_hardware_keyboard", false))
					((InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(input.getWindowToken(), 0);
			}
		});
		
		builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
    		public void onClick(DialogInterface dialog, int whichButton) {
    			if(!PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext()).getBoolean("use_hardware_keyboard", false))
    				((InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(input.getWindowToken(), 0);
    		}
    	});
		
		AlertDialog dialog = builder.create();
    	if(!PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext()).getBoolean("use_hardware_keyboard", false))
    		dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
    	dialog.show();
	}
	
	private static void actionDeleteFolder() {
		boolean isSketch = dragItem.getType().equals(FileItemType.SKETCH);
		
		final APDE global = (APDE) context.getApplicationContext();
		
		AlertDialog.Builder builder = new AlertDialog.Builder(((APDE) context.getApplicationContext()).getEditor());
		
		builder.setTitle(String.format(Locale.US, context.getResources().getString(R.string.delete_sketch_dialog_title), dragItem.getText()));
		builder.setMessage(String.format(Locale.US, context.getResources().getString(isSketch ? R.string.delete_sketch_dialog_message : R.string.delete_folder_dialog_message), dragItem.getText()));
		
		builder.setPositiveButton(R.string.delete, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				try {
					APDE.deleteFile(global.getSketchLocation(dragItem.getSketch().getPath(), dragItem.getSketch().getLocation()));
				} catch (IOException e) {
					e.printStackTrace();
				}
				
				APDE.SketchLocation draggedLocation = dragItem.getSketch().getLocation();
				APDE.SketchLocation selectedLocation = global.getSketchLocationType();
				
				String draggedPath = dragItem.getSketch().getPath();
				String selectedPath = global.getSketchPath();
				
				boolean draggingFolderContainsSelected = draggedLocation.equals(selectedLocation) && selectedPath.startsWith(draggedPath + "/");
				
				if(draggingIsSelected || draggingFolderContainsSelected) {
	    			global.selectSketch(APDE.DEFAULT_SKETCH_NAME, APDE.SketchLocation.TEMPORARY);
	    			global.getEditor().newSketch();
	    			
	    			global.getEditor().getSupportActionBar().setTitle(global.getSketchName());
				}
				
				global.getEditor().forceDrawerReload();
			}
		});
		
		builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
    		public void onClick(DialogInterface dialog, int whichButton) {}
    	});
		
		builder.create().show();
	}
	
	protected static boolean validateFolderName(String name, Activity activityContext) {
		//Make sure that the folder name's length > 0
		if (name.length() <= 0 || name.trim().length() <= 0) {
			showDialog(R.string.invalid_name, R.string.invalid_name_folder_no_text, activityContext);
			return false;
		}
		
		//Check all of the characters
		for (int i = 0; i < name.length(); i ++) {
			char c = name.charAt(i);
			if (c >= '0' && c <= '9') continue;
			if (c >= 'a' && c <= 'z') continue;
			if (c >= 'A' && c <= 'Z') continue;
			if (c == '_') continue;
			if (c == ' ') continue;
			
			showDialog(R.string.invalid_name, R.string.invalid_name_folder_invalid_char, activityContext);
			return false;
		}
		
		return true;
	}
	
	protected static boolean validateSketchName(String name, Activity activityContext) {
    	//Make sure that the name contains text
		if (name.length() <= 0) {
			showDialog(R.string.invalid_name, R.string.invalid_name_sketch_no_char, activityContext);
			return false;
		}
		
		//Check to make sure that the first character isn't a number and isn't an underscore
		char first = name.charAt(0);
		if ((first >= '0' && first <= '9') || first == '_') {
			showDialog(R.string.invalid_name, R.string.invalid_name_sketch_first_char, activityContext);
			return false;
		}
		
		//Check all of the characters
		for (int i = 0; i < name.length(); i ++) {
			char c = name.charAt(i);
			if (c >= '0' && c <= '9') continue;
			if (c >= 'a' && c <= 'z') continue;
			if (c >= 'A' && c <= 'Z') continue;
			if (c == '_') continue;
			
			showDialog(R.string.invalid_name, R.string.invalid_name_sketch_invalid_char, activityContext);
			return false;
		}
		
		//We can't have the name "sketch"
		if (name.equals(APDE.DEFAULT_SKETCH_NAME)) {
			showDialog(R.string.invalid_name, R.string.invalid_name_sketch_sketch, activityContext);
			return false;
		}
		
		return true;
	}
	
	protected static void showDialog(int titleId, int messageId, Activity activityContext) {
		//Convenience method
		showDialog(context.getResources().getString(titleId), context.getResources().getString(messageId), activityContext);
	}
	
	protected static void showDialog(String title, String message, Activity activityContext) {
		AlertDialog.Builder builder = new AlertDialog.Builder(activityContext);
		
		builder.setTitle(title);
		builder.setMessage(message);
		
		builder.setPositiveButton(context.getResources().getString(R.string.ok), new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {}
		});
		
		builder.create().show();
	}
	
	@SuppressLint("NewApi")
	public boolean onLongClickItem(final View view, int position) {
		//TODO Find out how to support 2.3.3...
		if(android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.HONEYCOMB) {
			FileItem item = getItem(position);
			
			if(item.isDraggable()) {
				startDrag(item, view);
				draggingIsSelected = position == selected;
				
				return true;
			}
		}
		
		return false;
	}
	
	private static View.OnDragListener folderDragListener;
	
	static {
		if(android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.HONEYCOMB) {
			folderDragListener = new View.OnDragListener() {
				private Handler delayHandler = new Handler();
				
				@SuppressLint("NewApi")
				@SuppressWarnings("deprecation")
				@Override
				public boolean onDrag(View view, DragEvent event) {
					final FileItem item = (FileItem) view.getTag();
					
					switch(event.getAction()) {
					case DragEvent.ACTION_DRAG_STARTED:
						if(event.getClipDescription().hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN)) {
							//When scrolling, views are recycled and it makes a mess...
							if(item.isDroppable() && dragItem != null && !item.equals(dragItem)) {
								view.setBackgroundColor(context.getResources().getColor(R.color.holo_select_light));
								view.invalidate();
							}
							
							//We have to do this here for some reason...
							if(dragView != null) {
								dragView.setBackgroundColor(context.getResources().getColor(R.color.grayed_out));
								dragView.invalidate();
							}
							
							return true;
						}
						
						return false;
						
					case DragEvent.ACTION_DRAG_ENTERED:
						//When scrolling, views are recycled and it makes a mess...
						if(item.isDroppable() && dragItem != null && !item.equals(dragItem)) {
							view.setBackgroundColor(context.getResources().getColor(R.color.holo_select));
							if(item.getType().equals(FileItemType.FOLDER)) {
								((ImageView) view.findViewById(R.id.drawer_list_item_icon)).setImageDrawable(context.getResources().getDrawable(R.drawable.ic_folder_open));
							}
							view.invalidate();
						}
						
						/*
						 * TODO Unfortunately, we can't have this fancy behavior because it doesn't work.
						 * Desired behavior: hovering over a folder with a dragged item for one second causes the drawer to navigate into the drawer...
						 * ...with the dragged item still dragging...
						 * ... for a more fluid drag and drop experience between multiple folders.
						 * The dragged item still drags, but none of the views are able to recieve drag events...
						 * ...because they weren't created until after the drag event started.
						 * They were created after because the list is populated on demand when a folder is opened.
						 * ...
						 * I can't find a workaround...
						 * Starting the drag again doesn't work.
						 * We can't even stop the first drag event...
						 * ...not even by dispatching an ACTION_UP motion event.
						 * ...
						 * So it's probably not worth the trouble...
						 */
						
						//				delayHandler.postDelayed(new Runnable() {
						//					@Override
						//					public void run() {
						//						((APDE) context.getApplicationContext()).getEditor().setDrawerLocation(item.getSketch());
						//						startDrag(dragItem, dragView);
						//						
						//						//Provide haptic feedback (if the user has vibrations enabled)
						//						if(PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext()).getBoolean("pref_vibrate", true))
						//							((android.os.Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE)).vibrate(100); //100 millis
						//					}
						//				}, 1000);
						
						return true;
						
					case DragEvent.ACTION_DRAG_LOCATION:
						return true;
						
					case DragEvent.ACTION_DRAG_EXITED:
						//Don't flip out if we've navigated out of this folder...
						//When scrolling, views are recycled and it makes a mess...
						if(item != null && item.isDroppable() && dragItem != null && !item.equals(dragItem)) {
							view.setBackgroundColor(context.getResources().getColor(R.color.holo_select_light));
							if(item.getType().equals(FileItemType.FOLDER)) {
								((ImageView) view.findViewById(R.id.drawer_list_item_icon)).setImageDrawable(context.getResources().getDrawable(R.drawable.ic_folder_closed));
							}
							view.invalidate();
						}
						
						delayHandler.removeCallbacksAndMessages(null);
						
						return true;
						
					case DragEvent.ACTION_DROP:
						//Don't flip out if we've navigated out of this folder...
						//When scrolling, views are recycled and it makes a mess...
						if(item != null && item.isDroppable() && dragItem != null && !item.equals(dragItem)) {
							APDE global = (APDE) context.getApplicationContext();
							
							APDE.SketchMeta source = dragItem.getSketch();
							APDE.SketchMeta destFolder = item.getSketch();
							
							APDE.SketchMeta destSketch = new APDE.SketchMeta(destFolder.getLocation(), destFolder.getPath() + "/" + source.getName());
							
							global.moveFolder(source, destSketch, global.getEditor());
						}
						
						return true;
						
					case DragEvent.ACTION_DRAG_ENDED:
						//Don't flip out if we've navigated out of this folder...
						//When scrolling, views are recycled and it makes a mess...
						if(item != null && item.isDroppable() && dragItem != null && !item.equals(dragItem)) {
							view.setBackgroundDrawable(context.getResources().getDrawable(R.drawable.bg_key));
							if(item.getType().equals(FileItemType.FOLDER)) {
								((ImageView) view.findViewById(R.id.drawer_list_item_icon)).setImageDrawable(context.getResources().getDrawable(R.drawable.ic_folder_closed));
							}
							view.invalidate();
						}
						
						if(draggingIsSelected) {
							dragView.setBackgroundColor(context.getResources().getColor(R.color.holo_select));
						} else {
							dragView.setBackgroundDrawable(context.getResources().getDrawable(R.drawable.bg_key));
						}
						
						folderActions.setVisibility(View.GONE);
						
						delayHandler.removeCallbacksAndMessages(null);
						
						return true;
					}
					
					return false;
				}
			};
		}
	}
	
	@Override
	public long getItemId(int position) {
		return position;
	}
	
	public static enum FileItemType {
		NAVIGATE_UP, MESSAGE, FOLDER, SKETCH;
		
		@Override
		public String toString() {
			switch(this) {
			case NAVIGATE_UP:
				return "navigateUp";
			case MESSAGE:
				return "message";
			case FOLDER:
				return "folder";
			case SKETCH:
				return "sketch";
			default:
				return "";
			}
		}
		
		public static FileItemType fromString(String value) {
			if (value.equals("navigateUp"))
				return NAVIGATE_UP;
			if (value.equals("message"))
				return MESSAGE;
			if (value.equals("folder"))
				return FOLDER;
			if (value.equals("sketch"))
				return SKETCH;
			
			//Uh-oh...
			return null;
		}
	}
	
	/**
	 * An item in the list - either a sketch, a folder, or the navigate up button.
	 */
	public static class FileItem {
		//Used for display
		private String path;
		private String text;
		private FileItemType type;
		
		//Used for the drag and drop mechanism
		private boolean draggable;
		private boolean droppable;
		
		//Used for the actual sketch information
		private APDE.SketchMeta sketchMeta;
		
		public FileItem(String text, FileItemType type) {
			this.path = "";
			this.text = text;
			this.type = type;
			draggable = false;
			droppable = false;
			sketchMeta = new APDE.SketchMeta(APDE.SketchLocation.TEMPORARY, "");
		}
		
		public FileItem(String path, String text, FileItemType type) {
			this.path = path;
			this.text = text;
			this.type = type;
			draggable = false;
			droppable = false;
			sketchMeta = new APDE.SketchMeta(APDE.SketchLocation.TEMPORARY, "");
		}
		
		public FileItem(String text, FileItemType type, boolean draggable, boolean droppable) {
			this.path = "";
			this.text = text;
			this.type = type;
			this.draggable = draggable;
			this.droppable = droppable;
			sketchMeta = new APDE.SketchMeta(APDE.SketchLocation.TEMPORARY, "");
		}
		
		public FileItem(String path, String text, FileItemType type, boolean draggable, boolean droppable) {
			this.path = path;
			this.text = text;
			this.type = type;
			this.draggable = draggable;
			this.droppable = droppable;
			sketchMeta = new APDE.SketchMeta(APDE.SketchLocation.TEMPORARY, "");
		}
		
		public FileItem(String text, FileItemType type, boolean draggable, boolean droppable, APDE.SketchMeta sketchMeta) {
			this.path = "";
			this.text = text;
			this.type = type;
			this.draggable = draggable;
			this.droppable = droppable;
			this.sketchMeta = sketchMeta;
		}
		
		public FileItem(String path, String text, FileItemType type, boolean draggable, boolean droppable, APDE.SketchMeta sketchMeta) {
			this.path = path;
			this.text = text;
			this.type = type;
			this.draggable = draggable;
			this.droppable = droppable;
			this.sketchMeta = sketchMeta;
		}
		
		public String getPath() {
			return path;
		}
		
		public String getText() {
			return text;
		}
		
		public FileItemType getType() {
			return type;
		}
		
		public boolean isDraggable() {
			return draggable;
		}
		
		public boolean isDroppable() {
			return droppable;
		}
		
		public APDE.SketchMeta getSketch() {
			return sketchMeta;
		}
		
		@Override
		public String toString() {
			return type.toString() + ":" + path + ":" + text + ":" + Boolean.toString(draggable) + ":" + Boolean.toString(droppable) + ":" + sketchMeta.toString();
		}
		
		public static FileItem fromString(String value) {
			String[] parts = value.split(":");
			
			if(parts.length != 6) {
				return null;
			}
			
			return new FileItem(parts[1], parts[2], FileItemType.fromString(parts[0]), Boolean.parseBoolean(parts[3]), Boolean.parseBoolean(parts[4]), APDE.SketchMeta.fromString(parts[5]));
		}
		
		@Override
		public boolean equals(Object other) {
			if(other instanceof FileItem) {
				FileItem otherFileItem = (FileItem) other;
				
				return type.equals(otherFileItem.getType()) && path.equals(otherFileItem.getPath()) && text.equals(otherFileItem.getText())
						&& draggable == otherFileItem.isDraggable() && droppable == otherFileItem.isDroppable();
			}
			
			return false;
		}
	}
}