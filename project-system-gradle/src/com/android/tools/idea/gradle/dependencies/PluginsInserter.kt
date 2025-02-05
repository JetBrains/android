/*
 * Copyright (C) 2025 The Android Open Source Project
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

import com.android.tools.idea.gradle.dependencies.PluginInsertionConfig.Companion.defaultInsertionConfig
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel
import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencySpec
import com.android.tools.idea.gradle.dsl.api.repositories.RepositoriesModel
import com.intellij.psi.PsiFile
import org.gradle.api.plugins.JavaPlatformPlugin.CLASSPATH_CONFIGURATION_NAME
import org.jetbrains.kotlin.utils.addIfNotNull

interface PluginsInserter {

  fun addPluginOrClasspath(
    pluginId: String,
    classpathModule: String,
    version: String,
    buildModels: List<GradleBuildModel>,
    matcherFactory: (String, String) -> PluginMatcher = { id, _ -> IdPluginMatcher(id) },
    classpathMatcher: DependencyMatcher = GroupNameDependencyMatcher(CLASSPATH_CONFIGURATION_NAME, "$classpathModule:$version"),
    config: PluginInsertionConfig = defaultInsertionConfig()
  ): Set<PsiFile>

  fun findPlaceAndAddPlugin(
    pluginId: String,
    version: String,
    buildModels: List<GradleBuildModel>,
    matcherFactory: (String, String) -> PluginMatcher = { id, _ -> IdPluginMatcher(id) }
  ): Set<PsiFile>

  fun addClasspathDependency(dependency: String,
                             excludes: List<ArtifactDependencySpec> = listOf(),
                             matcher: DependencyMatcher = ExactDependencyMatcher(CLASSPATH_CONFIGURATION_NAME,
                                                                                 dependency)): Set<PsiFile>

  fun addPlugin(pluginId: String, buildModel: GradleBuildModel, matcher: PluginMatcher = IdPluginMatcher(pluginId)): PsiFile?


  fun addPluginToModule(pluginId: String,
                        version: String,
                        buildModel: GradleBuildModel,
                        matcher: PluginMatcher = IdPluginMatcher(pluginId)): Set<PsiFile>

  fun addRepositoryFor(version: String, model: RepositoriesModel): PsiFile?

  fun applySettingsPlugin(pluginId: String,
                          version: String): Set<PsiFile>

  // Files may be already in proper state, so we need additional flag `succeed` to make sure
  // all changes already there
  data class TryAddResult(val changedFiles: Set<PsiFile>, val succeed: Boolean) {
    companion object {
      fun failed() = TryAddResult(setOf(), false)
    }

    fun appendWhenSuccess(f: () -> PsiFile?): TryAddResult {
      if (succeed) {
        val set = mutableSetOf<PsiFile>()
        set.addAll(changedFiles)
        set.addIfNotNull(f())
        return TryAddResult(set, true)
      }
      return this
    }
  }
}
