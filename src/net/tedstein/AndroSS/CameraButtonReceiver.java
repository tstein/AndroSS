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
	public static void setEnabled(boolean isChecked) {
		enabled = isChecked;
	}

	@Override
	public void onReceive(Context context, Intent intent) {
		if (CameraButtonReceiver.isEnabled() && AndroSSService.isEnabled()) {
			Log.d(TAG, "CameraButtonReceiver: Handling broadcast.");
			Intent i = new Intent(context, AndroSSService.class);
			i.putExtra("TAKE_SCREENSHOT", true);
			context.startService(i);
			if (!AndroSSService.isPersistent()) {
				context.stopService(i);
			}

			// This will prevent the real camera app from launching.
			abortBroadcast();
			Toast.makeText(context, "Took screenshot.", Toast.LENGTH_SHORT).show();
		}
	}
}
