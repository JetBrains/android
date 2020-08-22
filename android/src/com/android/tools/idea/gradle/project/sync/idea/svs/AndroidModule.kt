/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.idea.svs

import com.android.builder.model.AndroidProject
import com.android.builder.model.Dependencies
import com.android.builder.model.NativeAndroidProject
import com.android.builder.model.Variant
import com.android.builder.model.v2.models.ndk.NativeModule
import com.android.tools.idea.gradle.project.sync.Modules.createUniqueModuleId
import com.android.tools.idea.gradle.project.sync.idea.UsedInBuildAction
import org.gradle.tooling.model.gradle.BasicGradleProject

/**
 * The container class for Android module, containing its Android model, Variant models, and dependency modules.
 */
@UsedInBuildAction
class AndroidModule private constructor(
  val gradleProject: BasicGradleProject,
  val androidProject: AndroidProject,
  /** Old V1 model. It's only set if [NaiveModule] is not set. */
  val nativeAndroidProject: NativeAndroidProject?,
  /** New V2 model. It's only set if [nativeAndroidProject] is not set. */
  val nativeModule: NativeModule?
) {

  /** Constructs from V2 [NativeModule]. */
  constructor(gradleProject: BasicGradleProject,
              androidProject: AndroidProject,
              nativeModule: NativeModule?) : this(gradleProject, androidProject, null, nativeModule)

  /** Constructs from V1 [NativeAndroidProject]. */
  constructor(gradleProject: BasicGradleProject,
              androidProject: AndroidProject,
              nativeAndroidProject: NativeAndroidProject?) : this(gradleProject, androidProject, nativeAndroidProject, null)


  val variantGroup: VariantGroup = VariantGroup()
  val hasNative: Boolean = nativeAndroidProject != null || nativeModule != null
}

data class ModuleDependency(val id: String, val variant: String?, val abi: String?)
fun getModuleDependencies(dependencies: Dependencies, abi: String?): List<ModuleDependency> {
  return dependencies.libraries.mapNotNull { library ->
    val project = library.project ?: return@mapNotNull null
    ModuleDependency(createUniqueModuleId(library?.buildId ?: "", project), library.projectVariant, abi)
  }
}
