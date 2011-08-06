package net.tedstein.AndroSS;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import android.app.Activity;
import android.content.Intent;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.util.Log;


public class GLDetector extends Activity {
    public static final String VENDOR_EXTRA = "vendor";
    private static final String TAG = "AndroSS";



    class GLVendorDetectionRenderer implements GLSurfaceView.Renderer {
        public void onSurfaceCreated(GL10 gl, EGLConfig config) {
            String vendor = gl.glGetString(GL10.GL_VENDOR);
            Log.e(TAG, "GLDetector: vendor is " + vendor);

            Intent i = new Intent();
            i.putExtra(VENDOR_EXTRA, vendor);
            setResult(0, i);
            finish();
        }

        public void onSurfaceChanged(GL10 gl, int w, int h) { }

        public void onDrawFrame(GL10 gl) { }
    }



    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        GLSurfaceView glsv = new GLSurfaceView(this);
        glsv.setRenderer(new GLVendorDetectionRenderer());
        setContentView(glsv);
    }
}
