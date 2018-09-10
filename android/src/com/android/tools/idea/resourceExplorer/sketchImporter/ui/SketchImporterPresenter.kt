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
package com.android.tools.idea.resourceExplorer.sketchImporter.ui

import com.android.ide.common.resources.configuration.DensityQualifier
import com.android.resources.Density
import com.android.resources.ResourceType
import com.android.tools.idea.resourceExplorer.importer.DesignAssetImporter
import com.android.tools.idea.resourceExplorer.model.DesignAsset
import com.android.tools.idea.resourceExplorer.model.DesignAssetSet
import com.android.tools.idea.resourceExplorer.plugin.DesignAssetRendererManager
import com.android.tools.idea.resourceExplorer.sketchImporter.converter.SymbolsLibrary
import com.android.tools.idea.resourceExplorer.sketchImporter.converter.builders.DrawableFileGenerator
import com.android.tools.idea.resourceExplorer.sketchImporter.converter.builders.SketchToStudioConverter
import com.android.tools.idea.resourceExplorer.sketchImporter.converter.models.DrawableAssetModel
import com.android.tools.idea.resourceExplorer.sketchImporter.converter.models.AssetModel
import com.android.tools.idea.resourceExplorer.sketchImporter.parser.pages.SketchPage
import com.google.common.util.concurrent.ListenableFuture
import com.intellij.testFramework.LightVirtualFile
import org.jetbrains.android.facet.AndroidFacet
import java.awt.Dimension
import java.awt.Image
import java.awt.event.ItemEvent

private fun List<LightVirtualFile>.toAssets() = this.map {
  DesignAssetSet(it.name, listOf(DesignAsset(it, emptyList(), ResourceType.DRAWABLE)))
}

private const val DEFAULT_IMPORT_ALL = true

/**
 * The presenter in the MVP pattern developed for the Sketch Importer UI, connects the view to the model and deals with the logic behind the
 * user interface.
 */
class SketchImporterPresenter(private val sketchImporterView: SketchImporterView,
                              sketchFile: SketchFile,
                              private val designAssetImporter: DesignAssetImporter,
                              val facet: AndroidFacet) {

  private var importAll = DEFAULT_IMPORT_ALL
  private val pagePresenters = sketchFile.pages
    .mapNotNull { page ->
      val pagePresenter = PagePresenter(page, facet, sketchFile.symbolsLibrary)
      sketchImporterView.createPageView(pagePresenter)
      pagePresenter
    }

  init {
    sketchImporterView.addFilterExportableButton(!importAll)
    populatePages()
  }

  /**
   * Add previews in each [PageView] associated to the [PagePresenter]s and refresh the [SketchImporterView].
   */
  private fun populatePages() {
    pagePresenters.forEach {
      it.importAll = importAll
      it.populateView()
    }
    sketchImporterView.paintPages()
  }

  /**
   * Add exportable files to the project.
   */
  fun importFilesIntoProject() {
    val assets = pagePresenters.flatMap { presenter ->
      presenter.getExportableFiles().map { file ->
        // TODO change to only add selected files rather than all exportable files
        file to (presenter.getAsset(file)?.name ?: file.nameWithoutExtension)
      }
    }
      .map { (file, name) ->
        DesignAssetSet(name, listOf(DesignAsset(file, listOf(DensityQualifier(Density.ANYDPI)), ResourceType.DRAWABLE, name)))
      }
    designAssetImporter.importDesignAssets(assets, facet)
  }

  /**
   * Change the importAll setting and refresh the previews for all pages.
   */
  fun filterExportable(stateChange: Int) {
    importAll = when (stateChange) {
      ItemEvent.DESELECTED -> true
      ItemEvent.SELECTED -> false
      else -> DEFAULT_IMPORT_ALL
    }
    populatePages()
  }
}

class PagePresenter(private val sketchPage: SketchPage,
                    val facet: AndroidFacet,
                    private val symbolsLibrary: SymbolsLibrary) {

  lateinit var view: PageView
  private val pageOptions = PageOptions(sketchPage)
  private val rendererManager = DesignAssetRendererManager.getInstance()
  private var filesToAssets = generateFiles()
  var importAll = DEFAULT_IMPORT_ALL

  fun fetchImage(dimension: Dimension, designAssetSet: DesignAssetSet): ListenableFuture<out Image?> {
    val file = designAssetSet.designAssets.first().file
    return rendererManager.getViewer(file).getImage(file, facet.module, dimension)
  }

  /**
   * Refresh preview panel in the associated view.
   */
  fun populateView() {
    view.refreshPreviewPanel(sketchPage.name, PageOptions.PAGE_TYPE_LABELS, pageOptions.pageType.ordinal,
                             getExportableFiles().toAssets())
  }

  /**
   * Change the type of the page according to the [selection] and refresh the filesToAssets associated with that page (including
   * the previews in the [view]).
   */
  fun pageTypeChange(selection: String) {
    pageOptions.pageType = PageOptions.getPageTypeFromLabel(selection)
    filesToAssets = generateFiles()
    populateView()
  }

  /**
   * @return a mapping from [LightVirtualFile] assets to [AssetModel] based on the content in the [SketchPage] and the [PageOptions].
   */
  private fun generateFiles(): Map<LightVirtualFile, AssetModel> = when (pageOptions.pageType) {
    PageOptions.PageType.ICONS -> createIconFiles(sketchPage)
    else -> emptyMap()
  }

  /**
   * @return a mapping from [LightVirtualFile] Vector Drawables to [AssetModel], corresponding to each artboard in [page].
   */
  private fun createIconFiles(page: SketchPage): Map<LightVirtualFile, AssetModel> {
    val drawableFileGenerator = DrawableFileGenerator(facet.module.project)
    return page.artboards
      .associate { artboard ->
        val asset = SketchToStudioConverter.createDrawableAsset(artboard, symbolsLibrary)
        drawableFileGenerator.generateDrawableFile(asset) to asset
      }
  }

  /**
   * Filter only the files that are exportable (unless the importAll marker is set).
   */
  fun getExportableFiles(): List<LightVirtualFile> {
    val files = filesToAssets.keys
    return if (importAll) files.toList() else files.filter { filesToAssets[it]?.isExportable ?: false }
  }

  /**
   * Get options associated with a file.
   */
  fun getAsset(file: LightVirtualFile): AssetModel? = filesToAssets[file]
}