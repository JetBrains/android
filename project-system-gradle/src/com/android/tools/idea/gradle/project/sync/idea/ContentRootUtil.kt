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

import com.android.tools.idea.gradle.model.IdeAndroidArtifactCore
import com.android.tools.idea.gradle.model.IdeArtifactName
import com.android.tools.idea.gradle.model.IdeBaseArtifactCore
import com.android.tools.idea.gradle.model.IdeSourceProvider
import com.android.tools.idea.gradle.project.model.GradleAndroidModelData
import com.android.tools.idea.gradle.project.model.activeSourceProviders
import com.android.tools.idea.gradle.project.model.testFixturesSourceProviders
import com.android.tools.idea.gradle.util.GradleProjectSystemUtil.getGeneratedSourceFoldersToUse
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.project.ContentRootData
import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType
import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType.RESOURCE
import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType.RESOURCE_GENERATED
import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType.SOURCE
import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType.SOURCE_GENERATED
import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType.TEST
import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType.TEST_GENERATED
import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType.TEST_RESOURCE
import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType.TEST_RESOURCE_GENERATED
import com.intellij.openapi.externalSystem.model.project.ModuleData
import org.jetbrains.annotations.SystemDependent
import org.jetbrains.kotlin.idea.base.externalSystem.NodeWithData
import org.jetbrains.kotlin.idea.base.externalSystem.findAll
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.io.File

/**
 * Processes all sources contained within this [SourceProvider] using the given [processor]. This
 * [processor] is called with the absolute path to the file and the type of the source.
 */
private fun IdeSourceProvider.processAll(
  forTest: Boolean = false,
  processor: (String, ExternalSystemSourceType?) -> Unit
) {
  val allResources = resourcesDirectories + resDirectories + assetsDirectories + mlModelsDirectories + baselineProfileDirectories +
                    customSourceDirectories.map { it.directory }
  allResources.distinctBy { it.absolutePath }.forEach {
    processor(it.absolutePath, if (forTest) TEST_RESOURCE else RESOURCE)
  }

  processor(manifestFile.absolutePath, null)

  val allSources = aidlDirectories + javaDirectories + kotlinDirectories + renderscriptDirectories + shadersDirectories

  allSources.distinctBy { it.absolutePath }.forEach {
    processor(it.absolutePath, if (forTest) TEST else SOURCE)
  }
}

fun DataNode<ModuleData>.setupAndroidContentEntriesPerSourceSet(androidModel: GradleAndroidModelData) {
  val variant = androidModel.selectedVariantCore

  fun populateContentEntries(
    artifact: IdeBaseArtifactCore?,
    sourceProviders: List<IdeSourceProvider>
  ): List<DataNode<ContentRootData>> {
    val sourceSetDataNode = findSourceSetDataForArtifact(artifact ?: return emptyList())
    val contentRoots = collectContentRootDataForArtifact(artifact, sourceProviders, androidModel)
    return contentRoots.map { sourceSetDataNode.createChild(ProjectKeys.CONTENT_ROOT, it) }
  }

  val sourceSetContentRoots =
    mutableSetOf(populateContentEntries (variant.mainArtifact,  androidModel.activeSourceProviders)).apply {
      variant.hostTestArtifacts.forEach { add(populateContentEntries(it, androidModel.getTestSourceProviders(it.name))) }
      variant.deviceTestArtifacts
        .find { it.name == IdeArtifactName.ANDROID_TEST }
        ?.let { add(populateContentEntries(it, androidModel.getTestSourceProviders(it.name))) }
      add(populateContentEntries(variant.testFixturesArtifact, androidModel.testFixturesSourceProviders))
    }.flatten()

  val holderModuleRoots = findAll(ProjectKeys.CONTENT_ROOT)

  maybeMoveDuplicateHolderContentRootsToSourceSets(holderModuleRoots, sourceSetContentRoots)
}

private fun maybeMoveDuplicateHolderContentRootsToSourceSets(
  holderModuleRoots: List<NodeWithData<ContentRootData>>,
  sourceSetContentRoots: List<DataNode<ContentRootData>>
) {
  val sourceSetContentRootsByPath = sourceSetContentRoots.associateBy { it.data.rootPath }

  for (root in holderModuleRoots) {
    val replacement = sourceSetContentRootsByPath[root.data.rootPath] ?: continue
    for (sourceType in ExternalSystemSourceType.values()) {
      for (path in root.data.getPaths(sourceType)) {
        replacement.data.storePath(sourceType, path.path, path.packagePrefix)
      }
    }
    // NOTE: `.clear(true)` means remove this node from its parent and also clear it.
    root.node.clear(true)
  }
}

private fun collectContentRootDataForArtifact(
  artifact: IdeBaseArtifactCore?,
  sourceProviders: List<IdeSourceProvider>,
  androidModel: GradleAndroidModelData
) : Collection<ContentRootData> {
  checkNotNull(artifact) { "Couldn't find artifact for descriptor" }

  val newContentRoots = mutableListOf<ContentRootData>()

  // Function passed in to the methods below to register each source path with a ContentRootData object.
  fun addSourceFolder(path: @SystemDependent String, sourceType: ExternalSystemSourceType?) {
    val contentRootData = ContentRootData(GradleConstants.SYSTEM_ID, path)
    if (sourceType != null) {
      contentRootData.storePath(sourceType, path)
    }
    newContentRoots.add(contentRootData)
  }

  fun Collection<File>.processAs(type: ExternalSystemSourceType) = forEach { addSourceFolder(it.absolutePath, type) }
  fun Collection<String>.processAs(type: ExternalSystemSourceType) = forEach { addSourceFolder(it, type) }

  val generatedSourceFolderPaths = getGeneratedSourceFoldersToUse(
    artifact, androidModel.androidProject
  ).map(File::getAbsolutePath).toSet()
  sourceProviders.forEach { sourceProvider ->
    sourceProvider.processAll(artifact.isTestArtifact) { path, sourceType ->
      // For b/232007221 the variant specific source provider is currently giving us a kapt generated source folder as a Java folder.
      // In order to prevent duplicate root warnings and to ensure this kapt path is marked generated we ensure it is not added as
      // a source root.
      if (!generatedSourceFolderPaths.contains(path)) {
        addSourceFolder(path, sourceType)
      }
    }
  }

  generatedSourceFolderPaths.processAs(if (artifact.isTestArtifact) TEST_GENERATED else SOURCE_GENERATED)
  if (artifact is IdeAndroidArtifactCore) {
    artifact.generatedResourceFolders.processAs(if (artifact.isTestArtifact) TEST_RESOURCE_GENERATED else RESOURCE_GENERATED)
    artifact.generatedAssetFolders.processAs(if (artifact.isTestArtifact) TEST_GENERATED else SOURCE_GENERATED)
  }

  return newContentRoots
}

