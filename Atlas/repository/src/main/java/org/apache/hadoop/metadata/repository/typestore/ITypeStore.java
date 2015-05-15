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

package org.apache.hadoop.metadata.repository.typestore;

import com.google.common.collect.ImmutableList;
import org.apache.hadoop.metadata.MetadataException;
import org.apache.hadoop.metadata.typesystem.TypesDef;
import org.apache.hadoop.metadata.typesystem.types.TypeSystem;

public interface ITypeStore {
    /**
     * Persist the entire type system - insert or update
     * @param typeSystem type system to persist
     * @throws StorageException
     */
    public void store(TypeSystem typeSystem) throws MetadataException;

    /**
     * Persist the given type in the type system - insert or update
     * @param typeSystem type system
     * @param types types to persist
     * @throws StorageException
     */
    public void store(TypeSystem typeSystem, ImmutableList<String> types) throws MetadataException;

    /**
     * Restore all type definitions
     * @return List of persisted type definitions
     * @throws org.apache.hadoop.metadata.MetadataException
     */
    public TypesDef restore() throws MetadataException;
}
