package com.vuze.android.remote.activity;

import com.vuze.android.remote.R;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

public class TorrentDetailsFragment
	extends Fragment
{
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {

		View view = inflater.inflate(R.layout.frag_torrent_list, container, false);
		
		return view;
	}
}
