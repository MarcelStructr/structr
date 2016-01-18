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
package org.structr.files.cmis;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Logger;
import org.apache.chemistry.opencmis.commons.data.ExtensionsData;
import org.apache.chemistry.opencmis.commons.data.ObjectData;
import org.apache.chemistry.opencmis.commons.data.ObjectInFolderContainer;
import org.apache.chemistry.opencmis.commons.data.ObjectInFolderList;
import org.apache.chemistry.opencmis.commons.data.ObjectList;
import org.apache.chemistry.opencmis.commons.data.ObjectParentData;
import org.apache.chemistry.opencmis.commons.enums.IncludeRelationships;
import org.apache.chemistry.opencmis.commons.exceptions.CmisObjectNotFoundException;
import org.apache.chemistry.opencmis.commons.impl.dataobjects.ObjectInFolderContainerImpl;
import org.apache.chemistry.opencmis.commons.impl.dataobjects.ObjectParentDataImpl;
import org.apache.chemistry.opencmis.commons.spi.NavigationService;
import org.structr.cmis.CMISInfo;
import org.structr.cmis.wrapper.CMISObjectWrapper;
import org.structr.common.GraphObjectComparator;
import org.structr.common.SecurityContext;
import org.structr.common.error.FrameworkException;
import org.structr.core.GraphObject;
import org.structr.core.app.App;
import org.structr.core.app.Query;
import org.structr.core.app.StructrApp;
import org.structr.core.entity.AbstractNode;
import org.structr.core.graph.Tx;
import org.structr.files.cmis.repository.CMISRootFolder;
import org.structr.files.cmis.wrapper.CMISObjectInFolderWrapper;
import org.structr.files.cmis.wrapper.CMISObjectListWrapper;
import org.structr.web.entity.AbstractFile;
import org.structr.web.entity.FileBase;
import org.structr.web.entity.Folder;
import org.structr.web.entity.Image;

/**
 *
 *
 */
public class CMISNavigationService extends AbstractStructrCmisService implements NavigationService {

	private static final Logger logger = Logger.getLogger(CMISNavigationService.class.getName());
	private final StructrCMISService parentService;


	public CMISNavigationService(final StructrCMISService parentService, final SecurityContext securityContext) {
		super(parentService, securityContext);
		this.parentService = parentService;
	}

	@Override
	public ObjectInFolderList getChildren(final String repositoryId, final String folderId, final String propertyFilter, final String orderBy, final Boolean includeAllowableActions, final IncludeRelationships includeRelationships, final String renditionFilter, final Boolean includePathSegment, final BigInteger maxItems, final BigInteger skipCount, final ExtensionsData extension) {

		final App app = StructrApp.getInstance(securityContext);
		final CMISObjectInFolderWrapper wrapper = new CMISObjectInFolderWrapper(propertyFilter, includeAllowableActions, maxItems, skipCount);

		try (final Tx tx = app.tx()) {

			wrapper.wrap(getChildrenQuery(app, folderId).getAsList());

			wrapper.sort(orderBy);

			tx.success();

		} catch (final FrameworkException fex) {
			fex.printStackTrace();
		}

		return wrapper;
	}

	@Override
	public List<ObjectInFolderContainer> getDescendants(String repositoryId, String folderId, BigInteger depth, String filter, Boolean includeAllowableActions, IncludeRelationships includeRelationships, String renditionFilter, Boolean includePathSegment, ExtensionsData extension) {

		final List<ObjectInFolderContainer> result = new LinkedList<>();
		final App app                              = StructrApp.getInstance(securityContext);

		try (final Tx tx = app.tx()) {

			int maxDepth = Integer.MAX_VALUE;

			if (depth != null && depth.intValue() >= 0) {
				maxDepth = depth.intValue();
			}

			for (final AbstractFile child : getChildrenQuery(app, folderId).getAsList()) {

				recursivelyCollectDescendants(result, child, maxDepth, 1, includeAllowableActions);
			}


			tx.success();

		} catch (final FrameworkException fex) {
			fex.printStackTrace();
		}

		return result;
	}

	@Override
	public List<ObjectInFolderContainer> getFolderTree(final String repositoryId, final String folderId, final BigInteger depth, final String filter, final Boolean includeAllowableActions, final IncludeRelationships includeRelationships, final String renditionFilter, final Boolean includePathSegment, final ExtensionsData extension) {

		final List<ObjectInFolderContainer> result = new LinkedList<>();
		final App app                              = StructrApp.getInstance(securityContext);

		try (final Tx tx = app.tx()) {

			int maxDepth = Integer.MAX_VALUE;

			if (depth != null && depth.intValue() >= 0) {
				maxDepth = depth.intValue();
			}

			if (CMISInfo.ROOT_FOLDER_ID.equals(folderId)) {

				for (final Folder folder : app.nodeQuery(Folder.class).and(Folder.parent, null).sort(AbstractNode.name).getAsList()) {

					recursivelyCollectFolderTree(result, folder, maxDepth, 1, includeAllowableActions);
				}

			} else {

				final Folder folder = app.get(Folder.class, folderId);
				if (folder != null) {

					final List<Folder> children = folder.getProperty(Folder.folders);
					Collections.sort(children, new GraphObjectComparator(AbstractNode.name, false));

					for (final Folder child : children) {

						recursivelyCollectFolderTree(result, child, maxDepth, 1, includeAllowableActions);
					}

				} else {

					throw new CmisObjectNotFoundException("Folder with ID " + folderId + " does not exist");
				}
			}

			tx.success();

		} catch (final FrameworkException fex) {
			fex.printStackTrace();
		}

		return result;
	}

	@Override
	public List<ObjectParentData> getObjectParents(String repositoryId, String objectId, String propertyFilter, Boolean includeAllowableActions, IncludeRelationships includeRelationships, String renditionFilter, Boolean includeRelativePathSegment, ExtensionsData extension) {

		final App app  = StructrApp.getInstance(securityContext);

		try (final Tx tx = app.tx()) {

			final List<ObjectParentData> data = new LinkedList<>();
			final GraphObject graphObject     = app.get(objectId);

			if (graphObject instanceof AbstractFile) {

				final Folder parent             = ((AbstractFile)graphObject).getProperty(AbstractFile.parent);
				final ObjectData element        = parent != null ? CMISObjectWrapper.wrap(parent, propertyFilter, includeAllowableActions, false) : new CMISRootFolder(propertyFilter, includeAllowableActions, false);
				final ObjectParentDataImpl impl = new ObjectParentDataImpl(element);

				if(includeRelativePathSegment) {

					impl.setRelativePathSegment(graphObject.getProperty(AbstractNode.name));
				}

				data.add(impl);
			}

			tx.success();

			return data;

		} catch (Throwable t) {
			t.printStackTrace();
		}

		return null;
	}

	@Override
	public ObjectData getFolderParent(final String repositoryId, final String folderId, final String propertyFilter, final ExtensionsData extension) {

		final App app     = StructrApp.getInstance(securityContext);
		ObjectData result = null;

		try (final Tx tx = app.tx()) {

			final GraphObject graphObject = app.get(folderId);

			if (graphObject != null && graphObject instanceof AbstractFile) {

				final Folder parent = ((AbstractFile)graphObject).getProperty(AbstractFile.parent);
				if (parent != null) {

					result = CMISObjectWrapper.wrap(parent, propertyFilter, false, false);
				}
			}

			tx.success();

		} catch (Throwable t) {
			t.printStackTrace();
		}

		if (result != null) {
			return result;
		}

		return null;
	}

	@Override
	public ObjectList getCheckedOutDocs(String repositoryId, String folderId, String filter, String orderBy, Boolean includeAllowableActions, IncludeRelationships includeRelationships, String renditionFilter, BigInteger maxItems, BigInteger skipCount, ExtensionsData extension) {

		final App app = StructrApp.getInstance(securityContext);
		CMISObjectListWrapper checkedOutDocs = new CMISObjectListWrapper();

		boolean hasMaxItems = false;
		if(maxItems != null && maxItems.intValue() >= 0) {

			hasMaxItems = true;
		}

		try (final Tx tx = app.tx()) {

			List<FileBase> children = null;

			//collect documents from specific folder
			if(folderId != null) {

				if (CMISInfo.ROOT_FOLDER_ID.equals(folderId)) {

					children = app.nodeQuery(FileBase.class)
							.sort(AbstractFile.name)
							.and(AbstractFile.hasParent, false)
							.not().and(Image.isThumbnail, true)
							.getAsList();

				} else {

					final Folder folder = app.get(Folder.class, folderId);

					if (folder != null) {

						children = app.nodeQuery(FileBase.class)
								.and(AbstractFile.parent, folder)
								.sort(AbstractFile.name)
								.getAsList();
					}
				}


				for (final FileBase file : children) {

					String objectId = file.getProperty(FileBase.id);
					checkedOutDocs.add(parentService.getObject(repositoryId, objectId, filter, includeAllowableActions, includeRelationships, renditionFilter, null, null, extension));

					if(hasMaxItems) {

						if(checkedOutDocs.getSize() >= maxItems.intValue()) {

							//accessed limit, exit function
							return checkedOutDocs;
						}
					}
				}

			} else {

				//collect ALL docs beginning from root

				List<Folder> folderChildren = new ArrayList();

				List<AbstractFile> childrenRoot = app.nodeQuery(AbstractFile.class).sort(AbstractFile.name)
									.and(AbstractFile.hasParent, false)
									.not().and(Image.isThumbnail, true)
									.getAsList();

				for(AbstractFile child : childrenRoot) {

					if(child instanceof Folder) {

						folderChildren.add((Folder)child);

					} else if (child instanceof FileBase) {

						String objectId = child.getProperty(FileBase.id);
						checkedOutDocs.add(parentService.getObject(repositoryId, objectId, filter, includeAllowableActions, includeRelationships, renditionFilter, null, null, extension));

						if(hasMaxItems) {

							if(checkedOutDocs.getSize() >= maxItems.intValue()) {

								//accessed limit, exit function
								checkedOutDocs.sort(orderBy);
								return checkedOutDocs;
							}
						}
					}
				}

				Collections.sort(folderChildren, new GraphObjectComparator(AbstractNode.name, false));

				for (final Folder child : folderChildren) {

					recursivelyCollectDocuments(checkedOutDocs, hasMaxItems, child, repositoryId, filter, includeAllowableActions, includeRelationships, renditionFilter, maxItems, skipCount, extension);
				}
			}

			tx.success();

			//sort list before returning
			checkedOutDocs.sort(orderBy);

			return checkedOutDocs;

		} catch (final FrameworkException fex) {
			fex.printStackTrace();
		}

		return null;
	}


	// ----- private methods -----
	private void recursivelyCollectDocuments(CMISObjectListWrapper listWrapper, final boolean hasMaxItems, final Folder child, String repositoryId, String filter, Boolean includeAllowableActions, IncludeRelationships includeRelationships, String renditionFilter, BigInteger maxItems, BigInteger skipCount, ExtensionsData extension) throws FrameworkException {

		if(hasMaxItems) {
			if(listWrapper.getSize() >= maxItems.intValue()) {

				return;
			}
		}

		final List<AbstractFile> children = child.getProperty(FileBase.children);
		List<Folder> childrenFolder = new ArrayList();

		for(AbstractFile abstractFile : children) {

			if(abstractFile instanceof FileBase) {

				FileBase file = (FileBase) abstractFile;
				listWrapper.add(parentService.getObject(repositoryId, file.getProperty(FileBase.id), filter, includeAllowableActions, includeRelationships, renditionFilter, null, null, extension));

			} else if(abstractFile instanceof Folder) {

				childrenFolder.add((Folder)abstractFile);
			}
		}

		//sort children
		Collections.sort(childrenFolder, new GraphObjectComparator(AbstractNode.name, false));

		// descend into children
		for (final Folder folderChild: childrenFolder) {
			recursivelyCollectDocuments(listWrapper, hasMaxItems, folderChild, repositoryId, filter, includeAllowableActions, includeRelationships, renditionFilter, maxItems, skipCount, extension);
		}
	}

	private void recursivelyCollectFolderTree(final List<ObjectInFolderContainer> list, final Folder child, final int maxDepth, final int depth, final Boolean includeAllowableActions) throws FrameworkException {

		if (depth > maxDepth) {
			return;
		}

		final CMISObjectInFolderWrapper wrapper                = new CMISObjectInFolderWrapper(includeAllowableActions);
		final ObjectInFolderContainerImpl impl                 = new ObjectInFolderContainerImpl();
		final List<ObjectInFolderContainer> childContainerList = new LinkedList<>();
		final String pathSegment                               = child.getName();

		impl.setObject(wrapper.wrapObjectData(wrapper.wrapGraphObject(child), pathSegment));
		impl.setChildren(childContainerList);

		// add wrapped object to current list
		list.add(impl);

		// fetch and sort children
		final List<Folder> children = child.getProperty(Folder.folders);
		Collections.sort(children, new GraphObjectComparator(AbstractNode.name, false));

		// descend into children
		for (final Folder folderChild: children) {
			recursivelyCollectFolderTree(childContainerList, folderChild, maxDepth, depth+1, includeAllowableActions);
		}

	}

	private void recursivelyCollectDescendants(final List<ObjectInFolderContainer> list, final AbstractFile child, final int maxDepth, final int depth, final Boolean includeAllowableActions) throws FrameworkException {

		if (depth > maxDepth) {
			return;
		}

		final CMISObjectInFolderWrapper wrapper                = new CMISObjectInFolderWrapper(includeAllowableActions);
		final ObjectInFolderContainerImpl impl                 = new ObjectInFolderContainerImpl();
		final List<ObjectInFolderContainer> childContainerList = new LinkedList<>();
		final String pathSegment                               = child.getName();

		impl.setObject(wrapper.wrapObjectData(wrapper.wrapGraphObject(child), pathSegment));
		impl.setChildren(childContainerList);

		// add wrapped object to current list
		list.add(impl);

		if (child.getProperty(AbstractNode.type).equals("Folder")) {

			final App app     = StructrApp.getInstance();

			// descend into children
			for (final AbstractFile folderChild : app.nodeQuery(AbstractFile.class).sort(AbstractNode.name).and(AbstractFile.parent, (Folder)child).and(Image.isThumbnail, false).getAsList()) {
				recursivelyCollectDescendants(childContainerList, folderChild, maxDepth, depth+1, includeAllowableActions);
			}
		}
	}


	public Query<AbstractFile> getChildrenQuery (final App app, final String folderId) throws FrameworkException {

		final Query<AbstractFile> query = app.nodeQuery(AbstractFile.class).sort(AbstractNode.name);

		if (CMISInfo.ROOT_FOLDER_ID.equals(folderId)) {

			query.and(AbstractFile.hasParent, false).not().and(Image.isThumbnail, true);

		} else {

			final Folder folder = app.get(Folder.class, folderId);

			if (folder != null) {

				query.and(AbstractFile.parent, folder).and(Image.isThumbnail, false);

			} else {

				throw new CmisObjectNotFoundException("Folder with ID " + folderId + " does not exist");

			}

		}

		return query;
	}
}
