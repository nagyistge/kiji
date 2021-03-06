// (c) Copyright 2014 WibiData, Inc.
package org.kiji.spark.connector.rdd

import org.junit.Assert
import org.junit.Test
import org.kiji.schema.EntityId
import org.kiji.schema.EntityIdFactory
import org.kiji.schema.avro.ComponentType
import org.kiji.schema.avro.HashSpec
import org.kiji.schema.avro.RowKeyComponent
import org.kiji.schema.avro.RowKeyEncoding
import org.kiji.schema.avro.RowKeyFormat2
import org.kiji.spark.connector.rdd.hbase.HBaseKijiPartition

import scala.collection.JavaConverters.seqAsJavaListConverter

class TestHbaseKijiPartitionSuite {
  import org.kiji.spark.connector.rdd.TestHbaseKijiPartitionSuite._

  @Test
  def simpleKijiPartition() {
    val partition: HBaseKijiPartition = HBaseKijiPartition(
      INDEX,
      START_ENTITYID.getHBaseRowKey,
      STOP_ENTITYID.getHBaseRowKey
    )
    Assert.assertEquals(START_ENTITYID.getHBaseRowKey, partition.startLocation.getHBaseRowKey)
    Assert.assertEquals(STOP_ENTITYID.getHBaseRowKey, partition.stopLocation.getHBaseRowKey)
    Assert.assertEquals(INDEX, partition.index)
  }
}

object TestHbaseKijiPartitionSuite {
  val COMPONENTS = List(
      RowKeyComponent.newBuilder().setName("astring").setType(ComponentType.STRING).build(),
      RowKeyComponent.newBuilder().setName("anint").setType(ComponentType.INTEGER).build(),
      RowKeyComponent.newBuilder().setName("along").setType(ComponentType.LONG).build()
  )
  val FORMAT: RowKeyFormat2 = RowKeyFormat2.newBuilder()
      .setEncoding(RowKeyEncoding.FORMATTED)
      .setSalt(HashSpec.newBuilder().build())
      .setComponents(COMPONENTS.asJava)
      .build()

  val ENTITYID_FACTORY = EntityIdFactory.getFactory(FORMAT)
  val START_ENTITYID: EntityId = ENTITYID_FACTORY
      .getEntityId(
          "start": java.lang.String,
          0: java.lang.Integer,
          0L: java.lang.Long
      )
  val STOP_ENTITYID: EntityId = ENTITYID_FACTORY
      .getEntityId(
          "stop": java.lang.String,
          1: java.lang.Integer,
          1L: java.lang.Long
      )

  val INDEX = 1
}
