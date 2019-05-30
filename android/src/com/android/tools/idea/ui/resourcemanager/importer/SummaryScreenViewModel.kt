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

import com.android.tools.idea.npw.assetstudio.ui.ProposedFileTreeModel
import com.android.tools.idea.ui.resourcemanager.model.DesignAssetSet
import com.android.tools.idea.ui.resourcemanager.plugin.DesignAssetRendererManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.IconUtil
import com.intellij.util.ui.JBUI
import org.jetbrains.android.facet.AndroidFacet
import java.io.File
import java.util.concurrent.CompletableFuture
import javax.swing.Icon
import javax.swing.ImageIcon

/**
 * ViewModel for the confirmation step during the import flow.
 *
 * It provides the necessary methods to display a preview screen of
 * the files being imported.
 *
 * The class will use the [designAssetImporter] to generate the
 * file paths where the source files from [assetSetsToImport] will be copied.
 *
 * [assetSetsToImport] first needs to be set with the [DesignAssetSet] to import.
 *
 * To get the list of the target file paths, use [getFileTreeModel].
 *
 * Finally the file are imported by calling [doImport] which delegates the call
 * to designAssetImporter.
 */
class SummaryScreenViewModel(private val designAssetImporter: DesignAssetImporter,
                             private val rendererManager: DesignAssetRendererManager,
                             private val facet: AndroidFacet) {

  /**
   * The set of all the [DesignAssetSet] ready to be imported.
   *
   * Use [getFileTreeModel] to get a tree model of the target file structure
   * of the file being imported.
   */
  var assetSetsToImport: Set<DesignAssetSet> = emptySet()
    set(value) {
      field = value
      importingAsset = designAssetImporter.toImportingAssets(value)
      targetToSource = importingAsset
        .associate { it.targetPath to it.sourceFile }
    }

  /**
   * The list of [assetSetsToImport] converted into [ImportingAsset]
   */
  private var importingAsset = designAssetImporter.toImportingAssets(assetSetsToImport)

  private var targetToSource = importingAsset.map { it.targetPath to it.sourceFile }.toMap()

  private val resDirectory = getOrCreateDefaultResDirectory(facet).path

  private fun getPreviewFiles(): Set<File> = importingAsset
    .map { File(resDirectory, it.targetFolder + File.separatorChar + it.targetFileName) }
    .toSet()

  /**
   * Returns a [CompletableFuture] providing a [Icon] of the file to
   * be imported at [targetFilePath].
   *
   * The [targetFilePath] is the path of the file returned by the [ProposedFileTreeModel.Node.file].
   */
  fun getPreview(targetFilePath: String): CompletableFuture<Icon> {
    val path = FileUtil.getRelativePath(resDirectory, targetFilePath, File.separatorChar)
    val virtualFile = targetToSource[path]
                      ?: return CompletableFuture.completedFuture(IconUtil.getEmptyIcon(true))
    return rendererManager
      .getViewer(virtualFile)
      .getImage(virtualFile, facet.module, JBUI.size(200))
      .thenApply { image -> if (image != null) ImageIcon(image) else null }
  }

  /**
   * Import the assets in [assetSetsToImport] into the project.
   */
  fun doImport() {
    designAssetImporter.importDesignAssets(assetSetsToImport, facet)
  }

  /**
   * Returns a [ProposedFileTreeModel] to be used in a [javax.swing.JTree]
   * using a [com.android.tools.idea.npw.assetstudio.ui.ProposedFileTreeCellRenderer].
   */
  fun getFileTreeModel(): ProposedFileTreeModel {
    return ProposedFileTreeModel(getOrCreateDefaultResDirectory(facet), getPreviewFiles())
  }
}