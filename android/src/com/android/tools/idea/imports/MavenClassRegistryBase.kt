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

import com.android.tools.idea.projectsystem.DependencyType
import com.intellij.openapi.fileTypes.FileType

/** Registry provides lookup service for Google Maven Artifacts when asked. */
abstract class MavenClassRegistryBase {
  /**
   * Library data for importing a specific item (class or function) with its GMaven artifact.
   *
   * @property artifact maven coordinate: groupId:artifactId, please note version is not included
   *   here.
   * @property importedItemFqName fully-qualified name of the item to import. Can be a class or
   *   function name.
   * @property importedItemPackageName package name of the item to import.
   * @property version the version of the [artifact].
   */
  data class LibraryImportData(
    val artifact: String,
    val importedItemFqName: String,
    val importedItemPackageName: String,
    val version: String? = null
  )

  /** Coordinate for Google Maven artifact. */
  data class Coordinate(val groupId: String, val artifactId: String, val version: String)

  /**
   * Given an unresolved name, returns the likely collection of
   * [MavenClassRegistryBase.LibraryImportData] objects for the maven.google.com artifacts
   * containing a class or function matching the [name] and [receiverType].
   *
   * @param name simple or fully-qualified name typed by the user. May correspond to a class name
   *   (any files) or a top-level Kotlin function name (Kotlin files only).
   * @param receiverType the fully-qualified name of the receiver type, if any, or `null` for no
   *   receiver.
   */
  abstract fun findLibraryData(
    name: String,
    receiverType: String?,
    useAndroidX: Boolean,
    completionFileType: FileType?
  ): Collection<LibraryImportData>

  /**
   * Given an unresolved name, returns the likely collection of
   * [MavenClassRegistryBase.LibraryImportData] objects for the maven.google.com artifacts
   * containing a class or function matching the [name].
   *
   * @param name simple or fully-qualified name typed by the user. May correspond to a class name
   *   (any files) or a top-level Kotlin function name, including extension functions (Kotlin files
   *   only).
   */
  abstract fun findLibraryDataAnyReceiver(
    name: String,
    useAndroidX: Boolean,
    completionFileType: FileType?
  ): Collection<LibraryImportData>

  /**
   * For the given runtime artifact, if Kotlin is the adopted language, the corresponding ktx
   * library is provided.
   */
  abstract fun findKtxLibrary(artifact: String): String?

  /** Returns a collection of [Coordinate]. */
  abstract fun getCoordinates(): Collection<Coordinate>

  /** For the given runtime artifact, if it also requires an annotation processor, provide it. */
  fun findAnnotationProcessor(artifact: String): String? {
    return when (artifact) {
      "androidx.room:room-runtime",
      "android.arch.persistence.room:runtime" -> "android.arch.persistence.room:compiler"
      "androidx.remotecallback:remotecallback" -> "androidx.remotecallback:remotecallback-processor"
      else -> null
    }
  }

  /**
   * For the given artifact, if it also requires extra artifacts for proper functionality, provide
   * it.
   *
   * This is to handle those special cases. For example, for an unresolved symbol "@Preview",
   * "androidx.compose.ui:ui-tooling-preview" is one of the suggested artifacts to import based on
   * the extracted contents from the GMaven index file. However, this is not enough
   * -"androidx.compose.ui:ui-tooling" should be added on instead. So we just provide both in the
   * end.
   */
  fun findExtraArtifacts(artifact: String): Map<String, DependencyType> {
    return when (artifact) {
      "androidx.compose.ui:ui-tooling-preview" ->
        mapOf("androidx.compose.ui:ui-tooling" to DependencyType.DEBUG_IMPLEMENTATION)
      else -> emptyMap()
    }
  }
}
