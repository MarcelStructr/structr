/**
 * Copyright (C) 2010-2016 Structr GmbH
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
package org.structr.files.cmis.config;

import java.util.List;
import java.util.Set;
import org.apache.chemistry.opencmis.commons.data.Ace;
import org.apache.chemistry.opencmis.commons.enums.Action;

/**
 *
 *
 */

public class StructrFolderActions extends AbstractStructrActions {

	public StructrFolderActions(List<Ace> aces, String username, boolean isAdmin, boolean isOwner) {

	    super(aces, username, isAdmin, isOwner, false);
	}

	@Override
	protected void setReadPermissions() {

		actions.add(Action.CAN_GET_FOLDER_TREE);
		actions.add(Action.CAN_GET_PROPERTIES);
		actions.add(Action.CAN_GET_OBJECT_RELATIONSHIPS);
		actions.add(Action.CAN_GET_OBJECT_PARENTS);
		actions.add(Action.CAN_GET_FOLDER_PARENT);
		actions.add(Action.CAN_GET_DESCENDANTS);
		actions.add(Action.CAN_GET_CHILDREN);

		//authenticated users can create documents and folders
		//with only readPermission. Anonymous cannot.
		if(!isAnonymous) {
			actions.add(Action.CAN_CREATE_DOCUMENT);
			actions.add(Action.CAN_CREATE_FOLDER);
		}
	}

	@Override
	protected void setWritePermissions() {

		actions.add(Action.CAN_MOVE_OBJECT);
		actions.add(Action.CAN_UPDATE_PROPERTIES);
	}

	@Override
	protected void setDeletePermissions() {

		actions.add(Action.CAN_DELETE_OBJECT);
		actions.add(Action.CAN_DELETE_TREE);
	}

	@Override
	protected void setAccessControlPermissions() {

		actions.add(Action.CAN_GET_ACL);
		actions.add(Action.CAN_APPLY_ACL);
	}

	@Override
	public Set<Action> getAllowableActions() {

		return actions;
	}
}

/*
all important allowable actions summarized:

	    	actions.add(Action.CAN_DELETE_OBJECT);
		actions.add(Action.CAN_UPDATE_PROPERTIES);
		actions.add(Action.CAN_GET_FOLDER_TREE);
		actions.add(Action.CAN_GET_PROPERTIES);
		actions.add(Action.CAN_GET_OBJECT_RELATIONSHIPS);
		actions.add(Action.CAN_GET_OBJECT_PARENTS);
		actions.add(Action.CAN_GET_FOLDER_PARENT);
		actions.add(Action.CAN_GET_DESCENDANTS);
		actions.add(Action.CAN_MOVE_OBJECT);
//		actions.add(Action.CAN_DELETE_CONTENT_STREAM);
//		actions.add(Action.CAN_CHECK_OUT);
//		actions.add(Action.CAN_CANCEL_CHECK_OUT);
//		actions.add(Action.CAN_CHECK_IN);
//		actions.add(Action.CAN_SET_CONTENT_STREAM);
//		actions.add(Action.CAN_GET_ALL_VERSIONS);
//		actions.add(Action.CAN_ADD_OBJECT_TO_FOLDER);
//		actions.add(Action.CAN_REMOVE_OBJECT_FROM_FOLDER);
//		actions.add(Action.CAN_GET_CONTENT_STREAM);
		//actions.add(Action.CAN_APPLY_POLICY);
		//actions.add(Action.CAN_GET_APPLIED_POLICIES);
		//actions.add(Action.CAN_REMOVE_POLICY);
		actions.add(Action.CAN_GET_CHILDREN);
		actions.add(Action.CAN_CREATE_DOCUMENT);
		actions.add(Action.CAN_CREATE_FOLDER);
		//actions.add(Action.CAN_CREATE_RELATIONSHIP);
		//actions.add(Action.CAN_CREATE_ITEM);
		actions.add(Action.CAN_DELETE_TREE);
		//actions.add(Action.CAN_GET_RENDITIONS);
		actions.add(Action.CAN_GET_ACL);
		actions.add(Action.CAN_APPLY_ACL);
*/
