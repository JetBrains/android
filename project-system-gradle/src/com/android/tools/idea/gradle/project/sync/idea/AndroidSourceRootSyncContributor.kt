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
import com.android.ide.gradle.model.GradlePluginModel
import com.android.ide.gradle.model.dependencies.DeclaredDependencies
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.model.IdeAndroidProject
import com.android.tools.idea.gradle.model.IdeArtifactName
import com.android.tools.idea.gradle.model.IdeArtifactName.Companion.toWellKnownSourceSet
import com.android.tools.idea.gradle.model.impl.IdeVariantCoreImpl
import com.android.tools.idea.gradle.model.impl.IdeAndroidProjectImpl
import com.android.tools.idea.gradle.project.entities.GradleAndroidModelEntity
import com.android.tools.idea.gradle.project.entities.GradleModuleModelEntity
import com.android.tools.idea.gradle.project.entities.gradleAndroidModel
import com.android.tools.idea.gradle.project.entities.gradleModuleModel
import com.android.tools.idea.gradle.project.model.GradleAndroidModel
import com.android.tools.idea.gradle.project.model.GradleAndroidModelData
import com.android.tools.idea.gradle.project.model.GradleModuleModel
import com.android.tools.idea.gradle.project.sync.ModelFeature
import com.android.tools.idea.gradle.project.sync.ModelVersions
import com.android.tools.idea.gradle.project.sync.SyncActionOptions
import com.android.tools.idea.gradle.project.sync.computeVariantNameToBeSynced
import com.android.tools.idea.gradle.project.sync.convert
import com.android.tools.idea.gradle.project.sync.idea.AndroidGradleProjectResolver.Companion.toIdeDeclaredDependencies
import com.android.tools.idea.projectsystem.gradle.LINKED_ANDROID_GRADLE_MODULE_GROUP
import com.android.tools.idea.projectsystem.gradle.LinkedAndroidGradleModuleGroup
import com.android.tools.idea.sdk.AndroidSdks
import com.intellij.java.workspace.entities.JavaModuleSettingsEntity
import com.intellij.java.workspace.entities.JavaModuleSettingsEntityBuilder
import com.intellij.java.workspace.entities.JavaResourceRootPropertiesEntity
import com.intellij.java.workspace.entities.JavaSourceRootPropertiesEntity
import com.intellij.java.workspace.entities.javaResourceRoots
import com.intellij.java.workspace.entities.javaSettings
import com.intellij.java.workspace.entities.javaSourceRoots
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType
import com.intellij.openapi.externalSystem.model.project.IExternalSystemSourceType
import com.intellij.openapi.externalSystem.service.project.manage.SourceFolderManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModulePointerManager
import com.intellij.openapi.progress.checkCanceled
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.io.CanonicalPathPrefixTree
import com.intellij.openapi.util.io.toCanonicalPath
import com.intellij.openapi.vfs.VfsUtilCore.pathToUrl
import com.intellij.platform.workspace.jps.entities.ContentRootEntity
import com.intellij.platform.workspace.jps.entities.ExcludeUrlEntity
import com.intellij.platform.workspace.jps.entities.ExternalSystemModuleOptionsEntity
import com.intellij.platform.workspace.jps.entities.InheritedSdkDependency
import com.intellij.platform.workspace.jps.entities.ContentRootEntityBuilder
import com.intellij.platform.workspace.jps.entities.ModuleEntityBuilder
import com.intellij.platform.workspace.jps.entities.SourceRootEntityBuilder
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.jps.entities.ModuleSourceDependency
import com.intellij.platform.workspace.jps.entities.SdkDependency
import com.intellij.platform.workspace.jps.entities.SdkId
import com.intellij.platform.workspace.jps.entities.SourceRootEntity
import com.intellij.platform.workspace.jps.entities.SourceRootTypeId
import com.intellij.platform.workspace.jps.entities.TestModulePropertiesEntity
import com.intellij.platform.workspace.jps.entities.exModuleOptions
import com.intellij.platform.workspace.jps.entities.modifyModuleEntity
import com.intellij.platform.workspace.jps.entities.testProperties
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.ImmutableEntityStorage
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.toBuilder
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.workspaceModel.ide.legacyBridge.impl.java.JAVA_RESOURCE_ROOT_ENTITY_TYPE_ID
import com.intellij.workspaceModel.ide.legacyBridge.impl.java.JAVA_SOURCE_ROOT_ENTITY_TYPE_ID
import com.intellij.workspaceModel.ide.legacyBridge.impl.java.JAVA_TEST_RESOURCE_ROOT_ENTITY_TYPE_ID
import com.intellij.workspaceModel.ide.legacyBridge.impl.java.JAVA_TEST_ROOT_ENTITY_TYPE_ID
import org.gradle.tooling.model.GradleProject
import org.gradle.tooling.model.idea.IdeaModule
import org.jetbrains.android.sdk.AndroidSdkType
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.module.JpsModuleSourceRootType
import org.jetbrains.plugins.gradle.model.ExternalProject
import org.jetbrains.plugins.gradle.model.GradleLightBuild
import org.jetbrains.plugins.gradle.model.GradleLightProject
import org.jetbrains.plugins.gradle.service.project.GradleContentRootIndex
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolverUtil
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncContributor
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncExtension
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase
import org.jetbrains.plugins.gradle.service.syncAction.impl.bridge.GradleBridgeEntitySource
import org.jetbrains.plugins.gradle.service.syncAction.virtualFileUrl
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.io.File
import java.nio.file.Path

private val LOG = logger<AndroidSourceRootSyncContributor>()

// Need the source type to be nullable because of how AndroidManifest is handled.
internal typealias SourceSetData = Pair<IdeArtifactName, Map<out ExternalSystemSourceType?, Set<File>>>
internal typealias ModuleAction = (Module) -> Unit

/** This class is used to keep track of */
internal data class SourceSetUpdateResult(
  /** Represents list of module actions by name. Mutable because actions are removed as they are performed. */
  val allModuleActions: Map<String, List<ModuleAction>>,
  val allAndroidProjectContexts: List<SyncContributorAndroidProjectContext>,

  /** To be used with [MutableEntityStorage.replaceBySource], to make sure we only update relevant entities. */
  val updatedStorage: EntityStorage,
  val knownEntitySources: Set<EntitySource>,
)

private fun updateStorage(
  storage: ImmutableEntityStorage,
  result: SourceSetUpdateResult,
): ImmutableEntityStorage {
  val builder = storage.toBuilder()
  // Only replace the android related source sets
  builder.replaceBySource({ it in result.knownEntitySources }, result.updatedStorage)
  return builder.toSnapshot()
}

internal data class AndroidGradleProjectEntitySource(
  override val projectPath: String,
  override val phase: GradleSyncPhase,
  val buildRootUrl: VirtualFileUrl,
  val projectRootUrl: VirtualFileUrl,
) : GradleBridgeEntitySource

internal data class AndroidGradleSourceSetEntitySource(
  val projectEntitySource: AndroidGradleProjectEntitySource,
  val sourceSetName: String,
) : GradleBridgeEntitySource {
  override val projectPath: String by projectEntitySource::projectPath
  override val phase: GradleSyncPhase by projectEntitySource::phase
}

internal open class SyncContributorProjectContext(
  val context: ProjectResolverContext,
  val project: Project,
  val phase: GradleSyncPhase,
  val buildModel: GradleLightBuild,
  val projectModel: GradleLightProject
) {
  // For each project in the build, create an entity source representing the project, as the build entity source as the parent.
  val projectEntitySource = AndroidGradleProjectEntitySource(
    context.projectPath,
    phase,
    context.virtualFileUrl(buildModel.buildIdentifier.rootDir),
    context.virtualFileUrl(projectModel.projectDirectory)
  )

  val isGradleRootProject = context.projectPath == projectModel.projectDirectory.toPath().toCanonicalPath()

  val externalProject = context.getProjectModel(projectModel, ExternalProject::class.java)!!

  fun File.toVirtualFileUrl() = context.virtualFileUrl(this)
}


internal class SyncContributorAndroidProjectContext(
  context: ProjectResolverContext,
  project: Project,
  storage: EntityStorage,
  phase: GradleSyncPhase,
  buildModel: GradleLightBuild,
  projectModel: GradleLightProject,
  val syncOptions: SyncActionOptions,
  val versions: ModelVersions,
) : SyncContributorProjectContext (
    context,
    project,
    phase,
    buildModel,
    projectModel,
) {
  val basicAndroidProject = context.getProjectModel(projectModel, BasicAndroidProject::class.java)!!
  val androidProject = context.getProjectModel(projectModel, AndroidProject::class.java)!!
  val androidDsl = context.getProjectModel(projectModel, AndroidDsl::class.java)!!
  val gradlePluginModel = context.getProjectModel(projectModel, GradlePluginModel::class.java)!!
  val gradleProject = context.getProjectModel(projectModel, GradleProject::class.java)!!
  val ideaModule =  context.getProjectModel(projectModel, IdeaModule::class.java)!!

  // Need to use Impl version because GradleAndroidModelData expects an immutable implementation.
  val ideAndroidProject = context.getProjectModel(projectModel, IdeAndroidProject::class.java)!! as IdeAndroidProjectImpl
  val ideDeclaredDependencies = context.getProjectModel(projectModel, DeclaredDependencies::class.java)!!.toIdeDeclaredDependencies()

  val testArtifactsAndSourceSetsInMaps: Boolean = versions[ModelFeature.TEST_ARTIFACTS_AND_SOURCE_SETS_IN_MAPS]
  val sdk: SdkDependency?  =
    AndroidSdks.getInstance().findSuitableAndroidSdk(androidDsl.compileTarget)?.let {
      SdkDependency(SdkId(it.name, AndroidSdkType.SDK_NAME))
    }
  val variantName: String = computeVariantNameToBeSynced(syncOptions, projectModel.moduleId(), basicAndroidProject, androidDsl)!!

  private val holderModuleEntityNullable: ModuleEntity? = storage.resolve(ModuleId(resolveHolderModuleName()))
  // This is structured this way to make sure consumers don't have to worry about nullability.
  val holderModuleEntity: ModuleEntity by lazy { checkNotNull(holderModuleEntityNullable) { "Holder module can't be null!" } }

  val isValidContext = (holderModuleEntityNullable != null).logDebugIfFalse {
    "Holder module entity is null for ${projectModel.path}"
   }
   // TODO(b/384022658): Spaces in module names are causing issues, fix them to  be consistent with data services too.
   && (!resolveHolderModuleName().contains("\\s".toRegex())).logDebugIfFalse {
     "Module name has spaces in it ${resolveHolderModuleName()}"
   }
   // TODO(b/384022658): We don't behave well in the unlikely event when there is a rename that ends up with a holder
   // module with the same name as one of the existing source set modules (i.e. from app to app.main). This needs to be
   // handled separately in the platform
   && (holderModuleEntity.entitySource !is AndroidGradleSourceSetEntitySource).logDebugIfFalse {
     "Holder module is not populated via Android Gradle source sets for ${projectModel.path}"
   }

  internal val gradleAndroidModelDataFactory: (String) -> GradleAndroidModelData
    get() = { moduleName ->
      val ideAndroidProject = ideAndroidProject.copy(baseFeature = baseFeature)
      GradleAndroidModelData.create(
        moduleName = moduleName,
        rootDirPath = File(externalProject.projectDir.path),
        ideAndroidProject,
        ideDeclaredDependencies,
        ideAndroidProject.coreVariants.map { it as IdeVariantCoreImpl },
        variantName
      )
    }
  internal val gradleModuleModelFactory: (String) -> GradleModuleModel
    get() = { moduleName ->
      GradleModuleModel(
        moduleName,
        gradleProject,
        gradleProject.buildScript.sourceFile,
        context.projectGradleVersion,
        versions.agpVersionAsString,
        gradlePluginModel.hasSafeArgsJava(),
        gradlePluginModel.hasSafeArgsKotlin()
      )
    }


  // Mutable state
  val contentRootIndex = GradleContentRootIndex()
  val moduleActions = mutableMapOf<String, MutableList<ModuleAction>>()
  var baseFeature: String? = null


  fun registerModuleAction(moduleName: String, action: ModuleAction) {
    moduleActions.computeIfAbsent (moduleName) { mutableListOf() } += action
  }

  fun registerModuleActions(actions: Map<String, ModuleAction>) {
    actions.forEach { moduleName, action ->
      registerModuleAction(moduleName, action)
    }
  }


  companion object {
    internal fun create(context: ProjectResolverContext,
                        project: Project,
                        storage: EntityStorage,
                        phase: GradleSyncPhase,
                        syncOptions: SyncActionOptions,
                        buildModel: GradleLightBuild,
                        projectModel: GradleLightProject
    ): SyncContributorAndroidProjectContext? {
      return SyncContributorAndroidProjectContext(
        context,
        project,
        storage,
        phase,
        buildModel,
        projectModel,
        syncOptions,
        context.getProjectModel(projectModel, Versions::class.java)?.convert() ?: return null.also {
          LOG.debug("No Versions model found for ${projectModel.path}. Not an Android project!")
        }
        ).takeIf { it.isValidContext }
      }
  }
}

private val SOURCE_SET_UPDATE_RESULT_KEY: Key<SourceSetUpdateResult> = Key.create("SOURCE_SET_UPDATE_RESULT")

internal class AndroidDependencySyncContributor: GradleSyncContributor {

  override val phase: GradleSyncPhase = GradleSyncPhase.DEPENDENCY_MODEL_PHASE

  override suspend fun createProjectModel(
    context: ProjectResolverContext,
    storage: ImmutableEntityStorage,
  ): ImmutableEntityStorage {
    LOG.info("Processing DEPENDENCY_MODEL_PHASE for Android.")
    if (StudioFlags.PHASED_SYNC_DEPENDENCY_RESOLUTION_ENABLED.get()) {
      val previousResult = checkNotNull(context.getUserData(SOURCE_SET_UPDATE_RESULT_KEY)) {
        "No result from source set phase!"
      }
      val result = setupAndroidDependenciesForAllProjects(context, phase, previousResult.allAndroidProjectContexts, storage)
      return updateStorage(storage, result)
    }
    return storage
  }
}

internal class AndroidSourceRootSyncExtension : GradleSyncExtension {

  override suspend fun updateBridgeModel(
    context: ProjectResolverContext,
    phase: GradleSyncPhase,
  ) {
    LOG.info("Processing JPS bridges in phase $phase for Android.")
    if (phase == GradleSyncPhase.SOURCE_SET_MODEL_PHASE) {
      val previousResult = checkNotNull(context.getUserData(SOURCE_SET_UPDATE_RESULT_KEY)) {
        "No result from source set phase!"
      }
      performModuleActionsFromPreviousPhase(context.project, previousResult.allModuleActions)
    }
  }

  /**
   * Actual module instances will only be available in the phase after we commit changes to the storage.
   *
   * This method performs any module operations registered earlier after the instances are created.
   */
  private fun performModuleActionsFromPreviousPhase(project: Project, modulesActionsFromPreviousPhaseMap: Map<String, List<ModuleAction>>) {
    val modulesByName = project.modules.associateBy { it.name }
    modulesActionsFromPreviousPhaseMap.forEach { (moduleName, actions) ->
      val module = checkNotNull(modulesByName[moduleName]) { "No module found for module with registered actions!" }
      actions.forEach { it(module) }
    }
  }
}

internal class AndroidSourceRootSyncContributor : GradleSyncContributor {

  override val phase: GradleSyncPhase = GradleSyncPhase.SOURCE_SET_MODEL_PHASE

  override suspend fun createProjectModel(
    context: ProjectResolverContext,
    storage: ImmutableEntityStorage,
  ): ImmutableEntityStorage {
    LOG.info("Processing SOURCE_SET_MODEL_PHASE for Android.")
    val result = configureModulesForSourceSets(context, storage)
    context.putUserData(SOURCE_SET_UPDATE_RESULT_KEY, result)
    return updateStorage(storage, result)
  }

  /**
   * Duplicates the existing entity storage and mutates it by
   * - adding new module entities to it for source sets (or mutating the already existing ones where relevant)
   * - modifiying the holder module entities
   *
   * Returns a [SourceSetUpdateResult] instance holding info about the mutated state, to be used with [MutableEntityStorage.replaceBySource]
   */
  private suspend fun configureModulesForSourceSets(
    context: ProjectResolverContext,
    storage: ImmutableEntityStorage
  ): SourceSetUpdateResult {
    LOG.debug("Configuring modules for source sets")
    val project = context.project
    val syncOptions = context.getSyncOptions(project)

    val updatedEntities = MutableEntityStorage.from(storage)
    val allAndroidContexts = context.allBuilds.flatMap { buildModel ->
      buildModel.projects.mapNotNull { projectModel ->
        checkCanceled()
        SyncContributorAndroidProjectContext.create(context, project, storage, phase, syncOptions, buildModel, projectModel)
      }
    }

    if (allAndroidContexts.isEmpty()) {
      LOG.debug("Nothing to set up!")
    }

    val featureToAppMapping = allAndroidContexts.flatMap {
      it.ideAndroidProject.dynamicFeatures.mapNotNull { feature ->
        (feature to it.holderModuleEntity.exModuleOptions?.linkedProjectId)
          .takeIf { it.second != null }
      }
    }.toMap()
    allAndroidContexts.forEach {
      it.baseFeature = featureToAppMapping[it.ideAndroidProject.projectPath.projectPath]
    }

    val newModuleEntities = allAndroidContexts.flatMap {
      with(it) {
        LOG.debug("Setting up project ${projectModel.path}")
        val sourceSetModuleEntitiesByArtifact = getAllSourceSetModuleEntities(updatedEntities)
        if (sourceSetModuleEntitiesByArtifact.isEmpty()) (return@flatMap emptyList()).also {
          LOG.debug("No source sets found for ${projectModel.path}")
        }
        val sourceSetModules = sourceSetModuleEntitiesByArtifact.values

        updatedEntities.modifyModuleEntity(holderModuleEntity) {
          setJavaSettingsForHolderModule(this)
          setSdkForHolderModule(this)
          createOrUpdateAndroidGradleFacet(updatedEntities, this)
          createOrUpdateAndroidFacet(updatedEntities, this)
          linkModuleGroup(this, sourceSetModuleEntitiesByArtifact)
          setExcludeDirectoriesForHolderModule(updatedEntities)
          // There seems to be a bug in workspace model implementation that requires doing this to update list of changed props
          this.facets = facets
        }
        sourceSetModules
      }
    }
    val knownSourceSetEntitySources = newModuleEntities.map { it.entitySource }.toSet()
    // Remove orphaned modules. It is important here to first remove then add below to make sure replacement operations work correctly.
    val removedModules = removeOrphanedModules(allAndroidContexts, knownSourceSetEntitySources, updatedEntities)
    val removedModuleNames = removedModules.map { it.name }.toSet()


    newModuleEntities.forEach { newModuleEntity ->
      // Create or update the entity after doing all the mutations
      val existingEntity = updatedEntities.resolve(ModuleId(newModuleEntity.name))
      if (existingEntity == null) {
        updatedEntities addEntity newModuleEntity
      }
      else {
        updatedEntities.modifyModuleEntity(existingEntity) {
          this.entitySource = newModuleEntity.entitySource
          this.contentRoots = newModuleEntity.contentRoots
          this.exModuleOptions = newModuleEntity.exModuleOptions
          this.javaSettings = newModuleEntity.javaSettings
          // Not modifying existing dependencies here because we don't have that info here yet.
        }
      }
    }

    return SourceSetUpdateResult(
      allModuleActions = allAndroidContexts.flatMap { it.moduleActions.entries }.associate { it.key to it.value }.filterKeys {
        it !in removedModuleNames
      },
      allAndroidContexts,
      updatedEntities,
      knownSourceSetEntitySources +
      removedModules.map { it.entitySource } +
      allAndroidContexts.map { it.holderModuleEntity.entitySource }.toSet()
    )
  }
}

private fun SyncContributorAndroidProjectContext.setSdkForHolderModule(holderModuleEntity: ModuleEntityBuilder) {
  // Remove the existing SDK and replace it with the Android SDK (if it exists, otherwise just inherit the SDK)
  holderModuleEntity.dependencies.removeAll { it is InheritedSdkDependency || it is SdkDependency }
  holderModuleEntity.dependencies += sdk ?: InheritedSdkDependency
}

private fun SyncContributorAndroidProjectContext.setExcludeDirectoriesForHolderModule(storage: MutableEntityStorage) {
  // Not using content root index in this case because it specifically avoids settings the project root as a root, but we want that
  val typeToDirsMap = mapOf(
    ExternalSystemSourceType.EXCLUDED to ideaModule.contentRoots.flatMap { it.excludeDirectories }.toSet()
  )
  val contentRootUrl = typeToDirsMap.values.flatten().reduce { acc, file -> findCommonAncestor(acc, file) }

  storage.modifyModuleEntity(holderModuleEntity) {
    contentRoots = listOf(createContentRootEntity(name, entitySource, contentRootUrl, typeToDirsMap))
  }
}

// helpers
private fun SyncContributorAndroidProjectContext.getAllSourceSetModuleEntities(
  storage: EntityStorage,
): Map<IdeArtifactName, ModuleEntityBuilder> {
  val allSourceSets = getAllSourceSetsFromModels()

  val moduleEntitiesMap = mutableMapOf<String, ModuleEntityBuilder>()
  val mainSourceSetName = IdeArtifactName.MAIN.toWellKnownSourceSet().sourceSetName
  LOG.debug("Configuring module " + holderModuleEntity.name)


  return allSourceSets.associate  { (sourceSetArtifactName, typeToDirsMap) ->
    // For each source set in the project, create entity source and the actual entities.
    val sourceSetName = sourceSetArtifactName.toWellKnownSourceSet().sourceSetName
    val entitySource = AndroidGradleSourceSetEntitySource(projectEntitySource, sourceSetName)
    val moduleName = resolveSourceSetModuleName(storage, sourceSetName)
    val productionModuleName = resolveSourceSetModuleName(storage, mainSourceSetName)
      .takeIf { it != moduleName } // Only set for test modules
    LOG.debug("Configuring source set for $moduleName: $typeToDirsMap")
    val newModuleEntity = findOrCreateModuleEntity(
      moduleName,
      entitySource,
      moduleEntitiesMap,
      productionModuleName
    )

    // Create the content roots and associate it with the module
    newModuleEntity.contentRoots += createContentRootEntities(moduleName, entitySource, typeToDirsMap)
    newModuleEntity.javaSettings = createJavaModuleSettingsEntity(entitySource, sourceSetArtifactName)
    sourceSetArtifactName to newModuleEntity
  }
}

private fun SyncContributorAndroidProjectContext.linkModuleGroup(
  holderModuleEntity: ModuleEntityBuilder,
  sourceSetModules: Map<IdeArtifactName, ModuleEntityBuilder>) {
  val androidModuleGroup = getModuleGroup(sourceSetModules)
  val linkedModules = sourceSetModules.values + holderModuleEntity
  registerModuleActions(linkedModules.associate {
    it.name to { moduleInstance ->
      moduleInstance.putUserData(LINKED_ANDROID_GRADLE_MODULE_GROUP, androidModuleGroup)
    }
  })

  linkedModules.forEach { entity ->
    val gradleAndroidModelData = gradleAndroidModelDataFactory(entity.name)
    entity.gradleAndroidModel = GradleAndroidModelEntity(
      entitySource = entity.entitySource,
      gradleAndroidModel = GradleAndroidModel.create(project, gradleAndroidModelData)
    )
    entity.gradleModuleModel = GradleModuleModelEntity(
      entitySource = entity.entitySource,
      gradleModuleModel = gradleModuleModelFactory(entity.name)
    )
  }
}


private fun SyncContributorAndroidProjectContext.getModuleGroup(
  sourceSetModules: Map<IdeArtifactName, ModuleEntityBuilder>
): LinkedAndroidGradleModuleGroup {
  val modulePointerManager = ModulePointerManager.getInstance(project)
  return LinkedAndroidGradleModuleGroup(
    modulePointerManager.create(holderModuleEntity.name),
    modulePointerManager.create(checkNotNull(sourceSetModules[IdeArtifactName.MAIN]) { "Can't find main module!" }.name),
    sourceSetModules[IdeArtifactName.UNIT_TEST]?.let { modulePointerManager.create(it.name) },
    sourceSetModules[IdeArtifactName.ANDROID_TEST]?.let { modulePointerManager.create(it.name) },
    sourceSetModules[IdeArtifactName.TEST_FIXTURES]?.let { modulePointerManager.create(it.name) },
    sourceSetModules[IdeArtifactName.SCREENSHOT_TEST]?.let { modulePointerManager.create(it.name) }
  )
}

/** Removes the source sets modules that don't exist anymore and returns the removed module entities. */
private fun removeOrphanedModules(
  allAndroidContexts: List<SyncContributorAndroidProjectContext>,
  knownSourceSetEntitySources: Set<EntitySource>,
  updatedEntities: MutableEntityStorage,
): List<ModuleEntity> {
  val existingEntitiesByProjectSource = updatedEntities.entities(ModuleEntity::class.java).groupBy { entity ->
    when(entity.entitySource) {
      is AndroidGradleSourceSetEntitySource -> (entity.entitySource as AndroidGradleSourceSetEntitySource).projectEntitySource
      else -> null
    }
  }

  return allAndroidContexts.flatMap {
    // For each project, find the entities that are not known to it anymore and remove them.
    with(it) {
      existingEntitiesByProjectSource[projectEntitySource]?.filter {
        it.entitySource !in knownSourceSetEntitySources
      }.orEmpty().onEach {
        updatedEntities.removeEntity(it)
      }
    }
  }
}

/** Set up the javaSettings for the holder module. This does not set any compiler output paths as the holder modules don't have any. */
private fun SyncContributorAndroidProjectContext.setJavaSettingsForHolderModule(
  holderModuleEntity: ModuleEntityBuilder
) {
  holderModuleEntity.javaSettings = JavaModuleSettingsEntity(
    inheritedCompilerOutput = false,
    excludeOutput = context.isDelegatedBuild,
    entitySource = holderModuleEntity.entitySource)
}


// entity creation
internal fun SyncContributorProjectContext.createModuleEntity(
  name: String,
  entitySource: EntitySource
) = ModuleEntity(
  entitySource = entitySource,
  name = name,
  dependencies = listOf(
    ModuleSourceDependency
  ) + if (this is SyncContributorAndroidProjectContext && sdk != null) {
    sdk
  } else {
    InheritedSdkDependency
  }
) {
  // Annotate the module with external system info (with gradle path, external system type, etc.)
  exModuleOptions = createModuleOptionsEntity(entitySource)
}

private fun SyncContributorProjectContext.createModuleOptionsEntity(source: EntitySource) = ExternalSystemModuleOptionsEntity(
  entitySource = source
) {
  externalSystem = GradleConstants.SYSTEM_ID.id
  linkedProjectPath = externalProject.projectDir.path
  rootProjectPath = context.projectPath

  val holderModuleId = GradleProjectResolverUtil.getModuleId(context, externalProject)
  if (source is AndroidGradleSourceSetEntitySource) {
    externalSystemModuleType = GradleConstants.GRADLE_SOURCE_SET_MODULE_TYPE_KEY
    linkedProjectId = "$holderModuleId:${source.sourceSetName}"
  } else {
    linkedProjectId = holderModuleId
  }
}

private fun SyncContributorAndroidProjectContext.findOrCreateModuleEntity(
  name: String,
  entitySource: AndroidGradleSourceSetEntitySource,
  moduleEntitiesMap: MutableMap<String, ModuleEntityBuilder>,
  productionModuleName: String?
): ModuleEntityBuilder = moduleEntitiesMap.computeIfAbsent(name) {
  createModuleEntity(name, entitySource).also { moduleEntity ->
    // Use empty storage to look up facet because the facet doesn't exist when creating a module
    createOrUpdateAndroidFacet(MutableEntityStorage.create(), moduleEntity)
    if (productionModuleName != null) {
      moduleEntity.testProperties = TestModulePropertiesEntity(
        ModuleId(productionModuleName),
        entitySource
      )
    }

    registerModuleAction(moduleEntity.name) { moduleInstance ->
      moduleInstance.putUserData(LINKED_ANDROID_GRADLE_MODULE_GROUP, null)
      SourceFolderManager.getInstance(project).removeSourceFolders(moduleInstance)
    }
  }
}

private fun SyncContributorAndroidProjectContext.createContentRootEntities(
  moduleName: String,
  entitySource: EntitySource,
  typeToDirsMap: Map<out ExternalSystemSourceType?, Set<File>>
): List<ContentRootEntityBuilder> {
  val contentRootEntities = CanonicalPathPrefixTree.createMap<Path>()

  return resolveContentRoots(typeToDirsMap).onEach {
    contentRootEntities[it.toFile().toVirtualFileUrl().url] = it
  }.map { contentRootUrl ->
    createContentRootEntity(moduleName, entitySource, contentRootUrl.toFile(), typeToDirsMap.mapValues { (_, files) ->
      files.filter {
        contentRootUrl == contentRootEntities.getAncestorValues(it.toVirtualFileUrl().url).last()
      }.toSet()
    })
  }
}

private fun SyncContributorAndroidProjectContext.createContentRootEntity(
  moduleName: String,
  entitySource: EntitySource,
  contentRootUrl: File,
  typeToDirsMap: Map<out ExternalSystemSourceType?, Set<File>>
): ContentRootEntityBuilder {
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
          val (urlsWithExistingFiles, urlsWithMissingFiles) = urls.partition { it.exists() }
          // nulls filtered already, so using !! is fine
          val jpsType = type!!.toJpsModuleSourceRootType()
          if (jpsType != null) {
            registerModuleAction(moduleName) { moduleInstance ->
              val sourceFolderManager = SourceFolderManager.getInstance(project)
              urlsWithMissingFiles.forEach { url ->
                val normalizedUrl = pathToUrl(url.path)
                sourceFolderManager.addSourceFolder(moduleInstance, normalizedUrl, jpsType)
                if (type.isGenerated) {
                  sourceFolderManager.setSourceFolderGenerated(normalizedUrl, true)
                }
              }
            }
          }

          urlsWithExistingFiles.map {
            createSourceRootEntity(it, type, entitySource)
          }
        }
    }
}

private fun SyncContributorAndroidProjectContext.createJavaModuleSettingsEntity(
  entitySource: AndroidGradleSourceSetEntitySource,
  sourceSetArtifactName: IdeArtifactName
): JavaModuleSettingsEntityBuilder {
  return JavaModuleSettingsEntity(
     inheritedCompilerOutput = false, excludeOutput = context.isDelegatedBuild, entitySource = entitySource) {
    val artifact = getSelectedVariantArtifact(sourceSetArtifactName)

    val sourceCompilerOutput = if (sourceSetArtifactName == IdeArtifactName.MAIN) artifact?.classesFolders?.firstOrNull()?.toVirtualFileUrl() else null
    val testCompilerOutput = if (sourceSetArtifactName != IdeArtifactName.MAIN) artifact?.classesFolders?.firstOrNull()?.toVirtualFileUrl() else null
    this.compilerOutput = sourceCompilerOutput
    this.compilerOutputForTests = testCompilerOutput
  }
}

private fun SyncContributorAndroidProjectContext.createSourceRootEntity(
  file: File,
  type: IExternalSystemSourceType,
  entitySource: EntitySource
): SourceRootEntityBuilder = SourceRootEntity(
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

// copied from ContentRootDataService
private fun IExternalSystemSourceType.toJpsModuleSourceRootType():  JpsModuleSourceRootType<*>? {
  return when (ExternalSystemSourceType.from(this)) {
    ExternalSystemSourceType.SOURCE, ExternalSystemSourceType.SOURCE_GENERATED -> JavaSourceRootType.SOURCE
    ExternalSystemSourceType.TEST, ExternalSystemSourceType.TEST_GENERATED -> JavaSourceRootType.TEST_SOURCE
    ExternalSystemSourceType.RESOURCE, ExternalSystemSourceType.RESOURCE_GENERATED -> JavaResourceRootType.RESOURCE
    ExternalSystemSourceType.TEST_RESOURCE, ExternalSystemSourceType.TEST_RESOURCE_GENERATED -> JavaResourceRootType.TEST_RESOURCE
    ExternalSystemSourceType.EXCLUDED -> null
  }
}

internal fun SyncContributorProjectContext.resolveHolderModuleName(): String {
  return GradleProjectResolverUtil.getHolderModuleName(context, projectModel, externalProject)
}

internal fun SyncContributorProjectContext.resolveSourceSetModuleName(storage: EntityStorage, sourceSetName: String): String {
  return GradleProjectResolverUtil.resolveSourceSetModuleName(context, storage, projectModel, externalProject, sourceSetName)
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

private fun Boolean.logDebugIfFalse(msg: () -> String) = this.also {
  if (!this) {
    LOG.debug(msg())
  }
}