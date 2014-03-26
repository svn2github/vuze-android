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

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.util.Log;
import android.view.View;

import com.astuetz.PagerSlidingTabStrip;
import com.vuze.android.remote.AndroidUtils;
import com.vuze.android.remote.R;
import com.vuze.android.remote.SetTorrentIdListener;
import com.vuze.android.remote.activity.TorrentDetailsActivity;
import com.vuze.android.remote.activity.TorrentViewActivity;

/**
 * Torrent Details Fragment<br>
 * - Contains {@link PeersFragment}, {@link FilesFragment}, {@link TorrentInfoFragment}<br>
 * - Contained in {@link TorrentViewActivity} for wide screens<br>
 * - Contained in {@link TorrentDetailsActivity} for narrow screens<br>
 */
public class TorrentDetailsFragment
	extends Fragment
{
	protected static final String TAG = "TorrentDetailsFrag";

	ViewPager mViewPager;

	private TorrentDetailsPagerAdapter pagerAdapter;

	private long torrentID;

	public View onCreateView(android.view.LayoutInflater inflater,
			android.view.ViewGroup container, Bundle savedInstanceState) {

		View view = inflater.inflate(R.layout.frag_torrent_details, container,
				false);

		mViewPager = (ViewPager) view.findViewById(R.id.pager);
		pagerAdapter = new TorrentDetailsPagerAdapter(getFragmentManager(),
				mViewPager);

		// Bind the tabs to the ViewPager
		PagerSlidingTabStrip tabs = (PagerSlidingTabStrip) view.findViewById(R.id.pager_title_strip);

		mViewPager.setAdapter(pagerAdapter);
		tabs.setViewPager(mViewPager);

		tabs.setOnPageChangeListener(new OnPageChangeListener() {
			int oldPosition = 0;

			@Override
			public void onPageSelected(int position) {
				if (AndroidUtils.DEBUG) {
					Log.d(TAG, "page selected: " + position);
				}
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
	public void onResume() {
		super.onResume();

		Fragment newFrag = pagerAdapter.findFragmentByPosition(
				getFragmentManager(), mViewPager.getCurrentItem());
		// newFrag will be null on first view, so position 0 will not
		// get pageActivated from here
		if (newFrag instanceof FragmentPagerListener) {
			FragmentPagerListener l = (FragmentPagerListener) newFrag;
			l.pageActivated();
		}
	}
	
	@Override
	public void onPause() {
		Fragment newFrag = pagerAdapter.findFragmentByPosition(
				getFragmentManager(), mViewPager.getCurrentItem());
		if (newFrag instanceof FragmentPagerListener) {
			FragmentPagerListener l = (FragmentPagerListener) newFrag;
			l.pageDeactivated();
		}

		super.onPause();
	}

	// Called from Activity
	public void setTorrentIDs(long[] newIDs) {
		this.torrentID = newIDs != null && newIDs.length == 1 ? newIDs[0] : -1;
		pagerAdapter.setSelection(torrentID);
		AndroidUtils.runOnUIThread(this, new Runnable() {
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
