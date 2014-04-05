package com.calsignlabs.apde.support;

import java.util.ArrayList;

import android.content.Context;
import android.support.v7.app.ActionBar.Tab;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;

/**
 * This is a custom wrapper of ActionBarCompat's internal ScrollingTabContainerView.
 * <br>
 * It is far from fully functional and likely contains several bugs.
 * However, if you are interested, you can see the way that I've done some things...
 * <br><br>
 * The purpose of this class is to provide a tab view within the application
 * (not in the Action Bar, which is part of the activity decoration) that can be
 * slid over by the drawer.
 */
public class ScrollingTabContainerView extends android.support.v7.internal.widget.ScrollingTabContainerView {
	private ArrayList<Tab> tabs;
	private int selectedTab;
	
	private TabClickListener clickListener;
	private TabListener tabListener;
	
	public ScrollingTabContainerView(Context context, TabListener tabListener) {
		super(context);
		
		tabs = new ArrayList<Tab>();
		selectedTab = -1;
		
		clickListener = new TabClickListener();
		this.tabListener = tabListener;
	}
	
	/*
	 * To appease the Android Tools gods.
	 */
	public ScrollingTabContainerView(Context context) {
		super(context);
		
		tabs = new ArrayList<Tab>();
		selectedTab = -1;
		
		clickListener = new TabClickListener();
		this.tabListener = null;
	}
	
	/*
	 * To appease the Android Tools gods.
	 */
	public ScrollingTabContainerView(Context context, AttributeSet attributes) {
		super(context);
		
		tabs = new ArrayList<Tab>();
		selectedTab = -1;
		
		clickListener = new TabClickListener();
		this.tabListener = null;
	}
	
	/*
	 * To appease the Android Tools gods.
	 */
	public ScrollingTabContainerView(Context context, AttributeSet attributes, int num) {
		super(context);
		
		tabs = new ArrayList<Tab>();
		selectedTab = -1;
		
		clickListener = new TabClickListener();
		this.tabListener = null;
	}
	
	public void addTab(Tab tab) {
		addTab(tab, getTabCount(), false);
	}
	
	public void addSelectTab(Tab tab) {
		addTab(tab, getTabCount(), true);
	}
	
	public void addTab(Tab tab, int pos) {
		addTab(tab, pos, false);
	}
	
	@Override
	public void addTab(Tab tab, boolean setSelected) {
		addTab(tab, tabs.size(), setSelected);
	}
	
	@Override
	public void addTab(Tab tab, int position, boolean setSelected) {
		Tab unselected = getSelectedTab();
		
		super.addTab(tab, position, setSelected);
		tabs.add(position, tab);
		
		if(setSelected || selectedTab == -1) {
			if(unselected != null)
				tabListener.onTabUnselected(unselected);
			
			selectTab(position);
			tabListener.onTabSelected(getSelectedTab());
		}
	}
	
	public void removeTab(Tab tab) {
		if(!tabs.contains(tab))
			return;
		
		removeTab(indexOfTab(tab));
	}
	
	public void removeTab(int tab) {
		if(tabs.size() <= 0 || tab >= tabs.size() || tab < 0)
			return;
		
		if(selectedTab >= tabs.size()) {
			selectTab(selectedTab - 1);
		}
		
		if(selectedTab == tab && tabs.size() > 1) {
			if(selectedTab == 0) {
				selectTab(selectedTab + 1);
			} else {
				selectTab(selectedTab - 1);
			}
		}
		
		super.removeTabAt(tab);
		tabs.remove(tab);
		
		if(selectedTab >= tabs.size()) {
			if(tabs.size() > 0) {
				selectTab(0);
			} else {
				selectedTab = -1;
			}
		}
	}
	
	@Override
	public void removeAllTabs() {
		super.removeAllTabs();
		tabs.clear();
		
		selectedTab = -1;
	}
	
	public void removeSelectedTab() {
		removeTab(selectedTab);
	}
	
	public void selectLoadDefaultTab() {
		if(getTabCount() <= 0)
			return;
		
		selectedTab = 0;
		tabListener.onTabSelected(getTab(0));
	}
	
	public void selectTab(Tab tab) {
		if(!tabs.contains(tab))
			return;
		
		selectTab(indexOfTab(tab));
	}
	
	public void selectTab(int tab) {
		if(selectedTab != -1 && selectedTab < tabs.size())
			tabListener.onTabUnselected(getSelectedTab());
		
		super.setTabSelected(tab);
		selectedTab = tab;
		
		tabListener.onTabSelected(getSelectedTab());
	}
	
	public void selectLastTab() {
		selectTab(getTabCount() - 1);
	}
	
	public int indexOfTab(Tab tab) {
		return tabs.indexOf(tab);
	}
	
	public int indexOfTab(String title) {
		for(int i = 0; i < tabs.size(); i ++)
			if(tabs.get(i).getText().toString().equals(title))
				return i;
		
		return -1;
	}
	
	public Tab getTab(int tab) {
		return tabs.get(tab);
	}
	
	public int getSelectedTabIndex() {
		return selectedTab;
	}
	
	public Tab getSelectedTab() {
		if(selectedTab == -1)
			return null;
		
		return tabs.get(selectedTab);
	}
	
	public int getTabCount() {
		return tabs.size();
	}
	
	public TabView getTabView(Tab tab) {
		LinearLayout group = (LinearLayout) getChildAt(0);
		return (TabView) group.getChildAt(indexOfTab(tab));
	}
	
	public TabView getTabView(String title) {
		LinearLayout group = (LinearLayout) getChildAt(0);
		return (TabView) group.getChildAt(indexOfTab(title));
	}
	
	public TabView getTabView(int tab) {
		LinearLayout group = (LinearLayout) getChildAt(0);
		return (TabView) group.getChildAt(tab);
	}
	
	public TabView getNewTabView() {
		LinearLayout group = (LinearLayout) getChildAt(0);
		return (TabView) group.getChildAt(group.getChildCount() - 1);
	}
	
	public TabClickListener getTabClickListener() {
		return clickListener;
	}
	
	protected class TabClickListener implements OnClickListener {
		@Override
		public void onClick(View view) {
			if(view != null) {
				TabView tabView = (TabView) view;
				tabView.getTab().select();
			}
			
			Tab toSelect = null;
			Tab toUnselect = null;
			
			for(int i = 0; i < getTabCount(); i ++) {
				final View child = ((LinearLayout) getChildAt(0)).getChildAt(i);
				boolean preSelected = child.isSelected();
				boolean postSelected = child == view;
				
				Tab tab =  ((TabView) child).getTab();
				
				if(preSelected && !postSelected)
					toUnselect = tab;
				
				if(!preSelected && postSelected)
					toSelect = tab;
				
				if(preSelected && postSelected)
					tabListener.onTabReselected(tab);
				
				child.setSelected(postSelected);
			}
			
			if(toUnselect != null) {
				//If this isn't a re-selection
				//So it's a full-fledged selection and deselection
				
				tabListener.onTabUnselected(toUnselect);
				tabListener.onTabSelected(toSelect);
				
				selectedTab = indexOfTab(toSelect);
				setTabSelected(selectedTab);
			}
		}
	}
	
	public interface TabListener {
		public void onTabUnselected(Tab tab);
		public void onTabSelected(Tab tab);
		public void onTabReselected(Tab tab);
	}
}