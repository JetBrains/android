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
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootModel
import com.intellij.openapi.roots.OrderRootType
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.execution.GradleOrderEnumeratorHandler
import org.jetbrains.plugins.gradle.settings.GradleLocalSettings
import java.io.File
import java.util.LinkedList
import java.util.Objects

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
    val rootProjectPath = ExternalSystemApiUtil.getExternalRootProjectPath(module)
    // Always recurse for Android modules.
    var shouldRecurse = GradleAndroidModel.get(module) != null
    if (rootProjectPath != null && !shouldRecurse) {
      // Only recurse when the Gradle version is less than 2.5. This is taken from the GradleOrderEnumeratorHandler to make sure that
      // for non-android modules we return a consistent value.
      val gradleVersion = GradleLocalSettings.getInstance(module.project).getGradleVersion(rootProjectPath)
      shouldRecurse = gradleVersion != null && GradleVersion.version(gradleVersion).compareTo(GradleVersion.version("2.5")) < 0
    }
    val finalShouldRecurse = shouldRecurse
    return object : GradleOrderEnumeratorHandler(module) {
      override fun shouldAddRuntimeDependenciesToTestCompilationClasspath(): Boolean {
        return false
      }

      override fun shouldIncludeTestsFromDependentModulesToTestClasspath(): Boolean {
        return true
      }

      override fun shouldProcessDependenciesRecursively(): Boolean {
        return finalShouldRecurse
      }

      override fun addCustomModuleRoots(
        type: OrderRootType,
        rootModel: ModuleRootModel,
        result: MutableCollection<String>,
        includeProduction: Boolean,
        includeTests: Boolean
      ): Boolean {
        val androidModel = GradleAndroidModel.get(rootModel.module)
          ?: return super.addCustomModuleRoots(type, rootModel, result, includeProduction, includeTests)
        if (type != OrderRootType.CLASSES) {
          return false
        }
        super.addCustomModuleRoots(type, rootModel, result, includeProduction, includeTests)
        getAndroidCompilerOutputFolders(androidModel, includeProduction, includeTests).stream()
          .filter { root: String -> !result.contains(root) }
          .forEachOrdered { e: String -> result.add(e) }
        return true
      }
    }
  }

  companion object {
    private fun getAndroidCompilerOutputFolders(
      androidModel: GradleAndroidModel,
      includeProduction: Boolean,
      includeTests: Boolean
    ): List<String> {
      val toAdd: MutableList<String> = LinkedList()
      // The test artifact must be added to the classpath before the main artifact, this is so that tests pick up the correct classes
      // if multiple definitions of the same class exist in both the test and the main artifact.
      if (includeTests) {
        if (androidModel.selectedVariant.unitTestArtifact != null) {
          addFoldersFromJavaArtifact(androidModel.selectedVariant.unitTestArtifact!!, toAdd)
        }
        if (androidModel.selectedVariant.androidTestArtifact != null) {
          addFoldersFromAndroidArtifact(androidModel.selectedVariant.androidTestArtifact!!, toAdd)
        }
        if (androidModel.selectedVariant.testFixturesArtifact != null) {
          addFoldersFromAndroidArtifact(androidModel.selectedVariant.testFixturesArtifact!!, toAdd)
        }
      }
      if (includeProduction) {
        addFoldersFromAndroidArtifact(androidModel.selectedVariant.mainArtifact, toAdd)
      }
      return toAdd
    }

    private fun addFoldersFromBaseArtifact(artifact: IdeBaseArtifact, toAdd: MutableList<String>) {
      artifact.classesFolder
        .map(FilePaths::pathToIdeaUrl)
        .sorted()
        .forEach { e: String -> toAdd.add(e) }
    }

    private fun addFoldersFromJavaArtifact(artifact: IdeJavaArtifact, toAdd: MutableList<String>) {
      addFoldersFromBaseArtifact(artifact, toAdd)
    }

    private fun addFoldersFromAndroidArtifact(artifact: IdeAndroidArtifact, toAdd: MutableList<String>) {
      addFoldersFromBaseArtifact(artifact, toAdd)
      artifact.generatedResourceFolders.stream()
        .filter { obj: File? -> Objects.nonNull(obj) }
        .map { path: File? ->
          FilePaths.pathToIdeaUrl(
            path!!
          )
        }
        .forEach { e: String -> toAdd.add(e) }
    }
  }
}