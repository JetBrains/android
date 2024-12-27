/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.gradle.dependencies

import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.android.tools.idea.gradle.dsl.api.dependencies.CommonConfigurationNames.CLASSPATH
import com.intellij.psi.PsiFile

class DependenciesProcessor(private val projectModel: ProjectBuildModel) {
  fun apply(config: DependenciesConfig, module: com.intellij.openapi.module.Module): ApplyResult {
    val moduleModel = projectModel.getModuleBuildModel(module) ?: return ApplyResult.failure()
    var result: ApplyResult = ApplyResult.success(setOf())
    val helper = DependenciesHelper.withModel(projectModel)
    for (plugin in config.plugins) {
      // TODO rethink input types in DependenciesConfig
      helper.addPluginOrClasspath(plugin.pluginId,
                                  plugin.classpathModule,
                                  plugin.version,
                                  listOf(moduleModel),
                                  config.pluginMatcherFactory(plugin),
                                  config.dependencyMatcherFactory(CLASSPATH, plugin.classpathModule + ":" + plugin.version))
        .also { result = result.appendAll(it) }
    }
    for (dependency in config.dependencies) {
      helper.addDependency(dependency.configurationName, dependency.dependency, listOf(), moduleModel,
                           config.dependencyMatcherFactory(dependency.configurationName, dependency.dependency))
        .also { result = result.appendAll(it) }
    }
    for (platform in config.platforms) {
      helper.addPlatformDependency(platform.configurationName, platform.dependency, platform.enforced, moduleModel,
                                   config.dependencyMatcherFactory(platform.configurationName, platform.dependency))
        .also { result = result.appendAll(it) }
    }
    return result
  }
}

data class ApplyResult(val success: Boolean, val updated: Set<PsiFile>) {
  fun merge(result: ApplyResult) = copy(success = this.success && result.success, updated = this.updated + result.updated)
  fun append(file: PsiFile?) = file?.let { appendAll(setOf(it)) } ?: copy()
  fun appendAll(files: Set<PsiFile>) = copy(updated = this.updated + files)
  fun fail() = copy(false, updated)

  companion object {
    fun success(updated: Set<PsiFile>) = ApplyResult(true, updated)
    fun failure() = ApplyResult(false, setOf())
  }
}