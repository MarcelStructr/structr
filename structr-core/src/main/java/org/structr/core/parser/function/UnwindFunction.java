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
package org.structr.core.parser.function;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.structr.common.error.FrameworkException;
import org.structr.core.GraphObject;
import org.structr.schema.action.ActionContext;
import org.structr.schema.action.Function;

/**
 *
 */
public class UnwindFunction extends Function<Object, Object> {

	public static final String ERROR_MESSAGE_UNWIND = "Usage: ${unwind(list1, ...)}. Example: ${unwind(this.children)}";

	@Override
	public String getName() {
		return "unwind()";
	}

	@Override
	public Object apply(final ActionContext ctx, final GraphObject entity, final Object[] sources) throws FrameworkException {

		final List list = new ArrayList();
		for (final Object source : sources) {

			if (source instanceof Collection) {

				// filter null objects
				for (Object obj : (Collection)source) {
					if (obj != null) {

						if (obj instanceof Collection) {

							for (final Object elem : (Collection)obj) {

								if (elem != null) {

									list.add(elem);
								}
							}

						} else {

							list.add(obj);
						}
					}
				}

			} else if (source != null) {

				list.add(source);
			}
		}

		return list;
	}


	@Override
	public String usage(boolean inJavaScriptContext) {
		return ERROR_MESSAGE_UNWIND;
	}

	@Override
	public String shortDescription() {
		return "";
	}
}
