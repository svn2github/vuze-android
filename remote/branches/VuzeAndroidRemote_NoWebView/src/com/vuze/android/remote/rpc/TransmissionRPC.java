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

		private List<String> fields;

		private int[] fileIndexes;

		private List<String> fileFields;

		private ReplyMapReceivedListenerWithRefresh(ReplyMapReceivedListener l,
				long[] ids) {
			this.l = l;
			this.ids = ids;
			this.fields = getBasicTorrentFieldIDs();
		}

		private ReplyMapReceivedListenerWithRefresh(ReplyMapReceivedListener l,
				long[] ids, List<String> fields) {
			this.l = l;
			this.ids = ids;
			this.fields = fields;
		}

		public ReplyMapReceivedListenerWithRefresh(ReplyMapReceivedListener l,
				long[] torrentIDs, int[] fileIndexes, List<String> fileFields) {
			this.l = l;
			this.ids = torrentIDs;
			this.fileIndexes = fileIndexes;
			this.fileFields = fileFields;
			this.fields = getFileInfoFields();
		}

		@Override
		public void rpcSuccess(String id, Map optionalMap) {
			new Thread(new Runnable() {
				@Override
				public void run() {
					try {
						Thread.sleep(500);
					} catch (InterruptedException e) {
					}

					getTorrents(ids, fields, fileIndexes, fileFields, null);
				}
			}).start();
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

	// From Transmission's rpcimp.c :(
	// #define RECENTLY_ACTIVE_SECONDS 60
	protected static final long RECENTLY_ACTIVE_MS = 60 * 1000l;

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

	protected long lastRecentTorrentGet;

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
		getTorrents(null, getBasicTorrentFieldIDs(), null, null, l);
	}

	private void getTorrents(final Object ids, List<String> fields,
			final int[] fileIndexes, final List<String> fileFields,
			final TorrentListReceivedListener l) {

		Map<String, Object> map = new HashMap<String, Object>(2);
		map.put("method", "torrent-get");

		Map<String, Object> mapArguments = new HashMap<String, Object>();
		map.put("arguments", mapArguments);

		mapArguments.put("fields", fields);

		if (fileIndexes != null) {
			mapArguments.put("file-indexes", fileIndexes);
		}

		if (fileFields != null) {
			mapArguments.put("file-fields", fileFields);
		}

		if (ids != null) {
			mapArguments.put("ids", ids);
		}

		String idList = (ids instanceof long[]) ? Arrays.toString(((long[]) ids))
				: "" + ids;
		sendRequest(
				"getTorrents t=" + idList + "/f=" + Arrays.toString(fileIndexes) + ", "
						+ (fields == null ? "null" : fields.size()) + "/"
						+ (fileFields == null ? "null" : fileFields.size()), map,
				new ReplyMapReceivedListener() {

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
								if (map.containsKey(TransmissionVars.FIELD_TORRENT_FILE_COUNT)) {
									hasFileCountField = true;
									continue;
								}
								map.put(
										TransmissionVars.FIELD_TORRENT_FILE_COUNT,
										MapUtils.getMapList(map,
												TransmissionVars.FIELD_TORRENT_PRIORITIES,
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
						// send event to listeners on fail/error
						// some do a call for a specific torrentID and rely on a response 
						// of some sort to clean up (ie. files view progress bar), so
						// we must fake a reply with those torrentIDs

						List list = createFakeList(ids);

						TorrentListReceivedListener[] listReceivedListeners = getTorrentListReceivedListeners();
						for (TorrentListReceivedListener torrentListReceivedListener : listReceivedListeners) {
							torrentListReceivedListener.rpcTorrentListReceived(list);
						}
						if (l != null) {
							l.rpcTorrentListReceived(list);
						}
					}

					private List createFakeList(Object ids) {
						List<Map> list = new ArrayList<Map>();
						if (ids instanceof Long) {
							HashMap<String, Object> map = new HashMap<String, Object>(2);
							map.put("id", ids);
							list.add(map);
							return list;
						}
						if (ids instanceof long[]) {
							for (long torrentID : (long[]) ids) {
								HashMap<String, Object> map = new HashMap<String, Object>(2);
								map.put("id", torrentID);
								list.add(map);
							}
						}
						return list;
					}

					@Override
					public void rpcError(String id, Exception e) {
						// send event to listeners on fail/error
						// some do a call for a specific torrentID and rely on a response 
						// of some sort to clean up (ie. files view progress bar), so
						// we must fake a reply with those torrentIDs
						
						List list = createFakeList(ids);

						TorrentListReceivedListener[] listReceivedListeners = getTorrentListReceivedListeners();
						for (TorrentListReceivedListener torrentListReceivedListener : listReceivedListeners) {
							torrentListReceivedListener.rpcTorrentListReceived(list);
						}
						if (l != null) {
							l.rpcTorrentListReceived(list);
						}
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
			basicTorrentFieldIDs.add(TransmissionVars.FIELD_TORRENT_ID);
			basicTorrentFieldIDs.add(TransmissionVars.FIELD_TORRENT_NAME);
			basicTorrentFieldIDs.add(TransmissionVars.FIELD_TORRENT_PERCENT_DONE);
			basicTorrentFieldIDs.add(TransmissionVars.FIELD_TORRENT_SIZE_WHEN_DONE);
			basicTorrentFieldIDs.add(TransmissionVars.FIELD_TORRENT_RATE_UPLOAD);
			basicTorrentFieldIDs.add(TransmissionVars.FIELD_TORRENT_RATE_DOWNLOAD);
			basicTorrentFieldIDs.add(TransmissionVars.FIELD_TORRENT_ERROR); // TransmissionVars.TR_STAT_*
			basicTorrentFieldIDs.add(TransmissionVars.FIELD_TORRENT_ERROR_STRING);
			basicTorrentFieldIDs.add(TransmissionVars.FIELD_TORRENT_ETA);
			basicTorrentFieldIDs.add(TransmissionVars.FIELD_TORRENT_POSITION);
			basicTorrentFieldIDs.add(TransmissionVars.FIELD_TORRENT_UPLOAD_RATIO);
			basicTorrentFieldIDs.add(TransmissionVars.FIELD_TORRENT_DATE_ADDED);
			basicTorrentFieldIDs.add("speedHistory");
			basicTorrentFieldIDs.add("leftUntilDone");
			basicTorrentFieldIDs.add("tag-uids");
			basicTorrentFieldIDs.add(TransmissionVars.FIELD_TORRENT_STATUS); // TransmissionVars.TR_STATUS_*
		}

		List<String> fields = new ArrayList<String>(basicTorrentFieldIDs);
		if (hasFileCountField == null) {
			fields.add(TransmissionVars.FIELD_TORRENT_FILE_COUNT); // azRPC 2+
			fields.add(TransmissionVars.FIELD_TORRENT_PRIORITIES); // for filesCount
		} else if (hasFileCountField) {
			fields.add(TransmissionVars.FIELD_TORRENT_FILE_COUNT); // azRPC 2+
		} else {
			fields.add(TransmissionVars.FIELD_TORRENT_PRIORITIES); // for filesCount
		}

		return fields;
	}

	/**
	 * Get recently-active torrents, or all torrents if there are no recents
	 */
	public void getRecentTorrents(final TorrentListReceivedListener l) {
		getTorrents("recently-active", getBasicTorrentFieldIDs(), null, null,
				new TorrentListReceivedListener() {
					@Override
					public void rpcTorrentListReceived(List<?> listTorrents) {
						long diff = System.currentTimeMillis() - lastRecentTorrentGet;
						if (listTorrents.size() == 0) {
							if (diff >= RECENTLY_ACTIVE_MS) {
								getAllTorrents(l);
							}
						} else {
							lastRecentTorrentGet = System.currentTimeMillis();
						}
						if (l != null) {
							l.rpcTorrentListReceived(listTorrents);
						}
					}
				});
	}

	private List<String> getFileInfoFields() {
		List<String> fieldIDs = getBasicTorrentFieldIDs();
		fieldIDs.add("files");
		fieldIDs.add("fileStats");
		return fieldIDs;
	}

	public void getTorrentFileInfo(Object ids, int[] fileIndexes,
			List<String> fileFields, TorrentListReceivedListener l) {
		getTorrents(ids, getFileInfoFields(), fileIndexes, fileFields, l);
	}

	public void getTorrentPeerInfo(Object ids, TorrentListReceivedListener l) {
		List<String> fieldIDs = getBasicTorrentFieldIDs();
		fieldIDs.add("peers");

		getTorrents(ids, fieldIDs, null, null, l);
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
		sendRequest("startTorrents", map, new ReplyMapReceivedListenerWithRefresh(
				l, ids));
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

	public void setFilePriority(long torrentID, int[] fileIndexes, int priority,
			final ReplyMapReceivedListener l) {
		long[] ids = {
			torrentID
		};
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("method", "torrent-set");
		Map<String, Object> mapArguments = new HashMap<String, Object>();
		map.put("arguments", mapArguments);
		mapArguments.put("ids", ids);

		String key;
		switch (priority) {
			case TransmissionVars.TR_PRI_HIGH:
				key = "priority-high";
				break;

			case TransmissionVars.TR_PRI_NORMAL:
				key = "priority-normal";
				break;

			case TransmissionVars.TR_PRI_LOW:
				key = "priority-low";
				break;

			default:
				return;
		}

		mapArguments.put(key, fileIndexes);

		sendRequest("setFilePriority", map,
				new ReplyMapReceivedListenerWithRefresh(l, ids, fileIndexes, null));
	}

	public void setWantState(long torrentID, int[] fileIndexes, boolean wanted,
			final ReplyMapReceivedListener l) {
		long[] torrentIDs = {
			torrentID
		};
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("method", "torrent-set");
		Map<String, Object> mapArguments = new HashMap<String, Object>();
		map.put("arguments", mapArguments);
		mapArguments.put("ids", torrentIDs);
		mapArguments.put(wanted ? "files-wanted" : "files-unwanted", fileIndexes);

		sendRequest("setWantState", map, new ReplyMapReceivedListenerWithRefresh(l,
				torrentIDs, fileIndexes, null));
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
