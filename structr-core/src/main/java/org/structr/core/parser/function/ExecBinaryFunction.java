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

import java.io.OutputStream;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.lang3.StringUtils;
import org.structr.common.SecurityContext;
import org.structr.common.error.FrameworkException;
import org.structr.core.GraphObject;
import org.structr.core.app.StructrApp;
import org.structr.schema.action.ActionContext;
import org.structr.schema.action.Function;
import org.structr.util.AbstractBinaryProcess;

/**
 *
 */
public class ExecBinaryFunction extends Function<Object, Object> {

	private static final Logger logger = Logger.getLogger(ExecBinaryFunction.class.getName());

	public static final String ERROR_MESSAGE_EXEC    = "Usage: ${exec_binary(output, fileName [, parameters...]}";
	public static final String ERROR_MESSAGE_EXEC_JS = "Usage: ${{Structr.exec_binary(output, fileName [, parameters...]}}";

	@Override
	public String getName() {
		return "exec_binary()";
	}

	@Override
	public Object apply(final ActionContext ctx, final GraphObject entity, final Object[] sources) throws FrameworkException {

		if (arrayHasMinLengthAndAllElementsNotNull(sources, 1)) {

			final String scriptKey = sources[1].toString();
			final String script    = StructrApp.getConfigurationValue(scriptKey);
			final OutputStream out = (OutputStream)sources[0];

			if (StringUtils.isNotBlank(script)) {

				final StringBuilder scriptBuilder = new StringBuilder(script);
				if (sources.length > 2) {

					for (int i = 2; i < sources.length; i++) {
						if (sources[i] != null) {

							scriptBuilder.append(" ").append(sources[i].toString());
						}
					}
				}

				final ExecutorService executorService = Executors.newSingleThreadExecutor();
				final ScriptingProcess process        = new ScriptingProcess(ctx.getSecurityContext(), scriptBuilder.toString(), out);

				try {

					return executorService.submit(process).get();

				} catch (InterruptedException | ExecutionException iex) {

					iex.printStackTrace();

				} finally {

					executorService.shutdown();
				}

			} else {

				logger.log(Level.WARNING, "No script found for key {0} in structr.conf, nothing executed.", scriptKey);
			}
		}

		return "";
	}


	@Override
	public String usage(boolean inJavaScriptContext) {
		return (inJavaScriptContext ? ERROR_MESSAGE_EXEC_JS : ERROR_MESSAGE_EXEC);
	}

	@Override
	public String shortDescription() {
		return "Calls the given exported / dynamic method on the given entity";
	}

	private static class ScriptingProcess extends AbstractBinaryProcess<String> {

		private final StringBuilder commandLine = new StringBuilder();

		public ScriptingProcess(final SecurityContext securityContext, final String commandLine, final OutputStream out) {

			super(securityContext, out);

			this.commandLine.append(commandLine);
		}

		@Override
		public StringBuilder getCommandLine() {
			return commandLine;
		}

		@Override
		public String processExited(final int exitCode) {
			return errorStream();
		}

		@Override
		public void preprocess() {
		}
	}
}
