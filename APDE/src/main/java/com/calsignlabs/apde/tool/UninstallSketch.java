package com.calsignlabs.apde.tool;

import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import androidx.appcompat.app.AlertDialog;
import android.view.MenuItem;

import com.calsignlabs.apde.APDE;
import com.calsignlabs.apde.KeyBinding;
import com.calsignlabs.apde.R;

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
		
		try {
			Intent uninstallIntent = new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + getPackageName(sketchName)));
			context.getEditor().startActivity(uninstallIntent);
		} catch (ActivityNotFoundException e) {
			showUninstallError();
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
	
	private void showUninstallError() {
		AlertDialog.Builder builder = new AlertDialog.Builder(context.getEditor());
		builder.setTitle(R.string.tool_uninstall_sketch_error_not_installed_dialog_title);
		builder.setMessage(R.string.tool_uninstall_sketch_error_not_installed_dialog_message);
		builder.setNeutralButton(R.string.ok, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {}
		});
		
		builder.create().show();
	}
	
	private String getPackageName(String sketchName) {
		return "processing.test." + sketchName.toLowerCase(Locale.US);
	}
}
