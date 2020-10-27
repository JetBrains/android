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
import com.android.tools.idea.gradle.project.sync.Modules.createUniqueModuleId
import com.android.tools.idea.gradle.project.sync.idea.UsedInBuildAction
import org.gradle.tooling.model.gradle.BasicGradleProject

/**
 * The container class for Android module, containing its Android model, Variant models, and dependency modules.
 */
@UsedInBuildAction
class AndroidModule(
  val gradleProject: BasicGradleProject,
  val androidProject: AndroidProject,
  val nativeAndroidProject: NativeAndroidProject?
) {
  data class ModuleDependency(val id: String, val variant: String?, val abi: String?)

  private val _moduleDependencies: MutableList<ModuleDependency> = mutableListOf()
  private val variantsByName: MutableMap<String, Variant> = mutableMapOf()

  val variantGroup: VariantGroup = VariantGroup()
  val moduleDependencies: List<ModuleDependency> get() = _moduleDependencies

  fun containsVariant(variantName: String) = variantsByName.containsKey(variantName)

  fun addSelectedVariant(selectedVariant: Variant, abi: String?) {
    variantsByName[selectedVariant.name] = selectedVariant
    val artifact = selectedVariant.mainArtifact
    populateDependencies(artifact.dependencies, abi)
  }

  private fun populateDependencies(dependencies: Dependencies, abi: String?) = dependencies.libraries.forEach { library ->
    val project = library.project ?: return@forEach
    addModuleDependency(createUniqueModuleId(library?.buildId ?: "", project), library.projectVariant, abi)
  }

  private fun addModuleDependency(id: String, variant: String?, abi: String?) = _moduleDependencies.add(ModuleDependency(id, variant, abi))
}