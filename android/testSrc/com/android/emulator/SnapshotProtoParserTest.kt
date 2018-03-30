/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.emulator

import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Unit tests for [SnapshotProtoParser].
 */
class SnapshotProtoParserTest {
  @get:Rule // Tell JUnit that this property's getter function is a public rule
  val temporaryFolder = TemporaryFolder()

  @Test
  fun testSnapshotProtoParser() {
    val noImageName = "file_name_no_image"
    val hasImageName = "snap_file_has_image_name"
    val allGoodName = "snap_file_all_good"
    val snapLogicalName = "snap_logical_name"
    // Try to read a non-existing protobuf
    assertFailsWith(SnapshotProtoException::class) {
      SnapshotProtoParser(File("bogus_name"), "base_name")
    }

    // Create a snapshot protobuf without any images
    val builder = SnapshotOuterClass.Snapshot.newBuilder()

    val noImageFile = temporaryFolder.newFile(noImageName)
    val oStreamNoImage = FileOutputStream(noImageFile)
    val protoBuf = builder.build()
    protoBuf.writeTo(oStreamNoImage)

    // Try to read the protobuf with no images
    assertFailsWith(SnapshotProtoException::class) {
      SnapshotProtoParser(noImageFile, "base_name")
    }

    // Add an image to the protobuf--then it should be valid
    val imageBuilder = SnapshotOuterClass.Image.newBuilder()
    val anImage = imageBuilder.build()
    builder.addImages(anImage)

    val protoBufHasImage = builder.build()
    val hasImageFile = temporaryFolder.newFile(hasImageName)
    val oStreamHasImage = FileOutputStream(hasImageFile)
    protoBufHasImage.writeTo(oStreamHasImage)
    val hasImageProtoParser = SnapshotProtoParser(hasImageFile, "base_name")
    assertEquals("base_name", hasImageProtoParser.logicalName, "Wrong default logical name")

    // Add a logical name to the protobuf
    builder.logicalName = snapLogicalName
    val protoBufLogicalName = builder.build()
    val allGoodFile = temporaryFolder.newFile(allGoodName)
    val oStreamLogicalName = FileOutputStream(allGoodFile)
    protoBufLogicalName.writeTo(oStreamLogicalName)

    val allGoodProtoParser = SnapshotProtoParser(allGoodFile, "base_name")
    assertEquals(snapLogicalName, allGoodProtoParser.logicalName, "Wrong logical name")
  }
}
