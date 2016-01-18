/**
 * Copyright (C) 2010-2016 Structr GmbH
 *
 * This file is part of Structr <http://structr.org>.
 *
 * Structr is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * Structr is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Structr.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.structr.common;

import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import org.structr.api.util.Iterables;
import org.structr.api.graph.Relationship;

/**
 *
 *
 */
public class IdSorter<T extends Relationship> implements Iterable<T>, Comparator<T> {

	private List<T> sortedList = null;

	public IdSorter(final Iterable<T> source) {
		this.sortedList = Iterables.toList(source);
		Collections.sort(sortedList, this);
	}

	@Override
	public Iterator<T> iterator() {
		return sortedList.iterator();
	}

	@Override
	public int compare(T o1, T o2) {

		final long id1 = o1.getId();
		final long id2 = o2.getId();

		return id1 < id2 ? -1 : id1 > id2 ? 1 : 0;
	}
}
