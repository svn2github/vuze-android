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
 * 
 */

package com.vuze.android.remote.activity;

import java.util.Map;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.*;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.ListView;

import com.aelitis.azureus.util.JSONUtils;
import com.vuze.android.remote.*;
import com.vuze.android.remote.dialog.*;
import com.vuze.android.remote.dialog.DialogFragmentGenericRemoteProfile.GenericRemoteProfileListener;
import com.vuze.android.remote.rpc.RPC;

public class IntentHandler
	extends FragmentActivity
	implements GenericRemoteProfileListener
{

	private static final String TAG = "ProfileSelector";

	private ListView listview;

	private AppPreferences appPreferences;

	private ProfileArrayAdapter adapter;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.activity_intent_handler);

		final Intent intent = getIntent();

		boolean forceOpen = (intent.getFlags() & Intent.FLAG_ACTIVITY_CLEAR_TOP) > 0;

		if (AndroidUtils.DEBUG) {
			Log.d(TAG, "ForceOpen? " + forceOpen);
			Log.d(TAG, "IntentHandler intent = " + intent);
		}

		appPreferences = VuzeRemoteApp.getAppPreferences();

		Uri data = intent.getData();
		if (data != null) {
			try {
				String scheme = data.getScheme();
				String host = data.getHost();
				String path = data.getPath();
				if ("vuze".equals(scheme) && "remote".equals(host) && path != null
						&& data.getPath().length() > 1) {
					String ac = data.getPath().substring(1);
					intent.setData(null);
					if (ac.length() < 100) {
						new RemoteUtils(this).openRemote("vuze", ac, true, true);
						finish();
						return;
					}
				}
				if (host.equals("remote.vuze.com")
						&& data.getQueryParameter("ac") != null) {
					String ac = data.getQueryParameter("ac");
					intent.setData(null);
					if (ac.length() < 100) {
						new RemoteUtils(this).openRemote("vuze", ac, true, true);
						finish();
						return;
					}
				}
			} catch (Exception e) {
				if (AndroidUtils.DEBUG) {
					e.printStackTrace();
				}
			}
		}

		if (!forceOpen) {
			int numRemotes = getRemotesWithLocal().length;
			if (numRemotes == 0) {
				// New User: Send them to Login (Account Creation)
				Intent myIntent = new Intent(Intent.ACTION_VIEW, null, this,
						LoginActivity.class);
				myIntent.setFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION
						| Intent.FLAG_ACTIVITY_PREVIOUS_IS_TOP);

				startActivity(myIntent);
				finish();
				return;
			} else if (numRemotes == 1 || intent.getData() == null) {
				try {
					RemoteProfile remoteProfile = appPreferences.getLastUsedRemote();
					if (remoteProfile != null) {
						if (savedInstanceState == null) {
							new RemoteUtils(this).openRemote(remoteProfile, true, true);
							finish();
							return;
						}
					} else {
						Log.d(TAG, "Has Remotes, but no last remote");
					}
				} catch (Throwable t) {
					if (AndroidUtils.DEBUG) {
						Log.e(TAG, "onCreate", t);
					}
					VuzeEasyTracker.getInstance(this).logError(this, t);
				}
			}
		}

		listview = (ListView) findViewById(R.id.lvRemotes);
		listview.setItemsCanFocus(false);

		adapter = new ProfileArrayAdapter(this);

		listview.setAdapter(adapter);

		listview.setOnItemClickListener(new AdapterView.OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> parent, final View view,
					int position, long id) {
				Object item = parent.getItemAtPosition(position);

				if (item instanceof RemoteProfile) {
					RemoteProfile remote = (RemoteProfile) item;
					new RemoteUtils(IntentHandler.this).openRemote(remote, true,
							intent.getData() != null);
				}
			}

		});

		registerForContextMenu(listview);
	}

	private RemoteProfile[] getRemotesWithLocal() {
		RemoteProfile[] remotes = appPreferences.getRemotes();

		if (RPC.isLocalAvailable()) {
			if (AndroidUtils.DEBUG) {
				Log.d(TAG, "Local Vuze Detected");
			}

			boolean alreadyAdded = false;
			for (RemoteProfile remoteProfile : remotes) {
				if ("localhost".equals(remoteProfile.getHost())) {
					alreadyAdded = true;
					break;
				}
			}
			if (!alreadyAdded) {
				if (AndroidUtils.DEBUG) {
					Log.d(TAG, "Adding localhost profile..");
				}
				RemoteProfile localProfile = new RemoteProfile(
						RemoteProfile.TYPE_NORMAL);
				localProfile.setHost("localhost");
				localProfile.setNick(getString(R.string.local_name,
						android.os.Build.MODEL));
				RemoteProfile[] newRemotes = new RemoteProfile[remotes.length + 1];
				newRemotes[0] = localProfile;
				System.arraycopy(remotes, 0, newRemotes, 1, remotes.length);
				remotes = newRemotes;
			}
		}
		return remotes;
	}
	
	@Override
	protected void onResume() {
		super.onResume();

		if (adapter != null) {
			RemoteProfile[] remotesWithLocal = getRemotesWithLocal();
			adapter.addRemotes(remotesWithLocal);
		}
	}
	
	@Override
	protected void onStart() {
		super.onStart();
		VuzeEasyTracker.getInstance(this).activityStart(this);
	}

	@Override
	protected void onStop() {
		super.onStop();
		VuzeEasyTracker.getInstance(this).activityStop(this);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.menu_intenthandler, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {

		switch (item.getItemId()) {
			case R.id.action_add_profile: {
				Intent myIntent = new Intent(getIntent());
				myIntent.setFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
				myIntent.setClass(IntentHandler.this, LoginActivity.class);
				myIntent.putExtra("com.vuze.android.remote.login.ac", "");

				startActivity(myIntent);
				return true;
			}
			case R.id.action_adv_login: {
				DialogFragmentGenericRemoteProfile dlg = new DialogFragmentGenericRemoteProfile();
				dlg.show(getSupportFragmentManager(), "GenericRemoteProfile");

				return true;
			}
			case R.id.action_about: {
				DialogFragmentAbout dlg = new DialogFragmentAbout();
				dlg.show(getSupportFragmentManager(), "About");
				return true;
			}
		}

		return super.onOptionsItemSelected(item);
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v,
			ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);

		AdapterContextMenuInfo adapterMenuInfo = (AdapterContextMenuInfo) menuInfo;
		Object item = listview.getItemAtPosition(adapterMenuInfo.position);

		if (item instanceof RemoteProfile) {
			MenuInflater inflater = getMenuInflater();
			inflater.inflate(R.menu.menu_context_intenthandler, menu);
		}
	}

	@Override
	public boolean onContextItemSelected(MenuItem menuitem) {
		ContextMenuInfo menuInfo = menuitem.getMenuInfo();
		AdapterContextMenuInfo adapterMenuInfo = (AdapterContextMenuInfo) menuInfo;

		Object item = listview.getItemAtPosition(adapterMenuInfo.position);

		if (!(item instanceof RemoteProfile)) {
			return super.onContextItemSelected(menuitem);
		}

		final RemoteProfile remoteProfile = (RemoteProfile) item;

		switch (menuitem.getItemId()) {
			case R.id.action_edit_pref:
				editProfile(remoteProfile);

				return true;
			case R.id.action_delete_pref:
				new AlertDialog.Builder(this).setTitle("Remove Profile?").setMessage(
						"Configuration settings for profile '" + remoteProfile.getNick()
								+ "' will be deleted.").setPositiveButton("Remove",
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int which) {
								appPreferences.removeRemoteProfile(remoteProfile.getID());
								adapter.refreshList();
							}
						}).setNegativeButton(android.R.string.cancel,
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int which) {
							}
						}).setIcon(android.R.drawable.ic_dialog_alert).show();

				return true;
		}
		return super.onContextItemSelected(menuitem);
	}

	public void editProfile(RemoteProfile remoteProfile) {
		DialogFragment dlg = remoteProfile.getRemoteType() == RemoteProfile.TYPE_LOOKUP
				? new DialogFragmentVuzeRemoteProfile()
				: new DialogFragmentGenericRemoteProfile();
		Bundle args = new Bundle();
		Map<?, ?> profileAsMap = remoteProfile.getAsMap(false);
		String profileAsJSON = JSONUtils.encodeToJSON(profileAsMap);
		args.putSerializable("remote.json", profileAsJSON);
		dlg.setArguments(args);
		dlg.show(getSupportFragmentManager(), "GenericRemoteProfile");
	}

	public void profileEditDone(RemoteProfile oldProfile, RemoteProfile newProfile) {
		adapter.refreshList();
	}

}
