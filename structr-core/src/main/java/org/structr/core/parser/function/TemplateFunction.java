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
import org.structr.core.app.App;
import org.structr.core.app.StructrApp;
import org.structr.core.entity.AbstractNode;
import org.structr.core.entity.MailTemplate;
import org.structr.core.script.Scripting;
import org.structr.schema.action.ActionContext;
import org.structr.schema.action.Function;

/**
 *
 */
public class TemplateFunction extends Function<Object, Object> {

	public static final String ERROR_MESSAGE_TEMPLATE    = "Usage: ${template(name, locale, source)}. Example: ${template(\"TEXT_TEMPLATE_1\", \"en_EN\", this)}";
	public static final String ERROR_MESSAGE_TEMPLATE_JS = "Usage: ${{Structr.template(name, locale, source)}}. Example: ${{Structr.template(\"TEXT_TEMPLATE_1\", \"en_EN\", Structr.get('this'))}}";

	@Override
	public String getName() {
		return "template()";
	}

	@Override
	public Object apply(final ActionContext ctx, final GraphObject entity, final Object[] sources) throws FrameworkException {

		if (sources == null || sources != null && sources.length != 3) {
			return usage(ctx.isJavaScriptContext());
		}

		if (arrayHasLengthAndAllElementsNotNull(sources, 3) && sources[2] instanceof AbstractNode) {

			final App app = StructrApp.getInstance(entity != null ? entity.getSecurityContext() : ctx.getSecurityContext());
			final String name = sources[0].toString();
			final String locale = sources[1].toString();
			final MailTemplate template = app.nodeQuery(MailTemplate.class).andName(name).and(MailTemplate.locale, locale).getFirst();
			final AbstractNode templateInstance = (AbstractNode)sources[2];

			if (template != null) {

				final String text = template.getProperty(MailTemplate.text);
				if (text != null) {

					// recursive replacement call, be careful here
					return Scripting.replaceVariables(ctx, templateInstance, text);
				}
			}
		}

		return "";
	}


	@Override
	public String usage(boolean inJavaScriptContext) {
		return (inJavaScriptContext ? ERROR_MESSAGE_TEMPLATE_JS : ERROR_MESSAGE_TEMPLATE);
	}

	@Override
	public String shortDescription() {
		return "Returns a MailTemplate object with the given name, replaces the placeholders with values from the given entity";
	}

}
