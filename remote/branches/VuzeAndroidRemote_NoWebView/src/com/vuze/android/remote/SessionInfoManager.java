package com.vuze.android.remote;

import java.util.HashMap;
import java.util.Map;

import android.app.Activity;
import android.util.Log;

public class SessionInfoManager
{
	public static String BUNDLE_KEY = "RemoteProfileID";

	private static Map<String, SessionInfo> mapSessionInfo = new HashMap<String, SessionInfo>();

	public static SessionInfo getSessionInfo(String id, Activity activity,
			boolean rememberSettingChanges) {
		synchronized (mapSessionInfo) {
			SessionInfo sessionInfo = mapSessionInfo.get(id);
			if (sessionInfo == null) {
				RemoteProfile remoteProfile = VuzeRemoteApp.getAppPreferences().getRemote(
						id);

				if (remoteProfile == null) {
					Log.d("SessionInfo", "No SessionInfo for " + id);
					return null;
				}
				sessionInfo = new SessionInfo(activity, remoteProfile,
						rememberSettingChanges);
				mapSessionInfo.put(id, sessionInfo);
			}
			return sessionInfo;
		}
	}

	public static SessionInfo getSessionInfo(RemoteProfile remoteProfile,
			Activity activity, boolean rememberSettingChanges) {
		String id = remoteProfile.getID();
		synchronized (mapSessionInfo) {
			SessionInfo sessionInfo = mapSessionInfo.get(id);
			if (sessionInfo == null) {
				sessionInfo = new SessionInfo(activity, remoteProfile,
						rememberSettingChanges);
				mapSessionInfo.put(id, sessionInfo);
			}
			return sessionInfo;
		}
	}

}
