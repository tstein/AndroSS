package net.tedstein.AndroSS;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.TextView;

public class MoreSettings extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.more_settings);

        TextView goto_debug = (TextView)findViewById(R.id.tv_goto_debug);
        final Context c = this;
        goto_debug.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(c, DebugInfo.class);
                startActivity(i);
            }
        });
    }


    @Override
    protected void onResume() {
        super.onResume();

        TextView screenshot_dir_current = (TextView)findViewById(R.id.tv_screenshot_dir_current);
        screenshot_dir_current.setText(AndroSSService.getOutputDir());
    }
}
