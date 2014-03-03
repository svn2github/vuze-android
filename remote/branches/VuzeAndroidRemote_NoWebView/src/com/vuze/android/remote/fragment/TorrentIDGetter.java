package com.vuze.android.remote.fragment;

import com.vuze.android.remote.SessionInfo;

public interface TorrentIDGetter
{
	public long getTorrentID();
	public SessionInfo getSessionInfo();
}
