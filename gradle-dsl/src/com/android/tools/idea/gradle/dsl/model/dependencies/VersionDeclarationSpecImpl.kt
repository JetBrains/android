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

import com.android.tools.idea.gradle.dsl.api.dependencies.VersionDeclarationSpec
import com.google.common.base.Splitter

class VersionDeclarationSpecImpl internal constructor (
  private var require: String?,
  private var strictly: String?,
  private var prefer: String?
) : VersionDeclarationSpec {

  override fun getRequire(): String? = require

  override fun getStrictly(): String? = strictly

  override fun getPrefer(): String? = prefer

  fun setRequire(newRequire: String) {
    require = newRequire
  }

  fun setStrictly(newStrictly: String) {
    strictly = newStrictly
  }

  fun setPrefer(newPrefer: String?) {
    prefer = newPrefer
  }

  override fun toString() = "VersionDeclaration for <${compactNotation()}>"

  override fun compactNotation(): String? {
    return if (require != null && strictly != null) {
      return null
    }
    else if (require != null) {
      require!!
    }
    else if (strictly != null) {
      strictly!! + strictlySuffix + prefer.orEmpty()
    }
    else if (prefer?.isNotEmpty() == true) {
      "+$strictlySuffix$prefer"
    }
    else {
      null
    }
  }

  companion object {
    const val strictlySuffix = "!!"
    fun create(notation: String): VersionDeclarationSpecImpl? {
      return if (notation.contains(strictlySuffix)) {
        val segments = Splitter.on(strictlySuffix).trimResults().omitEmptyStrings().splitToList(notation)
        when (segments.size) {
          1 -> VersionDeclarationSpecImpl(null, segments[0], null)
          2 -> VersionDeclarationSpecImpl(null, segments[0], segments[1])
          else -> null
        }
      }
      else if (notation.isNotEmpty()) {
        VersionDeclarationSpecImpl(notation, null, null)
      }
      else null
    }
  }
}