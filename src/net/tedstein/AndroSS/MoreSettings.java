package net.tedstein.AndroSS;

import net.tedstein.AndroSS.AndroSSService.DeviceType;
import net.tedstein.AndroSS.util.RootUtils;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.widget.EditText;
import android.widget.Toast;

public class MoreSettings extends PreferenceActivity {
    private PreferenceManager mPreferenceManager;
    private SharedPreferences.Editor mSharedPreferenceEditor;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.more_settings);
        mPreferenceManager = getPreferenceManager();
        mSharedPreferenceEditor = mPreferenceManager.getSharedPreferences().edit();
        final Context context = this;

        final EditTextPreference screenshot_dir =
            (EditTextPreference)mPreferenceManager.findPreference("screenshot_dir");
        final EditText et = screenshot_dir.getEditText();
        final String old_output_dir = AndroSSService.getOutputDir();
        screenshot_dir.setSummary(old_output_dir);
        et.setSingleLine();
        screenshot_dir.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                String new_output_dir = (String)newValue;
                if (AndroSSService.setOutputDir(new_output_dir)) {
                    screenshot_dir.setSummary(AndroSSService.getOutputDir());
                    return true;
                } else {
                    Toast.makeText(context,
                            context.getString(R.string.change_output_error, new_output_dir),
                            Toast.LENGTH_LONG).show();
                    return false;
                }
            }
        });

        Preference retry_root_check = mPreferenceManager.findPreference("retry_root_check");
        if (AndroSSService.getDeviceType() != DeviceType.GENERIC) {
            retry_root_check.setEnabled(false);
        }
        retry_root_check.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                RootUtils.showRootTestMessage(context);
                return true;
            }
        });

        Preference debug_info = mPreferenceManager.findPreference("debug_info");
        debug_info.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Intent i = new Intent(context, DebugInfo.class);
                startActivity(i);
                return true;
            }
        });
    }


    @Override
    protected void onResume() {
        super.onResume();

        mSharedPreferenceEditor.putString("screenshot_dir", AndroSSService.getOutputDir());
    }
}