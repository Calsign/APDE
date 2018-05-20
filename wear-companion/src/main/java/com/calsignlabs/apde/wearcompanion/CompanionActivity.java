package com.calsignlabs.apde.wearcompanion;

import android.content.Intent;
import android.os.Bundle;
import android.support.wearable.activity.WearableActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class CompanionActivity extends WearableActivity {
	private Button stopButton;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_companion);
		
		stopButton = (Button) findViewById(R.id.button_watchface_loader_stop);
		
		if (startWatchfaceLoader()) {
			Toast.makeText(this, R.string.message_watchface_loader_started, Toast.LENGTH_LONG).show();
		}
		
		stopButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				if (stopWatchfaceLoader()) {
					Toast.makeText(CompanionActivity.this, R.string.message_watchface_loader_stopped, Toast.LENGTH_LONG).show();
				}
			}
		});
	}
	
	protected boolean startWatchfaceLoader() {
		Intent intent = new Intent(this, WatchfaceLoader.class);
		return startService(intent) != null;
	}
	
	protected boolean stopWatchfaceLoader() {
		Intent intent = new Intent(this, WatchfaceLoader.class);
		return stopService(intent);
	}
}
