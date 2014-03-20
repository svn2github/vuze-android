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

import java.util.List;

import android.app.Activity;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.view.View;

import com.astuetz.PagerSlidingTabStrip;
import com.vuze.android.remote.*;
import com.vuze.android.remote.activity.TorrentDetailsActivity;
import com.vuze.android.remote.activity.TorrentViewActivity;

/**
 * Torrent Details Fragment<br>
 * - Contains {@link PeersFragment}, {@link FilesFragment}<br>
 * - Contained in {@link TorrentViewActivity} for wide screens<br>
 * - Contained in {@link TorrentDetailsActivity} for narrow screens<br>
 */
public class TorrentDetailsFragment
	extends Fragment
{
	private static final String TAG = "TorrentDetailsFragment";

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

		tabs.setOnPageChangeListener(new OnPageChangeListener() {
			int oldPosition = 0;

			@Override
			public void onPageSelected(int position) {
				Fragment oldFrag = pagerAdapter.findFragmentByPosition(
						getFragmentManager(), oldPosition);
				if (oldFrag instanceof FragmentPagerListener) {
					FragmentPagerListener l = (FragmentPagerListener) oldFrag;
					l.pageDeactivated();
				}

				oldPosition = position;

				Fragment newFrag = pagerAdapter.findFragmentByPosition(
						getFragmentManager(), position);
				if (newFrag instanceof FragmentPagerListener) {
					FragmentPagerListener l = (FragmentPagerListener) newFrag;
					l.pageActivated();
				}
			}

			@Override
			public void onPageScrolled(int position, float positionOffset,
					int positionOffsetPixels) {
			}

			@Override
			public void onPageScrollStateChanged(int state) {
			}
		});

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
}
