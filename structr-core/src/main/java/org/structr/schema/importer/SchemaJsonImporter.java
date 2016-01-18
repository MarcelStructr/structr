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
package org.structr.schema.importer;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.chemistry.opencmis.commons.impl.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.structr.common.error.FrameworkException;
import org.structr.core.app.App;
import org.structr.core.app.StructrApp;
import org.structr.core.graph.MaintenanceCommand;
import org.structr.core.graph.Tx;
import org.structr.schema.export.StructrSchema;
import org.structr.schema.json.InvalidSchemaException;
import org.structr.schema.json.JsonSchema;

/**
 * This class can handle Schema JSON documents 
 */
public class SchemaJsonImporter extends SchemaImporter implements MaintenanceCommand {

	private static final Logger logger = Logger.getLogger(SchemaJsonImporter.class.getName());

	
	@Override
	public void execute(Map<String, Object> attributes) throws FrameworkException {

		final String fileName = (String)attributes.get("file");
		final String source   = (String)attributes.get("source");
		final String url      = (String)attributes.get("url");

		if (fileName == null && source == null && url == null) {
			throw new FrameworkException(422, "Please supply file, url or source parameter.");
		}

		if (fileName != null && source != null) {
			throw new FrameworkException(422, "Please supply only one of file, url or source.");
		}

		if (fileName != null && url != null) {
			throw new FrameworkException(422, "Please supply only one of file, url or source.");
		}

		if (url != null && source != null) {
			throw new FrameworkException(422, "Please supply only one of file, url or source.");
		}

		try {

			if (fileName != null) {

				SchemaJsonImporter.importSchemaJson(IOUtils.readAllLines(new FileInputStream(fileName)));

			} else if (url != null) {

				SchemaJsonImporter.importSchemaJson(IOUtils.readAllLines(new URL(url).openStream()));

			} else if (source != null) {

				SchemaJsonImporter.importSchemaJson(source);
			}

		} catch (IOException ioex) {
			//ioex.printStackTrace();
			logger.log(Level.FINE, "Filename: " + fileName + ", URL: " + url + ", source: " + source, ioex);
		}

		//analyzeSchema();
	}


	public static void importSchemaJson(final String source) throws FrameworkException {

		// nothing to do
		if (StringUtils.isBlank(source)) {
			return;
		}
		
		final App app = StructrApp.getInstance();

		// isolate write output
		try (final Tx tx = app.tx()) {

			final JsonSchema schema;

			try {
				schema = StructrSchema.createFromSource(source);

			} catch (InvalidSchemaException | URISyntaxException ex) {
				throw new FrameworkException(422, ex.getMessage());
			}

			try {
				StructrSchema.extendDatabaseSchema(app, schema);

			} catch (URISyntaxException ex) {
				throw new FrameworkException(422, ex.getMessage());
			}


			tx.success();
		}
		
	}

	@Override
	public boolean requiresEnclosingTransaction() {
		return false;
	}
}
