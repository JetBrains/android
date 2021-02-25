/*
 * Copyright (C) 2021 The Android Open Source Project
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
@file:JvmName("UpdateOfflineGMavenIndex")

package com.android.tools.idea.imports

import com.google.common.io.Resources
import java.net.URL
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardOpenOption

/**
 * Updates the checked in class index file of the Google Maven repository.
 *
 * This class can be run using IJ run configurations or from bazel:
 * `bazel run //tools/adt/idea/android:update_offline_gmaven_index_main`. In both cases, path to
 * the repo root directory (the one with `.repo` in it) needs to be passed as the only argument.
 */
fun main(args: Array<String>) {
  val root = args.singleOrNull() ?: error("You have to specify the repo root as only argument.")
  val repoRoot = Paths.get(root)
  if (!Files.isDirectory(repoRoot.resolve(".repo"))) {
    error("Invalid directory: should be pointing to the root of a tools checkout directory.")
  }

  val indexData = ungzip(readUrlData("$BASE_URL/$RELATIVE_PATH"))

  val file = repoRoot.resolve("tools/adt/idea/android/resources/gmavenIndex/$OFFLINE_NAME.json")
  Files.write(file, indexData, StandardOpenOption.CREATE)
  println("Finished updating $file.")
}

/**
 * Reads the data from the given URL.
 */
private fun readUrlData(url: String): ByteArray = Resources.asByteSource(URL(url)).read()
