/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.idea.data.service

import com.android.tools.idea.gradle.project.model.AndroidModuleModel
import com.android.tools.idea.gradle.project.sync.idea.computeSdkReloadingAsNeeded
import com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys.ANDROID_MODEL
import com.android.tools.idea.gradle.project.sync.issues.registerSyncIssues
import com.android.tools.idea.gradle.project.sync.setup.Facets.removeAllFacets
import com.android.tools.idea.gradle.project.sync.setup.post.MemorySettingsPostSyncChecker
import com.android.tools.idea.gradle.project.sync.setup.post.ProjectStructureUsageTracker
import com.android.tools.idea.gradle.project.sync.setup.post.TimeBasedReminder
import com.android.tools.idea.gradle.project.sync.setup.post.upgrade.recommendPluginUpgrade
import com.android.tools.idea.gradle.project.sync.setup.post.upgrade.shouldRecommendPluginUpgrade
import com.android.tools.idea.gradle.project.sync.validation.android.AndroidModuleValidator
import com.android.tools.idea.model.AndroidModel
import com.android.tools.idea.sdk.AndroidSdks
import com.android.tools.idea.sdk.IdeSdks
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.Key
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.IdeModelsProvider
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil.getRelativePath
import com.intellij.openapi.util.io.FileUtil.toSystemIndependentName
import com.intellij.openapi.util.io.systemIndependentPath
import com.intellij.openapi.vfs.VfsUtilCore
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.facet.AndroidFacetProperties.PATH_LIST_SEPARATOR_IN_FACET_CONFIGURATION
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Service that sets an Android SDK and facets to the modules of a project that has been imported from an Android-Gradle project.
 */
class AndroidModuleDataService @VisibleForTesting
internal constructor(private val myModuleValidatorFactory: AndroidModuleValidator.Factory) : ModuleModelDataService<AndroidModuleModel>() {

  // This constructor is called by the IDE. See this module's plugin.xml file, implementation of extension 'externalProjectDataService'.
  constructor() : this(AndroidModuleValidator.Factory())

  override fun getTargetDataKey(): Key<AndroidModuleModel> = ANDROID_MODEL

  public override fun importData(toImport: Collection<DataNode<AndroidModuleModel>>,
                                 project: Project,
                                 modelsProvider: IdeModifiableModelsProvider,
                                 modelsByModuleName: Map<String, AndroidModuleModel>) {
    val moduleValidator = myModuleValidatorFactory.create(project)
    for (module in modelsProvider.modules) {
      val androidModel = modelsByModuleName[module.name]

      if (androidModel != null) {
        // The SDK needs to be set here for Android modules, unfortunately we can't use intellijs
        // code to set this us as we need to reload the SDKs in case AGP has just downloaded it.
        // Android model is null for the root project module.
        val sdkToUse = AndroidSdks.getInstance().computeSdkReloadingAsNeeded(
          androidModel.androidProject.name,
          androidModel.androidProject.compileTarget,
          androidModel.androidProject.bootClasspath,
          IdeSdks.getInstance()
        )
        if (sdkToUse != null) {
          modelsProvider.getModifiableRootModel(module).sdk = sdkToUse
        }
        // Ensure sync issues can be reported.
        module.registerSyncIssues(androidModel.androidProject.syncIssues)

        // Create the Android facet and attache to the module.
        val androidFacet = modelsProvider.getModifiableFacetModel(module).getFacetByType(AndroidFacet.ID)
                           ?: createAndroidFacet(module, modelsProvider)
        // Configure that Android facet from the information in the AndroidModuleModel.
        configureFacet(androidFacet, androidModel)
        moduleValidator.validate(module, androidModel)
      }
      else {
        // If we don't have a model for this module then we need to ensure that no Android facets are left on the module.
        val facetModel = modelsProvider.getModifiableFacetModel(module)
        removeAllFacets(facetModel, AndroidFacet.ID)      }
    }

    if (modelsByModuleName.isNotEmpty()) {
      moduleValidator.fixAndReportFoundIssues()
    }
  }

  override fun onSuccessImport(imported: Collection<DataNode<AndroidModuleModel>>,
                               projectData: ProjectData?,
                               project: Project,
                               modelsProvider: IdeModelsProvider) {
    if (shouldRecommendPluginUpgrade(project)) recommendPluginUpgrade(project)

    MemorySettingsPostSyncChecker
      .checkSettings(project, TimeBasedReminder(project, "memory.settings.postsync", TimeUnit.DAYS.toMillis(1)))

    ProjectStructureUsageTracker(project).trackProjectStructure()
  }
}

/**
 * Creates an [AndroidFacet] on the given [module] with the default facet configuration.
 */
private fun createAndroidFacet(module: Module, modelsProvider: IdeModifiableModelsProvider): AndroidFacet {
  val model = modelsProvider.getModifiableFacetModel(module)
  val facetType = AndroidFacet.getFacetType()
  val facet = facetType.createFacet(module, AndroidFacet.NAME, facetType.createDefaultConfiguration(), null)
  model.addFacet(facet)
  return facet
}

/**
 * Configures the given [androidFacet] with the information that is present in the given [androidModuleModel].
 *
 * Note: we use the currently selected variant of the [androidModuleModel] to perform the configuration.
 */
private fun configureFacet(androidFacet: AndroidFacet, androidModuleModel: AndroidModuleModel) {
  @Suppress("DEPRECATION") // One of the legitimate assignments to the property.
  androidFacet.properties.ALLOW_USER_CONFIGURATION = false
  androidFacet.properties.PROJECT_TYPE = androidModuleModel.androidProject.projectType

  val modulePath = androidModuleModel.rootDirPath
  val sourceProvider = androidModuleModel.defaultSourceProvider
  androidFacet.properties.MANIFEST_FILE_RELATIVE_PATH = relativePath(modulePath, sourceProvider.manifestFile)
  androidFacet.properties.RES_FOLDER_RELATIVE_PATH = relativePath(modulePath, sourceProvider.resDirectories.firstOrNull())
  androidFacet.properties.ASSETS_FOLDER_RELATIVE_PATH = relativePath(modulePath, sourceProvider.assetsDirectories.firstOrNull())

  androidFacet.properties.RES_FOLDERS_RELATIVE_PATH = (androidModuleModel.activeSourceProviders.flatMap { provider ->
    provider.resDirectories
  } + androidModuleModel.mainArtifact.generatedResourceFolders).joinToString(PATH_LIST_SEPARATOR_IN_FACET_CONFIGURATION) { file ->
    VfsUtilCore.pathToUrl(file.absolutePath)
  }

  val testGenResources = androidModuleModel.artifactForAndroidTest?.generatedResourceFolders ?: listOf()
  // Why don't we include the standard unit tests source providers here?
  val testSourceProviders = androidModuleModel.androidTestSourceProviders
  androidFacet.properties.TEST_RES_FOLDERS_RELATIVE_PATH = (testSourceProviders.flatMap { provider ->
    provider.resDirectories
  } + testGenResources).joinToString(PATH_LIST_SEPARATOR_IN_FACET_CONFIGURATION) { file ->
    VfsUtilCore.pathToUrl(file.absolutePath)
  }

  // Why to we copy the variant information from the facet to the model, shouldn't this be the over-way around?
  val currentlySelectedVariant = androidFacet.properties.SELECTED_BUILD_VARIANT
  if (currentlySelectedVariant.isNotEmpty() && androidModuleModel.variantExists(currentlySelectedVariant)) {
    androidModuleModel.setSelectedVariantName(currentlySelectedVariant)
  }

  AndroidModel.set(androidFacet, androidModuleModel)
  androidModuleModel.syncSelectedVariantAndTestArtifact(androidFacet)
}

// It is safe to use "/" instead of File.separator. JpsAndroidModule uses it.
private const val SEPARATOR = "/"

private fun relativePath(basePath: File, file: File?) : String {
  val relativePath = if (file != null) getRelativePath(basePath, file) else null
  if (relativePath != null && !relativePath.startsWith(SEPARATOR)) {
    return SEPARATOR + toSystemIndependentName(relativePath)
  }
  return ""
}