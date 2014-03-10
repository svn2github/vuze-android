package com.vuze.android.remote.fragment;

import java.util.List;
import java.util.Map;

import android.app.Activity;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.view.ViewPager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import com.aelitis.azureus.util.MapUtils;
import com.astuetz.PagerSlidingTabStrip;
import com.vuze.android.remote.*;
import com.vuze.android.remote.activity.TorrentDetailsActivity;
import com.vuze.android.remote.activity.TorrentViewActivity;
import com.vuze.android.remote.dialog.DialogFragmentDeleteTorrent;

/**
 * Torrent Details Fragment<br>
 * - Contains {@link PeersFragment}, {@link FilesFragment}<br>
 * - Contained in {@link TorrentViewActivity} for wide screens<br>
 * - Contained in {@link TorrentDetailsActivity} for narrow screens<br>
 */
public class TorrentDetailsFragment
	extends Fragment
{
	ViewPager mViewPager;

	private TorrentDetailsPagerAdapter pagerAdapter;

	private SessionInfo sessionInfo;

	private long torrentID;

	public View onCreateView(android.view.LayoutInflater inflater,
			android.view.ViewGroup container, Bundle savedInstanceState) {

		View view = inflater.inflate(R.layout.frag_torrent_details, container,
				false);

		pagerAdapter = new TorrentDetailsPagerAdapter(getFragmentManager());
		mViewPager = (ViewPager) view.findViewById(R.id.pager);

		// Bind the tabs to the ViewPager
		PagerSlidingTabStrip tabs = (PagerSlidingTabStrip) view.findViewById(R.id.pager_title_strip);

		mViewPager.setAdapter(pagerAdapter);
		tabs.setViewPager(mViewPager);

		setHasOptionsMenu(true);

		return view;
	}

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);

		final Bundle extras = getActivity().getIntent().getExtras();
		if (extras == null) {
			System.err.println("No extras!");
			return;
		}

		String id = extras.getString(SessionInfoManager.BUNDLE_KEY);
		sessionInfo = SessionInfoManager.getSessionInfo(id, activity, true);
	}

	// Called from Activity
	public void setTorrentIDs(long[] newIDs) {
		this.torrentID = newIDs != null && newIDs.length == 1 ? newIDs[0] : -1;
		pagerAdapter.setSelection(torrentID);
		getActivity().runOnUiThread(new Runnable() {
			public void run() {
				List<Fragment> fragments = getFragmentManager().getFragments();
				for (Fragment item : fragments) {
  				if (item instanceof SetTorrentIdListener) {
  					((SetTorrentIdListener) item).setTorrentID(torrentID);
  				}
				}
			}
		});
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (handleMenu(item.getItemId())) {
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	protected boolean handleMenu(int itemId) {
		if (sessionInfo == null || torrentID < 0) {
			return false;
		}
		switch (itemId) {
			case R.id.action_sel_remove: {
				Map<?, ?> map = sessionInfo.getTorrent(torrentID);
				long id = MapUtils.getMapLong(map, "id", -1);
				String name = MapUtils.getMapString(map, "name", "");
				DialogFragmentDeleteTorrent.open(getFragmentManager(), name, id);
				return true;
			}
			case R.id.action_sel_start: {
				sessionInfo.getRpc().startTorrents(new long[] {
					torrentID
				}, false, null);
				return true;
			}
			case R.id.action_sel_forcestart: {
				sessionInfo.getRpc().startTorrents(new long[] {
					torrentID
				}, true, null);
				return true;
			}
			case R.id.action_sel_stop: {
				sessionInfo.getRpc().stopTorrents(new long[] {
					torrentID
				}, null);
				return true;
			}
			case R.id.action_sel_relocate:
				AndroidUtils.openMoveDataDialog(sessionInfo.getTorrent(torrentID),
						sessionInfo, getFragmentManager());
				return true;
			case R.id.action_sel_move_top: {
				sessionInfo.getRpc().simpleRpcCall("queue-move-top", new long[] {
					torrentID
				}, null);
				return true;
			}
			case R.id.action_sel_move_up: {
				sessionInfo.getRpc().simpleRpcCall("queue-move-up", new long[] {
					torrentID
				}, null);
				return true;
			}
			case R.id.action_sel_move_down: {
				sessionInfo.getRpc().simpleRpcCall("queue-move-down", new long[] {
					torrentID
				}, null);
				return true;
			}
			case R.id.action_sel_move_bottom: {
				sessionInfo.getRpc().simpleRpcCall("queue-move-bottom", new long[] {
					torrentID
				}, null);
				return true;
			}
		}
		return false;
	}

	@Override
	public void onPrepareOptionsMenu(Menu menu) {
		super.onPrepareOptionsMenu(menu);

		if (sessionInfo == null || torrentID < 0) {
			return;
		}
		Map<?, ?> torrent = sessionInfo.getTorrent(torrentID);
		int status = MapUtils.getMapInt(torrent,
				TransmissionVars.FIELD_TORRENT_STATUS,
				TransmissionVars.TR_STATUS_STOPPED);
		boolean canStart = status == TransmissionVars.TR_STATUS_STOPPED;
		boolean canStop = status != TransmissionVars.TR_STATUS_STOPPED;
		MenuItem menuStart = menu.findItem(R.id.action_sel_start);
		if (menuStart != null) {
			menuStart.setVisible(canStart);
		}

		MenuItem menuStop = menu.findItem(R.id.action_sel_stop);
		if (menuStop != null) {
			menuStop.setVisible(canStop);
		}

		AndroidUtils.fixupMenuAlpha(menu);
	}
}
