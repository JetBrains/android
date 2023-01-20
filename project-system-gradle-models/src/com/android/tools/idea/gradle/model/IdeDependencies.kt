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
package com.android.tools.idea.gradle.model

interface IdeDependencies {
  /**
   * Returns the Android library dependencies, both direct and transitive.
   *
   * @return the list of libraries of type LIBRARY_ANDROID.
   */
  @Deprecated("does not respect classpath order", ReplaceWith("this.libraries"))
  val androidLibraries: Collection<IdeAndroidLibraryDependency>

  /**
   * Returns the Java library dependencies, both direct and transitive dependencies.
   *
   * @return the list of libraries of type LIBRARY_JAVA.
   */
  @Deprecated("does not respect classpath order", ReplaceWith("this.libraries"))
  val javaLibraries: Collection<IdeJavaLibraryDependency>

  /**
   * Returns the Module dependencies.
   *
   * @return the list of libraries of type LIBRARY_MODULE.
   */
  @Deprecated("does not respect classpath order", ReplaceWith("this.libraries"))
  val moduleDependencies: Collection<IdeModuleDependency>

  /**
   * Returns the unknown dependencies.
   *
   * @return the list of libraries of all other types.
   */
  @Deprecated("does not respect classpath order", ReplaceWith("this.libraries"))
  val unknownDependencies: Collection<IdeUnknownDependency>


  /**
   * Returns the list of all dependencies, both direct and transitive
   *
   * @return the list of libraries of all types.
   */
  val libraries: List<IdeLibrary>

  /**
   * Returns the list of all dependencies, both direct and transitive as [IdeDependencyCore]s.
   * These contain an unresolved library reference [IdeDependencyCore.target] which should be resolved with a [IdeLibraryModelResolver].
   * They also contain a list of indexes of their dependencies, these are indices back into this list of dependencies.
   */
  val unresolvedDependencies: List<IdeDependencyCore>

  /**
   * Utility method to provide easy access to a resolver without having to re-create one from the library table.
   */
  val resolver: IdeLibraryModelResolver
}
