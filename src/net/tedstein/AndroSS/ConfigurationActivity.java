package net.tedstein.AndroSS;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.TextView;

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
				final SharedPreferences sp = getSharedPreferences(Prefs.PREFS_NAME, MODE_PRIVATE);
				final SharedPreferences.Editor spe = sp.edit();

				spe.putBoolean(Prefs.PERSISTENT_KEY, isChecked);
				spe.commit();
			}
		});

		useCamera.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				final SharedPreferences sp = getSharedPreferences(Prefs.PREFS_NAME, MODE_PRIVATE);
				final SharedPreferences.Editor spe = sp.edit();

				spe.putBoolean(Prefs.CAMERA_TRIGGER_KEY, isChecked);
				spe.commit();
			}
		});

		useShake.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				final SharedPreferences sp = getSharedPreferences(Prefs.PREFS_NAME, MODE_PRIVATE);
				final SharedPreferences.Editor spe = sp.edit();

				spe.putBoolean(Prefs.SHAKE_TRIGGER_KEY, isChecked);
				spe.commit();
			}
		});
	}

	@Override
	protected void onResume() {
		super.onResume();

		final SharedPreferences sp = getSharedPreferences(Prefs.PREFS_NAME, MODE_PRIVATE);

		CheckBox enabled = (CheckBox)findViewById(R.id.ServiceStatusCheckBox);
		CheckBox persistent = (CheckBox)findViewById(R.id.PersistenceCheckBox);
		CheckBox useShake = (CheckBox)findViewById(R.id.ShakeCheckBox);
		CheckBox useCamera = (CheckBox)findViewById(R.id.CameraButtonCheckBox);

		enabled.setChecked(sp.getBoolean(Prefs.ENABLED_KEY, false));
		persistent.setChecked(sp.getBoolean(Prefs.PERSISTENT_KEY, false));
		useShake.setChecked(sp.getBoolean(Prefs.SHAKE_TRIGGER_KEY, false));
		useCamera.setChecked(sp.getBoolean(Prefs.CAMERA_TRIGGER_KEY, false));

		TextView debugString = (TextView)findViewById(R.id.DebugStringText);

		debugString.setText(AndroSSService.getParamString());
	}
}
