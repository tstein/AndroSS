package net.tedstein.AndroSS;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;

public class ConfigurationActivity extends Activity {
	static {
		System.loadLibrary("AndroSS_nbridge");
	}

	@SuppressWarnings("unused")
	private static final String TAG = "AndroSS";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.config);

		final Context c = this;

		CheckBox enabled = (CheckBox)findViewById(R.id.ServiceStatusCheckBox);
		CheckBox persistent = (CheckBox)findViewById(R.id.PersistenceCheckBox);
		CheckBox useCamera = (CheckBox)findViewById(R.id.CameraButtonCheckBox);
		CheckBox useShake = (CheckBox)findViewById(R.id.ShakeCheckBox);

		enabled.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			public void onCheckedChanged(CompoundButton buttonView,
					boolean isChecked) {
				// This gets called on resumes and rotations, so we should make
				// sure we actually want to mess with the service before doing
				// so.
				boolean was_enabled = AndroSSService.isEnabled();
				Intent i = new Intent(c, AndroSSService.class);
				if (isChecked) {
					if (!was_enabled) {
						startService(i);
					}
				} else {
					if (was_enabled) {
						stopService(i);
					}
				}
			}
		});

		persistent.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				AndroSSService.setPersistent(isChecked);
			}
		});

		useCamera.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				CameraButtonReceiver.setEnabled(isChecked);
			}
		});

		useShake.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				AndroSSService.setShake(isChecked);
			}
		});
	}

	@Override
	protected void onResume() {
		super.onResume();

		CheckBox enabled = (CheckBox)findViewById(R.id.ServiceStatusCheckBox);
		CheckBox persistent = (CheckBox)findViewById(R.id.PersistenceCheckBox);
		CheckBox useCamera = (CheckBox)findViewById(R.id.CameraButtonCheckBox);
		CheckBox useShake = (CheckBox)findViewById(R.id.ShakeCheckBox);

		enabled.setChecked(AndroSSService.isEnabled());
		persistent.setChecked(AndroSSService.isPersistent());
		useCamera.setChecked(CameraButtonReceiver.isEnabled());
		useShake.setChecked(AndroSSService.isShakeEnabled());
	}
}
