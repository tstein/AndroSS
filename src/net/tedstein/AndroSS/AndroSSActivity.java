package net.tedstein.AndroSS;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

public class AndroSSActivity extends Activity {
	static {
		System.loadLibrary("AndroSS_nbridge");
	}

	private static final String TAG = "AndroSS";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		TextView tv = (TextView)findViewById(R.id.main_text);
		Button b = (Button) findViewById(R.id.SS_button);
		
		final Context c = this;
		
		// TODO: This is just terrible.
		b.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				startService(new Intent(c, AndroSSService.class));
				stopService(new Intent(c, AndroSSService.class));
			}
		});		
	}
}
