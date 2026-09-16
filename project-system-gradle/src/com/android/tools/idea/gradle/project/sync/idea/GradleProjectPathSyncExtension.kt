/*
 * Copyright (C) 2026 The Android Open Source Project
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
@file:Suppress("UnstableApiUsage")

package com.android.tools.idea.gradle.project.sync.idea

import com.android.tools.idea.gradle.model.impl.IdeModuleSourceSetImpl
import com.android.tools.idea.gradle.project.entities.GradleProjectPathEntity
import com.android.tools.idea.gradle.project.entities.GradleProjectPathEntitySource
import com.android.tools.idea.gradle.project.entities.GradleProjectPathSymbolicId
import com.android.tools.idea.gradle.project.entities.gradleProjectPath
import com.android.tools.idea.gradle.project.entities.modifyGradleProjectPathEntity
import com.android.tools.idea.projectsystem.gradle.GradleHolderProjectPath
import com.android.tools.idea.projectsystem.gradle.GradleProjectPath
import com.android.tools.idea.projectsystem.gradle.GradleSourceSetProjectPath
import com.android.tools.idea.projectsystem.gradle.toSourceSetPath
import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleEntityBuilder
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.jps.entities.exModuleOptions
import com.intellij.platform.workspace.jps.entities.modifyModuleEntity
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.entities
import com.intellij.util.PathUtil
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncExtension
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase
import org.jetbrains.plugins.gradle.util.GradleConstants

private val LOG = logger<GradleProjectPathSyncExtension>()

class GradleProjectPathSyncExtension : GradleSyncExtension {

  override fun updateProjectModel(
    context: ProjectResolverContext,
    syncStorage: MutableEntityStorage,
    projectStorage: MutableEntityStorage,
    phase: GradleSyncPhase,
  ) {
    if (!context.isPhasedSyncEnabled) return
    if (phase != GradleSyncPhase.SOURCE_SET_MODEL_PHASE) return
    populateGradleProjectPath(context, syncStorage, projectStorage)
  }

  companion object {
    private fun ModuleEntityBuilder.updateGradleProjectPath(projectStorage: MutableEntityStorage, gradleProjectPath: GradleProjectPath) {
      val existingEntity = projectStorage.resolve(GradleProjectPathSymbolicId(gradleProjectPath))
      if (existingEntity != null) {
        projectStorage.modifyGradleProjectPathEntity(existingEntity) { this.gradleProjectPath = gradleProjectPath }
        return
      }
      this@updateGradleProjectPath.gradleProjectPath =
        GradleProjectPathEntity(gradleProjectPath = gradleProjectPath, entitySource = GradleProjectPathEntitySource)
    }

    internal fun populateGradleProjectPath(
      context: ProjectResolverContext,
      syncStorage: MutableEntityStorage,
      projectStorage: MutableEntityStorage,
    ) {
      val allContexts =
        context.allBuilds.flatMap { buildModel ->
          buildModel.projects.mapNotNull { projectModel ->
            SyncContributorProjectContext(context, context.project, buildModel, projectModel)
          }
        }

      // all holder modules
      allContexts.forEach { context ->
        with(context) {
          val holderModuleEntity = projectStorage.resolve(ModuleId(resolveHolderModuleName())) ?: return@forEach
          // store in the project storage as it's a custom entity source - to avoid being replaced by .replaceBySource
          projectStorage.modifyModuleEntity(holderModuleEntity) {
            updateGradleProjectPath(
              projectStorage,
              GradleHolderProjectPath(PathUtil.toSystemIndependentName(buildModel.buildIdentifier.rootDir.path), projectModel.path),
            )
          }
        }
      }
      val contexts = checkNotNull(context.getUserData(SOURCE_SET_UPDATE_RESULT_KEY)).allAndroidProjectContexts
      // use syncStorage as the source of truth to update projectStorage

      val holderModuleEntityToGppMap: Map<String, GradleHolderProjectPath> =
        buildGppMapForAndroidProjects(contexts) + buildGppMapForJavaProjects(syncStorage, context)

      projectStorage.entities<ModuleEntity>().forEach {
        val sourceSetProjectPath = it.getSourceSetProjectPath(holderModuleEntityToGppMap) ?: return@forEach
        projectStorage.modifyModuleEntity(it) { updateGradleProjectPath(projectStorage, sourceSetProjectPath) }
      }
    }
  }
}

private fun ModuleEntity.getSourceSetProjectPath(
  holderModuleEntityToGppMap: Map<String, GradleHolderProjectPath>
): GradleSourceSetProjectPath? {
  val exModuleOptions = this.exModuleOptions ?: return null.also { LOG.debug("External module options not found for module ${this.name}") }
  if (exModuleOptions.externalSystemModuleType != GradleConstants.GRADLE_SOURCE_SET_MODULE_TYPE_KEY) return null
  val linkedProjectId = exModuleOptions.linkedProjectId ?: return null
  val sourceSetName = linkedProjectId.substringAfterLast(":")
  val gradleProjectPath = holderModuleEntityToGppMap[linkedProjectId.substringBeforeLast(":")] ?: return null
  val sourceSet = IdeModuleSourceSetImpl.wellKnownOrCreate(sourceSetName)
  return gradleProjectPath.toSourceSetPath(sourceSet)
}

/** Returns the mapping from ExternalSystemModuleOptions.linkedProjectID to [GradleProjectPath] for Android projects. */
private fun buildGppMapForAndroidProjects(
  allAndroidContexts: List<SyncContributorAndroidProjectContext>
): Map<String, GradleHolderProjectPath> =
  allAndroidContexts
    .mapNotNull { context ->
      context.holderModuleEntity.exModuleOptions?.linkedProjectId?.let {
        it to
          GradleHolderProjectPath(
            PathUtil.toSystemIndependentName(context.buildModel.buildIdentifier.rootDir.path),
            context.projectModel.path,
          )
      }
    }
    .toMap()

/** Returns the mapping from ExternalSystemModuleOptions.linkedProjectID to [GradleHolderProjectPath] for Java projects. */
private fun buildGppMapForJavaProjects(storage: EntityStorage, context: ProjectResolverContext): Map<String, GradleHolderProjectPath> =
  context.allBuilds
    .flatMap { buildModel ->
      buildModel.projects.mapNotNull { projectModel ->
        with(SyncContributorProjectContext(context, context.project, buildModel, projectModel)) {
          val entity = storage.resolve(ModuleId(resolveHolderModuleName())) ?: return@mapNotNull null
          val linkedProjectId = entity.exModuleOptions?.linkedProjectId ?: return@mapNotNull null
          linkedProjectId to
            GradleHolderProjectPath(PathUtil.toSystemIndependentName(buildModel.buildIdentifier.rootDir.path), projectModel.path)
        }
      }
    }
    .toMap()
