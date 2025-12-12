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
import com.android.tools.idea.gradle.model.impl.IdeAndroidProjectImpl
import com.android.tools.idea.gradle.model.impl.IdeTestSuiteImpl
import com.android.tools.idea.gradle.model.impl.IdeVariantCoreImpl
import com.android.tools.idea.gradle.project.entities.GradleAndroidModelEntity
import com.android.tools.idea.gradle.project.entities.GradleModuleModelEntity
import com.android.tools.idea.gradle.project.entities.gradleAndroidModel
import com.android.tools.idea.gradle.project.entities.gradleModuleModel
import com.android.tools.idea.gradle.project.entities.updateGradleAndroidModelMapping
import com.android.tools.idea.gradle.project.model.GradleAndroidModelData
import com.android.tools.idea.gradle.project.model.GradleAndroidModelImpl
import com.android.tools.idea.gradle.project.model.GradleModuleModel
import com.android.tools.idea.gradle.project.sync.ModelFeature
import com.android.tools.idea.gradle.project.sync.ModelVersions
import com.android.tools.idea.gradle.project.sync.SyncActionOptions
import com.android.tools.idea.gradle.project.sync.computeVariantNameToBeSynced
import com.android.tools.idea.gradle.project.sync.convert
import com.android.tools.idea.gradle.project.sync.getAllChildren
import com.android.tools.idea.gradle.project.sync.idea.AndroidGradleProjectResolver.Companion.toIdeDeclaredDependencies
import com.android.tools.idea.gradle.project.sync.patchForKapt
import com.android.tools.idea.projectsystem.gradle.LINKED_ANDROID_GRADLE_MODULE_GROUP
import com.android.tools.idea.projectsystem.gradle.LinkedAndroidGradleModuleGroup
import com.android.tools.idea.sdk.AndroidSdks
import com.google.common.collect.HashBasedTable
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
import com.intellij.openapi.util.getAndUpdateUserData
import com.intellij.openapi.util.io.CanonicalPathPrefixTree
import com.intellij.openapi.util.io.toCanonicalPath
import com.intellij.openapi.vfs.VfsUtilCore.pathToUrl
import com.intellij.platform.workspace.jps.entities.ContentRootEntity
import com.intellij.platform.workspace.jps.entities.ContentRootEntityBuilder
import com.intellij.platform.workspace.jps.entities.ExcludeUrlEntity
import com.intellij.platform.workspace.jps.entities.ExternalSystemModuleOptionsEntity
import com.intellij.platform.workspace.jps.entities.InheritedSdkDependency
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleEntityBuilder
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.jps.entities.ModuleSourceDependency
import com.intellij.platform.workspace.jps.entities.SdkDependency
import com.intellij.platform.workspace.jps.entities.SdkId
import com.intellij.platform.workspace.jps.entities.SourceRootEntity
import com.intellij.platform.workspace.jps.entities.SourceRootEntityBuilder
import com.intellij.platform.workspace.jps.entities.SourceRootTypeId
import com.intellij.platform.workspace.jps.entities.TestModulePropertiesEntity
import com.intellij.platform.workspace.jps.entities.exModuleOptions
import com.intellij.platform.workspace.jps.entities.modifyModuleEntity
import com.intellij.platform.workspace.jps.entities.testProperties
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.ImmutableEntityStorage
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.workspaceModel.ide.legacyBridge.impl.java.JAVA_RESOURCE_ROOT_ENTITY_TYPE_ID
import com.intellij.workspaceModel.ide.legacyBridge.impl.java.JAVA_SOURCE_ROOT_ENTITY_TYPE_ID
import com.intellij.workspaceModel.ide.legacyBridge.impl.java.JAVA_TEST_RESOURCE_ROOT_ENTITY_TYPE_ID
import com.intellij.workspaceModel.ide.legacyBridge.impl.java.JAVA_TEST_ROOT_ENTITY_TYPE_ID
import java.io.File
import java.nio.file.Path
import org.gradle.tooling.model.GradleProject
import org.gradle.tooling.model.idea.IdeaModule
import org.gradle.tooling.model.idea.IdeaProject
import org.jetbrains.android.sdk.AndroidSdkType
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.module.JpsModuleSourceRootType
import org.jetbrains.kotlin.idea.gradleTooling.model.kapt.KaptGradleModel
import org.jetbrains.plugins.gradle.model.ExternalProject
import org.jetbrains.plugins.gradle.model.GradleLightBuild
import org.jetbrains.plugins.gradle.model.GradleLightProject
import org.jetbrains.plugins.gradle.model.GradleTaskModel
import org.jetbrains.plugins.gradle.service.project.GradleContentRootIndex
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolverUtil
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncContributor
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncExtension
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase
import org.jetbrains.plugins.gradle.service.syncAction.impl.bridge.GradleBridgeEntitySource
import org.jetbrains.plugins.gradle.service.syncAction.virtualFileUrl
import org.jetbrains.plugins.gradle.util.GradleConstants

private val LOG = logger<GradleSyncContributor>()

// Need the source type to be nullable because of how AndroidManifest is handled.
internal typealias SourceSetData = Pair<IdeArtifactName, Map<out ExternalSystemSourceType?, Set<File>>>
internal typealias ModuleAction = (Module) -> Unit

/** This class is used to keep track of */
internal data class SourceSetUpdateResult(
  /** Represents list of module actions by name. Mutable because actions are removed as they are performed. */
  val allModuleActions: Map<String, List<ModuleAction>> = emptyMap(),
  val allAndroidProjectContexts: List<SyncContributorAndroidProjectContext> = emptyList(),
)

@VisibleForTesting
data class AndroidGradleProjectEntitySource(
  override val projectPath: String,
  override val phase: GradleSyncPhase
) : GradleBridgeEntitySource

data class AndroidGradleSourceSetEntitySource(
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
  val gradleTaskModel = context.getProjectModel(projectModel, GradleTaskModel::class.java)!!
  val kaptGradleModel = context.getProjectModel(projectModel, KaptGradleModel::class.java)

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
        ideAndroidProject.patchForKapt(kaptGradleModel),
        ideDeclaredDependencies,
        ideAndroidProject.coreVariants.map { it as IdeVariantCoreImpl }.patchForKapt(kaptGradleModel),
        variantName
      )
    }
  internal val gradleModuleModelFactory: (String) -> GradleModuleModel
    get() = { moduleName ->
      GradleModuleModel(
        moduleName,
        gradleProject,
        gradleTaskModel, // external project doesn't have tasks at this phase
        gradleProject.buildScript.sourceFile,
        context.projectGradleVersion,
        versions.agpVersionAsString,
        gradlePluginModel,
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

private val MODULE_ACTION_KEY: Key<Map<String, List<ModuleAction>>> = Key.create("AndroidSourceRootSyncContributor.moduleActionKey")

internal class AndroidSourceRootSyncExtension : GradleSyncExtension {

  override suspend fun updateBridgeModel(
    context: ProjectResolverContext,
    phase: GradleSyncPhase,
  ) {
    performModuleActions(context)
  }

  /**
   * Actual module instances will only be available in the phase after we commit changes to the storage.
   *
   * This method performs any module operations registered earlier after the instances are created.
   */
  private fun performModuleActions(context: ProjectResolverContext) {
    val moduleActions = context.getAndUpdateUserData(MODULE_ACTION_KEY, { null }) ?: return
    val modulesByName = context.project.modules.associateBy { it.name }
    moduleActions.forEach { (moduleName, actions) ->
      val module = checkNotNull(modulesByName[moduleName]) { "No module found for module with registered actions!" }
      actions.forEach { it(module) }
    }
  }
}

internal class AndroidSourceRootSyncAdditionalPhaseContributor : GradleSyncContributor {

  override val phase: GradleSyncPhase = GradleSyncPhase.ADDITIONAL_MODEL_PHASE

  override suspend fun createProjectModel(
    context: ProjectResolverContext,
    storage: ImmutableEntityStorage,
  ): ImmutableEntityStorage {
    if (!context.isPhasedSyncEnabled) return storage

    LOG.info("Processing phase ADDITIONAL_MODEL_PHASE for Android.")
    val previousResult = checkNotNull(context.getUserData(SOURCE_SET_UPDATE_RESULT_KEY)) {
      "No result from source set phase!"
    }

    val ideaProjectPathToModulePerBuild = HashBasedTable.create<GradleLightBuild, String, IdeaModule>()
    context.allBuilds.forEach { buildModel ->
      val ideaProject = context.getBuildModel(buildModel, IdeaProject::class.java) ?: return@forEach
      ideaProject.getAllChildren().forEach { ideaModule ->
        ideaProjectPathToModulePerBuild.put(buildModel, ideaModule.gradleProject.path, ideaModule)
      }
    }

    val updatedEntities = MutableEntityStorage.from(storage)
    previousResult.allAndroidProjectContexts.forEach {
      with(it) {
        updatedEntities.modifyModuleEntity(holderModuleEntity) {
          val ideaModule = ideaProjectPathToModulePerBuild[buildModel, gradleProject.path] ?: return@modifyModuleEntity
          setExcludeDirectoriesForHolderModule(updatedEntities, ideaModule)
        }
      }
    }

    return updatedEntities.toSnapshot()
  }

  /** Creates exclude directories based on the information provided by the [IdeaModule] model. */
  private fun SyncContributorAndroidProjectContext.setExcludeDirectoriesForHolderModule(storage: MutableEntityStorage, ideaModule: IdeaModule) {
    // Not using content root index in this case because it specifically avoids settings the project root as a root, but we want that
    val typeToDirsMap = mapOf(
      ExternalSystemSourceType.EXCLUDED to ideaModule.contentRoots.flatMap { it.excludeDirectories }.toSet()
    )
    val contentRootUrl = typeToDirsMap.values.flatten().reduce { acc, file -> findCommonAncestor(acc, file) }

    val newContentRoots = listOf(createContentRootEntity(holderModuleEntity.name, projectEntitySource.copy(phase = phase), contentRootUrl, typeToDirsMap))
    // It could be expensive to call modifyModuleEntity even if nothing has changed, so avoiding it if possible
    if (holderModuleEntity.contentRoots != newContentRoots) {
      storage.modifyModuleEntity(holderModuleEntity) {
        contentRoots = newContentRoots
      }
    }
  }

}

internal class AndroidSourceRootSyncDependencyPhaseContributor : GradleSyncContributor {

  override val phase: GradleSyncPhase = GradleSyncPhase.DEPENDENCY_MODEL_PHASE

  override suspend fun createProjectModel(
    context: ProjectResolverContext,
    storage: ImmutableEntityStorage,
  ): ImmutableEntityStorage {
    if (!context.isPhasedSyncEnabled) return storage

    LOG.info("Processing phase DEPENDENCY_MODEL_PHASE for Android.")
    val previousResult = checkNotNull(context.getUserData(SOURCE_SET_UPDATE_RESULT_KEY)) {
      "No result from source set phase!"
    }
    if (StudioFlags.PHASED_SYNC_DEPENDENCY_RESOLUTION_ENABLED.get()) {
      return setupAndroidDependenciesForAllProjects(
        context,
        previousResult.allAndroidProjectContexts,
        storage,
        phase
      )
    }
    return storage
  }
}

internal class AndroidSourceRootSyncSourceSetPhaseContributor : GradleSyncContributor {

  override val phase: GradleSyncPhase = GradleSyncPhase.ADDITIONAL_MODEL_PHASE

  override suspend fun createProjectModel(
    context: ProjectResolverContext,
    storage: ImmutableEntityStorage,
  ): ImmutableEntityStorage {
    if (!context.isPhasedSyncEnabled) return storage

    LOG.info("Processing phase SOURCE_SET_MODEL_PHASE for Android.")
    val (result, updatedStorage) = configureModulesForSourceSets(context, storage)
    context.putUserDataIfAbsent(SOURCE_SET_UPDATE_RESULT_KEY, result)
    context.putUserData(MODULE_ACTION_KEY, result.allModuleActions)
    return updatedStorage
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
  ): Pair<SourceSetUpdateResult, ImmutableEntityStorage>  {
    LOG.debug("Configuring modules for source sets")
    val project = context.project
    val syncOptions = context.getSyncOptions(project)
    val updatedStorage = MutableEntityStorage.from(storage)
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

    allAndroidContexts.forEach {
      with(it) {
        LOG.debug("Setting up project ${projectModel.path}")
        val sourceSetModuleEntitiesByArtifact = getAllSourceSetModuleEntities(updatedStorage)

        val knownArtifactsModuleEntitiesByArtifact = sourceSetModuleEntitiesByArtifact.knownArtifacts
        val knownArtifactsModuleEntities = knownArtifactsModuleEntitiesByArtifact.values
        if (knownArtifactsModuleEntities.isEmpty()) {
          LOG.debug("No source sets found for ${projectModel.path}")
          return@forEach
        }

        val testSuiteSourceSetModules = sourceSetModuleEntitiesByArtifact.testSuites.values

        updatedStorage.modifyModuleEntity(holderModuleEntity) {
          setJavaSettingsForHolderModule(this)
          setSdkForHolderModule(this)
          createAndroidGradleFacet(this)
          createAndroidFacet(this)
          linkModuleGroup(this, knownArtifactsModuleEntitiesByArtifact, testSuiteSourceSetModules)
          // There seems to be a bug in workspace model implementation that requires doing this to update list of changed props
          this.facets = facets
        }
        (knownArtifactsModuleEntities + testSuiteSourceSetModules).forEach { newModuleEntity ->
          val finalEntity = updatedStorage addEntity newModuleEntity
          updateGradleAndroidModelMapping(updatedStorage, finalEntity)
        }
      }
    }

    return SourceSetUpdateResult(
      allModuleActions = allAndroidContexts.flatMap { it.moduleActions.entries }.associate { it.key to it.value },
      allAndroidContexts,
    ) to updatedStorage.toSnapshot()
  }
}

private fun SyncContributorAndroidProjectContext.setSdkForHolderModule(holderModuleEntity: ModuleEntityBuilder) {
  // Remove the existing SDK and replace it with the Android SDK (if it exists, otherwise just inherit the SDK)
  holderModuleEntity.dependencies.removeAll { it is InheritedSdkDependency || it is SdkDependency }
  holderModuleEntity.dependencies += sdk ?: InheritedSdkDependency
}

private class AllSourceSetModuleEntities(
  val knownArtifacts: Map<IdeArtifactName, ModuleEntityBuilder>,
  val testSuites: Map<String, ModuleEntityBuilder>
)

// helpers
private fun SyncContributorAndroidProjectContext.getAllSourceSetModuleEntities(
  storage: EntityStorage,
): AllSourceSetModuleEntities {
  val allSourceSets = getAllSourceSetsFromModels()

  // This is the module name corresponding to the "holder" module
  val projectModuleName = resolveHolderModuleName()
  val moduleEntitiesMap = mutableMapOf<String, ModuleEntityBuilder>()
  val mainSourceSetName = IdeArtifactName.MAIN.toWellKnownSourceSet().sourceSetName
  LOG.debug("Configuring module $projectModuleName")

  val knownArtifactsSources = allSourceSets.associate { (sourceSetArtifactName, typeToDirsMap) ->
    // For each source set in the project, create entity source and the actual entities.
    val sourceSetName = sourceSetArtifactName.toWellKnownSourceSet().sourceSetName
    val entitySource = AndroidGradleSourceSetEntitySource(projectEntitySource, sourceSetName)
    val moduleName = resolveSourceSetModuleName(storage, sourceSetName)
    LOG.debug("Configuring source set for $moduleName: $typeToDirsMap")
    val productionModuleName = resolveSourceSetModuleName(storage, mainSourceSetName)
      .takeIf { it != moduleName } // Only set for test modules
    val newModuleEntity = findOrCreateModuleEntity(moduleName, entitySource, moduleEntitiesMap, productionModuleName)

    // Create the content roots and associate it with the module
    newModuleEntity.contentRoots += createContentRootEntities(moduleName, entitySource, typeToDirsMap)
    newModuleEntity.javaSettings = createJavaModuleSettingsEntity(entitySource, sourceSetArtifactName)
    sourceSetArtifactName to newModuleEntity
  }

  val testSuitesEnabled = StudioFlags.AGP_TEST_SUITES_ENABLED.get() && versions[ModelFeature.HAS_TEST_SUITES]
  val testSuiteSources = if (testSuitesEnabled) {
    val testSuites = getTestSuitesTargetingVariant(basicAndroidProject, ideAndroidProject, variantName)
    testSuites.associateBy { it.name }.mapValues {
      configureTestSuiteSourceSetModuleEntity(
        it.value, moduleEntitiesMap, projectModuleName, mainSourceSetName
      )
    }
  }
  else {
    emptyMap()
  }

  return AllSourceSetModuleEntities(knownArtifactsSources, testSuiteSources)
}

private fun getTestSuitesTargetingVariant(
  basicAndroidProject: BasicAndroidProject,
  ideAndroidProject: IdeAndroidProjectImpl,
  variantName: String
): List<IdeTestSuiteImpl> {
  val variant = basicAndroidProject.variants.first { it.name == variantName }
  return variant.testSuiteArtifacts.keys.map { testSuiteName ->
    ideAndroidProject.testSuites.first { it.name == testSuiteName }
  }
}

private fun SyncContributorAndroidProjectContext.configureTestSuiteSourceSetModuleEntity(
  testSuite: IdeTestSuiteImpl,
  moduleEntitiesMap: MutableMap<String, ModuleEntityBuilder>,
  projectModuleName: String,
  mainSourceSetName: String
): ModuleEntityBuilder {
  val allSourcesForTestSuite: Map<out ExternalSystemSourceType?, Set<File>> = getTestSuiteSourceSetDataForBasicAndroidProject(
    testSuite.sources)

  // For each test suite in the project, create entity source and the actual entities.
  val sourceSetName = testSuite.name
  val entitySource = AndroidGradleSourceSetEntitySource(projectEntitySource, sourceSetName)
  val moduleName = "$projectModuleName.$sourceSetName"
  LOG.debug("Configuring source set for $moduleName: $allSourcesForTestSuite")
  val newModuleEntity = findOrCreateModuleEntity(
    moduleName,
    entitySource,
    moduleEntitiesMap,
    productionModuleName = "$projectModuleName.$mainSourceSetName".takeIf { it != moduleName } // Only set for test modules
  )

  // Create the content roots and associate it with the module
  newModuleEntity.contentRoots += createContentRootEntities(moduleName, entitySource, allSourcesForTestSuite)

  return newModuleEntity
}

private fun SyncContributorAndroidProjectContext.linkModuleGroup(
  holderModuleEntity: ModuleEntityBuilder,
  sourceSetModules: Map<IdeArtifactName, ModuleEntityBuilder>,
  testSuiteModules: Collection<ModuleEntityBuilder>
) {
  val androidModuleGroup = getModuleGroup(sourceSetModules, testSuiteModules)
  val linkedModules = sourceSetModules.values + testSuiteModules + holderModuleEntity
  registerModuleActions(linkedModules.associate {
    it.name to { moduleInstance ->
      moduleInstance.putUserData(LINKED_ANDROID_GRADLE_MODULE_GROUP, androidModuleGroup)
    }
  })
  linkedModules.forEach { entity ->
    val gradleAndroidModelData = gradleAndroidModelDataFactory(entity.name)
    entity.gradleAndroidModel = GradleAndroidModelEntity(
      entitySource = projectEntitySource,
      gradleAndroidModel = GradleAndroidModelImpl(gradleAndroidModelData)
    )
    entity.gradleModuleModel = GradleModuleModelEntity(
      entitySource = projectEntitySource,
      gradleModuleModel = gradleModuleModelFactory(entity.name)
    )
  }
}


private fun SyncContributorAndroidProjectContext.getModuleGroup(
  sourceSetModules: Map<IdeArtifactName, ModuleEntityBuilder>,
  testSuiteModules: Collection<ModuleEntityBuilder>
): LinkedAndroidGradleModuleGroup {
  val modulePointerManager = ModulePointerManager.getInstance(project)
  return LinkedAndroidGradleModuleGroup(
    modulePointerManager.create(holderModuleEntity.name),
    modulePointerManager.create(checkNotNull(sourceSetModules[IdeArtifactName.MAIN]) { "Can't find main module!" }.name),
    sourceSetModules[IdeArtifactName.UNIT_TEST]?.let { modulePointerManager.create(it.name) },
    sourceSetModules[IdeArtifactName.ANDROID_TEST]?.let { modulePointerManager.create(it.name) },
    sourceSetModules[IdeArtifactName.TEST_FIXTURES]?.let { modulePointerManager.create(it.name) },
    sourceSetModules[IdeArtifactName.SCREENSHOT_TEST]?.let { modulePointerManager.create(it.name) },
    testSuiteModules.map { modulePointerManager.create(it.name) }
  )
}

/** Set up the javaSettings for the holder module. This does not set any compiler output paths as the holder modules don't have any. */
private fun SyncContributorAndroidProjectContext.setJavaSettingsForHolderModule(
  holderModuleEntity: ModuleEntityBuilder
) {
  holderModuleEntity.javaSettings = JavaModuleSettingsEntity(
    inheritedCompilerOutput = false,
    excludeOutput = context.isDelegatedBuild,
    entitySource = projectEntitySource
  )
}


// entity creation
internal fun SyncContributorProjectContext.createModuleEntity(
  name: String,
  entitySource: AndroidGradleSourceSetEntitySource
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
    createAndroidFacet(moduleEntity)
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
  entitySource: AndroidGradleSourceSetEntitySource,
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

private fun IdeaProject.getAllChildren() = modules.flatMap { it.getAllChildren { it.children.filterIsInstance<IdeaModule>().toList() }}

private fun GradleLightBuild.getAllChildren() = rootProject.getAllChildren { it.childProjects.toList() }