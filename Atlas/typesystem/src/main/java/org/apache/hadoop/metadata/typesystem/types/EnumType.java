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

package org.apache.hadoop.metadata.typesystem.types;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.apache.hadoop.metadata.MetadataException;

public class EnumType extends AbstractDataType<EnumValue> {

    public final TypeSystem typeSystem;
    public final String name;
    public final ImmutableMap<String, EnumValue> valueMap;
    public final ImmutableMap<Integer, EnumValue> ordinalMap;

    protected EnumType(TypeSystem typeSystem, String name, EnumValue... values) {
        this.typeSystem = typeSystem;
        this.name = name;
        ImmutableMap.Builder<String, EnumValue> b1 = new ImmutableMap.Builder();
        ImmutableMap.Builder<Integer, EnumValue> b2 = new ImmutableMap.Builder();
        for (EnumValue v : values) {
            b1.put(v.value, v);
            b2.put(v.ordinal, v);
        }
        valueMap = b1.build();
        ordinalMap = b2.build();
    }


    @Override
    public String getName() {
        return name;
    }

    @Override
    public EnumValue convert(Object val, Multiplicity m) throws MetadataException {
        if (val != null) {
            EnumValue e = null;
            if (val instanceof EnumValue) {
                e = valueMap.get(((EnumValue)val).value);
            } else if ( val instanceof Integer) {
                e = ordinalMap.get(val);
            } else if ( val instanceof  String) {
                e = valueMap.get(val);
            } else if ( val instanceof Number ) {
                e = ordinalMap.get(((Number)val).intValue());
            }

            if (e == null) {
                throw new ValueConversionException(this, val);
            }
            return e;
        }
        return convertNull(m);
    }

    @Override
    public DataTypes.TypeCategory getTypeCategory() {
        return DataTypes.TypeCategory.ENUM;
    }

    public EnumValue fromOrdinal(int o) {
        return ordinalMap.get(o);
    }

    public EnumValue fromValue(String val) {
        return valueMap.get(val.trim());
    }

    public ImmutableCollection<EnumValue> values() {
        return valueMap.values();
    }
}
