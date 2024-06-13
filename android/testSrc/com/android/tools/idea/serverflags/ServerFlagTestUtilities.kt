/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.serverflags

import com.android.tools.idea.serverflags.protos.Brand
import com.android.tools.idea.serverflags.protos.OSType
import com.android.tools.idea.serverflags.protos.ServerFlag
import com.android.tools.idea.serverflags.protos.ServerFlagData
import com.android.tools.idea.serverflags.protos.ServerFlagList
import com.android.tools.idea.serverflags.protos.ServerFlagTest
import com.google.protobuf.Any
import com.intellij.util.io.createParentDirectories
import java.nio.file.Path
import kotlin.io.path.createFile

private const val FILE_NAME = "serverflaglist.protobuf"

val serverFlagTestData: ServerFlagList
  get() {
    val flags = mutableListOf(
      ServerFlag.newBuilder().apply {
        percentEnabled = 0
        booleanValue = true
      }.build(),
      ServerFlag.newBuilder().apply {
        percentEnabled = 0
        intValue = 1
      }.build(),
      ServerFlag.newBuilder().apply {
        percentEnabled = 100
        floatValue = 1f
      }.build(),
      ServerFlag.newBuilder().apply {
        percentEnabled = 100
        stringValue = "foo"
      }.build(),
      ServerFlag.newBuilder().apply {
        percentEnabled = 100
        protoValue = Any.pack(ServerFlagTest.newBuilder().apply {
          content = "content"
        }.build())
      }.build(),
    )

    val flagData = listOf(
      makeServerFlagData("boolean", flags[0]),
      makeServerFlagData("int", flags[1]),
      makeServerFlagData("float", flags[2]),
      makeServerFlagData("string", flags[3]),
      makeServerFlagData("proto", flags[4]),
    )

    val builder = ServerFlagList.newBuilder().apply {
      configurationVersion = 1
    }
    builder.addAllServerFlags(flagData)
    return builder.build()
  }

val serverFlagTestDataByOs: ServerFlagList
  get() {
    val flagData = enumValues<OSType>().map {
      makeServerFlagData(it.toString(),
                         ServerFlag.newBuilder().apply {
                           percentEnabled = 100
                           booleanValue = true
                           addOsType(it)
                         }.build())
    }

    val builder = ServerFlagList.newBuilder().apply {
      configurationVersion = 1
    }
    builder.addAllServerFlags(flagData)
    return builder.build()
  }

val serverFlagTestDataByBrand: ServerFlagList
  get() {
    val flagData = enumValues<Brand>().map {
      makeServerFlagData(it.toString(),
                         ServerFlag.newBuilder().apply {
                           percentEnabled = 100
                           booleanValue = true
                           addBrand(it)
                         }.build())
    }

    val builder = ServerFlagList.newBuilder().apply {
      configurationVersion = 1
    }
    builder.addAllServerFlags(flagData)
    return builder.build()
  }

private fun makeServerFlagData(flagName: String, flag: ServerFlag): ServerFlagData {
  return ServerFlagData.newBuilder().apply {
    name = flagName
    serverFlag = flag
  }.build()
}

fun loadServerFlagList(path: Path, version: String): ServerFlagList {
  val filePath = path.resolve("$version/$FILE_NAME")
  filePath.toFile().inputStream().use { return ServerFlagList.parseFrom(it) }
}

fun saveServerFlagList(serverFlagList: ServerFlagList, path: Path, version: String) {
  val filePath = path.resolve("$version/$FILE_NAME")
  filePath.createParentDirectories().createFile()
  filePath.toFile().outputStream().use { serverFlagList.writeTo(it) }
}