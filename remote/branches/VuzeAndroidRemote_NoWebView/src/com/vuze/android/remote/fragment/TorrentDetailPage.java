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
 */

package com.vuze.android.remote.fragment;

import android.support.v4.app.Fragment;
import android.util.Log;

import com.vuze.android.remote.*;
import com.vuze.android.remote.fragment.TorrentDetailsPagerAdapter.PagerPosition;
import com.vuze.android.remote.rpc.TorrentListReceivedListener;

public abstract class TorrentDetailPage
	extends Fragment
	implements SetTorrentIdListener, RefreshTriggerListener,
	TorrentListReceivedListener, PagerPosition, FragmentPagerListener
{

	private static final String TAG = "TorrentDetailPage";

	private int pagerPosition = -1;

	protected long torrentID = -1;

	protected SessionInfo sessionInfo;

	private long pausedTorrentID = -1;

	private boolean viewActive = true;

	@Override
	public void onPause() {
		if (AndroidUtils.DEBUG) {
			Log.d(TAG, "onPause");
		}
		pageDeactivated();

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

		pagerPosition = getArguments().getInt("pagerPosition", pagerPosition);

		pageActivated();
	}

	@Override
	public void setPagerPosition(int position) {
		pagerPosition = position;
	}

	@Override
	public int getPagerPosition() {
		return pagerPosition;
	}

	@Override
	public void pageDeactivated() {
		viewActive = false;
		if (torrentID != -1) {
			pausedTorrentID = torrentID;
			setTorrentID(-1);
		}

		if (sessionInfo != null) {
			sessionInfo.removeRefreshTriggerListener(this);
		}
	}

	@Override
	public void pageActivated() {
		viewActive = true;
		if (pausedTorrentID >= 0) {
			setTorrentID(pausedTorrentID);
		} else if (torrentID >= 0) {
			setTorrentID(torrentID);
		} else {
			long newTorrentID = getArguments().getLong("torrentID", -1);
			setTorrentID(newTorrentID);
		}

		if (sessionInfo != null) {
			sessionInfo.addRefreshTriggerListener(this);
		}
	}

	public final void setTorrentID(long id) {
		if (!viewActive) {
			if (AndroidUtils.DEBUG) {
				Log.e(TAG, "setTorrentID: view not Active");
			}
			pausedTorrentID = id;
			return;
		}
		if (getActivity() == null) {
			if (AndroidUtils.DEBUG) {
				Log.e(TAG, "setTorrentID: No Activity");
			}
			pausedTorrentID = id;
			return;
		}

		boolean wasTorrent = torrentID >= 0;
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
		
		updateTorrentID(torrentID, isTorrent, wasTorrent, torrentIdChanged);
	}

	/**
	 * SessionInfo will not be null
	 */
	public abstract void updateTorrentID(long torrentID, boolean isTorrent,
			boolean wasTorrent, boolean torrentIdChanged);

}
