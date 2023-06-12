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

import com.android.tools.idea.gradle.dsl.api.dependencies.LibraryDeclarationSpec
import com.android.tools.idea.gradle.dsl.utils.GRADLE_PATH_SEPARATOR
import com.google.common.base.Joiner
import com.google.common.base.Splitter
import com.google.common.collect.Lists

class LibraryDeclarationSpecImpl(private var name: String,
                                 private var group: String,
                                 private var version: String?) : LibraryDeclarationSpec {

  companion object {
    fun create(notation: String): LibraryDeclarationSpecImpl? {
      val segments = Splitter.on(GRADLE_PATH_SEPARATOR).trimResults().omitEmptyStrings().splitToList(notation)
      return if (segments.size > 1) { // requires at least group and name, version is optional
        LibraryDeclarationSpecImpl(segments[1],
                                   segments[0],
                                   segments.getOrNull(2))
      }
      else null
    }
  }

  override fun getName(): String = name

  override fun getGroup(): String = group

  override fun getVersion(): String? = version

  fun setName(newName: String) {
    name = newName
  }

  fun setGroup(newGroup: String) {
    group = newGroup
  }

  fun setVersion(newVersion: String?) {
    version = newVersion
  }

  override fun toString(): String = compactNotation()

  override fun compactNotation(): String =
    Joiner.on(GRADLE_PATH_SEPARATOR).skipNulls().join(
      Lists.newArrayList(group, name, version)
    )
}
