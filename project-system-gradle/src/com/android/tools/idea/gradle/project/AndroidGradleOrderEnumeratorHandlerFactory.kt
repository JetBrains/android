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
package com.android.tools.idea.gradle.project

import com.android.tools.idea.gradle.model.IdeAndroidArtifact
import com.android.tools.idea.gradle.model.IdeBaseArtifact
import com.android.tools.idea.gradle.model.IdeJavaArtifact
import com.android.tools.idea.gradle.project.model.GradleAndroidModel
import com.android.tools.idea.io.FilePaths
import com.android.tools.idea.projectsystem.isAndroidTestModule
import com.android.tools.idea.projectsystem.isMainModule
import com.android.tools.idea.projectsystem.isTestFixturesModule
import com.android.tools.idea.projectsystem.isUnitTestModule
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootModel
import com.intellij.openapi.roots.OrderRootType
import org.jetbrains.plugins.gradle.execution.GradleOrderEnumeratorHandler

/**
 * [AndroidGradleOrderEnumeratorHandlerFactory] was introduced to make
 * order entries enumeration of Android modules consistent with the Android gradle importer.
 * Android gradle importer uses first-level dependencies for android modules,
 * and each module has to "export" their dependencies to make them visible to dependent modules.
 *
 *
 * Non-android gradle modules don't have such restriction (there will always be fully resolved dependency graph as a flat list)
 * and should not be affected by the recursive enumeration.
 * Which can lead to unexpected runtime classpath and performance degradation.
 */
class AndroidGradleOrderEnumeratorHandlerFactory : GradleOrderEnumeratorHandler.FactoryImpl() {
  override fun isApplicable(module: Module): Boolean {
    return GradleAndroidModel.get(module) != null
  }

  override fun createHandler(module: Module): GradleOrderEnumeratorHandler {
    if (!isApplicable(module)) {
      error("AndroidGradleOrderEnumeratorHandlerFactory is not applicable to $module")
    }
    return object : GradleOrderEnumeratorHandler(module) {
      override fun shouldAddRuntimeDependenciesToTestCompilationClasspath(): Boolean = false

      override fun shouldIncludeTestsFromDependentModulesToTestClasspath(): Boolean = true

      override fun shouldProcessDependenciesRecursively(): Boolean = true

      private fun applicableArtifacts(
        module: Module,
        androidModel: GradleAndroidModel,
        includeProduction: Boolean,
        includeTests: Boolean
      ): List<IdeBaseArtifact> {
        return listOfNotNull(
          androidModel.mainArtifact.takeIf { includeProduction && module.isMainModule() },
          androidModel.getArtifactForAndroidTest()?.takeIf { includeTests && module.isAndroidTestModule() },
          androidModel.selectedVariant.unitTestArtifact?.takeIf { includeTests && module.isUnitTestModule() },
          androidModel.selectedVariant.testFixturesArtifact?.takeIf { includeTests && module.isTestFixturesModule() },
        )
          .distinct()
      }

      override fun addCustomModuleRoots(
        type: OrderRootType,
        rootModel: ModuleRootModel,
        result: MutableCollection<String>,
        includeProduction: Boolean,
        includeTests: Boolean
      ): Boolean {
        val androidModel = GradleAndroidModel.get(rootModel.module)
          ?: return false // `isApplicable()` should have returned false.
        if (type != OrderRootType.CLASSES) {
          return false
        }
        val artifacts = applicableArtifacts(module, androidModel, includeProduction, includeTests)

        val existingResults = result.toHashSet()
        result.addAll(getAndroidCompilerOutputFolders(artifacts).distinct().filter { !existingResults.contains(it) })
        return true
      }
    }
  }

  companion object {
    private fun getAndroidCompilerOutputFolders(
      artifacts: List<IdeBaseArtifact>
    ): Sequence<String> {
      // The test artifact must be added to the classpath before the main artifact, this is so that tests pick up the correct classes
      // if multiple definitions of the same class exist in both the test and the main artifact.

      return artifacts
        .asSequence()
        .flatMap {
          when (it) {
            is IdeJavaArtifact -> addFoldersFromJavaArtifact(it)
            is IdeAndroidArtifact -> addFoldersFromAndroidArtifact(it)
          }
        }
    }

    private fun foldersFromBaseArtifact(artifact: IdeBaseArtifact): Sequence<String> {
      return artifact.classesFolder.asSequence().map(FilePaths::pathToIdeaUrl)
    }

    private fun addFoldersFromJavaArtifact(artifact: IdeJavaArtifact): Sequence<String> {
      return foldersFromBaseArtifact(artifact)
    }

    private fun addFoldersFromAndroidArtifact(artifact: IdeAndroidArtifact): Sequence<String> {
      return foldersFromBaseArtifact(artifact) +
        artifact.generatedResourceFolders.asSequence().map(FilePaths::pathToIdeaUrl)
    }
  }
}