/**
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 * 
 */

package com.vuze.android.remote;

import java.util.*;

import android.content.Context;

import com.vuze.android.remote.rpc.TorrentListReceivedListener;
import com.vuze.android.remote.rpc.TransmissionRPC;

/**
 * Access to all the information for a session, such as:<P>
 * - RemoteProfile<BR>
 * - SessionSettings<BR>
 * - RPC<BR>
 * - torrents<BR>
 */
public class SessionInfo
{
	private SessionSettings sessionSettings;

	private TransmissionRPC rpc;

	private RemoteProfile remoteProfile;

	/** <Key, TorrentMap> */
	private LinkedHashMap<Object, Map<?, ?>> mapOriginal;

	private Object mLock = new Object();

	private TorrentListReceivedListener torrentListReceivedListener;

	private boolean rememberSettingChanges;

	protected SessionInfo(SessionSettings sessionSettings, TransmissionRPC rpc,
			RemoteProfile remoteProfile, boolean rememberSettingChanges) {
		this.sessionSettings = sessionSettings;
		this.rpc = rpc;
		this.remoteProfile = remoteProfile;
		this.rememberSettingChanges = rememberSettingChanges;
		this.mapOriginal = new LinkedHashMap<Object, Map<?, ?>>();

		rpc.addTorrentListReceivedListener(new TorrentListReceivedListener() {
			@Override
			public void rpcTorrentListReceived(List listTorrents) {
				addTorrents(listTorrents);
			}
		});
	}

	/**
	 * @return the sessionSettings
	 */
	public SessionSettings getSessionSettings() {
		return sessionSettings;
	}

	/**
	 * @return the rpc
	 */
	public TransmissionRPC getRpc() {
		return rpc;
	}

	/**
	 * @return the remoteProfile
	 */
	public RemoteProfile getRemoteProfile() {
		return remoteProfile;
	}

	/**
	 * @param sessionSettings the sessionSettings to set
	 */
	public void setSessionSettings(SessionSettings sessionSettings) {
		this.sessionSettings = sessionSettings;
	}

	/**
	 * @param rpc the rpc to set
	 */
	public void setRpc(TransmissionRPC rpc) {
		this.rpc = rpc;
	}

	/**
	 * @param remoteProfile the remoteProfile to set
	 */
	public void setRemoteProfile(RemoteProfile remoteProfile) {
		this.remoteProfile = remoteProfile;
	}

	public LinkedHashMap<Object, Map<?, ?>> getTorrentList() {
		synchronized (mLock) {
			return new LinkedHashMap<Object, Map<?, ?>>(mapOriginal);
		}
	}

	public Object[] getTorrentListKeys() {
		synchronized (mLock) {
			return mapOriginal.keySet().toArray();
		}
	}

	public Map<?, ?> getTorrent(Object key) {
		synchronized (mLock) {
			return mapOriginal.get(key);
		}
	}

	@SuppressWarnings("rawtypes")
	public void addTorrents(List<? extends Map<?, ?>> collection) {
		synchronized (mLock) {
			for (Map mapTorrent : collection) {
				Object key = mapTorrent.get("id");
				if (key instanceof Integer) {
					key = Long.valueOf((Integer) key);
				}
				Map old = mapOriginal.put(key, mapTorrent);
				if (old != null) {
					// merge anything missing in new map with old
					for (Iterator iterator = old.keySet().iterator(); iterator.hasNext();) {
						Object torrentKey = iterator.next();
						if (!mapTorrent.containsKey(torrentKey)) {
							mapTorrent.put(torrentKey, old.get(torrentKey));
						}
					}
				}
			}
		}
		if (torrentListReceivedListener != null) {
			torrentListReceivedListener.rpcTorrentListReceived(collection);
		}
	}

	public void setTorrentListReceivedListener(TorrentListReceivedListener l) {
		this.torrentListReceivedListener = l;
	}

	public void saveProfileIfRemember(Context context) {
		if (rememberSettingChanges) {
			AppPreferences appPreferences = new AppPreferences(context);
			appPreferences.addRemoteProfile(remoteProfile);
		}
	}

}
