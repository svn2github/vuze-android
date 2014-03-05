package com.vuze.android.remote.fragment;

import java.util.List;

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
	implements SetTorrentIdListener
{
	private static final String TAG = "PeersFragment";

	private ListView listview;

	private PeersAdapter adapter;

	private long torrentID = -1;

	private SessionInfo sessionInfo;

	private TorrentIDGetter torrentIdGetter;

	public PeersFragment() {
		super();
	}

	public void setTorrentIdGetter(TorrentIDGetter torrentIdGetter) {
		this.torrentIdGetter = torrentIdGetter;
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
	public void onPause() {
		if (AndroidUtils.DEBUG) {
			Log.d(TAG, "onPause");
		}
		setTorrentID(sessionInfo, -1);

		super.onPause();
	}

	@Override
	public void onResume() {
		if (AndroidUtils.DEBUG) {
			Log.d(TAG, "onResume");
		}
		super.onResume();

		// fragment attached and instanciated, ok to setTorrentID now
		this.setTorrentID(torrentIdGetter.getSessionInfo(),
				torrentIdGetter.getTorrentID());
	}


	/* (non-Javadoc)
	 * @see com.vuze.android.remote.activity.SetTorrentIdListener#setTorrentID(com.vuze.android.remote.SessionInfo, long)
	 */
	public void setTorrentID(SessionInfo sessionInfo, long id) {
		if (torrentID != id && adapter != null) {
			adapter.clearList();
		}

		this.sessionInfo = sessionInfo;
		torrentID = id;

		if (adapter != null) {
			adapter.setSessionInfo(sessionInfo);
		}
		if (id < 0) {
			updateAdapterTorrentID(id);
			return;
		}
		sessionInfo.getRpc().getTorrentPeerInfo(id,
				new TorrentListReceivedListener() {
					@Override
					public void rpcTorrentListReceived(List<?> listTorrents) {
						updateAdapterTorrentID(torrentID);
					}
				});
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
		System.out.println("DS CHANGED Peer " + adapter);
	}

}
