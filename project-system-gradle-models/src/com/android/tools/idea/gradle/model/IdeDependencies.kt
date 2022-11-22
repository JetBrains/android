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
  val androidLibraries: Collection<IdeAndroidLibraryDependency>

  /**
   * Returns the Java library dependencies, both direct and transitive dependencies.
   *
   * @return the list of libraries of type LIBRARY_JAVA.
   */
  val javaLibraries: Collection<IdeJavaLibraryDependency>

  /**
   * Returns the Module dependencies.
   *
   * @return the list of libraries of type LIBRARY_MODULE.
   */
  val moduleDependencies: Collection<IdeModuleDependency>

  /**
   * Returns the unknown dependencies.
   *
   * @return the list of libraries of all other types.
   */
  val unknownDependencies: Collection<IdeUnknownDependency>
}
