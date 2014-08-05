package com.calsignlabs.apde;

import com.calsignlabs.apde.build.Manifest;
import com.calsignlabs.apde.build.Permission;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBarActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckedTextView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

public class PermissionsActivity extends ActionBarActivity {
	private boolean[] checked;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_permissions);
		
		getSupportActionBar().setHomeButtonEnabled(true);
		getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		
		getWindow().getDecorView().setBackgroundColor(getResources().getColor(R.color.activity_background));
	}
	
	@Override
	public void onResume() {
		super.onResume();
		
		final ListView permsList = (ListView) findViewById(R.id.permissions_list);
		
		permsList.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
		permsList.setItemsCanFocus(false);
		
		ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, R.layout.permissions_list_item);
		
		for(Permission perm : Manifest.permissions)
			adapter.add(perm.name());
		
		permsList.setAdapter(adapter);
		
		checked = new boolean[permsList.getCount()];
		
		//is this necessary?
		for(int i = 0; i < checked.length; i ++)
			checked[i] = false;
		
		permsList.setOnItemClickListener(new android.widget.ListView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> adapt, View view, int pos, long id) {
				CheckedTextView check = ((CheckedTextView) view);
				
				boolean ck = !checked[pos];
				
				checked[pos] = ck;
				
				//Dispatch the touch event to the child... this is awfully hacky
				MotionEvent me = MotionEvent.obtain(0, 0, 0, 0, 0, 0);
				check.onTouchEvent(me);
				me.recycle();
		}});
		
		permsList.setOnItemLongClickListener (new android.widget.AdapterView.OnItemLongClickListener() {
			public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
				showPermissionDescDialog(position);
				return true;
			}
		});
		
		loadData();
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
					
					builder.setTitle(R.string.delete_perm_dialog_title);
					builder.setMessage(R.string.delete_perm_dialog_message);
					
					builder.setPositiveButton(R.string.delete, new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialogInterface, int which) {
							saveData();
							
							Manifest.removeCustomPermission(perm, getApplicationContext());
							refreshPermissions();
							
							dialog.dismiss();
							
							loadData();
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
		saveData();
		
		super.onPause();
	}
	
	public void saveData() {
		SharedPreferences prefs = getSharedPreferences(((APDE) getApplicationContext()).getSketchName(), MODE_PRIVATE);
		SharedPreferences.Editor editor = prefs.edit();
		
		editor.putString("permissions", getData());
		
		editor.commit();
	}
	
	public void loadData() {
		SharedPreferences prefs = getSharedPreferences(((APDE) getApplicationContext()).getSketchName(), MODE_PRIVATE);
		setData(prefs.getString("permissions", ""));
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
            	finish();
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
		if(android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.HONEYCOMB)
			startActivity(new Intent(this, SettingsActivity.class));
		else
			startActivity(new Intent(this, SettingsActivityHC.class));
	}
	
	public void newPermission() {
		//Create the alert
		AlertDialog.Builder build = new AlertDialog.Builder(this);
		build.setTitle(R.string.new_permission_dialog_title);
		build.setMessage(R.string.new_permission_dialog_message);
		
		final EditText input = new EditText(this);
    	input.setSingleLine();
    	build.setView(input);
		
		build.setPositiveButton(R.string.create, new android.content.DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) { //TODO let the user customize the prefix and the description
				saveData();
				
				Manifest.addCustomPermission(input.getText().toString(), getResources().getString(R.string.custom_perm), getApplicationContext());
				Manifest.sortPermissions();
				refreshPermissions();
				
				loadData();
		}});
		build.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
    		public void onClick(DialogInterface dialog, int whichButton) {
    	}});
		
		AlertDialog alert = build.create();
		if(!PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getBoolean("use_hardware_keyboard", false))
			alert.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
		alert.show();
	}
	
	//Reloads the list of permissions
	public void refreshPermissions() {
		@SuppressWarnings("unchecked")
		ArrayAdapter<String> adapter = (ArrayAdapter<String>) ((ListView) findViewById(R.id.permissions_list)).getAdapter();
		adapter.clear();
		
		for(Permission perm : Manifest.permissions)
			adapter.add(perm.name());
	}
	
	public void setData(String data) {
		clearChecks();
		
		if(data.length() <= 0)
			return;
		
		String[] tokens = data.split(",");
		for(String token : tokens) {
			int index = token.lastIndexOf(".");
			checkItem(token.substring(index != -1 ? index + 1 : 0), true);
		}
	}
	
	/**
	 * @return the selected values, separated by commas
	 */
	public String getData() {
		//Combine all values
		String out = "";
		for(int i = 0; i < checked.length; i ++)
			if(checked[i])
				out += (Manifest.permissions.get(i)).consumableValue() + ",";
		
		return out;
	}
	
	public void clearChecks() {
		ListView permsList = (ListView) findViewById(R.id.permissions_list);
		permsList.clearChoices();
		permsList.requestLayout();
		
		checked = new boolean[permsList.getCount()];
	}
	
	public void checkItem(String value, boolean ck) {
		int index = permissionsPos(value);
		
		//We have a problem
		if(index == -1)
			return;
		
		checked[index] = ck;
		
		ListView permsList = (ListView) findViewById(R.id.permissions_list);
		permsList.setItemChecked(index, ck);
	}
	
	//Get the location of the permission in the list
	private int permissionsPos(String name) {
		for(int i = 0; i < Manifest.permissions.size(); i ++)
			if(((Manifest.permissions.get(i)).name()).equals(name))
				return i;
		
		return -1;
	}
}