/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.appinspection.ide.resolver

import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.io.FileService
import com.intellij.util.io.ZipUtil
import java.nio.file.Path
import java.util.UUID
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlinx.coroutines.withContext

/**
 * Unzips the library to a temporary scratch directory if it's a zip.
 *
 * Returns the resulting inspector jar's path.
 */
suspend fun extractZipIfNeeded(targetDir: Path, libraryPath: Path) =
  withContext(AndroidDispatchers.diskIoThread) {
    if (libraryPath.isDirectory()) {
      libraryPath
    } else {
      ZipUtil.extract(libraryPath, targetDir) { _, name -> name == INSPECTOR_JAR }
      targetDir
    }
  }

fun Path.resolveExistsOrNull(path: String) = resolve(path).takeIf { it.exists() }

fun FileService.createRandomTempDir() = getOrCreateTempDir(UUID.randomUUID().toString())
