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
@file:Suppress("UnstableApiUsage")

package com.android.tools.idea.gradle.project.sync

import com.android.builder.model.v2.ide.SourceProvider
import com.android.builder.model.v2.models.BasicAndroidProject
import com.android.builder.model.v2.models.Versions
import com.android.tools.idea.gradle.model.IdeArtifactName
import com.android.tools.idea.gradle.model.IdeArtifactName.Companion.toWellKnownSourceSet
import com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase
import com.intellij.java.workspace.entities.JavaResourceRootPropertiesEntity
import com.intellij.java.workspace.entities.JavaSourceRootPropertiesEntity
import com.intellij.java.workspace.entities.javaResourceRoots
import com.intellij.java.workspace.entities.javaSourceRoots
import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType
import com.intellij.openapi.externalSystem.model.project.IExternalSystemSourceType
import com.intellij.openapi.externalSystem.util.Order
import com.intellij.openapi.progress.checkCanceled
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.ContentRootEntity
import com.intellij.platform.workspace.jps.entities.ExcludeUrlEntity
import com.intellij.platform.workspace.jps.entities.ExternalSystemModuleOptionsEntity
import com.intellij.platform.workspace.jps.entities.InheritedSdkDependency
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.jps.entities.ModuleSourceDependency
import com.intellij.platform.workspace.jps.entities.SourceRootEntity
import com.intellij.platform.workspace.jps.entities.SourceRootTypeId
import com.intellij.platform.workspace.jps.entities.exModuleOptions
import com.intellij.platform.workspace.jps.entities.modifyModuleEntity
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.impl.url.toVirtualFileUrl
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import com.intellij.workspaceModel.ide.legacyBridge.impl.java.JAVA_RESOURCE_ROOT_ENTITY_TYPE_ID
import com.intellij.workspaceModel.ide.legacyBridge.impl.java.JAVA_SOURCE_ROOT_ENTITY_TYPE_ID
import com.intellij.workspaceModel.ide.legacyBridge.impl.java.JAVA_TEST_RESOURCE_ROOT_ENTITY_TYPE_ID
import com.intellij.workspaceModel.ide.legacyBridge.impl.java.JAVA_TEST_ROOT_ENTITY_TYPE_ID
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.model.ExternalProject
import org.jetbrains.plugins.gradle.model.GradleLightBuild
import org.jetbrains.plugins.gradle.model.GradleLightProject
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolverUtil.getModuleId
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncContributor
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncProjectConfigurator.project
import org.jetbrains.plugins.gradle.service.syncContributor.entitites.GradleBuildEntitySource
import org.jetbrains.plugins.gradle.service.syncContributor.entitites.GradleLinkedProjectEntitySource
import org.jetbrains.plugins.gradle.service.syncContributor.entitites.GradleProjectEntitySource
import org.jetbrains.plugins.gradle.service.syncContributor.entitites.GradleSourceSetEntitySource
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.io.File
import kotlin.io.path.absolute

private typealias SourceSetData = Pair<String, Map<IExternalSystemSourceType, Set<File>>>

@ApiStatus.Internal
@Order(GradleSyncContributor.Order.SOURCE_ROOT_CONTRIBUTOR)
class AndroidSourceRootSyncContributor : GradleSyncContributor {
  override suspend fun onModelFetchPhaseCompleted(
    context: ProjectResolverContext,
    storage: MutableEntityStorage,
    phase: GradleModelFetchPhase,
  ) {
    if (context.isPhasedSyncEnabled) {
      if (phase == GradleModelFetchPhase.PROJECT_SOURCE_SET_PHASE) {
        configureModulesForSourceSets(context, storage)
      }
    }
  }

  @Suppress("DEPRECATION") // Need to be backwards compatible here
  fun getAllSourceSets(context: ProjectResolverContext, projectModel: GradleLightProject): List<SourceSetData> {

    val versions = context.getProjectModel(projectModel, Versions::class.java)?.convert() ?: return emptyList()
    val mainSourceSet = context.getProjectModel(projectModel, BasicAndroidProject::class.java)?.mainSourceSet ?: return emptyList()

    val sourceSets = mutableListOf<SourceSetData>()
    mainSourceSet.sourceProvider?.let { sourceSets += createSourceSetData(IdeArtifactName.MAIN, it, isProduction = true)}
    // TODO(b/410774404): HAS_SCREENSHOT_TESTS_SUPPORT is not the best name for even though it's what indicates the availability in the
    // new fields. Consider renaming.
    if (versions[ModelFeature.HAS_SCREENSHOT_TESTS_SUPPORT]) {
      (mainSourceSet.deviceTestSourceProviders + mainSourceSet.hostTestSourceProviders).entries.forEach { (name, sourceProvider) ->
        sourceSets += createSourceSetData(convertArtifactName(name), sourceProvider, isProduction = false)
      }
    } else {
      mainSourceSet.androidTestSourceProvider?.let {
        sourceSets += createSourceSetData(IdeArtifactName.ANDROID_TEST, it, isProduction = false)
      }
      mainSourceSet.unitTestSourceProvider?.let {
        sourceSets += createSourceSetData(IdeArtifactName.UNIT_TEST, it, isProduction = false)
      }
    }
    return sourceSets
  }

  private suspend fun configureModulesForSourceSets(context: ProjectResolverContext, storage: MutableEntityStorage) {
    val virtualFileUrlManager = context.project().workspaceModel.getVirtualFileUrlManager()

    // Create an entity source representing the IDE project
    val linkedProjectRootPath = File(context.projectPath).toPath()
    val linkedProjectRootUrl = linkedProjectRootPath.toVirtualFileUrl(virtualFileUrlManager)
    val linkedProjectEntitySource = GradleLinkedProjectEntitySource(linkedProjectRootUrl)


    val newEntities = MutableEntityStorage.from(storage.toSnapshot())
    context.allBuilds.forEach { buildModel ->
      // For each build, create an entity source representing the Gradle build, as the root project source as parent
      val buildRootPath = buildModel.buildIdentifier.rootDir.toPath()
      val buildRootUrl = buildRootPath.toVirtualFileUrl(virtualFileUrlManager)
      val buildEntitySource = GradleBuildEntitySource(linkedProjectEntitySource, buildRootUrl)

      buildModel.projects.forEach { projectModel ->
        checkCanceled()

        // For each project in the build, create an entity source representing the project, as the build entity source as the parent.
        val projectRootPath = projectModel.projectDirectory.toPath()
        val projectRootUrl = projectRootPath.toVirtualFileUrl(virtualFileUrlManager)
        val projectEntitySource = GradleProjectEntitySource(buildEntitySource, projectRootUrl)

        val externalProject = context.getProjectModel(projectModel, ExternalProject::class.java) ?: return@forEach

        getAllSourceSets(context, projectModel).forEach  { (sourceSetName, typeToDirsMap) ->
          // For each source set in the project, create entity source and the actual entities.
          val entitySource = GradleSourceSetEntitySource(projectEntitySource, sourceSetName)
          // This is the module name corresponding to the "holder" module
          val projectModuleName = resolveModuleName(context, buildModel, projectModel)

          val newModuleEntity = createModuleEntity(entitySource, projectModuleName, sourceSetName)

          // Annotate the module with external system info (with gradle path, external system type, etc.)
          newModuleEntity.exModuleOptions = createModuleOptionsEntity(entitySource, context, externalProject)


          // Create the content root and associate it with the module
          val contentRootEntity = createContentRootEntity(entitySource, typeToDirsMap, virtualFileUrlManager)
          newModuleEntity.contentRoots = listOf(contentRootEntity)


          // Create the source roots and exclusions by type
          val (excludedUrls, sourceRoots) = typeToDirsMap.entries.partition { (sourceRootType, _) ->
            sourceRootType == ExternalSystemSourceType.EXCLUDED
          }

          contentRootEntity.excludedUrls = excludedUrls.flatMap { (_, urls) ->
            urls.map {
              ExcludeUrlEntity(entitySource = entitySource, url = it.toVirtualFileUrl(virtualFileUrlManager))
            }
          }
          contentRootEntity.sourceRoots = sourceRoots.flatMap { (type, urls) ->
            urls.mapNotNull {
              // TODO(b/384022658): If the source root doesn't exist, we should create a watcher here via SourceFolderManager,
              // but it's not possible at this moment due to the module itself not being created yet.
              it.takeIf { it.exists() }?.let { createSourceRootEntity(it, virtualFileUrlManager, type, entitySource) }
            }
          }

          // Create or update the entity after doing all the mutations
          val existingEntity = newEntities.resolve(ModuleId(newModuleEntity.name))
          if (existingEntity == null) {
            newEntities addEntity newModuleEntity
          } else {
            newEntities.modifyModuleEntity(existingEntity) {
              this.entitySource = newModuleEntity.entitySource
              this.contentRoots = newModuleEntity.contentRoots
              this.exModuleOptions = newModuleEntity.exModuleOptions
              // Not modifying existing dependencies here because we don't have that info here yet.
            }
          }
        }
      }
    }
    storage.replaceBySource({ it is GradleSourceSetEntitySource }, newEntities)
  }
}

// helpers
private fun createSourceSetData(name: IdeArtifactName, provider: SourceProvider, isProduction: Boolean): SourceSetData {
  val sourceSetName = name.toWellKnownSourceSet().sourceSetName
  val sourceDirectories = (provider.javaDirectories + provider.kotlinDirectories).toSet()
  val resourceDirectories = provider.resourcesDirectories.toSet() - sourceDirectories
  return sourceSetName to mapOf(
    (if (isProduction) ExternalSystemSourceType.SOURCE else ExternalSystemSourceType.TEST)
      to sourceDirectories,
    (if (isProduction) ExternalSystemSourceType.RESOURCE else ExternalSystemSourceType.TEST_RESOURCE)
      to resourceDirectories
  )
}

private fun findCommonAncestor(file1: File, file2: File) : File {
  val path1 = file1.toPath().absolute().normalize()
  val path2 = file2.toPath().absolute().normalize()
  if (path1.root != path2.root) return File("/")

  @Suppress("PathAsIterable") // Yes, I actually want to iterate the parts of the paths
  return path1.zip(path2).fold(path1.root) {  acc, (part1, part2) ->
    if (part1 != part2) return@fold acc
    acc.resolve(part1)
  }.toFile()
}


// entity creation
private fun createModuleEntity(
  entitySource: GradleSourceSetEntitySource,
  projectModuleName: String,
  sourceSetName: String
): ModuleEntity.Builder = ModuleEntity(
  entitySource = entitySource,
  name = "$projectModuleName.$sourceSetName",
  dependencies = listOf(
    InheritedSdkDependency,
    ModuleSourceDependency
  )
)

private fun createModuleOptionsEntity(
  source: EntitySource,
  context: ProjectResolverContext,
  externalProject: ExternalProject
) = ExternalSystemModuleOptionsEntity(
  entitySource = source
) {
  externalSystem = GradleConstants.SYSTEM_ID.id
  linkedProjectId = getModuleId(context, externalProject)
  linkedProjectPath = externalProject.projectDir.path
  rootProjectPath = context.projectPath

  externalSystemModuleGroup = externalProject.group
  externalSystemModuleVersion = externalProject.version
  externalSystemModuleType = GradleConstants.GRADLE_SOURCE_SET_MODULE_TYPE_KEY
}

private fun createContentRootEntity(
  entitySource: GradleSourceSetEntitySource,
  typeToDirsMap: Map<IExternalSystemSourceType, Set<File>>,
  virtualFileUrlManager: VirtualFileUrlManager
): ContentRootEntity.Builder {
  val contentRootUrl = typeToDirsMap.values.flatten().reduce { acc, file -> findCommonAncestor(acc, file) }

  return ContentRootEntity(entitySource = entitySource,
                           url = contentRootUrl.toVirtualFileUrl(virtualFileUrlManager),
                           excludedPatterns = emptyList())
}


private fun createSourceRootEntity(
  file: File,
  virtualFileUrlManager: VirtualFileUrlManager,
  type: IExternalSystemSourceType,
  entitySource: EntitySource
): SourceRootEntity.Builder = SourceRootEntity(
  url = file.toVirtualFileUrl(virtualFileUrlManager),
  rootTypeId = type.toSourceRootTypeId(),
  entitySource = entitySource
) {
  if (type.isResource) {
    javaResourceRoots += JavaResourceRootPropertiesEntity(
      generated = type.isGenerated,
      relativeOutputPath = "",
      entitySource = entitySource
    )
  }
  else {
    javaSourceRoots += JavaSourceRootPropertiesEntity(
      generated = type.isGenerated,
      packagePrefix = "",
      entitySource = entitySource
    )
  }
}


// copied from platform, ideally the methods below should be visible instead
private fun resolveModuleName(
  context: ProjectResolverContext,
  buildModel: GradleLightBuild,
  projectModel: GradleLightProject,
): String {
  val moduleName = resolveGradleProjectQualifiedName(buildModel, projectModel)
  val buildSrcGroup = context.getBuildSrcGroup(buildModel.name, buildModel.buildIdentifier)
  if (buildSrcGroup.isNullOrBlank()) {
    return moduleName
  }
  return "$buildSrcGroup.$moduleName"
}

private fun resolveGradleProjectQualifiedName(
  buildModel: GradleLightBuild,
  projectModel: GradleLightProject,
): String {
  if (projectModel.path == ":") {
    return buildModel.name
  }
  if (projectModel.path.startsWith(":")) {
    return buildModel.name + projectModel.path.replace(":", ".")
  }
  return projectModel.path.replace(":", ".")
}

private fun IExternalSystemSourceType.toSourceRootTypeId(): SourceRootTypeId {
  return when (ExternalSystemSourceType.from(this)) {
    ExternalSystemSourceType.SOURCE -> JAVA_SOURCE_ROOT_ENTITY_TYPE_ID
    ExternalSystemSourceType.SOURCE_GENERATED -> JAVA_SOURCE_ROOT_ENTITY_TYPE_ID
    ExternalSystemSourceType.TEST -> JAVA_TEST_ROOT_ENTITY_TYPE_ID
    ExternalSystemSourceType.TEST_GENERATED -> JAVA_TEST_ROOT_ENTITY_TYPE_ID
    ExternalSystemSourceType.RESOURCE -> JAVA_RESOURCE_ROOT_ENTITY_TYPE_ID
    ExternalSystemSourceType.RESOURCE_GENERATED -> JAVA_RESOURCE_ROOT_ENTITY_TYPE_ID
    ExternalSystemSourceType.TEST_RESOURCE -> JAVA_TEST_RESOURCE_ROOT_ENTITY_TYPE_ID
    ExternalSystemSourceType.TEST_RESOURCE_GENERATED -> JAVA_TEST_RESOURCE_ROOT_ENTITY_TYPE_ID
    else -> throw NoWhenBranchMatchedException("Unexpected source type: ${this}")
  }
}
