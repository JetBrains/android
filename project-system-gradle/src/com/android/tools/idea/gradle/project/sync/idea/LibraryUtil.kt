/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.tools.idea.gradle.model.IdeLibrary
import com.android.tools.idea.gradle.model.IdeModuleLibrary
import com.android.tools.idea.gradle.model.IdePreResolvedModuleLibrary
import com.android.tools.idea.gradle.model.IdeUnresolvedAndroidLibrary
import com.android.tools.idea.gradle.model.IdeUnresolvedJavaLibrary
import com.android.tools.idea.gradle.model.IdeUnresolvedKmpAndroidModuleLibrary
import com.android.tools.idea.gradle.model.IdeUnresolvedModuleLibrary
import com.android.tools.idea.gradle.model.IdeUnresolvedUnknownLibrary
import com.android.tools.idea.gradle.model.impl.IdeJavaLibraryImpl
import com.android.tools.idea.gradle.model.impl.IdeModuleLibraryImpl
import com.android.tools.idea.gradle.model.impl.IdeModuleSourceSetImpl
import com.android.tools.idea.gradle.model.impl.IdePreResolvedModuleLibraryImpl
import com.android.tools.idea.gradle.model.impl.IdeResolvedLibraryTable
import com.android.tools.idea.gradle.model.impl.IdeResolvedLibraryTableImpl
import com.android.tools.idea.gradle.model.impl.IdeUnresolvedLibraryTable
import com.android.tools.idea.projectsystem.gradle.GradleHolderProjectPath
import com.android.tools.idea.projectsystem.gradle.GradleProjectPath
import com.android.tools.idea.projectsystem.gradle.GradleSourceSetProjectPath
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.LibraryData
import com.intellij.openapi.externalSystem.model.project.LibraryLevel
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import org.jetbrains.kotlin.idea.gradle.configuration.KotlinSourceSetData
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolverUtil
import java.io.File

class ResolvedLibraryTableBuilder(
  private val getGradlePathBy: (moduleId: String) -> GradleProjectPath?,
  private val getModuleDataNode: (GradleProjectPath) -> DataNode<out ModuleData>?,
  private val resolveArtifact: (File) -> List<GradleSourceSetProjectPath>?,
  private val resolveKmpAndroidMainSourceSet: (GradleProjectPath) -> String?,
) {
  fun buildResolvedLibraryTable(
    ideLibraryTable: IdeUnresolvedLibraryTable,
  ): IdeResolvedLibraryTable {
    return ideLibraryTable.resolve(
      artifactResolver = { resolveArtifact(it) },
      moduleDependencyExpander = ::resolveAdditionalKmpSourceSets,
      kmpAndroidMainSourceSetResolver = resolveKmpAndroidMainSourceSet,
      logger = logger
    )
  }

  private fun resolveAdditionalKmpSourceSets(sourceSet: GradleSourceSetProjectPath): List<GradleSourceSetProjectPath> {
    return sequence {
      yield(sourceSet)
      val targetSourceSetData = getModuleDataNode(sourceSet)
        ?: let {
          logError("Resolved source set not found for: $sourceSet")
          return@sequence
        }
      val kmpDependsOn = ExternalSystemApiUtil.find(targetSourceSetData, KotlinSourceSetData.KEY)?.data?.sourceSetInfo?.dependsOn.orEmpty()
      yieldAll(kmpDependsOn.mapNotNull(getGradlePathBy))
    }
      .distinct()
      .filterIsInstance<GradleSourceSetProjectPath>()
      .toList()
  }

  private val logger = Logger.getInstance(this.javaClass)

  private fun logError(message: String) {
    logger.error(message, Throwable())
  }
}

private fun IdeUnresolvedLibraryTable.resolve(
  artifactResolver: (File) -> List<GradleSourceSetProjectPath>?,
  moduleDependencyExpander: (GradleSourceSetProjectPath) -> List<GradleSourceSetProjectPath>,
  kmpAndroidMainSourceSetResolver: (GradleProjectPath) -> String?,
  logger: Logger
): IdeResolvedLibraryTable {

  fun resolve(preResolved: IdePreResolvedModuleLibrary): List<IdeModuleLibrary> {
    val expandedSourceSets = moduleDependencyExpander(
      GradleSourceSetProjectPath(
        preResolved.buildId,
        preResolved.projectPath,
        preResolved.sourceSet
      )
    )
    return expandedSourceSets.map {
      IdeModuleLibraryImpl(
        buildId = it.buildRoot,
        projectPath = it.path,
        variant = preResolved.variant,
        lintJar = preResolved.lintJar,
        sourceSet = it.sourceSet
      )
    }
  }

  fun resolve(unresolved: IdeUnresolvedKmpAndroidModuleLibrary): List<IdeModuleLibrary> {
    val mainSourceSet = kmpAndroidMainSourceSetResolver(
      GradleHolderProjectPath(
        unresolved.buildId,
        unresolved.projectPath
      )
    ) ?: run {
      logger.error("Unable to find the main android sourceSet for the kotlin multiplatform module ${unresolved.projectPath}.")
      return emptyList()
    }

    return resolve(
      IdePreResolvedModuleLibraryImpl(
        buildId = unresolved.buildId,
        projectPath = unresolved.projectPath,
        variant = mainSourceSet,
        lintJar = unresolved.lintJar,
        sourceSet = IdeModuleSourceSetImpl(mainSourceSet, true)
      )
    )
  }

  fun resolve(unresolved: IdeUnresolvedModuleLibrary): List<IdeLibrary> {
    val targets = artifactResolver(unresolved.artifact)
      ?: return listOf(
        IdeJavaLibraryImpl(
          unresolved.artifact.path,
          null,
          unresolved.artifact.path,
          unresolved.artifact,
          null,
          null,
          null
        )
      )

    val unresolvedModuleBuilds = targets.filter { it.buildRoot != unresolved.buildId }
    if (unresolvedModuleBuilds.isNotEmpty()) {
      error("Unexpected resolved modules build id ${unresolvedModuleBuilds.map { it.buildRoot }.toSet()} != ${unresolved.buildId}")
    }

    val unresolvedModulePaths = targets.filter { it.path != unresolved.projectPath }
    if (unresolvedModulePaths.isNotEmpty()) {
      error("Unexpected resolved modules project path ${unresolvedModulePaths.map { it.path }.toSet()} != ${unresolved.projectPath}")
    }

    return targets.flatMap {
      resolve(
        IdePreResolvedModuleLibraryImpl(
          buildId = unresolved.buildId,
          projectPath = unresolved.projectPath,
          variant = unresolved.variant,
          lintJar = unresolved.lintJar,
          sourceSet = it.sourceSet
        )
      )
    }
  }

  return IdeResolvedLibraryTableImpl(
    libraries.map {
      when (it) {
        is IdeUnresolvedModuleLibrary -> resolve(it)
        is IdePreResolvedModuleLibrary -> resolve(it)
        is IdeUnresolvedKmpAndroidModuleLibrary -> resolve(it)
        is IdeUnresolvedUnknownLibrary -> listOf(it)
        is IdeUnresolvedAndroidLibrary -> listOf(it)
        is IdeUnresolvedJavaLibrary -> listOf(it)
      }
    }
  )
}

internal fun maybeLinkLibraryAndWorkOutLibraryLevel(projectDataNode: DataNode<ProjectData>, libraryData: LibraryData): LibraryLevel {
  // TODO(b/243008075): Work out the level of the library, if the library path is inside the module directory we treat
  // this as a Module level library. Otherwise we treat it as a Project level one.
  return when {
    !GradleProjectResolverUtil.linkProjectLibrary(projectDataNode, libraryData) -> LibraryLevel.MODULE
    else -> LibraryLevel.PROJECT
  }
}