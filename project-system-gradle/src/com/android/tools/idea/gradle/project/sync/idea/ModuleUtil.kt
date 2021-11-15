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
import com.android.tools.idea.gradle.model.IdeModuleSourceSet
import com.android.tools.idea.gradle.project.sync.idea.ModuleUtil.getModuleName
import com.android.tools.idea.gradle.util.GradleUtil
import com.android.tools.idea.util.CommonAndroidUtil.LINKED_ANDROID_MODULE_GROUP
import com.android.tools.idea.util.LinkedAndroidModuleGroup
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import org.jetbrains.android.util.firstNotNullResult
import org.jetbrains.kotlin.idea.inspections.gradle.findAll
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData

object ModuleUtil {
  @JvmStatic
  fun getModuleName(artifact: IdeBaseArtifact): String = getModuleName(artifact.name)

  @JvmStatic
  fun getModuleName(artifactName: IdeArtifactName): String {
    return when (artifactName) {
      IdeArtifactName.MAIN -> IdeModuleSourceSet.MAIN.sourceSetName
      IdeArtifactName.UNIT_TEST -> IdeModuleSourceSet.UNIT_TEST.sourceSetName
      IdeArtifactName.ANDROID_TEST -> IdeModuleSourceSet.ANDROID_TEST.sourceSetName
      IdeArtifactName.TEST_FIXTURES -> IdeModuleSourceSet.TEST_FIXTURES.sourceSetName
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
  fun DataNode<out ModuleData>.linkAndroidModuleGroup(dataToModuleMap: (ModuleData) -> Module?) {
    val holderModule = dataToModuleMap(data) ?: return
    // Clear the links, this prevents old links from being used
    holderModule.putUserData(LINKED_ANDROID_MODULE_GROUP, null)
    if (!holderModule.project.isModulePerSourceSetEnabled()) return
    var unitTestModule : Module? = null
    var androidTestModule : Module? = null
    var testFixturesModule : Module? = null
    var mainModule : Module? = null
    findAll(GradleSourceSetData.KEY).forEach {
      when(val sourceSetName = it.data.externalName.substringAfterLast(":")) {
        getModuleName(IdeArtifactName.MAIN) -> mainModule = dataToModuleMap(it.data)
        getModuleName(IdeArtifactName.UNIT_TEST) -> unitTestModule = dataToModuleMap(it.data)
        getModuleName(IdeArtifactName.ANDROID_TEST) -> androidTestModule = dataToModuleMap(it.data)
        getModuleName(IdeArtifactName.TEST_FIXTURES) -> testFixturesModule = dataToModuleMap(it.data)
        else -> logger<ModuleUtil>().warn("Unknown artifact name: $sourceSetName")
      }
    }
    if (mainModule == null) {
      logger<ModuleUtil>().error("Unexpected - Android module is missing a main source set")
      return
    }
    val androidModuleGroup = LinkedAndroidModuleGroup(holderModule, mainModule!!, unitTestModule, androidTestModule, testFixturesModule)
    androidModuleGroup.getModules().forEach { module ->
      module?.putUserData(LINKED_ANDROID_MODULE_GROUP, androidModuleGroup)
    }
  }

  @JvmStatic
  fun DataNode<ModuleData>.linkAndroidModuleGroup(ideModelProvider: IdeModifiableModelsProvider) =
    linkAndroidModuleGroup { ideModelProvider.findIdeModule(it) }

  @JvmStatic
  fun GradleSourceSetData.getIdeModuleSourceSet() =
    IdeModuleSourceSet.values().firstOrNull { sourceSetEnum -> sourceSetEnum.sourceSetName == moduleName }
}

fun String.removeSourceSetSuffixFromExternalProjectID() : String = removeSourceSetSuffix(":")

fun String.removeSourceSetSuffixFromModuleName() : String = removeSourceSetSuffix(".")

private fun String.removeSourceSetSuffix(delimiter: String) : String = IdeArtifactName.values().firstNotNullResult { artifactName ->
  val moduleName = getModuleName(artifactName)
  val suffix = "$delimiter$moduleName"
  if (this.endsWith(suffix)) {
    this.removeSuffix(suffix)
  } else {
    null
  }
} ?: this