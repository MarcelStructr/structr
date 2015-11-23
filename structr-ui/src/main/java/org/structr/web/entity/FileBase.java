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
package org.structr.web.entity;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.chemistry.opencmis.commons.data.AllowableActions;
import org.apache.chemistry.opencmis.commons.enums.BaseTypeId;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.structr.cmis.CMISInfo;
import org.structr.cmis.info.CMISDocumentInfo;
import org.structr.cmis.info.CMISFolderInfo;
import org.structr.cmis.info.CMISItemInfo;
import org.structr.cmis.info.CMISPolicyInfo;
import org.structr.cmis.info.CMISRelationshipInfo;
import org.structr.cmis.info.CMISSecondaryInfo;
import org.structr.common.Permission;
import org.structr.common.PropertyView;
import org.structr.common.SecurityContext;
import org.structr.common.View;
import org.structr.common.error.ErrorBuffer;
import org.structr.common.error.FrameworkException;
import org.structr.core.Export;
import org.structr.core.GraphObject;
import org.structr.core.GraphObjectMap;
import org.structr.core.Services;
import org.structr.core.app.StructrApp;
import org.structr.core.entity.Principal;
import org.structr.core.graph.NodeInterface;
import org.structr.core.graph.NodeService;
import org.structr.core.graph.Tx;
import org.structr.core.property.BooleanProperty;
import org.structr.core.property.GenericProperty;
import org.structr.core.property.IntProperty;
import org.structr.core.property.LongProperty;
import org.structr.core.property.Property;
import org.structr.core.property.StringProperty;
import org.structr.files.cmis.config.StructrFileActions;
import org.structr.files.text.FulltextIndexingTask;
import org.structr.files.text.FulltextTokenizer;
import org.structr.schema.action.JavaScriptSource;
import org.structr.web.common.FileHelper;
import org.structr.web.common.ImageHelper;
import org.structr.web.entity.relation.Folders;

/**
 *
 *
 */
public class FileBase extends AbstractFile implements Linkable, JavaScriptSource, CMISInfo, CMISDocumentInfo {

	private static final Logger logger = Logger.getLogger(FileBase.class.getName());

	public static final Property<String> indexedContent          = new StringProperty("indexedContent").indexed(NodeService.NodeIndex.fulltext);
	public static final Property<String> extractedContent        = new StringProperty("extractedContent");
	public static final Property<String> indexedWords            = new StringProperty("indexedWords");
	public static final Property<String> contentType             = new StringProperty("contentType").indexedWhenEmpty();
	public static final Property<String> relativeFilePath        = new StringProperty("relativeFilePath").readOnly();
	public static final Property<Long> size                      = new LongProperty("size").indexed().readOnly();
	public static final Property<String> url                     = new StringProperty("url");
	public static final Property<Long> checksum                  = new LongProperty("checksum").indexed().unvalidated().readOnly();
	public static final Property<Integer> cacheForSeconds        = new IntProperty("cacheForSeconds").cmis();
	public static final Property<Integer> version                = new IntProperty("version").indexed().readOnly();
	public static final Property<Boolean> isFile                 = new BooleanProperty("isFile").defaultValue(true).readOnly();

	public static final View publicView = new View(FileBase.class, PropertyView.Public, type, name, contentType, size, url, owner, path, isFile);
	public static final View uiView = new View(FileBase.class, PropertyView.Ui, type, contentType, relativeFilePath, size, url, parent, checksum, version, cacheForSeconds, owner, isFile, hasParent, extractedContent, indexedWords);

	@Override
	public boolean onCreation(final SecurityContext securityContext, final ErrorBuffer errorBuffer) throws FrameworkException {

		if (super.onCreation(securityContext, errorBuffer)) {

			setProperty(hasParent, getProperty(parentId) != null);

			if ("true".equals(StructrApp.getConfigurationValue(Services.APPLICATION_FILESYSTEM_ENABLED, "false"))) {

				final Folder workingOrHomeDir = getCurrentWorkingDir();
				if (workingOrHomeDir != null && getProperty(AbstractFile.parent) == null) {

					setProperty(AbstractFile.parent, workingOrHomeDir);
				}
			}

			return true;
		}

		return false;
	}

	@Override
	public boolean onModification(final SecurityContext securityContext, final ErrorBuffer errorBuffer) throws FrameworkException {

		if (super.onModification(securityContext, errorBuffer)) {

			setProperty(hasParent, getProperty(parentId) != null);
			return true;
		}

		return false;
	}

	@Override
	public void onNodeCreation() {

		final String uuid = getUuid();
		final String filePath = getDirectoryPath(uuid) + "/" + uuid;

		try {
			unlockReadOnlyPropertiesOnce();
			setProperty(relativeFilePath, filePath);

		} catch (Throwable t) {

			logger.log(Level.WARNING, "Exception while trying to set relative file path {0}: {1}", new Object[]{filePath, t});

		}
	}

	@Override
	public void onNodeDeletion() {

		String filePath = null;
		try {
			final String path = getRelativeFilePath();

			if (path != null) {

				filePath = FileHelper.getFilePath(path);

				java.io.File toDelete = new java.io.File(filePath);

				if (toDelete.exists() && toDelete.isFile()) {

					toDelete.delete();
				}
			}

		} catch (Throwable t) {

			logger.log(Level.WARNING, "Exception while trying to delete file {0}: {1}", new Object[]{filePath, t});

		}

	}

	@Override
	public void afterCreation(SecurityContext securityContext) {

		try {

			final String filesPath = Services.getInstance().getConfigurationValue(Services.FILES_PATH);
			java.io.File fileOnDisk = new java.io.File(filesPath + "/" + getRelativeFilePath());

			if (fileOnDisk.exists()) {
				return;
			}

			fileOnDisk.getParentFile().mkdirs();

			try {

				fileOnDisk.createNewFile();

			} catch (IOException ex) {

				logger.log(Level.SEVERE, "Could not create file", ex);
				return;
			}

			unlockReadOnlyPropertiesOnce();
			setProperty(checksum, FileHelper.getChecksum(FileBase.this));

			unlockReadOnlyPropertiesOnce();
			setProperty(version, 0);

			long fileSize = FileHelper.getSize(FileBase.this);
			if (fileSize > 0) {
				unlockReadOnlyPropertiesOnce();
				setProperty(size, fileSize);
			}

		} catch (FrameworkException ex) {

			logger.log(Level.SEVERE, "Could not create file", ex);

		}

	}

	@Override
	public String getPath() {
		return FileHelper.getFolderPath(this);
	}

	@Export
	public GraphObject getSearchContext(final String searchTerm, final int contextLength) {

		final String text = getProperty(extractedContent);
		if (text != null) {

			final String[] searchParts         = searchTerm.split("[\\s]+");
			final GenericProperty contextKey   = new GenericProperty("context");
			final GraphObjectMap contextObject = new GraphObjectMap();
			final Set<String> contextValues    = new LinkedHashSet<>();

			for (final String searchString : searchParts) {

				final String lowerCaseSearchString = searchString.toLowerCase();
				final String lowerCaseText         = text.toLowerCase();
				final StringBuilder wordBuffer     = new StringBuilder();
				final StringBuilder lineBuffer     = new StringBuilder();
				final int textLength               = text.length();

				/*
				 * we take an average word length of 8 characters, multiply
				 * it by the desired prefix and suffix word count, add 20%
				 * and try to extract up to prefixLength words.
				 */

				// modify these parameters to tune prefix and suffix word extraction
				// loop variables
				int newlineCount = 0;
				int wordCount    = 0;	// wordCount starts at 1 because we include the matching word
				int pos          = -1;

				do {

					// find next occurrence
					pos = lowerCaseText.indexOf(lowerCaseSearchString, pos + 1);
					if (pos > 0) {

						lineBuffer.setLength(0);
						wordBuffer.setLength(0);

						wordCount    = 0;
						newlineCount = 0;

						// fetch context words before search hit
						for (int i=pos; i>=0; i--) {

							final char c = text.charAt(i);

							if (!Character.isAlphabetic(c) && !Character.isDigit(c) && !FulltextTokenizer.SpecialChars.contains(c)) {

								wordCount += flushWordBuffer(lineBuffer, wordBuffer, true);

								// store character in buffer
								wordBuffer.insert(0, c);

								if (c == '\n') {

									// increase newline count
									newlineCount++;

								} else {

									// reset newline count
									newlineCount = 0;
								}

								// paragraph boundary reached
								if (newlineCount > 1) {
									break;
								}

								// stop if we collected half of the desired word count
								if (wordCount > contextLength / 2) {
									break;
								}


							} else {

								// store character in buffer
								wordBuffer.insert(0, c);

								// reset newline count
								newlineCount = 0;
							}
						}

						wordCount += flushWordBuffer(lineBuffer, wordBuffer, true);

						wordBuffer.setLength(0);

						// fetch context words after search hit
						for (int i=pos+1; i<textLength; i++) {

							final char c = text.charAt(i);

							if (!Character.isAlphabetic(c) && !Character.isDigit(c) && !FulltextTokenizer.SpecialChars.contains(c)) {

								wordCount += flushWordBuffer(lineBuffer, wordBuffer, false);

								// store character in buffer
								wordBuffer.append(c);

								if (c == '\n') {

									// increase newline count
									newlineCount++;

								} else {

									// reset newline count
									newlineCount = 0;
								}

								// paragraph boundary reached
								if (newlineCount > 1) {
									break;
								}

								// stop if we collected enough words
								if (wordCount > contextLength) {
									break;
								}

							} else {

								// store character in buffer
								wordBuffer.append(c);

								// reset newline count
								newlineCount = 0;
							}
						}

						wordCount += flushWordBuffer(lineBuffer, wordBuffer, false);

						// replace single newlines with space
						contextValues.add(lineBuffer.toString().trim());
					}

				} while (pos >= 0);
			}

			contextObject.put(contextKey, contextValues);

			return contextObject;
		}

		return null;
	}

	private int flushWordBuffer(final StringBuilder lineBuffer, final StringBuilder wordBuffer, final boolean prepend) {

		int wordCount = 0;

		if (wordBuffer.length() > 0) {

			final String word = wordBuffer.toString().replaceAll("[\\n\\t]+", " ");
			if (StringUtils.isNotBlank(word)) {

				if (prepend) {

					lineBuffer.insert(0, word);

				} else {

					lineBuffer.append(word);
				}

				// increase word count
				wordCount = 1;
			}

			wordBuffer.setLength(0);
		}

		return wordCount;
	}

	public void notifyUploadCompletion() {
		StructrApp.getInstance(securityContext).processTasks(new FulltextIndexingTask(this));
	}

	public String getRelativeFilePath() {

		return getProperty(FileBase.relativeFilePath);

	}

	public String getUrl() {

		return getProperty(FileBase.url);

	}

	public String getContentType() {

		return getProperty(FileBase.contentType);

	}

	public Long getSize() {

		return getProperty(size);

	}

	public Long getChecksum() {

		return getProperty(checksum);

	}

	public String getFormattedSize() {

		return FileUtils.byteCountToDisplaySize(getSize());

	}

	public static String getDirectoryPath(final String uuid) {

		return (uuid != null)
			? uuid.substring(0, 1) + "/" + uuid.substring(1, 2) + "/" + uuid.substring(2, 3) + "/" + uuid.substring(3, 4)
			: null;

	}

	public void increaseVersion() throws FrameworkException {

		final Integer _version = getProperty(FileBase.version);

		unlockReadOnlyPropertiesOnce();
		if (_version == null) {

			setProperty(FileBase.version, 1);

		} else {

			setProperty(FileBase.version, _version + 1);
		}
	}

	public InputStream getInputStream() {

		final String path = getRelativeFilePath();

		if (path != null) {

			final String filePath = FileHelper.getFilePath(path);

			FileInputStream fis = null;
			try {

				java.io.File fileOnDisk = new java.io.File(filePath);

				// Return file input stream and save checksum and size after closing
				fis = new FileInputStream(fileOnDisk);

				return fis;

			} catch (FileNotFoundException e) {
				logger.log(Level.FINE, "File not found: {0}", new Object[]{path});

				if (fis != null) {

					try {

						fis.close();

					} catch (IOException ignore) {
					}

				}
			}
		}

		return null;
	}

	public OutputStream getOutputStream() {
		return getOutputStream(true);
	}

	public OutputStream getOutputStream(final boolean notifyIndexerAfterClosing) {

		final String path = getRelativeFilePath();
		if (path != null) {

			final String filePath = FileHelper.getFilePath(path);

			try {

				final java.io.File fileOnDisk = new java.io.File(filePath);
				fileOnDisk.getParentFile().mkdirs();

				// Return file output stream and save checksum and size after closing
				final FileOutputStream fos = new FileOutputStream(fileOnDisk) {

					private boolean closed = false;

					@Override
					public void close() throws IOException {

						if (closed) {
							return;
						}

						try (Tx tx = StructrApp.getInstance().tx()) {

							super.close();

							final String _contentType = FileHelper.getContentMimeType(FileBase.this);

							unlockReadOnlyPropertiesOnce();
							setProperty(checksum, FileHelper.getChecksum(FileBase.this));

							unlockReadOnlyPropertiesOnce();
							setProperty(size, FileHelper.getSize(FileBase.this));
							setProperty(contentType, _contentType);

							if (StringUtils.startsWith(_contentType, "image") || ImageHelper.isImageType(getProperty(name))) {
								setProperty(NodeInterface.type, Image.class.getSimpleName());
							}

							increaseVersion();

							if (notifyIndexerAfterClosing) {
								notifyUploadCompletion();
							}

							tx.success();

						} catch (Throwable ex) {

							logger.log(Level.SEVERE, "Could not determine or save checksum and size after closing file output stream", ex);

						}

						closed = true;
					}
				};

				return fos;

			} catch (FileNotFoundException e) {

				e.printStackTrace();
				logger.log(Level.SEVERE, "File not found: {0}", new Object[]{path});
			}

		}

		return null;

	}

	public java.io.File getFileOnDisk() {

		final String path = getRelativeFilePath();
		if (path != null) {

			return new java.io.File(FileHelper.getFilePath(path));
		}

		return null;
	}

	// ----- private methods -----
	/**
	 * Returns the Folder entity for the current working directory,
	 * or the user's home directory as a fallback.
	 * @return
	 */
	private Folder getCurrentWorkingDir() {

		final Principal _owner  = getProperty(owner);
		Folder workingOrHomeDir = null;

		if (_owner != null && _owner instanceof User) {

			workingOrHomeDir = _owner.getProperty(User.workingDirectory);
			if (workingOrHomeDir == null) {

				workingOrHomeDir = _owner.getProperty(User.homeDirectory);
			}
		}

		return workingOrHomeDir;
	}

	// ----- interface Syncable -----
	@Override
	public List<GraphObject> getSyncData() throws FrameworkException {

		final List<GraphObject> data = super.getSyncData();

		// nodes
		data.add(getProperty(parent));
		data.add(getIncomingRelationship(Folders.class));

		return data;
	}

	// ----- interface JavaScriptSource -----
	@Override
	public String getJavascriptLibraryCode() {

		try (final InputStream is = getInputStream()) {

			return IOUtils.toString(is);

		} catch (IOException ioex) {
			ioex.printStackTrace();
		}

		return null;
	}

	// ----- CMIS support -----
	@Override
	public CMISInfo getCMISInfo() {
		return this;
	}

	@Override
	public BaseTypeId getBaseTypeId() {
		return BaseTypeId.CMIS_DOCUMENT;
	}

	@Override
	public CMISFolderInfo getFolderInfo() {
		return null;
	}

	@Override
	public CMISDocumentInfo getDocumentInfo() {
		return this;
	}

	@Override
	public CMISItemInfo geItemInfo() {
		return null;
	}

	@Override
	public CMISRelationshipInfo getRelationshipInfo() {
		return null;
	}

	@Override
	public CMISPolicyInfo getPolicyInfo() {
		return null;
	}

	@Override
	public CMISSecondaryInfo getSecondaryInfo() {
		return null;
	}

	@Override
	public String getParentId() {
		return getProperty(FileBase.parentId);
	}

	@Override
	public AllowableActions getAllowableActions() {
		return new StructrFileActions(isImmutable(), getAccessControlEntries(), getSecurityContext().getUser(false).getName());
	}

	@Override
	public String getChangeToken() {

		// versioning not supported yet.
		return null;
	}

	@Override
	public boolean isImmutable() {

		final Principal _owner = getOwnerNode();
		if (_owner != null) {

			return !_owner.isGranted(Permission.write, securityContext);
		}

		return true;
	}
}
