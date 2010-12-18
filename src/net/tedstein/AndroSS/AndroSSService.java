package net.tedstein.AndroSS;

import java.io.File;
import java.io.FileOutputStream;

import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.IBinder;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;

public class AndroSSService extends Service {
	static {
		System.loadLibrary("AndroSS_nbridge");
	}

	private static final String TAG = "AndroSS";
	public static int screen_width;
	public static int screen_height;
	public static int screen_depth;
	public static int[] c_offsets;
	public static int[] c_sizes;
	public static String files_dir;
	private static boolean initialized = false;

	private static native String getFBInfo(String bin_location);
	private static native byte[] getFBPixels(String bin_location, int bytes);

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	public void onCreate() {
		Toast.makeText(this, "Taking screenshot!", Toast.LENGTH_SHORT).show();
		takeScreenShot();
	}

	public void init() {
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

		AndroSSService.initialized = true;
		Log.d(TAG, "Service: Initialized.");
	}



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


	public void takeScreenShot() {
		// TODO: Some kind of locking would be more correct, though I'm not sure
		// I see anything that can go wrong other than some wasted cycles.
		if (!AndroSSService.initialized) {
			Log.d(TAG, "Service: Initializing.");
			init();
		}

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
	}
}