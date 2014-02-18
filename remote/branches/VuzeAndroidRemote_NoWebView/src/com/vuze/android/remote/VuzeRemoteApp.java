package com.vuze.android.remote;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

public class VuzeRemoteApp
	extends Application
{
	private static AppPreferences appPreferences;

	@Override
	public void onCreate() {
		super.onCreate();
		
		if (AndroidUtils.DEBUG) {
			Log.d(null, "Application.onCreate");
		}
		appPreferences = AppPreferences.createAppPreferences(getApplicationContext());
	}
	
	public static AppPreferences getAppPreferences() {
		return appPreferences;
	}
}
