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

interface IdeDependencyCore {
  val target: LibraryReference

  /**
   * List of direct dependencies for this dependency if known, for old versions of AGP (V1 models) this will be null.
   * For some dependencies (modules) this list of dependencies will be used as the classpath and as such we retain the order which
   * was provided by AGP.
   */
  val dependencies: List<Int>?
}

enum class ResolverType {
  GLOBAL,
  KMP_ANDROID
}

data class LibraryReference(val libraryIndex: Int, val resolverType: ResolverType = ResolverType.GLOBAL) : Serializable

interface IdeLibraryModelResolver {
  fun resolve(unresolved: IdeDependencyCore) : Sequence<IdeLibrary>
}
