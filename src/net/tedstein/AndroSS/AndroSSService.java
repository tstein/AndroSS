package net.tedstein.AndroSS;

import java.io.FileOutputStream;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.IBinder;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;

public class AndroSSService extends Service implements SensorEventListener {
	static {
		System.loadLibrary("AndroSS_nbridge");
	}

	private static final String TAG = "AndroSS";
	private static final float ACCEL_THRESH = 7.0F;
	private static SensorManager sm;
	// Phone graphical parameters.
	public static int screen_width;
	public static int screen_height;
	public static int screen_depth;
	public static int[] c_offsets;
	public static int[] c_sizes;
	public static String files_dir;
	// Service state.
	private static boolean initialized = false;
	private static boolean enabled = false;
	private static boolean persistent = false;
	private static boolean shakeTrigger = false;
	private static float old_x = 0;
	private static float old_y = 0;
	private static float old_z = 0;



	// Native function signatures.
	private static native String getFBInfo(String bin_location);
	private static native byte[] getFBPixels(String bin_location, int bytes);



	// Getters and setters.
	public static boolean isEnabled() {
		return enabled;
	}
	public static void setEnabled(boolean enable) {
		AndroSSService.enabled = enable;
	}

	public static boolean isPersistent() {
		return persistent;
	}	
	public static void setPersistent(boolean enable) {
		persistent = enable;
	}

	public static boolean isShakeEnabled() {
		return shakeTrigger;
	}
	public static void setShake(boolean enable) {
		if (enable) {
			shakeTrigger = true;
		} else {
			old_x = 0;
			old_y = 0;
			old_z = 0;
			shakeTrigger = false;
		}
	}



	// Inherited methods.
	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	public void onCreate() {
		start();
		Toast.makeText(this, "AndroSS service started.", Toast.LENGTH_SHORT).show();
		return;
	}

	public void onDestroy() {
		destroy();
		Toast.makeText(this, "AndroSS service stopped.", Toast.LENGTH_SHORT).show();
	}



	// State control functions.
	public void start() {
		if (!AndroSSService.initialized) {
			init();
		}
		AndroSSService.enabled = true;
		Log.d(TAG, "Service: Started.");
	}

	public void destroy() {
		AndroSSService.enabled = false;
		Log.d(TAG, "Service: Destroyed.");
	}

	public void init() {
		// TODO: Some kind of locking would be more correct, though I'm not sure
		// I see anything that can go wrong other than some wasted cycles.

		// Create the AndroSS external binary.
		// TODO: This would be a great time to chmod +x it, but will that stick?
		try {
			FileOutputStream myfile = openFileOutput("AndroSS", MODE_PRIVATE);
			myfile.write(Base64.decode(AndroSSNative.native64, Base64.DEFAULT));
			myfile.close();
		} catch (Exception e) {
			e.printStackTrace();
		}

		// Get screen info.
		files_dir = getFilesDir().getAbsolutePath();
		String param_string = getFBInfo(files_dir);
		Log.d(TAG, "Service: Got framebuffer params: " + param_string);
		String[] params = param_string.split(" ");

		AndroSSService.screen_width = Integer.parseInt(params[0]);
		AndroSSService.screen_height = Integer.parseInt(params[1]);
		AndroSSService.screen_depth = Integer.parseInt(params[2]);

		AndroSSService.c_offsets = new int[4];
		AndroSSService.c_sizes = new int[4];
		for (int color = 0; color < 4; ++color) {
			AndroSSService.c_offsets[color] = Integer.parseInt(params[3 + (color * 2)]);
			AndroSSService.c_sizes[color] = Integer.parseInt(params[4 + (color * 2)]);
		}

		AndroSSService.sm = (SensorManager)getSystemService(SENSOR_SERVICE);
		sm.registerListener(this,
				sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
				SensorManager.SENSOR_DELAY_GAME);

		AndroSSService.initialized = true;
		Log.d(TAG, "Service: Initialized.");
	}



	// Actual screen-shooting functionality.
	/**
	 * @param in - The value of the input pixel.
	 * @param offsets - An array of four bytes representing the offset of each
	 * color in the input pixel. This will be interpreted as [b, g, r, a].
	 * @param sizes - An array of four bytes representing how many bits each
	 * color occupies in the input pixel. This will be interpreted as
	 * [b, g, r, a].
	 * @return The input pixel formatted as an ARGB_8888 int. 
	 */
	private static int formatPixel(int in, int[] offsets, int[] sizes){
		int out[] = new int[4];
		int mask;

		for (int color = 0; color < 4; ++color) {
			// Build the mask by repeatedly shifting and incrementing.
			mask = 0;
			for (int bits = 0; bits < sizes[color]; ++bits) {
				mask <<= 1;
				++mask;
			}

			// Extract the desired bits from in, then shift them up if we have
			// less than a full byte of information.
			out[color] = (in >> offsets[color]) & mask;
			out[color] <<= 8 - sizes[color];
		}

		// If the framebuffer had no alpha channel, we're about to return an
		// invisible pixel. 
		if (sizes[3] == 0) {
			out[3] = 255;
		}

		// Finally, combine the components, and that's a pixel.
		int ret = 0;
		for (int color = 3; color >= 0; --color) {
			ret <<= 8;
			ret |= out[color];
		}
		return ret;
	}


	public static void takeScreenshot(Context c) {
		long start_time = System.currentTimeMillis();
		Log.d(TAG, "Service: Getting framebuffer pixels.");
		int bytes = screen_width * screen_height * (screen_depth / 8);
		byte[] pixels_bytes = getFBPixels(AndroSSService.files_dir, bytes);
		long pixel_time = System.currentTimeMillis();

		// android.graphics.Bitmap is expecting an array of ARGB_8888 ints, so
		// we'll need to do some conversion based on the bit depth. Extract
		// screen_depth / 8 bytes at a time and pass them off to formatPixel().
		int[] pixels = new int[bytes / 4];
		Log.d(TAG, "Service: Converting " + String.valueOf(pixels_bytes.length) +
		" bytes from the framebuffer.");
		int tmp_int;
		byte tmp_byte;
		int bpp = AndroSSService.screen_depth / 8;
		for (int i = 0; i < bytes; i += bpp) {
			tmp_int = 0;
			for (int j = bpp - 1; j >= 0; --j) {
				tmp_byte = pixels_bytes[i + j];
				tmp_int <<= 8;
				tmp_int |= (0x000000FF & (int)tmp_byte);
			}
			pixels[i / bpp] = formatPixel(tmp_int,
					AndroSSService.c_offsets, AndroSSService.c_sizes);
		}

		Log.d(TAG, "Service: Creating bitmap.");
		Bitmap bmp_ss = Bitmap.createBitmap(
				screen_width,
				screen_height,
				Bitmap.Config.ARGB_8888);
		bmp_ss.setPixels(pixels, 0, screen_width,
				0, 0, screen_width, screen_height);

		String filename = "/sdcard/ss.png";
		boolean success = false;
		try {
			FileOutputStream os = new FileOutputStream(filename);
			success = bmp_ss.compress(Bitmap.CompressFormat.PNG, 0, os);
			os.flush();
			os.close();
		} catch (Exception e) {
			Log.e(TAG, "Service: Oh god what");
			e.printStackTrace();
		} finally {
			Log.d(TAG, "Service: Writing to " + filename + ": " + (success ? "success" : "failure"));
		}

		long finish_time = System.currentTimeMillis();
		Log.d(TAG, "Service: Screenshot taken in " +
				String.valueOf(finish_time - start_time) +
				"ms (latency: " +
				String.valueOf(pixel_time - start_time) +
		"ms).");
		Toast.makeText(c, "Took screenshot.", Toast.LENGTH_SHORT).show();
	}



	// Triggering functions.
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
		// whatever
		return;
	}
	public void onSensorChanged(SensorEvent event) {
		if (AndroSSService.enabled && AndroSSService.shakeTrigger) {
			boolean first = false;
			if (old_x == 0 && old_y == 0 && old_z == 0) {
				first = true;
			}

			float x = event.values[0];
			float y = event.values[1];
			float z = event.values[2];

			float x_diff = x - old_x;
			float y_diff = y - old_y;
			float z_diff = z - old_z;

			old_x = x;
			old_y = y;
			old_z = z;

			if (!first) {
				double magnitude = Math.sqrt(
						(x_diff * x_diff) +
						(y_diff * y_diff) +
						(z_diff * z_diff));
				if (magnitude > AndroSSService.ACCEL_THRESH) {
					Log.d(TAG, String.format(
							"Service: Triggering on shake of magnitude %f.",
							magnitude));

					// We'll probably get a lot of shake events from a single
					// physical motion, so disable this trigger during the
					// screenshot.
					AndroSSService.setShake(false);
					AndroSSService.takeScreenshot(this);
					AndroSSService.setShake(true);

					if (!AndroSSService.isPersistent()) {
						AndroSSService.setEnabled(false);
					}
				}
			}
		}
	}
}
