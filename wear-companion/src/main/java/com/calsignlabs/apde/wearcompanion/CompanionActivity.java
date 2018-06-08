package com.calsignlabs.apde.wearcompanion;

import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.wearable.activity.WearableActivity;
import android.util.Log;
import android.widget.TextView;

public class CompanionActivity extends WearableActivity {
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_companion);
		
		// Display version of the wear companion
		String versionName = "";
		try {
			versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
		} catch (PackageManager.NameNotFoundException e) {
			// Oops
			Log.d("apde", "failed to load version name, that's strange");
		}
		((TextView) findViewById(R.id.wear_compnanion_activity_version)).setText(versionName);
	}
}
