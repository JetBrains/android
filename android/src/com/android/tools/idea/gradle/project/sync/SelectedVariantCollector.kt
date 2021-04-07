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
package com.android.tools.idea.gradle.project.sync

import com.android.tools.idea.gradle.project.facet.ndk.NdkFacet
import com.android.tools.idea.gradle.project.model.AndroidModuleModel
import com.android.tools.idea.gradle.project.model.NdkModuleModel
import com.android.tools.idea.gradle.util.getGradleProjectPath
import com.intellij.facet.ProjectFacetManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import org.jetbrains.android.facet.AndroidFacet

class SelectedVariantCollector(project: Project) {
  private val facetManager = ProjectFacetManager.getInstance(project)

  fun collectSelectedVariants(): SelectedVariants {
    return SelectedVariants(facetManager.getFacets(AndroidFacet.ID).mapNotNull { it.findSelectedVariant() }.associateBy { it.moduleId })
  }

  private fun AndroidFacet.findSelectedVariant(): SelectedVariant? {
    val moduleId = module.getModuleId() ?: return null
    val androidModuleModel = AndroidModuleModel.get(this)
    val variantDetails = androidModuleModel?.getSelectedVariantDetails()
    val ndkModuleModel = NdkModuleModel.get(module)
    val ndkFacet = NdkFacet.getInstance(module)
    if (ndkFacet != null && ndkModuleModel != null) {
      // Note, we lose ABI selection if cached models are not available.
      val (variant, abi) = ndkFacet.selectedVariantAbi ?: return null
      return SelectedVariant(moduleId, variant, abi, variantDetails)
    }
    return SelectedVariant(moduleId, properties.SELECTED_BUILD_VARIANT, null, variantDetails)
  }

  private fun AndroidModuleModel.getSelectedVariantDetails(): VariantDetails? {
    val selectedVariant = try {
      selectedVariant
    }
    catch (e: Exception) {
      Logger.getInstance(SelectedVariantCollector::class.java).error("Selected variant is not available for: $moduleName", e)
      return null
    }
    return createVariantDetailsFrom(androidProject.flavorDimensions, selectedVariant)
  }

  private fun Module.getModuleId(): String? {
    val gradleProjectPath = getGradleProjectPath() ?: return null
    return Modules.createUniqueModuleId(gradleProjectPath.projectRoot, gradleProjectPath.gradleProjectPath)
  }
}
