package com.calsignlabs.apde.build;

import android.content.Context;
import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.CapabilityClient;
import com.google.android.gms.wearable.CapabilityInfo;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import java.io.File;
import java.util.concurrent.ExecutionException;

public class WearableUtil {
	public static interface ResultCallback {
		void success();
		void failure();
	}
	
	public static void sendApkToWatch(Context context, File apkFile, final ResultCallback callback) {
		Uri apkUri = FileProvider.getUriForFile(context, "com.calsignlabs.apde.fileprovider", apkFile);
		Asset asset = Asset.createFromUri(apkUri);
		PutDataMapRequest dataMap = PutDataMapRequest.create("/apk");
		dataMap.getDataMap().putAsset("apk", asset);
		dataMap.getDataMap().putLong("timestamp", System.currentTimeMillis());
		PutDataRequest request = dataMap.asPutDataRequest();
		request.setUrgent();
		
		Task<DataItem> putTask = Wearable.getDataClient(context).putDataItem(request);
		putTask.addOnCompleteListener(new OnCompleteListener<DataItem>() {
			@Override
			public void onComplete(@NonNull Task<DataItem> task) {
				if (task.isSuccessful()) {
					callback.success();
				} else {
					callback.failure();
				}
			}
		});
	}
	
	public static void checkWatchAvailable(final Context context, final ResultCallback callback) {
		(new Thread(new Runnable() {
			@Override
			public void run() {
				boolean successful = false;
				
				try {
					CapabilityInfo info = Tasks.await(Wearable.getCapabilityClient(context).getCapability("apde_run_watch_sketches", CapabilityClient.FILTER_REACHABLE));
					
					for (Node node : info.getNodes()) {
						if (node.isNearby()) {
							successful = true;
							break;
						}
					}
				} catch (ExecutionException | InterruptedException e) {
					e.printStackTrace();
				}
				
				if (successful) {
					callback.success();
				} else {
					callback.failure();
				}
			}
		})).start();
	}
}
