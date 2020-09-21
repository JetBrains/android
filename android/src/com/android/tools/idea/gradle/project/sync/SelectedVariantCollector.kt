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

import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet
import com.android.tools.idea.gradle.project.facet.ndk.NdkFacet.Companion.getInstance
import com.android.tools.idea.gradle.project.model.NdkModuleModel.Companion.get
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import org.jetbrains.android.facet.AndroidFacet
import java.io.File
import java.io.IOException
import java.io.Serializable
import java.util.HashMap

class SelectedVariants : Serializable {
  // Key: module's Gradle ID, value: selected variant.
  /**
   * @see com.android.tools.idea.gradle.project.sync.Modules.createUniqueModuleId
   */
  private val mySelectedVariantByModule: MutableMap<String, String> = HashMap()

  // Map from module id to selected abi name, only available for native modules.
  private val mySelectedAbiByModule: MutableMap<String, String> = HashMap()

  fun addSelectedVariant(moduleId: String, variantName: String, abiName: String?) {
    mySelectedVariantByModule[moduleId] = variantName
    if (abiName != null) {
      mySelectedAbiByModule[moduleId] = abiName
    }
  }

  fun size(): Int {
    return mySelectedVariantByModule.size
  }

  fun getSelectedVariant(moduleId: String): String? {
    return mySelectedVariantByModule[moduleId]
  }

  fun getSelectedAbi(moduleId: String): String? {
    return mySelectedAbiByModule[moduleId]
  }

  val selectedVariantsByModule: Map<String, String> get() = HashMap(mySelectedVariantByModule)
  val selectedAbisByModule: Map<String, String> get() = HashMap(mySelectedAbiByModule)
}

class SelectedVariantCollector(private val project: Project) {
  fun collectSelectedVariants(): SelectedVariants {
    val selectedVariants = SelectedVariants()
    for (module in ModuleManager.getInstance(project).modules) {
      val variant = findSelectedVariant(module)
      if (variant != null) {
        selectedVariants.addSelectedVariant(variant.moduleId, variant.variantName, variant.abiName)
      }
    }
    return selectedVariants
  }

  private fun findSelectedVariant(module: Module): SelectedVariant? {
    val gradleFacet = GradleFacet.getInstance(module)
    if (gradleFacet != null) {
      val rootProjectPath: String
      rootProjectPath = try {
        val path = ExternalSystemApiUtil.getExternalRootProjectPath(module) ?: return null
        File(path).canonicalPath
      }
      catch (e: IOException) {
        Logger.getInstance(SelectedVariantCollector::class.java).error(e)
        return null
      }
      val rootFolder = File(rootProjectPath)
      val projectPath = gradleFacet.configuration.GRADLE_PROJECT_PATH
      val androidFacet = AndroidFacet.getInstance(module)
      val ndkModuleModel = get(module)
      val ndkFacet = getInstance(module)
      if (ndkFacet != null && ndkModuleModel != null) {
        val (variant, abi) = ndkFacet.selectedVariantAbi ?: return null
        return SelectedVariant(rootFolder, projectPath, variant, abi)
      }
      if (androidFacet != null) {
        return SelectedVariant(rootFolder, projectPath, androidFacet.properties.SELECTED_BUILD_VARIANT, null)
      }
    }
    return null
  }

  internal class SelectedVariant(rootFolderPath: File, gradlePath: String, val variantName: String, val abiName: String?) {
    val moduleId: String = Modules.createUniqueModuleId(rootFolderPath, gradlePath)
  }
}