package net.tedstein.AndroSS;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.Toast;

public class CameraButtonReceiver extends BroadcastReceiver {
	private static final String TAG = "AndroSS";
	private static boolean enabled = false;
	
	public static boolean isEnabled() {
		return enabled;
	}
	
	public static void enable() {
		enabled = true;
	}
	
	public static void disable() {
		enabled = false;
	}
	
	@Override
	public void onReceive(Context context, Intent intent) {
		if (CameraButtonReceiver.isEnabled() && AndroSSService.isEnabled()) {
			Log.d(TAG, "CameraButtonReceiver: Handling broadcast.");
			if (!AndroSSService.isPersistent()) {
				Intent i = new Intent(context, AndroSSService.class);
				context.stopService(i);
			}
			AndroSSService.takeScreenShot();
			
			// This will prevent the real camera app from launching.
			abortBroadcast();
			Toast.makeText(context, "Took screenshot.", Toast.LENGTH_SHORT).show();
		}		
	}
}
