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

import java.io.Serializable

@Deprecated("all subclasses and usages will be removed, work with IdeLibrary and subclasses instead")
sealed interface IdeDependency<T>

@Deprecated("all subclasses and usages will be removed, work with IdeLibrary and subclasses instead")
sealed interface IdeArtifactDependency<T : IdeArtifactLibrary> : IdeDependency<T> {
  val target: T
}

interface IdeDependencyCore {
  val target: LibraryReference

  /**
   * List of direct dependencies for this dependency if known, for old versions of AGP (V1 models) this will be null.
   * For some dependencies (modules) this list of dependencies will be used as the classpath and as such we retain the order which
   * was provided by AGP.
   */
  val dependencies: List<Int>?
}

@Deprecated("all subclasses and usages will be removed, work with IdeLibrary and subclasses instead")
interface IdeAndroidLibraryDependency : IdeArtifactDependency<IdeAndroidLibrary>

@Deprecated("all subclasses and usages will be removed, work with IdeLibrary and subclasses instead")
interface IdeJavaLibraryDependency : IdeArtifactDependency<IdeJavaLibrary>

data class LibraryReference(val libraryIndex: Int) : Serializable

interface IdeLibraryModelResolver {
  @Deprecated("IdeDependency and subclasses will be removed", ReplaceWith("this.resolve(unresolved)"))
  fun resolveAndroidLibrary(unresolved: IdeDependencyCore): Sequence<IdeAndroidLibraryDependency>
  @Deprecated("IdeDependency and subclasses will be removed", ReplaceWith("this.resolve(unresolved)"))
  fun resolveJavaLibrary(unresolved: IdeDependencyCore): Sequence<IdeJavaLibraryDependency>
  @Deprecated("IdeDependency and subclasses will be removed", ReplaceWith("this.resolve(unresolved)"))
  fun resolveModule(unresolved: IdeDependencyCore): Sequence<IdeModuleDependency>
  @Deprecated("IdeDependency and subclasses will be removed", ReplaceWith("this.resolve(unresolved)"))
  fun resolveUnknownLibrary(unresolved: IdeDependencyCore): Sequence<IdeUnknownDependency>
  fun resolve(unresolved: IdeDependencyCore) : Sequence<IdeLibrary>
}

@Deprecated("all subclasses and usages will be removed, work with IdeLibrary and subclasses instead")
interface IdeModuleDependency : IdeDependency<IdeModuleLibrary> {
  val target: IdeModuleLibrary
}

@Deprecated("all subclasses and usages will be removed, work with IdeLibrary and subclasses instead")
interface IdeUnknownDependency: IdeDependency<IdeUnknownLibrary> {
  val target: IdeUnknownLibrary
}

/**
 * Returns the gradle path.
 */
val IdeModuleDependency.projectPath: String get() = target.projectPath

/**
 * Returns an optional variant name if the consumed artifact of the library is associated to
 * one.
 */
val IdeModuleDependency.variant: String? get() = target.variant

/**
 * Returns the build id.
 */
val IdeModuleDependency.buildId: String get() = target.buildId

/**
 * Returns the sourceSet associated with the library.
 */
val IdeModuleDependency.sourceSet: IdeModuleSourceSet get() = target.sourceSet
