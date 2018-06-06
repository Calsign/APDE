package com.calsignlabs.apde.wearcompanion;

import android.content.Intent;
import android.os.Bundle;
import android.support.wearable.activity.WearableActivity;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

public class CompanionActivity extends WearableActivity {
	private Button watchfaceLoaderStartStopButton;
	private boolean watchfaceLoaderRunning = false;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_companion);
		
		watchfaceLoaderStartStopButton = (Button) findViewById(R.id.button_watchface_loader_stop);
		
		// Start service in background
		(new Thread(new Runnable() {
			@Override
			public void run() {
				if (startWatchfaceLoader()) {
					// Show toast on main thread
					runOnUiThread(new Runnable() {
						@Override
						public void run() {
							Toast.makeText(CompanionActivity.this, R.string.message_watchface_loader_started, Toast.LENGTH_LONG).show();
							watchfaceLoaderRunning = true;
						}
					});
				}
			}
		})).start();
		
		// Note: For some reason, it seems that the service isn't actually getting stopped.
		// Don't know why. Perhaps we should disable the "stop" button...
		
		watchfaceLoaderStartStopButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				if (watchfaceLoaderRunning) {
					if (stopWatchfaceLoader()) {
						Toast.makeText(CompanionActivity.this, R.string.message_watchface_loader_stopped, Toast.LENGTH_LONG).show();
						watchfaceLoaderRunning = false;
						watchfaceLoaderStartStopButton.setText(R.string.button_watchface_loader_start);
					}
				} else {
					if (startWatchfaceLoader()) {
						Toast.makeText(CompanionActivity.this, R.string.message_watchface_loader_started, Toast.LENGTH_LONG).show();
						watchfaceLoaderRunning = true;
						watchfaceLoaderStartStopButton.setText(R.string.button_watchface_loader_stop);
					}
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
