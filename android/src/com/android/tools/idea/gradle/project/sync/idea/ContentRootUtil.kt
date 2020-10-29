/*
 * Copyright (C) 2020 The Android Open Source Project
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

import com.android.builder.model.SourceProvider
import com.android.ide.common.gradle.model.IdeAndroidArtifact
import com.android.ide.common.gradle.model.IdeBaseArtifact
import com.android.ide.common.gradle.model.IdeVariant
import com.android.tools.idea.gradle.project.model.AndroidModuleModel
import com.android.tools.idea.gradle.project.model.NdkModuleModel
import com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys
import com.android.tools.idea.gradle.util.GradleUtil
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.project.ContentRootData
import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType
import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType.*
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.findAll
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.annotations.SystemDependent
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.io.File

/**
 * Sets up all of the content entries for a given [DataNode] containing the [ModuleData]. We use the given
 * [variant] to find the information required to set up the [ContentRootData] if the root folder of the module
 * exists as a content root we will attempt to re-use it. Anything outside of this folder will be added in their
 * own [ContentRootData].
 *
 * If no [variant] is provided that this method will operate solely on the information contained within the [DataNode] tree.
 * This method should have no effects outside of manipulating the [DataNode] tree from the [ModuleData] node downwards.
 */
@JvmOverloads
fun DataNode<ModuleData>.setupAndroidContentEntries(variant: IdeVariant? = null) {
  // 1 - Extract all of the information (models) we need from the nodes
  val androidModel = ExternalSystemApiUtil.find(this, AndroidProjectKeys.ANDROID_MODEL)?.data ?: return
  val nativeModel = ExternalSystemApiUtil.find(this, AndroidProjectKeys.NDK_MODEL)?.data
  val selectedVariant = variant ?: androidModel.selectedVariant

  // 2 - Compute all of the content roots that this module requires from the models we obtained above.
  val existingContentRoots = findAll(this, ProjectKeys.CONTENT_ROOT)
  val contentRoots = collectContentRootData(selectedVariant, androidModel, nativeModel, existingContentRoots)

  // 3 - Add the ContentRootData nodes to the module.
  contentRoots.forEach { contentRootData ->
    this.createChild(ProjectKeys.CONTENT_ROOT, contentRootData)
  }
}

/**
 * This is a helper for [setupAndroidContentEntries] this method collects all of the content roots for a given variant.
 *
 * This method will attempt to reuse the root project path from [androidModel] and will create new content entries for
 * anything outside of this path.
 *
 * Any existing [ContentRootData] will not be included in the returned collection, these may be modified.
 * The returned collection contains only new [ContentRootData] that were created inside this method.
 */
private fun collectContentRootData(
  variant: IdeVariant,
  androidModel: AndroidModuleModel,
  ndkModel: NdkModuleModel?,
  existingContentRoots: Collection<DataNode<ContentRootData>>?
) : Collection<ContentRootData> {
  val moduleRootPath = androidModel.rootDirPath.absolutePath

  // Attempt to reuse the main content root, we do this to reduce the work later when merging content roots in idea,
  // as apposed to creating a new data node for each path. We assume most of the paths will likely be under this
  // content root. To reduce the complexity of this code, for any paths outside the main content root we let
  // intellijs merging handle them by creating them all in a separate data node.
  val existingMainContentRoot = existingContentRoots?.firstOrNull {
    it.data.rootPath == moduleRootPath
  }

  val newContentRoots = mutableListOf<ContentRootData>()
  val mainContentRootData = existingMainContentRoot?.data ?: ContentRootData(GradleConstants.SYSTEM_ID, moduleRootPath).also {
    newContentRoots.add(it)
  }

  // Function passed in to the methods below to register each source path with a ContentRootData object.
  fun addSourceFolder(path: @SystemDependent String, sourceType: ExternalSystemSourceType?) {
    if (FileUtil.isAncestor(mainContentRootData.rootPath, path, false)) {
      if (sourceType != null) {
        mainContentRootData.storePath(sourceType, path)
      }
    }
    else {
      val contentRootData = ContentRootData(GradleConstants.SYSTEM_ID, path)
      if (sourceType != null) {
        contentRootData.storePath(sourceType, path)
      }
      newContentRoots.add(contentRootData)
    }
  }

  // Processes all generated sources that are contained directly in the artifacts and are not part of the source providers.
  variant.processGeneratedSources(androidModel, ::addSourceFolder)

  // Process all of the non-test source providers that are currently active for the selected variant.
  androidModel.activeSourceProviders.forEach { sourceProvider ->
    sourceProvider.processAll(false, ::addSourceFolder)
  }
  // Process all of the unit test and Android test source providers for the selected variant.
  (androidModel.unitTestSourceProviders + androidModel.androidTestSourceProviders).forEach { sourceProvider ->
    sourceProvider.processAll(true, ::addSourceFolder)
  }

  // Deal with any NDK specific folders.
  if (ndkModel != null) {
    // Exclude .externalNativeBuild (b/72450552)
    addSourceFolder(File(ndkModel.rootDirPath, ".externalNativeBuild").absolutePath, EXCLUDED)
    addSourceFolder(File(ndkModel.rootDirPath, ".cxx").absolutePath, EXCLUDED)
  }

  return newContentRoots
}

/**
 * Processes all the [SourceProvider]s and sources contained within this [IdeBaseArtifact], these are
 * processed by using the provided [processor], each [SourceProvider] is then passed to [processAll] along
 * with the processor.
 */
private fun IdeVariant.processGeneratedSources(
  androidModel: AndroidModuleModel,
  processor: (String, ExternalSystemSourceType) -> Unit
) {
  // Note: This only works with Gradle plugin versions 1.2 or higher. However we should be fine not supporting
  // this far back.
  GradleUtil.getGeneratedSourceFoldersToUse(mainArtifact, androidModel).forEach {
    processor(it.absolutePath, SOURCE_GENERATED)
  }
  mainArtifact.generatedResourceFolders.forEach {
    processor(it.absolutePath, RESOURCE_GENERATED)
  }

  testArtifacts.forEach { testArtifact ->
    // Note: This only works with Gradle plugin versions 1.2 or higher. However we should be fine not supporting
    // this far back.
    GradleUtil.getGeneratedSourceFoldersToUse(testArtifact, androidModel).forEach {
      processor(it.absolutePath, TEST_GENERATED)
    }
    if (testArtifact is IdeAndroidArtifact) {
      testArtifact.generatedResourceFolders.forEach {
        processor(it.absolutePath, TEST_RESOURCE_GENERATED)
      }
    }
  }
}

/**
 * Processes all sources contained within this [SourceProvider] using the given [processor]. This
 * [processor] is called with the absolute path to the file and the type of the source.
 */
private fun SourceProvider.processAll(
  forTest: Boolean = false,
  processor: (String, ExternalSystemSourceType?) -> Unit
) {
  (resourcesDirectories + resDirectories + assetsDirectories + mlModelsDirectories).forEach {
    processor(it.absolutePath, if (forTest) TEST_RESOURCE else RESOURCE)
  }
  processor(manifestFile.parentFile.absolutePath, null)

  val allSources = aidlDirectories + javaDirectories + cDirectories + cppDirectories + renderscriptDirectories + shadersDirectories

  allSources.forEach {
    processor(it.absolutePath, if (forTest) TEST else SOURCE)
  }
}