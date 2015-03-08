package com.calsignlabs.apde;

import android.content.Context;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import com.calsignlabs.apde.task.Task;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class LicensesActivity extends ActionBarActivity {
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_licenses);
		
		getWindow().getDecorView().setBackgroundColor(getResources().getColor(R.color.activity_background));
		
		final TextView content = (TextView) findViewById(R.id.about_licenses_text);
		
		((APDE) getApplication()).getTaskManager().launchTask("loadLicenses", false, this, true, new Task() {
			@Override
			public void run() {
				final String text = readAssetFile(LicensesActivity.this, "licenses.txt");
				
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
	
	private static String readAssetFile(Context context, String filename) {
		try {
			InputStream assetStream = context.getAssets().open(filename);
			BufferedInputStream stream = new BufferedInputStream(assetStream);
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			
			byte[] buffer = new byte[1024];
			int numRead;
			while ((numRead = stream.read(buffer)) != -1) {
				out.write(buffer, 0, numRead);
			}
			
			stream.close();
			assetStream.close();
			
			String text = new String(out.toByteArray());
			
			out.close();
			
			return text;
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return "";
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
