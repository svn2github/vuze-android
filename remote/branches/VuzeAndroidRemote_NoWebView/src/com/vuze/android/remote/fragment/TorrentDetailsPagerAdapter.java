package com.vuze.android.remote.fragment;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewGroup;

import com.vuze.android.remote.SessionInfo;

public class TorrentDetailsPagerAdapter
	extends FragmentPagerAdapter
	implements TorrentIDGetter
{

	private SparseArray<Fragment> pageReferences;

	private SessionInfo sessionInfo;

	private long torrentID;

	public TorrentDetailsPagerAdapter(FragmentManager fm) {
		super(fm);
		pageReferences = new SparseArray<Fragment>();
	}

	/* (non-Javadoc)
	 * @see com.vuze.android.remote.fragment.TorrentIDGetter#getTorrentID()
	 */
	@Override
	public long getTorrentID() {
		return torrentID;
	}

	/* (non-Javadoc)
	 * @see com.vuze.android.remote.fragment.TorrentIDGetter#getSessionInfo()
	 */
	@Override
	public SessionInfo getSessionInfo() {
		return sessionInfo;
	}

	@Override
	public Fragment getItem(int position) {
		Fragment fragment;
		switch (position) {
			case 1:
				fragment = new PeersFragment();
				((PeersFragment) fragment).setTorrentIdGetter(this);
				break;
			default:
				fragment = new FilesFragment();
				((FilesFragment) fragment).setTorrentIdGetter(this);
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
	}

}
