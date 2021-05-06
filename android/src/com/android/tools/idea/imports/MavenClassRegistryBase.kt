/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.imports

/**
 * Lookup from class names to maven.google.com artifacts.
 */
abstract class MavenClassRegistryBase {
  /**
   * Library for each of the GMaven artifact.
   *
   * @property artifact maven coordinate: groupId:artifactId, please note version is not included here.
   * @property packageName fully qualified package name which is used for the following import purposes.
   * @property version the version of the [artifact].
   */
  data class Library(val artifact: String, val packageName: String, val version: String? = null)

  /**
   * Given a class name, returns the likely collection of [Library] objects for the following quick fixes purposes.
   */
  abstract fun findLibraryData(className: String, useAndroidX: Boolean): Collection<Library>

  /**
   * For the given runtime artifact, if Kotlin is the adopted language, the corresponding ktx library is provided.
   */
  abstract fun findKtxLibrary(artifact: String): String?

  /**
   * For the given runtime artifact, if it also requires an annotation processor, provide it
   */
  fun findAnnotationProcessor(artifact: String): String? {
    return when (artifact) {
      "androidx.room:room-runtime",
      "android.arch.persistence.room:runtime" -> "android.arch.persistence.room:compiler"
      "androidx.remotecallback:remotecallback" -> "androidx.remotecallback:remotecallback-processor"
      else -> null
    }
  }
}