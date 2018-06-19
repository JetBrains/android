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
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule
import com.android.tools.idea.gradle.structure.model.android.PsCollectionBase
import com.android.tools.idea.gradle.structure.model.java.PsJavaModule
import com.intellij.openapi.module.ModuleManager
import java.io.File

enum class ModuleKind { ANDROID, JAVA }
data class ModuleKey(val kind: ModuleKind, val gradlePath: String)

class PsModuleCollection(parent: PsProjectImpl) : PsCollectionBase<PsModule, ModuleKey, PsProjectImpl>(parent) {
  override fun getKeys(from: PsProjectImpl): Set<ModuleKey> {
    val result = mutableSetOf<ModuleKey>()
    val projectParsedModel = from.parsedModel
    val resolvedModules = from.getResolvedModuleModelsByGradlePath()
    for ((gradlePath, resolvedModel) in resolvedModules) {
      when (resolvedModel) {
        is PsResolvedModuleModel.PsAndroidModuleResolvedModel -> result.add(ModuleKey(ModuleKind.ANDROID, gradlePath))
        is PsResolvedModuleModel.PsJavaModuleResolvedModel -> result.add(ModuleKey(ModuleKind.JAVA, gradlePath))
      }
    }
    parent.parsedModel.modules.forEach { path ->
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
    val moduleName =
      ModuleManager
        .getInstance(parent.ideProject)  // Use ideProject to find the name of the module.
        .modules
        .firstOrNull { GradleFacet.getInstance(it)?.gradleModuleModel?.gradlePath == key.gradlePath }
        ?.name
      ?: key.gradlePath.substring(1).replace(':', '-')

    val moduleResolvedModel =
      parent.getResolvedModuleModelsByGradlePath()[key.gradlePath]

    val moduleParsedModel = projectParsedModel.getModuleByGradlePath(key.gradlePath)

    // Module type cannot be changed within the PSD.
    return when (key.kind) {
      ModuleKind.ANDROID ->
        (model as PsAndroidModule)
          .init(moduleName,
                (moduleResolvedModel as? PsResolvedModuleModel.PsAndroidModuleResolvedModel)?.model,
                moduleParsedModel)
      ModuleKind.JAVA ->
        (model as PsJavaModule)
          .init(moduleName,
                (moduleResolvedModel as? PsResolvedModuleModel.PsJavaModuleResolvedModel)?.model,
                moduleParsedModel)
    }
  }
}

private fun ProjectBuildModel.getModuleByGradlePath(gradlePath: String): GradleBuildModel? =
  projectBuildModel?.virtualFile?.parent
    ?.findFileByRelativePath(
      gradlePath.trimStart(':').replace(':', '/'))?.path?.let { getModuleBuildModel(File(it)) }

private val ProjectBuildModel.modules: Set<String>
  get() = projectSettingsModel?.modulePaths()?.toSet().orEmpty()
