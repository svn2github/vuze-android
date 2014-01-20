package com.vuze.android.remote.activity;

import java.util.Map;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.view.PagerTabStrip;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.view.*;

import com.aelitis.azureus.util.MapUtils;
import com.vuze.android.remote.AndroidUtils;
import com.vuze.android.remote.R;
import com.vuze.android.remote.SessionInfo;
import com.vuze.android.remote.activity.TorrentViewActivity.TransmissionRpcAvailableListener;
import com.vuze.android.remote.dialog.DialogFragmentDeleteTorrent;

public class TorrentDetailsFragment
	extends Fragment
{
	ViewPager mViewPager;

	private TorrentDetailsPagerAdapter pagerAdapter;

	protected SessionInfo sessionInfo;

	private long[] ids;

	public View onCreateView(android.view.LayoutInflater inflater,
			android.view.ViewGroup container, Bundle savedInstanceState) {

		setHasOptionsMenu(true);
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
				setTorrentIDs(ids);
			}
		});

		PagerTabStrip pts = (PagerTabStrip) view.findViewById(R.id.pager_title_strip);
		pts.setTextSpacing(0);

		FragmentActivity activity = getActivity();
		if (activity instanceof TorrentViewActivity) {
			((TorrentViewActivity) activity).addRpcAvailableListener(new TransmissionRpcAvailableListener() {

				@Override
				public void uiReady() {
				}

				@Override
				public void transmissionRpcAvailable(SessionInfo sessionInfo) {
					TorrentDetailsFragment.this.sessionInfo = sessionInfo;
				}
			});
		}

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

	public void setTorrentIDs(long[] ids) {
		this.ids = ids;
		if (ids == null || ids.length != 1) {
			mViewPager.setVisibility(View.GONE);
		} else {
			pagerAdapter.setSelection(sessionInfo, ids[0]);
			mViewPager.setVisibility(View.VISIBLE);
			int currentItem = mViewPager.getCurrentItem();
			System.out.println("currentItem = " + currentItem);
			Fragment item = pagerAdapter.getFragment(currentItem);
			System.out.println("frag = " + item);
			if (item instanceof SetTorrentIdListener) {
				((SetTorrentIdListener) item).setTorrentID(sessionInfo, ids[0]);
			}
		}
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		super.onCreateOptionsMenu(menu, inflater);
		inflater.inflate(R.menu.menu_context, menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (handleMenu(item.getItemId())) {
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	protected boolean handleMenu(int itemId) {
		switch (itemId) {
			case R.id.action_sel_remove: {
				Map<?, ?> map = sessionInfo.getTorrent(ids[0]);
				long id = MapUtils.getMapLong(map, "id", -1);
				String name = MapUtils.getMapString(map, "name", "");
				DialogFragmentDeleteTorrent.open(getFragmentManager(), name, id);
				return true;
			}
			case R.id.action_sel_start: {
				sessionInfo.getRpc().startTorrents(ids, false, null);
				return true;
			}
			case R.id.action_sel_forcestart: {
				sessionInfo.getRpc().startTorrents(ids, true, null);
				return true;
			}
			case R.id.action_sel_stop: {
				sessionInfo.getRpc().stopTorrents(ids, null);
				return true;
			}
			case R.id.action_sel_relocate:
				AndroidUtils.openMoveDataDialog(sessionInfo.getTorrent(ids[0]),
						sessionInfo, getFragmentManager());
				return true;
			case R.id.action_sel_move_top: {
				sessionInfo.getRpc().simpleRpcCall("queue-move-top", ids, null);
				return true;
			}
			case R.id.action_sel_move_up: {
				sessionInfo.getRpc().simpleRpcCall("queue-move-up", ids, null);
				return true;
			}
			case R.id.action_sel_move_down: {
				sessionInfo.getRpc().simpleRpcCall("queue-move-down", ids, null);
				return true;
			}
			case R.id.action_sel_move_bottom: {
				sessionInfo.getRpc().simpleRpcCall("queue-move-bottom", ids, null);
				return true;
			}
		}
		return false;
	}

}
