/**
 * Copyright (C) 2010-2015 Structr GmbH
 *
 * This file is part of Structr <http://structr.org>.
 *
 * Structr is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * Structr is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Structr.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.structr.files.cmis.repository;

import org.apache.chemistry.opencmis.commons.data.AllowableActions;
import org.apache.chemistry.opencmis.commons.definitions.MutableTypeDefinition;
import org.apache.chemistry.opencmis.commons.definitions.TypeDefinition;
import org.apache.chemistry.opencmis.commons.enums.CmisVersion;
import org.apache.chemistry.opencmis.server.support.TypeDefinitionFactory;
import org.structr.cmis.CMISInfo;
import org.structr.cmis.wrapper.CMISFolderWrapper;
import org.structr.core.Services;
import org.structr.core.app.StructrApp;
import org.structr.core.property.PropertyMap;
import org.structr.files.cmis.config.StructrRootFolderActions;
import org.structr.web.entity.Folder;

/**
 *
 *
 */
public class CMISRootFolder extends CMISFolderWrapper {

	private MutableTypeDefinition typeDefinition = null;

	public CMISRootFolder(final String propertyFilter, final Boolean includeAllowableActions, final Boolean includeAcl) {

		super(propertyFilter, includeAllowableActions, includeAcl);

		typeDefinition = TypeDefinitionFactory.newInstance().createBaseFolderTypeDefinition(CmisVersion.CMIS_1_1);

		setId(CMISInfo.ROOT_FOLDER_ID);
		setName(CMISInfo.ROOT_FOLDER_ID);
		setDescription("Root Folder");
		setType(Folder.class.getSimpleName());

		final String superuserName = StructrApp.getConfigurationValue(Services.SUPERUSER_USERNAME);
		this.createdBy      = superuserName;
		this.lastModifiedBy = superuserName;

		setCreationDate(CMISInfo.ROOT_FOLDER_DATE);
		setLastModificationDate(CMISInfo.ROOT_FOLDER_DATE);

		setPath(CMISInfo.ROOT_FOLDER_ID);
		setParentId(null);

		// dynamic properties
		dynamicPropertyMap = new PropertyMap();
		dynamicPropertyMap.put(Folder.includeInFrontendExport, false);
		dynamicPropertyMap.put(Folder.position, null);
	}


	@Override
	public AllowableActions getAllowableActions() {
		return StructrRootFolderActions.getInstance();
	}

	// ----- public methods -----
	public TypeDefinition getTypeDefinition() {
		return typeDefinition;
	}

	// ----- protected methods -----
	@Override
	protected boolean isRootFolder() {
		return true;
	}
}
