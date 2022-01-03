/*
 * Copyright (C) 2022 The Android Open Source Project
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

interface IdeDependency<T: IdeLibrary> {
  val target: T
}

interface IdeArtifactDependency<T: IdeArtifactLibrary> : IdeDependency<T> {
  /**
   * Returns whether the dependency is on the compile class path but is not on the runtime class
   * path.
   */
  val isProvided: Boolean
}

interface IdeAndroidLibraryDependency: IdeArtifactDependency<IdeAndroidLibrary>
interface IdeJavaLibraryDependency: IdeArtifactDependency<IdeJavaLibrary>

interface IdeModuleDependency: IdeDependency<IdeModuleLibrary> {
  // NOTE: Target properties are exposed directly. This is to make it possible to drop the notion of `IdeModuleLibrary` once we can move
  //       `lintJar` property elsewhere.
  /**
   * Returns the gradle path.
   */
  val projectPath: String get() = target.projectPath

  /**
   * Returns an optional variant name if the consumed artifact of the library is associated to
   * one.
   */
  val variant: String? get() = target.variant

  /**
   * Returns the build id.
   */
  val buildId: String get() = target.buildId

  /**
   * Returns the sourceSet associated with the library.
   */
  val sourceSet: IdeModuleSourceSet get() = target.sourceSet
}