package com.vuze.android.remote;

import java.util.Comparator;
import java.util.Map;

public abstract class ComparatorMapFields
	implements Comparator<Object>
{
	private String[] sortFieldIDs;

	private Boolean[] sortOrderAsc;

	private Comparator comparator;

	public ComparatorMapFields(String[] sortFieldIDs, Boolean[] sortOrderAsc) {
		this.sortOrderAsc = sortOrderAsc;
		this.sortFieldIDs = sortFieldIDs;
	}

	public ComparatorMapFields(String[] sortFieldIDs, Boolean[] sortOrderAsc,
			Comparator<?> comparator) {
		this.sortOrderAsc = sortOrderAsc;
		this.sortFieldIDs = sortFieldIDs;
		this.comparator = comparator;
	}

	public ComparatorMapFields(Comparator comparator) {
		this.comparator = comparator;
	}

	public abstract Map<?, ?> mapGetter(Object o);

	public abstract int reportError(Comparable<?> oLHS, Comparable<?> oRHS,
			Throwable t);

	@SuppressWarnings({
		"unchecked",
		"rawtypes"
	})
	@Override
	public int compare(Object lhs, Object rhs) {
		Map<?, ?> mapLHS = mapGetter(lhs);
		Map<?, ?> mapRHS = mapGetter(rhs);

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
					int comp;
					if ((oLHS instanceof String) && (oLHS instanceof String)) {
						comp = sortOrderAsc[i]
								? ((String) oLHS).compareToIgnoreCase((String) oRHS)
								: ((String) oRHS).compareToIgnoreCase((String) oLHS);
					} else if (oRHS instanceof Number && oLHS instanceof Number) {
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
							comp = reportError(oLHS, oRHS, t);
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
}