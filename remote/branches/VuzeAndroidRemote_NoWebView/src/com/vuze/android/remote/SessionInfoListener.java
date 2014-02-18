package com.vuze.android.remote;

public interface SessionInfoListener
{
	public void transmissionRpcAvailable(SessionInfo sessionInfo);

	public void uiReady();
}