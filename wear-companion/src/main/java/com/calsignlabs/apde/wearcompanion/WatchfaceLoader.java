package com.calsignlabs.apde.wearcompanion;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.support.v4.content.FileProvider;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.tasks.Tasks;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutionException;

/**
 * Service to install watchfaces on the watch. Receives APKs over the Bluetooth transport.
 */
public class WatchfaceLoader extends WearableListenerService {
	public WatchfaceLoader() {}
	
	@Override
	public void onDataChanged(DataEventBuffer dataEvents) {
		// Use only the most recent asset
		// I don't think we will ever get more than one at once,
		// but there's no sense in installing an old version of the APK
		
		Asset asset = null;
		
		for (DataEvent dataEvent : dataEvents) {
			if (dataEvent.getType() == DataEvent.TYPE_CHANGED) {
				DataMapItem dataMapItem = DataMapItem.fromDataItem(dataEvent.getDataItem());
				asset = dataMapItem.getDataMap().getAsset("apk");
			}
		}
		
		Toast.makeText(this, R.string.message_watchface_loader_receiving_apk, Toast.LENGTH_LONG).show();
		Log.d("apde", "receiving apk");
		
		// Make apk file in internal storage
		File apkFile = new File(getFilesDir(), "sketch.apk");
		
		if (asset != null && makeFileFromAsset(asset, apkFile)) {
			Toast.makeText(this, R.string.message_watchface_loader_installing_apk, Toast.LENGTH_SHORT).show();
			Log.d("apde", "installing apk");
			
			installApk(apkFile);
		}
	}
	
	protected boolean makeFileFromAsset(Asset asset, File destFile) {
		try {
			InputStream inputStream = Tasks.await(Wearable.getDataClient(this).getFdForAsset(asset)).getInputStream();
			if (inputStream != null) {
				createFileFromInputStream(inputStream, destFile);
				return true;
			}
		} catch (ExecutionException | InterruptedException | IOException e) {
			e.printStackTrace();
		}
		
		return false;
	}
	
	@SuppressLint("SetWorldReadable")
	protected void installApk(File apkFile) {
		Intent promptInstall;
		
		if (android.os.Build.VERSION.SDK_INT >= 24) {
			// Need to use FileProvider
			Uri apkUri = FileProvider.getUriForFile(this, "com.calsignlabs.apde.wearcompanion.fileprovider", apkFile);
			promptInstall = new Intent(Intent.ACTION_INSTALL_PACKAGE).setData(apkUri).setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
		} else {
			// The package manager doesn't seem to like FileProvider...
			promptInstall = new Intent(Intent.ACTION_VIEW).setDataAndType(Uri.fromFile(apkFile), "application/vnd.android.package-archive");
			
			// Make world-readable
			// I know this is bad but it didn't work any other way on phones
			// and I don't have a watch with API < 24 to test on
			if (!apkFile.setReadable(true, false)) {
				System.out.println("Failed to make APK readable");
			}
		}
		
		promptInstall.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		
		startActivity(promptInstall);
	}
	
	// http://stackoverflow.com/questions/11820142/how-to-pass-a-file-path-which-is-in-assets-folder-to-filestring-path
	private static void createFileFromInputStream(InputStream inputStream, File destFile)  throws IOException {
		// Make sure that the parent folder exists
		destFile.getParentFile().mkdirs();
		
		FileOutputStream outputStream = new FileOutputStream(destFile);
		byte buffer[] = new byte[1024];
		int length = 0;
		
		while((length = inputStream.read(buffer)) > 0) {
			outputStream.write(buffer, 0, length);
		}
		
		outputStream.close();
		inputStream.close();
	}
}
