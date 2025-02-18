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

package org.apache.kyuubi.plugin.spark.authz.serde

import org.apache.kyuubi.util.reflect.ReflectUtils._

trait CatalogExtractor extends (AnyRef => Option[String]) with Extractor

object CatalogExtractor {
  val catalogExtractors: Map[String, CatalogExtractor] = {
    loadExtractorsToMap[CatalogExtractor]
  }
}

/**
 * org.apache.spark.sql.connector.catalog.CatalogPlugin
 */
class CatalogPluginCatalogExtractor extends CatalogExtractor {
  override def apply(v1: AnyRef): Option[String] = {
    Option(invokeAs[String](v1, "name"))
  }
}

/**
 * Option[org.apache.spark.sql.connector.catalog.CatalogPlugin]
 */
class CatalogPluginOptionCatalogExtractor extends CatalogExtractor {
  override def apply(v1: AnyRef): Option[String] = {
    v1 match {
      case Some(catalogPlugin: AnyRef) =>
        new CatalogPluginCatalogExtractor().apply(catalogPlugin)
      case _ => None
    }
  }
}

/**
 * Option[String]
 */
class StringOptionCatalogExtractor extends CatalogExtractor {
  override def apply(v1: AnyRef): Option[String] = {
    v1.asInstanceOf[Option[String]]
  }
}
