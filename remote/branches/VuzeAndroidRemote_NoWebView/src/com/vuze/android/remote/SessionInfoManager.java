package com.vuze.android.remote;

import java.util.HashMap;
import java.util.Map;

import com.vuze.android.remote.rpc.TransmissionRPC;

public class SessionInfoManager
{
	public static String BUNDLE_KEY = "RemoteProfileID";

	private static Map<String, SessionInfo> mapSessionInfo = new HashMap<String, SessionInfo>();

	public static SessionInfo getSessionInfo(String id) {
		return mapSessionInfo.get(id);
	}

	public static void setSessionInfo(SessionInfo sessionInfo) {
		String id = sessionInfo.getRemoteProfile().getID();
		mapSessionInfo.put(id, sessionInfo);
	}

	public static SessionInfo createSessionInfo(TransmissionRPC rpc,
			RemoteProfile remoteProfile, boolean rememberSettingChanges) {
		SessionInfo sessionInfo = new SessionInfo(rpc, remoteProfile,
				rememberSettingChanges);
		setSessionInfo(sessionInfo);
		return sessionInfo;
	}

}
