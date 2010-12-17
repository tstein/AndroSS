package net.tedstein.AndroSS;

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
    	Log.d(TAG, "Service: Getting framebuffer params.");
    	files_dir = getFilesDir().getAbsolutePath();
    	String[] params = getFBInfo(files_dir).split(" ");
    	
    	AndroSSService.screen_width = Integer.parseInt(params[0]);
    	AndroSSService.screen_height = Integer.parseInt(params[1]);
    	AndroSSService.screen_depth = Integer.parseInt(params[2]);
    	
    	AndroSSService.initialized = true;
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
		// we'll need to do some conversion based on the bit depth.
		int[] pixels = new int[bytes / 4];
		Log.d(TAG, "Service: Converting " + String.valueOf(pixels_bytes.length) +
				" bytes from the framebuffer to " +
				String.valueOf(pixels.length) +
				" int pixels.");
		int tmp;
		switch (screen_depth) {
		case 16: { // RGB_565
			// TODO: Anything!
		}
		case 32: { // BGRA_8888
			for (int i = 0; i < bytes; i += 4) {
				tmp = 0;
				for (int j = 0; j < 4; ++j) {
					tmp = (256 * tmp) + pixels_bytes[i + (3 - j) ];
				}
				pixels[i / 4] = tmp;
			}
		}
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
