package com.vuze.android.remote.activity;

import java.util.List;
import java.util.Map;

import org.json.JSONObject;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.View;
import android.widget.ListView;

import com.vuze.android.remote.R;
import com.vuze.android.remote.SessionInfo;
import com.vuze.android.remote.rpc.TorrentListReceivedListener;

public class FilesFragment
	extends Fragment implements SetTorrentIdListener
{
	private ListView listview;

	private FilesAdapter adapter;

	private long torrentID = -1;

	private SessionInfo sessionInfo;
	
	public FilesFragment() {
		super();
	}
	
	
	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		
		adapter = new FilesAdapter(this.getActivity());
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

		View view = inflater.inflate(R.layout.frag_torrent_files, container, false);

		setHasOptionsMenu(true);

		listview = (ListView) view.findViewById(R.id.files_list);

		listview.setItemsCanFocus(false);
		listview.setClickable(true);
		listview.setChoiceMode(ListView.CHOICE_MODE_SINGLE);


		return view;
	}

	/* (non-Javadoc)
	 * @see com.vuze.android.remote.activity.SetTorrentIdListener#setTorrentID(com.vuze.android.remote.SessionInfo, long)
	 */
	@Override
	public void setTorrentID(SessionInfo sessionInfo, long id) {
		this.sessionInfo = sessionInfo;
		torrentID  = id;
		Map<?, ?> torrent = sessionInfo.getTorrent(id);
		//System.out.println("torrent is " + torrent);
		if (adapter != null) {
			adapter.setSessionInfo(sessionInfo);
		}
		sessionInfo.getRpc().getTorrentFileInfo(id, new TorrentListReceivedListener() {
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
				System.out.println("DS CHANGED FILE " + adapter);
			}
		});
	}

}
