package com.vuze.android.remote.rpc;

import java.util.List;
import java.util.Map;

public interface TorrentListReceivedListener
{

	/**
	 * 
	 * @param listTorrents  List of Maps
	 */
	public void rpcTorrentListReceived(List listTorrents);


}
