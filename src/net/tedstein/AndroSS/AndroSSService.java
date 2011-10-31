package net.tedstein.AndroSS;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Calendar;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.tedstein.AndroSS.util.Prefs;
import android.app.Service;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.IBinder;
import android.os.Vibrator;
import android.provider.MediaStore.Images;
import android.util.Base64;
import android.util.Log;
import android.view.Surface;
import android.view.WindowManager;
import android.widget.Toast;

public class AndroSSService extends Service implements SensorEventListener {
    static {
        System.loadLibrary("AndroSS_nbridge");
    }

    public static enum CompressionType {PNG, JPG_HQ, JPG_FAST};
    public static enum DeviceType { UNKNOWN, GENERIC, TEGRA_2 };

    private static final String TAG = "AndroSS";
    private static final String DEFAULT_OUTPUT_DIR = "/sdcard/screenshots";
    private static final String DEFAULT_SU_PATH = "/system/xbin/su";
    private static final float ACCEL_THRESH = 7.0F;
    private static final long IGNORE_SHAKE_INTERVAL = 1000 * 1000 * 1000;
    private static SensorManager sm;
    // Phone graphical parameters and fixed config.
    private static int screen_width;
    private static int screen_height;
    private static int screen_depth;
    private static int fb_stride;
    private static int[] c_offsets;
    private static int[] c_sizes;
    private static String files_dir;
    private static DeviceType dev_type = DeviceType.UNKNOWN;
    private static String opengl_vendor = "unknown";
    // Static info about Tegra 2 devices.
    private static final String fbread_path = "/system/bin/fbread";
    // Service state.
    private static boolean initialized = false;
    private static String output_dir = DEFAULT_OUTPUT_DIR;
    private static String command;
    private static String su_path = DEFAULT_SU_PATH;
    private static long last_shake_event = 0;
    private static float old_x = 0;
    private static float old_y = 0;
    private static float old_z = 0;
    private static SharedPreferences sp = null;
    private static SharedPreferences.Editor spe = null;



    // Native function signatures.
    private static native String getFBInfo(int type, String command);
    private static native int[] getFBPixels(int type, String command,
            int pixels, int bpp,
            int[] offsets, int[] sizes);


    // Public static functions.
    public static boolean isEnabled() {
        if (!initialized) {
            return false;
        } else {
            return sp.getBoolean(Prefs.ENABLED_KEY, false);
        }
    }
    public static void setEnabled(boolean enable) {
        spe.putBoolean(Prefs.ENABLED_KEY, enable);
        spe.commit();
    }

    public static boolean isPersistent() {
        if (!initialized) {
            return false;
        } else {
            return sp.getBoolean(Prefs.PERSISTENT_KEY, false);
        }
    }
    public static void setPersistent(boolean enable) {
        spe.putBoolean(Prefs.PERSISTENT_KEY, enable);
        spe.commit();
    }

    public static boolean isShakeEnabled() {
        if (!initialized) {
            return false;
        } else {
            return sp.getBoolean(Prefs.SHAKE_TRIGGER_KEY, false);
        }
    }
    public static void setShake(boolean enable) {
        if (enable) {
            spe.putBoolean(Prefs.SHAKE_TRIGGER_KEY, true);
        } else {
            old_x = 0;
            old_y = 0;
            old_z = 0;
            spe.putBoolean(Prefs.SHAKE_TRIGGER_KEY, false);
        }
        spe.commit();
    }

    public static boolean isCameraButtonEnabled() {
        if (!initialized) {
            return false;
        } else {
            return sp.getBoolean(Prefs.CAMERA_TRIGGER_KEY, false);
        }
    }
    public static void setCameraButton(boolean enable) {
        spe.putBoolean(Prefs.CAMERA_TRIGGER_KEY, enable);
        spe.commit();
    }

    public static CompressionType getCompressionType() {
        if (!initialized) {
            return CompressionType.PNG;
        } else {
            return CompressionType.valueOf(
                    sp.getString(Prefs.COMPRESSION_KEY,
                            CompressionType.PNG.name()));
        }
    }

    public static void setCompressionType(CompressionType ct) {
        spe.putString(Prefs.COMPRESSION_KEY, ct.name());
        spe.commit();
    }

    public static String getOutputDir(Context context) {
        if (AndroSSService.initialized) {
            return AndroSSService.output_dir;
        } else {
            initSharedPreferences(context);
            return sp.getString(Prefs.OUTPUT_DIR_KEY, AndroSSService.DEFAULT_OUTPUT_DIR);
        }
    }

    public static boolean setOutputDir(Context context, String new_dir) {
        if (!new_dir.endsWith("/")) {
            new_dir += "/";
        }

        File f = new File(new_dir);
        f.mkdirs();
        if (f.canWrite()) {
            initSharedPreferences(context);
            spe.putString(Prefs.OUTPUT_DIR_KEY, new_dir);
            spe.commit();

            AndroSSService.output_dir = new_dir;
            Log.d(TAG, "Service: Updated output dir to: " + new_dir);
            return true;
        } else {
            Log.d(TAG, "Service: Cannot write to requested output dir: " + new_dir);
            return false;
        }
    }

    public static String getSuPath(Context context) {
        if (AndroSSService.initialized) {
            return AndroSSService.su_path;
        } else {
            initSharedPreferences(context);
            return sp.getString(Prefs.SU_PATH_KEY, AndroSSService.DEFAULT_SU_PATH);
        }
    }

    public static boolean setSuPath(Context context, String new_su) {
        File f = new File(new_su);
        if (f.canRead()) {
            initSharedPreferences(context);
            spe.putString(Prefs.SU_PATH_KEY, new_su);
            spe.commit();

            AndroSSService.su_path = new_su;
            updateCommand();
            Log.d(TAG, "Service: Updated su path to: " + new_su);
            return true;
        } else {
            Log.d(TAG, "Service: su path appears invalid: " + new_su);
            return false;
        }
    }

    public static boolean getRotationEnabled() {
        return sp.getBoolean(Prefs.ROTATION_KEY, true);
    }

    public static void setRotationEnabled(boolean rotate) {
        spe.putBoolean(Prefs.ROTATION_KEY, rotate);
        spe.commit();
    }

    public static String getParamString() {
        if (AndroSSService.initialized) {
            return getFBInfo(getDeviceType().ordinal(), command);
        } else {
            return "";
        }
    }

    public static boolean canSu(Context context) {
        createExternalBinary(context);
        int ret = 1;
        try {
            ret =
                Runtime.getRuntime()
                    .exec(su_path + " -c " + context.getFilesDir().getAbsolutePath() + "/AndroSS")
                    .waitFor();
        } catch (InterruptedException ie) {
        } catch (IOException e) {}
        return ret == 0 ? true : false;
    }

    public static DeviceType getDeviceType() {
        if (AndroSSService.dev_type == DeviceType.UNKNOWN) {
            Log.d(TAG, "Service: Don't know what kind of device we're on...");
            if (getOpenGLVendor().toLowerCase().contains("nvidia")) {
                Log.d(TAG, "Service: This is a Tegra 2-based device.");
                AndroSSService.dev_type = DeviceType.TEGRA_2;
            } else {
                Log.d(TAG, "Service: This is a regular device.");
                AndroSSService.dev_type = DeviceType.GENERIC;
            }
        }

        return AndroSSService.dev_type;
    }

    public static void setOpenGLVendor(String vendor) {
        AndroSSService.opengl_vendor = vendor;
    }

    public static String getOpenGLVendor() {
        return AndroSSService.opengl_vendor;
    }


    // Inherited methods.
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent.getBooleanExtra("TAKE_SCREENSHOT", false)) {
            takeScreenshot();
        }
        return 0;
    }

    public void onCreate() {
        boolean initialized = AndroSSService.initialized || init();

        if (initialized) {
            sm.registerListener(this,
                    sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                    SensorManager.SENSOR_DELAY_UI);
            setEnabled(true);

            Toast.makeText(this, "AndroSS service started.", Toast.LENGTH_SHORT).show();
            Log.d(TAG, "Service: Created.");
        } else {
            stopSelf();
        }
    }

    public void onDestroy() {
        if (AndroSSService.initialized) {
            setEnabled(false);
            sm.unregisterListener(this);
            Log.d(TAG, "Service: Destroyed.");
            Toast.makeText(this, "AndroSS service stopped.", Toast.LENGTH_SHORT).show();
        }
    }



    // State control functions.
    private static void initSharedPreferences(Context context) {
        if (sp == null || spe == null) {
            sp = context.getSharedPreferences(Prefs.PREFS_NAME, MODE_PRIVATE);
            spe = sp.edit();
        }
    }


    private static void updateCommand() {
        switch (AndroSSService.getDeviceType()) {
        case UNKNOWN:
            throw new IllegalStateException(
                "Service: Cannot call updateCommand() before setting a device type!");
        case GENERIC:
            AndroSSService.command = AndroSSService.su_path + " -c " + files_dir + "/AndroSS";
            break;
        case TEGRA_2:
            AndroSSService.command = AndroSSService.fbread_path;
            break;
        }

        Log.d(TAG, "Service: Updated command to: " + command);
    }


    private boolean init() {
        // Some kind of locking would be more correct, though I'm not sure I see anything that can
        // go wrong other than some wasted cycles.

        initSharedPreferences(this);

        // Configure necessary directories.
        AndroSSService.setOutputDir(
                this,
                sp.getString(Prefs.OUTPUT_DIR_KEY, AndroSSService.DEFAULT_OUTPUT_DIR));
        AndroSSService.files_dir = getFilesDir().getAbsolutePath();

        String param_string;
        AndroSSService.c_offsets = new int[4];
        AndroSSService.c_sizes = new int[4];

        updateCommand();

        switch (AndroSSService.getDeviceType()) {
        case GENERIC:
            // Configure su.
            AndroSSService.setSuPath(
                    this,
                    sp.getString(Prefs.SU_PATH_KEY, AndroSSService.DEFAULT_SU_PATH));

            // Create the AndroSS external binary.
            AndroSSService.createExternalBinary(this);

            param_string = getFBInfo(DeviceType.GENERIC.ordinal(), AndroSSService.command);

            // Parse screen info.
            if (param_string.equals("")) {
                Log.e(TAG,"Service: Got empty param string from native!");
                Toast.makeText(this,
                        getString(R.string.empty_param_error),
                        Toast.LENGTH_LONG)
                        .show();
                return false;
            }

            Log.d(TAG, "Service: Got framebuffer params: " + param_string);
            String[] params = param_string.split(" ");

            AndroSSService.screen_width = Integer.parseInt(params[0]);
            AndroSSService.screen_height = Integer.parseInt(params[1]);
            AndroSSService.screen_depth = Integer.parseInt(params[2]);

            for (int color = 0; color < 4; ++color) {
                AndroSSService.c_offsets[color] = Integer.parseInt(params[3 + (color * 2)]);
                AndroSSService.c_sizes[color] = Integer.parseInt(params[4 + (color * 2)]);
            }

            AndroSSService.fb_stride = Integer.parseInt(params[11]);
            if (fb_stride != AndroSSService.screen_width * (AndroSSService.screen_depth / 8)) {
                Log.w(TAG, "Service: stride != width * depth! Assuming depth = stride / width.");
                AndroSSService.screen_depth = (fb_stride / AndroSSService.screen_width) * 8;
            }
            break;
        case TEGRA_2:
            param_string = getFBInfo(DeviceType.TEGRA_2.ordinal(), AndroSSService.command);

            Pattern p = Pattern.compile(".*size\\s+(\\d+)x(\\d+)\\s+format\\s+(\\d+).*");
            Matcher m = p.matcher(param_string);
            if (m.matches() == false) {
                Log.e(TAG, "Service: Could not match from fbread output. Got: " + param_string);
                return false;
            }

            AndroSSService.screen_width = Integer.parseInt(m.group(1));
            AndroSSService.screen_height = Integer.parseInt(m.group(2));

            Log.d(TAG, "Service: Got pixel format " + m.group(3));
            int tegra_format = Integer.parseInt(m.group(3));
            switch (tegra_format) {
            case 1:
                AndroSSService.screen_depth = 32;
                AndroSSService.c_offsets[0] = 16;
                AndroSSService.c_offsets[1] = 8;
                AndroSSService.c_offsets[2] = 0;
                AndroSSService.c_offsets[3] = 24;

                for (int color = 0; color < 4; ++color) {
                    AndroSSService.c_sizes[color] = 8;
                }
                break;
            default:
                Log.w(TAG, "Service: We don't know what this pixel format is! Taking a guesss...");
                AndroSSService.screen_depth = 32;
                AndroSSService.c_offsets[0] = 16;
                AndroSSService.c_offsets[1] = 8;
                AndroSSService.c_offsets[2] = 0;
                AndroSSService.c_offsets[3] = 24;

                for (int color = 0; color < 4; ++color) {
                    AndroSSService.c_sizes[color] = 8;
                }
                break;
            }
            break;
        }

        AndroSSService.sm = (SensorManager)getSystemService(SENSOR_SERVICE);

        AndroSSService.initialized = true;
        Log.d(TAG, "Service: Initialized.");

        return true;
    }



    // Actual screen-shooting functionality.
    private static void createExternalBinary(Context context) {
        try {
            FileOutputStream myfile = context.openFileOutput("AndroSS", MODE_PRIVATE);
            myfile.write(Base64.decode(AndroSSNative.native64, Base64.DEFAULT));
            myfile.close();
            Runtime.getRuntime().exec("chmod 770 " + context.getFilesDir().getAbsolutePath()
                                          + "/AndroSS");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private String calToStr(Calendar c) {
        String ret = "";
        ret += String.format("%04d-", c.get(Calendar.YEAR));
        ret += String.format("%02d-", (c.get(Calendar.MONTH) + 1));
        ret += String.format("%02d_", c.get(Calendar.DAY_OF_MONTH));
        ret += String.format("%02d.", c.get(Calendar.HOUR_OF_DAY));
        ret += String.format("%02d.", c.get(Calendar.MINUTE));
        ret += String.format("%02d", c.get(Calendar.SECOND));
        return ret;
    }


    private boolean writeScreenshot(Bitmap bmp, String filename) {
        boolean success = false;

        File output = new File(AndroSSService.getOutputDir(this) + filename);
        try {
            // A little wasteful, maybe, but this avoids errors due to the
            // output dir not existing.
            output.getParentFile().mkdirs();
            FileOutputStream os = new FileOutputStream(output);
            switch (getCompressionType()) {
            case PNG:
                success = bmp.compress(Bitmap.CompressFormat.PNG, 0, os);
                break;
            case JPG_HQ:
                success = bmp.compress(Bitmap.CompressFormat.JPEG, 90, os);
                break;
            case JPG_FAST:
                success = bmp.compress(Bitmap.CompressFormat.JPEG, 40, os);
            }
            os.flush();
            os.close();
        } catch (FileNotFoundException fnfe) {
        } catch (IOException ioe) {}

        return success;
    }


    private void registerNewScreenshot(String filename, long when) {
        ContentResolver cr = getContentResolver();
        ContentValues cv = new ContentValues(7);
        File f = new File(filename);

        cv.put(Images.Media.DISPLAY_NAME, f.getName());
        cv.put(Images.Media.TITLE, f.getName());
        cv.put(Images.Media.DATE_TAKEN, when);
        cv.put(Images.Media.ORIENTATION, 0);
        cv.put(Images.Media.DATA, filename);
        cv.put(Images.Media.SIZE, f.length());

        switch (getCompressionType()) {
        case PNG:
            cv.put(Images.Media.MIME_TYPE, "image/png");
            break;
        case JPG_HQ:
        case JPG_FAST:
            cv.put(Images.Media.MIME_TYPE, "image/jpeg");
            break;
        }

        cr.insert(Images.Media.EXTERNAL_CONTENT_URI, cv);
    }


    private void notifyUser() {
        if (sp.getBoolean(Prefs.AUDIO_FEEDBACK_KEY, false)) {
            Uri default_notification =
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            RingtoneManager.getRingtone(this, default_notification).play();
        }

        if (sp.getBoolean(Prefs.VIBRATE_FEEDBACK_KEY, false)) {
            Vibrator vibe = (Vibrator)getSystemService(Context.VIBRATOR_SERVICE);
            vibe.vibrate(175);
        }

        if (sp.getBoolean(Prefs.TOAST_FEEDBACK_KEY, false)) {
            Toast.makeText(this, "Took screenshot.", Toast.LENGTH_SHORT).show();
        }
    }


    public void takeScreenshot() {
        Calendar start_time = Calendar.getInstance();
        Log.d(TAG, "Service: Getting framebuffer pixels.");

        int bpp = AndroSSService.screen_depth / 8;
        int bitmap_width = screen_width;
        int bitmap_height = screen_height;
        int rotation = -1;

        if (AndroSSService.getRotationEnabled()) {
            // Save the rotation state immediately.
            // getOrientation() is deprecated. As of API level 8, we should be
            // using getRotation() instead. If the minSdkLevel is raised above 7,
            // this should be changed.
            rotation = ((WindowManager)getSystemService(Context.WINDOW_SERVICE))
                .getDefaultDisplay().getOrientation();

            // We need to make a bitmap that is a square defined by the larger
            // of the two screen dimensions so that we have space to rotate.
            if (bitmap_width > bitmap_height) {
                bitmap_height = bitmap_width;
            } else {
                bitmap_width = bitmap_height;
            }
        }

        // First serious order of business is to get the pixels.
        int[] pixels = {0};
        String command = "";

        switch (AndroSSService.getDeviceType()) {
        case GENERIC:
            command =
                AndroSSService.getSuPath(this) + " -c " + AndroSSService.files_dir + "/AndroSS";
            break;
        case TEGRA_2:
            command = fbread_path;
            break;
        }
        pixels = getFBPixels(getDeviceType().ordinal(), command,
                    screen_width * screen_height, bpp,
                    c_offsets, c_sizes);

        long get_pixels_time = Calendar.getInstance().getTimeInMillis() - start_time.getTimeInMillis();

        if (pixels == null) {
            String message;
            if (getSuPath(this).equals(DEFAULT_SU_PATH) == false) {
                message = getString(R.string.null_pixels_custom_su_error);
            } else {
                message = getString(R.string.null_pixels_error);
            }
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            stopSelf();
            return;
        }

        Log.d(TAG, "Service: Creating bitmap.");
        Bitmap bmp_ss = Bitmap.createBitmap(
                bitmap_width,
                bitmap_height,
                Bitmap.Config.ARGB_8888);
        bmp_ss.setPixels(pixels, 0, screen_width,
                0, 0, screen_width, screen_height);

        if (AndroSSService.getRotationEnabled()) {
            Matrix rotator = new Matrix();
            switch (rotation) {
            case (Surface.ROTATION_0):
                break;
            case (Surface.ROTATION_90):
                rotator.postRotate(270);
                break;
            case (Surface.ROTATION_180):
                rotator.postRotate(180);
                break;
            case (Surface.ROTATION_270):
                rotator.postRotate(90);
                break;
            }

            // screen_{width,height} are applied before the rotate, so we don't
            // need to change them based on rotation.
            bmp_ss = Bitmap.createBitmap(bmp_ss, 0, 0, screen_width, screen_height, rotator, false);
        }

        // Build an intelligent filename, write out to file, and register with
        // the Android media services.
        String filename = calToStr(start_time);
        switch (getCompressionType()) {
        case PNG:
            filename += ".png";
            break;
        case JPG_HQ:
        case JPG_FAST:
            filename += ".jpg";
            break;
        }
        Calendar compress_start_time = Calendar.getInstance();
        boolean success = writeScreenshot(bmp_ss, filename);
        long compress_time = Calendar.getInstance().getTimeInMillis() - compress_start_time.getTimeInMillis();

        Log.d(TAG,
                "Service: Write to " + AndroSSService.getOutputDir(this) + filename + ": "
                + (success ? "succeeded" : "failed"));
        if (success) {
            long total_time = Calendar.getInstance().getTimeInMillis() - start_time.getTimeInMillis();

            registerNewScreenshot(AndroSSService.getOutputDir(this) + filename,
                    start_time.getTimeInMillis());
            Log.d(TAG, "Service: Screenshot taken in " +
                    String.valueOf(total_time) +
                    "ms (latency: " +
                    String.valueOf(get_pixels_time) +
                    "ms, compression: " +
                    String.valueOf(compress_time) +
                    "ms).");
            notifyUser();
        }

        if (!AndroSSService.isPersistent()) {
            stopSelf();
        }
    }



    // Triggering functions.
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // whatever
        return;
    }
    public void onSensorChanged(SensorEvent event) {
        if (isEnabled() && isShakeEnabled()) {
            boolean first = (last_shake_event == 0 ? true : false);
            boolean handled = (first ? true : false);

            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];

            float x_diff = x - old_x;
            float y_diff = y - old_y;
            float z_diff = z - old_z;

            old_x = x;
            old_y = y;
            old_z = z;

            // We'll probably get a lot of shake events from a single
            // physical motion, so ignore any new ones for a moment.
            if (!first && event.timestamp - last_shake_event > IGNORE_SHAKE_INTERVAL) {
                double magnitude = Math.sqrt(
                        (x_diff * x_diff) +
                        (y_diff * y_diff) +
                        (z_diff * z_diff));
                if (magnitude > AndroSSService.ACCEL_THRESH) {
                    handled = true;
                    Log.d(TAG, String.format(
                            "Service: Triggering on shake of magnitude %f with tstamp %d (offset: %d).",
                            magnitude, event.timestamp, event.timestamp - last_shake_event));
                    takeScreenshot();
                    if (!AndroSSService.isPersistent()) {
                        stopSelf();
                    }
                }
            }

            if (handled) {
                last_shake_event = event.timestamp;
            }
        }
    }
}
