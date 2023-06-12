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
package com.android.tools.idea.gradle.model

import java.io.File

/**
 * Root of the hierarchy of interfaces representing unresolved IDE libraries as seen by the Android Gradle plugin.
 * They do not contain any transforms performed as part of the IDEs inter-module dependency resolution. This resolution can
 * change unresolved module libraries into one or more java libraries or resolved modules if required (ie. for KMP).
 *
 * "Resolved" in this case refers to whether the library has been mapped to the correct IDE objects, this is relevant only
 * for module libraries.
 *
 * This interface should not be exposed outside the Gradle import code, see [IdeLibrary] for the resolved counterparts which
 * clients should be able to access.
 */
sealed interface IdeUnresolvedLibrary {
  /**
   * Returns the location of the lint jar. The file may not point to an existing file.
   *
   * Only valid for Android Library
   */
  val lintJar: File?
}

sealed interface IdeUnresolvedArtifactLibrary : IdeUnresolvedLibrary

interface IdeUnresolvedAndroidLibrary : IdeUnresolvedArtifactLibrary, IdeAndroidLibrary

interface IdeUnresolvedJavaLibrary : IdeUnresolvedArtifactLibrary, IdeJavaLibrary

interface IdeUnresolvedUnknownLibrary : IdeUnresolvedLibrary, IdeUnknownLibrary

interface IdeUnresolvedModuleLibrary : IdeUnresolvedLibrary {
  /**
   * Returns the gradle path.
   */
  val projectPath: String

  /**
   * Returns an optional variant name if the consumed artifact of the library is associated to
   * one.
   */
  val variant: String?

  /**
   * Returns the build id.
   */
  val buildId: String

  /**
   * The artifact that this module dependency is targeting, this is only populated when V2 models are used
   */
  val artifact: File
}

interface IdePreResolvedModuleLibrary : IdeUnresolvedLibrary {
  /**
   * Returns the gradle path.
   */
  val projectPath: String

  /**
   * Returns an optional variant name if the consumed artifact of the library is associated to
   * one.
   */
  val variant: String?

  /**
   * Returns the build id.
   */
  val buildId: String

  /**
   * Returns the sourceSet associated with the library.
   */
  val sourceSet: IdeModuleSourceSet
}
