package com.calsignlabs.apde.build;

import com.calsignlabs.apde.R;

/**
 * Represents a component target - either app, wallpaper, watchface, or vr. These are the possible
 * components that APDE can build for.
 */
public enum ComponentTarget {
	APP(R.drawable.ic_comp_app, R.string.editor_menu_comp_select_app, 17, "AppActivity.java.tmpl", "AppManifest.xml.tmpl"),
	WALLPAPER(R.drawable.ic_comp_wallpaper, R.string.editor_menu_comp_select_wallpaper, 17, "WallpaperService.java.tmpl", "WallpaperManifest.xml.tmpl"),
	WATCHFACE(R.drawable.ic_comp_watchface, R.string.editor_menu_comp_select_watchface, 19, "WatchFaceService.java.tmpl", "WatchFaceManifest.xml.tmpl"),
	VR(R.drawable.ic_comp_vr, R.string.editor_menu_comp_select_vr, 25, "VRActivity.java.tmpl", "VRManifest.xml.tmpl"),
	PREVIEW(R.drawable.ic_comp_preview, R.string.editor_menu_comp_select_preview, 17, "AppActivity.java.tmpl", "AppManifest.xml.tmpl");
	
	private int iconId;
	private int nameId;
	
	private int minSdk;
	
	private String mainClassTemplate;
	private String manifestTemplate;
	
	ComponentTarget(int iconId, int nameId, int minSdk, String mainClassTemplate, String manifestTemplate) {
		this.iconId = iconId;
		this.nameId = nameId;
		this.minSdk = minSdk;
		this.mainClassTemplate = mainClassTemplate;
		this.manifestTemplate = manifestTemplate;
	}
	
	public int getIconId() {
		return iconId;
	}
	
	public int getNameId() {
		return nameId;
	}
	
	public int getMinSdk() {
		return minSdk;
	}
	
	public String getMainClassTemplate() {
		return mainClassTemplate;
	}
	
	public String getManifestTemplate() {
		return manifestTemplate;
	}
	
	public int serialize() {
		switch (this) {
			case APP: return 0;
			case WALLPAPER: return 1;
			case WATCHFACE: return 2;
			case VR: return 3;
			case PREVIEW: return 4;
		}
		
		return -1;
	}
	
	public static ComponentTarget deserialize(int serialized) {
		switch (serialized) {
			case 0: return APP;
			case 1: return WALLPAPER;
			case 2: return WATCHFACE;
			case 3: return VR;
			case 4: return PREVIEW;
		}
		
		return null;
	}
}
