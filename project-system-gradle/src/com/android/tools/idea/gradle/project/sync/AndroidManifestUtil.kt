/*
 * Copyright (C) 2025 The Android Open Source Project
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
@file:JvmName("AndroidManifestUtil")

package com.android.tools.idea.gradle.project.sync

import com.intellij.openapi.diagnostic.Logger
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory


fun hasAndroidManifest(contentRoot: Path): Boolean {
  return try {
    Files.list(contentRoot).anyMatch { childProjectDir ->
      childProjectDir.isDirectory()
      && (childProjectDir.hasAndroidManifestImpl()
      || Files.list(childProjectDir).anyMatch { grandChildProjectDir -> grandChildProjectDir.hasAndroidManifestImpl() })
    }
  }
  catch (e: Exception) {
    Logger.getInstance(SdkSync::class.java).error("Failed to find Android manifest due to I/O exception", e)
    false
  }
}

private fun Path.hasAndroidManifestImpl(): Boolean =
  resolve("src/main/AndroidManifest.xml").exists()
  || resolve("src/androidMain/AndroidManifest.xml").exists()
