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

import java.io.*;
import java.util.*;

import org.gudy.azureus2.core3.util.DisplayFormatters;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.SearchManager;
import android.content.*;
import android.content.DialogInterface.OnClickListener;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.*;
import android.util.Base64;
import android.util.Log;
import android.view.*;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.SearchView;
import android.widget.SearchView.OnQueryTextListener;
import android.widget.TextView;

import com.aelitis.azureus.util.JSONUtils;
import com.aelitis.azureus.util.MapUtils;
import com.google.analytics.tracking.android.MapBuilder;
import com.vuze.android.remote.*;
import com.vuze.android.remote.dialog.DialogFragmentDeleteTorrent.DeleteTorrentDialogListener;
import com.vuze.android.remote.dialog.DialogFragmentMoveData.MoveDataDialogListener;
import com.vuze.android.remote.dialog.*;
import com.vuze.android.remote.dialog.DialogFragmentOpenTorrent.OpenTorrentDialogListener;
import com.vuze.android.remote.fragment.ActionModeBeingReplacedListener;
import com.vuze.android.remote.fragment.TorrentDetailsFragment;
import com.vuze.android.remote.fragment.TorrentListFragment;
import com.vuze.android.remote.fragment.TorrentListFragment.OnTorrentSelectedListener;
import com.vuze.android.remote.rpc.*;

/**
 * Torrent View -- containing:<br>
 * - Header with speed, filter info, torrent count<br>
 * - Torrent List {@link TorrentListFragment} and<br>
 * - Torrent Details {@link TorrentDetailsFragment} if room provides.
 */
public class TorrentViewActivity
	extends FragmentActivity
	implements OpenTorrentDialogListener, MoveDataDialogListener,
	SessionSettingsChangedListener, TorrentAddedReceivedListener,
	DeleteTorrentDialogListener, OnTorrentSelectedListener, SessionInfoListener,
	ActionModeBeingReplacedListener
{
	private SearchView mSearchView;

	private static final int[] fragmentIDS = {
		R.id.fragment1,
		R.id.fragment2
	};

	public final static int FILECHOOSER_RESULTCODE = 1;

	private static final boolean DEBUG = AndroidUtils.DEBUG;

	private String rpcRoot;

	private TextView tvUpSpeed;

	private TextView tvDownSpeed;

	private TextView tvCenter;

	protected boolean searchIsIconified = true;

	private RemoteProfile remoteProfile;

	private boolean wifiConnected;

	private boolean isOnline;

	private BroadcastReceiver mConnectivityReceiver;

	private boolean remember;

	private boolean disableRefreshButton;

	protected String page;

	protected TransmissionRPC rpc;

	private SessionInfo sessionInfo;

	private View view;

	private boolean isLocalHost;

	/**
	 * Used to capture the File Chooser results from {@link DialogFragmentOpenTorrent}
	 * 
	 * @see android.support.v4.app.FragmentActivity#onActivityResult(int, int, android.content.Intent)
	 */
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
		if (DEBUG) {
			Log.d(null, "ActivityResult!! " + requestCode + "/" + resultCode + ";"
					+ intent);
		}

		requestCode &= 0xFFFF;

		if (requestCode == FILECHOOSER_RESULTCODE) {
			Uri result = intent == null || resultCode != RESULT_OK ? null
					: intent.getData();
			if (DEBUG) {
				Log.d(null, "result = " + result);
			}
			if (result == null) {
				return;
			}
			openTorrent(result);
			return;
		}

		super.onActivityResult(requestCode, resultCode, intent);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setDefaultKeyMode(DEFAULT_KEYS_SEARCH_LOCAL);
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);

		Intent intent = getIntent();
		if (DEBUG) {
			Log.d(null, "TorrentViewActivity intent = " + intent);
			Log.d(null, "Type:" + intent.getType() + ";" + intent.getDataString());
		}

		final Bundle extras = intent.getExtras();
		if (extras == null) {
			System.err.println("No extras!");
			finish();
			return;
		}

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			setupHoneyComb();
		}
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
			setupIceCream();
		}

		setContentView(R.layout.activity_torrent_view);

		// setup view ids now because listeners below may trigger as soon as we get them
		view = findViewById(R.id.activity_torrent_view);
		tvUpSpeed = (TextView) findViewById(R.id.wvUpSpeed);
		tvDownSpeed = (TextView) findViewById(R.id.wvDnSpeed);
		tvCenter = (TextView) findViewById(R.id.wvCenter);

		// register BroadcastReceiver on network state changes
		mConnectivityReceiver = new BroadcastReceiver() {
			public void onReceive(Context context, Intent intent) {
				String action = intent.getAction();
				if (!action.equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
					return;
				}
				setWifiConnected(AndroidUtils.isWifiConnected(context));
				setOnline(AndroidUtils.isOnline(context), false);
			}
		};
		setOnline(AndroidUtils.isOnline(getApplicationContext()), true);
		final IntentFilter mIFNetwork = new IntentFilter();
		mIFNetwork.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
		registerReceiver(mConnectivityReceiver, mIFNetwork);

		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
			// old style menu
			registerForContextMenu(view);
		}

		remember = extras.getBoolean("com.vuze.android.remote.remember");
		String remoteAsJSON = extras.getString("remote.json");
		if (remoteAsJSON != null) {
			try {
				remoteProfile = new RemoteProfile(JSONUtils.decodeJSON(remoteAsJSON));
			} catch (Exception e) {
			}
		}

		if (remoteAsJSON == null) {

			String ac = extras.getString("com.vuze.android.remote.ac");
			String user = extras.getString("com.vuze.android.remote.user");

			AppPreferences appPreferences = VuzeRemoteApp.getAppPreferences();
			remoteProfile = appPreferences.getRemote(ac);
			if (remoteProfile == null) {
				remoteProfile = new RemoteProfile(user, ac);
			}
		}
		setTitle(remoteProfile.getNick());

		isLocalHost = remoteProfile.isLocalHost();
		if (!isOnline && !isLocalHost) {
			AndroidUtils.showConnectionError(this, R.string.no_network_connection,
					false);
			return;
		}

		setProgressBarIndeterminateVisibility(true);

		// Bind and Open take a while, do it on the non-UI thread
		Thread thread = new Thread("bindAndOpen") {
			public void run() {
				String host = remoteProfile.getHost();
				if (host != null && host.length() > 0
						&& remoteProfile.getRemoteType() == RemoteProfile.TYPE_NORMAL) {
					open(remoteProfile.getUser(), remoteProfile.getAC(), "http", host,
							remoteProfile.getPort(), remember);
				} else {
					bindAndOpen(remoteProfile.getAC(), remoteProfile.getUser(), remember);
				}
			}
		};
		thread.setDaemon(true);
		thread.start();

	}

	protected void ui_showOldRPCDialog() {
		if (isFinishing()) {
			return;
		}
		new AlertDialog.Builder(TorrentViewActivity.this).setMessage(
				R.string.old_rpc).setPositiveButton(android.R.string.ok,
				new OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
					}
				}).show();
	}

	@Override
	public void uiReady() {
		if (DEBUG) {
			Log.d(null, "UI READY");
		}

		// first time: track RPC version
		page = "RPC v" + rpc.getRPCVersion() + "/" + rpc.getRPCVersionAZ();

		runOnUiThread(new Runnable() {
			public void run() {
				if (rpc.getRPCVersion() < 14) {
					ui_showOldRPCDialog();
				}

				if (!isOnline && !isLocalHost) {
					pauseUI();
				}
				String dataString = getIntent().getDataString();
				if (dataString != null) {
					openTorrent(getIntent().getData());
				}

				setProgressBarIndeterminateVisibility(false);
				if (tvCenter != null && isOnline) {
					tvCenter.setText("");
				}

			}
		});

	}

	@Override
	protected void onStart() {
		super.onStart();
		VuzeEasyTracker.getInstance(this).activityStart(this);
	}

	protected void setWifiConnected(boolean wifiConnected) {
		if (this.wifiConnected == wifiConnected) {
			return;
		}
		this.wifiConnected = wifiConnected;
	}

	protected void setOnline(boolean isOnline, final boolean initialValue) {
		if (DEBUG) {
			Log.d(null, "set Online to " + isOnline);
		}
		if (this.isOnline == isOnline && !initialValue) {
			return;
		}
		this.isOnline = isOnline;
		runOnUiThread(new Runnable() {
			@SuppressLint("NewApi")
			public void run() {
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
					invalidateOptionsMenu();
				}
				if (TorrentViewActivity.this.isOnline) {
					if (!initialValue && tvCenter != null) {
						tvCenter.setText("");
					}
					if (!isLocalHost) {
						resumeUI();
					}
				} else {
					if (tvCenter != null) {
						System.out.println("NONO");
						tvCenter.setText(R.string.no_network_connection);
					}
					if (!isLocalHost) {
						pauseUI();
					}
				}
			}
		});
	}

	@Override
	protected void onNewIntent(Intent intent) {
		// Called via MetaSearch
		openTorrent(intent.getData());
	}

	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	protected void invalidateOptionsMenuHC() {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				supportInvalidateOptionsMenu();
			}
		});
	}

	@SuppressLint("NewApi")
	@Override
	public void invalidateOptionsMenu() {
		if (mSearchView != null) {
			searchIsIconified = mSearchView.isIconified();
		}
		System.out.println("InvalidateOptionsMenu Called");
		super.invalidateOptionsMenu();
	}

	@SuppressWarnings("null")
	protected void bindAndOpen(final String ac, final String user,
			boolean remember) {

		RPC rpc = new RPC();
		try {
			Map<?, ?> bindingInfo = rpc.getBindingInfo(ac);

			Map<?, ?> error = MapUtils.getMapMap(bindingInfo, "error", null);
			if (error != null) {
				String errMsg = MapUtils.getMapString(error, "msg", "Unknown Error");
				if (DEBUG) {
					Log.d(null, "Error from getBindingInfo " + errMsg);
				}

				AndroidUtils.showConnectionError(this, errMsg, false);
				return;
			}

			String host = MapUtils.getMapString(bindingInfo, "ip", null);
			String protocol = MapUtils.getMapString(bindingInfo, "protocol", null);
			int port = Integer.valueOf(MapUtils.getMapString(bindingInfo, "port", "0"));

			if (DEBUG) {
				if (host == null) {
					//ip = "192.168.2.59";
					host = "192.168.1.2";
					protocol = "http";
					port = 9092;
				}
			}

			if (host != null && protocol != null) {
				remoteProfile.setHost(host);
				remoteProfile.setPort(port);
				open("vuze", ac, protocol, host, port, remember);
			}
		} catch (final RPCException e) {
			VuzeEasyTracker.getInstance(this).logError(this, e);
			AndroidUtils.showConnectionError(TorrentViewActivity.this,
					e.getMessage(), false);
			if (DEBUG) {
				e.printStackTrace();
			}
		}
	}

	private void open(String user, final String ac, String protocol, String host,
			int port, boolean remember) {
		try {

			rpcRoot = protocol + "://" + host + ":" + port + "/";
			String rpcUrl = rpcRoot + "transmission/rpc";

			if (!AndroidUtils.isURLAlive(rpcUrl)) {
				AndroidUtils.showConnectionError(this, R.string.error_remote_not_found,
						false);
				return;
			}

			AppPreferences appPreferences = VuzeRemoteApp.getAppPreferences();
			remoteProfile.setLastUsedOn(System.currentTimeMillis());
			if (remember) {
				appPreferences.setLastRemote(ac);
				appPreferences.addRemoteProfile(remoteProfile);
			}

			if (DEBUG) {
				Log.d(null, "rpc root = " + rpcRoot);
			}

			rpc = new TransmissionRPC(rpcUrl, user, ac);
			sessionInfo = SessionInfoManager.createSessionInfo(rpc, remoteProfile,
					remember);
			sessionInfo.addRpcAvailableListener(TorrentViewActivity.this);
			sessionInfo.addSessionSettingsChangedListeners(TorrentViewActivity.this);
		} catch (Exception e) {
			VuzeEasyTracker.getInstance(this).logError(this, e);
			if (DEBUG) {
				e.printStackTrace();
			}
		}
	}

	@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
	private void setupIceCream() {
		getActionBar().setHomeButtonEnabled(true);
	}

	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	private void setupHoneyComb() {
		// needed because one of our test machines won't listen to <item name="android:windowActionBar">true</item>
		requestWindowFeature(Window.FEATURE_ACTION_BAR);

		// enable ActionBar app icon to behave as action to toggle nav drawer
		ActionBar actionBar = getActionBar();
		if (actionBar == null) {
			if (DEBUG) {
				System.err.println("actionBar is null");
			}
			return;
		}
		actionBar.setDisplayHomeAsUpEnabled(true);
	}

	@Override
	protected void onPause() {
		super.onPause();
		pauseUI();
	}

	private void pauseUI() {
	}

	@Override
	protected void onResume() {
		super.onResume();
		resumeUI();
	}

	private void resumeUI() {
	}

	@Override
	protected void onStop() {
		super.onStop();
		if (DEBUG) {
			Log.d(null, "EWR STOP");
		}
		VuzeEasyTracker.getInstance(this).activityStop(this);
	}

	@Override
	protected void onDestroy() {
		if (mConnectivityReceiver != null) {
			unregisterReceiver(mConnectivityReceiver);
			mConnectivityReceiver = null;
		}

		super.onDestroy();
		if (DEBUG) {
			Log.d(null, "EWR onDestroy");
		}
	}

	/* (non-Javadoc)
	 * @see com.vuze.android.remote.DialogFragmentOpenTorrent.OpenTorrentDialogListener#openTorrent(java.lang.String)
	 */
	public void openTorrent(String s) {
		if (s == null || s.length() == 0) {
			return;
		}
		rpc.addTorrentByUrl(s, false, this);

		VuzeEasyTracker.getInstance(this).send(
				MapBuilder.createEvent("RemoteAction", "AddTorrent", "AddTorrentByUrl",
						null).build());
	}

	@SuppressLint("NewApi")
	public void openTorrent(InputStream is) {
		try {
			byte[] bs = AndroidUtils.readInputStreamAsByteArray(is);
			String metainfo = Base64.encodeToString(bs, Base64.DEFAULT).replaceAll(
					"[\\r\\n]", "");
			rpc.addTorrentByMeta(metainfo, false, this);
		} catch (IOException e) {
			if (DEBUG) {
				e.printStackTrace();
			}
			VuzeEasyTracker.getInstance(this).logError(this, e);
		}
		VuzeEasyTracker.getInstance(this).send(
				MapBuilder.createEvent("remoteAction", "AddTorrent",
						"AddTorrentByMeta", null).build());
	}

	/* (non-Javadoc)
	 * @see com.vuze.android.remote.dialog.DialogFragmentOpenTorrent.OpenTorrentDialogListener#openTorrent(android.net.Uri)
	 */
	@Override
	public void openTorrent(Uri uri) {
		if (DEBUG) {
			Log.d(null, "openTorernt " + uri);
		}
		if (uri == null) {
			return;
		}
		String scheme = uri.getScheme();
		if (DEBUG) {
			Log.d(null, "openTorernt " + scheme);
		}
		if ("file".equals(scheme) || "content".equals(scheme)) {
			try {
				InputStream stream = getContentResolver().openInputStream(uri);
				openTorrent(stream);
			} catch (FileNotFoundException e) {
				if (DEBUG) {
					e.printStackTrace();
				}
				VuzeEasyTracker.getInstance(this).logError(this, e);
			}
		} else {
			openTorrent(uri.toString());
		}
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (handleMenu(item.getItemId())) {
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	protected boolean handleMenu(int itemId) {
		if (DEBUG) {
			System.out.println("HANDLE MENU " + itemId);
		}
		switch (itemId) {
			case android.R.id.home:

				Intent upIntent = NavUtils.getParentActivityIntent(this);
				if (NavUtils.shouldUpRecreateTask(this, upIntent)) {
					upIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

					// This activity is NOT part of this app's task, so create a new task
					// when navigating up, with a synthesized back stack.
					TaskStackBuilder.create(this)
					// Add all of this activity's parents to the back stack
					.addNextIntentWithParentStack(upIntent)
					// Navigate up to the closest parent
					.startActivities();
					finish();
				} else {
					upIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
					startActivity(upIntent);
					finish();
					// Opens parent with FLAG_ACTIVITY_CLEAR_TOP
					// Note: navigateUpFromSameTask and navigateUpTo doesn't set FLAG_ACTIVITY_CLEAR_TOP on JellyBean
					//NavUtils.navigateUpFromSameTask(this);
					//NavUtils.navigateUpTo(this, upIntent);
				}
				return true;
			case R.id.action_settings:
				if (sessionInfo == null) {
					return false;
				}

				AndroidUtils.showSessionSettings(getSupportFragmentManager(),
						sessionInfo);
				return true;
			case R.id.action_add_torrent:
				DialogFragmentOpenTorrent dlg = new DialogFragmentOpenTorrent();
				dlg.show(getSupportFragmentManager(), "OpenTorrentDialog");
				break;
			case R.id.action_search:
				onSearchRequested();
				return true;

			case R.id.action_context:
				openContextMenu(view);
				return true;

			case R.id.action_logout:
				new RemoteUtils(TorrentViewActivity.this).openRemoteList(getIntent());
				finish();
				return true;

			case R.id.action_start_all:
				rpc.startTorrents(null, false, null);
				return true;

			case R.id.action_stop_all:
				rpc.stopTorrents(null, null);
				return true;

			case R.id.action_refresh:
				if (sessionInfo == null) {
					return false;
				}
				sessionInfo.triggerRefresh(false);

				disableRefreshButton = true;
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
					invalidateOptionsMenuHC();
				}

				new Timer().schedule(new TimerTask() {
					public void run() {
						disableRefreshButton = false;
						if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
							invalidateOptionsMenuHC();
						}
					}
				}, 10000);
				return true;
		}
		return false;
	}

	@Override
	// For Android 2.x
	public void onCreateContextMenu(ContextMenu menu, View v,
			ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);

		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.menu_context_torrent_details, menu);
	}

	@Override
	// For Android 2.x
	public boolean onContextItemSelected(MenuItem item) {
		if (handleMenu(item.getItemId())) {
			return true;
		}
		return super.onContextItemSelected(item);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);

		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.menu_web, menu);

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			MenuItem searchItem = menu.findItem(R.id.action_search);
			setupSearchView(searchItem);
		}

		return true;
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		SessionSettings sessionSettings = sessionInfo == null ? null
				: sessionInfo.getSessionSettings();

		MenuItem menuSessionSettings = menu.findItem(R.id.action_settings);
		if (menuSessionSettings != null) {
			menuSessionSettings.setEnabled(sessionSettings != null);
		}

		if (sessionSettings != null) {
			MenuItem menuRefresh = menu.findItem(R.id.action_refresh);
			boolean refreshVisible = false;
			if (!sessionSettings.isRefreshIntervalIsEnabled()
					|| sessionSettings.getRefreshInterval() >= 30) {
				refreshVisible = true;
			}
			menuRefresh.setVisible(refreshVisible);
			menuRefresh.setEnabled(!disableRefreshButton);
		}

		MenuItem menuSearch = menu.findItem(R.id.action_search);
		if (menuSearch != null) {
			menuSearch.setEnabled(isOnline);
		}

		AndroidUtils.fixupMenuAlpha(menu);

		return super.onPrepareOptionsMenu(menu);
	}

	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	private void setupSearchView(MenuItem searchItem) {
		mSearchView = (SearchView) searchItem.getActionView();
		if (mSearchView == null) {
			return;
		}

		SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
		if (searchManager != null) {
			mSearchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));
		}
		mSearchView.setIconifiedByDefault(true);
		mSearchView.setIconified(searchIsIconified);
		mSearchView.setQueryHint(getResources().getString(R.string.search_box_hint));
		mSearchView.setOnQueryTextListener(new OnQueryTextListener() {
			@Override
			public boolean onQueryTextSubmit(String query) {
				AndroidUtils.executeSearch(query, TorrentViewActivity.this,
						remoteProfile, rpcRoot);
				return true;
			}

			@Override
			public boolean onQueryTextChange(String newText) {
				return false;
			}
		});
	}

	/* (non-Javadoc)
	 * @see android.app.Activity#onSearchRequested()
	 */
	@Override
	public boolean onSearchRequested() {
		Bundle appData = new Bundle();
		if (rpc.getRPCVersionAZ() >= 0) {
			appData.putString("com.vuze.android.remote.searchsource", rpcRoot);
			appData.putString("com.vuze.android.remote.ac", remoteProfile.getAC());
		}
		startSearch(null, false, appData, false);
		return true;
	}

	/* (non-Javadoc)
	 * @see com.vuze.android.remote.SessionSettingsChangedListener#sessionSettingsChanged(com.vuze.android.remote.SessionSettings)
	 */
	@Override
	public void sessionSettingsChanged(SessionSettings newSettings) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			invalidateOptionsMenuHC();
		}
	}

	/* (non-Javadoc)
	 * @see com.vuze.android.remote.dialog.DialogFragmentMoveData.MoveDataDialogListener#moveDataTo(long, java.lang.String)
	 */
	@Override
	public void moveDataTo(long id, String s) {
		rpc.moveTorrent(id, s, null);

		VuzeEasyTracker.getInstance(this).send(
				MapBuilder.createEvent("RemoteAction", "MoveData", null, null).build());
	}

	/* (non-Javadoc)
	 * @see com.vuze.android.remote.dialog.DialogFragmentMoveData.MoveDataDialogListener#moveDataHistoryChanged(java.util.ArrayList)
	 */
	@Override
	public void moveDataHistoryChanged(ArrayList<String> history) {
		if (remoteProfile == null) {
			return;
		}
		remoteProfile.setSavePathHistory(history);
		sessionInfo.saveProfileIfRemember();
	}

	/* (non-Javadoc)
	 * @see com.vuze.android.remote.SessionSettingsChangedListener#speedChanged(long, long)
	 */
	public void speedChanged(final long downSpeed, final long upSpeed) {
		runOnUiThread(new Runnable() {
			public void run() {
				if (tvDownSpeed != null) {
					if (downSpeed <= 0) {
						tvDownSpeed.setVisibility(View.GONE);
					} else {
						tvDownSpeed.setText(DisplayFormatters.formatByteCountToKiBEtcPerSec(downSpeed));
						tvDownSpeed.setVisibility(View.VISIBLE);
					}
				}
				if (tvUpSpeed != null) {
					if (upSpeed <= 0) {
						tvUpSpeed.setVisibility(View.GONE);
					} else {
						tvUpSpeed.setText(DisplayFormatters.formatByteCountToKiBEtcPerSec(upSpeed));
						tvUpSpeed.setVisibility(View.VISIBLE);
					}
				}
			}
		});
	}

	@SuppressWarnings("rawtypes")
	@Override
	public void torrentAdded(Map mapTorrentAdded, boolean duplicate) {
		rpc.getRecentTorrents(null);
	}

	/* (non-Javadoc)
	 * @see com.vuze.android.remote.rpc.TorrentAddedReceivedListener#torrentAddFailed(java.lang.String)
	 */
	@Override
	public void torrentAddFailed(String message) {
		AndroidUtils.showDialog(this, R.string.add_torrent, message);
	}

	@Override
	public void torrentAddError(Exception e) {
		AndroidUtils.showConnectionError(this, e.getMessage(), true);
	}

	/* (non-Javadoc)
	 * @see com.vuze.android.remote.dialog.DialogFragmentDeleteTorrent.DeleteTorrentDialogListener#deleteTorrent(java.lang.Object, boolean)
	 */
	@Override
	public void deleteTorrent(Object torrentID, boolean deleteData) {
		rpc.removeTorrent(torrentID, deleteData, null);
	}

	/* (non-Javadoc)
	 * @see com.vuze.android.remote.fragment.TorrentListFragment.OnTorrentSelectedListener#onTorrentSelectedListener(long[])
	 */
	@Override
	public void onTorrentSelectedListener(
			TorrentListFragment torrentListFragment, long[] ids, boolean inMultiMode) {
		// The user selected the headline of an article from the HeadlinesFragment
		// Do something here to display that article

		TorrentDetailsFragment detailFrag = (TorrentDetailsFragment) getSupportFragmentManager().findFragmentById(
				R.id.fragment2);
		View fragmentView = findViewById(R.id.fragment2_container);

		System.out.println("onTorrentSelectedListener: " + ids);
		try {
			//			int o = 0/ 0 ;
		} catch (Exception e) {
			e.printStackTrace();
		}
		if (detailFrag != null) {
			// If article frag is available, we're in two-pane layout...

			// Call a method in the TorrentDetailsFragment to update its content
			if (ids == null || ids.length != 1) {
				fragmentView.setVisibility(View.GONE);
			} else {
				fragmentView.setVisibility(View.VISIBLE);
			}
			detailFrag.setTorrentIDs(ids);
		} else if (ids != null && ids.length == 1 && !inMultiMode) {
			Intent intent = new Intent(getApplicationContext(),
					TorrentDetailsActivity.class);
			intent.putExtra("TorrentID", ids[0]);
			intent.putExtra("RemoteProfileID", remoteProfile.getID());
			startActivity(intent);
			torrentListFragment.finishActionMode();
		}
	}

	/* (non-Javadoc)
	 * @see com.vuze.android.remote.SessionInfo.TransmissionRpcAvailableListener#transmissionRpcAvailable(com.vuze.android.remote.SessionInfo)
	 */
	@Override
	public void transmissionRpcAvailable(SessionInfo sessionInfo) {
		for (int id : fragmentIDS) {
			Fragment fragment = getSupportFragmentManager().findFragmentById(id);

			if (fragment instanceof SessionInfoListener) {
				sessionInfo.addRpcAvailableListener((SessionInfoListener) fragment);
			}
		}
	}

	/* (non-Javadoc)
	 * @see com.vuze.android.remote.fragment.ActionModeBeingReplacedListener#setActionModeBeingReplaced(boolean)
	 */
	@Override
	public void setActionModeBeingReplaced(boolean beingReplaced) {
		for (int id : fragmentIDS) {
  		Fragment fragment = getSupportFragmentManager().findFragmentById(id);
  
  		if (fragment instanceof ActionModeBeingReplacedListener) {
  			((ActionModeBeingReplacedListener) fragment).setActionModeBeingReplaced(beingReplaced);
  		}
		}
	}

	@Override
	public void actionModeBeingReplacedDone() {
		for (int id : fragmentIDS) {
  		Fragment fragment = getSupportFragmentManager().findFragmentById(id);
  
  		if (fragment instanceof ActionModeBeingReplacedListener) {
  			((ActionModeBeingReplacedListener) fragment).actionModeBeingReplacedDone();
  		}
		}
	}
}
