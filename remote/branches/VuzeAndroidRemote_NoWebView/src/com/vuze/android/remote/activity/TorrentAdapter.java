package com.vuze.android.remote.activity;

import java.text.NumberFormat;
import java.util.*;

import org.gudy.azureus2.core3.util.DisplayFormatters;

import android.content.Context;
import android.content.res.Resources;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import com.aelitis.azureus.util.MapUtils;
import com.vuze.android.remote.R;
import com.vuze.android.remote.TransmissionVars;

public class TorrentAdapter
	extends BaseAdapter
	implements Filterable
{
	private Context context;

	private TorrentFilter filter;

	/** <Key, TorrentMap> */
	private LinkedHashMap<Object, Map<?, ?>> mapOriginal;

	/** List of they keys of all entries displayed, in the display order */
	private List<Object> displayList;

	public Object mLock = new Object();

	private Comparator<? super Map<?, ?>> comparator;

	private Resources resources;

	public TorrentAdapter(Context context) {
		this.context = context;
		resources = context.getResources();
		this.mapOriginal = new LinkedHashMap<Object, Map<?, ?>>();
		displayList = new ArrayList<Object>();
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		View rowView = inflater.inflate(R.layout.row_torrent_list, parent, false);

		boolean isChecked = false;
		if (parent instanceof ListView) {
			isChecked = ((ListView) parent).isItemChecked(position);
			System.out.println(position + " checked? " + isChecked);
		}

		rowView.setBackgroundColor(isChecked
				? resources.getColor(R.color.list_bg_f) : 0);

		TextView tvName = (TextView) rowView.findViewById(R.id.torrentrow_name);

		TextView tvProgress = (TextView) rowView.findViewById(R.id.torrentrow_progress_pct);
		ProgressBar pb = (ProgressBar) rowView.findViewById(R.id.torrentrow_progress);
		TextView tvInfo = (TextView) rowView.findViewById(R.id.torrentrow_info);
		TextView tvETA = (TextView) rowView.findViewById(R.id.torrentrow_eta);
		TextView tvUlRate = (TextView) rowView.findViewById(R.id.torrentrow_upspeed);
		TextView tvDlRate = (TextView) rowView.findViewById(R.id.torrentrow_downspeed);
		TextView tvStatus = (TextView) rowView.findViewById(R.id.torrentrow_state);

		Map<?, ?> item = getItem(position);
		if (tvName != null) {
			tvName.setText(MapUtils.getMapString(item, "name", "??"));
		}
		float pctDone = MapUtils.getMapFloat(item, "percentDone", 0f);
		if (tvProgress != null) {
			NumberFormat format = NumberFormat.getPercentInstance();
			format.setMaximumFractionDigits(1);
			String s = format.format(pctDone);
			tvProgress.setText(s);
		}
		if (pb != null) {
			pb.setProgress((int) (pctDone * 10000));
		}
		if (tvInfo != null) {
			Resources resources = parent.getResources();

			int fileCount = MapUtils.getMapInt(item, "fileCount", 0);
			long size = MapUtils.getMapLong(item, "sizeWhenDone", 0);

			String s = resources.getQuantityString(R.plurals.torrent_row_info,
					fileCount, fileCount)
					+ resources.getString(R.string.torrent_row_info2,
							DisplayFormatters.formatByteCountToKiBEtc(size));
			tvInfo.setText(s);
		}
		if (tvETA != null) {
			long etaSecs = MapUtils.getMapLong(item, "eta", -1);
			if (etaSecs > 0) {
				String s = DisplayFormatters.prettyFormat(etaSecs);
				tvETA.setText(s);
				tvETA.setVisibility(View.VISIBLE);
			} else {
				tvETA.setVisibility(View.GONE);
				tvETA.setText("");
			}
		}
		if (tvUlRate != null) {
			long rateUpload = MapUtils.getMapLong(item, "rateUpload", 0);

			if (rateUpload > 0) {
				tvUlRate.setText("\u25B2 "
						+ DisplayFormatters.formatByteCountToKiBEtcPerSec(rateUpload));
			} else {
				tvUlRate.setText("");
			}
		}
		if (tvDlRate != null) {
			long rateDownload = MapUtils.getMapLong(item, "rateDownload", 0);

			if (rateDownload > 0) {
				tvDlRate.setText("\u25BC "
						+ DisplayFormatters.formatByteCountToKiBEtcPerSec(rateDownload));
			} else {
				tvDlRate.setText("");
			}
		}
		if (tvStatus != null) {
			int status = MapUtils.getMapInt(item, "status", TransmissionVars.TR_STATUS_STOPPED);
			long error = MapUtils.getMapLong(item, "error", TransmissionVars.TR_STAT_OK);
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
					tvStatus.setText(id);
				} else {
					tvStatus.setText("");
				}
			} else {
				// error
				// TODO: parse error and add error type to message
				String errorString = MapUtils.getMapString(item, "errorString", "");
				tvStatus.setText(errorString);
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

	private class TorrentFilter
		extends Filter
	{

		@Override
		protected FilterResults performFiltering(CharSequence constraint) {
			FilterResults results = new FilterResults();

			if (constraint == null || constraint.length() == 0) {
				synchronized (mLock) {
					results.values = mapOriginal.keySet();
					results.count = mapOriginal.size();
				}
			} else {
				String constraintString = constraint.toString().toLowerCase();

				List<Object> nList = new ArrayList<Object>();

				for (Map<?, ?> map : mapOriginal.values()) {
					String name = MapUtils.getMapString(map, "name", "").toLowerCase();
					if (name.contains(constraintString)) {
						nList.add(map.get("id"));
					}
				}

				results.values = nList;
				results.count = nList.size();

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
					}
				}
				notifyDataSetChanged();
			}
		}

	}

	public void addAll(Collection<? extends Map<?, ?>> collection) {
		// How does this work with filters?
		synchronized (mLock) {
			for (Map<?, ?> mapTorrent : collection) {
				Object key = mapTorrent.get("id");
				mapOriginal.put(key, mapTorrent);
				displayList.add(key);
			}
		}
		doSort();
		notifyDataSetChanged();
	}

	public void setSort(Comparator<? super Map<?, ?>> comparator) {
		this.comparator = comparator;
		doSort();
		notifyDataSetChanged();
	}

	private void doSort() {
		if (comparator == null) {
			return;
		}
		synchronized (mLock) {
			Collections.sort(displayList, new Comparator<Object>() {
				@Override
				public int compare(Object lhs, Object rhs) {
					Map<?, ?> mapLHS = mapOriginal.get(lhs);
					Map<?, ?> mapRHS = mapOriginal.get(rhs);

					return comparator.compare(mapLHS, mapRHS);
				}
			});
		}
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
