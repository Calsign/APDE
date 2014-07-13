package com.calsignlabs.apde;

import java.util.ArrayList;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * An adapter used for the file browser navigation drawer.
 */
public class FileNavigatorAdapter extends BaseAdapter {
	public static final String NAVIGATE_UP_TEXT = "..";
	
	private Context context;
	
	private ArrayList<FileItem> items;
	private int selected;
	
	public FileNavigatorAdapter(Context context, ArrayList<FileItem> items, int selected) {
		this.context = context;
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
		return true;
	}
	
	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		//Let's see if we can convert the old view - otherwise inflate a new one
		if(convertView == null) {
			LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		    convertView = inflater.inflate(R.layout.drawer_list_item, parent, false);
		}
		
		FileItem item = getItem(position);
		
		TextView text = ((TextView) convertView.findViewById(R.id.drawer_list_item_text));
		ImageView icon = ((ImageView) convertView.findViewById(R.id.drawer_list_item_icon));
		
		text.setText(item.getText());
		
		switch(item.getType()) {
		case FOLDER:
			icon.setImageDrawable(context.getResources().getDrawable(R.drawable.ic_folder));
			break;
		case SKETCH:
			icon.setImageDrawable(context.getResources().getDrawable(R.drawable.ic_launcher));
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
		
		return convertView;
	}
	
	@Override
	public long getItemId(int position) {
		return position;
	}
	
	public static enum FileItemType {
		NAVIGATE_UP, FOLDER, SKETCH
	}
	
	/**
	 * An item in the list - either a sketch, a folder, or the navigate up button.
	 */
	public static class FileItem {
		private String text;
		private FileItemType type;
		
		public FileItem(String text, FileItemType type) {
			this.text = text;
			this.type = type;
		}
		
		public String getText() {
			return text;
		}
		
		public FileItemType getType() {
			return type;
		}
	}
}