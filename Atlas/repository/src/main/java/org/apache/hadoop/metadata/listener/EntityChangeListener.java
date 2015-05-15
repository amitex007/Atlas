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

package org.apache.hadoop.metadata.listener;

import org.apache.hadoop.metadata.MetadataException;
import org.apache.hadoop.metadata.typesystem.ITypedReferenceableInstance;

/**
 * Entity (a Typed instance) change notification listener.
 */
public interface EntityChangeListener {

    /**
     * This is upon adding a new typed instance to the repository.
     *
     * @param typedInstance a typed instance
     * @throws org.apache.hadoop.metadata.MetadataException
     */
    void onEntityAdded(ITypedReferenceableInstance typedInstance) throws MetadataException;

    /**
     * This is upon adding a new trait to a typed instance.
     *
     * @param guid          globally unique identifier for the entity
     * @param traitName     trait name for the instance that needs to be added to entity
     * @throws org.apache.hadoop.metadata.MetadataException
     */
    void onTraitAdded(String guid, String traitName) throws MetadataException;

    /**
     * This is upon deleting a trait from a typed instance.
     *
     * @param guid          globally unique identifier for the entity
     * @param traitName     trait name for the instance that needs to be deleted from entity
     * @throws org.apache.hadoop.metadata.MetadataException
     */
    void onTraitDeleted(String guid, String traitName) throws MetadataException;
}
