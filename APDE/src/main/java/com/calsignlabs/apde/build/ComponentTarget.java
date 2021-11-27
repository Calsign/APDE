package com.calsignlabs.apde.build;

import com.calsignlabs.apde.R;

/**
 * Represents a component target - either app, wallpaper, watchface, or vr. These are the possible
 * components that APDE can build for.
 */
public enum ComponentTarget {
	APP(
			R.drawable.ic_comp_app,
			R.string.editor_menu_comp_select_app,
			17,
			"AppActivity.java",
			"MainActivity.java",
			"AppManifest.xml",
			new String[] {"commonArtifacts"}),
	WALLPAPER(
			R.drawable.ic_comp_wallpaper,
			R.string.editor_menu_comp_select_wallpaper,
			17,
			"WallpaperService.java",
			"MainService.java",
			"WallpaperManifest.xml",
			new String[] {"commonArtifacts"}),
	WATCHFACE(
			R.drawable.ic_comp_watchface,
			R.string.editor_menu_comp_select_watchface,
			19,
			"WatchFaceService.java",
			"MainService.java",
			"WatchFaceManifest.xml",
			new String[] {"commonArtifacts", "watchArtifacts"}),
	VR(
			R.drawable.ic_comp_vr,
			R.string.editor_menu_comp_select_vr,
			25,
			"VRActivity.java",
			"MainService.java",
			"VRManifest.xml",
			new String[] {"commonArtifacts", "vrArtifacts"}),
	PREVIEW(
			R.drawable.ic_comp_preview,
			R.string.editor_menu_comp_select_preview,
			17,
			"AppActivity.java",
			"MainActivity.java",
			"AppManifest.xml",
			new String[] {"commonArtifacts"});
	
	private final int iconId;
	private final int nameId;
	
	private final int minSdk;
	
	private final String mainClassTemplate;
	private final String mainClassName;
	private final String manifestTemplate;
	
	private final String[] assetPrefixes;
	
	ComponentTarget(int iconId, int nameId, int minSdk, String mainClassTemplate, String mainClassName, String manifestTemplate, String[] assetPrefixes) {
		this.iconId = iconId;
		this.nameId = nameId;
		this.minSdk = minSdk;
		this.mainClassTemplate = mainClassTemplate;
		this.mainClassName = mainClassName;
		this.manifestTemplate = manifestTemplate;
		this.assetPrefixes = assetPrefixes;
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
		return mainClassTemplate + ".tmpl";
	}
	
	public String getMainClassName() {
		return mainClassName;
	}
	
	public String getManifestTemplate() {
		return manifestTemplate + ".tmpl";
	}
	
	public String[] getAssetPrefixes() {
		return assetPrefixes;
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
		
		// Default to app
		// Maybe not the best thing, but fixes crashes when downgrading
		return APP;
	}
}
