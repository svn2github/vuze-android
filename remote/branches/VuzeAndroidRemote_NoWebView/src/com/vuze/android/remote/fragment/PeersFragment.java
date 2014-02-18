package com.vuze.android.remote.fragment;

import java.util.List;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.View;
import android.widget.ListView;

import com.vuze.android.remote.R;
import com.vuze.android.remote.SessionInfo;
import com.vuze.android.remote.SetTorrentIdListener;
import com.vuze.android.remote.rpc.TorrentListReceivedListener;

public class PeersFragment
	extends Fragment
	implements SetTorrentIdListener
{
	private ListView listview;

	private PeersAdapter adapter;

	private long torrentID = -1;

	private SessionInfo sessionInfo;

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

		if (torrentID >= 0) {
			adapter.setTorrentID(torrentID);
		}
	}

	public View onCreateView(android.view.LayoutInflater inflater,
			android.view.ViewGroup container, Bundle savedInstanceState) {

		View view = inflater.inflate(R.layout.frag_torrent_peers, container, false);

		setHasOptionsMenu(true);

		listview = (ListView) view.findViewById(R.id.peers_list);

		listview.setItemsCanFocus(false);
		listview.setClickable(true);
		listview.setChoiceMode(ListView.CHOICE_MODE_SINGLE);

		return view;
	}

	/* (non-Javadoc)
	 * @see com.vuze.android.remote.activity.SetTorrentIdListener#setTorrentID(com.vuze.android.remote.SessionInfo, long)
	 */
	public void setTorrentID(SessionInfo sessionInfo, long id) {
		this.sessionInfo = sessionInfo;
		torrentID = id;
		
		//Map<?, ?> torrent = sessionInfo.getTorrent(id);
		//System.out.println("torrent is " + torrent);
		if (adapter != null) {
			adapter.setSessionInfo(sessionInfo);
		}
		sessionInfo.getRpc().getTorrentPeerInfo(id,
				new TorrentListReceivedListener() {
					@Override
					public void rpcTorrentListReceived(List listTorrents) {
						if (adapter != null) {
							getActivity().runOnUiThread(new Runnable() {
								@Override
								public void run() {
									adapter.setTorrentID(torrentID);
								}
							});
						}
						System.out.println("DS CHANGED Peer " + adapter);
					}
				});
	}

}
