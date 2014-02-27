package com.vuze.android.remote.fragment;

import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.*;
import android.view.ActionMode.Callback;
import android.webkit.MimeTypeMap;
import android.widget.*;
import android.widget.AdapterView.OnItemClickListener;

import com.aelitis.azureus.util.MapUtils;
import com.vuze.android.remote.*;
import com.vuze.android.remote.rpc.TorrentListReceivedListener;

public class FilesFragment
	extends Fragment
	implements SetTorrentIdListener, TorrentListReceivedListener
{
	protected static final String TAG = "FilesFragment";

	/**
	 * Launching an Intent without a Mime will result in a different list
	 * of apps then one including the Mime type.  Sometimes one is better than
	 * the other, especially with URLs :(
	 * 
	 * Pros for setting MIME:
	 * - In theory should provide more apps
	 * 
	 * Cons for setting MIME:
	 * - the Web browser will not show as an app, but rather a
	 * html viewer app, if you are lucky
	 * - A lot of apps that accept MIME types can't handle URLs and fail
	 */
	protected static final boolean tryLaunchWithMimeFirst = false;

	private ListView listview;

	private FilesAdapter adapter;

	private long torrentID = -1;

	private SessionInfo sessionInfo;

	protected int selectedFileIndex = -1;

	private Callback mActionModeCallback;

	protected ActionMode mActionMode;

	public FilesFragment() {
		super();
	}

	private ActionModeBeingReplacedListener mCallback;

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);

		if (activity instanceof ActionModeBeingReplacedListener) {
			mCallback = (ActionModeBeingReplacedListener) activity;
		}
	}

	@Override
	public void onPause() {
		if (sessionInfo != null) {
			sessionInfo.removeTorrentListReceivedListener(this);
		}
		super.onPause();
	}

	@Override
	public void onResume() {
		if (sessionInfo != null) {
			sessionInfo.addTorrentListReceivedListener(this);
		}
		super.onResume();
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			setupHoneyComb();
		}
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

		adapter = new FilesAdapter(this.getActivity());
		if (sessionInfo != null) {
			adapter.setSessionInfo(sessionInfo);
		}
		listview.setItemsCanFocus(true);
		listview.setAdapter(adapter);

		if (torrentID >= 0) {
			adapter.setTorrentID(torrentID);
		}
	}

	public View onCreateView(android.view.LayoutInflater inflater,
			android.view.ViewGroup container, Bundle savedInstanceState) {

		View view = inflater.inflate(R.layout.frag_torrent_files, container, false);

		listview = (ListView) view.findViewById(R.id.files_list);

		listview.setItemsCanFocus(false);
		listview.setClickable(true);
		listview.setChoiceMode(ListView.CHOICE_MODE_SINGLE);

		listview.setOnItemClickListener(new OnItemClickListener() {

			private long lastIdClicked = -1;

			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position,
					long id) {
				boolean isChecked = listview.isItemChecked(position);
				if (isChecked) {

				}
				selectedFileIndex = isChecked ? position : -1;

				if (mActionMode == null) {
					showContextualActions();
					lastIdClicked = id;
				} else if (lastIdClicked == id) {
					finishActionMode();
					//listview.setItemChecked(position, false);
					lastIdClicked = -1;
				} else {
					showContextualActions();

					lastIdClicked = id;
				}

				AndroidUtils.invalidateOptionsMenuHC(getActivity(), mActionMode);
			}
		});

		return view;
	}

	/* (non-Javadoc)
	 * @see com.vuze.android.remote.activity.SetTorrentIdListener#setTorrentID(com.vuze.android.remote.SessionInfo, long)
	 */
	@Override
	public void setTorrentID(SessionInfo sessionInfo, long id) {
		if (sessionInfo == null) {
			return;
		}
		this.sessionInfo = sessionInfo;
		if (torrentID != id && adapter != null) {
			adapter.clearList();
		}
		torrentID = id;
		//Map<?, ?> torrent = sessionInfo.getTorrent(id);
		//System.out.println("torrent is " + torrent);
		if (adapter != null) {
			adapter.setSessionInfo(sessionInfo);
		}
		// getTorrentFileInfo will fire FileFragment's TorrentListReceivedListener
		sessionInfo.getRpc().getTorrentFileInfo(id, null);
	}

	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	private void setupHoneyComb() {
		mActionModeCallback = new ActionMode.Callback() {

			// Called when the action mode is created; startActionMode() was called
			@Override
			public boolean onCreateActionMode(ActionMode mode, Menu menu) {
				// Inflate a menu resource providing context menu items
				MenuInflater inflater = mode.getMenuInflater();
				inflater.inflate(R.menu.menu_context_torrent_files, menu);

				return true;
			}

			// Called each time the action mode is shown. Always called after onCreateActionMode, but
			// may be called multiple times if the mode is invalidated.
			@Override
			public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
				if (sessionInfo == null || torrentID < 0) {
					return false;
				}

				boolean isComplete = false;
				Map<?, ?> mapFile = getSelectedFile();
				Map<?, ?> mapFileStats = getSelectedFileStats();
				if (mapFile != null) {
					long bytesCompleted = MapUtils.getMapLong(mapFile, "bytesCompleted",
							0);
					long length = MapUtils.getMapLong(mapFile, "length", -1);
					//System.out.println("mapFIle=" + mapFile);
					isComplete = bytesCompleted == length;
				}

				MenuItem menuLaunch = menu.findItem(R.id.action_sel_launch);
				if (menuLaunch != null) {
					boolean canLaunch = isComplete; //TODO: = && isOnline;
					menuLaunch.setEnabled(canLaunch);
				}

				MenuItem menuSave = menu.findItem(R.id.action_sel_save);
				if (menuSave != null) {
					boolean visible = sessionInfo != null
							&& !sessionInfo.getRemoteProfile().isLocalHost();
					menuSave.setVisible(visible);
					if (visible) {
						boolean canSave = isComplete;
						menuSave.setEnabled(canSave);
					}
				}

				int priority = MapUtils.getMapInt(mapFileStats,
						TransmissionVars.TORRENT_FIELD_FILES_PRIORITY,
						TransmissionVars.TR_PRI_NORMAL);
				MenuItem menuPriorityUp = menu.findItem(R.id.action_sel_priority_up);
				if (menuPriorityUp != null) {
					menuPriorityUp.setEnabled(!isComplete
							&& priority < TransmissionVars.TR_PRI_HIGH);
				}
				MenuItem menuPriorityDown = menu.findItem(R.id.action_sel_priority_down);
				if (menuPriorityDown != null) {
					menuPriorityDown.setEnabled(!isComplete
							&& priority > TransmissionVars.TR_PRI_LOW);
				}

				boolean wanted = MapUtils.getMapBoolean(mapFileStats, "wanted", true);
				MenuItem menuUnwant = menu.findItem(R.id.action_sel_unwanted);
				if (menuUnwant != null) {
					menuUnwant.setVisible(wanted);
				}
				MenuItem menuWant = menu.findItem(R.id.action_sel_wanted);
				if (menuWant != null) {
					menuWant.setVisible(!wanted);
				}

				AndroidUtils.fixupMenuAlpha(menu);

				return true;
			}

			// Called when the user selects a contextual menu item
			@Override
			public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
				if (sessionInfo == null || torrentID < 0) {
					return false;
				}
				switch (item.getItemId()) {
					case R.id.action_sel_launch: {
						Map<?, ?> selectedFile = getSelectedFile();
						if (selectedFile == null) {
							return false;
						}
						return launchFile(selectedFile);
					}
					case R.id.action_sel_save: {
						Map<?, ?> selectedFile = getSelectedFile();
						return saveFile(selectedFile);
					}
					case R.id.action_sel_wanted: {
						sessionInfo.getRpc().setWantState(torrentID, new long[] {
							selectedFileIndex
						}, true, null);
						return true;
					}
					case R.id.action_sel_unwanted: {
						// TODO: Delete Prompt
						sessionInfo.getRpc().setWantState(torrentID, new long[] {
							selectedFileIndex
						}, false, null);
						return true;
					}
					case R.id.action_sel_priority_up: {
						Map<?, ?> selectedFile = getSelectedFileStats();
						int priority = MapUtils.getMapInt(selectedFile,
								TransmissionVars.TORRENT_FIELD_FILES_PRIORITY,
								TransmissionVars.TR_PRI_NORMAL);

						if (priority >= TransmissionVars.TR_PRI_HIGH) {
							return true;
						} else {
							priority += 1;
						}
						sessionInfo.getRpc().setFilePriority(torrentID, new long[] {
							selectedFileIndex
						}, priority, null);
						return true;
					}
					case R.id.action_sel_priority_down: {
						Map<?, ?> selectedFile = getSelectedFileStats();
						int priority = MapUtils.getMapInt(selectedFile,
								TransmissionVars.TORRENT_FIELD_FILES_PRIORITY,
								TransmissionVars.TR_PRI_NORMAL);

						if (priority <= TransmissionVars.TR_PRI_LOW) {
							return true;
						} else {
							priority -= 1;
						}
						sessionInfo.getRpc().setFilePriority(torrentID, new long[] {
							selectedFileIndex
						}, priority, null);
						return true;
					}
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

				// delay so actionmode finishes up
				listview.post(new Runnable() {

					@Override
					public void run() {
						if (mCallback != null) {
							mCallback.actionModeBeingReplacedDone();
						}
					}
				});
			}
		};
	}

	protected boolean saveFile(Map<?, ?> selectedFile) {
		if (selectedFile == null) {
			return false;
		}
		if (sessionInfo == null) {
			return false;
		}
		if (sessionInfo.getRemoteProfile().isLocalHost()) {
			return false;
		}
		final String contentURL = MapUtils.getMapString(selectedFile, "contentURL",
				null);
		if (contentURL == null || contentURL.length() == 0) {
			return false;
		}
		final File directory = AndroidUtils.getDownloadDir();
		final File outFile = new File(directory, MapUtils.getMapString(
				selectedFile, "name", "foo.txt"));

		new Thread(new Runnable() {

			@Override
			public void run() {
				AndroidUtils.copyUrlToFile(contentURL, outFile);
				FragmentActivity activity = getActivity();
				if (activity == null) {
					return;
				}
				activity.runOnUiThread(new Runnable() {
					@Override
					public void run() {
						Toast.makeText(getActivity().getApplicationContext(),
								"Saved " + outFile.getName(), Toast.LENGTH_SHORT).show();
					}
				});
			}
		}).start();

		return true;
	}

	@SuppressWarnings("unused")
	protected boolean launchFile(Map<?, ?> selectedFile) {

		String fullPath = MapUtils.getMapString(selectedFile, "fullPath", null);
		if (fullPath != null && fullPath.length() > 0) {
			File file = new File(fullPath);
			if (file.exists()) {
				Uri uri = Uri.fromFile(file);
				Intent intent = new Intent(Intent.ACTION_VIEW, uri);
				try {
					startActivity(intent);
				} catch (android.content.ActivityNotFoundException ex) {

				}
				if (AndroidUtils.DEBUG) {
					System.out.println("Started " + uri);
				}
				return true;
			} else {
				if (AndroidUtils.DEBUG) {
					Log.d(TAG, "Launch: File Not Found: " + fullPath);
				}
			}
		}

		String contentURL = MapUtils.getMapString(selectedFile, "contentURL", null);
		if (contentURL != null && contentURL.length() > 0) {
			Uri uri = Uri.parse(contentURL);
			Intent intent = new Intent(Intent.ACTION_VIEW, uri);

			String extension = MimeTypeMap.getFileExtensionFromUrl(contentURL).toLowerCase(
					Locale.US);
			String mimetype = MimeTypeMap.getSingleton().getMimeTypeFromExtension(
					extension);
			if (mimetype != null && tryLaunchWithMimeFirst) {
				intent.setType(mimetype);
			}

			try {
				startActivity(intent);
				if (AndroidUtils.DEBUG) {
					System.out.println("Started " + uri + " MIME: " + intent.getType());
				}
			} catch (android.content.ActivityNotFoundException ex) {
				if (AndroidUtils.DEBUG) {
					Log.d(TAG, "no intent for view. " + ex.toString());
				}

				if (mimetype != null) {
					try {
						Intent intent2 = new Intent(Intent.ACTION_VIEW, uri);
						if (!tryLaunchWithMimeFirst) {
							intent.setType(mimetype);
						}
						startActivity(intent2);
						if (AndroidUtils.DEBUG) {
							System.out.println("Started (no mime set) " + uri);
						}
						return true;
					} catch (android.content.ActivityNotFoundException ex2) {
						if (AndroidUtils.DEBUG) {
							Log.d(TAG, "no intent for view. " + ex2.toString());
						}
					}
				}

				Toast.makeText(getActivity().getApplicationContext(),
						getActivity().getResources().getString(R.string.no_intent),
						Toast.LENGTH_SHORT).show();
			}
			return true;
		}

		return true;
	}

	protected Map<?, ?> getSelectedFile() {
		Map<?, ?> torrent = sessionInfo.getTorrent(torrentID);
		if (torrent == null) {
			return null;
		}
		List<?> listFiles = MapUtils.getMapList(torrent, "files", null);
		if (listFiles == null || selectedFileIndex < 0
				|| selectedFileIndex >= listFiles.size()) {
			return null;
		}
		Object object = listFiles.get(selectedFileIndex);
		if (object instanceof Map<?, ?>) {
			Map<?, ?> map = (Map<?, ?>) object;
			return map;
		}
		return null;
	}

	protected Map<?, ?> getSelectedFileStats() {
		Map<?, ?> torrent = sessionInfo.getTorrent(torrentID);
		if (torrent == null) {
			return null;
		}
		List<?> listFiles = MapUtils.getMapList(torrent, "fileStats", null);
		if (listFiles == null || selectedFileIndex < 0
				|| selectedFileIndex >= listFiles.size()) {
			return null;
		}
		Object object = listFiles.get(selectedFileIndex);
		if (object instanceof Map<?, ?>) {
			Map<?, ?> map = (Map<?, ?>) object;
			return map;
		}
		return null;
	}

	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	protected boolean showContextualActions() {
		if (mActionMode != null) {
			Map<?, ?> selectedFile = getSelectedFile();
			String name = MapUtils.getMapString(selectedFile, "name", null);
			mActionMode.setSubtitle(name);

			mActionMode.invalidate();
			return false;
		}

		if (mCallback != null) {
			mCallback.setActionModeBeingReplaced(true);
		}
		// Start the CAB using the ActionMode.Callback defined above
		mActionMode = getActivity().startActionMode(mActionModeCallback);
		mActionMode.setTitle(R.string.context_file_title);
		Map<?, ?> selectedFile = getSelectedFile();
		String name = MapUtils.getMapString(selectedFile, "name", null);
		mActionMode.setSubtitle(name);
		if (mCallback != null) {
			mCallback.setActionModeBeingReplaced(false);
		}
		return true;
	}

	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	public void finishActionMode() {
		if (mActionMode != null) {
			mActionMode.finish();
		}
	}

	/* (non-Javadoc)
	 * @see com.vuze.android.remote.rpc.TorrentListReceivedListener#rpcTorrentListReceived(java.util.List)
	 */
	@SuppressWarnings("rawtypes")
	@Override
	public void rpcTorrentListReceived(final List<?> listTorrents) {
		FragmentActivity activity = getActivity();
		if (activity == null) {
			return;
		}
		boolean found = false;
		for (Object item : listTorrents) {
			if (!(item instanceof Map)) {
				continue;
			}
			Map mapTorrent = (Map) item;
			Object key = mapTorrent.get("id");

			if (key instanceof Number) {
				found = ((Number) key).longValue() == torrentID;
				if (found) {
					if (AndroidUtils.DEBUG) {
						Log.d(TAG, "TorrentListReceived, contains torrent #" + torrentID);
					}
					break;
				}
			}
		}
		if (!found) {
			if (AndroidUtils.DEBUG) {
				Log.d(TAG, "TorrentListReceived, does not contain torretn #"
						+ torrentID);
			}
			return;
		}
		activity.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				adapter.setTorrentID(torrentID);
				AndroidUtils.invalidateOptionsMenuHC(getActivity(), mActionMode);
			}
		});
	}
}
