package com.vuze.android.remote.fragment;

import java.text.NumberFormat;
import java.util.*;

import org.gudy.azureus2.core3.util.DisplayFormatters;

import android.content.Context;
import android.content.res.Resources;
import android.text.SpannableString;
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

	static class ViewHolder
	{
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

	private Resources resources;

	private String[] sortFieldIDs;

	private Boolean[] sortOrderAsc;

	private SessionInfo sessionInfo;

	private int colorBGTagState;

	private int colorFGTagState;

	private int colorBGTagType0;

	private int colorFGTagType0;

	private int colorBGTagCat;

	private int colorFGTagCat;

	private int colorBGTagManual;

	private int colorFGTagManual;

	private TextViewFlipper flipper;

	public TorrentListAdapter(Context context) {
		this.context = context;
		resources = context.getResources();
		colorBGTagState = resources.getColor(R.color.bg_tag_type_2);
		colorFGTagState = resources.getColor(R.color.fg_tag_type_2);
		colorBGTagType0 = resources.getColor(R.color.bg_tag_type_0);
		colorFGTagType0 = resources.getColor(R.color.fg_tag_type_0);
		colorBGTagCat = resources.getColor(R.color.bg_tag_type_cat);
		colorFGTagCat = resources.getColor(R.color.fg_tag_type_cat);
		colorBGTagManual = resources.getColor(R.color.bg_tag_type_manualtag);
		colorFGTagManual = resources.getColor(R.color.fg_tag_type_manualtag);

		flipper = new TextViewFlipper(R.anim.anim_field_change);

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
		long torrentID = MapUtils.getMapLong(item, "id", -1);

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
			viewHolder.tvTags = (TextView) rowView.findViewById(R.id.torrentrow_tags);

			rowView.setTag(viewHolder);
		}

		final ViewHolder holder = (ViewHolder) rowView.getTag();

		holder.animateFlip = holder.torrentID == torrentID;
		holder.torrentID = torrentID;
		ViewHolderFlipValidator validator = new ViewHolderFlipValidator(holder,
				torrentID);

		//		boolean isChecked = false;
		//		if (parent instanceof ListView) {
		//			isChecked = ((ListView) parent).isItemChecked(position);
		//			System.out.println(position + " checked? " + isChecked);
		//		}

		//		rowView.setBackgroundColor(isChecked
		//				? resources.getColor(R.color.list_bg_f) : 0);

		if (holder.tvName != null) {
			flipper.changeText(holder.tvName,
					MapUtils.getMapString(item, "name", "??"), holder.animateFlip,
					validator);
		}

		float pctDone = MapUtils.getMapFloat(item, "percentDone", 0f);
		if (holder.tvProgress != null) {
			NumberFormat format = NumberFormat.getPercentInstance();
			format.setMaximumFractionDigits(1);
			String s = format.format(pctDone);
			flipper.changeText(holder.tvProgress, s, holder.animateFlip, validator);
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
			long error = MapUtils.getMapLong(item, "error",
					TransmissionVars.TR_STAT_OK);
			if (error != TransmissionVars.TR_STAT_OK) {
				// error
				// TODO: parse error and add error type to message
				String errorString = MapUtils.getMapString(item, "errorString", "");
				if (s.length() > 0) {
					s += "\n";
				}
				s += errorString;
			}

			flipper.changeText(holder.tvInfo, s, holder.animateFlip, validator);
		}
		if (holder.tvETA != null) {
			long etaSecs = MapUtils.getMapLong(item, "eta", -1);
			String eta = etaSecs > 0 ? DisplayFormatters.prettyFormat(etaSecs) : "";
			flipper.changeText(holder.tvETA, eta, holder.animateFlip, validator);
		}
		if (holder.tvUlRate != null) {
			long rateUpload = MapUtils.getMapLong(item, "rateUpload", 0);

			String rateString = rateUpload <= 0 ? "" : "\u25B2 "
					+ DisplayFormatters.formatByteCountToKiBEtcPerSec(rateUpload);
			flipper.changeText(holder.tvUlRate, rateString, holder.animateFlip,
					validator);
		}
		if (holder.tvDlRate != null) {
			long rateDownload = MapUtils.getMapLong(item, "rateDownload", 0);
			String rateString = rateDownload <= 0 ? "" : "\u25BC "
					+ DisplayFormatters.formatByteCountToKiBEtcPerSec(rateDownload);
			flipper.changeText(holder.tvDlRate, rateString, holder.animateFlip,
					validator);
		}

		if (holder.tvStatus != null) {
			List<?> mapTagUIDs = MapUtils.getMapList(item, "tag-uids", null);
			StringBuilder text = new StringBuilder();
			int color = -1;

			if (mapTagUIDs == null) {

				int status = MapUtils.getMapInt(item,
						TransmissionVars.TORRENT_FIELD_STATUS,
						TransmissionVars.TR_STATUS_STOPPED);
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
					text.append(context.getString(id));
				}
			} else {
				for (Object o : mapTagUIDs) {
					String name = null;
					int type = 0;
					if (o instanceof Number) {
						Map<?, ?> mapTag = sessionInfo.getTag(((Number) o).longValue());
						if (mapTag != null) {
							String htmlColor = MapUtils.getMapString(mapTag, "color", null);
							if (htmlColor != null && htmlColor.startsWith("#")) {
								color = Integer.decode("0x" + htmlColor.substring(1));
							}
							name = MapUtils.getMapString(mapTag, "name", null);
							type = MapUtils.getMapInt(mapTag, "type", 0);
						}
					}
					if (type != 2) {
						continue;
					}
					if (name == null) {
						name = o.toString();
					}
					if (text.length() > 0) {
						text.append(" ");
					}
					text.append("| ");
					text.append(name);
					text.append(" |");
				}
			}

			SpannableString ss = new SpannableString(text);
			String string = text.toString();
			AndroidUtils.setSpanBubbles(ss, string, "|", holder.tvStatus.getPaint(),
					color < 0 ? colorBGTagState : color, colorFGTagState, colorBGTagState);
			flipper.changeText(holder.tvStatus, ss, holder.animateFlip, validator);
		}

		if (holder.tvTags != null) {
			List<?> mapTagUIDs = MapUtils.getMapList(item, "tag-uids", null);
			StringBuilder sb = new StringBuilder();
			if (mapTagUIDs != null) {
				for (Object o : mapTagUIDs) {
					String name = null;
					int type = 0;
					// TODO: Use Color
					long color = -1;
					if (o instanceof Number) {
						Map<?, ?> mapTag = sessionInfo.getTag(((Number) o).longValue());
						if (mapTag != null) {
							type = MapUtils.getMapInt(mapTag, "type", 0);
							if (type == 2) {
								continue;
							}
							if (type == 1) {
								boolean canBePublic = MapUtils.getMapBoolean(mapTag,
										"canBePublic", false);
								if (!canBePublic) {
									continue;
								}
							}
							name = MapUtils.getMapString(mapTag, "name", null);
							String htmlColor = MapUtils.getMapString(mapTag, "color", null);
							if (htmlColor != null && htmlColor.startsWith("#")) {
								color = Long.decode("0x" + htmlColor.substring(1));
							}
						}
					}
					if (name == null) {
						name = o.toString();
					}
					if (sb.length() > 0) {
						sb.append(" ");
					}
					if (type > 3) {
						type = 3;
					}
					String token = "~" + type + "~";
					sb.append(token);
					sb.append(" ");
					sb.append(name);
					sb.append(" ");
					sb.append(token);
				}
			}
			if (sb.length() == 0) {
				flipper.changeText(holder.tvTags, "", holder.animateFlip, validator);
			} else {
				SpannableString ss = new SpannableString(sb);
				String string = sb.toString();
				int color = -1;
				AndroidUtils.setSpanBubbles(ss, string, "~0~",
						holder.tvStatus.getPaint(), color < 0 ? colorBGTagType0 : color,
						colorFGTagType0, colorBGTagType0);
				AndroidUtils.setSpanBubbles(ss, string, "~1~",
						holder.tvStatus.getPaint(), color < 0 ? colorBGTagCat : color,
						colorFGTagCat, colorBGTagCat);
				AndroidUtils.setSpanBubbles(ss, string, "~3~",
						holder.tvStatus.getPaint(), color < 0 ? colorBGTagManual : color,
						colorFGTagManual, colorBGTagManual);
				flipper.changeText(holder.tvTags, ss, holder.animateFlip, validator);
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
		private long filterMode;

		private String constraint;

		public void setFilterMode(long filterMode) {
			this.filterMode = filterMode;
			filter(constraint);
		}

		@Override
		protected FilterResults performFiltering(CharSequence _constraint) {
			this.constraint = _constraint == null ? null
					: _constraint.toString().toLowerCase();
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
					System.out.println("filtering " + results.count);
				}
			} else {
				if (DEBUG) {
					System.out.println("filtering " + listKeys.size());
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
					System.out.println("type filtered to " + listKeys.size());
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
						System.out.println("text filtered to " + listKeys.size());
					}
				}

				results.values = listKeys;
				results.count = listKeys.size();

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
					if (results.values instanceof List) {
						displayList = (List) results.values;
						doSort();
					}
				}
			}
		}

	}

	public void refreshDisplayList() {
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
				TransmissionVars.TORRENT_FIELD_NAME, "").toLowerCase();
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
						TransmissionVars.TORRENT_FIELD_RATE_DOWNLOAD, -1);
				long ulRate = MapUtils.getMapLong(map,
						TransmissionVars.TORRENT_FIELD_RATE_UPLOAD, -1);
				if (ulRate <= 0 && dlRate <= 0) {
					return false;
				}
				break;

			case FILTERBY_COMPLETE: {
				float pctDone = MapUtils.getMapFloat(map,
						TransmissionVars.TORRENT_FIELD_PERCENT_DONE, 0);
				if (pctDone < 1.0f) {
					return false;
				}
				break;
			}
			case FILTERBY_INCOMPLETE: {
				float pctDone = MapUtils.getMapFloat(map,
						TransmissionVars.TORRENT_FIELD_PERCENT_DONE, 0);
				if (pctDone >= 1.0f) {
					return false;
				}
				break;
			}
			case FILTERBY_STOPPED: {
				int status = MapUtils.getMapInt(map,
						TransmissionVars.TORRENT_FIELD_STATUS,
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
		System.out.println("sort: " + Arrays.asList(sortFieldIDs) + "/"
				+ Arrays.asList(sortOrderAsc));
		synchronized (mLock) {
			Collections.sort(displayList, new Comparator<Object>() {
				@SuppressWarnings({
					"unchecked",
					"rawtypes"
				})
				@Override
				public int compare(Object lhs, Object rhs) {
					Map<?, ?> mapLHS = sessionInfo.getTorrent(lhs);
					Map<?, ?> mapRHS = sessionInfo.getTorrent(rhs);

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
								int comp;
								if (oRHS instanceof Number && oLHS instanceof Number) {
									long lRHS = ((Number) oRHS).longValue();
									long lLHS = ((Number) oLHS).longValue();
									if (sortOrderAsc[i]) {
										comp = lLHS > lRHS ? 1 : lLHS == lRHS ? 0 : -1;
									} else {
										comp = lLHS > lRHS ? -1 : lLHS == lRHS ? 0 : 1;
									}
								} else {
									try {
										comp = sortOrderAsc[i] ? oLHS.compareTo(oRHS)
												: oRHS.compareTo(oLHS);
									} catch (Throwable t) {
										VuzeEasyTracker.getInstance(context).logError(context, t);
										comp = 0;
									}
								}
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
