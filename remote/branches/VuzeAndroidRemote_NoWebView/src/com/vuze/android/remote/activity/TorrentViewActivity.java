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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

import org.gudy.azureus2.core3.util.DisplayFormatters;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.app.SearchManager;
import android.content.*;
import android.content.DialogInterface.OnClickListener;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.NavUtils;
import android.support.v4.app.TaskStackBuilder;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.SearchView.OnQueryTextListener;
import android.text.Html;
import android.util.Base64;
import android.util.Log;
import android.view.*;
import android.widget.TextView;
import android.widget.Toast;

import com.aelitis.azureus.util.JSONUtils;
import com.aelitis.azureus.util.MapUtils;
import com.google.analytics.tracking.android.MapBuilder;
import com.vuze.android.remote.*;
import com.vuze.android.remote.NetworkState.NetworkStateListener;
import com.vuze.android.remote.SessionInfo.RpcExecuter;
import com.vuze.android.remote.dialog.*;
import com.vuze.android.remote.dialog.DialogFragmentMoveData.MoveDataDialogListener;
import com.vuze.android.remote.dialog.DialogFragmentOpenTorrent.OpenTorrentDialogListener;
import com.vuze.android.remote.fragment.*;
import com.vuze.android.remote.fragment.TorrentListFragment.OnTorrentSelectedListener;
import com.vuze.android.remote.rpc.TorrentAddedReceivedListener;
import com.vuze.android.remote.rpc.TransmissionRPC;

/**
 * Torrent View -- containing:<br>
 * - Header with speed, filter info, torrent count<br>
 * - Torrent List {@link TorrentListFragment} and<br>
 * - Torrent Details {@link TorrentDetailsFragment} if room provides.
 */
public class TorrentViewActivity
	extends ActionBarActivity
	implements OpenTorrentDialogListener, MoveDataDialogListener,
	SessionSettingsChangedListener, TorrentAddedReceivedListener,
	OnTorrentSelectedListener, SessionInfoListener,
	ActionModeBeingReplacedListener, NetworkStateListener, SessionInfoGetter
{
	private SearchView mSearchView;

	private static final int[] fragmentIDS = {
		R.id.fragment1,
		R.id.fragment2
	};

	public final static int FILECHOOSER_RESULTCODE = 1;

	private static final boolean DEBUG = AndroidUtils.DEBUG;

	private static final String TAG = "TorrentView";

	private TextView tvUpSpeed;

	private TextView tvDownSpeed;

	private TextView tvCenter;

	protected boolean searchIsIconified = true;

	private RemoteProfile remoteProfile;

	private boolean disableRefreshButton;

	protected String page;

	private SessionInfo sessionInfo;

	private boolean isLocalHost;

	private boolean uiReady = false;

	/**
	 * Used to capture the File Chooser results from {@link DialogFragmentOpenTorrent}
	 * 
	 * @see android.support.v4.app.FragmentActivity#onActivityResult(int, int, android.content.Intent)
	 */
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
		if (DEBUG) {
			Log.d(TAG, "ActivityResult!! " + requestCode + "/" + resultCode + ";"
					+ intent);
		}

		requestCode &= 0xFFFF;

		if (requestCode == FILECHOOSER_RESULTCODE) {
			Uri result = intent == null || resultCode != RESULT_OK ? null
					: intent.getData();
			if (DEBUG) {
				Log.d(TAG, "result = " + result);
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
		setDefaultKeyMode(DEFAULT_KEYS_SEARCH_LOCAL);
		super.onCreate(savedInstanceState);

		Intent intent = getIntent();
		if (DEBUG) {
			Log.d(TAG, "TorrentViewActivity intent = " + intent);
			Log.d(TAG, "Type:" + intent.getType() + ";" + intent.getDataString());
		}

		final Bundle extras = intent.getExtras();
		if (extras == null) {
			Log.e(TAG, "No extras!");
			finish();
			return;
		}

		boolean remember = extras.getBoolean("com.vuze.android.remote.remember");
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

		sessionInfo = SessionInfoManager.getSessionInfo(remoteProfile, this,
				remember);
		sessionInfo.addRpcAvailableListener(this);
		sessionInfo.addSessionSettingsChangedListeners(this);

		setupActionBar();
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
			setupIceCream();
		}

		setContentView(R.layout.activity_torrent_view);

		// setup view ids now because listeners below may trigger as soon as we get them
		tvUpSpeed = (TextView) findViewById(R.id.wvUpSpeed);
		tvDownSpeed = (TextView) findViewById(R.id.wvDnSpeed);
		tvCenter = (TextView) findViewById(R.id.wvCenter);

		setSubtitle(remoteProfile.getNick());

		isLocalHost = remoteProfile.isLocalHost();
		if (!VuzeRemoteApp.getNetworkState().isOnline() && !isLocalHost) {
			AndroidUtils.showConnectionError(this, R.string.no_network_connection,
					false);
			return;
		}
	}

	private void setSubtitle(String name) {
		ActionBar actionBar = getSupportActionBar();
		if (actionBar != null) {
			actionBar.setSubtitle(name);
		}
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

	/* (non-Javadoc)
	 * @see com.vuze.android.remote.SessionInfoListener#uiReady()
	 */
	@Override
	public void uiReady(final TransmissionRPC rpc) {
		if (DEBUG) {
			Log.d(TAG, "UI READY");
		}

		uiReady = true;
		// first time: track RPC version
		page = "RPC v" + rpc.getRPCVersion() + "/" + rpc.getRPCVersionAZ();

		runOnUiThread(new Runnable() {
			public void run() {
				if (isFinishing()) {
					return;
				}
				if (rpc.getRPCVersion() < 14) {
					ui_showOldRPCDialog();
				}

				String dataString = getIntent().getDataString();
				if (dataString != null) {
					openTorrent(getIntent().getData());
					getIntent().setData(null);
				}

				if (tvCenter != null && VuzeRemoteApp.getNetworkState().isOnline()) {
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

	@Override
	protected void onNewIntent(Intent intent) {
		if (DEBUG) {
			Log.d(TAG, "onNewIntent " + intent);
		}
		super.onNewIntent(intent);
		// Called via MetaSearch
		openTorrent(intent.getData());
	}

	protected void invalidateOptionsMenuHC() {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				supportInvalidateOptionsMenu();
			}
		});
	}

	@Override
	public void supportInvalidateOptionsMenu() {
		if (mSearchView != null) {
			searchIsIconified = mSearchView.isIconified();
		}
		if (AndroidUtils.DEBUG_MENU) {
			Log.d(TAG, "InvalidateOptionsMenu Called");
		}
		super.supportInvalidateOptionsMenu();
	}

	@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
	private void setupIceCream() {
		getActionBar().setHomeButtonEnabled(true);
	}

	private void setupActionBar() {
		ActionBar actionBar = getSupportActionBar();
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
		VuzeRemoteApp.getNetworkState().removeListener(this);
		if (sessionInfo != null) {
			sessionInfo.activityPaused();
			sessionInfo.removeSessionSettingsChangedListeners(TorrentViewActivity.this);
		}
		super.onPause();
	}

	@Override
	protected void onResume() {
		VuzeRemoteApp.getNetworkState().addListener(this);
		if (sessionInfo != null) {
			sessionInfo.activityResumed();
			sessionInfo.addSessionSettingsChangedListeners(TorrentViewActivity.this);
		}

		super.onResume();
	}

	@Override
	protected void onStop() {
		super.onStop();
		VuzeEasyTracker.getInstance(this).activityStop(this);
	}

	/* (non-Javadoc)
	 * @see com.vuze.android.remote.DialogFragmentOpenTorrent.OpenTorrentDialogListener#openTorrent(java.lang.String)
	 */
	public void openTorrent(final String sTorrent) {
		if (sTorrent == null || sTorrent.length() == 0 || sessionInfo == null) {
			return;
		}
		sessionInfo.executeRpc(new RpcExecuter() {
			@Override
			public void executeRpc(TransmissionRPC rpc) {
				rpc.addTorrentByUrl(sTorrent, false, TorrentViewActivity.this);
			}
		});
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				Context context = isFinishing() ? VuzeRemoteApp.getContext()
						: TorrentViewActivity.this;
				String s = context.getResources().getString(R.string.toast_adding_xxx,
						sTorrent);
				Toast.makeText(context, s, Toast.LENGTH_SHORT).show();
			}
		});

		VuzeEasyTracker.getInstance(this).send(
				MapBuilder.createEvent("RemoteAction", "AddTorrent", "AddTorrentByUrl",
						null).build());
	}

	@SuppressLint("NewApi")
	public void openTorrent(final String name, InputStream is) {
		if (sessionInfo == null) {
			return;
		}
		try {
			byte[] bs = AndroidUtils.readInputStreamAsByteArray(is);
			final String metainfo = Base64.encodeToString(bs, Base64.DEFAULT).replaceAll(
					"[\\r\\n]", "");
			sessionInfo.executeRpc(new RpcExecuter() {
				@Override
				public void executeRpc(TransmissionRPC rpc) {
					rpc.addTorrentByMeta(metainfo, false, TorrentViewActivity.this);
				}
			});
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					Context context = isFinishing() ? VuzeRemoteApp.getContext()
							: TorrentViewActivity.this;
					String s = context.getResources().getString(
							R.string.toast_adding_xxx, name);
					Toast.makeText(context, s, Toast.LENGTH_SHORT).show();
				}
			});
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
			Log.d(TAG, "openTorernt " + uri);
		}
		if (uri == null) {
			return;
		}
		String scheme = uri.getScheme();
		if (DEBUG) {
			Log.d(TAG, "openTorrent " + scheme);
		}
		if ("file".equals(scheme) || "content".equals(scheme)) {
			try {
				InputStream stream = getContentResolver().openInputStream(uri);
				openTorrent(uri.toString(), stream);
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
			Log.d(TAG, "HANDLE MENU " + itemId);
		}
		if (isFinishing()) {
			return true;
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
			case R.id.action_settings: {
				if (sessionInfo == null) {
					return false;
				}

				AndroidUtils.showSessionSettings(getSupportFragmentManager(),
						sessionInfo);
				return true;
			}
			case R.id.action_add_torrent: {
				DialogFragmentOpenTorrent dlg = new DialogFragmentOpenTorrent();
				dlg.show(getSupportFragmentManager(), "OpenTorrentDialog");
				break;
			}

			case R.id.action_search:
				onSearchRequested();
				return true;

			case R.id.action_logout: {
				new RemoteUtils(TorrentViewActivity.this).openRemoteList(getIntent());
				SessionInfoManager.removeSessionInfo(remoteProfile.getID());
				finish();
				return true;
			}

			case R.id.action_start_all: {
				if (sessionInfo == null) {
					return false;
				}
				sessionInfo.executeRpc(new RpcExecuter() {
					@Override
					public void executeRpc(TransmissionRPC rpc) {
						rpc.startTorrents(TAG, null, false, null);
					}
				});

				return true;
			}

			case R.id.action_stop_all: {
				if (sessionInfo == null) {
					return false;
				}
				sessionInfo.executeRpc(new RpcExecuter() {
					@Override
					public void executeRpc(TransmissionRPC rpc) {
						rpc.stopTorrents(TAG, null, null);
					}
				});

				return true;
			}

			case R.id.action_refresh: {
				if (sessionInfo == null) {
					return false;
				}
				sessionInfo.triggerRefresh(false, null);

				disableRefreshButton = true;
				invalidateOptionsMenuHC();

				new Timer().schedule(new TimerTask() {
					public void run() {
						disableRefreshButton = false;
						invalidateOptionsMenuHC();
					}
				}, 10000);
				return true;
			}

			case R.id.action_about: {
				DialogFragmentAbout dlg = new DialogFragmentAbout();
				dlg.show(getSupportFragmentManager(), "About");
				return true;
			}

			case R.id.action_rate: {
				final String appPackageName = getPackageName();
				try {
					startActivity(new Intent(Intent.ACTION_VIEW,
							Uri.parse("market://details?id=" + appPackageName)));
				} catch (android.content.ActivityNotFoundException anfe) {
					startActivity(new Intent(Intent.ACTION_VIEW,
							Uri.parse("http://play.google.com/store/apps/details?id="
									+ appPackageName)));
				}
				return true;
			}

			case R.id.action_forum: {
				String url = "http://www.vuze.com/forums/android-remote";
				Intent i = new Intent(Intent.ACTION_VIEW);
				i.setData(Uri.parse(url));
				startActivity(i);
				return true;
			}

			case R.id.action_vote: {
				String url = "http://vote.vuze.com/forums/227649";
				Intent i = new Intent(Intent.ACTION_VIEW);
				i.setData(Uri.parse(url));
				startActivity(i);
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		if (AndroidUtils.DEBUG_MENU) {
			Log.d(TAG, "onCreateOptionsMenu");
		}

		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.menu_torrent_list, menu);

		MenuItem searchItem = menu.findItem(R.id.action_search);
		setupSearchView(searchItem);

		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		if (AndroidUtils.DEBUG_MENU) {
			Log.d(TAG, "onPrepareOptionsMenu");
		}
		SessionSettings sessionSettings = sessionInfo == null ? null
				: sessionInfo.getSessionSettings();

		MenuItem menuSessionSettings = menu.findItem(R.id.action_settings);
		if (menuSessionSettings != null) {
			menuSessionSettings.setEnabled(sessionSettings != null);
		}

		if (sessionSettings != null) {
			MenuItem menuRefresh = menu.findItem(R.id.action_refresh);
			boolean refreshVisible = false;
			if (!remoteProfile.isUpdateIntervalEnabled()
					|| remoteProfile.getUpdateInterval() >= 45) {
				refreshVisible = true;
			}
			menuRefresh.setVisible(refreshVisible);
			menuRefresh.setEnabled(!disableRefreshButton);
		}

		MenuItem menuSearch = menu.findItem(R.id.action_search);
		if (menuSearch != null) {
			menuSearch.setEnabled(VuzeRemoteApp.getNetworkState().isOnline());
		}

		AndroidUtils.fixupMenuAlpha(menu);

		return super.onPrepareOptionsMenu(menu);
	}

	private void setupSearchView(MenuItem searchItem) {
		mSearchView = (SearchView) MenuItemCompat.getActionView(searchItem);
		if (mSearchView == null) {
			return;
		}

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO) {
			setupSearchView_Froyo(mSearchView);
		}
		mSearchView.setIconifiedByDefault(true);
		mSearchView.setIconified(searchIsIconified);
		mSearchView.setQueryHint(getResources().getString(R.string.search_box_hint));
		mSearchView.setOnQueryTextListener(new OnQueryTextListener() {
			@Override
			public boolean onQueryTextSubmit(String query) {
				AndroidUtils.executeSearch(query, TorrentViewActivity.this,
						remoteProfile, sessionInfo.getRpcRoot());
				return true;
			}

			@Override
			public boolean onQueryTextChange(String newText) {
				return false;
			}
		});
	}

	@TargetApi(Build.VERSION_CODES.FROYO)
	private void setupSearchView_Froyo(SearchView mSearchView) {
		SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
		if (searchManager != null) {
			mSearchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));
		}
	}

	/* (non-Javadoc)
	 * @see android.app.Activity#onSearchRequested()
	 */
	@Override
	public boolean onSearchRequested() {
		Bundle appData = new Bundle();
		if (sessionInfo != null && sessionInfo.getRPCVersionAZ() >= 0) {
			appData.putString("com.vuze.android.remote.searchsource",
					sessionInfo.getRpcRoot());
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
		invalidateOptionsMenuHC();
	}

	/* (non-Javadoc)
	 * @see com.vuze.android.remote.dialog.DialogFragmentMoveData.MoveDataDialogListener#moveDataTo(long, java.lang.String)
	 */
	@Override
	public void moveDataTo(final long id, final String s) {
		if (sessionInfo == null) {
			return;
		}
		sessionInfo.executeRpc(new RpcExecuter() {
			@Override
			public void executeRpc(TransmissionRPC rpc) {
				rpc.moveTorrent(id, s, null);

				VuzeEasyTracker.getInstance().send(
						MapBuilder.createEvent("RemoteAction", "MoveData", null, null).build());
			}
		});

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
				if (isFinishing()) {
					return;
				}
				if (tvDownSpeed != null) {
					if (downSpeed <= 0) {
						tvDownSpeed.setVisibility(View.GONE);
					} else {
						String s = "\u25BC "
								+ DisplayFormatters.formatByteCountToKiBEtcPerSec(downSpeed);
						tvDownSpeed.setText(Html.fromHtml(s));
						tvDownSpeed.setVisibility(View.VISIBLE);
					}
				}
				if (tvUpSpeed != null) {
					if (upSpeed <= 0) {
						tvUpSpeed.setVisibility(View.GONE);
					} else {
						String s = "\u25B2 "
								+ DisplayFormatters.formatByteCountToKiBEtcPerSec(upSpeed);
						tvUpSpeed.setText(Html.fromHtml(s));
						tvUpSpeed.setVisibility(View.VISIBLE);
					}
				}
			}
		});
	}

	@SuppressWarnings("rawtypes")
	@Override
	public void torrentAdded(final Map mapTorrentAdded, boolean duplicate) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				String name = MapUtils.getMapString(mapTorrentAdded, "name", "Torrent");
				Context context = isFinishing() ? VuzeRemoteApp.getContext()
						: TorrentViewActivity.this;
				String s = context.getResources().getString(R.string.toast_added, name);
				Toast.makeText(context, s, Toast.LENGTH_LONG).show();
			}
		});
		if (sessionInfo != null) {
			sessionInfo.executeRpc(new RpcExecuter() {
				@Override
				public void executeRpc(TransmissionRPC rpc) {
					rpc.getRecentTorrents(TAG, null);
				}
			});
		}
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

		if (DEBUG) {
			Log.d(TAG, "onTorrentSelectedListener: " + Arrays.toString(ids)
					+ ";multi?" + inMultiMode + ";" + detailFrag);
		}
		if (detailFrag != null && fragmentView != null) {
			// If article frag is available, we're in two-pane layout...

			// Call a method in the TorrentDetailsFragment to update its content
			if (ids == null || ids.length != 1) {
				fragmentView.setVisibility(View.GONE);
			} else {
				fragmentView.setVisibility(View.VISIBLE);
			}
			detailFrag.setTorrentIDs(ids);
		} else if (ids != null && ids.length == 1 && !inMultiMode) {
			torrentListFragment.clearSelection();

			Intent intent = new Intent(Intent.ACTION_VIEW, null, this,
					TorrentDetailsActivity.class);
			intent.setFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
			intent.putExtra("TorrentID", ids[0]);
			intent.putExtra("RemoteProfileID", remoteProfile.getID());
			startActivity(intent);
		}
	}

	/* (non-Javadoc)
	 * @see com.vuze.android.remote.SessionInfo.TransmissionRpcAvailableListener#transmissionRpcAvailable(com.vuze.android.remote.SessionInfo)
	 */
	@Override
	public void transmissionRpcAvailable(SessionInfo sessionInfo) {
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

	@Override
	public void onlineStateChanged(final boolean isOnline) {
		runOnUiThread(new Runnable() {
			public void run() {
				if (isFinishing()) {
					return;
				}
				supportInvalidateOptionsMenu();
				if (isOnline) {
					if (uiReady && tvCenter != null) {
						tvCenter.setText("");
					}
				} else {
					if (tvCenter != null) {
						tvCenter.setText(R.string.no_network_connection);
					}
				}
			}
		});
	}

	@Override
	public void wifiConnectionChanged(boolean isWifiConnected) {
	}

	@Override
	public SessionInfo getSessionInfo() {
		return sessionInfo;
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
	}
}
