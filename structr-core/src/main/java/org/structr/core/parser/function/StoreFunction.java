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

import org.structr.common.error.FrameworkException;
import org.structr.core.GraphObject;
import org.structr.schema.action.ActionContext;
import org.structr.schema.action.Function;

/**
 *
 */
public class StoreFunction extends Function<Object, Object> {

	public static final String ERROR_MESSAGE_STORE    = "Usage: ${store(key, value)}. Example: ${store('tmpUser', this.owner)}";
	public static final String ERROR_MESSAGE_STORE_JS = "Usage: ${{Structr.store(key, value)}}. Example: ${{Structr.store('tmpUser', Structr.get('this').owner)}}";

	@Override
	public String getName() {
		return "store()";
	}

	@Override
	public Object apply(final ActionContext ctx, final GraphObject entity, final Object[] sources) throws FrameworkException {

		if (sources.length > 0 && sources[0] != null && sources[0] instanceof String) {

			if (sources[1] != null) {

				ctx.store(sources[0].toString(), sources[1]);
			}

		} else {

			return usage(ctx.isJavaScriptContext());
		}

		return "";
	}


	@Override
	public String usage(boolean inJavaScriptContext) {
		return (inJavaScriptContext ? ERROR_MESSAGE_STORE_JS : ERROR_MESSAGE_STORE);
	}

	@Override
	public String shortDescription() {
		return "Stores the given value with the given key in the temporary store";
	}

}
