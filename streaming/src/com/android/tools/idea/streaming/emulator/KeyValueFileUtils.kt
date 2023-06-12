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
@file:JvmName("KeyValueFiles")
package com.android.tools.idea.streaming.emulator

import com.google.common.base.Splitter
import com.intellij.openapi.diagnostic.Logger
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.StandardOpenOption.CREATE
import java.util.TreeMap
import kotlin.text.Charsets.UTF_8

/**
 * Reads a subset of values in a key-value file. A key and the corresponding value are separated by an equals sign.
 *
 * @param file the file to read from
 * @param keysToExtract the keys to be returned together with the corresponding values,
 *     or null to return all keys and values.
 */
fun readKeyValueFile(file: Path, keysToExtract: Set<String>? = null): Map<String, String>? {
  val result = mutableMapOf<String, String>()
  try {
    for (line in Files.readAllLines(file)) {
      val keyValue = KEY_VALUE_SPLITTER.splitToList(line)
      if (keyValue.size == 2 && (keysToExtract == null || keysToExtract.contains(keyValue[0]))) {
        result[keyValue[0]] = keyValue[1]
      }
    }
    return result
  }
  catch (e: IOException) {
    logError("Error reading $file", e)
    return null
  }
}

/**
 * Updates some values in a key-value file. A key and the corresponding value are separated by an equals sign.
 *
 * @param file the file to write to
 * @param updates the keys and the corresponding values to update; keys with null values are deleted
 */
fun updateKeyValueFile(file: Path, updates: Map<String, String?>) {
  val originalContents = readKeyValueFile(file) ?: return
  val sortedContents = TreeMap(originalContents)
  sortedContents.putAll(updates)
  val lines = sortedContents.entries.asSequence()
    .filter { it.value != null }
    .map { "${it.key}=${it.value}"}
    .toList()
  // Write to a temporary file first then atomically replace the original file.
  val tempFile = file.resolveSibling(file.fileName.toString() + ".temp")
  try {
    Files.write(tempFile, lines, UTF_8, CREATE)
    Files.move(tempFile, file, REPLACE_EXISTING, ATOMIC_MOVE)
  }
  catch (e: IOException) {
    try {
      Files.deleteIfExists(tempFile)
    }
    catch (ignore: IOException) {
    }
    logError("Error writing $file", e)
  }
}

private fun logError(message: String, e: IOException) {
  logger().error(e.message?.let { "$message - $it" } ?: message)
}

private val KEY_VALUE_SPLITTER = Splitter.on('=').trimResults()

private fun logger() = Logger.getInstance("#com.android.tools.idea.streaming.emulator.KeyValueFileUtils")
