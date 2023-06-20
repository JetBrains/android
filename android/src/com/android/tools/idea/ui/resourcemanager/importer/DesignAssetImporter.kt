/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.ui.resourcemanager.importer

import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.resources.ResourceFolderType
import com.android.tools.idea.gradle.npw.project.GradleAndroidModuleTemplate
import com.android.tools.idea.projectsystem.SourceProviderManager
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.ui.resourcemanager.model.DesignAsset
import com.android.tools.idea.ui.resourcemanager.model.ResourceAssetSet
import com.android.tools.idea.ui.resourcemanager.model.designAssets
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import org.jetbrains.android.facet.AndroidFacet
import java.io.File

private const val IMPORT_COMMAND_NAME = "Import resources"

/**
 * Manage importing a batch of resources into the project.
 */
class DesignAssetImporter {

  val folderConfiguration = FolderConfiguration()

  fun importDesignAssets(assetSets: Collection<ResourceAssetSet>,
                         androidFacet: AndroidFacet,
                         resFolder: File = getOrCreateDefaultResDirectory(androidFacet)) {

    // Flatten all the design assets and then regroup them by folder name
    // so assets with the same folder name are imported together.
    val groupedAssets = toIntermediateAssets(assetSets, resFolder)
      .groupBy(IntermediateAsset::targetFolderName)

    LocalFileSystem.getInstance().refreshIoFiles(listOf(resFolder))

    WriteCommandAction.runWriteCommandAction(androidFacet.module.project, IMPORT_COMMAND_NAME, null, {
      groupedAssets
        .forEach { (folderName, importingAsset) ->
          copyAssetsInFolder(folderName, importingAsset, resFolder)
        }
    })
  }

  /**
   * Use the data available in the provided [ResourceAssetSet] to generate the [IntermediateAsset]
   * containing data about the target path of the [DesignAsset]s.
   */
  fun toIntermediateAssets(assetSets: Collection<ResourceAssetSet>,
                           resFolder: File = File("res")) =
    assetSets.flatMap { this.toIntermediateAsset(it, resFolder) }

  /**
   * Transforms the [DesignAsset] of the [assetSet] into a list of [IntermediateAsset].
   */
  private fun toIntermediateAsset(assetSet: ResourceAssetSet, resFolder: File) =
    assetSet.designAssets.map { IntermediateAsset(it.file, resFolder.path, getFolderName(it), assetSet.name) }

  /**
   * Copy the [DesignAsset]s into [subDirName] within the provided [resFolder].
   * @param subDirName the name of the resource subdirectory.
   * @param resFolder the full path to the target resource folder.
   */
  private fun copyAssetsInFolder(subDirName: String,
                                 designAssets: List<IntermediateAsset>,
                                 resFolder: File) {
    val folder = VfsUtil.createDirectoryIfMissing(resFolder.path)
    val directory = VfsUtil.createDirectoryIfMissing(folder, subDirName)
    designAssets.forEach {
      val resourceName = it.targetFileName
      if (it.sourceFile.fileSystem.protocol != LocalFileSystem.getInstance().protocol) {
        directory.findChild(resourceName)?.delete(this)
        val projectFile = directory.createChildData(this, resourceName)
        val contentsToByteArray = it.sourceFile.contentsToByteArray()
        projectFile.setBinaryContent(contentsToByteArray)
      }
      else {
        directory.findChild(resourceName)?.delete(this)
        it.sourceFile.copy(this, directory, resourceName)
      }
    }
  }

  /**
   * Get the folder name the the qualifiers applied to the [designAsset]
   */
  private fun getFolderName(designAsset: DesignAsset): String {
    folderConfiguration.reset()
    designAsset.qualifiers.forEach { folderConfiguration.addQualifier(it) }
    return folderConfiguration.getFolderName(ResourceFolderType.DRAWABLE)
  }
}

/**
 * Returns the first res/ directory of the main source provider of the [androidFacet].
 * If the facet has no res/ directory, it will try to create one.
 */
fun getOrCreateDefaultResDirectory(androidFacet: AndroidFacet): File {
  val resDirectories =
    SourceProviderManager.getInstance(androidFacet).mainIdeaSourceProvider.resDirectoryUrls.map { File(VfsUtil.urlToPath(it)) }
  if (resDirectories.isEmpty()) {
    val project = androidFacet.module.project
    return GradleAndroidModuleTemplate.createDefaultModuleTemplate(project, androidFacet.module.name).paths.resDirectories.first()
  }
  return resDirectories.firstOrNull { it.exists() }
         ?: resDirectories.first().also { it.createNewFile() }
}

/**
 * Returns a list of [SourceSetResDir] available in the provided [facet].
 *
 * If for some reason, no resource directory exist, it will return
 * a default one.
 *
 * @see getOrCreateDefaultResDirectory
 */
fun getSourceSetsResDirs(facet: AndroidFacet): Array<SourceSetResDir> {
  val resDirs = facet.module.getModuleSystem().getModuleTemplates(null)
    .flatMap { template ->
      template.paths.resDirectories.map {
        SourceSetResDir(it, template.name)
      }
    }.toTypedArray()

  return if (resDirs.isEmpty()) {
    arrayOf(SourceSetResDir(getOrCreateDefaultResDirectory(facet), "default"))
  }
  else {
    resDirs
  }
}

/**
 * A utility data class used to represent a resource directory for a sourceSet.
 */
data class SourceSetResDir constructor(
  /**
   * The relative path of this resource directory from the project base path.
   */
  val absolutePath: File,

  /**
   * The name of the source set containing this directory
   */
  val sourceSetName: String)