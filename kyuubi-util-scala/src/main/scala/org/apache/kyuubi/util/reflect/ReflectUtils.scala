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

package org.apache.kyuubi.util.reflect

import scala.util.{Failure, Success, Try}

object ReflectUtils {

  def getFieldVal[T](target: Any, fieldName: String): T =
    Try {
      DynFields.builder().hiddenImpl(target.getClass, fieldName).build[T]().get(target)
    } match {
      case Success(value) => value
      case Failure(e) =>
        val candidates = target.getClass.getDeclaredFields.map(_.getName).mkString("[", ",", "]")
        throw new RuntimeException(s"$fieldName not in ${target.getClass} $candidates", e)
    }

  def getFieldValOpt[T](target: Any, name: String): Option[T] =
    Try(getFieldVal[T](target, name)).toOption

  def invoke(target: AnyRef, methodName: String, args: (Class[_], AnyRef)*): AnyRef =
    try {
      val (types, values) = args.unzip
      DynMethods.builder(methodName).hiddenImpl(target.getClass, types: _*).build()
        .invoke(target, values: _*)
    } catch {
      case e: NoSuchMethodException =>
        val candidates = target.getClass.getMethods.map(_.getName).mkString("[", ",", "]")
        throw new RuntimeException(s"$methodName not in ${target.getClass} $candidates", e)
    }

  def invokeAs[T](target: AnyRef, methodName: String, args: (Class[_], AnyRef)*): T =
    invoke(target, methodName, args: _*).asInstanceOf[T]
}
