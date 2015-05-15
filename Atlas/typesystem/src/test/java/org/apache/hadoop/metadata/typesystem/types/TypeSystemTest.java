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

import com.google.common.collect.ImmutableList;
import org.apache.hadoop.metadata.typesystem.types.utils.TypesUtil;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import scala.actors.threadpool.Arrays;

import java.util.Collections;
import java.util.List;

public class TypeSystemTest extends BaseTest {

    @BeforeClass
    public void setUp() throws Exception {
        super.setup();
    }

    @AfterMethod
    public void tearDown() throws Exception {
        getTypeSystem().reset();
    }

    @Test
    public void testGetTypeNames() throws Exception {
        getTypeSystem().defineEnumType("enum_test",
                new EnumValue("0", 0),
                new EnumValue("1", 1),
                new EnumValue("2", 2),
                new EnumValue("3", 3));
        Assert.assertTrue(getTypeSystem().getTypeNames().contains("enum_test"));
    }

    @Test
    public void testIsRegistered() throws Exception {
        getTypeSystem().defineEnumType("enum_test",
                new EnumValue("0", 0),
                new EnumValue("1", 1),
                new EnumValue("2", 2),
                new EnumValue("3", 3));
        Assert.assertTrue(getTypeSystem().isRegistered("enum_test"));
    }

    @Test
    public void testGetTraitsNames() throws Exception {
        HierarchicalTypeDefinition<TraitType> classificationTraitDefinition =
                TypesUtil.createTraitTypeDef("Classification",
                        ImmutableList.<String>of(),
                        TypesUtil.createRequiredAttrDef("tag", DataTypes.STRING_TYPE));
        HierarchicalTypeDefinition<TraitType> piiTrait =
                TypesUtil.createTraitTypeDef("PII", ImmutableList.<String>of());
        HierarchicalTypeDefinition<TraitType> phiTrait =
                TypesUtil.createTraitTypeDef("PHI", ImmutableList.<String>of());
        HierarchicalTypeDefinition<TraitType> pciTrait =
                TypesUtil.createTraitTypeDef("PCI", ImmutableList.<String>of());
        HierarchicalTypeDefinition<TraitType> soxTrait =
                TypesUtil.createTraitTypeDef("SOX", ImmutableList.<String>of());
        HierarchicalTypeDefinition<TraitType> secTrait =
                TypesUtil.createTraitTypeDef("SEC", ImmutableList.<String>of());
        HierarchicalTypeDefinition<TraitType> financeTrait =
                TypesUtil.createTraitTypeDef("Finance", ImmutableList.<String>of());

        getTypeSystem().defineTypes(
                ImmutableList.<StructTypeDefinition>of(),
                ImmutableList.of(classificationTraitDefinition, piiTrait, phiTrait, pciTrait,
                        soxTrait, secTrait, financeTrait),
                ImmutableList.<HierarchicalTypeDefinition<ClassType>>of());

        final ImmutableList<String> traitsNames = getTypeSystem().getTraitsNames();
        Assert.assertEquals(traitsNames.size(), 7);
        List traits = Arrays.asList(new String[]{
                "Classification",
                "PII",
                "PHI",
                "PCI",
                "SOX",
                "SEC",
                "Finance",
        });

        Assert.assertFalse(Collections.disjoint(traitsNames, traits));
    }
}
