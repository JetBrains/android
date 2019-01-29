/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.resourceExplorer.importer

import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.resources.ResourceFolderType
import com.android.tools.idea.resourceExplorer.model.DesignAsset
import com.android.tools.idea.resourceExplorer.model.DesignAssetSet
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import org.jetbrains.android.facet.AndroidFacet
import java.io.File

/**
 * Manage importing a batch of resources into the project.
 */
class DesignAssetImporter {

  val folderConfiguration = FolderConfiguration()

  fun importDesignAssets(assetSets: List<DesignAssetSet>,
                         androidFacet: AndroidFacet,
                         resFolder: File = getDefaultResDirectory(androidFacet)) {

    // Flatten all the design assets and then regroup them by folder name
    // so assets with the same folder name are imported together.
    val groupedAssets = assetSets
      .flatMap { assetSet -> assetSet.designAssets }
      .groupBy { designAsset -> getFolderName(designAsset) }

    LocalFileSystem.getInstance().refreshIoFiles(listOf(resFolder))
    WriteCommandAction.runWriteCommandAction(androidFacet.module.project) {
      groupedAssets
        .forEach { folderName, designAssets ->
          copyAssetsInFolder(folderName, designAssets, resFolder)
        }
    }
  }

  /**
   * Copy the [DesignAsset]s into [folderName] within the provided [resFolder]
   */
  private fun copyAssetsInFolder(folderName: String,
                                 designAssets: List<DesignAsset>,
                                 resFolder: File) {
    val folder = VfsUtil.findFileByIoFile(resFolder, true)
    val directory = VfsUtil.createDirectoryIfMissing(folder, folderName)
    designAssets.forEach {
      val resourceName = """${it.name}.${it.file.extension}"""
      if (it.file.fileSystem.protocol != LocalFileSystem.getInstance().protocol) {
        directory.findChild(resourceName)?.delete(this)
        val projectFile = directory.createChildData(this, resourceName)
        val contentsToByteArray = it.file.contentsToByteArray()
        projectFile.setBinaryContent(contentsToByteArray)
      }
      else {
        directory.findChild(resourceName)?.delete(this)
        it.file.copy(this, directory, resourceName)
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
 * Returns the first res/ directory of the main source provider of the [androidFacet]
 */
private fun getDefaultResDirectory(androidFacet: AndroidFacet): File {
  return androidFacet.mainSourceProvider.resDirectories.let { resDirs ->
    resDirs.firstOrNull { it.exists() }
    ?: resDirs.first().also { it.createNewFile() }
  }
}