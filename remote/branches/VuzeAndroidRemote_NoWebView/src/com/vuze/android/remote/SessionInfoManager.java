package com.vuze.android.remote;

import java.util.HashMap;
import java.util.Map;

import com.vuze.android.remote.rpc.TransmissionRPC;

public class SessionInfoManager
{
	private static Map<String, SessionInfo> mapSessionInfo = new HashMap<String, SessionInfo>();
	
	public static SessionInfo getSessionInfo(String id) {
		return mapSessionInfo.get(id);
	}
	
	public static void setSessionInfo(SessionInfo sessionInfo) {
		String id = sessionInfo.getRemoteProfile().getID();
		mapSessionInfo.put(id, sessionInfo);
	}
	
	public static SessionInfo createSessionInfo(SessionSettings sessionSettings, TransmissionRPC rpc,
			RemoteProfile remoteProfile, boolean rememberSettingChanges) {
		SessionInfo sessionInfo = new SessionInfo(sessionSettings, rpc, remoteProfile, rememberSettingChanges);
		setSessionInfo(sessionInfo);
		return sessionInfo;
	}

}
