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
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.chemistry.opencmis.commons.data.ExtensionsData;
import org.apache.chemistry.opencmis.commons.data.RepositoryInfo;
import org.apache.chemistry.opencmis.commons.definitions.Choice;
import org.apache.chemistry.opencmis.commons.definitions.MutableDocumentTypeDefinition;
import org.apache.chemistry.opencmis.commons.definitions.MutableFolderTypeDefinition;
import org.apache.chemistry.opencmis.commons.definitions.MutableItemTypeDefinition;
import org.apache.chemistry.opencmis.commons.definitions.MutablePolicyTypeDefinition;
import org.apache.chemistry.opencmis.commons.definitions.MutablePropertyDefinition;
import org.apache.chemistry.opencmis.commons.definitions.MutableRelationshipTypeDefinition;
import org.apache.chemistry.opencmis.commons.definitions.MutableSecondaryTypeDefinition;
import org.apache.chemistry.opencmis.commons.definitions.MutableTypeDefinition;
import org.apache.chemistry.opencmis.commons.definitions.PropertyDefinition;
import org.apache.chemistry.opencmis.commons.definitions.TypeDefinition;
import org.apache.chemistry.opencmis.commons.definitions.TypeDefinitionContainer;
import org.apache.chemistry.opencmis.commons.definitions.TypeDefinitionList;
import org.apache.chemistry.opencmis.commons.enums.BaseTypeId;
import static org.apache.chemistry.opencmis.commons.enums.BaseTypeId.CMIS_DOCUMENT;
import org.apache.chemistry.opencmis.commons.enums.Cardinality;
import org.apache.chemistry.opencmis.commons.enums.CmisVersion;
import org.apache.chemistry.opencmis.commons.enums.ContentStreamAllowed;
import org.apache.chemistry.opencmis.commons.enums.PropertyType;
import org.apache.chemistry.opencmis.commons.enums.Updatability;
import org.apache.chemistry.opencmis.commons.exceptions.CmisObjectNotFoundException;
import org.apache.chemistry.opencmis.commons.impl.dataobjects.TypeDefinitionContainerImpl;
import org.apache.chemistry.opencmis.commons.impl.dataobjects.TypeMutabilityImpl;
import org.apache.chemistry.opencmis.commons.spi.RepositoryService;
import org.apache.chemistry.opencmis.server.support.TypeDefinitionFactory;
import org.apache.commons.lang3.StringUtils;
import org.structr.api.graph.PropertyContainer;
import org.structr.cmis.CMISInfo;
import org.structr.common.PropertyView;
import org.structr.common.SecurityContext;
import org.structr.common.error.FrameworkException;
import org.structr.core.GraphObject;
import org.structr.core.app.App;
import org.structr.core.app.StructrApp;
import org.structr.core.entity.AbstractNode;
import org.structr.core.entity.SchemaNode;
import org.structr.core.graph.NodeAttribute;
import org.structr.core.graph.Tx;
import org.structr.core.graph.search.SearchCommand;
import org.structr.core.property.PropertyKey;
import org.structr.core.property.StringProperty;
import org.structr.dynamic.File;
import org.structr.files.cmis.config.StructrRepositoryInfo;
import org.structr.files.cmis.wrapper.CMISTypeDefinitionListWrapper;
import org.structr.schema.ConfigurationProvider;
import org.structr.web.entity.Folder;

/**
 *
 *
 */
public class CMISRepositoryService extends AbstractStructrCmisService implements RepositoryService {

	private static final Logger logger = Logger.getLogger(CMISRepositoryService.class.getName());

	private final RepositoryInfo repositoryInfo = new StructrRepositoryInfo();

	//enthält die information über das derzeitig geladene schema, ob es
	//vom user selbst gemacht ist oder hard-coded ist.
	//dieses attribut dient quasi als temporäre lösung, damit ich das attribut
	//nicht durch mehrere methoden per parameter durchgeben muss.
	//noch sehr unschöne realisierung, die methoden sind aber so sehr
	//miteinander verzweigt, dass ich es nicht an der stelle per datenbankaufruf
	//laden kann, wo ich es auch brauche.
	//private boolean isBuiltInType;

	public CMISRepositoryService(final StructrCMISService parentService, final SecurityContext securityContext) {
		super(parentService, securityContext);
	}

	@Override
	public List<RepositoryInfo> getRepositoryInfos(final ExtensionsData extension) {

		final List<RepositoryInfo> infoList = new LinkedList<>();

		infoList.add(repositoryInfo);

		return infoList;
	}

	@Override
	public RepositoryInfo getRepositoryInfo(String repositoryId, ExtensionsData extension) {

		if (repositoryId != null && repositoryId.equals(repositoryInfo.getId())) {
			return repositoryInfo;
		}

		throw new CmisObjectNotFoundException(repositoryId);
	}

	@Override
	public TypeDefinitionList getTypeChildren(String repositoryId, String typeId, Boolean includePropertyDefinitions, BigInteger maxItems, BigInteger skipCount, ExtensionsData extension) {

		// important: children are the direct children of a type, as opposed to the descendants

		final CMISTypeDefinitionListWrapper results = new CMISTypeDefinitionListWrapper(maxItems, skipCount);

		if (typeId != null) {

			final BaseTypeId baseTypeId = getBaseTypeId(typeId);
			if (baseTypeId != null) {

				results.addAll(getBaseTypeChildren(baseTypeId, includePropertyDefinitions));

			} else {

				final Class type = StructrApp.getConfiguration().getNodeEntityClass(typeId);
				if (type != null) {

					results.addAll(getTypeChildren(typeId, includePropertyDefinitions));

				} else {

					throw new CmisObjectNotFoundException("Type with ID " + typeId + " does not exist");
				}
			}

		} else {

			results.add(getDocumentTypeDefinition(BaseTypeId.CMIS_DOCUMENT.value(), includePropertyDefinitions, true));
			results.add(getFolderTypeDefinition(BaseTypeId.CMIS_FOLDER.value(), includePropertyDefinitions, true));

			//Have to check every single cmis:type, if custom or hard-coded?
			results.add(getItemTypeDefinition(BaseTypeId.CMIS_ITEM.value(), includePropertyDefinitions, true));

			results.add(getPolicyTypeDefinition(BaseTypeId.CMIS_POLICY.value(), includePropertyDefinitions, true));
			results.add(getRelationshipTypeDefinition(BaseTypeId.CMIS_RELATIONSHIP.value(), includePropertyDefinitions, true));
			results.add(getSecondaryTypeDefinition(BaseTypeId.CMIS_SECONDARY.value(), includePropertyDefinitions, true));
		}

		return results;
	}

	@Override
	public List<TypeDefinitionContainer> getTypeDescendants(String repositoryId, String typeId, BigInteger depth, Boolean includePropertyDefinitions, ExtensionsData extension) {

		// important: descendants are ALL children and children of children, i.e. the whole tree

		/*
		Id typeId: The typeId of an object-type speciﬁed in the repository.
		 - If speciﬁed, then the repository MUST return all of descendant types of the speciﬁed type.
		 - If not speciﬁed, then the Repository MUST return all types and MUST ignore the value of the depth parameter.
		*/

		final List<TypeDefinitionContainer> results = new LinkedList<>();

		if (typeId != null) {

			final BaseTypeId baseTypeId = getBaseTypeId(typeId);
			if (baseTypeId != null) {

				final TypeDefinition typeDefinition     = getTypeDefinition(repositoryId, typeId, extension);
				final TypeDefinitionContainer container = getTypeDefinitionContainer(typeDefinition, includePropertyDefinitions);

				results.add(container);

			} else {

				final Class type = StructrApp.getConfiguration().getNodeEntityClass(typeId);
				if (type != null) {

					final TypeDefinition typeDefinition = extendTypeDefinition(type, includePropertyDefinitions);
					if (typeDefinition != null) {

						results.add(getTypeDefinitionContainer(typeDefinition, includePropertyDefinitions));

					} else {

						throw new CmisObjectNotFoundException("Type with ID " + typeId + " does not exist");
					}

				} else {

					throw new CmisObjectNotFoundException("Type with ID " + typeId + " does not exist");
				}
			}

		} else {

			results.add(getTypeDefinitionContainer(getDocumentTypeDefinition(BaseTypeId.CMIS_DOCUMENT.value(), includePropertyDefinitions, true), includePropertyDefinitions));
			results.add(getTypeDefinitionContainer(getFolderTypeDefinition(BaseTypeId.CMIS_FOLDER.value(), includePropertyDefinitions, true), includePropertyDefinitions));
			results.add(getTypeDefinitionContainer(getItemTypeDefinition(BaseTypeId.CMIS_ITEM.value(), includePropertyDefinitions, true), includePropertyDefinitions));
			results.add(getTypeDefinitionContainer(getPolicyTypeDefinition(BaseTypeId.CMIS_POLICY.value(), includePropertyDefinitions, true), includePropertyDefinitions));
			results.add(getTypeDefinitionContainer(getRelationshipTypeDefinition(BaseTypeId.CMIS_RELATIONSHIP.value(), includePropertyDefinitions, true), includePropertyDefinitions));
			results.add(getTypeDefinitionContainer(getSecondaryTypeDefinition(BaseTypeId.CMIS_SECONDARY.value(), includePropertyDefinitions, true), includePropertyDefinitions));
		}

		return results;
	}

	@Override
	public TypeDefinition getTypeDefinition(final String repositoryId, final String typeId, final ExtensionsData extension) {

		switch (typeId) {

			case "cmis:document":
				return getDocumentTypeDefinition(typeId, true, true);

			case "cmis:folder":
				return getFolderTypeDefinition(typeId, true, true);

			case "cmis:item":
				return getItemTypeDefinition(typeId, true, true);

			case "cmis:policy":
				return getPolicyTypeDefinition(typeId, true, true);

			case "cmis:relationship":
				return getRelationshipTypeDefinition(typeId, true, true);

			case "cmis:secondary":
				return getSecondaryTypeDefinition(typeId, true, true);
		}

		final Class type = StructrApp.getConfiguration().getNodeEntityClass(typeId);
		if (type != null) {

			final TypeDefinition extendedTypeDefinition = extendTypeDefinition(type, true);
			if (extendedTypeDefinition != null) {

				return extendedTypeDefinition;
			}
		}

		throw new CmisObjectNotFoundException("Type with ID " + typeId + " does not exist");
	}

	/**
	 * FIRST version of creating types (for Structr only cmis:item):
	 * Simplify new type on its name and custom properties and create
	 * it then
	 * Example: "cmis:objectId" System Property and already default, no need
	 * to extract this information
	 * "age" has no "cmis:", therefore it is a new custom property
	 * specified by the user
	 * ONLY TESTED PROPERTYTYPE INTEGER AND STRING RIGHT NOW
	 * @param repositoryId
	 * @param type
	 * @param extension
	 * @return
	 */
	@Override
	public TypeDefinition createType(String repositoryId, TypeDefinition type, ExtensionsData extension) {

		final App app = StructrApp.getInstance(securityContext);

		//Name of the Schema/cmis:item itself
		String typeName = type.getLocalName();

		//Key is name of property, Value is the information of its type
		//for example: Age:Integer
		ArrayList<CustomPropertyContainer> customProperties = new ArrayList<>();
		extractCustomProperties(type, customProperties);

		try (final Tx tx = app.tx()) {

			SchemaNode node = app.create(SchemaNode.class,
				new NodeAttribute(SchemaNode.name, typeName)
			);

			//set all new custom properties
			for(CustomPropertyContainer p : customProperties) {

				node.setProperty(new StringProperty(p.propertyName), p.propertyType);
			}

			tx.success();

		} catch (FrameworkException fex) {

			fex.printStackTrace();
		}

		//return always cmis:item definition, because documenttypes,
		//foldertypes and so on can't be created in Structr
		//isBuiltInType = false;
		return getItemTypeDefinition(typeName, true, true);
	}

	@Override
	public TypeDefinition updateType(String repositoryId, TypeDefinition type, ExtensionsData extension) {

		/*
			Object-Type type: A type definition object with the property definitions that are to change.
			1) Repositories MUST ignore all fields in the type definition except for the type id and the list of properties.
			2) Properties that are not changing MUST NOT be included, including any inherited property definitions.
			3) For the properties that are being included, an entire copy of the property definition should be present
			4) Special note about choice values. There are only two types of changes permitted.
			– New choice added to the list.
			– Changing the displayname for an existing choice.
			5) For any choice that is being added or having its display name changed, both the displayName and
			value MUST be present.
		*/

		//Custom-Attributes extrahieren und überprüfen, ob diese schon vorhanden sind.

		final App app = StructrApp.getInstance(securityContext);

		//Name of the Schema/cmis:item itself
		String typeName = type.getLocalName();

		//Key is name of property, Value is the information of its type
		//for example: Age:Integer
		ArrayList<CustomPropertyContainer> customProperties = new ArrayList<>();
		extractCustomProperties(type, customProperties);

		try (final Tx tx = app.tx()) {


			SchemaNode node = app.nodeQuery(SchemaNode.class).and(AbstractNode.name, typeName).getFirst();
			PropertyContainer container = node.getPropertyContainer();

			//set all new custom properties
			for(CustomPropertyContainer p : customProperties) {

				//check if customproperty is already included
				//PropertyKey k;


				//node.getProperty(null);

			//	node.

				//if(container.hasProperty(p.propertyName)) {

				//	int check=0;
				//}

				//node.setProperty(new StringProperty(p.propertyName), p.propertyType);
			}

			tx.success();

		} catch (FrameworkException fex) {

			fex.printStackTrace();
		}


		return null;
	}

	@Override
	public void deleteType(String repositoryId, String typeId, ExtensionsData extension) {

		final App app = StructrApp.getInstance(securityContext);

		try (final Tx tx = app.tx()) {

			//Extracts SchemaNode with help of its name.
			//Schemas have always a unique name, so it should be fine.
			//Important and hard-coded Schemas like "User",
			//won't be deleted. However I am working on a better
			//solution.

			SchemaNode node = app.nodeQuery(SchemaNode.class).and(AbstractNode.name, typeId).getFirst();
			app.delete(node);

			tx.success();

		} catch (FrameworkException fex) {

			fex.printStackTrace();
		}
	}

	// ----- private methods -----
	private void extractCustomProperties (TypeDefinition type, ArrayList<CustomPropertyContainer> customProperties) {

		for(Map.Entry<String, PropertyDefinition<?>> entry : type.getPropertyDefinitions().entrySet()) {

			//if no "cmis:" at the beginning, the property is a
			//custom property
			if(!entry.getKey().startsWith("cmis:")) {

				String propertyName = entry.getKey();
				String propertyType = entry.getValue().getPropertyType().value();

				customProperties.add(new CustomPropertyContainer(propertyName, propertyType));
			}
		}
	}

	private MutableSecondaryTypeDefinition getSecondaryTypeDefinition(final String typeId, final boolean includePropertyDefinitions, final boolean baseType) {

		final TypeDefinitionFactory factory      = TypeDefinitionFactory.newInstance();
		final MutableSecondaryTypeDefinition def = factory.createSecondaryTypeDefinition(CmisVersion.CMIS_1_1, baseType ? null : BaseTypeId.CMIS_SECONDARY.value());

		def.setIsCreatable(false);

		initializeExtendedType(def, typeId);

		if (!includePropertyDefinitions) {
			def.removeAllPropertyDefinitions();
		}

		return def;
	}

	private MutableRelationshipTypeDefinition getRelationshipTypeDefinition(final String typeId, final boolean includePropertyDefinitions, final boolean baseType) {

		final TypeDefinitionFactory factory         = TypeDefinitionFactory.newInstance();
		final MutableRelationshipTypeDefinition def = factory.createRelationshipTypeDefinition(CmisVersion.CMIS_1_1, baseType ? null : BaseTypeId.CMIS_RELATIONSHIP.value());

		def.setIsCreatable(false);

		initializeExtendedType(def, typeId);

		if (!includePropertyDefinitions) {
			def.removeAllPropertyDefinitions();
		}

		return def;
	}

	private MutableItemTypeDefinition getItemTypeDefinition(final String typeId, final boolean includePropertyDefinitions, final boolean baseType) {

		final TypeDefinitionFactory factory = TypeDefinitionFactory.newInstance();
		final MutableItemTypeDefinition def = factory.createItemTypeDefinition(CmisVersion.CMIS_1_1, baseType ? null : BaseTypeId.CMIS_ITEM.value());

		def.setIsCreatable(false);

		if(typeId.equals(BaseTypeId.CMIS_ITEM.value())) {

			initializeExtendedType(def, typeId);
		} else {

			initializeExtendedItemType(def, typeId);
		}

		if (!includePropertyDefinitions) {
			def.removeAllPropertyDefinitions();
		}

		return def;
	}

	private void initializeExtendedItemType(final MutableTypeDefinition type, final String typeId) {

		type.setId(typeId);

		type.setLocalName(typeId);
		type.setQueryName(typeId);
		type.setDisplayName(typeId);
		type.setDescription(typeId);

		TypeMutabilityImpl t = new TypeMutabilityImpl();
		t.setCanCreate(true);
		t.setCanUpdate(true);

//		if(isBuiltInType) {
//
//			t.setCanDelete(false);
//		} else {
//
//			t.setCanDelete(true);
//		}

		t.setCanDelete(true);
		type.setTypeMutability(t);
	}

	private MutablePolicyTypeDefinition getPolicyTypeDefinition(final String typeId, final boolean includePropertyDefinitions, final boolean baseType) {

		final TypeDefinitionFactory factory   = TypeDefinitionFactory.newInstance();
		final MutablePolicyTypeDefinition def = factory.createPolicyTypeDefinition(CmisVersion.CMIS_1_1, baseType ? null : BaseTypeId.CMIS_POLICY.value());

		def.setIsCreatable(false);

		initializeExtendedType(def, typeId);

		if (!includePropertyDefinitions) {
			def.removeAllPropertyDefinitions();
		}

		return def;
	}

	private MutableFolderTypeDefinition getFolderTypeDefinition(final String typeId, final boolean includePropertyDefinitions, final boolean baseType) {

		final TypeDefinitionFactory factory   = TypeDefinitionFactory.newInstance();
		final MutableFolderTypeDefinition def = factory.createFolderTypeDefinition(CmisVersion.CMIS_1_1, baseType ? null : BaseTypeId.CMIS_FOLDER.value());

		initializeExtendedType(def, typeId);

		if (!includePropertyDefinitions) {
			def.removeAllPropertyDefinitions();
		}

		return def;
	}

	private MutableDocumentTypeDefinition getDocumentTypeDefinition(final String typeId, final boolean includePropertyDefinitions, final boolean baseType) {

		final TypeDefinitionFactory factory     = TypeDefinitionFactory.newInstance();
		final MutableDocumentTypeDefinition def = factory.createDocumentTypeDefinition(CmisVersion.CMIS_1_1, baseType ? null : BaseTypeId.CMIS_DOCUMENT.value());

		// content is required for Structr documents
		def.setContentStreamAllowed(ContentStreamAllowed.REQUIRED);

		initializeExtendedType(def, typeId);

		if (!includePropertyDefinitions) {

			def.removeAllPropertyDefinitions();
		}

		return def;
	}

	private List<TypeDefinition> getTypeChildren(final String typeId, final Boolean includePropertyDefinitions) {

		final Set<String> subtypes         = new LinkedHashSet<>(SearchCommand.getAllSubtypesAsStringSet(typeId));
		final ConfigurationProvider config = StructrApp.getConfiguration();
		final List<TypeDefinition> result  = new LinkedList<>();

		// subtypes set from Structr contains initial type as well..
		subtypes.remove(typeId);

		for (final String subtype : subtypes) {

			final Class subclass = config.getNodeEntityClass(subtype);
			if (subclass != null) {

				final TypeDefinition extendedTypeDefinition = extendTypeDefinition(subclass, includePropertyDefinitions);
				if (extendedTypeDefinition != null) {

					result.add(extendedTypeDefinition);
				}
			}
		}

		return result;
	}

	private List<TypeDefinition> getBaseTypeChildren(final BaseTypeId baseTypeId, final Boolean includePropertyDefinitions) {

		final ConfigurationProvider config = StructrApp.getConfiguration();
		final List<TypeDefinition> result  = new LinkedList<>();
		final App app                      = StructrApp.getInstance();

		// static definition of base type children, add new types here!
		switch (baseTypeId) {

			case CMIS_DOCUMENT:
				result.add(extendTypeDefinition(File.class, includePropertyDefinitions));
				break;

			case CMIS_FOLDER:
				result.add(extendTypeDefinition(Folder.class, includePropertyDefinitions));
				break;

			case CMIS_ITEM:

				try (final Tx tx = app.tx()) {

					for (final SchemaNode schemaNode : app.nodeQuery(SchemaNode.class).sort(AbstractNode.name).getAsList()) {

						final Class type = config.getNodeEntityClass(schemaNode.getClassName());

						if (type != null) {

							final CMISInfo info = getCMISInfo(type);
							if (info != null && baseTypeId.equals(info.getBaseTypeId())) {

//								isBuiltInType = schemaNode.getProperty(SchemaNode.isBuiltinType);

								final TypeDefinition extendedTypeDefinition = extendTypeDefinition(type, includePropertyDefinitions);
								if (extendedTypeDefinition != null) {

									result.add(extendedTypeDefinition);
								}
							}
						}
					}

					tx.success();

				} catch (final FrameworkException fex) {
					fex.printStackTrace();
				}
				break;
		}

		return result;
	}

	private TypeDefinitionContainer getTypeDefinitionContainer(final TypeDefinition typeDefinition, final Boolean includePropertyDefinitions) {

		final TypeDefinitionContainerImpl result = new TypeDefinitionContainerImpl();
		final List<TypeDefinitionContainer> list = new LinkedList<>();

		result.setTypeDefinition(typeDefinition);
		result.setChildren(list);

		final String typeId         = typeDefinition.getId();
		final BaseTypeId baseTypeId = getBaseTypeId(typeId);

		if (baseTypeId != null) {

			for (final TypeDefinition child : getBaseTypeChildren(baseTypeId, includePropertyDefinitions)) {

				list.add(getTypeDefinitionContainer(child, includePropertyDefinitions));
			}

		} else {

			for (final TypeDefinition child : getTypeChildren(typeDefinition.getId(), includePropertyDefinitions)) {

				list.add(getTypeDefinitionContainer(child, includePropertyDefinitions));
			}

		}

		return result;
	}

	private MutableTypeDefinition extendTypeDefinition(final Class<? extends GraphObject> type, final Boolean includePropertyDefinitions) {

		final String typeName                = type.getSimpleName();
		MutableTypeDefinition result         = null;

		try {

			// instantiate class to obtain runtime CMIS information
			final GraphObject obj = type.newInstance();
			if (obj != null) {

				final CMISInfo info = obj.getCMISInfo();
				if (info != null) {

					final BaseTypeId baseTypeId = info.getBaseTypeId();
					if (baseTypeId != null) {

						switch (baseTypeId) {

							case CMIS_DOCUMENT:
								result = getDocumentTypeDefinition(typeName, includePropertyDefinitions, false);
								break;

							case CMIS_FOLDER:
								result = getFolderTypeDefinition(typeName, includePropertyDefinitions, false);
								break;

							case CMIS_ITEM:
								result = getItemTypeDefinition(typeName, includePropertyDefinitions, false);
								break;

							case CMIS_POLICY:
								result = getPolicyTypeDefinition(typeName, includePropertyDefinitions, false);
								break;

							case CMIS_RELATIONSHIP:
								result = getRelationshipTypeDefinition(typeName, includePropertyDefinitions, false);
								break;

							case CMIS_SECONDARY:
								result = getSecondaryTypeDefinition(typeName, includePropertyDefinitions, false);
								break;
						}

						if (result != null) {

							// initialize..
							for (final PropertyKey key : StructrApp.getConfiguration().getPropertySet(type, PropertyView.All)) {

								final MutablePropertyDefinition property = createProperty(type, key);
								if (property != null) {

									result.addPropertyDefinition(property);
								}
							}
						}
					}
				}
			}

		} catch (final IllegalAccessException | InstantiationException iex) {
			iex.printStackTrace();
		}


		return result;
	}

	private void initializeExtendedType(final MutableTypeDefinition type, final String typeId) {

		type.setId(typeId);
		type.setLocalName(typeId);
		type.setQueryName(typeId);
		type.setDisplayName(typeId);
		type.setDescription(typeId);

		TypeMutabilityImpl t = new TypeMutabilityImpl();
		t.setCanCreate(false);
		t.setCanDelete(false);
		t.setCanUpdate(false);
		type.setTypeMutability(t);
	}

	private MutablePropertyDefinition createProperty(final Class type, final PropertyKey key) {

		// include all dynamic and CMIS-enabled keys in definition
		if (key.isDynamic() || key.isCMISProperty()) {

			// only include primitives here
			final TypeDefinitionFactory factory = TypeDefinitionFactory.newInstance();
			final PropertyType dataType         = key.getDataType();

			if (dataType != null) {

				final String propertyId         = key.jsonName();
				final String displayName        = propertyId;
				final String description        = StringUtils.capitalize(propertyId);
				final Class declaringClass      = key.getDeclaringClass();
				final boolean isInherited       = !type.getSimpleName().equals(declaringClass.getSimpleName());
				final Cardinality cardinality   = Cardinality.SINGLE;
				final Updatability updatability = Updatability.READWRITE;
				final boolean required          = key.isNotNull();
				final boolean queryable         = key.isIndexed();
				final boolean orderable         = key.isIndexed();

				final MutablePropertyDefinition property = factory.createPropertyDefinition(
					propertyId,
					displayName,
					description,
					dataType,
					cardinality,
					updatability,
					isInherited,
					required,
					queryable,
					orderable
				);

				// add enum choices if present
				final Class valueType = key.valueType();
				if (valueType != null && valueType.isEnum()) {

					final List<Choice> choices = new LinkedList<>();

					for (final Object option : valueType.getEnumConstants()) {

						final String optionName = option.toString();

						choices.add(factory.createChoice(optionName, optionName));
					}

					property.setIsOpenChoice(false);
					property.setChoices(choices);
				}

				return property;
			}
		}

		return null;
	}

	//------------------------

	/**
	 * Simple private class, to buffer all the necessary information for
	 * custom property in createType(). Can be easily expanded in the future.
	 */
	private class CustomPropertyContainer {

		public String propertyName;
		public String propertyType;

		CustomPropertyContainer(String name, String type) {

			propertyName = "_" + name;
			propertyType = type.substring(0,1).toUpperCase() + type.substring(1);
		}
	}
}