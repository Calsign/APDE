package com.calsignlabs.apde.wearcompanion.watchface;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import android.util.Log;

import com.google.android.gms.tasks.Tasks;
import com.google.android.gms.wearable.CapabilityClient;
import com.google.android.gms.wearable.CapabilityInfo;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.Wearable;

import org.json.JSONObject;

import java.io.OutputStream;

/*
 * Note: This file should be kept in sync with APDE/src/assets/log-broadcaster/APDEInternalLogBroadcasterUtil.java.
 */
public class LoggingUtil {
	private static Handler handler = new Handler(Looper.getMainLooper());
	
	public static class ExceptionHandler implements Thread.UncaughtExceptionHandler {
		private Thread.UncaughtExceptionHandler defaultHandler;
		
		private Context context;
		
		public ExceptionHandler(Context context) {
			defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
			
			this.context = context;
		}
		
		public void uncaughtException(Thread t, Throwable e) {
			Log.d("apde_watchface_sketch", e.toString());
			broadcastMessage(e.getMessage(), 'x', e.getClass().getName(), context);
			// Don't pass through - we don't want to kill the service
			// If we kill the service then we can't send the message back to APDE
//			defaultHandler.uncaughtException(t, e);
		}
	}
	
	public static class ConsoleStream extends OutputStream {
		private final byte single[] = new byte[1];
		private final char severity;
		
		private Context context;
		
		public ConsoleStream(char severity, Context context) {
			this.severity = severity;
			
			this.context = context;
		}
		
		@Override
		public void close() {}
		@Override
		public void flush() {}
		
		@Override
		public void write(byte b[]) {
			write(b, 0, b.length);
		}
		
		@Override
		public void write(byte b[], int offset, int length) {
			broadcastMessage(new String(b, offset, length), severity, "", context);
		}
		
		@Override
		public void write(int b) {
			single[0] = (byte) b;
			write(single, 0, 1);
		}
	}
	
	protected static String bestNodeId = null;
	
	public static void initMessageBroadcaster(final Context context) {
		Wearable.getCapabilityClient(context).addListener(new CapabilityClient.OnCapabilityChangedListener() {
			@Override
			public void onCapabilityChanged(@NonNull CapabilityInfo capabilityInfo) {
				updateBestNode(context);
			}
		}, "apde_receive_logs");
		
		// Can't do this on the main thread
		(new Thread(new Runnable() {
			@Override
			public void run() {
				updateBestNode(context);
			}
		})).start();
	}
	
	private static void updateBestNode(Context context) {
		bestNodeId = null;
		
		try {
			CapabilityInfo info = Tasks.await(Wearable.getCapabilityClient(context).getCapability("apde_receive_logs", CapabilityClient.FILTER_REACHABLE));
			for (Node node : info.getNodes()) {
				if (node.isNearby()) {
					bestNodeId = node.getId();
				}
			}
		} catch (Exception e) {
			// Don't call printStackTrace() because that would make an infinite loop
			Log.e("apde", e.toString());
		}
	}
	
	public static void broadcastMessage(final String message, final char severity, final String exception, final Context context) {
		handler.post(new Runnable() {
			public void run() {
				try {
					JSONObject json = new JSONObject();
					json.put("severity", Character.toString(severity));
					json.put("message", message);
					json.put("exception", exception);
					byte[] data = json.toString().getBytes();
					
					if (bestNodeId != null) {
						Wearable.getMessageClient(context).sendMessage(bestNodeId, "/apde_receive_logs", data);
					}
				} catch (Exception e) {
					// Don't call printStackTrace() because that would make an infinite loop
					Log.e("apde", e.toString());
				}
			}
		});
	}
}
