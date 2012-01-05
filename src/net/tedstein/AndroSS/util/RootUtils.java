package net.tedstein.AndroSS.util;

import net.tedstein.AndroSS.AndroSSService;
import net.tedstein.AndroSS.R;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.SharedPreferences;



public class RootUtils {
    private static String[] SU_PATHS = { "/system/xbin/su", "/system/bin/su" };


    public static void showRootTestMessage(Context c) {
        final Context context = c;
        new AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.root_test_message_title))
            .setMessage(context.getString(R.string.root_test_message))
            .setNeutralButton(context.getString(R.string.root_test_message_neutral),
                              new OnClickListener() {
                                  @Override
                                  public void onClick(DialogInterface dialog, int which) {
                                      boolean have_root = false;
                                      for (String su : SU_PATHS) {
                                          AndroSSService.setSuPath(context, su);
                                          have_root = AndroSSService.canSu(context);
                                          if (have_root) break;
                                      }

                                      if (!have_root) {
                                          showRootTestFailedMessage(context);
                                      }

                                      final SharedPreferences sp =
                                          context.getSharedPreferences(Prefs.PREFS_NAME,
                                                                       Context.MODE_PRIVATE);
                                      final SharedPreferences.Editor spe = sp.edit();
                                      spe.putBoolean(Prefs.HAVE_TESTED_ROOT_KEY, true);
                                      spe.putBoolean(Prefs.HAVE_ROOT_KEY, have_root);
                                      spe.commit();
                                  }
                              }).show();
    }


    public static void showRootTestFailedMessage(Context context) {
        new AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.root_test_failed_message_title))
            .setMessage(context.getString(R.string.root_test_failed_message))
            .setNeutralButton(context.getString(R.string.root_test_failed_message_neutral), null)
            .show();
    }
}
