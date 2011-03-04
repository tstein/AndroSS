package net.tedstein.AndroSS;

import net.tedstein.AndroSS.AndroSSService.CompressionType;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.Spinner;

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
		Spinner compression = (Spinner)findViewById(R.id.CompressionSpinner);
		ArrayAdapter<CharSequence> types = ArrayAdapter.createFromResource(this,
				R.array.compression_types, android.R.layout.simple_spinner_item);
		types.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		compression.setAdapter(types);

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

		compression.setOnItemSelectedListener(new OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> parent, View view,
					int pos, long id) {
				final SharedPreferences sp = getSharedPreferences(Prefs.PREFS_NAME, MODE_PRIVATE);
				final SharedPreferences.Editor spe = sp.edit();

				CompressionType ct = CompressionType.values()[pos];

				spe.putString(Prefs.COMPRESSION_KEY, ct.name());
				spe.commit();
			}

			@Override
			public void onNothingSelected(AdapterView<?> parent) {
				return;
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
		Spinner compression = (Spinner)findViewById(R.id.CompressionSpinner);

		enabled.setChecked(sp.getBoolean(Prefs.ENABLED_KEY, false));
		persistent.setChecked(sp.getBoolean(Prefs.PERSISTENT_KEY, false));
		useShake.setChecked(sp.getBoolean(Prefs.SHAKE_TRIGGER_KEY, false));
		useCamera.setChecked(sp.getBoolean(Prefs.CAMERA_TRIGGER_KEY, false));
		compression.setSelection(
				CompressionType.valueOf(
						sp.getString(Prefs.COMPRESSION_KEY,
								CompressionType.PNG.name()))
						.ordinal());

//		TextView debugString = (TextView)findViewById(R.id.DebugStringText);
//
//		debugString.setText(AndroSSService.getParamString());
	}
}
