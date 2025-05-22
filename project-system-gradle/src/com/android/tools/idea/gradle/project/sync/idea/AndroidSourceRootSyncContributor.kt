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

package com.android.tools.idea.gradle.project.sync.idea

import com.android.builder.model.v2.models.AndroidDsl
import com.android.builder.model.v2.models.AndroidProject
import com.android.builder.model.v2.models.BasicAndroidProject
import com.android.builder.model.v2.models.Versions
import com.android.tools.idea.gradle.project.sync.ModelFeature
import com.android.tools.idea.gradle.project.sync.ModelVersions
import com.android.tools.idea.gradle.project.sync.SyncActionOptions
import com.android.tools.idea.gradle.project.sync.convert
import com.android.tools.idea.gradle.project.sync.idea.entities.AndroidGradleSourceSetEntitySource
import com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase
import com.intellij.java.workspace.entities.JavaResourceRootPropertiesEntity
import com.intellij.java.workspace.entities.JavaSourceRootPropertiesEntity
import com.intellij.java.workspace.entities.javaResourceRoots
import com.intellij.java.workspace.entities.javaSourceRoots
import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType
import com.intellij.openapi.externalSystem.model.project.IExternalSystemSourceType
import com.intellij.openapi.externalSystem.util.Order
import com.intellij.openapi.progress.checkCanceled
import com.intellij.openapi.project.Project
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
import com.intellij.workspaceModel.ide.legacyBridge.impl.java.JAVA_RESOURCE_ROOT_ENTITY_TYPE_ID
import com.intellij.workspaceModel.ide.legacyBridge.impl.java.JAVA_SOURCE_ROOT_ENTITY_TYPE_ID
import com.intellij.workspaceModel.ide.legacyBridge.impl.java.JAVA_TEST_RESOURCE_ROOT_ENTITY_TYPE_ID
import com.intellij.workspaceModel.ide.legacyBridge.impl.java.JAVA_TEST_ROOT_ENTITY_TYPE_ID
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.model.ExternalProject
import org.jetbrains.plugins.gradle.model.GradleLightBuild
import org.jetbrains.plugins.gradle.model.GradleLightProject
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolverUtil
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncContributor
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncProjectConfigurator.project
import org.jetbrains.plugins.gradle.service.syncContributor.entitites.GradleBuildEntitySource
import org.jetbrains.plugins.gradle.service.syncContributor.entitites.GradleLinkedProjectEntitySource
import org.jetbrains.plugins.gradle.service.syncContributor.entitites.GradleProjectEntitySource
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.io.File
import kotlin.io.path.absolute

// Need the source type to be nullable because of how AndroidManifest is handled.
internal typealias SourceSetData = Pair<String, Map<out ExternalSystemSourceType?, Set<File>>>

internal class SyncContributorGradleProjectContext(
  val context: ProjectResolverContext,
  val project: Project,
  val buildModel: GradleLightBuild,
  val projectModel: GradleLightProject,
  val syncOptions: SyncActionOptions,
  val versions: ModelVersions,
) {
  private val virtualFileUrlManager = project.workspaceModel.getVirtualFileUrlManager()
  // Create an entity source representing each project root
  val rootIdeaProjectEntitySource = GradleLinkedProjectEntitySource(File(context.projectPath).toVirtualFileUrl())
  // For each build, create an entity source representing the Gradle build, as the root project source as parent
  val buildEntitySource = GradleBuildEntitySource(rootIdeaProjectEntitySource, buildModel.buildIdentifier.rootDir.toVirtualFileUrl())
  // For each project in the build, create an entity source representing the project, as the build entity source as the parent.
  val projectEntitySource = GradleProjectEntitySource(buildEntitySource, projectModel.projectDirectory.toVirtualFileUrl())
  val externalProject = context.getProjectModel(projectModel, ExternalProject::class.java)!!
  val basicAndroidProject = context.getProjectModel(projectModel, BasicAndroidProject::class.java)!!
  val androidProject = context.getProjectModel(projectModel, AndroidProject::class.java)!!
  val androidDsl = context.getProjectModel(projectModel, AndroidDsl::class.java)!!
  // TODO(b/410774404): HAS_SCREENSHOT_TESTS_SUPPORT is not the best name for even though it's what indicates the availability in the
  // new fields. Consider renaming.
  val useContainer: Boolean = versions[ModelFeature.HAS_SCREENSHOT_TESTS_SUPPORT]

  fun File.toVirtualFileUrl() = toVirtualFileUrl(virtualFileUrlManager)

  companion object {
    internal fun create(context: ProjectResolverContext,
                                project: Project,
                                syncOptions: SyncActionOptions,
                                buildModel: GradleLightBuild,
                                projectModel: GradleLightProject): SyncContributorGradleProjectContext? =
      context.getProjectModel(projectModel, Versions::class.java)?.convert()?.let {
        SyncContributorGradleProjectContext(
          context,
          project,
          buildModel,
          projectModel,
          syncOptions,
          it
        )
      }
  }
}

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

  private suspend fun configureModulesForSourceSets(context: ProjectResolverContext, storage: MutableEntityStorage) {
    val project = context.project()
    val syncOptions = context.getSyncOptions(project)

    val existingSourceSetEntities = MutableEntityStorage.from(storage.toSnapshot())
    val allAndroidContexts = context.allBuilds.flatMap { buildModel ->
      buildModel.projects.mapNotNull allProjects@{ projectModel ->
        checkCanceled()
        SyncContributorGradleProjectContext.create(context, project, syncOptions, buildModel, projectModel)
      }
    }
    val newModuleEntities =  allAndroidContexts.flatMap { it.getAllSourceSetModuleEntities() }

    newModuleEntities.forEach { newModuleEntity ->
      // Create or update the entity after doing all the mutations
      val existingEntity = existingSourceSetEntities.resolve(ModuleId(newModuleEntity.name))
      if (existingEntity == null) {
        existingSourceSetEntities addEntity newModuleEntity
      } else {
        existingSourceSetEntities.modifyModuleEntity(existingEntity) {
          this.entitySource = newModuleEntity.entitySource
          this.contentRoots = newModuleEntity.contentRoots
          this.exModuleOptions = newModuleEntity.exModuleOptions
          // Not modifying existing dependencies here because we don't have that info here yet.
        }
      }
    }
    // Only replace the android related source sets
    storage.replaceBySource({ it is AndroidGradleSourceSetEntitySource }, existingSourceSetEntities)
  }
}

// helpers
internal fun SyncContributorGradleProjectContext.getAllSourceSetModuleEntities(): List<ModuleEntity.Builder> {
  val allSourceSets = getAllSourceSetsFromModels()

  // This is the module name corresponding to the "holder" module
  val projectModuleName = resolveModuleName()
  val moduleEntitiesMap = mutableMapOf<String, ModuleEntity.Builder>()

  return allSourceSets.map  { (sourceSetName, typeToDirsMap) ->
    // For each source set in the project, create entity source and the actual entities.
    val entitySource = AndroidGradleSourceSetEntitySource(projectEntitySource, sourceSetName)
    val moduleName = "$projectModuleName.$sourceSetName"
    val newModuleEntity = findOrCreateModuleEntity(moduleName, entitySource, moduleEntitiesMap)

    // Create the content root and associate it with the module
    val contentRootEntity = createContentRootEntity(entitySource, typeToDirsMap)

    newModuleEntity.contentRoots += contentRootEntity
    newModuleEntity
  }.filter {
    // In some scenarios (for instance KMP), we end up with duplicate content roots, so ignoring those modules completely for now
    // TODO(b/384022658): It's possible this will not be an issue if we start merging source roots as it's being done in the platform side
    // using ContentRootIndex. That's left to later as it's not visible to us yet.
    it.contentRoots.hasNoDuplicates()
  }
}

private fun List<ContentRootEntity.Builder>.hasNoDuplicates() = this.distinctBy { it.url }.size == this.size

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
private fun SyncContributorGradleProjectContext.findOrCreateModuleEntity(
  name: String,
  entitySource: AndroidGradleSourceSetEntitySource,
  moduleEntitiesMap: MutableMap<String, ModuleEntity.Builder>
): ModuleEntity.Builder = moduleEntitiesMap.computeIfAbsent(name) {
  ModuleEntity(
    entitySource = entitySource,
    name = name,
    dependencies = listOf(
      InheritedSdkDependency,
      ModuleSourceDependency
    )
  ) {
    // Annotate the module with external system info (with gradle path, external system type, etc.)
    exModuleOptions = createModuleOptionsEntity(entitySource)
  }
}

private fun SyncContributorGradleProjectContext.createModuleOptionsEntity(source: EntitySource) = ExternalSystemModuleOptionsEntity(
  entitySource = source
) {
  externalSystem = GradleConstants.SYSTEM_ID.id
  linkedProjectId = GradleProjectResolverUtil.getModuleId(context, externalProject)
  linkedProjectPath = externalProject.projectDir.path
  rootProjectPath = context.projectPath

  externalSystemModuleGroup = externalProject.group
  externalSystemModuleVersion = externalProject.version
  externalSystemModuleType = GradleConstants.GRADLE_SOURCE_SET_MODULE_TYPE_KEY
}

private fun SyncContributorGradleProjectContext.createContentRootEntity(
  entitySource: EntitySource,
  typeToDirsMap: Map<out ExternalSystemSourceType?, Set<File>>
): ContentRootEntity.Builder {
  val contentRootUrl = typeToDirsMap.values.flatten().reduce { acc, file -> findCommonAncestor(acc, file) }

  return ContentRootEntity(
      entitySource = entitySource,
      url = contentRootUrl.toVirtualFileUrl(),
      excludedPatterns = emptyList()
    ) {
      // Create the source roots and exclusions by type
      val (excluded, roots) = typeToDirsMap.entries.partition { (sourceRootType, _) ->
        sourceRootType == ExternalSystemSourceType.EXCLUDED
      }

      excludedUrls += excluded.flatMap { (_, urls) ->
        urls.map {
          ExcludeUrlEntity(entitySource = entitySource, url = it.toVirtualFileUrl())
        }
      }

      sourceRoots += roots
        .filter { (type, _) -> type != null } // manifest directory can have null type
        .flatMap { (type, urls) ->
          urls.mapNotNull {
            // TODO(b/384022658): If the source root doesn't exist, we should create a watcher here via SourceFolderManager,
            // but it's not possible at this moment due to the module itself not being created yet.
            it.takeIf { it.exists() }?.let { createSourceRootEntity(it, type!!, entitySource) }
          }
        }
    }
  }

private fun SyncContributorGradleProjectContext.createSourceRootEntity(
  file: File,
  type: IExternalSystemSourceType,
  entitySource: EntitySource
): SourceRootEntity.Builder = SourceRootEntity(
  url = file.toVirtualFileUrl(),
  rootTypeId = type.toSourceRootTypeId(),
  entitySource = entitySource
) {
  if (type.isResource) {
    javaResourceRoots += JavaResourceRootPropertiesEntity(
      generated = type.isGenerated,
      relativeOutputPath = "",
      entitySource = entitySource
    )
  } else {
    javaSourceRoots += JavaSourceRootPropertiesEntity(
      generated = type.isGenerated,
      packagePrefix = "",
      entitySource = entitySource
    )
  }
}


// copied from platform and modified to have the sync contributor context
private fun SyncContributorGradleProjectContext.resolveModuleName(): String {
  val moduleName = resolveGradleProjectQualifiedName()
  val buildSrcGroup = context.getBuildSrcGroup(buildModel.name, buildModel.buildIdentifier)
  if (buildSrcGroup.isNullOrBlank()) {
    return moduleName
  }
  return "$buildSrcGroup.$moduleName"
}

private fun SyncContributorGradleProjectContext.resolveGradleProjectQualifiedName(): String {
  if (projectModel.path == ":") {
    return buildModel.name
  }
  if (projectModel.path.startsWith(":")) {
    return buildModel.name + projectModel.path.replace(":", ".")
  }
  return projectModel.path.replace(":", ".")
}

// copied from platform as is
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
    else -> throw NoWhenBranchMatchedException("Unexpected source type: $this")
  }
}