/*
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

package org.apache.hadoop.metadata.query

import com.thinkaurelius.titan.core.TitanGraph
import org.apache.hadoop.metadata.query.Expressions._
import org.apache.hadoop.metadata.typesystem.types.TypeSystem
import org.junit.runner.RunWith
import org.scalatest._
import Matchers._
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class GremlinTest2 extends FunSuite with BeforeAndAfterAll with BaseGremlinTest {

  var g: TitanGraph = null

  override def beforeAll() {
    TypeSystem.getInstance().reset()
    QueryTestsUtils.setupTypes
    g = QueryTestsUtils.setupTestGraph
  }

  override def afterAll() {
    g.shutdown()
  }

  test("testTraitSelect") {
    val r = QueryProcessor.evaluate(_class("Table").as("t").join("Dimension").as("dim").select(id("t"), id("dim")), g)
    validateJson(r, "{\n  \"query\":\"Table as t.Dimension as dim select t as _col_0, dim as _col_1\",\n  \"dataType\":{\n    \"typeName\":\"\",\n    \"attributeDefinitions\":[\n      {\n        \"name\":\"_col_0\",\n        \"dataTypeName\":\"Table\",\n        \"multiplicity\":{\n          \"lower\":0,\n          \"upper\":1,\n          \"isUnique\":false\n        },\n        \"isComposite\":false,\n        \"isUnique\":false,\n        \"isIndexable\":true,\n        \"reverseAttributeName\":null\n      },\n      {\n        \"name\":\"_col_1\",\n        \"dataTypeName\":\"Dimension\",\n        \"multiplicity\":{\n          \"lower\":0,\n          \"upper\":1,\n          \"isUnique\":false\n        },\n        \"isComposite\":false,\n        \"isUnique\":false,\n        \"isIndexable\":true,\n        \"reverseAttributeName\":null\n      }\n    ]\n  },\n  \"rows\":[\n    {\n      \"$typeName$\":\"\",\n      \"_col_1\":{\n        \"$typeName$\":\"Dimension\"\n      },\n      \"_col_0\":{\n        \"id\":\"3328\",\n        \"$typeName$\":\"Table\",\n        \"version\":0\n      }\n    },\n    {\n      \"$typeName$\":\"\",\n      \"_col_1\":{\n        \"$typeName$\":\"Dimension\"\n      },\n      \"_col_0\":{\n        \"id\":\"4864\",\n        \"$typeName$\":\"Table\",\n        \"version\":0\n      }\n    },\n    {\n      \"$typeName$\":\"\",\n      \"_col_1\":{\n        \"$typeName$\":\"Dimension\"\n      },\n      \"_col_0\":{\n        \"id\":\"6656\",\n        \"$typeName$\":\"Table\",\n        \"version\":0\n      }\n    }\n  ]\n}")
  }

  test("testTrait") {
    val r = QueryProcessor.evaluate(_trait("Dimension"), g)
    validateJson(r)
  }

  test("testTraitInstance") {
    val r = QueryProcessor.evaluate(_trait("Dimension").traitInstance(), g)
    validateJson(r)
  }

  test("testInstanceAddedToFilter") {
    val r = QueryProcessor.evaluate(_trait("Dimension").hasField("typeName"), g)
    validateJson(r)
  }

  test("testInstanceFilter") {
    val r = QueryProcessor.evaluate(_trait("Dimension").traitInstance().hasField("name"), g)
    validateJson(r)
  }

  test("testLineageWithPath") {
    val r = QueryProcessor.evaluate(_class("Table").loop(id("LoadProcess").field("outputTable")).path(), g)
    validateJson(r)
  }

  test("testLineageAllSelectWithPath") {
    val r = QueryProcessor.evaluate(_class("Table").as("src").loop(id("LoadProcess").field("outputTable")).as("dest").
      select(id("src").field("name").as("srcTable"), id("dest").field("name").as("destTable")).path(), g)
    validateJson(r)
  }

  test("testLineageAllSelectWithPathFromParser") {
    val p = new QueryParser
    val e = p("Table as src loop (LoadProcess outputTable) as dest " +
      "select src.name as srcTable, dest.name as destTable withPath").right.get
    //Table as src loop (LoadProcess where LoadProcess.outputTable) as dest select src.name as srcTable, dest.name as destTable withPath
    val r = QueryProcessor.evaluate(e, g)
    validateJson(r)
  }

  test("testLineageAllSelectWithPathFromParser2") {
    val p = new QueryParser

    val e = p("Table as src loop (`LoadProcess->outputTable` inputTables) as dest " +
      "select src.name as srcTable, dest.name as destTable withPath").right.get
    val r = QueryProcessor.evaluate(e, g)
    validateJson(r)
  }

  test("testHighLevelLineage") {
        val r = HiveLineageQuery("Table", "sales_fact_monthly_mv",
          "LoadProcess",
          "inputTables",
          "outputTable",
        None, Some(List("name")), true, GraphPersistenceStrategy1, g).evaluate()
    validateJson(r)
  }

  test("testHighLevelWhereUsed") {
    val r = HiveWhereUsedQuery("Table", "sales_fact",
      "LoadProcess",
      "inputTables",
      "outputTable",
      None, Some(List("name")), true, GraphPersistenceStrategy1, g).evaluate()
    validateJson(r)
  }

}