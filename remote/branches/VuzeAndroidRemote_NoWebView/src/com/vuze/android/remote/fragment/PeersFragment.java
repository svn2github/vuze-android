package com.vuze.android.remote.fragment;

import java.util.List;

import android.app.Activity;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.View;
import android.widget.ListView;

import com.vuze.android.remote.*;
import com.vuze.android.remote.rpc.TorrentListReceivedListener;

public class PeersFragment
	extends Fragment
	implements SetTorrentIdListener, RefreshTriggerListener
{
	private static final String TAG = "PeersFragment";

	private ListView listview;

	private PeersAdapter adapter;

	private long torrentID = -1;

	private SessionInfo sessionInfo;

	private long pausedTorrentID = -1;

	public PeersFragment() {
		super();
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

		adapter = new PeersAdapter(this.getActivity());
		if (sessionInfo != null) {
			adapter.setSessionInfo(sessionInfo);
		}
		listview.setItemsCanFocus(true);
		listview.setAdapter(adapter);
	}

	public View onCreateView(android.view.LayoutInflater inflater,
			android.view.ViewGroup container, Bundle savedInstanceState) {

		View view = inflater.inflate(R.layout.frag_torrent_peers, container, false);

		listview = (ListView) view.findViewById(R.id.peers_list);

		listview.setItemsCanFocus(false);
		listview.setClickable(true);
		listview.setChoiceMode(ListView.CHOICE_MODE_SINGLE);

		setHasOptionsMenu(true);
		return view;
	}

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);

		if (activity instanceof SessionInfoGetter) {
			SessionInfoGetter getter = (SessionInfoGetter) activity;
			sessionInfo = getter.getSessionInfo();
		}
	}

	@Override
	public void onPause() {
		if (AndroidUtils.DEBUG) {
			Log.d(TAG, "onPause");
		}
		pausedTorrentID = torrentID;
		setTorrentID(-1);

		super.onPause();
	}

	@Override
	public void onResume() {
		if (AndroidUtils.DEBUG) {
			Log.d(TAG, "onResume " + this + ", pausedTorrentID=" + pausedTorrentID);
		}
		super.onResume();

		if (getActivity() instanceof SessionInfoGetter) {
			SessionInfoGetter getter = (SessionInfoGetter) getActivity();
			sessionInfo = getter.getSessionInfo();
		}

		if (sessionInfo != null) {
			sessionInfo.addRefreshTriggerListener(this);
		}

		if (pausedTorrentID >= 0) {
			setTorrentID(pausedTorrentID);
		} else if (torrentID >= 0) {
			setTorrentID(torrentID);
		} else {
			long newTorrentID = getArguments().getLong("torrentID", -1);
			setTorrentID(newTorrentID);
		}
	}

	/* (non-Javadoc)
	 * @see com.vuze.android.remote.activity.SetTorrentIdListener#setTorrentID(com.vuze.android.remote.SessionInfo, long)
	 */
	public void setTorrentID(long id) {
		if (getActivity() == null) {
			if (AndroidUtils.DEBUG) {
				Log.e(TAG, "setTorrentID: No Activity");
			}
			pausedTorrentID = id;
			return;
		}
		if (adapter == null) {
			if (AndroidUtils.DEBUG) {
				Log.e(TAG, "setTorrentID: No Adapter");
			}
			pausedTorrentID = id;
			return;
		}

		//boolean wasTorrent = torrentID >= 0;
		boolean isTorrent = id >= 0;
		boolean torrentIdChanged = id != torrentID;

		if (sessionInfo == null) {
			if (AndroidUtils.DEBUG) {
				Log.e(TAG, "setTorrentID: No sessionInfo");
			}
			pausedTorrentID = id;
			return;
		}
		if (torrentIdChanged) {
			adapter.clearList();
		}

		torrentID = id;

		//System.out.println("torrent is " + torrent);
		adapter.setSessionInfo(sessionInfo);
		if (isTorrent) {
			sessionInfo.getRpc().getTorrentPeerInfo(id,
					new TorrentListReceivedListener() {
						@Override
						public void rpcTorrentListReceived(List<?> listTorrents) {
							updateAdapterTorrentID(torrentID);
						}
					});
		}

		if (torrentIdChanged) {
			AndroidUtils.clearChecked(listview);
		}
	}

	private void updateAdapterTorrentID(long id) {
		if (adapter == null) {
			return;
		}
		FragmentActivity activity = getActivity();
		if (activity == null) {
			return;
		}
		activity.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				adapter.setTorrentID(torrentID);
			}
		});
	}

	@Override
	public void triggerRefresh() {
		sessionInfo.getRpc().getTorrentPeerInfo(torrentID,
				new TorrentListReceivedListener() {
					@Override
					public void rpcTorrentListReceived(List<?> listTorrents) {
						updateAdapterTorrentID(torrentID);
					}
				});
	}
}
