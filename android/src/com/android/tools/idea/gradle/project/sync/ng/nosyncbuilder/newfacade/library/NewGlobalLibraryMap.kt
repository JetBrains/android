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
package com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.newfacade.library

import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.interfaces.library.GlobalLibraryMap
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.interfaces.library.Library
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.misc.Level2GlobalLibraryMap
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.misc.Level2Library
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.misc.PathConverter
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.misc.toNew
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.proto.LibraryProto

data class NewGlobalLibraryMap(override val libraries: Map<String, Library>) : GlobalLibraryMap {
  constructor(globalLibraryMap: Level2GlobalLibraryMap) : this(
    globalLibraryMap.libraries.mapValues { it.value.toNew() }
  )

  constructor(androidLibraries: Map<String, Library>,
              javaLibraries: Map<String, Library>,
              nativeLibraries: Map<String, Library>,
              moduleDependencies: Map<String, Library>
  ) : this(androidLibraries + javaLibraries + nativeLibraries + moduleDependencies)

  constructor(proto: LibraryProto.GlobalLibraryMap, converter: PathConverter) : this(
    proto.androidLibrariesMap.mapValues { NewAndroidLibrary(it.value, converter) },
    proto.javaLibrariesMap.mapValues { NewJavaLibrary(it.value, converter) },
    proto.nativeLibrariesMap.mapValues { NewNativeLibrary(it.value, converter) },
    proto.moduleDependenciesMap.mapValues { NewModuleDependency(it.value) }
  )
}
