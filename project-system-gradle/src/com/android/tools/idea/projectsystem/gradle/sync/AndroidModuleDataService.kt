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
package com.android.tools.idea.projectsystem.gradle.sync

import com.android.AndroidProjectTypes
import com.android.tools.idea.IdeInfo
import com.android.tools.idea.facet.AndroidArtifactFacet
import com.android.tools.idea.gradle.model.IdeAndroidProjectType
import com.android.tools.idea.gradle.model.IdeLibraryModelResolver
import com.android.tools.idea.gradle.model.IdeVariant
import com.android.tools.idea.gradle.model.impl.IdeLibraryModelResolverImpl
import com.android.tools.idea.gradle.plugin.AndroidPluginInfo
import com.android.tools.idea.gradle.project.GradleProjectInfo
import com.android.tools.idea.gradle.project.ProjectStructure
import com.android.tools.idea.gradle.project.SupportedModuleChecker
import com.android.tools.idea.gradle.project.model.GradleAndroidModel
import com.android.tools.idea.gradle.project.model.GradleAndroidModelData
import com.android.tools.idea.gradle.project.sync.getProjectSyncRequest
import com.android.tools.idea.gradle.project.sync.idea.ModuleUtil.linkAndroidModuleGroup
import com.android.tools.idea.gradle.project.sync.idea.ModuleUtil.unlinkAndroidModuleGroup
import com.android.tools.idea.gradle.project.sync.idea.computeSdkReloadingAsNeeded
import com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys
import com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys.ANDROID_MODEL
import com.android.tools.idea.gradle.project.sync.idea.data.service.ModuleModelDataService
import com.android.tools.idea.gradle.project.sync.setProjectSyncRequest
import com.android.tools.idea.gradle.project.sync.setup.Facets.removeAllFacets
import com.android.tools.idea.gradle.project.sync.setup.post.MemorySettingsPostSyncChecker
import com.android.tools.idea.gradle.project.sync.setup.post.ProjectSetup
import com.android.tools.idea.gradle.project.sync.setup.post.ProjectStructureUsageTracker
import com.android.tools.idea.gradle.project.sync.setup.post.TimeBasedReminder
import com.android.tools.idea.gradle.project.sync.validation.android.AndroidModuleValidator
import com.android.tools.idea.gradle.project.upgrade.AssistantInvoker
import com.android.tools.idea.model.AndroidModel
import com.android.tools.idea.projectsystem.getAllLinkedModules
import com.android.tools.idea.projectsystem.isAndroidTestModule
import com.android.tools.idea.projectsystem.isMainModule
import com.android.tools.idea.run.RunConfigurationChecker
import com.android.tools.idea.sdk.AndroidSdks
import com.android.tools.idea.sdk.IdeSdks
import com.google.common.annotations.VisibleForTesting
import com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_AGP_VERSION_UPDATED
import com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_VARIANT_SELECTION_CHANGED_BY_USER
import com.intellij.facet.ModifiableFacetModel
import com.intellij.notification.NotificationGroupManager
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.Key
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.IdeModelsProvider
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.LanguageLevelModuleExtension
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.io.FileUtil.getRelativePath
import com.intellij.openapi.util.io.FileUtil.toSystemIndependentName
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.pom.java.LanguageLevel
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.facet.AndroidFacetProperties.PATH_LIST_SEPARATOR_IN_FACET_CONFIGURATION
import org.jetbrains.plugins.gradle.util.GradleConstants.SYSTEM_ID
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Service that sets an Android SDK and facets to the modules of a project that has been imported from an Android-Gradle project.
 */
class AndroidModuleDataService @VisibleForTesting
internal constructor(private val myModuleValidatorFactory: AndroidModuleValidator.Factory) : ModuleModelDataService<GradleAndroidModelData>() {

  // This constructor is called by the IDE. See this module's plugin.xml file, implementation of extension 'externalProjectDataService'.
  constructor() : this(AndroidModuleValidator.Factory())

  override fun getTargetDataKey(): Key<GradleAndroidModelData> = ANDROID_MODEL

  /**
   * This method is responsible for managing the presence of both the [AndroidFacet] and [AndroidArtifactFacet] across all modules.
   *
   * It also sets up the SDKs and language levels for all modules that stem from an [GradleAndroidModel]
   */
  public override fun importData(
    toImport: Collection<DataNode<GradleAndroidModelData>>,
    project: Project,
    modelsProvider: IdeModifiableModelsProvider,
    modelsByModuleName: Map<String, DataNode<GradleAndroidModelData>>
  ) {
    val moduleValidator = myModuleValidatorFactory.create(project)

    fun importAndroidModel(
      nodeToImport: DataNode<GradleAndroidModelData>,
      mainModuleDataNode: DataNode<ModuleData>,
      modelFactory: (GradleAndroidModelData) -> GradleAndroidModel
    ) {
      val mainModuleData = mainModuleDataNode.data
      val mainIdeModule = modelsProvider.findIdeModule(mainModuleData) ?: return

      val androidModel = nodeToImport.data

      mainModuleDataNode.linkAndroidModuleGroup(modelsProvider)

      val modules = mainIdeModule.getAllLinkedModules()
      modules.forEach { module ->
        val facetModel = modelsProvider.getModifiableFacetModel(module)

        val androidFacet = modelsProvider.getModifiableFacetModel(module).getFacetByType(AndroidFacet.ID)
          ?: createAndroidFacet(module, facetModel)
        // Configure that Android facet from the information in the AndroidModuleModel.
        val gradleAndroidModel = modelFactory(androidModel)
        configureFacet(androidFacet, module, gradleAndroidModel)

        moduleValidator.validate(module, gradleAndroidModel)
      }
    }

    toImport
      .mapNotNull { modelNode ->
        val moduleNode = ExternalSystemApiUtil.findParent(
          modelNode,
          ProjectKeys.MODULE
        ) ?: return@mapNotNull null

        val projectNode = ExternalSystemApiUtil.findParent(
          moduleNode,

          ProjectKeys.PROJECT
        ) ?: return@mapNotNull null

        Triple(projectNode, moduleNode, modelNode)
      }
      .groupBy { it.first }
      .forEach { (projectNode, nodes) ->
        val libraryResolver = createLibraryResolverFor(projectNode)
        val modelFactory = GradleAndroidModel.createFactory(project, libraryResolver)
        nodes.forEach { (_, moduleNode, modelNode) ->
          importAndroidModel(modelNode, moduleNode, modelFactory)
        }
      }
    if (modelsByModuleName.isNotEmpty()) {
      moduleValidator.fixAndReportFoundIssues()
    }
  }

  private fun Module.setupSdkAndLanguageLevel(
    modelsProvider: IdeModifiableModelsProvider,
    languageLevel: LanguageLevel?,
    sdkToUse: Sdk?
  ) {
    val rootModel = modelsProvider.getModifiableRootModel(this)
    if (languageLevel != null) {
      rootModel.getModuleExtension(
        LanguageLevelModuleExtension::class.java
      ).languageLevel = languageLevel
    }
    if (sdkToUse != null) {
      rootModel.sdk = sdkToUse
    }
  }

  override fun removeData(
    toRemoveComputable: Computable<out Collection<Module>>,
    toIgnore: Collection<DataNode<GradleAndroidModelData>>,
    projectData: ProjectData,
    project: Project,
    modelsProvider: IdeModifiableModelsProvider
  ) {
    for (module in toRemoveComputable.get()) {
      val facetModel = modelsProvider.getModifiableFacetModel(module)
      removeAllFacets(facetModel, AndroidFacet.ID)
      module.unlinkAndroidModuleGroup()
    }
  }

  /**
   * This may be called from either the EDT or a background thread depending on if the project import is being run synchronously.
   */
  override fun onSuccessImport(
    imported: Collection<DataNode<GradleAndroidModelData>>,
    projectData: ProjectData?,
    project: Project,
    modelsProvider: IdeModelsProvider
  ) {
    GradleProjectInfo.getInstance(project).isNewProject = false

    // TODO(b/200268010): this only triggers when we have actually run sync, as opposed to having loaded models from cache.  That means
    //  that we should be able to move this to some kind of sync listener.
    if (projectData != null) {
      val projectSyncRequest = project.getProjectSyncRequest(projectData.linkedExternalProjectPath)
      val trigger = projectSyncRequest?.trigger
      if (trigger != null) {
        project.setProjectSyncRequest(projectData.linkedExternalProjectPath, null)
        if (trigger != TRIGGER_AGP_VERSION_UPDATED && trigger != TRIGGER_VARIANT_SELECTION_CHANGED_BY_USER) {
          AndroidPluginInfo.findFromModel(project)?.let { info ->
            project.getService(AssistantInvoker::class.java).maybeRecommendPluginUpgrade(project, info)
          }
        }
      }
    }

    if (IdeInfo.getInstance().isAndroidStudio) {
      MemorySettingsPostSyncChecker
        .checkSettings(project, TimeBasedReminder(project, "memory.settings.postsync", TimeUnit.DAYS.toMillis(1)))
    }

    if (projectData != null) {
      ProjectStructureUsageTracker(project).trackProjectStructure(projectData.linkedExternalProjectPath)
    }

    SupportedModuleChecker.getInstance().checkForSupportedModules(project)

    ProjectSetup(project).setUpProject(false /* sync successful */)

    RunConfigurationChecker.getInstance(project).ensureRunConfigsInvokeBuild()

    ProjectStructure.getInstance(project).analyzeProjectStructure()
  }

  override fun postProcess(
    toImport: Collection<DataNode<GradleAndroidModelData>>,
    projectData: ProjectData?,
    project: Project,
    modelsProvider: IdeModifiableModelsProvider
  ) {
    super.postProcess(toImport, projectData, project, modelsProvider)
    // We need to set the SDK in postProcess since we need to ensure that this is run after the code in
    // KotlinGradleAndroidModuleModelProjectDataService.
    val modulesWithSDKSetupFailureByCompileTarget = mutableMapOf<String, MutableList<String>>()
    for (nodeToImport in toImport) {
      val mainModuleDataNode = ExternalSystemApiUtil.findParent(
        nodeToImport,
        ProjectKeys.MODULE
      ) ?: continue
      val mainModuleData = mainModuleDataNode.data
      val mainIdeModule = modelsProvider.findIdeModule(mainModuleData) ?: continue

      val androidModel = nodeToImport.data
      // The SDK needs to be set here for Android modules, unfortunately we can't use intellijs
      // code to set this us as we need to reload the SDKs in case AGP has just downloaded it.
      // Android model is null for the root project module.
      val sdkToUse = AndroidSdks.getInstance().computeSdkReloadingAsNeeded(
        project,
        androidModel.androidProject.projectPath.projectPath,
        androidModel.androidProject.compileTarget,
        androidModel.androidProject.bootClasspath,
        IdeSdks.getInstance()
      )

      if (sdkToUse == null) {
        modulesWithSDKSetupFailureByCompileTarget.getOrPut(androidModel.androidProject.compileTarget) { mutableListOf() }
          .add(androidModel.androidProject.projectPath.projectPath)
      }

      val modules = mainIdeModule.getAllLinkedModules()
      modules.forEach { module ->
        module.setupSdkAndLanguageLevel(modelsProvider, androidModel.getJavaLanguageLevel(), sdkToUse)
      }
    }

    modulesWithSDKSetupFailureByCompileTarget.forEach { (compileTarget, modules) ->
      val message = "Could not find compile target $compileTarget for modules ${modules.joinToString(", ")}"
      NotificationGroupManager.getInstance()
        .getNotificationGroup("Android SDK Setup Issues").createNotification(message, MessageType.ERROR).notify(project)
    }
  }
}

/**
 * Creates an [AndroidFacet] on the given [module] with the default facet configuration.
 */
private fun createAndroidFacet(module: Module, facetModel: ModifiableFacetModel): AndroidFacet {
  val facetType = AndroidFacet.getFacetType()
  val facet = facetType.createFacet(module, AndroidFacet.NAME, facetType.createDefaultConfiguration(), null)
  @Suppress("UnstableApiUsage")
  facetModel.addFacet(facet, ExternalSystemApiUtil.toExternalSource(SYSTEM_ID))
  return facet
}

/**
 * Configures the given [androidFacet] with the information that is present in the given [androidModuleModel].
 *
 * Note: we use the currently selected variant of the [androidModuleModel] to perform the configuration.
 */
private fun configureFacet(androidFacet: AndroidFacet, module: Module, androidModuleModel: GradleAndroidModel) {
  @Suppress("DEPRECATION") // One of the legitimate assignments to the property.
  androidFacet.properties.ALLOW_USER_CONFIGURATION = false
  @Suppress("DEPRECATION")
  androidFacet.properties.PROJECT_TYPE = when (androidModuleModel.androidProject.projectType) {
    IdeAndroidProjectType.PROJECT_TYPE_ATOM -> AndroidProjectTypes.PROJECT_TYPE_ATOM
    IdeAndroidProjectType.PROJECT_TYPE_APP -> AndroidProjectTypes.PROJECT_TYPE_APP
    IdeAndroidProjectType.PROJECT_TYPE_DYNAMIC_FEATURE -> AndroidProjectTypes.PROJECT_TYPE_DYNAMIC_FEATURE
    IdeAndroidProjectType.PROJECT_TYPE_FEATURE -> AndroidProjectTypes.PROJECT_TYPE_FEATURE
    IdeAndroidProjectType.PROJECT_TYPE_INSTANTAPP -> AndroidProjectTypes.PROJECT_TYPE_INSTANTAPP
    IdeAndroidProjectType.PROJECT_TYPE_LIBRARY -> AndroidProjectTypes.PROJECT_TYPE_LIBRARY
    IdeAndroidProjectType.PROJECT_TYPE_TEST -> AndroidProjectTypes.PROJECT_TYPE_TEST
  }

  val modulePath = androidModuleModel.rootDirPath
  val sourceProvider = androidModuleModel.defaultSourceProvider
  androidFacet.properties.MANIFEST_FILE_RELATIVE_PATH = relativePath(modulePath, sourceProvider.manifestFile)
  androidFacet.properties.RES_FOLDER_RELATIVE_PATH = relativePath(modulePath, sourceProvider.resDirectories.firstOrNull())
  androidFacet.properties.ASSETS_FOLDER_RELATIVE_PATH = relativePath(modulePath, sourceProvider.assetsDirectories.firstOrNull())

  androidFacet.properties.RES_FOLDERS_RELATIVE_PATH = when {
    module.isMainModule() ->
      (androidModuleModel.activeSourceProviders.flatMap { provider ->
        provider.resDirectories
      } + androidModuleModel.mainArtifact.generatedResourceFolders).joinToString(PATH_LIST_SEPARATOR_IN_FACET_CONFIGURATION) { file ->
        VfsUtilCore.pathToUrl(file.absolutePath)
      }
    else -> ""
  }

  val testGenResources = androidModuleModel.getArtifactForAndroidTest()?.generatedResourceFolders ?: listOf()
  // Why don't we include the standard unit tests source providers here?
  val testSourceProviders = androidModuleModel.androidTestSourceProviders
  androidFacet.properties.TEST_RES_FOLDERS_RELATIVE_PATH = when {
    module.isAndroidTestModule() ->
      (testSourceProviders.flatMap { provider ->
        provider.resDirectories
      } + testGenResources).joinToString(PATH_LIST_SEPARATOR_IN_FACET_CONFIGURATION) { file ->
        VfsUtilCore.pathToUrl(file.absolutePath)
      }
    else -> ""
  }

  AndroidModel.set(androidFacet, androidModuleModel)
  syncSelectedVariant(androidFacet, androidModuleModel.selectedVariant)
}

// It is safe to use "/" instead of File.separator. JpsAndroidModule uses it.
private const val SEPARATOR = "/"

private fun relativePath(basePath: File, file: File?): String {
  val relativePath = if (file != null) getRelativePath(basePath, file) else null
  if (relativePath != null && !relativePath.startsWith(SEPARATOR)) {
    return SEPARATOR + toSystemIndependentName(relativePath)
  }
  return ""
}

fun syncSelectedVariant(facet: AndroidFacet, variant: IdeVariant) {
  val state = facet.properties
  state.SELECTED_BUILD_VARIANT = variant.name
}

internal fun createLibraryResolverFor(projectNode: DataNode<ProjectData>): IdeLibraryModelResolver {
  val libraryTable = ExternalSystemApiUtil.find(projectNode, AndroidProjectKeys.IDE_LIBRARY_TABLE)?.data
    ?: error("IDE library table node not found")
  return IdeLibraryModelResolverImpl.fromLibraryTable(libraryTable)
}
