package com.vuze.android.remote.rpc;

import java.util.*;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.auth.UsernamePasswordCredentials;

import android.util.Log;

import com.aelitis.azureus.util.JSONUtils;
import com.aelitis.azureus.util.MapUtils;
import com.vuze.android.remote.AndroidUtils;
import com.vuze.android.remote.SessionInfo;
import com.vuze.android.remote.TransmissionVars;

@SuppressWarnings("rawtypes")
public class TransmissionRPC
{
	private final class ReplyMapReceivedListenerWithRefresh
		implements ReplyMapReceivedListener
	{
		private final ReplyMapReceivedListener l;

		private final long[] ids;

		private ReplyMapReceivedListenerWithRefresh(ReplyMapReceivedListener l,
				long[] ids) {
			this.l = l;
			this.ids = ids;
		}

		@Override
		public void rpcSuccess(String id, Map optionalMap) {
			getTorrents(ids, getBasicTorrentFieldIDs(), null);
			if (l != null) {
				l.rpcSuccess(id, optionalMap);
			}
		}

		@Override
		public void rpcFailure(String id, String message) {
			if (l != null) {
				l.rpcFailure(id, message);
			}
		}

		@Override
		public void rpcError(String id, Exception e) {
			if (l != null) {
				l.rpcError(id, e);
			}
		}
	}

	private static final String TAG = "RPC";

	private String rpcURL;

	private UsernamePasswordCredentials creds;

	protected Header[] headers;

	private int rpcVersion;

	private int rpcVersionAZ;

	private Boolean hasFileCountField = null;

	private List<String> basicTorrentFieldIDs;

	private List<TorrentListReceivedListener> torrentListReceivedListeners = new ArrayList<TorrentListReceivedListener>();

	private List<SessionSettingsReceivedListener> sessionSettingsReceivedListeners = new ArrayList<SessionSettingsReceivedListener>();

	protected Map latestSessionSettings;

	public TransmissionRPC(String rpcURL, String username, String ac) {
		if (username != null) {
			creds = new UsernamePasswordCredentials(username, ac);
		}

		this.rpcURL = rpcURL;

		updateSessionSettings(ac);
	}

	public void getSessionStats(ReplyMapReceivedListener l) {
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("method", "session-stats");
		sendRequest("session-stats", map, l);
	}

	private void updateSessionSettings(String id) {
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("method", "session-get");
		sendRequest(id, map, new ReplyMapReceivedListener() {

			@Override
			public void rpcSuccess(String id, Map map) {
				synchronized (sessionSettingsReceivedListeners) {
					latestSessionSettings = map;
					rpcVersion = MapUtils.getMapInt(map, "rpc-version", -1);
					rpcVersionAZ = MapUtils.getMapInt(map, "az-rpc-version", -1);
					if (rpcVersionAZ < 0 && map.containsKey("az-version")) {
						rpcVersionAZ = 0;
					}
					if (rpcVersionAZ >= 1) { // TODO: 2
						hasFileCountField = true;
					}
					if (AndroidUtils.DEBUG) {
						Log.d(TAG, "Received Session-Get. " + map);
					}
					for (SessionSettingsReceivedListener l : sessionSettingsReceivedListeners) {
						l.sessionPropertiesUpdated(map);
					}
				}
			}

			@Override
			public void rpcFailure(String id, String message) {
			}

			@Override
			public void rpcError(String id, Exception e) {
			}
		});
	}

	public void addTorrentByUrl(String url, boolean addPaused,
			final TorrentAddedReceivedListener l) {
		addTorrent(false, url, addPaused, l);
	}

	public void addTorrentByMeta(String torrentData, boolean addPaused,
			final TorrentAddedReceivedListener l) {
		addTorrent(true, torrentData, addPaused, l);
	}

	private void addTorrent(boolean isTorrentData, String data,
			boolean addPaused, final TorrentAddedReceivedListener l) {
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("method", "torrent-add");

		Map<String, Object> mapArguments = new HashMap<String, Object>();
		map.put("arguments", mapArguments);
		mapArguments.put("paused", addPaused);
		if (isTorrentData) {
			mapArguments.put("metainfo", data);
		} else {
			mapArguments.put("filename", data);
		}
		//download-dir

		sendRequest("addTorrentByUrl", map, new ReplyMapReceivedListener() {

			@Override
			public void rpcSuccess(String id, Map optionalMap) {
				Map mapTorrentAdded = MapUtils.getMapMap(optionalMap, "torrent-added",
						null);
				if (mapTorrentAdded != null) {
					l.torrentAdded(mapTorrentAdded, false);
					return;
				}
				Map mapTorrentDupe = MapUtils.getMapMap(optionalMap,
						"torrent-duplicate", null);
				if (mapTorrentDupe != null) {
					l.torrentAdded(mapTorrentDupe, true);
					return;
				}
			}

			@Override
			public void rpcFailure(String id, String message) {
				l.torrentAddFailed(message);
			}

			@Override
			public void rpcError(String id, Exception e) {
				l.torrentAddError(e);
			}
		});
	}

	public void getAllTorrents(TorrentListReceivedListener l) {
		getTorrents(null, getBasicTorrentFieldIDs(), l);
	}

	private void getTorrents(Object ids, List<String> fields,
			final TorrentListReceivedListener l) {

		Map<String, Object> map = new HashMap<String, Object>(2);
		map.put("method", "torrent-get");

		Map<String, Object> mapArguments = new HashMap<String, Object>();
		map.put("arguments", mapArguments);

		mapArguments.put("fields", fields);

		if (ids != null) {
			mapArguments.put("ids", ids);
		}

		sendRequest("getTorrents " + ids, map, new ReplyMapReceivedListener() {

			@SuppressWarnings({
				"unchecked",
			})
			@Override
			public void rpcSuccess(String id, Map optionalMap) {
				List list = MapUtils.getMapList(optionalMap, "torrents",
						Collections.EMPTY_LIST);
				if (hasFileCountField == null || !hasFileCountField) {
					for (Object o : list) {
						if (!(o instanceof Map)) {
							continue;
						}
						Map map = (Map) o;
						if (map.containsKey(TransmissionVars.TORRENT_FIELD_FILE_COUNT)) {
							hasFileCountField = true;
							continue;
						}
						map.put(
								TransmissionVars.TORRENT_FIELD_FILE_COUNT,
								MapUtils.getMapList(map,
										TransmissionVars.TORRENT_FIELD_PRIORITIES,
										Collections.EMPTY_LIST).size());
					}
				}
				TorrentListReceivedListener[] listReceivedListeners = getTorrentListReceivedListeners();
				for (TorrentListReceivedListener torrentListReceivedListener : listReceivedListeners) {
					torrentListReceivedListener.rpcTorrentListReceived(list);
				}
				if (l != null) {
					l.rpcTorrentListReceived(list);
				}
			}

			@Override
			public void rpcFailure(String id, String message) {
			}

			@Override
			public void rpcError(String id, Exception e) {
			}
		});
	}

	private void sendRequest(final String id, final Map data,
			final ReplyMapReceivedListener l) {
		if (id == null || data == null) {
			if (AndroidUtils.DEBUG) {
				System.err.println("sendRequest(" + id + ","
						+ JSONUtils.encodeToJSON(data) + "," + l + ")");
			}
			return;
		}
		new Thread(new Runnable() {
			@SuppressWarnings("unchecked")
			@Override
			public void run() {
				data.put("random", Math.random());
				try {
					Map reply = RestJsonClient.connect(id, rpcURL, data, headers, creds);

					String result = MapUtils.getMapString(reply, "result", "");
					if (l != null) {
						if (result.equals("success")) {
							l.rpcSuccess(id,
									MapUtils.getMapMap(reply, "arguments", Collections.EMPTY_MAP));
						} else {
							if (AndroidUtils.DEBUG) {
								Log.d(null, id + "]rpcFailure: " + result);
							}
							l.rpcFailure(id, result);
						}
					}
				} catch (RPCException e) {
					HttpResponse httpResponse = e.getHttpResponse();
					if (httpResponse != null
							&& httpResponse.getStatusLine().getStatusCode() == 409) {
						if (AndroidUtils.DEBUG) {
							Log.d(TAG, "409: retrying");
						}
						Header header = httpResponse.getFirstHeader("X-Transmission-Session-Id");
						headers = new Header[] {
							header
						};
						sendRequest(id, data, l);
						return;
					}
					if (l != null) {
						l.rpcError(id, e);
					}
					if (AndroidUtils.DEBUG) {
						e.printStackTrace();
					}
				}
			}
		}, "sendRequest" + id).start();
	}

	public synchronized List<String> getBasicTorrentFieldIDs() {
		if (basicTorrentFieldIDs == null) {

			basicTorrentFieldIDs = new ArrayList<String>();
			basicTorrentFieldIDs.add(TransmissionVars.TORRENT_FIELD_ID);
			basicTorrentFieldIDs.add(TransmissionVars.TORRENT_FIELD_NAME);
			basicTorrentFieldIDs.add(TransmissionVars.TORRENT_FIELD_PERCENT_DONE);
			basicTorrentFieldIDs.add(TransmissionVars.TORRENT_FIELD_SIZE_WHEN_DONE);
			basicTorrentFieldIDs.add(TransmissionVars.TORRENT_FIELD_RATE_UPLOAD);
			basicTorrentFieldIDs.add(TransmissionVars.TORRENT_FIELD_RATE_DOWNLOAD);
			basicTorrentFieldIDs.add(TransmissionVars.TORRENT_FIELD_ERROR); // TransmissionVars.TR_STAT_*
			basicTorrentFieldIDs.add(TransmissionVars.TORRENT_FIELD_ERROR_STRING);
			basicTorrentFieldIDs.add(TransmissionVars.TORRENT_FIELD_ETA);
			basicTorrentFieldIDs.add(TransmissionVars.TORRENT_FIELD_POSITION);
			basicTorrentFieldIDs.add(TransmissionVars.TORRENT_FIELD_UPLOAD_RATIO);
			basicTorrentFieldIDs.add(TransmissionVars.TORRENT_FIELD_DATE_ADDED);
			basicTorrentFieldIDs.add("speedHistory");
			basicTorrentFieldIDs.add("leftUntilDone");
			basicTorrentFieldIDs.add("tag-uids");
			basicTorrentFieldIDs.add(TransmissionVars.TORRENT_FIELD_STATUS); // TransmissionVars.TR_STATUS_*
		}

		List<String> fields = new ArrayList<String>(basicTorrentFieldIDs);
		if (hasFileCountField == null) {
			fields.add(TransmissionVars.TORRENT_FIELD_FILE_COUNT); // azRPC 2+
			fields.add(TransmissionVars.TORRENT_FIELD_PRIORITIES); // for filesCount
		} else if (hasFileCountField) {
			fields.add(TransmissionVars.TORRENT_FIELD_FILE_COUNT); // azRPC 2+
		} else {
			fields.add(TransmissionVars.TORRENT_FIELD_PRIORITIES); // for filesCount
		}

		return fields;
	}

	public void getRecentTorrents(TorrentListReceivedListener l) {
		getTorrents("recently-active", getBasicTorrentFieldIDs(), l);
	}

	public void getTorrentFileInfo(Object ids, TorrentListReceivedListener l) {
		List<String> fieldIDs = getBasicTorrentFieldIDs();
		fieldIDs.add("files");
		fieldIDs.add("fileStats");

		getTorrents(ids, fieldIDs, l);
	}

	public void getTorrentPeerInfo(Object ids, TorrentListReceivedListener l) {
		List<String> fieldIDs = getBasicTorrentFieldIDs();
		fieldIDs.add("peers");

		getTorrents(ids, fieldIDs, l);
	}

	public void simpleRpcCall(String method, ReplyMapReceivedListener l) {
		simpleRpcCall(method, null, l);
	}

	public void simpleRpcCall(String method, long[] ids,
			ReplyMapReceivedListener l) {
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("method", method);
		if (ids != null) {
			Map<String, Object> mapArguments = new HashMap<String, Object>();
			map.put("arguments", mapArguments);
			mapArguments.put("ids", ids);
		}
		sendRequest(method, map, l);
	}

	public void startTorrents(long[] ids, boolean forceStart,
			ReplyMapReceivedListener l) {
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("method", forceStart ? "torrent-start-now" : "torrent-start");
		if (ids != null) {
			Map<String, Object> mapArguments = new HashMap<String, Object>();
			map.put("arguments", mapArguments);
			mapArguments.put("ids", ids);
		}
		sendRequest("startTorrents", map, new ReplyMapReceivedListenerWithRefresh(l,
				ids));
	}

	public void stopTorrents(final long[] ids, final ReplyMapReceivedListener l) {
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("method", "torrent-stop");
		if (ids != null) {
			Map<String, Object> mapArguments = new HashMap<String, Object>();
			map.put("arguments", mapArguments);
			mapArguments.put("ids", ids);
		}
		sendRequest("stopTorrents", map, new ReplyMapReceivedListenerWithRefresh(l,
				ids));
	}

	/**
	 * To ensure session torrent list is fully up to date, 
	 * you should be using {@link SessionInfo#addTorrentListReceivedListener(TorrentListReceivedListener)}
	 * instead of this one.
	 */
	public void addTorrentListReceivedListener(TorrentListReceivedListener l) {
		synchronized (torrentListReceivedListeners) {
			if (!torrentListReceivedListeners.contains(l)) {
				torrentListReceivedListeners.add(l);
			}
		}
	}

	public void removeTorrentListReceivedListener(TorrentListReceivedListener l) {
		synchronized (torrentListReceivedListeners) {
			torrentListReceivedListeners.remove(l);
		}
	}

	public void addSessionSettingsReceivedListener(
			SessionSettingsReceivedListener l) {
		synchronized (sessionSettingsReceivedListeners) {
			if (!sessionSettingsReceivedListeners.contains(l)) {
				sessionSettingsReceivedListeners.add(l);
				if (latestSessionSettings != null) {
					l.sessionPropertiesUpdated(latestSessionSettings);
				}
			}
		}
	}

	public void removeSessionSettingsReceivedListener(
			SessionSettingsReceivedListener l) {
		synchronized (sessionSettingsReceivedListeners) {
			sessionSettingsReceivedListeners.remove(l);
		}
	}

	public TorrentListReceivedListener[] getTorrentListReceivedListeners() {
		return torrentListReceivedListeners.toArray(new TorrentListReceivedListener[0]);
	}

	public void moveTorrent(long id, String newLocation,
			ReplyMapReceivedListener listener) {
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("method", "torrent-set-location");

		Map<String, Object> mapArguments = new HashMap<String, Object>();
		map.put("arguments", mapArguments);

		mapArguments.put("ids", new long[] {
			id
		});
		mapArguments.put("move", true);
		mapArguments.put("location", newLocation);

		sendRequest("torrent-set-location", map, listener);
	}

	public void removeTorrent(Object id, boolean deleteData,
			ReplyMapReceivedListener listener) {
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("method", "torrent-remove");

		Map<String, Object> mapArguments = new HashMap<String, Object>();
		map.put("arguments", mapArguments);

		if (id instanceof Object[] || id instanceof Collection) {
			mapArguments.put("ids", id);
		} else {
			mapArguments.put("ids", new Object[] {
				id
			});
		}
		mapArguments.put("delete-local-data", deleteData);

		sendRequest("torrent-remove", map, listener);
	}

	public void updateSettings(Map<String, Object> changes) {
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("method", "session-set");

		map.put("arguments", changes);

		sendRequest("session-get", map, null);
	}

	public int getRPCVersion() {
		return rpcVersion;
	}

	public int getRPCVersionAZ() {
		return rpcVersionAZ;
	}

}
