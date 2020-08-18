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
import com.android.builder.model.level2.DependencyGraphs
import com.android.tools.idea.gradle.project.sync.Modules.createUniqueModuleId
import com.android.tools.idea.gradle.project.sync.idea.UsedInBuildAction
import org.gradle.tooling.model.gradle.BasicGradleProject
import java.util.regex.Pattern

/**
 * The container class for Android module, containing its Android model, Variant models, and dependency modules.
 */
@UsedInBuildAction
class AndroidModule(
  val gradleProject: BasicGradleProject,
  val androidProject: AndroidProject,
  val nativeAndroidProject: NativeAndroidProject?
) {
  companion object {
    // Format of ArtifactAddress for module library, BuildId@@GradlePath::Variant, the "::Variant" part is optional if variant is null.
    val MODULE_ARTIFACT_ADDRESS_PATTERN: Pattern = Pattern.compile("([^@]*)@@(.[^:]*)(::(.*))?")
  }

  data class ModuleDependency(val id: String, val variant: String?, val abi: String?)

  private val _moduleDependencies: MutableList<ModuleDependency> = mutableListOf()
  private val variantsByName: MutableMap<String, Variant> = mutableMapOf()

  val variantGroup: VariantGroup = VariantGroup()
  val moduleDependencies: List<ModuleDependency> get() = _moduleDependencies

  fun containsVariant(variantName: String) = variantsByName.containsKey(variantName)

  fun addSelectedVariant(selectedVariant: Variant, abi: String?) {
    variantsByName[selectedVariant.name] = selectedVariant
    val artifact = selectedVariant.mainArtifact
    val dependencies = artifact.dependencies
    if (dependencies.libraries.isEmpty()) {
      // Level4 DependencyGraphs model.
      // DependencyGraph was added in AGP 3.0. If the code gets here, means current AGP is 3.2+, no try/catch needed.
      populateDependencies(artifact.dependencyGraphs, abi)
    }
    else {
      // Level1 Dependencies model.
      populateDependencies(dependencies, abi)
    }
  }

  private fun populateDependencies(dependencies: Dependencies, abi: String?) = dependencies.libraries.forEach { library ->
    val project = library.project ?: return@forEach
    addModuleDependency(createUniqueModuleId(library?.buildId ?: "", project), library.projectVariant, abi)
  }

  private fun populateDependencies(dependencyGraphs: DependencyGraphs,
                                   abi: String?) = dependencyGraphs.compileDependencies.forEach { item ->
    val matcher = MODULE_ARTIFACT_ADDRESS_PATTERN.matcher(item.artifactAddress)
    if (matcher.matches()) {
      val buildId = matcher.group(1)
      val project = matcher.group(2)
      if (buildId != null && project != null) {
        addModuleDependency(createUniqueModuleId(buildId, project), matcher.group(4), abi)
      }
    }
  }

  private fun addModuleDependency(id: String, variant: String?, abi: String?) = _moduleDependencies.add(ModuleDependency(id, variant, abi))
}