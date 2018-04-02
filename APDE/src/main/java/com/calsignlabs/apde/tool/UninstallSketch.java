package com.calsignlabs.apde.tool;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.support.v7.app.AlertDialog;
import android.view.MenuItem;

import com.calsignlabs.apde.APDE;
import com.calsignlabs.apde.KeyBinding;
import com.calsignlabs.apde.R;

import java.util.List;
import java.util.Locale;

public class UninstallSketch implements Tool {
	public static final String PACKAGE_NAME = "com.calsignlabs.apde.tool.UninstallSketch";
	
	private APDE context;
	
	@Override
	public void init(APDE context) {
		this.context = context;
	}
	
	@Override
	public String getMenuTitle() {
		return context.getResources().getString(R.string.tool_uninstall_sketch);
	}
	
	@Override
	public void run() {
		String sketchName = context.getSketchName();
		
		if (isSketchInstalled(sketchName)) {
			Intent uninstallIntent = new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + getPackageName(sketchName)));
			context.getEditor().startActivity(uninstallIntent);
		} else {
			AlertDialog.Builder builder = new AlertDialog.Builder(context.getEditor());
			builder.setTitle(R.string.tool_uninstall_sketch_error_not_installed_dialog_title);
			builder.setMessage(R.string.tool_uninstall_sketch_error_not_installed_dialog_message);
			builder.setNeutralButton(R.string.ok, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {}
			});
			
			builder.create().show();
		}
	}
	
	@Override
	public KeyBinding getKeyBinding() {
		return null;
	}
	
	@Override
	public boolean showInToolsMenu(APDE.SketchLocation sketchLocation) {
		return true;
	}
	
	@Override
	public boolean createSelectionActionModeMenuItem(MenuItem convert) {
		return false;
	}
	
	private boolean isSketchInstalled(String sketchName) {
		List<PackageInfo> installedPackages = context.getPackageManager().getInstalledPackages(0);
		String packageName = getPackageName(sketchName);
		
		for (PackageInfo packageInfo : installedPackages) {
			if (packageInfo.packageName.equals(packageName)) {
				return true;
			}
		}
		
		return false;
	}
	
	private String getPackageName(String sketchName) {
		return "processing.test." + sketchName.toLowerCase(Locale.US);
	}
}
