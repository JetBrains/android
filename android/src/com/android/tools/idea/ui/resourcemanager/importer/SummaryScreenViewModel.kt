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

import com.android.tools.idea.ui.wizard.ProposedFileTreeModel
import com.android.tools.idea.ui.resourcemanager.model.ResourceAssetSet
import com.android.tools.idea.ui.resourcemanager.model.getMetadata
import com.android.tools.idea.ui.resourcemanager.plugin.DesignAssetRendererManager
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.IconUtil
import com.intellij.util.ui.JBUI
import org.jetbrains.android.facet.AndroidFacet
import java.io.File
import java.util.concurrent.CompletableFuture
import javax.swing.Icon
import javax.swing.ImageIcon
import kotlin.properties.Delegates

/**
 * ViewModel for the confirmation step during the import flow.
 *
 * It provides the necessary methods to display a preview screen of
 * the files being imported.
 *
 * The class will use the [designAssetImporter] to generate the
 * file paths where the source files from [assetSetsToImport] will be copied.
 *
 * [assetSetsToImport] first needs to be set with the [ResourceAssetSet] to import.
 *
 * To get the list of the target file paths, use [fileTreeModel].
 *
 * Finally the file are imported by calling [doImport] which delegates the call
 * to designAssetImporter.
 */
class SummaryScreenViewModel(private val designAssetImporter: DesignAssetImporter,
                             private val rendererManager: DesignAssetRendererManager,
                             private val facet: AndroidFacet,
                             val availableResDirs: Array<SourceSetResDir>) {

  var selectedFile: File? by Delegates.observable<File?>(null, { _, old, new ->
    if (!FileUtil.filesEqual(new, old)) {
      updateCallback()
    }
  })

  /**
   * Callback registered by the view to be notified when this view-model changes.
   */
  var updateCallback: () -> Unit = {}

  /**
   * A map of metadata where the key represent the name of the metadata and
   * the value is a human readable version of the metadata.
   */
  val metadata: Map<String, String>
    get() {
      val selectedFile = selectedFile ?: return emptyMap()
      return getMetadata(selectedFile)
    }

  /**
   * The set of all the [ResourceAssetSet] ready to be imported.
   *
   * Use [fileTreeModel] to get a tree model of the target file structure
   * of the file being imported.
   */
  var assetSetsToImport: Set<ResourceAssetSet> by Delegates.observable(emptySet(), { _, _, _ ->
    updateIntermediateAssets()
  })

  private val absoluteResDirPath get() = selectedResDir.absolutePath

  /**
   * The [SourceSetResDir] chosen by the user.
   */
  var selectedResDir: SourceSetResDir by Delegates.observable(availableResDirs.first(), { _, old, new ->
    if (old != new) {
      updateIntermediateAssets()
    }
  })

  /**
   * The list of [assetSetsToImport] converted into [IntermediateAsset]
   */
  private var importingAsset = designAssetImporter.toIntermediateAssets(assetSetsToImport, absoluteResDirPath)

  /**
   * A map used to convenience when the user select a file from the [fileTreeModel]
   * to get the the right [VirtualFile].
   */
  private var targetToSource: Map<String, VirtualFile> = emptyMap()

  private fun getPreviewFiles(): Set<File> = importingAsset
    .sortedBy { it.targetFolderName }
    .map { File(absoluteResDirPath, it.targetRelativePath) }
    .toSet()

  /**
   * Returns a [ProposedFileTreeModel] to be used in a [javax.swing.JTree]
   * using a [com.android.tools.idea.ui.wizard.ProposedFileTreeCellRenderer].
   */
  var fileTreeModel: ProposedFileTreeModel = ProposedFileTreeModel(absoluteResDirPath, getPreviewFiles())

  private fun updateIntermediateAssets() {
    importingAsset = designAssetImporter.toIntermediateAssets(assetSetsToImport)
    targetToSource = importingAsset.associate { it.targetRelativePath to it.intermediateFile }
    fileTreeModel = ProposedFileTreeModel(absoluteResDirPath, getPreviewFiles())
    updateCallback()
  }

  /**
   * Returns a [CompletableFuture] providing a [Icon] of the [selectedFile]
   *
   * The [selectedFile] is the path of the file returned by the [ProposedFileTreeModel.Node.file]
   * and should be set before calling this method.
   */
  fun getPreview(): CompletableFuture<Icon> {
    val selectedFile = selectedFile ?: return CompletableFuture.completedFuture(null)
    val path = FileUtil.getRelativePath(absoluteResDirPath, selectedFile)
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
    designAssetImporter.importDesignAssets(assetSetsToImport, facet, absoluteResDirPath)
  }

  /**
   * Returns the metadata to display to the user for the provided [selectedFile].
   * @see VirtualFile.getMetadata
   */
  private fun getMetadata(selectedFile: File): Map<String, String> {
    val path = FileUtil.getRelativePath(absoluteResDirPath, selectedFile)
    val sourceFile = targetToSource[path] ?: return emptyMap()
    return sourceFile.getMetadata().mapKeys { it.key.metadataName }
  }

  /**
   * Returns the path relative from the current module if [absolutePath] is
   * within the module otherwise returns the absolutePath
   **/
  fun getUserFormattedPath(absolutePath: File): String {
    val moduleDirPath = ModuleUtil.getModuleDirPath(facet.module)
    if(FileUtil.isAncestor(moduleDirPath, absolutePath.path, false)) {
      return absolutePath.relativeTo(File(moduleDirPath)).path
    } else {
      return absolutePath.path
    }
  }
}
