package com.calsignlabs.apde;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.calsignlabs.apde.build.Manifest;
import com.calsignlabs.apde.build.Permission;
import com.calsignlabs.apde.build.SketchPreviewerBuilder;

import java.util.ArrayList;
import java.util.List;

public class PermissionsActivity extends AppCompatActivity {
	private boolean[] checked;
	
	private PermissionAdapter adapter;
	
	private boolean isPreviewSetttings;
	private String startData;
	
	protected class PermissionAdapter extends BaseAdapter {
		@Override
		public int getCount() {
			return Manifest.permissions.size();
		}
		
		@Override
		public Permission getItem(int position) {
			return Manifest.permissions.get(position);
		}
		
		@Override
		public long getItemId(int i) {
			return i;
		}
		
		@Override
		public View getView(final int position, View convertView, ViewGroup parent) {
			if (convertView == null) {
				LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
				convertView = inflater.inflate(R.layout.permissions_list_item, parent, false);
			}
			
			final RelativeLayout layout = (RelativeLayout) convertView;
			final TextView label = (TextView) layout.findViewById(R.id.permissions_list_item_label);
			final CheckBox check = (CheckBox) layout.findViewById(R.id.permissions_list_item_checkbox);
			
			label.setText(Manifest.permissions.get(position).name());
			check.setChecked(checked[position]);
			
			View.OnClickListener onClickListener = new View.OnClickListener() {
				@Override
				public void onClick(View view) {
					boolean ck = !checked[position];
					
					checked[position] = ck;
					check.setChecked(ck);
				}
			};
			
			View.OnLongClickListener onLongClickListener = new View.OnLongClickListener() {
				@Override
				public boolean onLongClick(View view) {
					showPermissionDescDialog(position);
					
					return true;
				}
			};
			
			layout.setOnClickListener(onClickListener);
			label.setOnClickListener(onClickListener);
			check.setOnClickListener(onClickListener);
			
			layout.setOnLongClickListener(onLongClickListener);
			label.setOnLongClickListener(onLongClickListener);
			check.setOnLongClickListener(onLongClickListener);
			
			return convertView;
		}
	};
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_permissions);
		
		Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
		toolbar.setBackgroundColor(getResources().getColor(R.color.bar_overlay));
		setSupportActionBar(toolbar);
		
		getSupportActionBar().setHomeButtonEnabled(true);
		getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		
		getWindow().getDecorView().setBackgroundColor(getResources().getColor(R.color.activity_background));
		
		isPreviewSetttings = getIntent().getBooleanExtra("previewSettings", false);
		
		if (isPreviewSetttings) {
			getSupportActionBar().setTitle(R.string.pref_build_preview_permissions);
		}
	}
	
	@Override
	public void onResume() {
		super.onResume();
		
		final ListView permsList = (ListView) findViewById(R.id.permissions_list);
		
		permsList.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
		permsList.setItemsCanFocus(false);
		
		checked = new boolean[Manifest.permissions.size()];
		for (int i = 0; i < checked.length; i ++) {
			checked[i] = false;
		}
		
		if (isPreviewSetttings) {
			loadDataPreviewSettings();
		} else {
			loadData();
		}
		
		adapter = new PermissionAdapter();
		permsList.setAdapter(adapter);
	}
	
	//Displays a permission description dialog
	private void showPermissionDescDialog(final int perm) {
		//Inflate the layout
		LinearLayout layout = (LinearLayout) getLayoutInflater().inflate(R.layout.permission_desc_popup, null);
		
		//Populate the layout with permission values
		((TextView) layout.findViewById(R.id.permission_desc_popup_title)).setText(Manifest.permissions.get(perm).name());
		((TextView) layout.findViewById(R.id.permission_desc_popup_message)).setText(Manifest.permissions.get(perm).desc());
		
		//Create the alert
		AlertDialog.Builder build = new AlertDialog.Builder(this);
		build.setView(layout);
		
		final AlertDialog dialog = build.create();
		dialog.setCanceledOnTouchOutside(true);
		
		if(Manifest.permissions.get(perm).custom()) {
			((TextView) layout.findViewById(R.id.permission_desc_popup_message)).setVisibility(TextView.GONE); //TODO custom descriptions
			
			Button delete = (Button) layout.findViewById(R.id.permissions_desc_popup_delete);
			delete.setVisibility(Button.VISIBLE);
			delete.setOnClickListener(new Button.OnClickListener() {
				@Override
				public void onClick(View view) {
					AlertDialog.Builder builder = new AlertDialog.Builder(PermissionsActivity.this);
					
					builder.setTitle(R.string.permissions_delete_permission_dialog_title);
					builder.setMessage(R.string.permissions_delete_permission_dialog_message);
					
					builder.setPositiveButton(R.string.delete, new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialogInterface, int which) {
							String data = getData();
							
							Manifest.removeCustomPermission(perm, getApplicationContext());
							adapter.notifyDataSetChanged();
							
							dialog.dismiss();
							
							setData(data);
						}
					});
					
					builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {}
					});
					
					builder.create().show();
			}});
		}
		
		dialog.show();
	}
	
	@Override
	public void onPause() {
		if (!isPreviewSetttings) {
			saveData();
		}
		
		super.onPause();
	}
	
	@Override
	public void onBackPressed() {
		if (isPreviewSetttings) {
			promptSaveDataPreviewSettings();
		} else {
			super.onBackPressed();
		}
	}
	
	private SharedPreferences getSharedPreferences() {
		return getSharedPreferences(((APDE) getApplicationContext()).getSketchName(), MODE_PRIVATE);
	}
	
	public void saveData() {
		getSharedPreferences().edit().putString("permissions", getData()).apply();
	}
	
	public void loadData() {
		setData(getSharedPreferences().getString("permissions", ""));
	}
	
	public void loadDataPreviewSettings() {
		setData(SketchPreviewerBuilder.getInstalledPermissions(this));
		startData = getData();
	}
	
	public void promptSaveDataPreviewSettings() {
		if (startData.equals(getData())) {
			// If the permissions haven't changed, then don't worry about it
			finish();
			return;
		}
		
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		
		builder.setTitle(R.string.pref_build_preview_permissions_reinstall_dialog_title);
		builder.setMessage(R.string.pref_build_preview_permissions_reinstall_dialog_message);
		
		builder.setPositiveButton(R.string.pref_build_preview_permissions_reinstall_dialog_button_install, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialogInterface, int i) {
				previewReinstall();
			}
		});
		
		builder.setNegativeButton(R.string.pref_build_preview_permissions_reinstall_dialog_button_exit, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialogInterface, int i) {
				finish();
			}
		});
		
		builder.setNeutralButton(R.string.cancel, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialogInterface, int i) {}
		});
		
		builder.show();
	}
	
	protected void previewReinstall() {
		Intent result = new Intent();
		result.putExtra("permissions", getDataArray());
		setResult(RESULT_OK, result);
		finish();
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.activity_permissions, menu);
		return true;
	}
	
	@Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case android.R.id.home:
            	if (isPreviewSetttings) {
            		promptSaveDataPreviewSettings();
				} else {
					finish();
				}
                return true;
            case R.id.menu_new_permission:
            	newPermission();
            	return true;
            case R.id.action_settings:
            	launchSettings();
            	return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
	
	private void launchSettings() {
		startActivity(new Intent(this, SettingsActivity.class));
	}
	
	public void newPermission() {
		//Create the alert
		AlertDialog.Builder build = new AlertDialog.Builder(this);
		build.setTitle(R.string.permissions_new_permission_dialog_title);
		build.setMessage(R.string.permissions_new_permission_dialog_message);
		
		APDE global = (APDE) getApplicationContext();
		
		final EditText input = global.createAlertDialogEditText(this, build, "", false);
		
		build.setPositiveButton(R.string.create, new android.content.DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) { //TODO let the user customize the prefix and the description
				String data = getData();
				
				Manifest.addCustomPermission(input.getText().toString(), getResources().getString(R.string.permissions_custom_perm), getApplicationContext());
				Manifest.sortPermissions();
				adapter.notifyDataSetChanged();
				
				setData(data);
		}});
		build.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton) {
			}
		});
		
		AlertDialog alert = build.create();
		if (!PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getBoolean("use_hardware_keyboard", false)
				&& alert.getWindow() != null) {
			alert.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
		}
		alert.show();
	}
	
	public void setData(String data) {
		clearChecks();
		
		if (data.length() <= 0) {
			return;
		}
		
		setData(data.split(","));
	}
	
	public void setData(String[] data) {
		for (String permission : data) {
			int index = permission.lastIndexOf(".");
			checkItem(permission.substring(index != -1 ? index + 1 : 0), true);
		}
	}
	
	/**
	 * @return the selected values, separated by commas
	 */
	public String getData() {
		// Combine all values
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < checked.length; i ++) {
			if (checked[i]) {
				out.append((Manifest.permissions.get(i)).consumableValue());
				out.append(",");
			}
		}
		
		return out.toString();
	}
	
	public String[] getDataArray() {
		List<String> permissions = new ArrayList<>();
		for (int i = 0; i < checked.length; i ++) {
			if (checked[i]) {
				permissions.add((Manifest.permissions.get(i)).consumableValue());
			}
		}
		String[] out = new String[permissions.size()];
		for (int i = 0; i < out.length; i ++) {
			out[i] = permissions.get(i);
		}
		return out;
	}
	
	public void clearChecks() {
		ListView permsList = (ListView) findViewById(R.id.permissions_list);
		permsList.clearChoices();
		permsList.requestLayout();
		
		for (int i = 0; i < checked.length; i ++) {
			checked[i] = false;
		}
	}
	
	public void checkItem(String value, boolean ck) {
		int index = permissionsPos(value);
		
		//We have a problem
		if (index == -1) {
			return;
		}
		
		checked[index] = ck;
		
		ListView permsList = (ListView) findViewById(R.id.permissions_list);
		permsList.setItemChecked(index, ck);
	}
	
	//Get the location of the permission in the list
	private int permissionsPos(String name) {
		for (int i = 0; i < Manifest.permissions.size(); i ++) {
			if (((Manifest.permissions.get(i)).name()).equals(name)) {
				return i;
			}
		}
		
		return -1;
	}
}