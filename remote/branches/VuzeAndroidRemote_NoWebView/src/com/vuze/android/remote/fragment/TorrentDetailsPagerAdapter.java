package com.vuze.android.remote.fragment;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewGroup;

import com.vuze.android.remote.SessionInfo;
import com.vuze.android.remote.SetTorrentIdListener;

public class TorrentDetailsPagerAdapter
	extends FragmentPagerAdapter
{

	private SparseArray<Fragment> pageReferences;

	private SessionInfo sessionInfo;

	private long torrentID;

	public TorrentDetailsPagerAdapter(FragmentManager fm) {
		super(fm);
		pageReferences = new SparseArray<Fragment>();
	}

	@Override
	public Fragment getItem(int position) {
		Fragment fragment;
		switch (position) {
			case 1:
				fragment = new PeersFragment();
				break;
			default:
				fragment = new FilesFragment();
		}

		if (sessionInfo != null) {
			if (fragment instanceof SetTorrentIdListener) {
				((SetTorrentIdListener) fragment).setTorrentID(sessionInfo, torrentID);
			}
		}
		pageReferences.put(position, fragment);
		return fragment;
	}

	@SuppressWarnings("deprecation")
	public void destroyItem(View container, int position, Object object) {
		super.destroyItem(container, position, object);
		pageReferences.remove(position);
	}

	@Override
	public void destroyItem(ViewGroup container, int position, Object object) {
		super.destroyItem(container, position, object);
		pageReferences.remove(position);
	}

	public Fragment getFragment(int key) {
		return pageReferences.get(key);
	}

	@Override
	public int getCount() {
		return 2;
	}

	@Override
	public CharSequence getPageTitle(int position) {
		switch (position) {
			case 0:
				return "Files";

			case 1:
				return "Peers";

			case 2:
				return "Sources";
		}
		return super.getPageTitle(position);
	}

	public void setSelection(SessionInfo sessionInfo, long torrentID) {
		if (sessionInfo == null) {
			return;
		}
		this.sessionInfo = sessionInfo;
		this.torrentID = torrentID;

		for (int i = 0, nsize = pageReferences.size(); i < nsize; i++) {
			Fragment fragment = pageReferences.valueAt(i);
			if (fragment instanceof SetTorrentIdListener) {
				((SetTorrentIdListener) fragment).setTorrentID(sessionInfo, torrentID);
			}
		}
	}

}
