/**
 * (c) Copyright 2014 WibiData, Inc.
 *
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.kiji.spark.connector.serialization

import scala.collection.JavaConverters.asScalaIteratorConverter

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.Serializer
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output

import org.kiji.schema.EntityId
import org.kiji.schema.KijiCell
import org.kiji.schema.KijiColumnName
import org.kiji.schema.KijiDataRequest
import org.kiji.schema.impl.MaterializedKijiResult

/**
 * Kryo serializer for [[org.kiji.schema.impl.MaterializedKijiResult]].
 * Non-implementation specific.
 */
class MaterializedKijiResultSerializer[T] extends Serializer[MaterializedKijiResult[T]] {
  override def write(
                      kryo: Kryo,
                      output: Output,
                      kijiResult: MaterializedKijiResult[T]): Unit = {
    kryo.writeClassAndObject(output, kijiResult.getDataRequest)
    kryo.writeClassAndObject(output, kijiResult.getEntityId)
    kryo.writeClassAndObject(output, kijiResult.iterator().asScala.toList)
  }

  override def read(
                     kryo: Kryo,
                     input: Input,
                     clazz: Class[MaterializedKijiResult[T]]): MaterializedKijiResult[T] = {
    val dataRequest: KijiDataRequest = kryo.readClassAndObject(input).asInstanceOf[KijiDataRequest]
    val entityId: EntityId = kryo.readClassAndObject(input).asInstanceOf[EntityId]
    val materials: List[KijiCell[T]] = kryo.readClassAndObject(input).asInstanceOf[List[KijiCell[T]]]

    // Uses Java collections because MaterializedKijiResult takes a Java SortedMap as a parameter
    val map = new java.util.TreeMap[KijiColumnName, java.util.List[KijiCell[T]]]
    for (cell: KijiCell[T] <- materials) {
      if (!map.containsKey(cell.getColumn)) {
        val list = new java.util.ArrayList[KijiCell[T]]()
        list.add(cell)
        map.put(cell.getColumn, list)
      } else {
        map.get(cell.getColumn).add(cell)
      }
    }
    val materializedResult =  map.asInstanceOf[java.util.SortedMap[KijiColumnName, java.util.List[KijiCell[T]]]]

    MaterializedKijiResult.create(entityId, dataRequest, materializedResult)
  }
}