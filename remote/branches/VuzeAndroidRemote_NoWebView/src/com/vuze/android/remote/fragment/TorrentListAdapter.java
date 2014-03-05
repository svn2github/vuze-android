package com.vuze.android.remote.fragment;

import java.util.*;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import com.aelitis.azureus.util.MapUtils;
import com.vuze.android.remote.*;
import com.vuze.android.remote.TextViewFlipper.FlipValidator;

public class TorrentListAdapter
	extends BaseAdapter
	implements Filterable
{
	public final static int FILTERBY_ALL = 8;

	public final static int FILTERBY_ACTIVE = 4;

	public final static int FILTERBY_COMPLETE = 9;

	public final static int FILTERBY_INCOMPLETE = 1;

	public final static int FILTERBY_STOPPED = 2;

	public static final boolean DEBUG = AndroidUtils.DEBUG;

	private static final String TAG = "TorrentListAdapter";

	protected static class ViewHolder
	{
		public ViewHolder(View rowView) {
			tvName = (TextView) rowView.findViewById(R.id.torrentrow_name);
			tvProgress = (TextView) rowView.findViewById(R.id.torrentrow_progress_pct);
			pb = (ProgressBar) rowView.findViewById(R.id.torrentrow_progress);
			tvInfo = (TextView) rowView.findViewById(R.id.torrentrow_info);
			tvETA = (TextView) rowView.findViewById(R.id.torrentrow_eta);
			tvUlRate = (TextView) rowView.findViewById(R.id.torrentrow_upspeed);
			tvDlRate = (TextView) rowView.findViewById(R.id.torrentrow_downspeed);
			tvStatus = (TextView) rowView.findViewById(R.id.torrentrow_state);
			tvTags = (TextView) rowView.findViewById(R.id.torrentrow_tags);
		}

		long torrentID = -1;

		TextView tvName;

		TextView tvProgress;

		ProgressBar pb;

		TextView tvInfo;

		TextView tvETA;

		TextView tvUlRate;

		TextView tvDlRate;

		TextView tvStatus;

		TextView tvTags;

		boolean animateFlip;
	}

	public static class ViewHolderFlipValidator
		implements FlipValidator
	{
		private ViewHolder holder;

		private long torrentID;

		public ViewHolderFlipValidator(ViewHolder holder, long torrentID) {
			this.holder = holder;
			this.torrentID = torrentID;
		}

		@Override
		public boolean isStillValid() {
			return holder.torrentID == torrentID;
		}
	}

	private Context context;

	private TorrentFilter filter;

	/** List of they keys of all entries displayed, in the display order */
	private List<Object> displayList;

	public Object mLock = new Object();

	private Comparator<? super Map<?, ?>> comparator;

	private String[] sortFieldIDs;

	private Boolean[] sortOrderAsc;

	private SessionInfo sessionInfo;

	private TorrentListRowFiller torrentListRowFiller;

	public TorrentListAdapter(Context context) {
		this.context = context;

		torrentListRowFiller = new TorrentListRowFiller(context);

		displayList = new ArrayList<Object>();
	}

	public void setSessionInfo(SessionInfo sessionInfo) {
		this.sessionInfo = sessionInfo;
	}

	public int getPosition(Map<?, ?> item) {
		Object itemKey = item.get("id");
		int position = -1;
		synchronized (mLock) {
			int i = -1;
			for (Iterator<?> iterator = displayList.iterator(); iterator.hasNext();) {
				i++;
				Object key = iterator.next();
				if (key.equals(itemKey)) {
					position = i;
					break;
				}
			}
		}
		return position;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		return getView(position, convertView, parent, false);
	}

	public void refreshView(int position, View view, ListView listView) {
		getView(position, view, listView, true);
	}

	public View getView(int position, View convertView, ViewGroup parent,
			boolean requireHolder) {
		View rowView = convertView;

		Map<?, ?> item = getItem(position);

		if (rowView == null) {
			if (requireHolder) {
				return null;
			}
			LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			rowView = inflater.inflate(R.layout.row_torrent_list, parent, false);
			ViewHolder viewHolder = new ViewHolder(rowView);

			rowView.setTag(viewHolder);
		}

		final ViewHolder holder = (ViewHolder) rowView.getTag();

		torrentListRowFiller.fillHolder(holder, item, sessionInfo);

		return rowView;
	}

	@Override
	public TorrentFilter getFilter() {
		if (filter == null) {
			filter = new TorrentFilter();
		}
		return filter;
	}

	public class TorrentFilter
		extends Filter
	{
		private long filterMode;

		private String constraint;

		public void setFilterMode(long filterMode) {
			this.filterMode = filterMode;
			filter(constraint);
		}

		@Override
		protected FilterResults performFiltering(CharSequence _constraint) {
			this.constraint = _constraint == null ? null
					: _constraint.toString().toLowerCase(Locale.US);
			FilterResults results = new FilterResults();

			if (sessionInfo == null) {
				return results;
			}

			boolean hasConstraint = constraint != null && constraint.length() > 0;

			Object[] listTorrents = sessionInfo.getTorrentListKeys();
			ArrayList<Object> listKeys = new ArrayList<Object>();
			Collections.addAll(listKeys, listTorrents);

			if (!hasConstraint && filterMode < 0) {
				synchronized (mLock) {
					results.values = listKeys;
					results.count = listKeys.size();
				}
				if (DEBUG) {
					Log.d(TAG, "filtering " + results.count);
				}
			} else {
				if (DEBUG) {
					Log.d(TAG, "filtering " + listKeys.size());
				}

				if (filterMode >= 0 && filterMode != FILTERBY_ALL) {
					synchronized (mLock) {
						for (Iterator<Object> iterator = listKeys.iterator(); iterator.hasNext();) {
							Object key = iterator.next();

							if (!filterCheck(filterMode, key)) {
								iterator.remove();
							}
						}
					}
				}

				if (DEBUG) {
					Log.d(TAG, "type filtered to " + listKeys.size());
				}

				if (hasConstraint) {
					synchronized (mLock) {
						for (Iterator<?> iterator = listKeys.iterator(); iterator.hasNext();) {
							Object key = iterator.next();

							if (!constraintCheck(constraint, key)) {
								iterator.remove();
							}
						}
					}

					if (DEBUG) {
						Log.d(TAG, "text filtered to " + listKeys.size());
					}
				}

				results.values = listKeys;
				results.count = listKeys.size();

			}
			return results;
		}

		@SuppressWarnings("unchecked")
		@Override
		protected void publishResults(CharSequence constraint, FilterResults results) {
			// Now we have to inform the adapter about the new list filtered
			if (results.count == 0) {
				displayList.clear();
				notifyDataSetInvalidated();
			} else {
				synchronized (mLock) {
					if (results.values instanceof List) {
						displayList = (List<Object>) results.values;
						doSort();
					}
				}
			}
		}

	}

	public void refreshDisplayList() {
		if (DEBUG) {
			Log.d(TAG, "refreshDisplayList");
		}
		if (sessionInfo != null) {
			TorrentFilter filter = getFilter();
			// How does this work with filters?
			Object[] keys = sessionInfo.getTorrentListKeys();
			synchronized (mLock) {
				for (Object key : keys) {
					if (!displayList.contains(key)
							&& constraintCheck(filter.constraint, key)
							&& filterCheck(filter.filterMode, key)) {
						displayList.add(key);
					}
				}
			}
		}
		doSort();
	}

	public boolean constraintCheck(CharSequence constraint, Object key) {
		if (constraint == null || constraint.length() == 0) {
			return true;
		}
		Map<?, ?> map = sessionInfo.getTorrent(key);
		if (map == null) {
			return false;
		}

		String name = MapUtils.getMapString(map,
				TransmissionVars.FIELD_TORRENT_NAME, "").toLowerCase(Locale.US);
		return name.contains(constraint);
	}

	private boolean filterCheck(long filterMode, Object key) {
		Map<?, ?> map = sessionInfo.getTorrent(key);
		if (map == null) {
			return false;
		}

		if (filterMode > 10) {
			List<?> listTagUIDs = MapUtils.getMapList(map, "tag-uids", null);
			if (listTagUIDs != null) {
				for (Object o : listTagUIDs) {
					if (o instanceof Long) {
						Long tagUID = (Long) o;
						if (tagUID == filterMode) {
							return true;
						}
					}
				}
			}

			return false;
		}

		switch ((int) filterMode) {
			case FILTERBY_ACTIVE:
				long dlRate = MapUtils.getMapLong(map,
						TransmissionVars.FIELD_TORRENT_RATE_DOWNLOAD, -1);
				long ulRate = MapUtils.getMapLong(map,
						TransmissionVars.FIELD_TORRENT_RATE_UPLOAD, -1);
				if (ulRate <= 0 && dlRate <= 0) {
					return false;
				}
				break;

			case FILTERBY_COMPLETE: {
				float pctDone = MapUtils.getMapFloat(map,
						TransmissionVars.FIELD_TORRENT_PERCENT_DONE, 0);
				if (pctDone < 1.0f) {
					return false;
				}
				break;
			}
			case FILTERBY_INCOMPLETE: {
				float pctDone = MapUtils.getMapFloat(map,
						TransmissionVars.FIELD_TORRENT_PERCENT_DONE, 0);
				if (pctDone >= 1.0f) {
					return false;
				}
				break;
			}
			case FILTERBY_STOPPED: {
				int status = MapUtils.getMapInt(map,
						TransmissionVars.FIELD_TORRENT_STATUS,
						TransmissionVars.TR_STATUS_STOPPED);
				if (status != TransmissionVars.TR_STATUS_STOPPED) {
					return false;
				}
				break;
			}
		}
		return true;
	}

	public void setSort(String[] fieldIDs, Boolean[] sortOrderAsc) {
		synchronized (mLock) {
			sortFieldIDs = fieldIDs;
			Boolean[] order;
			if (sortOrderAsc == null) {
				order = new Boolean[sortFieldIDs.length];
				Arrays.fill(order, Boolean.FALSE);
			} else if (sortOrderAsc.length != sortFieldIDs.length) {
				order = new Boolean[sortFieldIDs.length];
				Arrays.fill(order, Boolean.FALSE);
				System.arraycopy(sortOrderAsc, 0, order, 0, sortOrderAsc.length);
			} else {
				order = sortOrderAsc;
			}
			this.sortOrderAsc = order;
			comparator = null;
		}
		doSort();
	}

	public void setSort(Comparator<? super Map<?, ?>> comparator) {
		synchronized (mLock) {
			this.comparator = comparator;
		}
		doSort();
	}

	private void doSort() {
		if (sessionInfo == null) {
			if (DEBUG) {
				Log.d(TAG, "doSort skipped: No sessionInfo");
			}
			return;
		}
		if (comparator == null && sortFieldIDs == null) {
			if (DEBUG) {
				Log.d(TAG, "doSort skipped: no comparator and no sort");
			}
			return;
		}
		if (DEBUG) {
			Log.d(
					TAG,
					"sort: " + Arrays.asList(sortFieldIDs) + "/"
							+ Arrays.asList(sortOrderAsc));
		}

		ComparatorMapFields sorter = new ComparatorMapFields(sortFieldIDs,
				sortOrderAsc, comparator) {

			@Override
			public int reportError(Comparable<?> oLHS, Comparable<?> oRHS, Throwable t) {
				VuzeEasyTracker.getInstance(context).logError(context, t);
				return 0;
			}

			@Override
			public Map<?, ?> mapGetter(Object o) {
				return sessionInfo.getTorrent(o);
			}
		};

		synchronized (mLock) {
			Collections.sort(displayList, sorter);
		}

		notifyDataSetChanged();
	}

	/* (non-Javadoc)
	 * @see android.widget.Adapter#getCount()
	 */
	@Override
	public int getCount() {
		return displayList.size();
	}

	/* (non-Javadoc)
	 * @see android.widget.Adapter#getItem(int)
	 */
	@Override
	public Map<?, ?> getItem(int position) {
		if (sessionInfo == null) {
			return new HashMap<Object, Object>();
		}
		Object key = displayList.get(position);
		return sessionInfo.getTorrent(key);
	}

	/* (non-Javadoc)
	 * @see android.widget.Adapter#getItemId(int)
	 */
	@Override
	public long getItemId(int position) {
		return position;
	}

}
