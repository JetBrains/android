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
package com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.misc

import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.proto.FileProto
import junit.framework.TestCase
import java.io.File
import kotlin.test.assertFails

class PathConverterTest : TestCase() {
  fun testToProtoSimple() {
    val rootString = "/root/dir"
    val childString = "path/to/child.file"
    val fileString = "$rootString/$childString"

    val converter = PathConverter(File(rootString), File("/"), File("/lib"), File("/out"))

    assertEquals(childString, converter.fileToProto(File(fileString)).relativePath)
  }

  fun testToProtoSDK() {
    val root = File("/root/")
    val sdkRoot = File("/sdk/")
    val childString = "path/to/child.file"
    val file = File(root, childString)
    val sdkFile = File(sdkRoot, childString)

    val converter = PathConverter(root, sdkRoot, File("/lib"), File("/out"))

    assertEquals(childString, converter.fileToProto(file, PathConverter.DirType.MODULE).relativePath)
    assertEquals(childString, converter.fileToProto(sdkFile, PathConverter.DirType.SDK).relativePath)
    assertFails {
      converter.fileToProto(sdkFile)
    }
  }

  fun testToProtoFail() {
    val root = File("/root/dir")
    val badRoot = File("/root/baddir")
    val file = File(root, "path/to/child.file")

    val converter = PathConverter(badRoot, File("/"), File("/lib"), File("/out"))

    assertFails {
      converter.fileToProto(file)
    }
  }

  fun testFromProto() {
    val root = File("/root/dir")
    val childString = "path/to/child.file"
    val file = File(root, "path/to/child.file")

    val converter = PathConverter(root, File("/"), File("/lib"), File("/out"))

    val proto = FileProto.File.newBuilder()
      .setRelativePath(childString)
      .setRelativeTo(FileProto.File.RelativeTo.MODULE)
      .build()!!

    assertEquals(file, converter.fileFromProto(proto))
  }

  fun testFromHalfSpecifiedProto() {
    val proto = FileProto.File.newBuilder()
      .setRelativePath("/")
      .build()!!

    val converter = PathConverter(File("/"), File("/"), File("/lib"), File("/out"))

    assertFails {
      converter.fileFromProto(proto)
    }
  }
}