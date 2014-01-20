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
import java.net.HttpURLConnection;
import java.net.URL;
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
import android.os.Handler;
import android.support.v4.app.*;
import android.util.Base64;
import android.util.Log;
import android.view.*;
import android.view.ContextMenu.ContextMenuInfo;
import android.webkit.JavascriptInterface;
import android.widget.SearchView;
import android.widget.SearchView.OnQueryTextListener;
import android.widget.TextView;

import com.aelitis.azureus.util.JSONUtils;
import com.aelitis.azureus.util.MapUtils;
import com.google.analytics.tracking.android.MapBuilder;
import com.vuze.android.remote.*;
import com.vuze.android.remote.activity.TorrentListFragment.OnTorrentSelectedListener;
import com.vuze.android.remote.dialog.DialogFragmentDeleteTorrent.DeleteTorrentDialogListener;
import com.vuze.android.remote.dialog.DialogFragmentMoveData.MoveDataDialogListener;
import com.vuze.android.remote.dialog.*;
import com.vuze.android.remote.dialog.DialogFragmentOpenTorrent.OpenTorrentDialogListener;
import com.vuze.android.remote.dialog.DialogFragmentSessionSettings.SessionSettingsListener;
import com.vuze.android.remote.rpc.*;

/**
 * Torrent View -- containing:<br>
 * - Header with speed, filter info, torrent count<br>
 * - Torrent List {@link TorrentListFragment} and<br>
 * - Torrent Details {@link TorrentDetailsFragment} if room provides.
 */
public class TorrentViewActivity
	extends FragmentActivity
	implements OpenTorrentDialogListener, SessionSettingsListener,
	MoveDataDialogListener, SessionSettingsReceivedListener,
	TorrentAddedReceivedListener, DeleteTorrentDialogListener,
	OnTorrentSelectedListener
{
	private SearchView mSearchView;

	public final static int FILECHOOSER_RESULTCODE = 1;

	private static final boolean DEBUG = AndroidUtils.DEBUG;

	private String rpcRoot;

	protected boolean uiReady = false;

	private TextView tvUpSpeed;

	private TextView tvDownSpeed;

	private TextView tvCenter;

	protected SessionSettings sessionSettings;

	protected boolean searchIsIconified = true;

	private RemoteProfile remoteProfile;

	private boolean wifiConnected;

	private boolean isOnline;

	private BroadcastReceiver mConnectivityReceiver;

	private boolean remember;

	private boolean disableRefreshButton;

	private int rpcVersion;

	private int rpcVersionAZ;

	protected String page;

	protected TransmissionRPC rpc;

	private Handler handler;

	public static interface TransmissionRpcAvailableListener
	{
		public void transmissionRpcAvailable(SessionInfo sessionInfo);

		public void uiReady();
	}

	List<TransmissionRpcAvailableListener> availabilityListeners = new ArrayList<TransmissionRpcAvailableListener>();

	private SessionInfo sessionInfo;

	private View view;

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

			AppPreferences appPreferences = new AppPreferences(this);
			remoteProfile = appPreferences.getRemote(ac);
			if (remoteProfile == null) {
				remoteProfile = new RemoteProfile(user, ac);
			}
		}
		setTitle(remoteProfile.getNick());

		if (!isOnline && !"localhost".equals(remoteProfile.getHost())) {
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

	protected void showOldRPCDialog() {
		runOnUiThread(new Runnable() {
			public void run() {
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
		});
	}

	private void setUIReady() {
		if (DEBUG) {
			Log.d(null, "UI READY");
		}

		runOnUiThread(new Runnable() {
			public void run() {
				uiReady = true;
				for (TransmissionRpcAvailableListener l : availabilityListeners) {
					l.uiReady();
				}

				if (!isOnline) {
					pauseUI();
				}
				String dataString = getIntent().getDataString();
				if (dataString != null) {
					openTorrent(getIntent().getData());
				}

				setProgressBarIndeterminateVisibility(false);
				if (tvCenter != null) {
					tvCenter.setText("");
				}

				initRefreshHandler();
			}
		});

	}

	public void initRefreshHandler() {
		if (handler == null) {
			handler = new Handler();
		}
		long interval = getRefreshInterval();
		if (interval > 0) {
			handler.postDelayed(new Runnable() {
				public void run() {
					refresh(true);
					long interval = getRefreshInterval();
					if (interval > 0) {
						handler.postDelayed(null, interval * 1000);
					}
				}
			}, interval * 1000);
		}
	}

	protected long getRefreshInterval() {
		boolean isUpdateIntervalEnabled = remoteProfile.isUpdateIntervalEnabled();
		long interval = remoteProfile.getUpdateInterval();
		if (sessionSettings != null) {
			sessionSettings.setRefreshIntervalEnabled(isUpdateIntervalEnabled);
			if (interval >= 0) {
				sessionSettings.setRefreshInterval(interval);
			}
		}
		if (!isUpdateIntervalEnabled) {
			interval = 0;
		}

		return interval;
	}

	private void refresh(boolean recentOnly) {
		rpc.getRecentTorrents(null);
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
		if (this.isOnline == isOnline) {
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
					resumeUI();
				} else {
					if (tvCenter != null) {
						tvCenter.setText(R.string.no_network_connection);
					}
					pauseUI();
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

			if (!isURLAlive(rpcUrl)) {
				AndroidUtils.showConnectionError(this, R.string.error_remote_not_found,
						false);
				return;
			}

			AppPreferences appPreferences = new AppPreferences(this);
			remoteProfile.setLastUsedOn(System.currentTimeMillis());
			if (remember) {
				appPreferences.setLastRemote(ac);
				appPreferences.addRemoteProfile(remoteProfile);
			}

			if (DEBUG) {
				Log.d(null, "rpc root = " + rpcRoot);
			}

			rpc = new TransmissionRPC(rpcUrl, user, ac, this);
			sessionInfo = SessionInfoManager.createSessionInfo(sessionSettings, rpc,
					remoteProfile, remember);
			for (TransmissionRpcAvailableListener l : availabilityListeners) {
				l.transmissionRpcAvailable(sessionInfo);
			}
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

	public String quoteIt(String s) {
		return s.replaceAll("'", "\\'").replaceAll("\\\\", "\\\\\\\\");
	}

	@SuppressLint("NewApi")
	public void openTorrent(InputStream is) {
		try {
			byte[] bs = readInputStreamAsByteArray(is);
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
		System.out.println("HANDLE MENU " + itemId);
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
				showSessionSettings();
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
				// TODO: Maybe get all of them?
				rpc.getRecentTorrents(null);

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

	private void showSessionSettings() {
		if (sessionSettings == null) {
			return;
		}
		DialogFragmentSessionSettings dlg = new DialogFragmentSessionSettings();
		Bundle bundle = new Bundle();
		bundle.putSerializable(SessionSettings.class.getName(), sessionSettings);
		dlg.setArguments(bundle);
		dlg.show(getSupportFragmentManager(), "SessionSettings");
	}

	@Override
	// For Android 2.x
	public void onCreateContextMenu(ContextMenu menu, View v,
			ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);

		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.menu_context, menu);
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
				executeSearch(query);
				return true;
			}

			@Override
			public boolean onQueryTextChange(String newText) {
				return false;
			}
		});
	}

	@Override
	public boolean onSearchRequested() {
		Bundle appData = new Bundle();
		if (rpcVersionAZ >= 0) {
			appData.putString("com.vuze.android.remote.searchsource", rpcRoot);
			appData.putString("com.vuze.android.remote.ac", remoteProfile.getAC());
		}
		startSearch(null, false, appData, false);
		return true;
	}

	private static byte[] readInputStreamAsByteArray(InputStream is)
			throws IOException {
		int available = is.available();
		if (available <= 0) {
			available = 32 * 1024;
		}
		ByteArrayOutputStream baos = new ByteArrayOutputStream(available);

		byte[] buffer = new byte[32 * 1024];

		try {
			while (true) {

				int len = is.read(buffer);

				if (len <= 0) {

					break;
				}

				baos.write(buffer, 0, len);
			}

			return (baos.toByteArray());

		} finally {

			is.close();
		}
	}

	public static boolean isURLAlive(String URLName) {
		try {
			HttpURLConnection.setFollowRedirects(false);
			HttpURLConnection con = (HttpURLConnection) new URL(URLName).openConnection();
			con.setConnectTimeout(2000);
			con.setReadTimeout(2000);
			con.setRequestMethod("HEAD");
			con.getResponseCode();
			//	Log.d(null, "conn result=" + con.getResponseCode() + ";" + con.getResponseMessage());
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	/* (non-Javadoc)
	 * @see com.vuze.android.remote.dialog.DialogFragmentSessionSettings.SessionSettingsListener#sessionSettingsChanged(com.vuze.android.remote.SessionSettings)
	 */
	@Override
	public void sessionSettingsChanged(SessionSettings newSettings) {

		if (sessionSettings == null) {
			// Should not have happened -- dialog can only show when sessionSettings is non-null
			return;
		}
		if (newSettings.isRefreshIntervalIsEnabled() != sessionSettings.isRefreshIntervalIsEnabled()
				|| newSettings.getRefreshInterval() != sessionSettings.getRefreshInterval()) {
			if (!newSettings.isRefreshIntervalIsEnabled()) {
				handler.removeCallbacksAndMessages(null);
				handler = null;
			} else {
				runOnUiThread(new Runnable() {
					public void run() {
						if (handler == null) {
							initRefreshHandler();
						}
					}
				});
			}
			remoteProfile.setUpdateInterval(newSettings.getRefreshInterval());
			remoteProfile.setUpdateIntervalEnabled(newSettings.isRefreshIntervalIsEnabled());
			sessionInfo.saveProfileIfRemember(this);
		}
		Map<String, Object> changes = new HashMap<String, Object>();
		if (newSettings.isDLAuto() != sessionSettings.isDLAuto()) {
			changes.put("speed-limit-down-enabled", newSettings.isDLAuto());
		}
		if (newSettings.isULAuto() != sessionSettings.isULAuto()) {
			changes.put("speed-limit-up-enabled", newSettings.isULAuto());
		}
		if (newSettings.getUlSpeed() != sessionSettings.getUlSpeed()) {
			changes.put("speed-limit-up", newSettings.getUlSpeed());
		}
		if (newSettings.getDlSpeed() != sessionSettings.getDlSpeed()) {
			changes.put("speed-limit-down", newSettings.getDlSpeed());
		}
		if (changes.size() > 0) {
			rpc.updateSettings(changes);
		}
		sessionSettings = newSettings;
		sessionInfo.setSessionSettings(newSettings);

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

	@Override
	public void moveDataHistoryChanged(ArrayList<String> history) {
		if (remoteProfile == null) {
			return;
		}
		remoteProfile.setSavePathHistory(history);
		sessionInfo.saveProfileIfRemember(this);
	}

	/*
		@SuppressWarnings("rawtypes")
		public void selectionChanged(final List<Map> selectedTorrentFields,
				boolean haveActiveSel, boolean havePausedSel) {
			Log.d(null, "SELECTION CHANGED " + getCheckedItemCount(listview));
			TorrentViewActivity.this.haveActiveSel = haveActiveSel;
			TorrentViewActivity.this.havePausedSel = havePausedSel;

			runOnUiThread(new Runnable() {
				public void run() {
					if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
						selectionChangedHoneyComb();
					}
				}

				@TargetApi(Build.VERSION_CODES.HONEYCOMB)
				private void selectionChangedHoneyComb() {
					if (listview.getCheckedItemCount() == 0) {
						if (mActionMode != null) {
							mActionMode.finish();
						} else {
							supportInvalidateOptionsMenu();
						}
						return;
					}

					showContextualActions();
				}

			});
		}
	*/
	public void updateSpeed(final long downSpeed, final long upSpeed) {
		runOnUiThread(new Runnable() {
			public void run() {
				if (downSpeed <= 0) {
					tvDownSpeed.setVisibility(View.GONE);
				} else {
					tvDownSpeed.setText(DisplayFormatters.formatByteCountToKiBEtcPerSec(downSpeed));
					tvDownSpeed.setVisibility(View.VISIBLE);
				}
				if (upSpeed <= 0) {
					tvUpSpeed.setVisibility(View.GONE);
				} else {
					tvUpSpeed.setText(DisplayFormatters.formatByteCountToKiBEtcPerSec(upSpeed));
					tvUpSpeed.setVisibility(View.VISIBLE);
				}
			}
		});
	}

	/*
		public void updateTorrentStates(boolean haveActive, boolean havePaused,
				boolean haveActiveSel, boolean havePausedSel) {
			TorrentViewActivity.this.haveActive = haveActive;
			TorrentViewActivity.this.havePaused = havePaused;
			TorrentViewActivity.this.haveActiveSel = haveActiveSel;
			TorrentViewActivity.this.havePausedSel = havePausedSel;

			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
				invalidateOptionsMenuHC();
			}
		}

	*/

	@SuppressWarnings("rawtypes")
	public void sessionPropertiesUpdated(Map map) {
		boolean firstCall = sessionSettings == null;
		SessionSettings settings = new SessionSettings();
		settings.setDLIsAuto(MapUtils.getMapBoolean(map,
				"speed-limit-down-enabled", true));
		settings.setULIsAuto(MapUtils.getMapBoolean(map, "speed-limit-up-enabled",
				true));
		settings.setDownloadDir(MapUtils.getMapString(map, "download-dir", null));
		long refreshRateSecs = MapUtils.getMapLong(map, "refresh_rate", 0);
		long profileRefeshInterval = remoteProfile.getUpdateInterval();
		long newRefreshRate = refreshRateSecs == 0 && profileRefeshInterval > 0
				? profileRefeshInterval : refreshRateSecs;
		if (refreshRateSecs != profileRefeshInterval || sessionSettings == null) {
			settings.setRefreshIntervalEnabled(refreshRateSecs > 0);
		} else {
			settings.setRefreshIntervalEnabled(sessionSettings.isRefreshIntervalIsEnabled());
		}
		settings.setRefreshInterval(newRefreshRate);

		settings.setDlSpeed(MapUtils.getMapLong(map, "speed-limit-down", 0));
		settings.setUlSpeed(MapUtils.getMapLong(map, "speed-limit-up", 0));
		if (firstCall) {
			// first time: track RPC version
			rpcVersion = MapUtils.getMapInt(map, "rpc-version", -1);
			rpcVersionAZ = MapUtils.getMapInt(map, "az-rpc-version", -1);
			if (rpcVersionAZ < 0 && map.containsKey("az-version")) {
				rpcVersionAZ = 0;
			}
			page = "RPC v" + rpcVersion + "/" + rpcVersionAZ;

			if (rpcVersion < 14) {
				showOldRPCDialog();
			}
		}
		TorrentViewActivity.this.sessionSettings = settings;
		sessionInfo.setSessionSettings(settings);

		if (firstCall) {
			setUIReady();
		}

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			invalidateOptionsMenuHC();
		}
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

	public void addRpcAvailableListener(TransmissionRpcAvailableListener l) {
		availabilityListeners.add(l);
		if (rpc != null) {
			l.transmissionRpcAvailable(sessionInfo);
		}
	}

	@JavascriptInterface
	public boolean executeSearch(String search) {
		Intent myIntent = new Intent(Intent.ACTION_SEARCH);
		myIntent.setClass(this, MetaSearch.class);
		if (remoteProfile.getRemoteType() == RemoteProfile.TYPE_LOOKUP) {
			Bundle bundle = new Bundle();
			bundle.putString("com.vuze.android.remote.searchsource", rpcRoot);
			bundle.putString("com.vuze.android.remote.ac", remoteProfile.getAC());
			myIntent.putExtra(SearchManager.APP_DATA, bundle);
		}
		myIntent.putExtra(SearchManager.QUERY, search);

		startActivity(myIntent);
		return true;
	}

	@Override
	public void onTorrentSelectedListener(long[] ids) {
		// The user selected the headline of an article from the HeadlinesFragment
		// Do something here to display that article

		TorrentDetailsFragment articleFrag = (TorrentDetailsFragment) getSupportFragmentManager().findFragmentById(
				R.id.fragment2);

		if (articleFrag != null) {
			// If article frag is available, we're in two-pane layout...

			// Call a method in the TorrentDetailsFragment to update its content
			articleFrag.setTorrentIDs(ids);
		} else if (ids != null && ids.length == 1) {
			Intent intent = new Intent(getApplicationContext(),
					TorrentDetailsActivity.class);
			intent.putExtra("TorrentID", ids[0]);
			intent.putExtra("RemoteProfileID", remoteProfile.getID());
			startActivity(intent);
		}
	}
}
