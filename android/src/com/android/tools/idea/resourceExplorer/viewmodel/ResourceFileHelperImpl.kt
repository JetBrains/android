/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.resourceExplorer.viewmodel

import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.tools.idea.res.ModuleResourceRepository
import com.android.tools.idea.resourceExplorer.model.DesignAsset
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.facet.ResourceFolderManager
import org.jetbrains.android.util.AndroidResourceUtil
import java.io.IOException

private val logger = Logger.getInstance(ResourceFileHelper::class.java)

/**
 * Helper class to manipulate Resources related Virtual file
 */
interface ResourceFileHelper {

  /**
   * Copy the [asset]'s [VirtualFile] into the resource directory of the provided [facet] and name
   * it [resourceName]
   */
  fun copyInProjectResources(asset: DesignAsset, resourceName: String, facet: AndroidFacet)

  class ResourceFileHelperImpl : ResourceFileHelper {

    override fun copyInProjectResources(asset: DesignAsset, resourceName: String, facet: AndroidFacet) {
      val folder = getResourceFolderForAsset(asset, facet)
      val fileName = getNewFileName(resourceName, asset)
      copyIfNotExisting(folder, fileName, asset)
    }

    private fun getNewFileName(resourceName: String, asset: DesignAsset) =
        resourceName + if (asset.file.extension != null) "." + asset.file.extension else ""

    /**
     * Create if necessary an return the directroy corresponding to the qualifiers
     * of the provided [DesignAsset].
     *
     * @throws IOException if the facet does not contains any resource folder
     */
    private fun getResourceFolderForAsset(asset: DesignAsset, facet: AndroidFacet): VirtualFile {
      val resourceDirs = ModuleResourceRepository.getOrCreateInstance(facet).resourceDirs
      if (resourceDirs.isEmpty()) {
        throw IOException("No resource directory found in this module (${facet.module.name})")
      }
      val resourceSubdirs = AndroidResourceUtil.getResourceSubdirs(asset.type, resourceDirs)
      val folderName = getFolderConfiguration(asset).getFolderName(asset.type)
      return findOrCreateResourceFolder(resourceSubdirs, folderName, facet)
    }

    /**
     * Build a [FolderConfiguration] from the [com.android.ide.common.resources.configuration.ResourceQualifier]
     * of the provided [DesignAsset]
     */
    private fun getFolderConfiguration(asset: DesignAsset): FolderConfiguration {
      val folderConfiguration = FolderConfiguration()
      asset.qualifiers.forEach(folderConfiguration::addQualifier)
      return folderConfiguration
    }

    private fun findOrCreateResourceFolder(
        resourceSubdirs: List<VirtualFile>,
        folderName: String,
        facet: AndroidFacet) =
        findResourceSubdir(resourceSubdirs, folderName) ?: createResSubDir(folderName, facet)

    private fun createResSubDir(folderName: String, facet: AndroidFacet) =
        WriteAction.compute<VirtualFile, IOException> {
          ResourceFolderManager.getInstance(facet).primaryFolder!!.createChildDirectory(this, folderName)
        }

    private fun findResourceSubdir(resourceSubdirs: List<VirtualFile>, folderName: String) =
        resourceSubdirs.firstOrNull { dir -> dir.name == folderName }

    private fun copyIfNotExisting(folder: VirtualFile, fileName: String, asset: DesignAsset) {
      if (folder.findChild(fileName) == null) {
        // TODO Ask if user wants to override
        WriteAction.run<IOException> {
          asset.file.copy(this, folder, fileName)
        }
        logger.info("$fileName copied into ${folder.path}")
      }
      else {
        logger.info("$fileName already exist in ${folder.path}")
      }
    }
  }
}