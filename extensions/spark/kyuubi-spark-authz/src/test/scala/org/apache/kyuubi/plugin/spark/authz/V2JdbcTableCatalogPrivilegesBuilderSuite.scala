/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.kyuubi.plugin.spark.authz

import java.sql.DriverManager

import scala.util.Try

import org.scalatest.Outcome

import org.apache.kyuubi.plugin.spark.authz.serde._
import org.apache.kyuubi.plugin.spark.authz.util.AuthZUtils._

class V2JdbcTableCatalogPrivilegesBuilderSuite extends V2CommandsPrivilegesSuite {
  override protected val catalogImpl: String = "in-memory"

  override protected val supportsUpdateTable = true
  override protected val supportsMergeIntoTable = true
  override protected val supportsDelete = true
  override protected val supportsPartitionGrammar = false
  override protected val supportsPartitionManagement = false

  val dbUrl = s"jdbc:derby:memory:$catalogV2"
  val jdbcUrl: String = s"$dbUrl;create=true"

  override def beforeAll(): Unit = {
    if (isSparkV31OrGreater) {
      spark.conf.set(
        s"spark.sql.catalog.$catalogV2",
        "org.apache.spark.sql.execution.datasources.v2.jdbc.JDBCTableCatalog")
      spark.conf.set(s"spark.sql.catalog.$catalogV2.url", jdbcUrl)
      spark.conf.set(
        s"spark.sql.catalog.$catalogV2.driver",
        "org.apache.derby.jdbc.AutoloadedDriver")
    }
    super.beforeAll()
  }

  override def afterAll(): Unit = {
    super.afterAll()

    // cleanup db
    Try {
      DriverManager.getConnection(s"$dbUrl;shutdown=true")
    }
  }

  override def withFixture(test: NoArgTest): Outcome = {
    assume(isSparkV31OrGreater)
    test()
  }

  test("Extracting table info with ResolvedDbObjectNameTableExtractor") {
    val ns1 = "testns1"
    val tbl = "testtbl"
    withDatabase(s"$ns1") { ns =>
      sql(s"CREATE NAMESPACE $ns")
      withTable(s"$catalogV2.$ns1.$tbl") { t =>
        Seq(
          s"CREATE TABLE IF NOT EXISTS $t(key int)",
          s"CREATE TABLE IF NOT EXISTS $t as SELECT 1",
          s"REPLACE TABLE $t as SELECT 1").foreach { str =>
          val plan = executePlan(str).analyzed
          val spec = TABLE_COMMAND_SPECS(plan.getClass.getName)
          var table: Table = null
          spec.tableDescs.find { d =>
            Try(table = d.extract(plan, spark).get).isSuccess
          }
          withClue(str) {
            assert(table.catalog === Some(catalogV2))
            assert(table.database === Some(ns1))
            assert(table.table === tbl)
            assert(table.owner.isEmpty)
          }
        }
      }
    }
  }

  test("Extracting table info with ResolvedTableTableExtractor") {
    val ns1 = "testns1"
    val tbl = "testtbl"
    withDatabase(s"$ns1") { ns =>
      sql(s"CREATE NAMESPACE $ns")
      withTable(s"$catalogV2.$ns1.$tbl") { t =>
        sql(s"CREATE TABLE IF NOT EXISTS $t(key int)")
        val sql1 = s"SHOW CREATE TABLE $t"
        val plan = executePlan(sql1).analyzed
        val spec = TABLE_COMMAND_SPECS(plan.getClass.getName)
        var table: Table = null
        spec.tableDescs.find { d =>
          Try(table = d.extract(plan, spark).get).isSuccess
        }
        withClue(sql1) {
          assert(table.catalog === Some(catalogV2))
          assert(table.database === Some(ns1))
          assert(table.table === tbl)
          assert(table.owner.isEmpty)
        }
      }
    }
  }

  test("Extracting table info with DataSourceV2RelationTableExtractor") {
    val ns1 = "testns1"
    val tbl = "testtbl"
    withDatabase(s"$ns1") { ns =>
      sql(s"CREATE NAMESPACE $ns")
      withTable(s"$catalogV2.$ns1.$tbl") { t =>
        sql(s"CREATE TABLE IF NOT EXISTS $t(key int)")
        val sql1 = s"INSERT INTO TABLE $t VALUES(1)"
        val plan = executePlan(sql1).analyzed
        val spec = TABLE_COMMAND_SPECS(plan.getClass.getName)
        var table: Table = null
        spec.tableDescs.find { d => Try(table = d.extract(plan, spark).get).isSuccess }
        withClue(sql1) {
          assert(table.catalog === Some(catalogV2))
          assert(table.database === Some(ns1))
          assert(table.table === tbl)
          assert(table.owner.isEmpty)
        }
      }
    }
  }

  test("Extracting database info with ResolvedDBObjectNameDatabaseExtractor") {
    val ns1 = "testns1"

    Seq(s"CREATE NAMESPACE IF NOT EXISTS $catalogV2.$ns1", s"USE $catalogV2.$ns1").foreach { sql =>
      val plan = executePlan(sql).analyzed
      val spec = DB_COMMAND_SPECS(plan.getClass.getName)
      var db: Database = null
      spec.databaseDescs.find { d =>
        Try(db = d.extract(plan)).isSuccess
      }
      withClue(sql) {
        assert(db.catalog === Some(catalogV2))
        assert(db.database === ns1)
      }
    }

  }

  test("Extracting database info with ResolvedNamespaceDatabaseExtractor") {
    val ns1 = "testns1"
    withDatabase(s"$ns1") { ns =>
      sql(s"CREATE NAMESPACE $ns")
      val sql1 = s"DROP NAMESPACE IF EXISTS $catalogV2.$ns1"
      val plan = executePlan(sql1).analyzed
      val spec = DB_COMMAND_SPECS(plan.getClass.getName)
      var db: Database = null
      spec.databaseDescs.find { d =>
        Try(db = d.extract(plan)).isSuccess
      }
      withClue(sql1) {
        assert(db.catalog === Some(catalogV2))
        assert(db.database === ns1)
      }
    }
  }
}
