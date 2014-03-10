package com.vuze.android.remote.fragment;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;

import com.vuze.android.remote.SetTorrentIdListener;

public class TorrentDetailsPagerAdapter
	extends FragmentStatePagerAdapter
{

	private long torrentID = -1;

	public TorrentDetailsPagerAdapter(FragmentManager fm) {
		super(fm);
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
		
		updateFragmentArgs(fragment);

		return fragment;
	}

	private void updateFragmentArgs(Fragment fragment) {
		if (fragment == null) {
			return;
		}
		if (fragment.getActivity() != null) {
			if (fragment instanceof SetTorrentIdListener) {
				((SetTorrentIdListener) fragment).setTorrentID(torrentID);
			}
		} else {
  		Bundle arguments = fragment.getArguments();
  		if (arguments == null) {
  			arguments = new Bundle();
  		}
  		arguments.putLong("torrentID", torrentID);
  		fragment.setArguments(arguments);
		}
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

	public void setSelection(long torrentID) {
		this.torrentID = torrentID;
	}

}
