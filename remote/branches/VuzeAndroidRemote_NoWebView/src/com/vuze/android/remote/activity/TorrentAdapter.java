package com.vuze.android.remote.activity;

import java.text.NumberFormat;
import java.util.*;

import org.gudy.azureus2.core3.util.DisplayFormatters;

import android.content.Context;
import android.content.res.Resources;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import com.aelitis.azureus.util.MapUtils;
import com.vuze.android.remote.*;

public class TorrentAdapter
	extends BaseAdapter
	implements Filterable
{
	public final static int FILTERBY_ALL = 8;

	public final static int FILTERBY_ACTIVE = 4;

	public final static int FILTERBY_COMPLETE = 9;

	public final static int FILTERBY_INCOMPLETE = 1;

	public final static int FILTERBY_STOPPED = 2;

	public static final boolean DEBUG = AndroidUtils.DEBUG;

	static class ViewHolder
	{
		TextView tvName;

		TextView tvProgress;

		ProgressBar pb;

		TextView tvInfo;

		TextView tvETA;

		TextView tvUlRate;

		TextView tvDlRate;

		TextView tvStatus;
	}

	private Context context;

	private TorrentFilter filter;

	/** List of they keys of all entries displayed, in the display order */
	private List<Object> displayList;

	public Object mLock = new Object();

	private Comparator<? super Map<?, ?>> comparator;

	private Resources resources;

	private String[] sortFieldIDs;

	private Boolean[] sortOrderAsc;

	private SessionInfo sessionInfo;

	public TorrentAdapter(Context context) {
		this.context = context;
		resources = context.getResources();
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
			for (Iterator iterator = displayList.iterator(); iterator.hasNext();) {
				i++;
				Object key = (Object) iterator.next();
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
		if (rowView == null) {
			if (requireHolder) {
				return null;
			}
			LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			rowView = inflater.inflate(R.layout.row_torrent_list, parent, false);
			ViewHolder viewHolder = new ViewHolder();

			viewHolder.tvName = (TextView) rowView.findViewById(R.id.torrentrow_name);

			viewHolder.tvProgress = (TextView) rowView.findViewById(R.id.torrentrow_progress_pct);
			viewHolder.pb = (ProgressBar) rowView.findViewById(R.id.torrentrow_progress);
			viewHolder.tvInfo = (TextView) rowView.findViewById(R.id.torrentrow_info);
			viewHolder.tvETA = (TextView) rowView.findViewById(R.id.torrentrow_eta);
			viewHolder.tvUlRate = (TextView) rowView.findViewById(R.id.torrentrow_upspeed);
			viewHolder.tvDlRate = (TextView) rowView.findViewById(R.id.torrentrow_downspeed);
			viewHolder.tvStatus = (TextView) rowView.findViewById(R.id.torrentrow_state);

			rowView.setTag(viewHolder);
		}

		ViewHolder holder = (ViewHolder) rowView.getTag();

		//		boolean isChecked = false;
		//		if (parent instanceof ListView) {
		//			isChecked = ((ListView) parent).isItemChecked(position);
		//			System.out.println(position + " checked? " + isChecked);
		//		}

		//		rowView.setBackgroundColor(isChecked
		//				? resources.getColor(R.color.list_bg_f) : 0);

		Map<?, ?> item = getItem(position);
		if (holder.tvName != null) {
			holder.tvName.setText(MapUtils.getMapString(item, "name", "??"));
		}
		float pctDone = MapUtils.getMapFloat(item, "percentDone", 0f);
		if (holder.tvProgress != null) {
			NumberFormat format = NumberFormat.getPercentInstance();
			format.setMaximumFractionDigits(1);
			String s = format.format(pctDone);
			holder.tvProgress.setText(s);
		}
		if (holder.pb != null) {
			holder.pb.setProgress((int) (pctDone * 10000));
		}
		if (holder.tvInfo != null) {
			int fileCount = MapUtils.getMapInt(item, "fileCount", 0);
			long size = MapUtils.getMapLong(item, "sizeWhenDone", 0);

			String s = resources.getQuantityString(R.plurals.torrent_row_info,
					fileCount, fileCount)
					+ resources.getString(R.string.torrent_row_info2,
							DisplayFormatters.formatByteCountToKiBEtc(size));
			holder.tvInfo.setText(s);
		}
		if (holder.tvETA != null) {
			long etaSecs = MapUtils.getMapLong(item, "eta", -1);
			if (etaSecs > 0) {
				String s = DisplayFormatters.prettyFormat(etaSecs);
				holder.tvETA.setText(s);
				holder.tvETA.setVisibility(View.VISIBLE);
			} else {
				holder.tvETA.setVisibility(View.GONE);
				holder.tvETA.setText("");
			}
		}
		if (holder.tvUlRate != null) {
			long rateUpload = MapUtils.getMapLong(item, "rateUpload", 0);

			if (rateUpload > 0) {
				holder.tvUlRate.setText("\u25B2 "
						+ DisplayFormatters.formatByteCountToKiBEtcPerSec(rateUpload));
			} else {
				holder.tvUlRate.setText("");
			}
		}
		if (holder.tvDlRate != null) {
			long rateDownload = MapUtils.getMapLong(item, "rateDownload", 0);

			if (rateDownload > 0) {
				holder.tvDlRate.setText("\u25BC "
						+ DisplayFormatters.formatByteCountToKiBEtcPerSec(rateDownload));
			} else {
				holder.tvDlRate.setText("");
			}
		}
		if (holder.tvStatus != null) {
			int status = MapUtils.getMapInt(item,
					TransmissionVars.TORRENT_FIELD_STATUS,
					TransmissionVars.TR_STATUS_STOPPED);
			long error = MapUtils.getMapLong(item, "error",
					TransmissionVars.TR_STAT_OK);
			if (error == TransmissionVars.TR_STAT_OK) {
				int id;
				switch (status) {
					case TransmissionVars.TR_STATUS_CHECK_WAIT:
					case TransmissionVars.TR_STATUS_CHECK:
						id = R.string.torrent_status_checking;
						break;

					case TransmissionVars.TR_STATUS_DOWNLOAD:
						id = R.string.torrent_status_download;
						break;

					case TransmissionVars.TR_STATUS_DOWNLOAD_WAIT:
						id = R.string.torrent_status_queued_dl;
						break;

					case TransmissionVars.TR_STATUS_SEED:
						id = R.string.torrent_status_seed;
						break;

					case TransmissionVars.TR_STATUS_SEED_WAIT:
						id = R.string.torrent_status_queued_ul;
						break;

					case TransmissionVars.TR_STATUS_STOPPED:
						id = R.string.torrent_status_stopped;
						break;

					default:
						id = -1;
						break;
				}
				if (id >= 0) {
					holder.tvStatus.setText(id);
				} else {
					holder.tvStatus.setText("");
				}
			} else {
				// error
				// TODO: parse error and add error type to message
				String errorString = MapUtils.getMapString(item, "errorString", "");
				holder.tvStatus.setText(errorString);
			}
		}

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
		private int filterMode;

		private CharSequence constraint;

		public void setFilterMode(int filterMode) {
			this.filterMode = filterMode;
			filter(constraint);
		}

		@Override
		protected FilterResults performFiltering(CharSequence constraint) {
			this.constraint = constraint;
			FilterResults results = new FilterResults();

			if (sessionInfo == null) {
				return results;
			}

			boolean hasConstraint = constraint != null && constraint.length() > 0;

			LinkedHashMap<Object, Map<?, ?>> mapOriginal = sessionInfo.getTorrentList();
			if (!hasConstraint && filterMode < 0) {
				synchronized (mLock) {
					results.values = mapOriginal.keySet();
					results.count = mapOriginal.size();
				}
				if (DEBUG) {
					System.out.println("doall=" + results.count);
				}
			} else {
				// might need to be LinkedHashMap to keep order if filter must be by order
				Map<?, Map<?, ?>> mapCopy = new HashMap<Object, Map<?, ?>>(mapOriginal);

				if (DEBUG) {
					System.out.println("doSome1=" + mapCopy.size());
				}

				if (filterMode >= 0 && filterMode != FILTERBY_ALL) {
					synchronized (mLock) {
						for (Iterator iterator = mapCopy.keySet().iterator(); iterator.hasNext();) {
							Object key = iterator.next();
							Map map = mapCopy.get(key);

							switch (filterMode) {
								case FILTERBY_ACTIVE:
									long dlRate = MapUtils.getMapLong(map,
											TransmissionVars.TORRENT_FIELD_RATE_DOWNLOAD, -1);
									long ulRate = MapUtils.getMapLong(map,
											TransmissionVars.TORRENT_FIELD_RATE_UPLOAD, -1);
									if (ulRate <= 0 && dlRate <= 0) {
										iterator.remove();
									}
									break;

								case FILTERBY_COMPLETE: {
									float pctDone = MapUtils.getMapFloat(map,
											TransmissionVars.TORRENT_FIELD_PERCENT_DONE, 0);
									if (pctDone < 1.0f) {
										iterator.remove();
									}
									break;
								}
								case FILTERBY_INCOMPLETE: {
									float pctDone = MapUtils.getMapFloat(map,
											TransmissionVars.TORRENT_FIELD_PERCENT_DONE, 0);
									if (pctDone >= 1.0f) {
										iterator.remove();
									}
									break;
								}
								case FILTERBY_STOPPED: {
									int status = MapUtils.getMapInt(map,
											TransmissionVars.TORRENT_FIELD_STATUS,
											TransmissionVars.TR_STATUS_STOPPED);
									if (status != TransmissionVars.TR_STATUS_STOPPED) {
										iterator.remove();
									}
									break;
								}
							}
						}
					}
				}

				if (DEBUG) {
					System.out.println("doSome2=" + mapCopy.size());
				}

				if (hasConstraint) {
					String constraintString = constraint.toString().toLowerCase();

					synchronized (mLock) {
						for (Iterator iterator = mapCopy.keySet().iterator(); iterator.hasNext();) {
							Object key = iterator.next();
							Map map = mapCopy.get(key);

							String name = MapUtils.getMapString(map,
									TransmissionVars.TORRENT_FIELD_NAME, "").toLowerCase();
							if (!name.contains(constraintString)) {
								iterator.remove();
							}
						}
					}

					if (DEBUG) {
						System.out.println("doSome3=" + mapCopy.size());
					}
				}


				results.values = mapCopy.keySet();
				results.count = mapCopy.size();

			}
			return results;
		}

		@Override
		protected void publishResults(CharSequence constraint, FilterResults results) {
			// Now we have to inform the adapter about the new list filtered
			if (results.count == 0) {
				displayList.clear();
				notifyDataSetInvalidated();
			} else {
				synchronized (mLock) {
					if (results.values instanceof Collection) {
						displayList = new ArrayList<Object>((Collection<?>) results.values);
						doSort();
					}
				}
			}
		}

	}

	public void refreshDisplayList() {
		if (sessionInfo != null) {
			// How does this work with filters?
			Object[] keys = sessionInfo.getTorrentListKeys();
			synchronized (mLock) {
				for (Object key : keys) {
					if (!displayList.contains(key)) {
						displayList.add(key);
					}
				}
			}
		}
		doSort();
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
			return;
		}
		if (comparator == null && sortFieldIDs == null) {
			return;
		}
		System.out.println("sort: " + Arrays.asList(sortFieldIDs) + "/" + Arrays.asList(sortOrderAsc));
		synchronized (mLock) {
			final LinkedHashMap<Object, Map<?, ?>> mapOriginal = sessionInfo.getTorrentList();
			Collections.sort(displayList, new Comparator<Object>() {
				@SuppressWarnings({
					"unchecked",
					"rawtypes"
				})
				@Override
				public int compare(Object lhs, Object rhs) {
					Map<?, ?> mapLHS = mapOriginal.get(lhs);
					Map<?, ?> mapRHS = mapOriginal.get(rhs);

					if (mapLHS == null || mapRHS == null) {
						return 0;
					}

					if (sortFieldIDs == null) {
						return comparator.compare(mapLHS, mapRHS);
					} else {
						for (int i = 0; i < sortFieldIDs.length; i++) {
							String fieldID = sortFieldIDs[i];
							Comparable oLHS = (Comparable) mapLHS.get(fieldID);
							Comparable oRHS = (Comparable) mapRHS.get(fieldID);
							if (oLHS == null || oRHS == null) {
								System.out.println("no field:" + fieldID);
								if (oLHS != oRHS) {
									return oLHS == null ? -1 : 1;
								} // else == drops to next sort field

							} else {
								int comp = sortOrderAsc[i] ? oLHS.compareTo(oRHS)
										: oRHS.compareTo(oLHS);
								if (comp != 0) {
									return comp;
								} // else == drops to next sort field
							}
						}

						return 0;
					}
				}
			});
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
			return new HashMap();
		}
		LinkedHashMap<Object, Map<?, ?>> mapOriginal = sessionInfo.getTorrentList();
		return mapOriginal.get(displayList.get(position));
	}

	/* (non-Javadoc)
	 * @see android.widget.Adapter#getItemId(int)
	 */
	@Override
	public long getItemId(int position) {
		return position;
	}

}
