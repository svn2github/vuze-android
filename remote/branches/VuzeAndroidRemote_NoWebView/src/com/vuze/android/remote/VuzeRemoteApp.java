package com.vuze.android.remote;

import android.app.Application;
import android.util.Log;

public class VuzeRemoteApp
	extends Application
{
	private static AppPreferences appPreferences;
	private static NetworkState networkState;

	@Override
	public void onCreate() {
		super.onCreate();

		if (AndroidUtils.DEBUG) {
			Log.d(null, "Application.onCreate");
		}
		appPreferences = AppPreferences.createAppPreferences(getApplicationContext());
		networkState = new NetworkState(getApplicationContext());
	}
	
	@Override
	public void onTerminate() {
		// NOTE: This is never called except in emulation!
		networkState.dipose();

		super.onTerminate();
	}

	public static AppPreferences getAppPreferences() {
		return appPreferences;
	}

	public static NetworkState getNetworkState() {
		return networkState;
	}
}
