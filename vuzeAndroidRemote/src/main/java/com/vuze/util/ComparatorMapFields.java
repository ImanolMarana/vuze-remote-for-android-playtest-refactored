/*
 * Copyright (c) Azureus Software, Inc, All Rights Reserved.
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
 */

package com.vuze.util;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;

public abstract class ComparatorMapFields
	implements Comparator<Object>
{
	private String[] sortFieldIDs;

	private Boolean[] sortOrderAsc;

	private Comparator<? super Map<?, ?>> comparator;

	public ComparatorMapFields() {
	}

	public ComparatorMapFields(String[] sortFieldIDs, Boolean[] sortOrderAsc) {
		this.sortOrderAsc = sortOrderAsc;
		this.sortFieldIDs = sortFieldIDs;
	}

	public ComparatorMapFields(String[] sortFieldIDs, Boolean[] sortOrderAsc,
			Comparator<? super Map<?, ?>> comparator) {
		this.sortOrderAsc = sortOrderAsc;
		this.sortFieldIDs = sortFieldIDs;
		this.comparator = comparator;
	}

	public ComparatorMapFields(Comparator<? super Map<?, ?>> comparator) {
		this.comparator = comparator;
	}

	public void setSortFields(String[] sortFieldIDs, Boolean[] sortOrderAsc) {
		this.sortFieldIDs = sortFieldIDs;
		this.sortOrderAsc = sortOrderAsc;
		this.comparator = null;
	}

	public void setComparator(Comparator comparator) {
		this.comparator = comparator;
		this.sortFieldIDs = null;
	}

	public boolean isValid() {
		return comparator != null || sortFieldIDs != null;
	}

	public String toDebugString() {
		return Arrays.asList(sortFieldIDs) + "/" + Arrays.asList(sortOrderAsc);
	}

	public abstract Map<?, ?> mapGetter(Object o);

	@SuppressWarnings("UnusedParameters")
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
      return compareUsingComparator(mapLHS, mapRHS);
    } else {
      return compareUsingSortFields(mapLHS, mapRHS);
    }
  }

  private int compareUsingComparator(Map<?, ?> mapLHS, Map<?, ?> mapRHS) {
    if (comparator == null) {
      return 0;
    }
    return comparator.compare(mapLHS, mapRHS);
  }

  private int compareUsingSortFields(Map<?, ?> mapLHS, Map<?, ?> mapRHS) {
    for (int i = 0; i < sortFieldIDs.length; i++) {
      String fieldID = sortFieldIDs[i];
      Comparable oLHS = (Comparable) mapLHS.get(fieldID);
      Comparable oRHS = (Comparable) mapRHS.get(fieldID);

      int comp = compareFieldValues(fieldID, mapLHS, mapRHS, oLHS, oRHS, i);
      if (comp != 0) {
        return comp;
      } 
    }
    return 0;
  }

  private int compareFieldValues(String fieldID, Map<?, ?> mapLHS, Map<?, ?> mapRHS, Comparable oLHS,
      Comparable oRHS, int i) {
    if (oLHS == null || oRHS == null) {
      return compareNullValues(oLHS, oRHS);
    } else {
      return compareNonNUllValues(fieldID, mapLHS, mapRHS, oLHS, oRHS, i);
    }
  }
  
  private int compareNullValues(Comparable oLHS, Comparable oRHS){
    if (oLHS != oRHS) {
      return oLHS == null ? -1 : 1;
    }
    return 0;
  }
  
  private int compareNonNUllValues(String fieldID, Map<?, ?> mapLHS, Map<?, ?> mapRHS, Comparable oLHS,
      Comparable oRHS, int i){
    oLHS = modifyField(fieldID, mapLHS, oLHS);
    oRHS = modifyField(fieldID, mapRHS, oRHS);

    if ((oLHS instanceof String) && (oRHS instanceof String)) {
      return compareStringValues(oLHS, oRHS, i);
    } else if (oRHS instanceof Number && oLHS instanceof Number) {
      return compareNumberValues(oLHS, oRHS, i);
    } else {
      return comapreGenericValues(oLHS, oRHS, i);
    } 
  }

  private int compareStringValues(Comparable oLHS, Comparable oRHS, int i){
    return sortOrderAsc[i]
        ? ((String) oLHS).compareToIgnoreCase((String) oRHS)
        : ((String) oRHS).compareToIgnoreCase((String) oLHS);
  }

  private int compareNumberValues(Comparable oLHS, Comparable oRHS, int i){
    if (oRHS instanceof Double || oLHS instanceof Double
        || oRHS instanceof Float || oLHS instanceof Float) {
      double dRHS = ((Number) oRHS).doubleValue();
      double dLHS = ((Number) oLHS).doubleValue();
      return sortOrderAsc[i] ? Double.compare(dLHS, dRHS)
          : Double.compare(dRHS, dLHS);
    } else {
      // convert to long so we can compare Integer and Long objects
      long lRHS = ((Number) oRHS).longValue();
      long lLHS = ((Number) oLHS).longValue();
      // Not available until API 19
      // comp = sortOrderAsc[i] ? Long.compare(lLHS, lRHS) :Long.compare(lRHS, lLHS);
      if (sortOrderAsc[i]) {
        return lLHS > lRHS ? 1 : lLHS == lRHS ? 0 : -1;
      } else {
        return lLHS > lRHS ? -1 : lLHS == lRHS ? 0 : 1;
      }
    }
  }

  private int comapreGenericValues(Comparable oLHS, Comparable oRHS, int i){
    try {
      return sortOrderAsc[i] ? oRHS.compareTo(oLHS)
          : oLHS.compareTo(oRHS);
    } catch (Throwable t) {
      return reportError(oLHS, oRHS, t);
    }
  }
  
//Refactoring end
					if ((oLHS instanceof String) && (oRHS instanceof String)) {
						comp = sortOrderAsc[i]
								? ((String) oLHS).compareToIgnoreCase((String) oRHS)
								: ((String) oRHS).compareToIgnoreCase((String) oLHS);
					} else if (oRHS instanceof Number && oLHS instanceof Number) {
						if (oRHS instanceof Double || oLHS instanceof Double
								|| oRHS instanceof Float || oLHS instanceof Float) {
							double dRHS = ((Number) oRHS).doubleValue();
							double dLHS = ((Number) oLHS).doubleValue();
							comp = sortOrderAsc[i] ? Double.compare(dLHS, dRHS)
									: Double.compare(dRHS, dLHS);
						} else {
							// convert to long so we can compare Integer and Long objects
							long lRHS = ((Number) oRHS).longValue();
							long lLHS = ((Number) oLHS).longValue();
							// Not available until API 19
							// comp = sortOrderAsc[i] ? Long.compare(lLHS, lRHS) :Long.compare(lRHS, lLHS);
							if (sortOrderAsc[i]) {
								comp = lLHS > lRHS ? 1 : lLHS == lRHS ? 0 : -1;
							} else {
								comp = lLHS > lRHS ? -1 : lLHS == lRHS ? 0 : 1;
							}
						}
					} else {
						try {
							comp = sortOrderAsc[i] ? oRHS.compareTo(oLHS)
									: oLHS.compareTo(oRHS);
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

	public Comparable modifyField(String fieldID, Map<?, ?> map, Comparable o) {
		return o;
	}
}