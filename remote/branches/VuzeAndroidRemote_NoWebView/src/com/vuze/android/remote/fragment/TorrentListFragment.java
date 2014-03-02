package com.vuze.android.remote.fragment;

import java.util.List;
import java.util.Map;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.os.*;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.format.DateUtils;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.*;
import android.view.ActionMode.Callback;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView.MultiChoiceModeListener;
import android.widget.*;
import android.widget.AdapterView.OnItemLongClickListener;

import com.aelitis.azureus.util.MapUtils;
import com.google.analytics.tracking.android.MapBuilder;
import com.handmark.pulltorefresh.library.*;
import com.handmark.pulltorefresh.library.PullToRefreshBase.Mode;
import com.handmark.pulltorefresh.library.PullToRefreshBase.OnPullEventListener;
import com.handmark.pulltorefresh.library.PullToRefreshBase.OnRefreshListener;
import com.handmark.pulltorefresh.library.PullToRefreshBase.State;
import com.vuze.android.remote.*;
import com.vuze.android.remote.AndroidUtils.ValueStringArray;
import com.vuze.android.remote.R;
import com.vuze.android.remote.dialog.*;
import com.vuze.android.remote.dialog.DialogFragmentFilterBy.FilterByDialogListener;
import com.vuze.android.remote.dialog.DialogFragmentSortBy.SortByDialogListener;
import com.vuze.android.remote.fragment.TorrentListAdapter.TorrentFilter;
import com.vuze.android.remote.rpc.TorrentListReceivedListener;

public class TorrentListFragment
	extends Fragment
	implements TorrentListReceivedListener, FilterByDialogListener,
	SortByDialogListener, SessionInfoListener, ActionModeBeingReplacedListener
{
	public interface OnTorrentSelectedListener
	{
		public void onTorrentSelectedListener(
				TorrentListFragment torrentListFragment, long[] ids, boolean inMultiMode);
	}

	private OnTorrentSelectedListener mCallback;

	private static final boolean DEBUG = AndroidUtils.DEBUG;

	private ListView listview;

	protected ActionMode mActionMode;

	private TorrentListAdapter adapter;

	private SessionInfo sessionInfo;

	private EditText filterEditText;

	private boolean haveActive;

	private boolean havePaused;

	private Callback mActionModeCallback;

	private TextView tvFilteringBy;

	private TextView tvTorrentCount;

	private PullToRefreshListView pullListView;

	private long lastUpdated;

	private boolean actionModeBeingReplaced;

	private boolean rebuildActionMode;

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);

		if (activity instanceof OnTorrentSelectedListener) {
			mCallback = (OnTorrentSelectedListener) activity;
		}

	}

	@Override
	public void uiReady() {
		/*
		sessionInfo.getRpc().simpleRpcCall("rcm-is-enabled" , new ReplyMapReceivedListener() {
			
			@Override
			public void rpcSuccess(String id, Map optionalMap) {
				System.out.println("rcm-is-enabled: " + optionalMap);
			}
			
			@Override
			public void rpcFailure(String id, String message) {
			}
			
			@Override
			public void rpcError(String id, Exception e) {
			}
		});
		sessionInfo.getRpc().simpleRpcCall("rcm-get-list" , new ReplyMapReceivedListener() {
			
			@Override
			public void rpcSuccess(String id, Map optionalMap) {
				System.out.println("rcm-get-list: " + optionalMap);
			}
			
			@Override
			public void rpcFailure(String id, String message) {
			}
			
			@Override
			public void rpcError(String id, Exception e) {
			}
		});
		*/

		sessionInfo.getRpc().getAllTorrents(null);

		RemoteProfile remoteProfile = sessionInfo.getRemoteProfile();

		String[] sortBy = remoteProfile.getSortBy();
		Boolean[] sortOrder = remoteProfile.getSortOrder();
		if (sortBy != null) {
			sortBy(sortBy, sortOrder, false);
		}

		long filterBy = remoteProfile.getFilterBy();
		if (filterBy > 10) {
			Map<?, ?> tag = sessionInfo.getTag(filterBy);
			
			filterBy(filterBy, MapUtils.getMapString(tag, "name", "fooo"), false);
		} else if (filterBy >= 0) {
			final ValueStringArray filterByList = AndroidUtils.getValueStringArray(
					getResources(), R.array.filterby_list);
			for (int i = 0; i < filterByList.values.length; i++) {
				long val = filterByList.values[i];
				if (val == filterBy) {
					filterBy(filterBy, filterByList.strings[i], false);
					break;
				}
			}
		}

	}

	@Override
	public void transmissionRpcAvailable(SessionInfo sessionInfo) {
		TorrentListFragment.this.sessionInfo = sessionInfo;
		adapter.setSessionInfo(sessionInfo);
		sessionInfo.addTorrentListReceivedListener(this);
	}

	/* (non-Javadoc)
	 * @see android.support.v4.app.Fragment#onCreateView(android.view.LayoutInflater, android.view.ViewGroup, android.os.Bundle)
	 */
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {

		View view = inflater.inflate(R.layout.frag_torrent_list, container, false);

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			setupHoneyComb();
		}

		View oListView = view.findViewById(android.R.id.list);
		if (oListView instanceof ListView) {
			listview = (ListView) oListView;
		} else if (oListView instanceof PullToRefreshListView) {
			pullListView = (PullToRefreshListView) oListView;
			listview = pullListView.getRefreshableView();
			pullListView.setOnPullEventListener(new OnPullEventListener<ListView>() {
				private Handler pullRefreshHandler;

				@Override
				public void onPullEvent(PullToRefreshBase<ListView> refreshView,
						State state, Mode direction) {
					if (state == State.PULL_TO_REFRESH) {
						if (pullRefreshHandler != null) {
							pullRefreshHandler.removeCallbacks(null);
							pullRefreshHandler = null;
						}
						pullRefreshHandler = new Handler(Looper.getMainLooper());

						pullRefreshHandler.postDelayed(new Runnable() {
							@Override
							public void run() {
								FragmentActivity activity = getActivity();
								if (activity == null) {
									return;
								}
								long sinceMS = System.currentTimeMillis() - lastUpdated;
								String since = DateUtils.getRelativeDateTimeString(activity,
										lastUpdated, DateUtils.SECOND_IN_MILLIS,
										DateUtils.WEEK_IN_MILLIS, 0).toString();
								String s = activity.getResources().getString(
										R.string.last_updated, since);
								pullListView.getLoadingLayoutProxy().setLastUpdatedLabel(s);

								if (pullRefreshHandler != null) {
									pullRefreshHandler.postDelayed(this,
											sinceMS < DateUtils.MINUTE_IN_MILLIS
													? DateUtils.SECOND_IN_MILLIS
													: sinceMS < DateUtils.HOUR_IN_MILLIS
															? DateUtils.MINUTE_IN_MILLIS
															: DateUtils.HOUR_IN_MILLIS);
								}
							}
						}, 0);
					} else if (state == State.RESET || state == State.REFRESHING) {
						if (pullRefreshHandler != null) {
							pullRefreshHandler.removeCallbacks(null);
							pullRefreshHandler = null;
						}
					}
				}
			});
			pullListView.setOnRefreshListener(new OnRefreshListener<ListView>() {
				@Override
				public void onRefresh(PullToRefreshBase<ListView> refreshView) {
					sessionInfo.getRpc().getRecentTorrents(
							new TorrentListReceivedListener() {
								@Override
								public void rpcTorrentListReceived(List<?> listTorrents) {
									FragmentActivity activity = getActivity();
									if (activity == null) {
										return;
									}
									activity.runOnUiThread(new Runnable() {
										@Override
										public void run() {
											pullListView.onRefreshComplete();
										}
									});
								}
							});
				}

			});
		}
		filterEditText = (EditText) view.findViewById(R.id.filterText);

		listview.setItemsCanFocus(false);
		listview.setClickable(true);
		listview.setChoiceMode(ListView.CHOICE_MODE_SINGLE);

		adapter = new TorrentListAdapter(this.getActivity());

		listview.setAdapter(adapter);

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			setupHoneyCombListView(listview);
		}

		listview.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			long lastIdClicked = -1;

			@Override
			public void onItemClick(AdapterView<?> parent, final View view,
					int position, long id) {
				//Object item = parent.getItemAtPosition(position);

				if (DEBUG) {
					Log.d(
							null,
							position + "CLICKED; checked? "
									+ listview.isItemChecked(position) + "; last="
									+ lastIdClicked);
				}

				boolean isChecked = listview.isItemChecked(position);
				int choiceMode = listview.getChoiceMode();
				if (choiceMode == ListView.CHOICE_MODE_MULTIPLE_MODAL) {
					lastIdClicked = -1;
					// CHOICE_MODE_MULTIPLE_MODAL doesn't check items
					listview.setItemChecked(position, !isChecked);
				} else {
					if (!isChecked) {
						listview.setItemChecked(position, true);
					}
					// always isChecked, so we can't use it to uncheck
					// maybe actionmode will help..
					if (mActionMode == null) {
						showContextualActions();
						lastIdClicked = id;
					} else if (lastIdClicked == id) {
						finishActionMode();
						//listview.setItemChecked(position, false);
						lastIdClicked = -1;
					} else {
						lastIdClicked = id;
					}
				}

				if (mCallback != null) {
					mCallback.onTorrentSelectedListener(TorrentListFragment.this,
							getSelectedIDs(), false);
				}

				AndroidUtils.invalidateOptionsMenuHC(getActivity(), mActionMode);
			}

		});

		// Long click switches to multi-select mode
		listview.setOnItemLongClickListener(new OnItemLongClickListener() {

			@Override
			public boolean onItemLongClick(AdapterView<?> parent, View view,
					int position, long id) {
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
					int[] selectedPositions = getSelectedPositions();
					switchListViewToMulti_HC();
					for (int pos : selectedPositions) {
						listview.setItemChecked(pos, true);
					}
				}
				listview.setItemChecked(position, true);
				return true;
			}
		});

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

		setHasOptionsMenu(true);

		return view;
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		FragmentActivity activity = getActivity();
		tvFilteringBy = (TextView) activity.findViewById(R.id.wvFilteringBy);
		tvTorrentCount = (TextView) activity.findViewById(R.id.wvTorrentCount);

		super.onActivityCreated(savedInstanceState);
	}

	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	public void finishActionMode() {
		if (mActionMode != null) {
			mActionMode.finish();
		}
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

	private int[] getSelectedPositions() {
		SparseBooleanArray checked = listview.getCheckedItemPositions();
		int size = checked.size(); // number of name-value pairs in the array
		int[] positions = new int[size];
		int pos = 0;
		for (int i = 0; i < size; i++) {
			int position = checked.keyAt(i);
			positions[pos] = position;
			pos++;
		}
		if (pos < size) {
			int[] finalPositions = new int[pos];
			System.arraycopy(positions, 0, finalPositions, 0, pos);
			return finalPositions;
		}
		return positions;
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

	/*
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
	*/

	/* (non-Javadoc)
	 * @see com.vuze.android.remote.rpc.TorrentListReceivedListener#rpcTorrentListReceived(java.util.List)
	 */
	@Override
	public void rpcTorrentListReceived(final List<?> listTorrents) {
		lastUpdated = System.currentTimeMillis();
		FragmentActivity activity = getActivity();
		if (activity == null) {
			return;
		}
		activity.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				adapter.refreshDisplayList();
				updateTorrentCount(adapter.getCount());
				AndroidUtils.invalidateOptionsMenuHC(getActivity(), mActionMode);
			}
		});
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

	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	private void switchListViewToMulti_HC() {
		// CHOICE_MODE_MULTIPLE_MODAL is for CAB
		listview.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);
	}

	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	private void setupHoneyCombListView(ListView lv) {

		if (DEBUG) {
			System.out.println("MULTI:setup");
		}
		lv.setMultiChoiceModeListener(new MultiChoiceModeListener() {
			// Called when the action mode is created; startActionMode() was called
			@Override
			public boolean onCreateActionMode(ActionMode mode, Menu menu) {
				if (DEBUG) {
					System.out.println("MULTI:ON CREATEACTIONMODE");
				}
				mActionMode = mode;
				// Inflate a menu resource providing context menu items
				MenuInflater inflater = mode.getMenuInflater();
				inflater.inflate(R.menu.menu_context_torrent_details, menu);
				return true;
			}

			// Called each time the action mode is shown. Always called after onCreateActionMode, but
			// may be called multiple times if the mode is invalidated.
			@Override
			public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
				if (DEBUG) {
					System.out.println("MULTI:onPrepareActionMode");
				}
				MenuItem menuMove = menu.findItem(R.id.action_sel_move);
				menuMove.setEnabled(listview.getCheckedItemCount() > 0);

				Map<?, ?>[] selectedTorrentMaps = getSelectedTorrentMaps();
				boolean canStart = false;
				boolean canStop = false;
				for (Map<?, ?> mapTorrent : selectedTorrentMaps) {
					int status = MapUtils.getMapInt(mapTorrent,
							TransmissionVars.FIELD_TORRENT_STATUS,
							TransmissionVars.TR_STATUS_STOPPED);
					canStart |= status == TransmissionVars.TR_STATUS_STOPPED;
					canStop |= status != TransmissionVars.TR_STATUS_STOPPED;
				}
				MenuItem menuStart = menu.findItem(R.id.action_sel_start);
				menuStart.setVisible(canStart);

				MenuItem menuStop = menu.findItem(R.id.action_sel_stop);
				menuStop.setVisible(canStop);

				TorrentListFragment.this.onPrepareOptionsMenu(menu);

				AndroidUtils.fixupMenuAlpha(menu);

				return true;
			}

			// Called when the user selects a contextual menu item
			@Override
			public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
				if (DEBUG) {
					System.out.println("MULTI:onActionItemClicked");
				}
				if (TorrentListFragment.this.handleMenu(item.getItemId())) {
					return true;
				}
				return false;
			}

			// Called when the user exits the action mode
			@Override
			public void onDestroyActionMode(ActionMode mode) {
				if (DEBUG) {
					System.out.println("MULTI:onDestroyActionMode");
				}
				mActionMode = null;
				listview.post(new Runnable() {
					@Override
					public void run() {
						listview.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
					}
				});
				if (mCallback != null) {
					mCallback.onTorrentSelectedListener(TorrentListFragment.this,
							new long[] {}, false);
				}
			}

			@Override
			public void onItemCheckedStateChanged(ActionMode mode, int position,
					long id, boolean checked) {
				if (DEBUG) {
					System.out.println("MULTI:CHECK CHANGE");
				}

				String subtitle = getResources().getString(
						R.string.context_torrent_subtitle_selected,
						getCheckedItemCount(listview));
				mode.setSubtitle(subtitle);
				if (mCallback != null) {
					mCallback.onTorrentSelectedListener(TorrentListFragment.this,
							getSelectedIDs(), true);
				}
				AndroidUtils.invalidateOptionsMenuHC(getActivity(), mActionMode);
			}
		});

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
			System.out.println("HANDLE MENU FRAG " + itemId);
		}
		switch (itemId) {
			case R.id.action_filterby:
				DialogFragmentFilterBy.openFilterByDialog(this,
						sessionInfo.getRemoteProfile().getID());
				return true;
			case R.id.action_filter:
				boolean newVisibility = filterEditText.getVisibility() != View.VISIBLE;
				filterEditText.setVisibility(newVisibility ? View.VISIBLE : View.GONE);
				if (newVisibility) {
					filterEditText.requestFocus();
					InputMethodManager mgr = (InputMethodManager) getActivity().getSystemService(
							Context.INPUT_METHOD_SERVICE);
					mgr.showSoftInput(filterEditText, InputMethodManager.SHOW_IMPLICIT);
					VuzeEasyTracker.getInstance(this).send(
							MapBuilder.createEvent("uiAction", "ViewShown", "FilterBox", null).build());
				} else {
					InputMethodManager mgr = (InputMethodManager) getActivity().getSystemService(
							Context.INPUT_METHOD_SERVICE);
					mgr.hideSoftInputFromWindow(filterEditText.getWindowToken(), 0);
				}
				return true;
			case R.id.action_sortby:
				DialogFragmentSortBy.open(getFragmentManager(), this);
				return true;
			case R.id.action_context:
				// TODO: openContextMenu(myWebView);
				return true;

			case R.id.action_start_all:
				sessionInfo.getRpc().startTorrents(null, false, null);
				return true;

			case R.id.action_stop_all:
				sessionInfo.getRpc().stopTorrents(null, null);
				return true;

				// Start of Context Menu Items
			case R.id.action_sel_remove: {
				Map<?, ?>[] maps = getSelectedTorrentMaps();
				for (Map<?, ?> map : maps) {
					long id = MapUtils.getMapLong(map, "id", -1);
					String name = MapUtils.getMapString(map, "name", "");
					// TODO: One at a time!
					DialogFragmentDeleteTorrent.open(getFragmentManager(), name, id);
				}
				return true;
			}
			case R.id.action_sel_start: {
				long[] ids = getSelectedIDs();
				sessionInfo.getRpc().startTorrents(ids, false, null);
				return true;
			}
			case R.id.action_sel_forcestart: {
				long[] ids = getSelectedIDs();
				sessionInfo.getRpc().startTorrents(ids, true, null);
				return true;
			}
			case R.id.action_sel_stop: {
				long[] ids = getSelectedIDs();
				sessionInfo.getRpc().stopTorrents(ids, null);
				return true;
			}
			case R.id.action_sel_relocate:
				if (getCheckedItemCount(listview) == 0) {
					return true;
				}
				AndroidUtils.openMoveDataDialog(getFirstSelected(), sessionInfo,
						getFragmentManager());
				return true;
			case R.id.action_sel_move_top: {
				sessionInfo.getRpc().simpleRpcCall("queue-move-top", getSelectedIDs(),
						null);
				return true;
			}
			case R.id.action_sel_move_up: {
				sessionInfo.getRpc().simpleRpcCall("queue-move-up", getSelectedIDs(),
						null);
				return true;
			}
			case R.id.action_sel_move_down: {
				sessionInfo.getRpc().simpleRpcCall("queue-move-down", getSelectedIDs(),
						null);
				return true;
			}
			case R.id.action_sel_move_bottom: {
				sessionInfo.getRpc().simpleRpcCall("queue-move-bottom",
						getSelectedIDs(), null);
				return true;
			}
		}
		return false;
	}

	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	private void setupHoneyComb() {
		mActionModeCallback = new ActionMode.Callback() {

			// Called when the action mode is created; startActionMode() was called
			@Override
			public boolean onCreateActionMode(ActionMode mode, Menu menu) {
				// Inflate a menu resource providing context menu items
				MenuInflater inflater = mode.getMenuInflater();
				inflater.inflate(R.menu.menu_context_torrent_details, menu);

				return true;
			}

			// Called each time the action mode is shown. Always called after onCreateActionMode, but
			// may be called multiple times if the mode is invalidated.
			@Override
			public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
				//System.out.println("onPrepareActionMode in frag");
				MenuItem menuMove = menu.findItem(R.id.action_sel_move);
				menuMove.setEnabled(listview.getCheckedItemCount() > 0);

				Map<?, ?>[] selectedTorrentMaps = getSelectedTorrentMaps();
				boolean canStart = false;
				boolean canStop = false;
				for (Map<?, ?> mapTorrent : selectedTorrentMaps) {
					int status = MapUtils.getMapInt(mapTorrent,
							TransmissionVars.FIELD_TORRENT_STATUS,
							TransmissionVars.TR_STATUS_STOPPED);
					canStart |= status == TransmissionVars.TR_STATUS_STOPPED;
					canStop |= status != TransmissionVars.TR_STATUS_STOPPED;
				}
				MenuItem menuStart = menu.findItem(R.id.action_sel_start);
				menuStart.setVisible(canStart);

				MenuItem menuStop = menu.findItem(R.id.action_sel_stop);
				menuStop.setVisible(canStop);

				TorrentListFragment.this.onPrepareOptionsMenu(menu);

				AndroidUtils.fixupMenuAlpha(menu);

				return true;
			}

			// Called when the user selects a contextual menu item
			@Override
			public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
				if (TorrentListFragment.this.handleMenu(item.getItemId())) {
					return true;
				}
				return false;
			}

			// Called when the user exits the action mode
			@Override
			public void onDestroyActionMode(ActionMode mode) {
				System.out.println("destroy " + mode + "/" + mActionMode);
				mActionMode = null;

				if (!actionModeBeingReplaced) {
					listview.clearChoices();
					// Not sure why ListView doesn't invalidate by default
					adapter.notifyDataSetInvalidated();
					if (mCallback != null) {
						mCallback.onTorrentSelectedListener(TorrentListFragment.this,
								new long[] {}, false);
					}
				}
			}
		};
	}

	@Override
	public void onPrepareOptionsMenu(Menu menu) {

		MenuItem menuStartAll = menu.findItem(R.id.action_start_all);
		if (menuStartAll != null) {
			menuStartAll.setEnabled(havePaused);
		}
		MenuItem menuStopAll = menu.findItem(R.id.action_stop_all);
		if (menuStopAll != null) {
			menuStopAll.setEnabled(haveActive);
		}
		MenuItem menuContext = menu.findItem(R.id.action_context);
		if (menuContext != null) {
			menuContext.setVisible(getCheckedItemCount(listview) > 0);
		}

		AndroidUtils.fixupMenuAlpha(menu);

		super.onPrepareOptionsMenu(menu);
	}

	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	protected boolean showContextualActions() {
		if (mActionMode != null) {
			mActionMode.invalidate();
			return false;
		}

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			// Start the CAB using the ActionMode.Callback defined above
			mActionMode = getActivity().startActionMode(mActionModeCallback);
			mActionMode.setSubtitle(R.string.multi_select_tip);
			mActionMode.setTitle(R.string.context_torrent_title);
		}
		return true;
	}

	public void refreshRow(Map<?, ?> mapTorrent) {
		int position = adapter.getPosition(mapTorrent);
		if (position < 0) {
			return;
		}
		View view = listview.getChildAt(position);
		if (view == null) {
			return;
		}
		adapter.refreshView(position, view, listview);
	}

	@Override
	public void filterBy(final long filterMode, final String name, boolean save) {
		if (DEBUG) {
			Log.d(null, "FILTER BY " + name);
		}

		FragmentActivity activity = getActivity();
		if (activity == null) {
			return;
		}
		activity.runOnUiThread(new Runnable() {
			public void run() {
				// java.lang.RuntimeException: Can't create handler inside thread that has not called Looper.prepare()
				TorrentFilter filter = adapter.getFilter();
				filter.setFilterMode(filterMode);
				if (tvFilteringBy != null) {
					tvFilteringBy.setText(name);
				}
			}
		});
		if (save) {
			sessionInfo.getRemoteProfile().setFilterBy(filterMode);
			sessionInfo.saveProfileIfRemember();
		}
	}

	public void sortBy(final String[] sortFieldIDs, final Boolean[] sortOrderAsc,
			boolean save) {
		if (DEBUG) {
			Log.d(null, "SORT BY!");
		}
		FragmentActivity activity = getActivity();
		if (activity == null) {
			return;
		}
		activity.runOnUiThread(new Runnable() {
			public void run() {
				adapter.setSort(sortFieldIDs, sortOrderAsc);
			}
		});

		if (save) {
			sessionInfo.getRemoteProfile().setSortBy(sortFieldIDs, sortOrderAsc);
			sessionInfo.saveProfileIfRemember();
		}
	}

	@Override
	public void flipSortOrder() {
		if (sessionInfo == null) {
			return;
		}
		RemoteProfile remoteProfile = sessionInfo.getRemoteProfile();
		if (remoteProfile == null) {
			return;
		}
		Boolean[] sortOrder = remoteProfile.getSortOrder();
		if (sortOrder == null) {
			return;
		}
		for (int i = 0; i < sortOrder.length; i++) {
			sortOrder[i] = !sortOrder[i];
		}
		sortBy(remoteProfile.getSortBy(), sortOrder, true);
	}

	public void updateTorrentCount(final long total) {
		if (tvTorrentCount == null) {
			return;
		}
		FragmentActivity activity = getActivity();
		if (activity == null) {
			return;
		}
		activity.runOnUiThread(new Runnable() {
			public void run() {
				if (total == 0) {
					tvTorrentCount.setText("");
				} else {
					tvTorrentCount.setText(total + " torrents");
				}
			}
		});
	}

	public void setTorrentIDs(SessionInfo sessionInfo, long[] ids) {
	}

	@Override
	public void setActionModeBeingReplaced(boolean actionModeBeingReplaced) {
		this.actionModeBeingReplaced = actionModeBeingReplaced;
		if (actionModeBeingReplaced) {
			rebuildActionMode = mActionMode != null;
			System.out.println("actionmode: will rebuild? " + rebuildActionMode);
		}
	}

	@Override
	public void actionModeBeingReplacedDone() {
		System.out.println("actionmode: done. will rebuild? " + rebuildActionMode);
		if (rebuildActionMode) {
			rebuildActionMode = false;
			showContextualActions();
		}
	}
}
