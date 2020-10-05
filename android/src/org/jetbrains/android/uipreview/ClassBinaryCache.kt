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
package org.jetbrains.android.uipreview

import java.net.URL

/**
 * Interface to record and retrieve class binary data.
 */
interface ClassBinaryCache {
  /**
   * Return class binary data or null if unknown.
   */
  fun get(fqcn: String): ByteArray?

  /**
   * Record class binary [data] with the associated [libraryPath].
   */
  fun put(fqcn: String, libraryPath: String, data: ByteArray)

  /**
   * Sets [paths] of the dependencies from which classes are cached.
   */
  fun setDependencies(paths: Collection<String>)

  companion object {
    @JvmField
    val NO_CACHE = object : ClassBinaryCache {
      override fun get(fqcn: String): ByteArray? = null
      override fun put(fqcn: String, libraryPath: String, data: ByteArray) { }
      override fun setDependencies(paths: Collection<String>) { }
    }
  }
}