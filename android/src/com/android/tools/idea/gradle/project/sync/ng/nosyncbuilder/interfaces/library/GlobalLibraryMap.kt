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
package com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.interfaces.library

import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.interfaces.androidproject.AndroidProject
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.misc.PathConverter
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.proto.LibraryProto

/**
 * Global map of all the [Library] instances used in a single or multi-module gradle project.
 *
 * This is a separate model to query (the same way [AndroidProject] is queried). It must
 * be queried after all the models have been queried for their [AndroidProject].
 *
 */
interface GlobalLibraryMap {
  /** The map of external libraries used by all the variants in the module. */
  val libraries: Map<String, Library>

  // TODO: create Map.filterIsInstance() extension function and use it instead (or prove that it's too hard)?
  fun toProto(converter: PathConverter): LibraryProto.GlobalLibraryMap = LibraryProto.GlobalLibraryMap.newBuilder()
    .putAllAndroidLibraries(libraries.filterValues { it is AndroidLibrary }.mapValues { (it.value as AndroidLibrary).toProto(converter) })
    .putAllJavaLibraries(libraries.filterValues { it is JavaLibrary }.mapValues { (it.value as JavaLibrary).toProto(converter) })
    .putAllNativeLibraries(libraries.filterValues { it is NativeLibrary }.mapValues { (it.value as NativeLibrary).toProto(converter) })
    .putAllModuleDependencies(libraries.filterValues { it is ModuleDependency }.mapValues { (it.value as ModuleDependency).toProto() })
    .build()!!
}
