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
package org.structr.files.cmis;

import java.util.List;
import org.apache.chemistry.opencmis.commons.data.Ace;
import org.apache.chemistry.opencmis.commons.data.Acl;
import org.apache.chemistry.opencmis.commons.data.ExtensionsData;
import org.apache.chemistry.opencmis.commons.enums.AclPropagation;
import org.apache.chemistry.opencmis.commons.exceptions.CmisInvalidArgumentException;
import org.apache.chemistry.opencmis.commons.exceptions.CmisObjectNotFoundException;
import org.apache.chemistry.opencmis.commons.spi.AclService;
import org.structr.cmis.wrapper.CMISObjectWrapper;
import org.structr.common.AccessControllable;
import org.structr.common.Permission;
import org.structr.common.Permissions;
import org.structr.common.SecurityContext;
import org.structr.common.error.FrameworkException;
import org.structr.core.GraphObject;
import org.structr.core.app.App;
import org.structr.core.app.StructrApp;
import org.structr.core.entity.AbstractNode;
import org.structr.core.entity.Principal;
import org.structr.core.graph.Tx;

/**
 *
 *
 */
public class CMISAclService extends AbstractStructrCmisService implements AclService {

	public CMISAclService(final StructrCMISService parentService, final SecurityContext securityContext) {
		super(parentService, securityContext);
	}

	@Override
	public Acl getAcl(final String repositoryId, final String objectId, final Boolean onlyBasicPermissions, final ExtensionsData extension) {

		final App app = StructrApp.getInstance(securityContext);

		try (final Tx tx = app.tx()) {

			final GraphObject node = app.get(objectId);
			if (node != null) {

				return CMISObjectWrapper.wrap(node, null, false, true);
			}

			tx.success();

		} catch (FrameworkException fex) {
			fex.printStackTrace();
		}

		throw new CmisObjectNotFoundException("Object with ID " + objectId + " does not exist");
	}

	/**
	 * Applies the given Acl exclusively, i.e. removes all other permissions / grants first.
	 *
	 * @param repositoryId
	 * @param objectId
	 * @param acl
	 * @param aclPropagation
	 *
	 * @return the resulting Acl
	 */
	public Acl applyAcl(final String repositoryId, final String objectId, final Acl acl, final AclPropagation aclPropagation) {

		final App app = StructrApp.getInstance(securityContext);

		try (final Tx tx = app.tx()) {

			final GraphObject obj = app.get(objectId);
			if (obj != null) {

				if (obj instanceof AbstractNode) {

					final AbstractNode node = (AbstractNode)obj;

					node.revokeAll();

					// process add ACL entries
					for (final Ace toAdd : acl.getAces()) {

						//if anonymous gets applied, gets thrown because of not known id?
						//here is the part to work on for making acl-editor to work properly

						applyAce(node, toAdd, false, aclPropagation);
					}

				} else {

					throw new CmisInvalidArgumentException("Object with ID " + objectId + " is not access controllable");
				}
			}

			tx.success();

			// return the wrapper which implements the Acl interface
			return CMISObjectWrapper.wrap(obj, null, false, true);

		} catch (FrameworkException fex) {
			fex.printStackTrace();
		}

		throw new CmisObjectNotFoundException("You don't have permission to change ACE for the object" + objectId);
	}

	@Override
	public Acl applyAcl(final String repositoryId, final String objectId, final Acl addAces, final Acl removeAces, final AclPropagation aclPropagation, final ExtensionsData extension) {

		final App app = StructrApp.getInstance(securityContext);

		try (final Tx tx = app.tx()) {

			final GraphObject obj = app.get(objectId);
			if (obj != null) {

				if (obj instanceof AccessControllable) {

					final AccessControllable node = (AccessControllable)obj;

					// process remove ACL entries first
					for (final Ace toRemove : removeAces.getAces()) {
						applyAce( node, toRemove, true, aclPropagation);
					}

					// process add ACL entries
					for (final Ace toAdd : addAces.getAces()) {
						applyAce(node, toAdd, false, aclPropagation);
					}

				} else {

					throw new CmisInvalidArgumentException("Object with ID " + objectId + " is not access controllable");
				}
			}

			tx.success();

			// return the wrapper which implements the Acl interface
			return CMISObjectWrapper.wrap(obj, null, false, true);

		} catch (FrameworkException fex) {
			fex.printStackTrace();
		}

		throw new CmisObjectNotFoundException("Object with ID " + objectId + " does not exist");
	}

	// ----- private methods -----
	private void applyAce(final AccessControllable node, final Ace toAdd, final boolean revoke, final AclPropagation aclPropagation) throws FrameworkException {

		final String principalId       = toAdd.getPrincipalId();
		final List<String> permissions = toAdd.getPermissions();

		if(null != principalId)

		//checks which flag set the user in cmis workbench
		//no further implementation yet
		/*boolean objectOnly;
		if(aclPropagation.value().equals(AclPropagation.REPOSITORYDETERMINED.value())) {
		objectOnly = false;
		} else if(aclPropagation.value().equals(AclPropagation.OBJECTONLY.value())) {
		objectOnly = true;
		} else {
		throw new CmisInvalidArgumentException("Only 'repository determined' and 'objectonly' available.");
		}*/
		//!

		switch (principalId) {

			case Principal.ANONYMOUS:
				//Only allow read-Permission here!!!
				//Don't accept other flags!!
				if(permissions.size() == 1 && permissions.get(0).equals("read")) {

					//set or unset here the visibleToPublicUsers-Flag for the object

				} else {

					throw new CmisInvalidArgumentException("'anonymous' and 'anyone' accept only the read permission.");
				}

				break;

			case Principal.ANYONE:

				int stub = 0;
				break;

			default:
				applyDefaultAce(principalId, permissions, revoke, node);
				break;

		}

	}

	private void applyDefaultAce(String principalId, List<String> permissions, boolean revoke, AccessControllable node) throws FrameworkException {

		final Principal principal = CMISObjectWrapper.translateUsernameToPrincipal(principalId);

			if (principal != null) {

				for (final String permissionString : permissions) {

					final Permission permission = Permissions.valueOf(permissionString);

					if (permission != null) {

						if (revoke) {

							node.revoke(permission, principal);

						} else {

							node.grant(permission, principal);
						}

					} else {

						throw new CmisInvalidArgumentException("Permission with ID " + permissionString + " does not exist");
					}
				}

			} else {

				throw new CmisObjectNotFoundException("Principal with ID " + principalId + " does not exist");
			}
	}
}
