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
package com.android.tools.rendering.security

import com.intellij.openapi.application.PathManager
import java.nio.file.Path
import java.nio.file.Paths

private fun normalizeDirectoryPath(originalPath: Path): String =
  originalPath.normalize().toString() + originalPath.fileSystem.separator

private fun normalizeDirectoryPath(stringPath: String): String =
  normalizeDirectoryPath(Paths.get(stringPath))

object RenderSecurityManagerDefaults {
  @JvmStatic
  fun getDefaultAllowedPaths(): Array<String> =
    arrayOf(
      // When loading classes, IntelliJ might sometimes drop a corruption marker
      normalizeDirectoryPath(PathManager.getIndexRoot()),
      /*
        Root of the path where IntelliJ stores the logs. When loading classes,
        IntelliJ might try to update cache hashes for the loaded files
      */
      normalizeDirectoryPath(PathManager.getLogPath()),
      // When loading classes, IntelliJ might try to update cache hashes for the loaded files
      normalizeDirectoryPath(Paths.get(PathManager.getSystemPath(), "caches")),
    )
}
