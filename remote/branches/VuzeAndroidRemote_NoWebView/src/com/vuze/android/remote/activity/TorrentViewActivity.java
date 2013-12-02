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
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.NavUtils;
import android.support.v4.app.TaskStackBuilder;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Base64;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.*;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View.OnLongClickListener;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebSettings;
import android.widget.AbsListView.MultiChoiceModeListener;
import android.widget.*;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.SearchView.OnQueryTextListener;

import com.aelitis.azureus.util.JSONUtils;
import com.aelitis.azureus.util.MapUtils;
import com.google.analytics.tracking.android.MapBuilder;
import com.vuze.android.remote.*;
import com.vuze.android.remote.dialog.*;
import com.vuze.android.remote.dialog.DialogFragmentFilterBy.FilterByDialogListener;
import com.vuze.android.remote.dialog.DialogFragmentMoveData.MoveDataDialogListener;
import com.vuze.android.remote.dialog.DialogFragmentOpenTorrent.OpenTorrentDialogListener;
import com.vuze.android.remote.dialog.DialogFragmentSessionSettings.SessionSettingsListener;
import com.vuze.android.remote.dialog.DialogFragmentSortBy.SortByDialogListener;
import com.vuze.android.remote.rpc.*;

public class TorrentViewActivity
	extends FragmentActivity
	implements OpenTorrentDialogListener, FilterByDialogListener,
	SortByDialogListener, SessionSettingsListener, MoveDataDialogListener,
	TorrentListReceivedListener, SessionSettingsReceivedListener,
	TorrentAddedReceivedListener
{
	private SearchView mSearchView;

	protected ActionMode mActionMode;

	public final static int FILECHOOSER_RESULTCODE = 1;

	private static final boolean DEBUG = AndroidUtils.DEBUG;

	private boolean haveActive;

	private boolean havePaused;

	private ActionMode.Callback mActionModeCallback;

	protected boolean haveActiveSel;

	protected boolean havePausedSel;

	private EditText filterEditText;

	private String rpcRoot;

	protected boolean uiReady = false;

	private TextView tvUpSpeed;

	private TextView tvDownSpeed;

	private TextView tvTorrentCount;

	private TextView tvFilteringBy;

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

	private TransmissionRPC rpc;

	private ListView listview;

	private TorrentAdapter adapter;

	private Handler handler;

	private RefreshReplyMapReceivedListener refreshActiveOnReply;

	/* (non-Javadoc)
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

		refreshActiveOnReply = new RefreshReplyMapReceivedListener(true);

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			setupHoneyComb();
		}
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
			setupIceCream();
		}

		setContentView(R.layout.activity_torrent_view);

		// setup view ids now because listeners below may trigger as soon as we get them
		tvUpSpeed = (TextView) findViewById(R.id.wvUpSpeed);
		tvDownSpeed = (TextView) findViewById(R.id.wvDnSpeed);
		tvFilteringBy = (TextView) findViewById(R.id.wvFilteringBy);
		tvTorrentCount = (TextView) findViewById(R.id.wvTorrentCount);
		tvCenter = (TextView) findViewById(R.id.wvCenter);

		filterEditText = (EditText) findViewById(R.id.filterText);
		listview = (ListView) findViewById(R.id.torlist_listview);

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

		remember = extras.getBoolean("com.vuze.android.remote.remember");
		String remoteAsJSON = extras.getString("remote.json");
		if (remoteAsJSON != null) {
			remoteProfile = new RemoteProfile(JSONUtils.decodeJSON(remoteAsJSON));
		} else {

			String ac = extras.getString("com.vuze.android.remote.ac");
			String user = extras.getString("com.vuze.android.remote.user");

			AppPreferences appPreferences = new AppPreferences(this);
			remoteProfile = appPreferences.getRemote(ac);
			if (remoteProfile == null) {
				remoteProfile = new RemoteProfile(user, ac);
			}
		}
		setTitle(remoteProfile.getNick());

		filterEditText.addTextChangedListener(new TextWatcher() {

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				Filter filter = adapter.getFilter();
				filter.filter(s);
			}

			@Override
			public void beforeTextChanged(CharSequence s, int start, int count,
					int after) {
			}

			@Override
			public void afterTextChanged(Editable s) {
			}
		});

		listview.setItemsCanFocus(false);
		listview.setClickable(true);
		listview.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
		adapter = new TorrentAdapter(this);

		listview.setAdapter(adapter);

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			setupHoneyComb(listview);
		}
		listview.setOnItemClickListener(new AdapterView.OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> parent, final View view,
					int position, long id) {
				//Object item = parent.getItemAtPosition(position);

				Log.d(null,
						position + "CLICKED; checked? " + listview.isItemChecked(position));

				selectionChanged(null, haveActiveSel, havePausedSel);
				//listview.setItemChecked(position, !listview.isItemChecked(position));
			}

		});
		listview.setOnItemSelectedListener(new OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> parent, View view,
					int position, long id) {
				selectionChanged(null, haveActiveSel, havePausedSel);
			}

			@Override
			public void onNothingSelected(AdapterView<?> parent) {
				selectionChanged(null, haveActiveSel, havePausedSel);
			}
		});

		//registerForContextMenu(listview);

		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
			// old style menu
			registerForContextMenu(listview);
		} else {
			listview.setOnLongClickListener(new OnLongClickListener() {
				public boolean onLongClick(View view) {
					return showContextualActions();
				}
			});
		}

		setProgressBarIndeterminateVisibility(true);

		if (!isOnline) {
			AndroidUtils.showConnectionError(this, R.string.no_network_connection,
					false);
			return;
		}

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
		uiReady = true;
		if (DEBUG) {
			Log.d(null, "UI READY");
		}
		if (!isOnline) {
			pauseUI();
		}
		String dataString = getIntent().getDataString();
		if (dataString != null) {
			openTorrent(getIntent().getData());
		}

		String sortBy = remoteProfile.getSortBy();
		if (sortBy != null) {
			sortBy(sortBy, false);
		}

		String filterBy = remoteProfile.getFilterBy();
		if (filterBy != null) {
			String[] valuesArray = getResources().getStringArray(
					R.array.filterby_list_values);
			String[] stringArray = getResources().getStringArray(
					R.array.filterby_list);
			for (int i = 0; i < valuesArray.length; i++) {
				String value = valuesArray[i];
				if (value.equals(filterBy)) {
					filterBy(filterBy, stringArray[i], false);
					break;
				}
			}
		}

		runOnUiThread(new Runnable() {
			public void run() {
				setProgressBarIndeterminateVisibility(false);
				tvCenter.setText("");
				
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
		//rpc.getRecentTorrents(this);
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
					if (!initialValue) {
						tvCenter.setText("");
					}
					resumeUI();
				} else {
					tvCenter.setText(R.string.no_network_connection);
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
		} catch (Exception e) {
			VuzeEasyTracker.getInstance(this).logError(this, e);
			if (DEBUG) {
				e.printStackTrace();
			}
		}
	}

	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	protected boolean showContextualActions() {
		if (mActionMode != null) {
			mActionMode.invalidate();
			return false;
		}

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			// Start the CAB using the ActionMode.Callback defined above
			mActionMode = startActionMode(mActionModeCallback);
		}
		return true;
	}

	@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
	private void setupJellyBean(WebSettings webSettings) {
		webSettings.setAllowUniversalAccessFromFileURLs(true);
	}

	@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
	private void setupIceCream() {
		getActionBar().setHomeButtonEnabled(true);
	}

	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	private void setupHoneyComb(ListView listview) {
		listview.setMultiChoiceModeListener(new MultiChoiceModeListener() {
			// Called when the action mode is created; startActionMode() was called
			@Override
			public boolean onCreateActionMode(ActionMode mode, Menu menu) {
				// Inflate a menu resource providing context menu items
				MenuInflater inflater = mode.getMenuInflater();
				inflater.inflate(R.menu.menu_context, menu);
				return true;
			}

			// Called each time the action mode is shown. Always called after onCreateActionMode, but
			// may be called multiple times if the mode is invalidated.
			@Override
			public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
				MenuItem menuMove = menu.findItem(R.id.action_sel_move);
				menuMove.setEnabled(TorrentViewActivity.this.listview.getCheckedItemCount() > 0);

				MenuItem menuStart = menu.findItem(R.id.action_sel_start);
				menuStart.setVisible(havePausedSel);

				MenuItem menuStop = menu.findItem(R.id.action_sel_stop);
				menuStop.setVisible(haveActiveSel);

				fixupMenu(menu);

				return true;
			}

			// Called when the user selects a contextual menu item
			@Override
			public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
				if (TorrentViewActivity.this.handleMenu(item.getItemId())) {
					return true;
				}
				return false;
			}

			// Called when the user exits the action mode
			@Override
			public void onDestroyActionMode(ActionMode mode) {
				mActionMode = null;
				clearChecked();
			}

			@Override
			public void onItemCheckedStateChanged(ActionMode mode, int position,
					long id, boolean checked) {
			}
		});

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

		mActionModeCallback = new ActionMode.Callback() {

			// Called when the action mode is created; startActionMode() was called
			@Override
			public boolean onCreateActionMode(ActionMode mode, Menu menu) {
				// Inflate a menu resource providing context menu items
				MenuInflater inflater = mode.getMenuInflater();
				inflater.inflate(R.menu.menu_context, menu);
				return true;
			}

			// Called each time the action mode is shown. Always called after onCreateActionMode, but
			// may be called multiple times if the mode is invalidated.
			@Override
			public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
				MenuItem menuMove = menu.findItem(R.id.action_sel_move);
				menuMove.setEnabled(listview.getCheckedItemCount() > 0);

				MenuItem menuStart = menu.findItem(R.id.action_sel_start);
				menuStart.setVisible(havePausedSel);

				MenuItem menuStop = menu.findItem(R.id.action_sel_stop);
				menuStop.setVisible(haveActiveSel);

				fixupMenu(menu);

				return true;
			}

			// Called when the user selects a contextual menu item
			@Override
			public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
				if (TorrentViewActivity.this.handleMenu(item.getItemId())) {
					return true;
				}
				return false;
			}

			// Called when the user exits the action mode
			@Override
			public void onDestroyActionMode(ActionMode mode) {
				mActionMode = null;

				listview.clearChoices();
				// Not sure why ListView doesn't invalidate by default
				adapter.notifyDataSetInvalidated();
			}
		};

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
			rpc.addTorrentByMeta(metainfo, havePaused, this);
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
			case R.id.action_filterby:
				openFilterByDialog();
				return true;
			case R.id.action_filter:
				boolean newVisibility = filterEditText.getVisibility() != View.VISIBLE;
				filterEditText.setVisibility(newVisibility ? View.VISIBLE : View.GONE);
				if (newVisibility) {
					filterEditText.requestFocus();
					InputMethodManager mgr = (InputMethodManager) this.getSystemService(Context.INPUT_METHOD_SERVICE);
					mgr.showSoftInput(filterEditText, InputMethodManager.SHOW_IMPLICIT);
					VuzeEasyTracker.getInstance(TorrentViewActivity.this).send(
							MapBuilder.createEvent("uiAction", "ViewShown", "FilterBox", null).build());
				} else {
					InputMethodManager mgr = (InputMethodManager) this.getSystemService(Context.INPUT_METHOD_SERVICE);
					mgr.hideSoftInputFromWindow(filterEditText.getWindowToken(), 0);
				}
				return true;
			case R.id.action_settings:
				showSessionSettings();
				return true;
			case R.id.action_sortby:
				openSortByDialog();
				return true;
			case R.id.action_add_torrent:
				DialogFragmentOpenTorrent dlg = new DialogFragmentOpenTorrent();
				dlg.show(getSupportFragmentManager(), "OpenTorrentDialog");
				break;
			case R.id.action_search:
				onSearchRequested();
				return true;

			case R.id.action_context:
				// TODO: openContextMenu(myWebView);
				return true;

			case R.id.action_logout:
				new RemoteUtils(TorrentViewActivity.this).openRemoteList(getIntent());
				finish();
				return true;

			case R.id.action_start_all:
				rpc.startTorrents(null, false, refreshActiveOnReply);
				return true;

			case R.id.action_stop_all:
				rpc.stopTorrents(null, refreshActiveOnReply);
				return true;

			case R.id.action_refresh:
				// TODO: Maybe get all of them?
				rpc.getRecentTorrents(this);

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

				// Start of Context Menu Items
			case R.id.action_sel_remove:{
				Map[] maps = getSelectedTorrentMaps();
				for (Map map : maps) {
					long id = MapUtils.getMapLong(map, "id", -1);
					String name = MapUtils.getMapString(map, "name", "");
					// TODO: One at a time!
					showConfirmDeleteDialog(name, id);
				}
				return true;
			}
			case R.id.action_sel_start: {
				long[] ids = getSelectedIDs();
				rpc.startTorrents(ids, false, refreshActiveOnReply);
				return true;
			}
			case R.id.action_sel_forcestart: {
				long[] ids = getSelectedIDs();
				rpc.startTorrents(ids, true, refreshActiveOnReply);
				return true;
			}
			case R.id.action_sel_stop: {
				long[] ids = getSelectedIDs();
				rpc.stopTorrents(ids, refreshActiveOnReply);
				return true;
			}
			case R.id.action_sel_relocate:
				openMoveDataDialog();
				return true;
			case R.id.action_sel_move_top: {
				rpc.simpleRpcCall("queue-move-top", getSelectedIDs(),
						refreshActiveOnReply);
				return true;
			}
			case R.id.action_sel_move_up: {
				rpc.simpleRpcCall("queue-move-up", getSelectedIDs(),
						refreshActiveOnReply);
				return true;
			}
			case R.id.action_sel_move_down: {
				rpc.simpleRpcCall("queue-move-down", getSelectedIDs(),
						refreshActiveOnReply);
				return true;
			}
			case R.id.action_sel_move_bottom: {
				rpc.simpleRpcCall("queue-move-bottom", getSelectedIDs(),
						refreshActiveOnReply);
				return true;
			}
		}
		return false;
	}

	@SuppressWarnings("rawtypes")
	private void openMoveDataDialog() {
		if (getCheckedItemCount(listview) == 0) {
			return;
		}
		DialogFragmentMoveData dlg = new DialogFragmentMoveData();
		Bundle bundle = new Bundle();
		Map mapTorrent = getFirstSelected();
		if (mapTorrent == null) {
			return;
		}

		bundle.putLong("id",  MapUtils.getMapLong(mapTorrent, "id", -1));
		bundle.putString("name", "" + mapTorrent.get("name"));

		String defaultDownloadDir = sessionSettings.getDownloadDir();
		String downloadDir = MapUtils.getMapString(mapTorrent, "downloadDir",
				defaultDownloadDir);
		bundle.putString("downloadDir", downloadDir);
		ArrayList<String> history = new ArrayList<String>();
		if (defaultDownloadDir != null) {
			history.add(defaultDownloadDir);
		}

		List<String> saveHistory = remoteProfile.getSavePathHistory();
		for (String s : saveHistory) {
			if (!history.contains(s)) {
				history.add(s);
			}
		}
		bundle.putStringArrayList("history", history);
		dlg.setArguments(bundle);
		dlg.show(getSupportFragmentManager(), "MoveDataDialog");
	}

	private int getCheckedItemCount(ListView listview) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			return getCheckedItemCount_11(listview);
		}
		return getCheckedItemCount_Pre11(listview);
	}

	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	private int getCheckedItemCount_11(ListView listview) {
		return listview.getCheckedItemCount();
	}

	private int getCheckedItemCount_Pre11(ListView listview) {
		int total = 0;
		SparseBooleanArray checked = listview.getCheckedItemPositions();
		int size = checked.size(); // number of name-value pairs in the array
		for (int i = 0; i < size; i++) {
			int key = checked.keyAt(i);
			boolean value = checked.get(key);
			if (value) {
				total++;
			}
		}
		return total;
	}

	private Map<?, ?>[] getSelectedTorrentMaps() {
		SparseBooleanArray checked = listview.getCheckedItemPositions();
		int size = checked.size(); // number of name-value pairs in the array
		Map<?, ?>[] torrentMaps = new Map<?, ?>[size];
		int pos = 0;
		for (int i = 0; i < size; i++) {
			int key = checked.keyAt(i);
			boolean value = checked.get(key);
			if (value) {
				Map<?, ?> mapTorrent = (Map<?, ?>) listview.getItemAtPosition(key);
				if (mapTorrent != null) {
					torrentMaps[pos] = mapTorrent;
					pos++;
				}
			}
		}
		if (pos < size) {
			Map<?, ?>[] torrents = new Map<?, ?>[pos];
			System.arraycopy(torrentMaps, 0, torrents, 0, pos);
			return torrents;
		}
		return torrentMaps;
	}

	private long[] getSelectedIDs() {
		SparseBooleanArray checked = listview.getCheckedItemPositions();
		int size = checked.size(); // number of name-value pairs in the array
		long[] moreIDs = new long[size];
		int pos = 0;
		for (int i = 0; i < size; i++) {
			int key = checked.keyAt(i);
			boolean value = checked.get(key);
			if (value) {
				Map<?, ?> mapTorrent = (Map<?, ?>) listview.getItemAtPosition(key);
				long id = MapUtils.getMapLong(mapTorrent, "id", -1);
				if (id >= 0) {
					moreIDs[pos] = id;
					pos++;
				}
			}
		}
		if (pos < size) {
			long[] ids = new long[pos];
			System.arraycopy(moreIDs, 0, ids, 0, pos);
			return ids;
		}
		return moreIDs;
	}

	private Map<?, ?> getFirstSelected() {
		SparseBooleanArray checked = listview.getCheckedItemPositions();
		int size = checked.size(); // number of name-value pairs in the array
		for (int i = 0; i < size; i++) {
			int key = checked.keyAt(i);
			boolean value = checked.get(key);
			if (value) {
				return (Map<?, ?>) listview.getItemAtPosition(key);
			}
		}
		return null;
	}

	private void clearChecked() {
		SparseBooleanArray checked = listview.getCheckedItemPositions();
		int size = checked.size(); // number of name-value pairs in the array
		for (int i = 0; i < size; i++) {
			int key = checked.keyAt(i);
			boolean value = checked.get(key);
			if (value) {
				listview.setItemChecked(key, false);
			}
		}
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

	private void openSortByDialog() {
		DialogFragmentSortBy dlg = new DialogFragmentSortBy();
		dlg.show(getSupportFragmentManager(), "OpenSortDialog");
	}

	private void openFilterByDialog() {
		DialogFragmentFilterBy dlg = new DialogFragmentFilterBy();
		dlg.show(getSupportFragmentManager(), "OpenFilterDialog");
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

	private void fixupMenu(Menu menu) {
		for (int i = 0; i < menu.size(); i++) {
			MenuItem item = menu.getItem(i);
			Drawable icon = item.getIcon();
			if (icon != null) {
				icon.setAlpha(item.isEnabled() ? 255 : 64);
			}
		}
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

		MenuItem menuStartAll = menu.findItem(R.id.action_start_all);
		if (menuStartAll != null) {
			menuStartAll.setEnabled(havePaused);
		}
		MenuItem menuStopAll = menu.findItem(R.id.action_stop_all);
		if (menuStopAll != null) {
			menuStopAll.setEnabled(haveActive);
		}
		MenuItem menuSessionSettings = menu.findItem(R.id.action_settings);
		if (menuSessionSettings != null) {
			menuSessionSettings.setEnabled(sessionSettings != null);
		}

		MenuItem menuContext = menu.findItem(R.id.action_context);
		if (menuContext != null) {
			menuContext.setVisible(getCheckedItemCount(listview) > 0);
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

		fixupMenu(menu);

		return super.onPrepareOptionsMenu(menu);
	}

	@Override
	public void filterBy(String filterMode, final String name, boolean save) {
		// TODO
		runOnUiThread(new Runnable() {
			public void run() {
				tvFilteringBy.setText(name);
			}
		});
		if (save) {
			remoteProfile.setFilterBy(filterMode);
			saveProfileIfRemember();
		}
	}

	private void saveProfileIfRemember() {
		if (remember) {
			AppPreferences appPreferences = new AppPreferences(this);
			appPreferences.addRemoteProfile(remoteProfile);
		}
	}

	@Override
	public void sortBy(String sortType, boolean save) {
		// TODO
		
		if (save) {
			remoteProfile.setSortBy(sortType);
			saveProfileIfRemember();
		}
	}

	@Override
	public void flipSortOrder() {
		// TODO
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
				// TODO: jsInterface.executeSearch(query);
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
			saveProfileIfRemember();
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

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			invalidateOptionsMenuHC();
		}
	}

	public void moveDataTo(long id, String s) {
		rpc.moveTorrent(id, s, refreshActiveOnReply);

		VuzeEasyTracker.getInstance(this).send(
				MapBuilder.createEvent("RemoteAction", "MoveData", null, null).build());
	}

	@Override
	public void moveDataHistoryChanged(ArrayList<String> history) {
		if (remoteProfile == null) {
			return;
		}
		remoteProfile.setSavePathHistory(history);
		saveProfileIfRemember();
	}

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

	public void deleteTorrent(long torrentID) {
		rpc.removeTorrent(torrentID, true, refreshActiveOnReply);
	}

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

	public void updateTorrentCount(final long total) {
		runOnUiThread(new Runnable() {
			public void run() {
				if (total == 0) {
					tvTorrentCount.setText("");
				} else {
					tvTorrentCount.setText(total + " torrents");
				}
			}
		});
	}

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

		if (firstCall) {
			rpc.getAllTorrents(this);
			setUIReady();
		}

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			invalidateOptionsMenuHC();
		}
	}

	/* (non-Javadoc)
	 * @see com.vuze.android.remote.rpc.TorrentListReceivedListener#rpcTorrentListReceived(java.util.List)
	 */
	@Override
	public void rpcTorrentListReceived(final List listTorrents) {
		if (DEBUG) {
			Log.d(null, "got TorrentList: " + listTorrents.size());
		}
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				adapter.addAll(listTorrents);
			}
		});
	}

	@Override
	public void torrentAdded(Map mapTorrentAdded, boolean duplicate) {
		rpc.getRecentTorrents(this);
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

	public class RefreshReplyMapReceivedListener
		implements ReplyMapReceivedListener
	{

		private boolean recentOnly;

		public RefreshReplyMapReceivedListener(boolean recentOnly) {
			this.recentOnly = recentOnly;
		}

		@Override
		public void rpcSuccess(String id, Map optionalMap) {
			refresh(recentOnly);
		}

		@Override
		public void rpcError(String id, Exception e) {
		}

		@Override
		public void rpcFailure(String id, String message) {
		}

	}

	public boolean showConfirmDeleteDialog(String name, final long torrentID) {
		Resources res = getResources();
		String message = res.getString(R.string.dialog_delete_message, name);

		new AlertDialog.Builder(this).setTitle(R.string.dialog_delete_title).setMessage(
				message).setPositiveButton(R.string.dialog_delete_button_remove,
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						deleteTorrent(torrentID);
					}
				}).setNegativeButton(android.R.string.cancel,
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
					}
				}).setIcon(android.R.drawable.ic_dialog_alert).show();
		return true;
	}

}
