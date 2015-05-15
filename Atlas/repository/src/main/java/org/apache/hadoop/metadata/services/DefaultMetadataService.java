/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.metadata.services;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import org.apache.hadoop.metadata.MetadataException;
import org.apache.hadoop.metadata.discovery.SearchIndexer;
import org.apache.hadoop.metadata.listener.EntityChangeListener;
import org.apache.hadoop.metadata.listener.TypesChangeListener;
import org.apache.hadoop.metadata.repository.MetadataRepository;
import org.apache.hadoop.metadata.repository.typestore.ITypeStore;
import org.apache.hadoop.metadata.typesystem.ITypedReferenceableInstance;
import org.apache.hadoop.metadata.typesystem.ITypedStruct;
import org.apache.hadoop.metadata.typesystem.Referenceable;
import org.apache.hadoop.metadata.typesystem.Struct;
import org.apache.hadoop.metadata.typesystem.TypesDef;
import org.apache.hadoop.metadata.typesystem.json.InstanceSerialization;
import org.apache.hadoop.metadata.typesystem.json.Serialization$;
import org.apache.hadoop.metadata.typesystem.json.TypesSerialization;
import org.apache.hadoop.metadata.typesystem.types.ClassType;
import org.apache.hadoop.metadata.typesystem.types.IDataType;
import org.apache.hadoop.metadata.typesystem.types.Multiplicity;
import org.apache.hadoop.metadata.typesystem.types.TraitType;
import org.apache.hadoop.metadata.typesystem.types.TypeSystem;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Simple wrapper over TypeSystem and MetadataRepository services with hooks
 * for listening to changes to the repository.
 */
@Singleton
public class DefaultMetadataService implements MetadataService {

    private static final Logger LOG =
            LoggerFactory.getLogger(DefaultMetadataService.class);

    private final Set<TypesChangeListener> typesChangeListeners = new LinkedHashSet<>();
    private final Set<EntityChangeListener> entityChangeListeners
            = new LinkedHashSet<>();

    private final TypeSystem typeSystem;
    private final MetadataRepository repository;
    private final ITypeStore typeStore;

    @Inject
    DefaultMetadataService(MetadataRepository repository,
                           SearchIndexer searchIndexer, ITypeStore typeStore) throws MetadataException {
        this.typeStore = typeStore;
        this.typeSystem = TypeSystem.getInstance();
        this.repository = repository;

        registerListener(searchIndexer);
    }

    /**
     * Creates a new type based on the type system to enable adding
     * entities (instances for types).
     *
     * @param typeDefinition definition as json
     * @return a unique id for this type
     */
    @Override
    public JSONObject createType(String typeDefinition) throws MetadataException {
        try {
            Preconditions.checkNotNull(typeDefinition, "type definition cannot be null");

            TypesDef typesDef = TypesSerialization.fromJson(typeDefinition);
            Map<String, IDataType> typesAdded = typeSystem.defineTypes(typesDef);
            //TODO how do we handle transaction - store failure??
            typeStore.store(typeSystem, ImmutableList.copyOf(typesAdded.keySet()));

            onTypesAddedToRepo(typesAdded);

            JSONObject response = new JSONObject();
            for (Map.Entry<String, IDataType> entry : typesAdded.entrySet()) {
                response.put(entry.getKey(), entry.getValue().getName());
            }

            return response;
        } catch (JSONException e) {
            LOG.error("Unable to create response for types={}", typeDefinition, e);
            throw new MetadataException("Unable to create response");
        }
    }

    /**
     * Return the definition for the given type.
     *
     * @param typeName name for this type, must be unique
     * @return type definition as JSON
     */
    @Override
    public String getTypeDefinition(String typeName) throws MetadataException {
        final IDataType dataType = typeSystem.getDataType(IDataType.class, typeName);
        return TypesSerialization.toJson(typeSystem, dataType.getName());
    }

    /**
     * Return the list of types in the repository.
     *
     * @return list of type names in the repository
     */
    @Override
    public List<String> getTypeNamesList() throws MetadataException {
        return typeSystem.getTypeNames();
    }

    /**
     * Return the list of trait type names in the type system.
     *
     * @return list of trait type names in the type system
     */
    @Override
    public List<String> getTraitNamesList() throws MetadataException {
        return typeSystem.getTraitsNames();
    }

    /**
     * Creates an entity, instance of the type.
     *
     * @param entityInstanceDefinition definition
     * @return guid
     */
    @Override
    public String createEntity(String entityInstanceDefinition) throws MetadataException {
        Preconditions.checkNotNull(entityInstanceDefinition,
                "entity instance definition cannot be null");

        ITypedReferenceableInstance entityTypedInstance =
                deserializeClassInstance(entityInstanceDefinition);

        final String guid = repository.createEntity(entityTypedInstance);

        onEntityAddedToRepo(entityTypedInstance);
        return guid;
    }

    private ITypedReferenceableInstance deserializeClassInstance(
            String entityInstanceDefinition) throws MetadataException {

        try {
            final Referenceable entityInstance = InstanceSerialization.fromJsonReferenceable(
                    entityInstanceDefinition, true);
            final String entityTypeName = entityInstance.getTypeName();
            Preconditions.checkNotNull(entityTypeName, "entity type cannot be null");

            ClassType entityType = typeSystem.getDataType(ClassType.class, entityTypeName);
            return entityType.convert(entityInstance, Multiplicity.REQUIRED);
        } catch (Exception e) {
            throw new MetadataException("Error deserializing class instance", e);
        }
    }

    /**
     * Return the definition for the given guid.
     *
     * @param guid guid
     * @return entity definition as JSON
     */
    @Override
    public String getEntityDefinition(String guid) throws MetadataException {
        Preconditions.checkNotNull(guid, "guid cannot be null");

        final ITypedReferenceableInstance instance = repository.getEntityDefinition(guid);
        return Serialization$.MODULE$.toJson(instance);
    }

    /**
     * Return the list of entity names for the given type in the repository.
     *
     * @param entityType type
     * @return list of entity names for the given type in the repository
     */
    @Override
    public List<String> getEntityList(String entityType) throws MetadataException {
        validateTypeExists(entityType);

        return repository.getEntityList(entityType);
    }

    @Override
    public void updateEntity(String guid, String property, String value) throws MetadataException {
        Preconditions.checkNotNull(guid, "guid cannot be null");
        Preconditions.checkNotNull(property, "property cannot be null");
        Preconditions.checkNotNull(value, "property value cannot be null");

        repository.updateEntity(guid, property, value);
    }

    private void validateTypeExists(String entityType) throws MetadataException {
        Preconditions.checkNotNull(entityType, "entity type cannot be null");

        // verify if the type exists
        if (!typeSystem.isRegistered(entityType)) {
            throw new MetadataException("type is not defined for : " + entityType);
        }
    }

    /**
     * Gets the list of trait names for a given entity represented by a guid.
     *
     * @param guid globally unique identifier for the entity
     * @return a list of trait names for the given entity guid
     * @throws MetadataException
     */
    @Override
    public List<String> getTraitNames(String guid) throws MetadataException {
        Preconditions.checkNotNull(guid, "entity GUID cannot be null");
        return repository.getTraitNames(guid);
    }

    /**
     * Adds a new trait to an existing entity represented by a guid.
     *
     * @param guid                    globally unique identifier for the entity
     * @param traitInstanceDefinition trait instance json that needs to be added to entity
     * @throws MetadataException
     */
    @Override
    public void addTrait(String guid,
                         String traitInstanceDefinition) throws MetadataException {
        Preconditions.checkNotNull(guid, "entity GUID cannot be null");
        Preconditions.checkNotNull(traitInstanceDefinition, "Trait instance cannot be null");

        ITypedStruct traitInstance = deserializeTraitInstance(traitInstanceDefinition);
        final String traitName = traitInstance.getTypeName();

        // ensure trait type is already registered with the TS
        Preconditions.checkArgument(typeSystem.isRegistered(traitName),
                "trait=%s should be defined in type system before it can be added", traitName);

        repository.addTrait(guid, traitInstance);

        onTraitAddedToEntity(guid, traitName);
    }

    private ITypedStruct deserializeTraitInstance(String traitInstanceDefinition)
        throws MetadataException {

        try {
            Struct traitInstance = InstanceSerialization.fromJsonStruct(
                    traitInstanceDefinition, true);
            final String entityTypeName = traitInstance.getTypeName();
            Preconditions.checkNotNull(entityTypeName, "entity type cannot be null");

            TraitType traitType = typeSystem.getDataType(TraitType.class, entityTypeName);
            return traitType.convert(
                    traitInstance, Multiplicity.REQUIRED);
        } catch (Exception e) {
            throw new MetadataException("Error deserializing trait instance", e);
        }
    }

    /**
     * Deletes a given trait from an existing entity represented by a guid.
     *
     * @param guid                 globally unique identifier for the entity
     * @param traitNameToBeDeleted name of the trait
     * @throws MetadataException
     */
    @Override
    public void deleteTrait(String guid,
                            String traitNameToBeDeleted) throws MetadataException {
        Preconditions.checkNotNull(guid, "entity GUID cannot be null");
        Preconditions.checkNotNull(traitNameToBeDeleted, "Trait name cannot be null");

        // ensure trait type is already registered with the TS
        Preconditions.checkArgument(typeSystem.isRegistered(traitNameToBeDeleted),
                "trait=%s should be defined in type system before it can be deleted",
                traitNameToBeDeleted);

        repository.deleteTrait(guid, traitNameToBeDeleted);

        onTraitDeletedFromEntity(guid, traitNameToBeDeleted);
    }

    private void onTypesAddedToRepo(Map<String, IDataType> typesAdded) throws MetadataException {
        for (TypesChangeListener listener : typesChangeListeners) {
            for (Map.Entry<String, IDataType> entry : typesAdded.entrySet()) {
                listener.onAdd(entry.getKey(), entry.getValue());
            }
        }
    }

    public void registerListener(TypesChangeListener listener) {
        typesChangeListeners.add(listener);
    }

    public void unregisterListener(TypesChangeListener listener) {
        typesChangeListeners.remove(listener);
    }

    private void onEntityAddedToRepo(ITypedReferenceableInstance typedInstance)
        throws MetadataException {

        for (EntityChangeListener listener : entityChangeListeners) {
            listener.onEntityAdded(typedInstance);
        }
    }

    private void onTraitAddedToEntity(String typeName,
                                      String traitName) throws MetadataException {
        for (EntityChangeListener listener : entityChangeListeners) {
            listener.onTraitAdded(typeName, traitName);
        }
    }

    private void onTraitDeletedFromEntity(String typeName,
                                          String traitName) throws MetadataException {
        for (EntityChangeListener listener : entityChangeListeners) {
            listener.onTraitDeleted(typeName, traitName);
        }
    }

    public void registerListener(EntityChangeListener listener) {
        entityChangeListeners.add(listener);
    }

    public void unregisterListener(EntityChangeListener listener) {
        entityChangeListeners.remove(listener);
    }
}
