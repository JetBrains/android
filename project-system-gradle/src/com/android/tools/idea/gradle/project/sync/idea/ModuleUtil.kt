/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.idea

import com.android.tools.idea.gradle.model.IdeArtifactName
import com.android.tools.idea.gradle.model.IdeArtifactName.Companion.toWellKnownSourceSet
import com.android.tools.idea.gradle.model.IdeModuleSourceSet
import com.android.tools.idea.gradle.model.impl.IdeModuleSourceSetImpl
import com.android.tools.idea.gradle.project.sync.idea.data.model.KotlinMultiplatformAndroidSourceSetType
import com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys
import com.android.tools.idea.projectsystem.LINKED_ANDROID_MODULE_GROUP
import com.android.tools.idea.projectsystem.LinkedAndroidModuleGroup
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import org.jetbrains.kotlin.idea.gradle.configuration.KotlinTargetData
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData

object ModuleUtil {
  @JvmStatic
  fun getModuleName(artifactName: IdeArtifactName): String {
    return artifactName.toWellKnownSourceSet().sourceSetName
  }

  /**
   * Do not use this method outside of project system code.
   *
   * This method is used to link all modules that come from the same Gradle project.
   * It uses user data under the [LINKED_ANDROID_MODULE_GROUP] key to store an instance of [LinkedAndroidModuleGroup] on each module.
   *
   * @param dataToModuleMap a map of external system [ModuleData] to modules required in order to lookup a modules children
   * @return if android module group was successfully linked
   */
  @JvmStatic
  fun DataNode<out ModuleData>.linkAndroidModuleGroup(dataToModuleMap: (ModuleData) -> Module?): Boolean {
    val holderModule = dataToModuleMap(data) ?: return false
    // Clear the links, this prevents old links from being used
    holderModule.putUserData(LINKED_ANDROID_MODULE_GROUP, null)

    val possibleSourceSetNames = if (ExternalSystemApiUtil.find(this, KotlinTargetData.KEY)?.data?.externalName == "android") {
      val kotlinMultiplatformAndroidSourceSetData = ExternalSystemApiUtil.findParent(this, ProjectKeys.PROJECT)?.let {
        ExternalSystemApiUtil.find(it, AndroidProjectKeys.KOTLIN_MULTIPLATFORM_ANDROID_SOURCE_SETS_TABLE)
      }?.data?.sourceSetsByGradleProjectPath?.get(this.data.id)

      mapOf(
        kotlinMultiplatformAndroidSourceSetData?.get(KotlinMultiplatformAndroidSourceSetType.MAIN) to IdeArtifactName.MAIN,
        kotlinMultiplatformAndroidSourceSetData?.get(KotlinMultiplatformAndroidSourceSetType.UNIT_TEST) to IdeArtifactName.UNIT_TEST,
        kotlinMultiplatformAndroidSourceSetData?.get(KotlinMultiplatformAndroidSourceSetType.ANDROID_TEST) to IdeArtifactName.ANDROID_TEST,
      )
    }
    else {
      IdeArtifactName.values().associate { getModuleName(it) to it }
    }

    val ideArtifactNameToModule = ExternalSystemApiUtil.findAll(this, GradleSourceSetData.KEY).mapNotNull {
      val sourceSetName = it.data.externalName.substringAfterLast(":")
      val ideArtifactName = possibleSourceSetNames[sourceSetName] ?: return@mapNotNull null
      ideArtifactName to dataToModuleMap(it.data)
    }.toMap()

    val mainModule = ideArtifactNameToModule[IdeArtifactName.MAIN] ?: run {
      logger<ModuleUtil>().info("Android module (${holderModule.name}) is missing a main source set")
      return false
    }

    val androidModuleGroup = LinkedAndroidModuleGroup(holderModule, mainModule, ideArtifactNameToModule[IdeArtifactName.UNIT_TEST],
                                                      ideArtifactNameToModule[IdeArtifactName.ANDROID_TEST],
                                                      ideArtifactNameToModule[IdeArtifactName.TEST_FIXTURES],
                                                      ideArtifactNameToModule[IdeArtifactName.SCREENSHOT_TEST])
    androidModuleGroup.getModules().forEach { module ->
      module.putUserData(LINKED_ANDROID_MODULE_GROUP, androidModuleGroup)
    }
    return true
  }

  @JvmStatic
  fun DataNode<ModuleData>.linkAndroidModuleGroup(ideModelProvider: IdeModifiableModelsProvider) =
    linkAndroidModuleGroup { ideModelProvider.findIdeModule(it) }

  @JvmStatic
  fun Module.unlinkAndroidModuleGroup() {
    val androidModuleGroup = getUserData(LINKED_ANDROID_MODULE_GROUP) ?: return
    androidModuleGroup.getModules().filter { !it.isDisposed }.forEach { it.putUserData(LINKED_ANDROID_MODULE_GROUP, null) }
  }

  @JvmStatic
  fun GradleSourceSetData.getIdeModuleSourceSet(): IdeModuleSourceSet = IdeModuleSourceSetImpl.wellKnownOrCreate(moduleName)
}