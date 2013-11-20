package com.vuze.android.remote.rpc;

import java.util.*;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.auth.UsernamePasswordCredentials;

import android.util.Log;

import com.aelitis.azureus.util.MapUtils;
import com.vuze.android.remote.AndroidUtils;
import com.vuze.android.remote.TransmissionVars;

@SuppressWarnings("rawtypes")
public class TransmissionRPC
{

	private String rpcURL;

	private UsernamePasswordCredentials creds;

	protected Header[] headers;

	private int rpcVersion;

	private int rpcVersionAZ;

	private Boolean hasFileCountField = null;

	private List<String> basicTorrentFieldIDs;

	private Object mLock = new Object();

	public TransmissionRPC(String rpcURL, String username, String ac,
			final SessionSettingsReceivedListener l) {
		creds = new UsernamePasswordCredentials(username, ac);

		this.rpcURL = rpcURL;

		getSession(ac, new ReplyMapReceivedListener() {

			@Override
			public void rpcSuccess(String id, Map map) {
				rpcVersion = MapUtils.getMapInt(map, "rpc-version", -1);
				rpcVersionAZ = MapUtils.getMapInt(map, "az-rpc-version", -1);
				if (rpcVersionAZ < 0 && map.containsKey("az-version")) {
					rpcVersionAZ = 0;
				}
				if (rpcVersionAZ >= 2) {
					hasFileCountField = true;
				}
				l.sessionPropertiesUpdated(map);
			}

			@Override
			public void rpcFailure(String id, String message) {
			}

			@Override
			public void rpcError(String id, Exception e) {
			}
		});
	}

	public void getSession(String id, final ReplyMapReceivedListener l) {
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("method", "session-get");
		sendRequest(id, map, l);
	}

	public void addTorrentByUrl(String url, boolean addPaused,
			final TorrentAddedReceivedListener l) {
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("method", "torrent-add");

		Map<String, Object> mapArguments = new HashMap<String, Object>();
		map.put("arguments", mapArguments);
		mapArguments.put("paused", addPaused);
		mapArguments.put("filename", url);
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
		getTorrents(null, l);
	}

	public void getTorrents(Object ids, final TorrentListReceivedListener l) {

		Map<String, Object> map = new HashMap<String, Object>(2);
		map.put("method", "torrent-get");

		Map<String, Object> mapArguments = new HashMap<String, Object>();
		map.put("arguments", mapArguments);

		List<String> fields = getBasicTorrentFieldIDs();

		mapArguments.put("fields", fields);

		if (ids != null) {
			mapArguments.put("ids", ids);
		}

		sendRequest("whatever", map, new ReplyMapReceivedListener() {

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
								MapUtils.getMapList(map, TransmissionVars.TORRENT_FIELD_WANTED,
										Collections.EMPTY_LIST).size());
					}
				}
				l.rpcTorrentListReceived(list);
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
		if (id == null || data == null || l == null) {
			if (AndroidUtils.DEBUG) {
				System.err.println("sendRequest(" + id + "," + data + "," + l + ")");
			}
			return;
		}
		new Thread(new Runnable() {
			@SuppressWarnings("unchecked")
			@Override
			public void run() {
				data.put("random", Math.random());
				try {
					Map reply = RestJsonClient.connect(rpcURL, data, headers, creds);

					String result = MapUtils.getMapString(reply, "result", "");
					if (result.equals("success")) {
						l.rpcSuccess(id,
								MapUtils.getMapMap(reply, "arguments", Collections.EMPTY_MAP));
					} else {
						l.rpcFailure(id, result);
					}
				} catch (RPCException e) {
					HttpResponse httpResponse = e.getHttpResponse();
					if (httpResponse != null
							&& httpResponse.getStatusLine().getStatusCode() == 409) {
						if (AndroidUtils.DEBUG) {
							Log.d(null, "409: retrying");
						}
						Header header = httpResponse.getFirstHeader("X-Transmission-Session-Id");
						headers = new Header[] {
							header
						};
						sendRequest(id, data, l);
						return;
					}
					l.rpcError(id, e);
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
			basicTorrentFieldIDs.add(TransmissionVars.TORRENT_FIELD_STATUS); // TransmissionVars.TR_STATUS_*
		}

		List<String> fields = new ArrayList<String>(basicTorrentFieldIDs);
		if (hasFileCountField == null) {
			fields.add(TransmissionVars.TORRENT_FIELD_FILE_COUNT); // azRPC 2+
			fields.add(TransmissionVars.TORRENT_FIELD_WANTED); // for filesCount
		} else if (hasFileCountField) {
			fields.add(TransmissionVars.TORRENT_FIELD_FILE_COUNT); // azRPC 2+
		} else {
			fields.add(TransmissionVars.TORRENT_FIELD_WANTED); // for filesCount
		}

		return fields;
	}

	public void getRecentTorrents(TorrentListReceivedListener l) {
		getTorrents("recently-active", l);
	}

}
