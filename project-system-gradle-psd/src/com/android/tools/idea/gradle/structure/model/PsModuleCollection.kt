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

import com.android.SdkConstants
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule
import com.android.tools.idea.gradle.structure.model.android.PsMutableCollectionBase
import com.android.tools.idea.gradle.structure.model.empty.PsEmptyModule
import com.android.tools.idea.gradle.structure.model.java.PsJavaModule
import com.android.tools.idea.gradle.util.GradleUtil
import com.android.tools.idea.projectsystem.gradle.GradleProjectPath
import com.android.tools.idea.projectsystem.gradle.getGradleProjectPath
import com.intellij.openapi.Disposable
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.util.containers.mapSmartSet
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolverUtil
import java.io.File

enum class ModuleKind { ANDROID, JAVA, FAKE, EMPTY }
data class ModuleKey(val kind: ModuleKind, val gradlePath: String)

class PsModuleCollection(parent: PsProjectImpl) : PsMutableCollectionBase<PsModule, ModuleKey, PsProjectImpl>(parent) {
  private val onModuleChangedHandlers = mutableListOf<Pair<Disposable, (PsModule) -> Unit>>()

  init {
    refresh()
  }

  override fun getKeys(from: PsProjectImpl): Set<ModuleKey> {
    val result = mutableSetOf<ModuleKey>()
    val projectParsedModel = from.parsedModel
    val resolvedModules = from.getResolvedModuleModelsByGradlePath()
    for ((gradlePath, resolvedModel) in resolvedModules) {
      val moduleParsedModel = gradleModuleParsedModel(resolvedModel, projectParsedModel, gradlePath)
      when {
        moduleParsedModel == null -> result.add(ModuleKey(ModuleKind.EMPTY, gradlePath))
        resolvedModel is PsResolvedModuleModel.PsAndroidModuleResolvedModel -> result.add(ModuleKey(ModuleKind.ANDROID, gradlePath))
        resolvedModel is PsResolvedModuleModel.PsJavaModuleResolvedModel -> result.add(ModuleKey(ModuleKind.JAVA, gradlePath))
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

    //Add non-existing mid-hierarchy modules as empty modules.
    val declaredPaths = result.mapSmartSet { it.gradlePath }
    declaredPaths
      .flatMap { GradleUtil.getAllParentModulesPaths(it) }
      .filterNot { declaredPaths.contains(it) }
      .distinct()
      .forEach { result.add(ModuleKey(ModuleKind.EMPTY, it)) }

    return result.sortedBy { it.gradlePath }.toSet()
  }

  override fun create(key: ModuleKey): PsModule = when (key.kind) {
    ModuleKind.ANDROID -> PsAndroidModule(parent, key.gradlePath)
    ModuleKind.JAVA -> PsJavaModule(parent, key.gradlePath)
    ModuleKind.EMPTY -> PsEmptyModule(parent, key.gradlePath)
    ModuleKind.FAKE -> throw IllegalArgumentException()
  }.also { module ->
    onModuleChangedHandlers.forEach { (disposable, handler) -> module.onChange(disposable, handler) }
  }

  override fun update(key: ModuleKey, model: PsModule) {
    val projectParsedModel = parent.parsedModel
    val gradlePathName = key.gradlePath.substringAfterLast(':')
    val moduleName = if (gradlePathName.isNotEmpty()) gradlePathName
                     else parent.ideProject.getModuleByGradlePath(key.gradlePath)?.name ?: "Unknown"

    val moduleResolvedModel =
        parent.getResolvedModuleModelsByGradlePath()[key.gradlePath]

    val gradlePath = key.gradlePath
    val moduleParsedModel = gradleModuleParsedModel(moduleResolvedModel, projectParsedModel, gradlePath)

    // Module type cannot be changed within the PSD.
    return when (key.kind) {
      ModuleKind.ANDROID -> {
        val androidModuleResolvedModel = moduleResolvedModel as? PsResolvedModuleModel.PsAndroidModuleResolvedModel
        (model as PsAndroidModule)
            .init(moduleName,
                  findParentModuleFor(key.gradlePath),
                  androidModuleResolvedModel?.model,
                  androidModuleResolvedModel?.nativeModel,
                  androidModuleResolvedModel?.syncIssues,
                  moduleParsedModel)
      }
      ModuleKind.JAVA ->
        (model as PsJavaModule)
            .init(moduleName,
                  findParentModuleFor(key.gradlePath),
                  (moduleResolvedModel as? PsResolvedModuleModel.PsJavaModuleResolvedModel)?.model,
                  (moduleResolvedModel as? PsResolvedModuleModel.PsJavaModuleResolvedModel)?.dependencies,
                  moduleParsedModel)
      ModuleKind.EMPTY -> (model as PsEmptyModule).init(moduleName, findParentModuleFor(key.gradlePath))
      ModuleKind.FAKE -> throw IllegalArgumentException()
    }
  }

  private fun gradleModuleParsedModel(
    moduleResolvedModel: PsResolvedModuleModel?,
    projectParsedModel: ProjectBuildModel,
    gradlePath: String
  ): GradleBuildModel? =
    moduleResolvedModel
        ?.buildFile
        ?.let { buildFilePath -> LocalFileSystem.getInstance().findFileByIoFile(File(buildFilePath)) }
        ?.let { virtualFile -> projectParsedModel.getModuleBuildModel(virtualFile) }
    ?: projectParsedModel.takeIf { it.modules.contains(gradlePath) }?.getModuleByGradlePath(gradlePath)

  override fun instantiateNew(key: ModuleKey) = throw UnsupportedOperationException()

  override fun removeExisting(key: ModuleKey) {
    for (module in parent.modules) {
      val dynamicFeatures = (module as? PsAndroidModule)?.parsedModel?.android()?.dynamicFeatures() ?: continue
      val dynamicFeatureItem = dynamicFeatures.getListValue(key.gradlePath) ?: continue
      dynamicFeatureItem.delete()
      module.isModified = true
    }
    parent.parsedModel.projectSettingsModel?.removeModulePath(key.gradlePath)
  }

  private fun findParentModuleFor(gradlePath: String): PsModule? {
    var remainingPath = gradlePath
    while (remainingPath.contains(SdkConstants.GRADLE_PATH_SEPARATOR)) {
      remainingPath = remainingPath.substringBeforeLast(':', "")
      entries.entries.find { it.key.gradlePath == remainingPath }
          ?.let { return it.value }
    }
    return null
  }

  fun onModuleChanged(disposable: Disposable, handler: (PsModule) -> Unit) {
    Disposer.register(disposable, Disposable { onModuleChangedHandlers.remove(disposable to handler) })
    onModuleChangedHandlers.add(disposable to handler)
    forEach { module -> module.onChange(disposable, handler) }
  }
}

fun Project.getModuleByGradlePath(gradlePath: String) =
    // TODO(b/149203281): Fix support for composite builds.
    ModuleManager
        .getInstance(this)  // Use ideProject to find the name of the module.
        .modules
        .firstOrNull { it.getGradleProjectPath()?.path == gradlePath }

private fun ProjectBuildModel.getModuleByGradlePath(gradlePath: String): GradleBuildModel? =
    when {
      projectSettingsModel == null && gradlePath == ":" -> projectBuildModel
      else -> {
        projectSettingsModel?.let { projectSettingsModel ->
          projectSettingsModel
              .buildFile(gradlePath)
              ?.let { relativeFile ->
                (projectSettingsModel.moduleDirectory(":") ?: return@getModuleByGradlePath null).resolve(relativeFile)
              }
              ?.let { absoluteFile -> LocalFileSystem.getInstance().findFileByIoFile(absoluteFile) }
              ?.let { virtualFile -> this.getModuleBuildModel(virtualFile) }
        }
      }
    }

private val ProjectBuildModel.modules: Set<String>
  get() = projectSettingsModel.let { it?.modulePaths() ?: setOf(":") }
