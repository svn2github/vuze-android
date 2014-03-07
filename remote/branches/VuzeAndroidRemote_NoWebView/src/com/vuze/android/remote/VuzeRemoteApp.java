package com.vuze.android.remote;

import android.app.Application;
import android.content.Context;
import android.util.DisplayMetrics;
import android.util.Log;

public class VuzeRemoteApp
	extends Application
{
	private static AppPreferences appPreferences;

	private static NetworkState networkState;

	private static Context applicationContext;

	@Override
	public void onCreate() {
		super.onCreate();

		if (AndroidUtils.DEBUG) {
			Log.d(null, "Application.onCreate");
		}
		applicationContext = getApplicationContext();
		appPreferences = AppPreferences.createAppPreferences(applicationContext);
		networkState = new NetworkState(applicationContext);

		if (AndroidUtils.DEBUG) {
			DisplayMetrics dm = getContext().getResources().getDisplayMetrics();

			System.out.println(dm.widthPixels + "px x " + dm.heightPixels + "px");
			System.out.println(pxToDp(dm.widthPixels) + "dp x "
					+ pxToDp(dm.heightPixels) + "dp");
		}

		appPreferences.setNumOpens(appPreferences.getNumOpens() + 1);
	}

	public int pxToDp(int px) {
		DisplayMetrics dm = getContext().getResources().getDisplayMetrics();

		int dp = Math.round(px / (dm.xdpi / DisplayMetrics.DENSITY_DEFAULT));
		return dp;
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

	public static Context getContext() {
		return applicationContext;
	}
}
