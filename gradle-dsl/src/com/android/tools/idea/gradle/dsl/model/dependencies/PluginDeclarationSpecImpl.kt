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
package com.android.tools.idea.gradle.dsl.model.dependencies

import com.android.tools.idea.gradle.dsl.api.dependencies.PluginDeclarationSpec
import com.android.tools.idea.gradle.dsl.api.dependencies.VersionDeclarationSpec
import com.android.tools.idea.gradle.dsl.utils.GRADLE_PATH_SEPARATOR
import com.google.common.base.Joiner
import com.google.common.base.Splitter
import com.google.common.collect.Lists

class PluginDeclarationSpecImpl(private var id: String,
                                private var version: VersionDeclarationSpec): PluginDeclarationSpec {
  companion object {
    fun create(notation: String): PluginDeclarationSpecImpl? {
      val segments = Splitter.on(GRADLE_PATH_SEPARATOR).trimResults().omitEmptyStrings().splitToList(notation)
      return when (segments.size) {
        2 -> {
          val versionSpec = VersionDeclarationSpecImpl.create(segments[1])
          versionSpec?.let {
            PluginDeclarationSpecImpl(segments[0], versionSpec)
          }
        }
        else -> null
      }
    }
  }

  override fun getId(): String = id

  override fun getVersion(): VersionDeclarationSpec = version

  fun setVersion(newVersion: VersionDeclarationSpec) {
    version = newVersion
  }

  /**
   * Return true if newVersion was parsed and set as version spec
   */
  fun setStringVersion(newVersion: String): Boolean {
    val newSpec = VersionDeclarationSpecImpl.create(newVersion)
    return if (newSpec != null) {
      version = newSpec
      true
    }
    else false
  }

  fun setId(newId: String): Boolean {
    id = newId
    return true
  }

  override fun toString(): String = compactNotation()

  override fun compactNotation(): String {
    val str = version.compactNotation()
    val versionString = if (str.isNullOrBlank()) null else str

    return Joiner.on(GRADLE_PATH_SEPARATOR).skipNulls().join(
      Lists.newArrayList(id, versionString)
    )
  }
}