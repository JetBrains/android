/*
 * Copyright (C) 2016 The Android Open Source Project
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
@file:JvmName("ModuleSetup")
package com.android.tools.idea.gradle.project.sync.setup.post

import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet
import com.android.tools.idea.gradle.project.model.AndroidModuleModel
import com.android.tools.idea.project.AndroidRunConfigurations
import com.android.tools.idea.testartifacts.scopes.GradleTestArtifactSearchScopes
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.kotlin.idea.configuration.compilerArgumentsBySourceSet
import org.jetbrains.kotlin.idea.configuration.configureFacetByCompilerArguments
import org.jetbrains.kotlin.idea.configuration.sourceSetName
import org.jetbrains.kotlin.idea.facet.KotlinFacet

fun setUpModules(project: Project) {
  project.fixRunConfigurations()
  GradleTestArtifactSearchScopes.initializeScopes(project)
  ModuleManager.getInstance(project).modules.forEach { module ->
    recordLastAgpVersion(module)
    setupAndroidRunConfiguration(module)
    setupKotlinOptionsOnFacet(module)
  }
}

// Added due to KT-19958
private fun setupKotlinOptionsOnFacet(module: Module) {
  val facet = AndroidFacet.getInstance(module) ?: return
  val androidModel = AndroidModuleModel.get(facet) ?: return
  val sourceSetName = androidModel.selectedVariant.name
  if (module.sourceSetName == sourceSetName) return
  val argsInfo = module.compilerArgumentsBySourceSet?.get(sourceSetName) ?: return
  val kotlinFacet = KotlinFacet.get(module) ?: return
  module.sourceSetName = sourceSetName
  configureFacetByCompilerArguments(kotlinFacet, argsInfo, null)
}

private fun setupAndroidRunConfiguration(module: Module) {
  val facet = AndroidFacet.getInstance(module)
  if (facet != null && facet.configuration.isAppProject) {
    AndroidRunConfigurations.getInstance().createRunConfiguration(facet)
  }
}

private fun recordLastAgpVersion(module: Module) {
  GradleFacet.getInstance(module)?.configuration?.let { facet ->
    facet.LAST_SUCCESSFUL_SYNC_AGP_VERSION = facet.LAST_KNOWN_AGP_VERSION
  }
}
