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
import com.android.tools.idea.ui.resourcemanager.model.DesignAsset
import com.android.tools.idea.ui.resourcemanager.model.DesignAssetSet
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.android.facet.AndroidFacet
import java.io.File

private const val IMPORT_COMMAND_NAME = "Import resources"

/**
 * Utility class meant to be use temporarily to map a source file to the desired name and folder after import.
 * It is used to do the transition from [DesignAssetSet] to resource file in the res/ directory because
 * [DesignAssetSet] are grouped by name while Android resources are grouped by qualifiers.
 */
private data class ImportingAsset(val name: String, val sourceFile: VirtualFile, var targetFolder: String)

/**
 * Manage importing a batch of resources into the project.
 */
class DesignAssetImporter {

  val folderConfiguration = FolderConfiguration()

  fun importDesignAssets(assetSets: Collection<DesignAssetSet>,
                         androidFacet: AndroidFacet,
                         resFolder: File = getDefaultResDirectory(androidFacet)) {

    // Flatten all the design assets and then regroup them by folder name
    // so assets with the same folder name are imported together.
    val groupedAssets = assetSets
      .flatMap(this::toImportingAsset)
      .groupBy(ImportingAsset::targetFolder)

    LocalFileSystem.getInstance().refreshIoFiles(listOf(resFolder))

    WriteCommandAction.runWriteCommandAction(androidFacet.module.project, IMPORT_COMMAND_NAME, null, {
      groupedAssets
        .forEach { folderName, importingAsset ->
          copyAssetsInFolder(folderName, importingAsset, resFolder)
        }
    }, emptyArray())
  }

  /**
   * Transform the [DesignAsset] of the [assetSet] into a list of [ImportingAsset].
   */
  private fun toImportingAsset(assetSet: DesignAssetSet) =
    assetSet.designAssets.map { ImportingAsset(assetSet.name, it.file, getFolderName(it)) }

  /**
   * Copy the [DesignAsset]s into [folderName] within the provided [resFolder]
   */
  private fun copyAssetsInFolder(folderName: String,
                                 designAssets: List<ImportingAsset>,
                                 resFolder: File) {
    val folder = VfsUtil.findFileByIoFile(resFolder, true)
    val directory = VfsUtil.createDirectoryIfMissing(folder, folderName)
    designAssets.forEach {
      val resourceName = """${it.name}.${it.sourceFile.extension}"""
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
 * Returns the first res/ directory of the main source provider of the [androidFacet]
 */
private fun getDefaultResDirectory(androidFacet: AndroidFacet): File {
  return androidFacet.mainSourceProvider.resDirectories.let { resDirs ->
    resDirs.firstOrNull { it.exists() }
    ?: resDirs.first().also { it.createNewFile() }
  }
}