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
import com.android.tools.idea.gradle.model.IdeBaseArtifact

import com.android.tools.idea.gradle.project.sync.idea.ModuleUtil.getModuleName
import com.android.tools.idea.gradle.util.GradleUtil
import com.intellij.openapi.diagnostic.logger
import com.android.tools.idea.util.CommonAndroidUtil.LINKED_ANDROID_MODULE_GROUP
import com.android.tools.idea.util.LinkedAndroidModuleGroup
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.inspections.gradle.findAll
import org.jetbrains.kotlin.utils.addToStdlib.firstNotNullResult
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData

object ModuleUtil {
  @JvmStatic
  fun getModuleName(artifact: IdeBaseArtifact): String = getModuleName(artifact.name)

  @JvmStatic
  fun getModuleName(artifactName: IdeArtifactName): String {
    return when (artifactName) {
      IdeArtifactName.MAIN -> "main"
      IdeArtifactName.UNIT_TEST -> "unitTest"
      IdeArtifactName.ANDROID_TEST -> "androidTest"
    }
  }

  @JvmStatic
  fun Project.isModulePerSourceSetEnabled(): Boolean = GradleUtil.getGradleProjectSettings(this)?.isResolveModulePerSourceSet ?: false

  /**
   * Do not use this method outside of project system code.
   *
   * This method is used to link all modules that come from the same Gradle project.
   * It uses user data under the [LINKED_ANDROID_MODULE_GROUP] key to store an instance of [LinkedAndroidModuleGroup] on each module.
   *
   * @param dataToModuleMap a map of external system [ModuleData] to modules required in order to lookup a modules children
   */
  @JvmStatic
  fun DataNode<ModuleData>.linkAndroidModuleGroup(dataToModuleMap: (ModuleData) -> Module?) {
    val holderModule = dataToModuleMap(data) ?: return
    if (!holderModule.project.isModulePerSourceSetEnabled()) return
    var unitTestModule : Module? = null
    var androidTestModule : Module? = null
    var mainModule : Module? = null
    findAll(GradleSourceSetData.KEY).forEach {
      when(val sourceSetName = it.data.externalName.substringAfterLast(":")) {
        getModuleName(IdeArtifactName.MAIN) -> mainModule = dataToModuleMap(it.data)
        getModuleName(IdeArtifactName.UNIT_TEST) -> unitTestModule = dataToModuleMap(it.data)
        getModuleName(IdeArtifactName.ANDROID_TEST) -> androidTestModule = dataToModuleMap(it.data)
        else -> logger<ModuleUtil>().warn("Unknown artifact name: $sourceSetName")
      }
    }
    if (mainModule == null) {
      logger<ModuleUtil>().error("Unexpected - Android module is missing a main source set")
      return
    }
    val androidModuleGroup = LinkedAndroidModuleGroup(holderModule, mainModule!!, unitTestModule, androidTestModule)
    androidModuleGroup.getModules().forEach { module ->
      module?.putUserData(LINKED_ANDROID_MODULE_GROUP, androidModuleGroup)
    }
  }

  @JvmStatic
  fun DataNode<ModuleData>.linkAndroidModuleGroup(ideModelProvider: IdeModifiableModelsProvider) =
    linkAndroidModuleGroup { ideModelProvider.findIdeModule(it) }

  @JvmStatic
  fun Module.getAllLinkedModules() = getUserData(LINKED_ANDROID_MODULE_GROUP)?.getModules() ?: listOf(this)

  @JvmStatic
  fun Module.getMainModule() = getUserData(LINKED_ANDROID_MODULE_GROUP)?.main ?: this

  @JvmStatic
  fun Module.getUnitTestModule() = getUserData(LINKED_ANDROID_MODULE_GROUP)?.unitTest ?: this

  @JvmStatic
  fun Module.getAndroidTestModule() = getUserData(LINKED_ANDROID_MODULE_GROUP)?.androidTest ?: this

  @JvmStatic
  fun Module.getHolderModule() = getUserData(LINKED_ANDROID_MODULE_GROUP)?.holder ?: this

  /**
   * Utility method to find out if a module is derived from an Android Gradle project. This will return true
   * if the given module is the module representing any of the Android source sets (main/unitTest/androidTest) or the
   * holder module used as the parent of these source set modules.
   */
  @JvmStatic
  fun Module.isLinkedAndroidModule() = getUserData(LINKED_ANDROID_MODULE_GROUP) != null
}

fun String.removeSourceSetSuffixFromExternalProjectID() : String = IdeArtifactName.values().firstNotNullResult { artifactName ->
    val moduleName = getModuleName(artifactName)
    val suffix = ":$moduleName"
    if (this.endsWith(suffix)) {
      this.removeSuffix(suffix)
    } else {
      null
    }
  } ?: this