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

public class FilesAdapter
	extends BaseAdapter
	implements Filterable
{
	static class ViewHolder
	{
		TextView tvName;

		TextView tvProgress;

		ProgressBar pb;

		TextView tvInfo;

		TextView tvETA;

		TextView tvStatus;
	}

	private Context context;

	private FileFilter filter;

	/** List of they keys of all entries displayed, in the display order */
	private List<Object> displayList;

	public Object mLock = new Object();

	private Comparator<? super Map<?, ?>> comparator;

	private Resources resources;

	private String[] sortFieldIDs;

	private Boolean[] sortOrderAsc;

	private SessionInfo sessionInfo;

	private long torrentID;

	public FilesAdapter(Context context) {
		this.context = context;
		resources = context.getResources();
		displayList = new ArrayList<Object>();
	}

	public void setSessionInfo(SessionInfo sessionInfo) {
		this.sessionInfo = sessionInfo;
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
			rowView = inflater.inflate(R.layout.row_file_list, parent, false);
			ViewHolder viewHolder = new ViewHolder();

			viewHolder.tvName = (TextView) rowView.findViewById(R.id.filerow_name);

			viewHolder.tvProgress = (TextView) rowView.findViewById(R.id.filerow_progress_pct);
			viewHolder.pb = (ProgressBar) rowView.findViewById(R.id.filerow_progress);
			viewHolder.tvInfo = (TextView) rowView.findViewById(R.id.filerow_info);
			viewHolder.tvETA = (TextView) rowView.findViewById(R.id.filerow_eta);
			viewHolder.tvStatus = (TextView) rowView.findViewById(R.id.filerow_state);

			rowView.setTag(viewHolder);
		}

		ViewHolder holder = (ViewHolder) rowView.getTag();

		Map<?, ?> item = getItem(position);
		boolean wanted = MapUtils.getMapBoolean(item, "wanted", true);

		if (holder.tvName != null) {
			holder.tvName.setText(MapUtils.getMapString(item, "name", "??"));
		}
		long bytesCompleted = MapUtils.getMapLong(item, "bytesCompleted", 0);
		long length = MapUtils.getMapLong(item, "length", -1);
		if (length > 0) {
			float pctDone = (float) bytesCompleted / length;
			if (holder.tvProgress != null) {
				holder.tvProgress.setVisibility(wanted ? View.VISIBLE : View.INVISIBLE);
				NumberFormat format = NumberFormat.getPercentInstance();
				format.setMaximumFractionDigits(1);
				String s = format.format(pctDone);
				holder.tvProgress.setText(s);
			}
			if (holder.pb != null) {
				holder.pb.setVisibility(wanted ? View.VISIBLE : View.INVISIBLE);
				holder.pb.setProgress((int) (pctDone * 10000));
			}
		}
		if (holder.tvInfo != null) {
			String s = resources.getString(R.string.files_row_size,
					DisplayFormatters.formatByteCountToKiBEtc(bytesCompleted),
					DisplayFormatters.formatByteCountToKiBEtc(length));
			holder.tvInfo.setText(s);
		}
		if (holder.tvETA != null) {
			holder.tvETA.setVisibility(wanted ? View.VISIBLE : View.INVISIBLE);
			
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
		if (holder.tvStatus != null) {
			int priority = MapUtils.getMapInt(item,
					TransmissionVars.TORRENT_FIELD_FILES_PRIORITY,
					TransmissionVars.TR_PRI_NORMAL);
			int id;
			switch (priority) {
				case TransmissionVars.TR_PRI_HIGH:
					id = R.string.torrent_file_priority_high;
					break;
				case TransmissionVars.TR_PRI_LOW:
					id = R.string.torrent_file_priority_low;
					break;
				default:
					id = R.string.torrent_file_priority_normal;
					break;
			}

			holder.tvStatus.setText(id);
		}

		return rowView;
	}

	@Override
	public FileFilter getFilter() {
		if (filter == null) {
			filter = new FileFilter();
		}
		return filter;
	}

	public class FileFilter
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
			System.out.println("performFIlter Start");
			FilterResults results = new FilterResults();

			if (sessionInfo == null) {
				System.out.println("noSessionInfo");

				return results;
			}


			synchronized (mLock) {
				Map<?, ?> torrent = sessionInfo.getTorrent(torrentID);
				if (torrent == null) {
					return results;
				}
				final List listFiles = MapUtils.getMapList(torrent, "files", null);
				final List listFileStats = MapUtils.getMapList(torrent, "fileStats",
						null);
				//					System.out.println("listFiles=" + listFiles);
				if (listFiles == null) {
					return results;
				}
				System.out.println("listFiles=" + listFiles.size());
				if (listFileStats != null) {
					List mergedFileMaps = new ArrayList();
					for (int i = 0; i < listFiles.size() && i < listFileStats.size(); i++) {
						try {
							Map mapFile = (Map) listFiles.get(i);
							Map mapFileStats = (Map) listFileStats.get(i);

							Map map = new HashMap(mapFile);
							map.putAll(mapFileStats);
							mergedFileMaps.add(map);
						} catch (Throwable t) {
							VuzeEasyTracker.getInstance(context).logError(context, t);
							t.printStackTrace();
						}

					}
					results.values = mergedFileMaps;
					results.count = mergedFileMaps.size();
				} else {
					results.values = new ArrayList(listFiles);
					results.count = listFiles.size();
				}

			}

			System.out.println("performFIlter End");
			return results;
		}

		@Override
		protected void publishResults(CharSequence constraint, FilterResults results) {
			{
				synchronized (mLock) {
					displayList = (List<Object>) results.values;

					doSort();
				}
				notifyDataSetChanged();
			}
		}

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
		notifyDataSetChanged();
	}

	public void setSort(Comparator<? super Map<?, ?>> comparator) {
		synchronized (mLock) {
			this.comparator = comparator;
		}
		doSort();
		notifyDataSetChanged();
	}

	private void doSort() {
		if (sessionInfo == null) {
			return;
		}
		if (comparator == null && sortFieldIDs == null) {
			return;
		}
		synchronized (mLock) {

			Collections.sort(displayList, new Comparator<Object>() {
				@SuppressWarnings({
					"unchecked",
					"rawtypes"
				})
				@Override
				public int compare(Object lhs, Object rhs) {
					Map<?, ?> mapLHS = (Map) lhs;
					Map<?, ?> mapRHS = (Map) rhs;

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
	}

	/* (non-Javadoc)
	 * @see android.widget.Adapter#getCount()
	 */
	@Override
	public int getCount() {
		return displayList == null ? 0 : displayList.size();
	}

	/* (non-Javadoc)
	 * @see android.widget.Adapter#getItem(int)
	 */
	@Override
	public Map<?, ?> getItem(int position) {
		if (sessionInfo == null) {
			return new HashMap();
		}
		return (Map<?, ?>) displayList.get(position);
	}

	public void setTorrentID(long torrentID) {
		this.torrentID = torrentID;
		getFilter().filter("");
	}

	/* (non-Javadoc)
	 * @see android.widget.Adapter#getItemId(int)
	 */
	@Override
	public long getItemId(int position) {
		return position;
	}

}
