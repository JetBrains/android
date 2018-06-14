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
package com.android.tools.idea.gradle.structure.model

import com.android.tools.idea.gradle.dsl.api.GradleBuildModel
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet
import com.android.tools.idea.gradle.project.model.AndroidModuleModel
import com.android.tools.idea.gradle.project.model.JavaModuleModel
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule
import com.android.tools.idea.gradle.structure.model.android.PsCollectionBase
import com.android.tools.idea.gradle.structure.model.java.PsJavaModule
import com.android.tools.idea.gradle.util.GradleUtil.getGradlePath
import com.intellij.openapi.module.ModuleManager
import org.jetbrains.android.facet.AndroidFacet
import java.io.File

enum class ModuleKind { ANDROID, JAVA }
data class ModuleKey(val kind: ModuleKind, val gradlePath: String)

class PsModuleCollection(parent: PsProject) : PsCollectionBase<PsModule, ModuleKey, PsProject>(parent) {
  override fun getKeys(from: PsProject): Set<ModuleKey> {
    val result = mutableSetOf<ModuleKey>()
    val projectParsedModel = from.parsedModel
    val parsedModules = parent.parsedModel.modules.toMutableSet()
    for (resolvedModel in ModuleManager.getInstance(from.ideProject).modules) {
      val gradlePath = getGradlePath(resolvedModel)
      val moduleParsedModel = projectParsedModel.getModuleBuildModel(resolvedModel)
      if (gradlePath != null && moduleParsedModel != null) {
        val gradleModel = AndroidFacet.getInstance(resolvedModel)
        if (gradleModel != null) {
          result.add(ModuleKey(ModuleKind.ANDROID, gradlePath))
          parsedModules.remove(gradlePath)
        }
        else {
          val javaModuleModel = JavaModuleModel.get(resolvedModel)
          if (javaModuleModel != null && javaModuleModel.isBuildable) {
            result.add(ModuleKey(ModuleKind.JAVA, gradlePath))
            parsedModules.remove(gradlePath)
          }
        }
      }
    }
    parent.parsedModel.modules.forEach{ path ->
      val buildModel = projectParsedModel.getModuleByGradlePath(path)
      val moduleType = buildModel?.parsedModelModuleType()
      val moduleKey = when (moduleType) {
        PsModuleType.JAVA -> ModuleKey(ModuleKind.JAVA, path)
        PsModuleType.ANDROID_APP,
        PsModuleType.ANDROID_DYNAMIC_FEATURE,
        PsModuleType.ANDROID_FEATURE,
        PsModuleType.ANDROID_INSTANTAPP,
        PsModuleType.ANDROID_LIBRARY,
        PsModuleType.ANDROID_TEST -> ModuleKey(ModuleKind.ANDROID, path)
        null,
        PsModuleType.UNKNOWN -> null
      }
      moduleKey?.let { result.add(it) }
    }
    return result
  }

  override fun create(key: ModuleKey): PsModule = when (key.kind) {
    ModuleKind.ANDROID -> PsAndroidModule(parent, key.gradlePath)
    ModuleKind.JAVA -> PsJavaModule(parent, key.gradlePath)
  }

  override fun update(key: ModuleKey, model: PsModule) {
    val projectParsedModel = parent.parsedModel
    val moduleManager = ModuleManager.getInstance(parent.ideProject)
    val module = moduleManager.modules.firstOrNull { GradleFacet.getInstance(it)?.gradleModuleModel?.gradlePath == key.gradlePath }
    val moduleName = module?.name ?: key.gradlePath.substring(1).replace(':', '-')
    val moduleParsedModel = module?.let { projectParsedModel.getModuleBuildModel(module) }
                            ?: projectParsedModel.getModuleByGradlePath(key.gradlePath)
    return when (key.kind) {
      // Module type cannot be changed within the PSD.
      ModuleKind.ANDROID -> (model as PsAndroidModule).init(moduleName, module?.let { AndroidModuleModel.get(module) }, moduleParsedModel)
      // Module type cannot be changed within the PSD.
      ModuleKind.JAVA -> (model as PsJavaModule).init(moduleName, module?.let { JavaModuleModel.get(module) }, moduleParsedModel)
    }
  }
}

private fun ProjectBuildModel.getModuleByGradlePath(gradlePath: String): GradleBuildModel? =
  projectBuildModel?.virtualFile?.parent
    ?.findFileByRelativePath(
      gradlePath.trimStart(':').replace(':', '/'))?.path?.let { getModuleBuildModel(File(it)) }

private val ProjectBuildModel.modules: Set<String>
  get() = projectSettingsModel?.modulePaths()?.toSet().orEmpty()
