package net.tedstein.AndroSS;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class CameraButtonReceiver extends BroadcastReceiver {
    private static final String TAG = "AndroSS";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (AndroSSService.isCameraButtonEnabled() && AndroSSService.isEnabled()) {
            Log.d(TAG, "CameraButtonReceiver: Handling broadcast.");
            Intent i = new Intent(context, AndroSSService.class);
            i.putExtra("TAKE_SCREENSHOT", true);
            context.startService(i);

            // This will prevent the real camera app from launching.
            abortBroadcast();
        }
    }
}
