package com.calsignlabs.apde;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import com.calsignlabs.apde.task.Task;

public class LicensesActivity extends AppCompatActivity {
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_licenses);
		
		Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
		toolbar.setBackgroundColor(getResources().getColor(R.color.bar_overlay));
		setSupportActionBar(toolbar);
		
		getSupportActionBar().setHomeButtonEnabled(true);
		getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		
		getWindow().getDecorView().setBackgroundColor(getResources().getColor(R.color.activity_background));
		
		final TextView content = (TextView) findViewById(R.id.about_licenses_text);
		
		((APDE) getApplication()).getTaskManager().launchTask("loadLicenses", false, this, true, new Task() {
			@Override
			public void run() {
				final String text = APDE.readAssetFile(LicensesActivity.this, "licenses.txt");
				
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						content.setText(text);
					}
				});
			}
			
			@Override
			public CharSequence getTitle() {
				return getResources().getString(R.string.pref_about_licenses);
			}
		});
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		//Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.menu_licenses, menu);
		return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case android.R.id.home:
			finish();
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}
}
