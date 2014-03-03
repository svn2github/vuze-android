package com.vuze.android.remote.fragment;

import java.util.Map;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.view.*;

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
	implements SessionInfoListener
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

		tabs.setOnPageChangeListener(new OnPageChangeListener() {

			private int currentItem;

			@Override
			public void onPageScrollStateChanged(int state) {
			}

			@Override
			public void onPageScrolled(int position, float arg1, int arg2) {
			}

			@Override
			public void onPageSelected(int position) {
				// Not called for first page 0 view

				// Set old page's torrent id to -1 so it doesn't update anymore
				if (position != currentItem) {
					Fragment item = pagerAdapter.getFragment(currentItem);
					if (item instanceof SetTorrentIdListener) {
						((SetTorrentIdListener) item).setTorrentID(sessionInfo, -1);
					}
				}

				if (torrentID < 0) {
					return;
				}
				currentItem = mViewPager.getCurrentItem();

				Fragment item = pagerAdapter.getFragment(currentItem);
				if (item instanceof SetTorrentIdListener) {
					((SetTorrentIdListener) item).setTorrentID(sessionInfo, torrentID);
				}

			}
		});

		mViewPager.setAdapter(pagerAdapter);
		tabs.setViewPager(mViewPager);

		setHasOptionsMenu(false);

		return view;
	}

	// Called from Activity
	public void setTorrentIDs(long[] newIDs) {
		this.torrentID = newIDs != null && newIDs.length == 1 ? newIDs[0] : -1;
		getActivity().runOnUiThread(new Runnable() {
			public void run() {
				boolean enable = torrentID >= 0;
				setHasOptionsMenu(enable);
				pagerAdapter.setSelection(sessionInfo, torrentID);

				int currentItem = mViewPager.getCurrentItem();
				Fragment item = pagerAdapter.getFragment(currentItem);
				if (item instanceof SetTorrentIdListener) {
					((SetTorrentIdListener) item).setTorrentID(sessionInfo, torrentID);
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

	@Override
	public void transmissionRpcAvailable(SessionInfo _sessionInfo) {
		sessionInfo = _sessionInfo;
	}

	@Override
	public void uiReady() {
	}
}
