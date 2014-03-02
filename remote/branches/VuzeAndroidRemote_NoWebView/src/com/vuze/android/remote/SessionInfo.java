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

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.aelitis.azureus.util.MapUtils;
import com.vuze.android.remote.NetworkState.NetworkStateListener;
import com.vuze.android.remote.rpc.*;

/**
 * Access to all the information for a session, such as:<P>
 * - RemoteProfile<BR>
 * - SessionSettings<BR>
 * - RPC<BR>
 * - torrents<BR>
 */
public class SessionInfo
	implements SessionSettingsReceivedListener, NetworkStateListener
{
	private static final String TAG = "SessionInfo";

	private SessionSettings sessionSettings;

	private TransmissionRPC rpc;

	private RemoteProfile remoteProfile;

	/** <Key, TorrentMap> */
	private LinkedHashMap<Object, Map<?, ?>> mapOriginal;

	private Object mLock = new Object();

	private List<TorrentListReceivedListener> torrentListReceivedListeners = new ArrayList<TorrentListReceivedListener>();

	private List<SessionSettingsChangedListener> sessionSettingsChangedListeners = new ArrayList<SessionSettingsChangedListener>();

	private boolean rememberSettingChanges;

	private Handler handler;

	boolean uiReady = false;

	List<SessionInfoListener> availabilityListeners = new ArrayList<SessionInfoListener>();

	private Map<?, ?> mapSessionStats;

	private Map<Long, Map<?, ?>> mapTags;

	protected SessionInfo(TransmissionRPC rpc, RemoteProfile _remoteProfile,
			boolean rememberSettingChanges) {
		this.rpc = rpc;
		this.remoteProfile = _remoteProfile;
		this.rememberSettingChanges = rememberSettingChanges;
		this.mapOriginal = new LinkedHashMap<Object, Map<?, ?>>();

		for (SessionInfoListener l : availabilityListeners) {
			l.transmissionRpcAvailable(this);
		}

		rpc.addTorrentListReceivedListener(new TorrentListReceivedListener() {
			@Override
			public void rpcTorrentListReceived(List<?> listTorrents) {
				addTorrents(listTorrents);
			}
		});

		rpc.addSessionSettingsReceivedListener(this);

		VuzeRemoteApp.getNetworkState().addListener(this);
	}

	/* (non-Javadoc)
	 * @see com.vuze.android.remote.rpc.SessionSettingsReceivedListener#sessionPropertiesUpdated(java.util.Map)
	 */
	@Override
	public void sessionPropertiesUpdated(Map<?, ?> map) {
		SessionSettings settings = new SessionSettings();
		settings.setDLIsAuto(MapUtils.getMapBoolean(map,
				"speed-limit-down-enabled", true));
		settings.setULIsAuto(MapUtils.getMapBoolean(map, "speed-limit-up-enabled",
				true));
		settings.setDownloadDir(MapUtils.getMapString(map, "download-dir", null));
		long refreshRateSecs = MapUtils.getMapLong(map, "refresh_rate", 0);
		long profileRefeshInterval = remoteProfile.getUpdateInterval();
		long newRefreshRate = refreshRateSecs == 0 && profileRefeshInterval > 0
				? profileRefeshInterval : refreshRateSecs;
		if (refreshRateSecs != profileRefeshInterval || sessionSettings == null) {
			settings.setRefreshIntervalEnabled(refreshRateSecs > 0);
		} else {
			settings.setRefreshIntervalEnabled(sessionSettings.isRefreshIntervalIsEnabled());
		}
		settings.setRefreshInterval(newRefreshRate);

		settings.setDlSpeed(MapUtils.getMapLong(map, "speed-limit-down", 0));
		settings.setUlSpeed(MapUtils.getMapLong(map, "speed-limit-up", 0));

		sessionSettings = settings;

		synchronized (sessionSettingsChangedListeners) {
			for (SessionSettingsChangedListener l : sessionSettingsChangedListeners) {
				l.sessionSettingsChanged(settings);
			}
		}

		if (!uiReady) {
			getRpc().simpleRpcCall("tags-get-list", new ReplyMapReceivedListener() {

				@Override
				public void rpcSuccess(String id, Map<?, ?> optionalMap) {
					List<?> tagList = MapUtils.getMapList(optionalMap, "tags", null);
					if (tagList == null) {
						mapTags = null;
						setUIReady();
						return;
					}
					mapTags = new HashMap<Long, Map<?, ?>>();
					for (Object tag : tagList) {
						if (tag instanceof Map) {
							Map<?, ?> mapTag = (Map<?, ?>) tag;
							mapTags.put(MapUtils.getMapLong(mapTag, "uid", 0), mapTag);
						}
					}
					setUIReady();
				}

				@Override
				public void rpcFailure(String id, String message) {
					setUIReady();
				}

				@Override
				public void rpcError(String id, Exception e) {
					setUIReady();
				}
			});

		}
	}

	private void setUIReady() {
		uiReady = true;
		initRefreshHandler();
		for (SessionInfoListener l : availabilityListeners) {
			l.uiReady();
		}
	}

	public Map<?, ?> getTag(Long uid) {
		if (mapTags == null) {
			return null;
		}
		// TODO: if .get is null, maybe repull tag list?
		return mapTags.get(uid);
	}

	public List<Map<?, ?>> getTags() {
		if (mapTags == null) {
			return null;
		}
		return new ArrayList<Map<?, ?>>(mapTags.values());
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

	/*
	public LinkedHashMap<Object, Map<?, ?>> getTorrentList() {
		synchronized (mLock) {
			return new LinkedHashMap<Object, Map<?, ?>>(mapOriginal);
		}
	}
	*/

	public List<Map<?, ?>> getTorrentList() {
		synchronized (mLock) {
			return new LinkedList<Map<?, ?>>(mapOriginal.values());
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

	@SuppressWarnings({
		"rawtypes",
		"unchecked"
	})
	public void addTorrents(List<?> collection) {
		System.out.println("adding torrents " + collection.size());
		synchronized (mLock) {
			for (Object item : collection) {
				if (!(item instanceof Map)) {
					continue;
				}
				Map mapTorrent = (Map) item;
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
							System.out.println(key + " missing " + torrentKey);
							mapTorrent.put(torrentKey, old.get(torrentKey));
						}
					}
				}
			}
		}

		TorrentListReceivedListener[] listeners = getTorrentListReceivedListeners();
		for (TorrentListReceivedListener l : listeners) {
			l.rpcTorrentListReceived(collection);
		}
	}

	public void addTorrentListReceivedListener(TorrentListReceivedListener l) {
		synchronized (torrentListReceivedListeners) {
			if (torrentListReceivedListeners.contains(l)) {
				return;
			}
			torrentListReceivedListeners.add(l);
			List<Map<?, ?>> torrentList = getTorrentList();
			if (torrentList.size() > 0) {
				l.rpcTorrentListReceived(torrentList);
			}
		}
	}

	public void removeTorrentListReceivedListener(TorrentListReceivedListener l) {
		synchronized (torrentListReceivedListeners) {
			torrentListReceivedListeners.remove(l);
		}
	}

	private TorrentListReceivedListener[] getTorrentListReceivedListeners() {
		synchronized (torrentListReceivedListeners) {
			return torrentListReceivedListeners.toArray(new TorrentListReceivedListener[0]);
		}
	}

	public void saveProfileIfRemember() {
		if (rememberSettingChanges) {
			AppPreferences appPreferences = VuzeRemoteApp.getAppPreferences();
			appPreferences.addRemoteProfile(remoteProfile);
		}
	}

	public void updateSessionSettings(SessionSettings newSettings) {
		SessionSettings originalSettings = getSessionSettings();

		if (newSettings.isRefreshIntervalIsEnabled() != originalSettings.isRefreshIntervalIsEnabled()
				|| newSettings.getRefreshInterval() != originalSettings.getRefreshInterval()) {
			remoteProfile.setUpdateInterval(newSettings.getRefreshInterval());
			remoteProfile.setUpdateIntervalEnabled(newSettings.isRefreshIntervalIsEnabled());
			saveProfileIfRemember();

			if (!newSettings.isRefreshIntervalIsEnabled()) {
				cancelRefreshHandler();
			} else {
				if (handler == null) {
					initRefreshHandler();
				}
			}
		}
		Map<String, Object> changes = new HashMap<String, Object>();
		if (newSettings.isDLAuto() != originalSettings.isDLAuto()) {
			changes.put("speed-limit-down-enabled", newSettings.isDLAuto());
		}
		if (newSettings.isULAuto() != originalSettings.isULAuto()) {
			changes.put("speed-limit-up-enabled", newSettings.isULAuto());
		}
		if (newSettings.getUlSpeed() != originalSettings.getUlSpeed()) {
			changes.put("speed-limit-up", newSettings.getUlSpeed());
		}
		if (newSettings.getDlSpeed() != originalSettings.getDlSpeed()) {
			changes.put("speed-limit-down", newSettings.getDlSpeed());
		}
		if (changes.size() > 0) {
			rpc.updateSettings(changes);
		}

		sessionSettings = newSettings;

		synchronized (sessionSettingsChangedListeners) {
			for (SessionSettingsChangedListener l : sessionSettingsChangedListeners) {
				l.sessionSettingsChanged(sessionSettings);
			}
		}
	}

	private void cancelRefreshHandler() {
		if (handler == null) {
			return;
		}
		handler.removeCallbacksAndMessages(null);
		handler = null;
	}

	public void initRefreshHandler() {
		if (AndroidUtils.DEBUG) {
			Log.d(TAG, "Init Handler");
		}
		if (handler != null) {
			return;
		}
		long interval = getRefreshInterval();
		if (AndroidUtils.DEBUG) {
			Log.d(TAG, "Handler fires every " + interval);
		}
		if (interval <= 0) {
			return;
		}
		handler = new Handler(Looper.getMainLooper());
		handler.postDelayed(new Runnable() {
			public void run() {
				if (AndroidUtils.DEBUG) {
					Log.d(TAG, "Fire Handler");
				}
				triggerRefresh(true);
				long interval = getRefreshInterval();
				if (AndroidUtils.DEBUG) {
					Log.d(TAG, "Handler fires in " + interval);
				}
				if (interval > 0) {
					handler.postDelayed(this, interval * 1000);
				}
			}
		}, interval * 1000);
	}

	public void triggerRefresh(boolean recentOnly) {
		if (AndroidUtils.DEBUG) {
			Log.d(TAG, "Refresh Triggered");
		}
		rpc.getSessionStats(new ReplyMapReceivedListener() {

			@Override
			public void rpcSuccess(String id, Map<?, ?> optionalMap) {
				updateSessionStats(optionalMap);
			}

			@Override
			public void rpcFailure(String id, String message) {
			}

			@Override
			public void rpcError(String id, Exception e) {
			}
		});
		if (recentOnly) {
			rpc.getRecentTorrents(null);
		} else {
			rpc.getAllTorrents(null);
		}
	}

	protected void updateSessionStats(Map<?, ?> map) {
		Map<?, ?> oldSessionStats = mapSessionStats;
		mapSessionStats = map;

/*
   string                     | value type
   ---------------------------+-------------------------------------------------
   "activeTorrentCount"       | number
   "downloadSpeed"            | number
   "pausedTorrentCount"       | number
   "torrentCount"             | number
   "uploadSpeed"              | number
   ---------------------------+-------------------------------+
   "cumulative-stats"         | object, containing:           |
                              +------------------+------------+
                              | uploadedBytes    | number     | tr_session_stats
                              | downloadedBytes  | number     | tr_session_stats
                              | filesAdded       | number     | tr_session_stats
                              | sessionCount     | number     | tr_session_stats
                              | secondsActive    | number     | tr_session_stats
   ---------------------------+-------------------------------+
   "current-stats"            | object, containing:           |
                              +------------------+------------+
                              | uploadedBytes    | number     | tr_session_stats
                              | downloadedBytes  | number     | tr_session_stats
                              | filesAdded       | number     | tr_session_stats
                              | sessionCount     | number     | tr_session_stats
                              | secondsActive    | number     | tr_session_stats
*/
		long oldDownloadSpeed = MapUtils.getMapLong(oldSessionStats,
				TransmissionVars.TR_SESSION_STATS_DOWNLOAD_SPEED, 0);
		long newDownloadSpeed = MapUtils.getMapLong(map,
				TransmissionVars.TR_SESSION_STATS_DOWNLOAD_SPEED, 0);
		long oldUploadSpeed = MapUtils.getMapLong(oldSessionStats,
				TransmissionVars.TR_SESSION_STATS_UPLOAD_SPEED, 0);
		long newUploadSpeed = MapUtils.getMapLong(map,
				TransmissionVars.TR_SESSION_STATS_UPLOAD_SPEED, 0);

		if (oldDownloadSpeed != newDownloadSpeed
				|| oldUploadSpeed != newUploadSpeed) {
			synchronized (sessionSettingsChangedListeners) {
				for (SessionSettingsChangedListener l : sessionSettingsChangedListeners) {
					l.speedChanged(newDownloadSpeed, newUploadSpeed);
				}
			}
		}
	}

	protected long getRefreshInterval() {
		boolean isUpdateIntervalEnabled = remoteProfile.isUpdateIntervalEnabled();
		long interval = remoteProfile.getUpdateInterval();
		if (sessionSettings != null) {
			sessionSettings.setRefreshIntervalEnabled(isUpdateIntervalEnabled);
			if (interval >= 0) {
				sessionSettings.setRefreshInterval(interval);
			}
		}
		if (!isUpdateIntervalEnabled) {
			interval = 0;
		}

		return interval;
	}

	public void addSessionSettingsChangedListeners(
			SessionSettingsChangedListener l) {
		synchronized (sessionSettingsChangedListeners) {
			if (!sessionSettingsChangedListeners.contains(l)) {
				sessionSettingsChangedListeners.add(l);
				if (sessionSettings != null) {
					l.sessionSettingsChanged(sessionSettings);
				}
			}
		}
	}

	public void removeSessionSettingsChangedListeners(
			SessionSettingsChangedListener l) {
		synchronized (sessionSettingsChangedListeners) {
			sessionSettingsChangedListeners.remove(l);
		}
	}

	public void addRpcAvailableListener(SessionInfoListener l) {
		if (availabilityListeners.contains(l)) {
			return;
		}
		availabilityListeners.add(l);
		if (rpc != null) {
			l.transmissionRpcAvailable(this);
		}
		if (uiReady) {
			l.uiReady();
		}
	}

	@Override
	public void onlineStateChanged(boolean isOnline) {
		if (isOnline || getRemoteProfile().isLocalHost()) {
			initRefreshHandler();
		} else {
			cancelRefreshHandler();
		}
	}

	@Override
	public void wifiConnectionChanged(boolean isWifiConnected) {
	}
}
