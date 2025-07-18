/*
 * Copyright (C) 2015 The Android Open Source Project
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

import com.android.Version
import com.android.ide.common.repository.AgpVersion
import com.android.tools.idea.IdeInfo
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.model.impl.IdeLibraryModelResolverImpl
import com.android.tools.idea.gradle.plugin.AndroidPluginInfo
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet
import com.android.tools.idea.gradle.project.facet.ndk.NativeHeaderRootType
import com.android.tools.idea.gradle.project.facet.ndk.NativeSourceRootType
import com.android.tools.idea.gradle.project.facet.ndk.NdkFacet
import com.android.tools.idea.gradle.project.model.GradleAndroidModel
import com.android.tools.idea.gradle.project.model.GradleAndroidModelData
import com.android.tools.idea.gradle.project.sync.AutoSyncBehavior
import com.android.tools.idea.gradle.project.sync.AutoSyncSettingStore
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker
import com.android.tools.idea.gradle.project.sync.GradleSyncStateHolder
import com.android.tools.idea.gradle.project.sync.idea.AndroidGradleProjectResolver.Companion.shouldDisableForceUpgrades
import com.android.tools.idea.gradle.project.sync.idea.ModuleUtil.linkAndroidModuleGroup
import com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys.ANDROID_MODEL
import com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys.GRADLE_MODULE_MODEL
import com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys.IDE_LIBRARY_TABLE
import com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys.KMP_ANDROID_LIBRARY_TABLE
import com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys.NDK_MODEL
import com.android.tools.idea.gradle.project.sync.idea.findAndSetupSelectedCachedVariantData
import com.android.tools.idea.gradle.project.sync.idea.getSelectedVariantAndAbis
import com.android.tools.idea.gradle.project.upgrade.AgpVersionChecker
import com.android.tools.idea.gradle.project.upgrade.AssistantInvoker
import com.android.tools.idea.gradle.util.GradleProjectSystemUtil.GRADLE_SYSTEM_ID
import com.android.tools.idea.gradle.util.LocalProperties
import com.android.tools.idea.model.AndroidModel
import com.android.tools.idea.sdk.IdeSdks
import com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger
import com.intellij.execution.RunConfigurationProducerService
import com.intellij.execution.actions.RunConfigurationProducer
import com.intellij.execution.junit.JUnitConfigurationType
import com.intellij.facet.Facet
import com.intellij.facet.FacetManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.Key
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManager
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManagerImpl
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.ExternalStorageConfigurationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.rootManager
import com.intellij.openapi.roots.ExternalProjectSystemRegistry
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleSourceOrderEntry
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.PROJECT_LOADED_FROM_CACHE_BUT_HAS_NO_MODULES
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.workspaceModel.ide.JpsProjectLoadingManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import org.jetbrains.android.AndroidStartupManager
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.kotlin.idea.base.util.isAndroidModule
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.settings.GradleSettingsListener
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.io.File

/**
 * Syncs Android Gradle project with the persisted project data on startup.
 */
class AndroidGradleProjectStartupActivity : ProjectActivity {

  @Service(Service.Level.PROJECT)
  class StartupService(private val project: Project) : AndroidGradleProjectStartupService<Unit>() {

    suspend fun performStartupActivity(isJpsProjectLoaded: Boolean = false) {
      LOG.debug { "AndroidGradleProjectStartupActivity.performStartupActivity(isJpsProjectLoaded = $isJpsProjectLoaded)" }
      if (Registry.`is`("android.gradle.project.startup.activity.disabled")) return

      runInitialization {
        // Need to wait for both JpsProjectLoadingManager and ExternalProjectsManager, as well as the completion of
        // AndroidNewProjectInitializationStartupActivity.  In old-skool thread
        // programming I'd probably use an atomic integer and wait for the count to reach 3.
        coroutineScope {
          val myJob = currentCoroutineContext().job
          val externalProjectsJob = CompletableDeferred<Unit>(parent = myJob)
          val jpsProjectJob = CompletableDeferred<Unit>(parent = myJob)
          val newProjectStartupJob = async { project.service<AndroidNewProjectInitializationStartupActivity.StartupService>().awaitInitialization() }

          ExternalProjectsManager.getInstance(project).runWhenInitializedInBackground { externalProjectsJob.complete(Unit) }
          whenAllModulesLoaded(project, isJpsProjectLoaded) { jpsProjectJob.complete(Unit) }
          awaitAll(newProjectStartupJob, externalProjectsJob, jpsProjectJob)
        }

        performActivity(project)
      }
    }
  }

  override suspend fun execute(project: Project) {
    project.service<StartupService>().performStartupActivity()
  }
}

private val LOG = Logger.getInstance(AndroidGradleProjectStartupActivity::class.java)

private suspend fun performActivity(project: Project) {
  val gradleProjectInfo = GradleProjectInfo.getInstance(project)
  val info = Info.getInstance(project)

  fun shouldSyncOrAttachModels(): Boolean {
    if (gradleProjectInfo.isSkipStartupActivity) return false

    // Opening an IDEA project with Android modules (AS and IDEA - i.e. previously synced).
    if (info.androidModules.isNotEmpty()) return true

    // Opening a Gradle project with .idea but no .iml files or facets (Typical for AS but not in IDEA)
    return IdeInfo.getInstance().isAndroidStudio && info.isBuildWithGradle
  }

  if (shouldSyncOrAttachModels()) {
    ensureProjectStoredExternally(project)
    removePointlessModules(project)
    addJUnitProducersToIgnoredList(project)
    /**
       * Attempts to see if the models cached by IDEAs external system are valid, if they are then we attach them to the facet,
       * if they are not then we request a project sync in order to ensure that the IDE has access to all the models it needs to function.
       */
      try {
        attachCachedModelsOrTriggerSyncBody(project, gradleProjectInfo)
      }
      catch (e: RequestSyncThrowable) {
        if (AutoSyncSettingStore.autoSyncBehavior == AutoSyncBehavior.Default) {
          // TODO(b/155467517): Reconsider the way we launch sync when GradleSyncInvoker is deleted. We may want to handle each external project
          //  path individually.
          LOG.info("Requesting Gradle sync (${e.reason}).")
          GradleSyncInvoker.getInstance().requestProjectSync(project, GradleSyncInvoker.Request(e.trigger))
        }
        else {
          LOG.info("Requesting Gradle sync (${e.reason}) cancelled by Auto Sync disabled.")
          SyncDueMessage.maybeShow(project)
        }
      }
    subscribeToGradleSettingChanges(project)
  }

  gradleProjectInfo.isSkipStartupActivity = false
}

private fun subscribeToGradleSettingChanges(project: Project) {
  val disposable = project.getService(AndroidStartupManager.ProjectDisposableScope::class.java)
  val connection = project.messageBus.connect(disposable)
  connection.subscribe(GradleSettingsListener.TOPIC, object : GradleSettingsListener {
    override fun onGradleJvmChange(oldGradleJvm: String?, newGradleJvm: String?, linkedProjectPath: String) {
      GradleSyncStateHolder.getInstance(project).recordGradleJvmConfigurationChanged()
    }
  })
}

private fun whenAllModulesLoaded(project: Project, isJpsProjectLoaded: Boolean, callback: () -> Unit) {
  if (isJpsProjectLoaded || project.getUserData(PROJECT_LOADED_FROM_CACHE_BUT_HAS_NO_MODULES) == true) {
    // All modules are loaded at this point and JpsProjectLoadingManager.jpsProjectLoaded is not triggered, so invoke callback directly.
    callback()
  } else {
    // Initially, IJ loads the state of workspace model from the cache and in DelayedProjectSynchronizer synchronizes the state of
    // workspace model with project model files using JpsProjectModelSynchronizer. Since that activity runs async we need to detect
    // when the JPS was loaded, otherwise, any change will be overridden.
    JpsProjectLoadingManager.getInstance(project).jpsProjectLoaded { callback() }
  }
}

private fun ensureProjectStoredExternally(project: Project) {
  // Force switch users from the broken "Generate .iml files for modules imported from Gradle" setting. b/396390662
  val state = ExternalStorageConfigurationManager.getInstance(project).isEnabled
  LOG.info("ExternalStorageConfigurationManager.isEnabled=$state for $project")
  // Implementation of this setter invokes some 'swapStore' function for each module.
  // Even though at this moment it seems to be empty, it is safer to do under WriteLock.
  ExternalProjectsManagerImpl.getInstance(project).setStoreExternally(true)
}

private suspend fun removePointlessModules(project: Project) {
  val moduleManager = ModuleManager.getInstance(project)
  val emptyModulesToRemove = mutableListOf<Pair<Module, Module.() -> Unit>>()
  val nativeOnlySourceRootsModulesToRemove = mutableListOf<Pair<Module, Module.() -> Unit>>()

  moduleManager.modules.forEach { module ->
    if (module.isLoaded && ExternalSystemModulePropertyManager.getInstance(module).getExternalSystemId().isNullOrEmpty()) {
      if (module.isEmptyModule()) {
        emptyModulesToRemove.add(Pair(module) {
          LOG.warn("Disposing module '$name' which is empty, not registered with the external system and '$moduleFilePath' does not exist.")
        })
      } else if (module.hasOnlyNativeRoots()) {
        nativeOnlySourceRootsModulesToRemove.add(Pair(module) {
          LOG.warn("Disposing module '$name' which is not registered with the external system and contains only native roots.")
        })
      }
    }
  }

  removeModules(
    moduleManager,
    modules = emptyModulesToRemove + nativeOnlySourceRootsModulesToRemove
  )
}

private suspend fun removeModules(moduleManager: ModuleManager, modules: List<Pair<Module, Module.() -> Unit>>) {
  if (modules.isEmpty()) return
  edtWriteAction {
    with(moduleManager.getModifiableModel()) {
      modules.forEach { (module, onRemovingModule) ->
        onRemovingModule(module)
        disposeModule(module)
      }
      commit()
    }
  }
}

private class RequestSyncThrowable(val reason: String, val trigger: Trigger) : Throwable()

private fun attachCachedModelsOrTriggerSyncBody(project: Project, gradleProjectInfo: GradleProjectInfo) {
  val moduleManager = ModuleManager.getInstance(project)
  val projectDataManager = ProjectDataManager.getInstance()

  fun DataNode<ProjectData>.modules(): Collection<DataNode<ModuleData>> =
    ExternalSystemApiUtil.findAllRecursively(this, ProjectKeys.MODULE)

  fun requestSync(reason: String, trigger: Trigger? = null): Nothing {
    throw RequestSyncThrowable(reason,
                               trigger
                               ?: if (gradleProjectInfo.isNewProject) Trigger.TRIGGER_PROJECT_NEW else Trigger.TRIGGER_PROJECT_REOPEN)
  }

  val existingGradleModules = moduleManager.modules.filter { ExternalSystemApiUtil.isExternalSystemAwareModule(GRADLE_SYSTEM_ID, it) }

  val modulesById =
    existingGradleModules
      .mapNotNull { module ->
        val externalId = ExternalSystemApiUtil.getExternalProjectId(module)
                         ?: requestSync("Unable to get external project id for ${module.name} from project ${project.name}.")
        externalId to module
      }
      .toMap()

  val projectDataNodes: List<DataNode<ProjectData>> =
    GradleSettings.getInstance(project)
      .linkedProjectsSettings
      .mapNotNull { it.externalProjectPath }
      .toSet()
      .map { externalProjectPath ->
        val externalProjectInfo = projectDataManager.getExternalProjectData(project, GradleConstants.SYSTEM_ID, externalProjectPath)
        if (externalProjectInfo != null && externalProjectInfo.lastImportTimestamp != externalProjectInfo.lastSuccessfulImportTimestamp) {
          requestSync("Sync failed in last import attempt. Path: ${externalProjectInfo.externalProjectPath}")
        }
        externalProjectInfo?.externalProjectStructure?.modules()?.forEach { moduleDataNode ->
          if (ExternalSystemApiUtil.getChildren(moduleDataNode, ANDROID_MODEL).singleOrNull() != null) {
            val isLinked = moduleDataNode.linkAndroidModuleGroup(project) { data -> modulesById[data.id] }
            if (!isLinked) {
              requestSync("Not enough information to link all modules from: ${moduleDataNode.data.id}")
            }
          }
        }
        val localAndroidSdkPath = LocalProperties(File(externalProjectPath)).androidSdkPath
        if (localAndroidSdkPath == null || localAndroidSdkPath.path.isNullOrBlank()) {
          requestSync("No SDK path defined in local.properties.", Trigger.TRIGGER_PROJECT_MODIFIED)
        }
        if (localAndroidSdkPath.path != IdeSdks.getInstance().androidSdkPath?.path) {
          requestSync("SDK path defined in local.properties is invalid.", Trigger.TRIGGER_PROJECT_MODIFIED)
        }

        val moduleVariants = project.getSelectedVariantAndAbis()
        externalProjectInfo?.findAndSetupSelectedCachedVariantData(moduleVariants)
          ?: requestSync("DataNode<ProjectData> not found for $externalProjectPath. Variants: $moduleVariants")
      }


  if (projectDataNodes.isEmpty()) {
    requestSync("No linked projects found")
  }

  val facets =
    existingGradleModules
      .flatMap { module ->
        FacetManager.getInstance(module).let {
          it.getFacetsByType(GradleFacet.getFacetTypeId()) +
            it.getFacetsByType(AndroidFacet.ID) +
            it.getFacetsByType(NdkFacet.facetTypeId)
        }
      }
      .toMutableSet()

  // With phased sync, facet entities are stored in the workspace model instead of using JPS (i.e. XML) serialization.
  // When deserializing the entities in the new workspace model, the external source is lost, but we know at this point these are
  // facets tied to Gradle modules, so we can explicitly mark them here. We don't need to do anything similar for modules, as that
  // information isn't lost for modules.
  if (StudioFlags.PHASED_SYNC_ENABLED.get() && StudioFlags.PHASED_SYNC_BRIDGE_DATA_SERVICE_DISABLED.get()) {
    facets.forEach {
      it.externalSource = ExternalProjectSystemRegistry.getInstance().getSourceById(GradleConstants.SYSTEM_ID.getId())
    }
  }


  existingGradleModules.asSequence().flatMap { module ->
    ModuleRootManager.getInstance(module)
      .orderEntries.filterIsInstance<LibraryOrderEntry>().asSequence()
      .mapNotNull { it.library }
      .filter { it.name?.startsWith("Gradle: ") ?: false }
      // Module level libraries and libraries not listed in any library table usually represent special kinds of artifacts like local
      // libraries in `lib` folders, generated code, etc. We are interested in libraries with JAR files in the shared Gradle cache.
      .filter { it.table?.tableLevel == LibraryTablesRegistrar.PROJECT_LEVEL }
  }
    .distinct()
    .forEach { library ->
      // CLASSES root contains jar file and res folder, and none of them are guaranteed to exist. Fail validation only if
      // all files are missing. If the library lifetime in the Gradle cache has expired there will be none that exists.
      // TODO(b/160088430): Review when the platform is fixed and not existing entries are correctly removed.
      // For other types of root we do not perform any validations since urls are intentionally or unintentionally not removed
      // from libraries if the location changes. See TODO: b/160088430.
      val expectedUrls = library.getUrls(OrderRootType.CLASSES)
      if (expectedUrls.isNotEmpty() && expectedUrls.none { url: String -> VirtualFileManager.getInstance().findFileByUrl(url) != null }) {
        requestSync("Cannot find any of:\n ${expectedUrls.joinToString(separator = ",\n") { it }}\n in ${library.name}")
      }
    }

  class ModuleSetupData(
    val module: Module,
    val dataNode: DataNode<out ModuleData>,
    val gradleAndroidModelFactory: (GradleAndroidModelData) -> GradleAndroidModel
  )

  val moduleSetupData: Collection<ModuleSetupData> =
    projectDataNodes.flatMap { projectData ->
      val libraries = ExternalSystemApiUtil.find(projectData, IDE_LIBRARY_TABLE)?.data
      val kmpLibraries = ExternalSystemApiUtil.find(projectData, KMP_ANDROID_LIBRARY_TABLE)?.data
      val libraryResolver = IdeLibraryModelResolverImpl.fromLibraryTables(libraries, kmpLibraries)
      val modelFactory = GradleAndroidModel.createFactory(project, libraryResolver)
      projectData
        .modules()
        .flatMap inner@{ node ->
          val sourceSets = ExternalSystemApiUtil.findAll(node, GradleSourceSetData.KEY)

          val externalId = node.data.id
          val module = modulesById[externalId] ?: requestSync("Module $externalId not found")

          if (sourceSets.isEmpty()) {
            listOf(ModuleSetupData(module, node, modelFactory))
          } else {
            sourceSets
              .mapNotNull { sourceSet ->
                val moduleId = modulesById[sourceSet.data.id] ?: requestSync("Module ${sourceSet.data.id} not found")
                if (moduleId.isAndroidModule()) ModuleSetupData(moduleId, sourceSet, modelFactory) else null
              } + ModuleSetupData(module, node, modelFactory)
          }
        }
    }

  val attachModelActions = moduleSetupData.flatMap { data ->

    fun GradleAndroidModelData.validate() =
      shouldDisableForceUpgrades() ||
      AgpVersion.parse(Version.ANDROID_GRADLE_PLUGIN_VERSION).let { latestKnown ->
          !ApplicationManager.getApplication().getService(AgpVersionChecker::class.java).versionsAreIncompatible(agpVersion, latestKnown)
        }

    fun <T, V : Facet<*>> prepare(
      dataKey: Key<T>,
      getModel: (DataNode<*>, Key<T>) -> T?,
      getFacet: (Module) -> V?,
      attach: V.(T) -> Unit,
      validate: T.() -> Boolean = { true }
    ): (() -> Unit) {
      val model = getModel(data.dataNode, dataKey) ?: return { /* No model for datanode/datakey pair */ }
      if (!model.validate()) {
        requestSync("invalid model found for $dataKey in ${data.module.name}")
      }
      val facet = getFacet(data.module) ?: requestSync("no facet found for $dataKey in ${data.module.name} module")
      facets.remove(facet)
      return { facet.attach(model) }
    }

    // For models that can be broken into source sets we need to check the parent datanode for the model
    // For now we check both the current and parent node for code simplicity, once we finalize the layout for NDK and switch to
    // module per source set we should replace this code with were we know the model will be living.
    fun <T> getModelForMaybeSourceSetDataNode(): (DataNode<*>, Key<T>) -> T? {
      return { n, k -> getModelFromDataNode(n, k) ?: n.parent?.let { getModelFromDataNode(it, k) } }
    }
    listOf(
      prepare(
        ANDROID_MODEL,
        getModelForMaybeSourceSetDataNode(),
        AndroidFacet::getInstance,
        { AndroidModel.set(this, data.gradleAndroidModelFactory(it)) },
        validate = GradleAndroidModelData::validate
      ),
      prepare(GRADLE_MODULE_MODEL, ::getModelFromDataNode, GradleFacet::getInstance, GradleFacet::setGradleModuleModel),
      prepare(NDK_MODEL, ::getModelFromDataNode, NdkFacet::getInstance, NdkFacet::setNdkModuleModel)
    )
  }

  if (facets.isNotEmpty()) {
    requestSync("Cached models not available for:\n" + facets.joinToString(separator = ",\n") { "${it.module.name} : ${it.typeId}" })
  }

  LOG.info("Up-to-date models found in the cache. Not invoking Gradle sync.")
  attachModelActions.forEach { it() }

  additionalProjectSetup(project)

  GradleSyncStateHolder.getInstance(project).syncSkipped(null)
}

private fun <T> getModelFromDataNode(moduleDataNode: DataNode<*>, dataKey: Key<T>) =
  ExternalSystemApiUtil
    .getChildren(moduleDataNode, dataKey)
    .singleOrNull() // None or one node is expected here.
    ?.data

private fun additionalProjectSetup(project: Project) {
  AndroidPluginInfo.findFromModel(project)?.let { info ->
    project.getService(AssistantInvoker::class.java).maybeRecommendPluginUpgrade(project, info)
  }
  ProjectStructure.getInstance(project).analyzeProjectStructure()
  GradleVersionCatalogDetector.getInstance(project).maybeSuggestToml(project)
}

// Make sure that we do not use JUnit to run tests. This could happen if we find that we cannot use Gradle to run the unit tests.
// But since we have moved to running tests with Gradle we only want to run these when it is possible via Gradle.
// This would also make sure that we do not even try to create configurations using JUnit.
@VisibleForTesting
fun addJUnitProducersToIgnoredList(project: Project) {
  val producerService = RunConfigurationProducerService.getInstance(project)
  val allJUnitProducers = RunConfigurationProducer.EP_NAME.extensionList.filter { it.configurationType == JUnitConfigurationType.getInstance() }
  for (producer in allJUnitProducers) {
    producerService.state.ignoredProducers.add(producer::class.java.name)
  }
}

@RequiresBackgroundThread
private fun Module.isEmptyModule() =
  moduleFile == null &&
  rootManager.let { roots -> roots.contentEntries.isEmpty() && roots.orderEntries.all { it is ModuleSourceOrderEntry } }

private fun Module.hasOnlyNativeRoots() =
  rootManager.let { roots ->
    roots.sourceRoots.isNotEmpty() &&
    roots.getSourceRoots(NativeSourceRootType).size + roots.getSourceRoots(NativeHeaderRootType).size == roots.sourceRoots.size
  }
