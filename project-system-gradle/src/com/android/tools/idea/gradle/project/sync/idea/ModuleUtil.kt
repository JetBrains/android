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
import com.android.tools.idea.gradle.model.IdeModuleSourceSet
import com.android.tools.idea.gradle.model.IdeModuleWellKnownSourceSet
import com.android.tools.idea.gradle.model.impl.IdeModuleSourceSetImpl
import com.android.tools.idea.gradle.project.sync.idea.ModuleUtil.getModuleName
import com.android.tools.idea.util.CommonAndroidUtil.LINKED_ANDROID_MODULE_GROUP
import com.android.tools.idea.util.LinkedAndroidModuleGroup
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData

object ModuleUtil {
  @JvmStatic
  fun getModuleName(artifactName: IdeArtifactName): String {
    return artifactName.toWellKnownSourceSet().sourceSetName
  }

  @JvmStatic
  fun IdeArtifactName.toWellKnownSourceSet(): IdeModuleWellKnownSourceSet {
    return when (this) {
      IdeArtifactName.MAIN -> IdeModuleWellKnownSourceSet.MAIN
      IdeArtifactName.UNIT_TEST -> IdeModuleWellKnownSourceSet.UNIT_TEST
      IdeArtifactName.ANDROID_TEST -> IdeModuleWellKnownSourceSet.ANDROID_TEST
      IdeArtifactName.TEST_FIXTURES -> IdeModuleWellKnownSourceSet.TEST_FIXTURES
    }
  }

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
    var unitTestModule : Module? = null
    var androidTestModule : Module? = null
    var testFixturesModule : Module? = null
    var mainModule : Module? = null
    ExternalSystemApiUtil.findAll(this, GradleSourceSetData.KEY).forEach {
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
  fun Module.unlinkAndroidModuleGroup() {
    val androidModuleGroup = getUserData(LINKED_ANDROID_MODULE_GROUP) ?: return
    androidModuleGroup.getModules().filter { !it.isDisposed }.forEach { it.putUserData(LINKED_ANDROID_MODULE_GROUP, null) }
  }

  @JvmStatic
  fun GradleSourceSetData.getIdeModuleSourceSet(): IdeModuleSourceSet = IdeModuleSourceSetImpl.wellKnownOrCreate(moduleName)
}

fun String.removeSourceSetSuffixFromExternalProjectID() : String = removeSourceSetSuffix(":")

fun String.removeSourceSetSuffixFromModuleName() : String = removeSourceSetSuffix(".")

private fun String.removeSourceSetSuffix(delimiter: String) : String = IdeArtifactName.values().firstNotNullOfOrNull { artifactName ->
  val moduleName = getModuleName(artifactName)
  val suffix = "$delimiter$moduleName"
  if (this.endsWith(suffix)) {
    this.removeSuffix(suffix)
  } else {
    null
  }
} ?: this
