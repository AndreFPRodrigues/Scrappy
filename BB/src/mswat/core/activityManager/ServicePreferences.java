package mswat.core.activityManager;

import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.PreferenceActivity;

public class ServicePreferences extends PreferenceActivity implements
		OnSharedPreferenceChangeListener { 
	
	public final String TPR = "tpr";
	public final String LOG = "log";


	public final String TOUCH_INDEX = "touch_device";

	@Override
	@SuppressWarnings("deprecation")
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.preferences);
		getPreferenceScreen().getSharedPreferences()
				.registerOnSharedPreferenceChangeListener(this);
		
 
	}

	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
			String key) {
		
	}

}
