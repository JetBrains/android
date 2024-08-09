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
import com.android.tools.idea.gradle.project.model.GradleAndroidModel
import com.android.tools.idea.gradle.project.model.NdkModuleModel
import com.android.tools.idea.projectsystem.getAndroidFacets
import com.android.tools.idea.projectsystem.gradle.getGradleProjectPath
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import org.jetbrains.android.facet.AndroidFacet

class SelectedVariantCollector(private val project: Project) {

  fun collectSelectedVariants(): SelectedVariants {
    return SelectedVariants(
      project.getAndroidFacets().mapNotNull { it.findSelectedVariant() }.associateBy { it.moduleId }
    )
  }

}

fun getSelectedVariantDetails(androidModel: GradleAndroidModel, selectedAbi: String?): VariantDetails? {
  val selectedVariant = try {
    androidModel.selectedVariantCore
  }
  catch (e: Exception) {
    Logger.getInstance(SelectedVariantCollector::class.java).error("Selected variant is not available for: ${androidModel.moduleName}", e)
    return null
  }
  return createVariantDetailsFrom(androidModel.androidProject.flavorDimensions, selectedVariant, selectedAbi)
}

internal fun AndroidFacet.findSelectedVariant(): SelectedVariant? {
  val module = module
  val moduleId = module.getModuleIdForSyncRequest() ?: return null
  val gradleAndroidModel = GradleAndroidModel.get(this)
  val ndkModuleModel = NdkModuleModel.get(module)
  val variantDetails = gradleAndroidModel?.let { getSelectedVariantDetails(gradleAndroidModel, ndkModuleModel?.selectedAbi) }
  val ndkFacet = NdkFacet.getInstance(module)
  if (ndkFacet != null && ndkModuleModel != null) {
    // Note, we lose ABI selection if cached models are not available.
    val (variant, abi) = ndkFacet.configuration.selectedVariantAbi ?: return null
    return SelectedVariant(moduleId, variant, abi, variantDetails)
  }
  return SelectedVariant(moduleId, properties.SELECTED_BUILD_VARIANT, null, variantDetails)
}


@JvmName("getModuleIdForSyncRequest")
fun Module.getModuleIdForSyncRequest(): String? {
  val gradleProjectPath = getGradleProjectPath()
  if (gradleProjectPath != null) {
    return Modules.createUniqueModuleId(gradleProjectPath.buildRoot, gradleProjectPath.path)
  }

  Logger.getInstance(SelectedVariantCollector::class.java).warn("Module ${name} is not a Gradle module")
  return null
}
