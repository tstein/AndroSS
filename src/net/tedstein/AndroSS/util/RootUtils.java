package net.tedstein.AndroSS.util;

import net.tedstein.AndroSS.AndroSSService;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.SharedPreferences;

public class RootUtils {
    public static void showRootTestMessage(Context c) {
        final Context context = c;
        new AlertDialog.Builder(context)
        .setTitle("Checking for root")
        .setMessage("AndroSS needs root to work, so let's see if you're " +
                    "set up properly. This is just a quick test and your su " +
                    "whitelister may distinguish between this and our normal " +
                    "operation, so no need to whitelist us right now.")
        .setNeutralButton("Let's do this!", new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                boolean have_root = AndroSSService.canSu(context);
                if (!have_root) {
                    showRootTestFailedMessage(context);
                }

                final SharedPreferences sp = context.getSharedPreferences(Prefs.PREFS_NAME,
                        Context.MODE_PRIVATE);
                final SharedPreferences.Editor spe = sp.edit();
                spe.putBoolean(Prefs.HAVE_TESTED_ROOT_KEY, true);
                spe.putBoolean(Prefs.HAVE_ROOT_KEY, have_root);
                spe.commit();
            }
        })
        .show();
    }

    public static void showRootTestFailedMessage(Context context) {
        new AlertDialog.Builder(context)
        .setTitle("Root test failed")
        .setMessage("Something went wrong when trying to use su. AndroSS " +
                    "only works correctly on rooted devices. If you think " +
                    "this should have worked and you have a few minutes, " +
                    "feel free to contact me through any method listed in " +
                    "this app's Market page.")
        .setNeutralButton("Darn! :(", null)
        .show();
    }
}
