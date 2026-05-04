/*
 * Copyright 2026 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.qsync

import com.intellij.openapi.extensions.ExtensionPointName
import java.nio.file.Path

/** Extension point to provide the directory where aspect source files are stored. */
interface BundledAspectsProvider {
  fun getAspectDirectory(): Path?

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<BundledAspectsProvider> = ExtensionPointName.create("com.google.idea.blaze.BundledAspectsProvider")

    @JvmStatic
    fun getAspectDirectories(): List<Path> {
      return EP_NAME.extensionList.mapNotNull { it.getAspectDirectory() }
    }
  }
}
