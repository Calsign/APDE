package com.calsignlabs.apde;

import java.util.LinkedHashMap;
import java.util.Map;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Adapter;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;

public class SectionedListAdapter extends BaseAdapter {
	public final Map<String, Adapter> sections = new LinkedHashMap<String, Adapter>();
	public final ArrayAdapter<String> headers;
	public final static int TYPE_SECTION_HEADER = 0;
	
	public SectionedListAdapter(Context context) {
		headers = new ArrayAdapter<String>(context, R.layout.drawer_list_header);
	}
	
	public void addSection(String section, Adapter adapter) {
		headers.add(section);
		sections.put(section, adapter);
	}
	
	public Object getItem(int position) {
		for(Object section : sections.keySet()) {
			Adapter adapter = sections.get(section);
			int size = adapter.getCount() + 1;
			
			if(position == 0)
				return section;
			if(position < size)
				return adapter.getItem(position - 1);
			
			position -= size;
		}
		
		return null;
	}
	
	public int getCount() {
		int total = 0;
		for(Adapter adapter : sections.values())
			total += adapter.getCount() + 1;
		return total;
	}
	
	@Override
	public int getViewTypeCount() {
		int total = 1;
		for(Adapter adapter : sections.values())
			total += adapter.getViewTypeCount();
		
		return total;
	}
	
	@Override
	public int getItemViewType(int position) {
		int type = 1;
		for(Object section : sections.keySet()) {
			Adapter adapter = sections.get(section);
			int size = adapter.getCount() + 1;
			
			if(position == 0)
				return TYPE_SECTION_HEADER;
			if(position < size)
				return type + adapter.getItemViewType(position - 1);
			
			position -= size;
			type += adapter.getViewTypeCount();
		}
		
		return -1;
	}
	
	public boolean areAllItemsSelectable() {
		return false;
	}
	
	@Override
	public boolean isEnabled(int position) {
		return (getItemViewType(position) != TYPE_SECTION_HEADER);
	}
	
	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		int sectionnum = 0;
		for(Object section : this.sections.keySet()) {
			Adapter adapter = sections.get(section);
			int size = adapter.getCount() + 1;
			
			if(position == 0)
				return headers.getView(sectionnum, convertView, parent);
			if(position < size)
				return adapter.getView(position - 1, convertView, parent);
			
			position -= size;
			sectionnum++;
		}
		
		return null;
	}
	
	@Override
	public long getItemId(int position) {
		return position;
	}
}