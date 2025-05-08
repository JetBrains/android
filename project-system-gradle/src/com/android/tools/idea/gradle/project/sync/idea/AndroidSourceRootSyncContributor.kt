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

import com.android.builder.model.v2.ide.AndroidArtifact
import com.android.builder.model.v2.ide.BasicArtifact
import com.android.builder.model.v2.ide.JavaArtifact
import com.android.builder.model.v2.ide.SourceProvider
import com.android.builder.model.v2.models.AndroidDsl
import com.android.builder.model.v2.models.AndroidProject
import com.android.builder.model.v2.models.BasicAndroidProject
import com.android.builder.model.v2.models.Versions
import com.android.tools.idea.gradle.model.IdeArtifactName
import com.android.tools.idea.gradle.model.IdeArtifactName.Companion.toWellKnownSourceSet
import com.android.tools.idea.gradle.project.sync.ModelFeature
import com.android.tools.idea.gradle.project.sync.ModelVersions
import com.android.tools.idea.gradle.project.sync.Modules
import com.android.tools.idea.gradle.project.sync.SingleVariantSyncActionOptions
import com.android.tools.idea.gradle.project.sync.SyncActionOptions
import com.android.tools.idea.gradle.project.sync.convert
import com.android.tools.idea.gradle.project.sync.convertArtifactName
import com.android.tools.idea.gradle.project.sync.getDefaultVariant
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
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolverUtil
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncContributor
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncProjectConfigurator.project
import org.jetbrains.plugins.gradle.service.syncContributor.entitites.GradleBuildEntitySource
import org.jetbrains.plugins.gradle.service.syncContributor.entitites.GradleLinkedProjectEntitySource
import org.jetbrains.plugins.gradle.service.syncContributor.entitites.GradleProjectEntitySource
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.io.File
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.plus
import kotlin.io.path.absolute
import kotlin.takeIf

// Need the source type to be nullable because of how AndroidManifest is handled.
private typealias SourceSetData = Pair<String, Map<out ExternalSystemSourceType?, Set<File>>>

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

  suspend fun getAllSourceSets(context: ProjectResolverContext, projectModel: GradleLightProject): List<SourceSetData> {
    val versions = context.getProjectModel(projectModel, Versions::class.java)?.convert() ?: return emptyList()
    val basicAndroidProject = context.getProjectModel(projectModel, BasicAndroidProject::class.java) ?: return emptyList()
    val androidProject = context.getProjectModel(projectModel, AndroidProject::class.java) ?: return emptyList()
    val androidDsl = context.getProjectModel(projectModel, AndroidDsl::class.java) ?: return emptyList()
    val variantName = getVariantName(
      context.getSyncOptions(context.project()),
      projectModel,
      basicAndroidProject,
      androidDsl
    ) ?: return emptyList()

    // TODO(b/410774404): HAS_SCREENSHOT_TESTS_SUPPORT is not the best name for even though it's what indicates the availability in the
    // new fields. Consider renaming.
    val useContainer = versions[ModelFeature.HAS_SCREENSHOT_TESTS_SUPPORT]

    val (buildType, flavors) = basicAndroidProject.variants
      .singleOrNull { it.name == variantName }
      .let { (it?.buildType) to it?.productFlavors.orEmpty() }


    // TODO(b/384022658): Handle test fixtures and any other potentially relevant sources
    return getSourceSetDataForBasicAndroidProject(useContainer, basicAndroidProject, variantName, buildType, flavors, versions) +
           getSourceSetDataForAndroidProject(useContainer, androidProject, variantName)
  }


  @Suppress("DEPRECATION") // Need to be backwards compatible here
  private fun getSourceSetDataForBasicAndroidProject(useContainer: Boolean,
                                                     basicAndroidProject: BasicAndroidProject,
                                                     variantName: String,
                                                     buildTypeForVariant: String?,
                                                     productFlavorsForVariant: List<String>,
                                                     versions: ModelVersions): List<SourceSetData> {
    val sourceSets = mutableListOf<SourceSetData>()

    val containers =
      basicAndroidProject.mainSourceSet?.let { listOf(it) }.orEmpty() +
      basicAndroidProject.buildTypeSourceSets
        .filter { it.sourceProvider?.name == buildTypeForVariant } +
      basicAndroidProject.productFlavorSourceSets
        .filter { it.sourceProvider?.name in productFlavorsForVariant }

    fun processBasicArtifact(artifact: BasicArtifact, name: IdeArtifactName, isProduction: Boolean) {
      artifact.variantSourceProvider?.let { sourceSets += createSourceSetDataForSourceProvider(name, it, isProduction, versions) }
      artifact.multiFlavorSourceProvider?.let { sourceSets += createSourceSetDataForSourceProvider(name, it, isProduction, versions) }
    }

    basicAndroidProject.variants
      .filter { it.name == variantName }
      .forEach {
        processBasicArtifact(it.mainArtifact, IdeArtifactName.MAIN, isProduction = true)
        containers.forEach {
          it.sourceProvider?.let { sourceSets += createSourceSetDataForSourceProvider(IdeArtifactName.MAIN, it, isProduction = true, versions) }
        }

        if (useContainer) {
          (it.deviceTestArtifacts + it.hostTestArtifacts).entries.forEach { (name, artifact) ->
            val artifactName = convertArtifactName(name)
            processBasicArtifact(artifact, artifactName, isProduction = false)
            containers.forEach {
              (it.deviceTestSourceProviders + it.hostTestSourceProviders)[name]?.let {
                sourceSets += createSourceSetDataForSourceProvider(convertArtifactName(name), it, isProduction = false, versions)
              }
            }
          }
        } else {
          it.androidTestArtifact?.let {
            processBasicArtifact(it, IdeArtifactName.UNIT_TEST, isProduction = false)
            containers.forEach {
              it.androidTestSourceProvider?.let {
                sourceSets += createSourceSetDataForSourceProvider(IdeArtifactName.ANDROID_TEST, it, isProduction = false, versions)
              }
            }
          }
          it.unitTestArtifact?.let {
            processBasicArtifact(it, IdeArtifactName.UNIT_TEST, isProduction = false)
            containers.forEach {
              it.unitTestSourceProvider?.let {
                sourceSets += createSourceSetDataForSourceProvider(IdeArtifactName.UNIT_TEST, it, isProduction = false, versions)
              }
            }

          }
        }
      }

    return sourceSets
  }

  @Suppress("DEPRECATION") // Need to be backwards compatible here
  private fun getSourceSetDataForAndroidProject(useContainer: Boolean, androidProject: AndroidProject, selectedVariantName: String): List<SourceSetData>{
    val sourceSets = mutableListOf<SourceSetData>()

    androidProject.variants
      .filter { it.name == selectedVariantName }
      .forEach { variant ->
        sourceSets += createSourceSetDataForAndroidArtifact(IdeArtifactName.MAIN, variant.mainArtifact, isProduction = true)
        if (useContainer) {
          variant.deviceTestArtifacts.entries.forEach { (name, artifact) ->
            sourceSets += createSourceSetDataForAndroidArtifact(convertArtifactName(name), artifact, isProduction = false)
          }
          variant.hostTestArtifacts.entries.forEach { (name, artifact) ->
            sourceSets += createSourceSetDataForTestJavaArtifact(convertArtifactName(name), artifact)
          }
        }
        else {
          variant.androidTestArtifact?.let {
            sourceSets += createSourceSetDataForAndroidArtifact(IdeArtifactName.ANDROID_TEST, it, isProduction = false)
          }
          variant.unitTestArtifact?.let {
            sourceSets += createSourceSetDataForTestJavaArtifact(IdeArtifactName.UNIT_TEST, it)
          }
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


    val existingEntities = MutableEntityStorage.from(storage.toSnapshot())
    val newModuleEntities = context.allBuilds.flatMap { buildModel ->
      buildModel.projects.flatMap { projectModel ->
        checkCanceled()
        getModuleEntities(context, virtualFileUrlManager, linkedProjectEntitySource, buildModel, projectModel, getAllSourceSets(context, projectModel))
      }
    }
    newModuleEntities.forEach { newModuleEntity ->
      // Create or update the entity after doing all the mutations
      val existingEntity = existingEntities.resolve(ModuleId(newModuleEntity.name))
      if (existingEntity == null) {
        existingEntities addEntity newModuleEntity
      } else {
        existingEntities.modifyModuleEntity(existingEntity) {
          this.entitySource = newModuleEntity.entitySource
          this.contentRoots = newModuleEntity.contentRoots
          this.exModuleOptions = newModuleEntity.exModuleOptions
          // Not modifying existing dependencies here because we don't have that info here yet.
        }
      }
    }
    // Only replace the android related source sets
    storage.replaceBySource({ it is AndroidGradleSourceSetEntitySource }, existingEntities)
  }
}

// helpers
private fun getModuleEntities(context: ProjectResolverContext,
                              virtualFileUrlManager: VirtualFileUrlManager,
                              linkedProjectEntitySource: GradleLinkedProjectEntitySource,
                              buildModel: GradleLightBuild,
                              projectModel: GradleLightProject,
                              allSourceSets: List<SourceSetData>): Collection<ModuleEntity.Builder> {
  // For each build, create an entity source representing the Gradle build, as the root project source as parent
  val buildRootPath = buildModel.buildIdentifier.rootDir.toPath()
  val buildRootUrl = buildRootPath.toVirtualFileUrl(virtualFileUrlManager)
  val buildEntitySource = GradleBuildEntitySource(linkedProjectEntitySource, buildRootUrl)

  // For each project in the build, create an entity source representing the project, as the build entity source as the parent.
  val projectRootPath = projectModel.projectDirectory.toPath()
  val projectRootUrl = projectRootPath.toVirtualFileUrl(virtualFileUrlManager)
  val projectEntitySource = GradleProjectEntitySource(buildEntitySource, projectRootUrl)

  // This is the module name corresponding to the "holder" module
  val projectModuleName = resolveModuleName(context, buildModel, projectModel)

  val externalProject = context.getProjectModel(projectModel, ExternalProject::class.java) ?: return emptyList()

  val moduleEntitiesMap = mutableMapOf<String, ModuleEntity.Builder>()

  return allSourceSets.map  { (sourceSetName, typeToDirsMap) ->
    // For each source set in the project, create entity source and the actual entities.
    val entitySource = AndroidGradleSourceSetEntitySource(projectEntitySource, sourceSetName)
    val moduleName = "$projectModuleName.$sourceSetName"
    val newModuleEntity = findOrCreateModuleEntity(context, externalProject, entitySource, moduleName,moduleEntitiesMap)

    // Create the content root (if it doesn't exist yet) and associate it with the module
    val contentRootEntity = createContentRootEntity(entitySource, typeToDirsMap, virtualFileUrlManager)

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

private fun createSourceSetDataForSourceProvider(name: IdeArtifactName,
                                                 provider: SourceProvider,
                                                 isProduction: Boolean,
                                                 versions: ModelVersions): List<SourceSetData> {
  val sourceDirectories = (
    provider.javaDirectories +
    provider.kotlinDirectories +
    provider.aidlDirectories.orEmpty() +
    provider.renderscriptDirectories.orEmpty() +
    provider.shadersDirectories.orEmpty()).toSet()

  // TODO(b/384022658): Handle custom directories
  val resourceDirectories =
    provider.resourcesDirectories.toSet() +
    provider.resDirectories.orEmpty() +
    provider.mlModelsDirectories.orEmpty() +
    provider.assetsDirectories.orEmpty() + (
      if (versions[ModelFeature.HAS_BASELINE_PROFILE_DIRECTORIES])
        provider.baselineProfileDirectories.orEmpty()
      else
        emptySet()
    ) - sourceDirectories // exclude source directories in case they are shared

  val sourceSetName = name.toWellKnownSourceSet().sourceSetName
  return  listOf(
    sourceSetName to mapOf(
      (if (isProduction) ExternalSystemSourceType.SOURCE else ExternalSystemSourceType.TEST)
        to sourceDirectories,
      (if (isProduction) ExternalSystemSourceType.RESOURCE else ExternalSystemSourceType.TEST_RESOURCE)
        to resourceDirectories,
    ) +  provider.manifestFile?.parentFile?.let { mapOf(null to setOf(it)) }.orEmpty()
  )
}

private fun createSourceSetDataForAndroidArtifact(name: IdeArtifactName, artifact: AndroidArtifact, isProduction: Boolean): List<SourceSetData> {
  val sourceSetName = name.toWellKnownSourceSet().sourceSetName
  return artifact.generatedSourceFolders.map {
    sourceSetName to mapOf(
      (if (isProduction) ExternalSystemSourceType.SOURCE else ExternalSystemSourceType.TEST) to setOf(it)
    )
  } + artifact.generatedResourceFolders.map {
    sourceSetName to mapOf(
      (if (isProduction) ExternalSystemSourceType.RESOURCE else ExternalSystemSourceType.TEST_RESOURCE) to setOf(it)
    )
  }
}

private fun createSourceSetDataForTestJavaArtifact(name: IdeArtifactName, artifact: JavaArtifact): List<SourceSetData> {
  val sourceSetName = name.toWellKnownSourceSet().sourceSetName
  return artifact.generatedSourceFolders.map {
    sourceSetName to mapOf(
      ExternalSystemSourceType.TEST to setOf(it)
    )
  }
}


private fun getVariantName(
  syncOptions: SyncActionOptions,
  gradleProject: GradleLightProject,
  basicAndroidProject: BasicAndroidProject,
  androidDsl: AndroidDsl
): String? =
  when (syncOptions) {
   is SingleVariantSyncActionOptions ->
     syncOptions.switchVariantRequest.takeIf { it?.moduleId == gradleProject.moduleId() }?.variantName // newly user-selected variant
     ?: syncOptions.selectedVariants.getSelectedVariant(gradleProject.moduleId()) // variants selected by the last sync
   else -> null
 } ?: basicAndroidProject.variants.toList().getDefaultVariant(androidDsl.buildTypes, androidDsl.productFlavors) // default variant

private fun GradleLightProject.moduleId() = Modules.createUniqueModuleId(projectIdentifier.buildIdentifier.rootDir, path)

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
private fun findOrCreateModuleEntity(
  context: ProjectResolverContext,
  externalProject: ExternalProject,
  entitySource: EntitySource,
  name: String,
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
    exModuleOptions = createModuleOptionsEntity(entitySource, context, externalProject)
  }
}

private fun createModuleOptionsEntity(
  source: EntitySource,
  context: ProjectResolverContext,
  externalProject: ExternalProject
) = ExternalSystemModuleOptionsEntity(
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

private fun createContentRootEntity(
  entitySource: EntitySource,
  typeToDirsMap: Map<out ExternalSystemSourceType?, Set<File>>,
  virtualFileUrlManager: VirtualFileUrlManager,
): ContentRootEntity.Builder {
  val contentRootUrl = typeToDirsMap.values.flatten().reduce { acc, file -> findCommonAncestor(acc, file) }

  return ContentRootEntity(
      entitySource = entitySource,
      url = contentRootUrl.toVirtualFileUrl(virtualFileUrlManager),
      excludedPatterns = emptyList()
    ) {
      // Create the source roots and exclusions by type
      val (excluded, roots) = typeToDirsMap.entries.partition { (sourceRootType, _) ->
        sourceRootType == ExternalSystemSourceType.EXCLUDED
      }

      excludedUrls += excluded.flatMap { (_, urls) ->
        urls.map {
          ExcludeUrlEntity(entitySource = entitySource, url = it.toVirtualFileUrl(virtualFileUrlManager))
        }
      }

      sourceRoots += roots
        .filter { (type, _) -> type != null } // manifest directory can have null type
        .flatMap { (type, urls) ->
          urls.mapNotNull {
            // TODO(b/384022658): If the source root doesn't exist, we should create a watcher here via SourceFolderManager,
            // but it's not possible at this moment due to the module itself not being created yet.
            it.takeIf { it.exists() }?.let { createSourceRootEntity(it, virtualFileUrlManager, type!!, entitySource) }
          }
        }
    }
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
  } else {
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
    else -> throw NoWhenBranchMatchedException("Unexpected source type: $this")
  }
}
