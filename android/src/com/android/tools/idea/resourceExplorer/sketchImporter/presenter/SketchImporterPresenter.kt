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
package com.android.tools.idea.resourceExplorer.sketchImporter.presenter

import com.android.ide.common.resources.configuration.DensityQualifier
import com.android.resources.Density
import com.android.resources.ResourceType
import com.android.tools.idea.resourceExplorer.importer.DesignAssetImporter
import com.android.tools.idea.resourceExplorer.model.DesignAsset
import com.android.tools.idea.resourceExplorer.model.DesignAssetSet
import com.android.tools.idea.resourceExplorer.plugin.DesignAssetRendererManager
import com.android.tools.idea.resourceExplorer.sketchImporter.logic.DrawableGenerator
import com.android.tools.idea.resourceExplorer.sketchImporter.logic.VectorDrawable
import com.android.tools.idea.resourceExplorer.sketchImporter.model.ImportOptions
import com.android.tools.idea.resourceExplorer.sketchImporter.model.PageOptions
import com.android.tools.idea.resourceExplorer.sketchImporter.model.SketchFile
import com.android.tools.idea.resourceExplorer.sketchImporter.structure.SketchPage
import com.android.tools.idea.resourceExplorer.sketchImporter.view.SketchImporterView
import com.google.common.util.concurrent.ListenableFuture
import com.intellij.testFramework.LightVirtualFile
import org.jetbrains.android.facet.AndroidFacet
import java.awt.Dimension
import java.awt.Image
import java.awt.event.ItemEvent

private fun List<LightVirtualFile>.toAssets() = this.map {
  DesignAssetSet(it.name, listOf(DesignAsset(it, emptyList(), ResourceType.DRAWABLE)))
}

class SketchImporterPresenter(private val view: SketchImporterView,
                              private val sketchFile: SketchFile,
                              private val importOptions: ImportOptions,
                              private val designAssetImporter: DesignAssetImporter,
                              val facet: AndroidFacet) {

  private val pageIdToFiles = generateFiles()

  init {
    view.addFilterExportableButton(!importOptions.importAll)
    populatePages()
  }

  /**
   * Create assets and add a preview for each page in the view.
   */
  private fun populatePages() {
    pageIdToFiles.forEach { pageId, files ->
      val pageOptions = importOptions.getPageOptions(pageId) ?: return@forEach

      view.addAssetPage(pageId, pageOptions.name, PageOptions.PAGE_TYPE_LABELS, pageOptions.pageType.ordinal,
                        getExportableFiles(files).toAssets())
    }
  }

  private val rendererManager = DesignAssetRendererManager.getInstance()

  fun fetchImage(dimension: Dimension, designAssetSet: DesignAssetSet): ListenableFuture<out Image?> {
    val file = designAssetSet.designAssets.first().file
    return rendererManager.getViewer(file).getImage(file, facet.module, dimension)
  }

  /**
   * Filter only the files that are exportable (unless the importAll marker is set).
   */
  private fun getExportableFiles(files: List<LightVirtualFile>): List<LightVirtualFile> {
    return if (importOptions.importAll) files else files.filter { importOptions.getIconOptions(it.name)?.isExportable ?: false }
  }

  /**
   * Add exportable files to the project.
   */
  fun importFilesIntoProject() {
    val assets = pageIdToFiles.values
      .flatten()
      .filter { importOptions.importAll || importOptions.getIconOptions(it.name)?.isExportable ?: false }  // to be replaced with selected files
      .associate { it to it.nameWithoutExtension }  // to be replaced with the name from options
      .map { (file, name) ->
        DesignAssetSet(name, listOf(DesignAsset(file, listOf(DensityQualifier(Density.ANYDPI)), ResourceType.DRAWABLE, name)))
      }
    designAssetImporter.importDesignAssets(assets, facet)
  }

  /**
   * @return mapping of [SketchPage.objectId] to lists of [LightVirtualFile] assets parsed from the page.
   */
  private fun generateFiles(): Map<String, List<LightVirtualFile>> {
    return sketchFile.pages
      .mapNotNull { sketchPage -> getPageOptions(sketchPage)?.let { sketchPage to it } }
      .associate { (page, option) -> page.objectId to generateFilesFromPage(page, option) }
  }

  /**
   * Fetch options corresponding to [sketchPage] from the [importOptions].
   */
  private fun getPageOptions(sketchPage: SketchPage) =
    importOptions.getPageOptions(sketchPage.objectId)

  /**
   * @return a list of [LightVirtualFile] assets based on the content in the [SketchPage] and the [PageOptions].
   */
  private fun generateFilesFromPage(page: SketchPage, pageOptions: PageOptions): List<LightVirtualFile> {
    return when (pageOptions.pageType) {
      PageOptions.PageType.ICONS -> createIconFiles(page)
      else -> emptyList()
    }
  }

  /**
   * @return a list of [LightVirtualFile] Vector Drawables corresponding to each artboard in [page].
   */
  private fun createIconFiles(page: SketchPage) = page.artboards
    .mapNotNull { artboard ->
      val iconName = importOptions.getIconOptions(artboard.objectId)?.name ?: return@mapNotNull null
      val vectorDrawable = VectorDrawable(artboard)
      DrawableGenerator(facet.module.project, vectorDrawable).generateFile(iconName)
    }

  /**
   * Change the type of the page with [pageId] according to the [selection] and refresh the files associated with that page (including
   * the previews in the [view]).
   */
  fun pageTypeChange(pageId: String, selection: String) {
    val pageOptions = importOptions.getPageOptions(pageId)

    if (pageOptions != null) {
      pageOptions.pageType = PageOptions.getPageTypeFromLabel(selection)
    }

    val page = sketchFile.findLayer(pageId) as? SketchPage ?: return
    val options = getPageOptions(page) ?: return
    val files = generateFilesFromPage(page, options)
    view.refreshPreview(pageId, getExportableFiles(files).toAssets())
  }

  /**
   * Change the importAll setting and refresh the preview.
   */
  fun filterExportable(stateChange: Int) {
    importOptions.importAll = when (stateChange) {
      ItemEvent.DESELECTED -> true
      ItemEvent.SELECTED -> false
      else -> ImportOptions.DEFAULT_IMPORT_ALL
    }
    view.clearPreview()
    populatePages()
    view.revalidatePreview()
    view.repaintPreview()
  }
}