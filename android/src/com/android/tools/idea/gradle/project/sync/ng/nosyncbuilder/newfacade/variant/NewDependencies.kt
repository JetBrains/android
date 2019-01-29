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
package com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.newfacade.variant

import com.android.ide.common.gradle.model.level2.IdeDependencies
import com.android.ide.common.gradle.model.level2.IdeModuleLibrary
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.interfaces.library.ModuleDependency
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.interfaces.variant.Dependencies
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.misc.OldNativeLibrary
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.newfacade.library.NewModuleDependency
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.proto.VariantProto

data class NewDependencies(
  override val androidLibraries: Collection<String>,
  override val javaLibraries: Collection<String>,
  override val nativeLibraries: Collection<String>,
  override val moduleDependencies: Collection<ModuleDependency>
) : Dependencies {
  constructor(oldDependencies: IdeDependencies, oldNativeLibraries: Collection<OldNativeLibrary>) : this(
    oldDependencies.androidLibraries.map { it.artifactAddress },
    oldDependencies.javaLibraries.map { it.artifactAddress },
    listOf(), // TODO: oldNativeLibraries.map {NewNativeLibrary(oldNativeLibraries)}
    oldDependencies.moduleDependencies.map {
      NewModuleDependency(it as IdeModuleLibrary)
    }
  )

  constructor(proto: VariantProto.Dependencies) : this(
    proto.androidLibrariesList,
    proto.javaLibrariesList,
    proto.nativeLibrariesList,
    proto.moduleDependenciesList.map {
      NewModuleDependency(it)
    }
  )
}