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
				Intent i = new Intent(c, AndroSSService.class);
				if (isChecked) {
					startService(i);
				} else {
					stopService(i);
				}
			}
		});

		persistent.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				if (isChecked) {
					AndroSSService.setPersistent();
				} else {
					AndroSSService.unsetPersistent();
				}
			}
		});

		useCamera.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				if (isChecked) {
					CameraButtonReceiver.enable();
				} else {
					CameraButtonReceiver.disable();
				}
			}
		});

		useShake.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				if (isChecked) {
					AndroSSService.setShake();
				} else {
					AndroSSService.unsetShake();
				}
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
