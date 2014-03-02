package com.vuze.android.remote.fragment;

import java.util.Map;

import android.app.Activity;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.view.PagerTabStrip;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.view.*;

import com.aelitis.azureus.util.MapUtils;
import com.vuze.android.remote.*;
import com.vuze.android.remote.activity.TorrentDetailsActivity;
import com.vuze.android.remote.activity.TorrentViewActivity;
import com.vuze.android.remote.dialog.DialogFragmentDeleteTorrent;
import com.vuze.android.remote.fragment.TorrentListFragment.OnTorrentSelectedListener;

/**
 * Torrent Details Fragment<br>
 * - Contains {@link PeersFragment}, {@link FilesFragment}<br>
 * - Contained in {@link TorrentViewActivity} for wide screens<br>
 * - Contained in {@link TorrentDetailsActivity} for narrow screens<br>
 */
public class TorrentDetailsFragment
	extends Fragment implements SessionInfoListener
{
	ViewPager mViewPager;

	private TorrentDetailsPagerAdapter pagerAdapter;

	private SessionInfo sessionInfo;

	private long[] torrentIDs;

	public View onCreateView(android.view.LayoutInflater inflater,
			android.view.ViewGroup container, Bundle savedInstanceState) {

		View view = inflater.inflate(R.layout.frag_torrent_details, container,
				false);

		pagerAdapter = new TorrentDetailsPagerAdapter(getFragmentManager());
		mViewPager = (ViewPager) view.findViewById(R.id.pager);
		mViewPager.setAdapter(pagerAdapter);

		mViewPager.setOnPageChangeListener(new OnPageChangeListener() {

			@Override
			public void onPageScrollStateChanged(int arg0) {
			}

			@Override
			public void onPageScrolled(int arg0, float arg1, int arg2) {
			}

			@Override
			public void onPageSelected(int arg0) {
				setTorrentIDs(torrentIDs);
			}
		});

		PagerTabStrip pts = (PagerTabStrip) view.findViewById(R.id.pager_title_strip);
		pts.setTextSpacing(0);

		setHasOptionsMenu(false);

		return view;
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		int currentItem = mViewPager.getCurrentItem();
		System.out.println("1currentItem = " + currentItem);
		Fragment item = pagerAdapter.getFragment(currentItem);
		System.out.println("1frag = " + item);

	}

	public void setTorrentIDs(long[] newIDs) {
		this.torrentIDs = newIDs;
		getActivity().runOnUiThread(new Runnable() {
			public void run() {
				if (torrentIDs == null || torrentIDs.length != 1) {
					torrentIDs = null;
					setHasOptionsMenu(false);
				} else {
					setHasOptionsMenu(true);
					pagerAdapter.setSelection(sessionInfo, torrentIDs[0]);
					int currentItem = mViewPager.getCurrentItem();
					System.out.println("currentItem = " + currentItem);
					Fragment item = pagerAdapter.getFragment(currentItem);
					System.out.println("frag = " + item);
					if (item instanceof SetTorrentIdListener) {
						((SetTorrentIdListener) item).setTorrentID(sessionInfo, torrentIDs[0]);
					}
				}
			}
		});
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		inflater.inflate(R.menu.menu_context_torrent_details, menu);
		System.out.println("td.onCreateOptionsMenu");
		super.onCreateOptionsMenu(menu, inflater);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (handleMenu(item.getItemId())) {
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	protected boolean handleMenu(int itemId) {
		if (sessionInfo == null || torrentIDs == null) {
			return false;
		}
		switch (itemId) {
			case R.id.action_sel_remove: {
				Map<?, ?> map = sessionInfo.getTorrent(torrentIDs[0]);
				long id = MapUtils.getMapLong(map, "id", -1);
				String name = MapUtils.getMapString(map, "name", "");
				DialogFragmentDeleteTorrent.open(getFragmentManager(), name, id);
				return true;
			}
			case R.id.action_sel_start: {
				sessionInfo.getRpc().startTorrents(torrentIDs, false, null);
				return true;
			}
			case R.id.action_sel_forcestart: {
				sessionInfo.getRpc().startTorrents(torrentIDs, true, null);
				return true;
			}
			case R.id.action_sel_stop: {
				sessionInfo.getRpc().stopTorrents(torrentIDs, null);
				return true;
			}
			case R.id.action_sel_relocate:
				AndroidUtils.openMoveDataDialog(sessionInfo.getTorrent(torrentIDs[0]),
						sessionInfo, getFragmentManager());
				return true;
			case R.id.action_sel_move_top: {
				sessionInfo.getRpc().simpleRpcCall("queue-move-top", torrentIDs, null);
				return true;
			}
			case R.id.action_sel_move_up: {
				sessionInfo.getRpc().simpleRpcCall("queue-move-up", torrentIDs, null);
				return true;
			}
			case R.id.action_sel_move_down: {
				sessionInfo.getRpc().simpleRpcCall("queue-move-down", torrentIDs, null);
				return true;
			}
			case R.id.action_sel_move_bottom: {
				sessionInfo.getRpc().simpleRpcCall("queue-move-bottom", torrentIDs, null);
				return true;
			}
		}
		return false;
	}

	@Override
	public void onPrepareOptionsMenu(Menu menu) {
		super.onPrepareOptionsMenu(menu);

		if (sessionInfo == null || torrentIDs == null) {
			return;
		}
		Map<?, ?> torrent = sessionInfo.getTorrent(torrentIDs[0]);
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

	@Override
	public void transmissionRpcAvailable(SessionInfo _sessionInfo) {
		sessionInfo = _sessionInfo;
		if (torrentIDs != null) {
			setTorrentIDs(torrentIDs);
		}
	}

	@Override
	public void uiReady() {
	}
}
