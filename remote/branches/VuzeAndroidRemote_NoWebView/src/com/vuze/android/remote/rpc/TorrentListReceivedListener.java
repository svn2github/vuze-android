package com.vuze.android.remote.rpc;

import java.util.List;

public interface TorrentListReceivedListener
{
	/**
	 * 
	 * @param listTorrents  List of Maps
	 */
	public void rpcTorrentListReceived(List<?> listTorrents);
}
