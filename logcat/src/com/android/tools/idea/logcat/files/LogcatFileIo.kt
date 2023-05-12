/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.logcat.files

import com.android.tools.idea.logcat.devices.Device
import com.android.tools.idea.logcat.files.LogcatFileData.Metadata
import com.android.tools.idea.logcat.message.LogcatMessage
import com.android.tools.idea.logcat.util.LOGGER
import com.google.gson.GsonBuilder
import com.google.gson.JsonSyntaxException
import java.io.File
import java.io.FileWriter
import java.io.IOException


private val gson = GsonBuilder()
  .setPrettyPrinting()
  .create()

/** Contains functions to read and write a Logcat file */
internal object LogcatFileIo {
  fun writeLogcat(
    file: File,
    logcatMessages: List<LogcatMessage>,
    device: Device,
    filter: String,
    projectApplicationIds: Set<String>
  ) {
    val data = LogcatFileData(Metadata(device, filter, projectApplicationIds), logcatMessages)
    FileWriter(file).use {
      gson.toJson(data, it)
    }
  }

  fun readLogcat(file: File): LogcatFileData? {
    val contents = try {
      file.readText()
    }
    catch (e: IOException) {
      LOGGER.info("Failed to load Logcat file '$file'", e)
      return null
    }
    if (contents.isEmpty()) {
      LOGGER.info("Logcat file '$file' is empty")
      return null
    }
    if (contents.startsWith("{")) {
      return try {
        gson.fromJson(contents, LogcatFileData::class.java)
      }
      catch (e: JsonSyntaxException) {
        LOGGER.info("Failed to parse Logcat file '$file'", e)
        null
      }
    }
    LOGGER.info("Unknown Logcat file format found in '$file'")
    return null
  }
}
