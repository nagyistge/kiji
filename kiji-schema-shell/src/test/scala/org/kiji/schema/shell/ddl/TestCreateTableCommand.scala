/**
 * (c) Copyright 2012 WibiData, Inc.
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

package org.kiji.schema.shell.ddl

import scala.collection.JavaConversions._
import org.specs2.mutable._

import org.kiji.schema.avro.RowKeyEncoding
import org.kiji.schema.avro.RowKeyFormat2
import org.kiji.schema.layout.KijiTableLayout
import org.kiji.schema.shell.DDLException
import org.kiji.schema.shell.ddl.key._

class TestCreateTableCommand extends CommandTestCase {
  "CreateTableCommand" should {
    "require 1+ locality groups" in {
      val ctcmd = new CreateTableCommand(env, "foo", Some("desc"), DefaultKeySpec, List())
      ctcmd.validateArguments() must throwA[DDLException]
    }

    "require non-empty name" in {
      val ctcmd = new CreateTableCommand(env, "", Some("desc"), RawFormattedKeySpec, List())
      ctcmd.validateArguments() must throwA[DDLException]
    }

    "create reasonable looking Avro records" in {
      val locGroup = new LocalityGroupClause("default", None, List())
      val ctcmd = new CreateTableCommand(env, "foo", None, new HashedFormattedKeySpec,
          List(locGroup))

      ctcmd.validateArguments()
      val layout = ctcmd.getInitialLayout()
      ctcmd.updateLayout(layout)
      layout.getDescription() mustEqual ""
      layout.getName() mustEqual "foo"
      KijiTableLayout.getEncoding(layout.getKeysFormat()) mustEqual RowKeyEncoding.FORMATTED
      val locGroupAvroList = layout.getLocalityGroups()
      locGroupAvroList.size mustEqual 1
      val locGroupAvro = locGroupAvroList.head
      locGroupAvro.getName() mustEqual "default"

      // Check that this succeeds. MockKijiSystem will validate that enough
      // default values are populated.
      ctcmd.applyUpdate(layout.build())
    }

    "support hashed as default row format" in {
      val locGroup = new LocalityGroupClause("default", None, List())
      val ctcmd = new CreateTableCommand(env, "foo", None, DefaultKeySpec, List(locGroup))

      ctcmd.validateArguments()
      val layout = ctcmd.getInitialLayout()
      ctcmd.updateLayout(layout)
      KijiTableLayout.getEncoding(layout.getKeysFormat()) mustEqual RowKeyEncoding.FORMATTED
      layout.getKeysFormat().asInstanceOf[RowKeyFormat2].getSalt().getHashSize() mustEqual 16
      layout.getKeysFormat().asInstanceOf[RowKeyFormat2]
          .getSalt().getSuppressKeyMaterialization() mustEqual true
      layout.getKeysFormat().asInstanceOf[RowKeyFormat2].getComponents().size mustEqual 1
      layout.getKeysFormat().asInstanceOf[RowKeyFormat2].getComponents()(0).getName mustEqual "key"

      // Check that this succeeds. MockKijiSystem will validate that enough
      // default values are populated.
      ctcmd.applyUpdate(layout.build())
    }

    "support row format raw" in {
      val locGroup = new LocalityGroupClause("default", None, List())
      val ctcmd = new CreateTableCommand(env, "foo", None, RawFormattedKeySpec, List(locGroup))

      ctcmd.validateArguments()
      val layout = ctcmd.getInitialLayout()
      ctcmd.updateLayout(layout)
      KijiTableLayout.getEncoding(layout.getKeysFormat()) mustEqual RowKeyEncoding.RAW

      // Check that this succeeds. MockKijiSystem will validate that enough
      // default values are populated.
      ctcmd.applyUpdate(layout.build())
    }

    "support row format hash prefix" in {
      val locGroup = new LocalityGroupClause("default", None, List())
      val ctcmd = new CreateTableCommand(env, "foo", None,
          new HashPrefixKeySpec(4), List(locGroup))

      ctcmd.validateArguments()
      val layout = ctcmd.getInitialLayout()
      ctcmd.updateLayout(layout)
      KijiTableLayout.getEncoding(layout.getKeysFormat()) mustEqual RowKeyEncoding.FORMATTED
      layout.getKeysFormat().asInstanceOf[RowKeyFormat2].getSalt().getHashSize() mustEqual 4
      layout.getKeysFormat().asInstanceOf[RowKeyFormat2].getComponents().size mustEqual 1
      layout.getKeysFormat().asInstanceOf[RowKeyFormat2].getComponents()(0).getName mustEqual "key"

      // Check that this succeeds. MockKijiSystem will validate that enough
      // default values are populated.
      ctcmd.applyUpdate(layout.build())
    }

    "fail if hashprefix size is greater than 16" in {
      val locGroup = new LocalityGroupClause("default", None, List())
      val ctcmd = new CreateTableCommand(env, "foo", None,
          new HashPrefixKeySpec(20), List(locGroup))

      ctcmd.validateArguments() must throwA[DDLException]
    }

    "fail if hashprefix size is less than 1" in {
      val locGroup = new LocalityGroupClause("default", None, List())
      val ctcmd = new CreateTableCommand(env, "foo", None,
          new HashPrefixKeySpec(0), List(locGroup))

      ctcmd.validateArguments() must throwA[DDLException]

      val ctcmd2 = new CreateTableCommand(env, "foo", None,
          new HashPrefixKeySpec(-2), List(locGroup))

      ctcmd.validateArguments() must throwA[DDLException]
    }

    "refuse to create tables that already exist" in {
      val locGroup = new LocalityGroupClause("default", None, List())
      val ctcmd = new CreateTableCommand(env, "foo", Some("desc"), DefaultKeySpec,
          List(locGroup))

      ctcmd.validateArguments()
      val layout = ctcmd.getInitialLayout()
      ctcmd.updateLayout(layout)
      layout.getDescription() mustEqual "desc" // Check that Some(desc) works.

      ctcmd.applyUpdate(layout.build()) // This should succeed.
      ctcmd.validateArguments() must throwA[DDLException] // But now the table exists. This fails.
    }
  }
}
