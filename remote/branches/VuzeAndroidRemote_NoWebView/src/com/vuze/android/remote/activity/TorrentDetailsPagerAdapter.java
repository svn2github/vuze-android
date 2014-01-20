package com.vuze.android.remote.activity;

import java.util.HashMap;
import java.util.Map;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.view.View;
import android.view.ViewGroup;

import com.vuze.android.remote.SessionInfo;

public class TorrentDetailsPagerAdapter
	extends FragmentPagerAdapter
{

	private Map<Integer, Fragment> mPageReferenceMap;

	private SessionInfo sessionInfo;

	private long torrentID;

	public TorrentDetailsPagerAdapter(FragmentManager fm) {
		super(fm);
		mPageReferenceMap = new HashMap<Integer, Fragment>();
	}

	@Override
	public Fragment getItem(int position) {
		switch (position) {
			case 1:
				PeersFragment peersFragment = new PeersFragment();
				peersFragment.setTorrentID(sessionInfo, torrentID);
				mPageReferenceMap.put(position, peersFragment);
				return peersFragment;
			default:
				FilesFragment filesFragment = new FilesFragment();
				filesFragment.setTorrentID(sessionInfo, torrentID);
				mPageReferenceMap.put(position, filesFragment);
				return filesFragment;
		}
	}

	public void destroyItem(View container, int position, Object object) {
		super.destroyItem(container, position, object);
		mPageReferenceMap.remove(position);
	}

	@Override
	public void destroyItem(ViewGroup container, int position, Object object) {
		super.destroyItem(container, position, object);
		mPageReferenceMap.remove(position);
	}

	public Fragment getFragment(int key) {
		return mPageReferenceMap.get(key);
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
		this.sessionInfo = sessionInfo;
		this.torrentID = torrentID;
	}

}
