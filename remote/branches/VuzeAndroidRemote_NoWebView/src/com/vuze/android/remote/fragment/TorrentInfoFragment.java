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

public class TorrentInfoFragment
	extends Fragment
	implements SetTorrentIdListener, RefreshTriggerListener
{
	private static final String TAG = "TorrentInfoFragment";

	private long torrentID = -1;

	private SessionInfo sessionInfo;

	private long pausedTorrentID = -1;

	public TorrentInfoFragment() {
		super();
	}

	public View onCreateView(android.view.LayoutInflater inflater,
			android.view.ViewGroup container, Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.frag_torrent_peers, container, false);
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

		if (sessionInfo != null) {
			sessionInfo.removeRefreshTriggerListener(this);
		}

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

		torrentID = id;
	}

	@Override
	public void triggerRefresh() {
		// Right now, all the tabs are built, so even if we are on TorrentInfo,
		// Files view is still built and firing off it's own refresh
	}
}
