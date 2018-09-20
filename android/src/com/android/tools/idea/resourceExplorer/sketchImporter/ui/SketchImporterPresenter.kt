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
import com.android.tools.idea.resourceExplorer.sketchImporter.converter.builders.SketchToStudioConverter.getResources
import com.android.tools.idea.resourceExplorer.sketchImporter.converter.models.AssetModel
import com.android.tools.idea.resourceExplorer.sketchImporter.converter.models.DrawableAssetModel
import com.android.tools.idea.resourceExplorer.sketchImporter.converter.models.StudioResourcesModel
import com.android.tools.idea.resourceExplorer.sketchImporter.parser.document.SketchDocument
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
  private val documentPresenter = DocumentPresenter(sketchFile.document, facet, sketchFile.symbolsLibrary)

  init {
    sketchImporterView.createDocumentView(documentPresenter)
    sketchImporterView.addFilterExportableButton(!importAll)
    populateViews()
  }

  /**
   * Add previews in each [PageView] associated to the [PagePresenter]s and refresh the [SketchImporterView].
   */
  private fun populateViews() {
    pagePresenters.forEach {
      it.importAll = importAll
      it.populateView()
    }
    documentPresenter.importAll = importAll
    documentPresenter.populateView()
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
    populateViews()
  }
}

abstract class ResourcesPresenter(protected val facet: AndroidFacet) {
  var importAll = DEFAULT_IMPORT_ALL
  private val drawableFileGenerator = DrawableFileGenerator(facet.module.project)
  protected abstract val resources: StudioResourcesModel
  protected abstract val filesToAssets: Map<LightVirtualFile, DrawableAssetModel>
  private val rendererManager = DesignAssetRendererManager.getInstance()

  fun fetchImage(dimension: Dimension, designAssetSet: DesignAssetSet): ListenableFuture<out Image?> {
    val file = designAssetSet.designAssets.first().file
    return rendererManager.getViewer(file).getImage(file, facet.module, dimension)
  }

  /**
   * Refresh preview panel in the associated view.
   */
  abstract fun populateView()

  /**
   * @return a mapping from [LightVirtualFile] assets to [DrawableAssetModel] based on the content in the [StudioResourcesModel].
   */
  protected fun generateDrawableFiles(): Map<LightVirtualFile, DrawableAssetModel> {
    return resources.drawableAssets?.associate {
      drawableFileGenerator.generateDrawableFile(it) to it
    } ?: emptyMap()
  }

  /**
   * Filter only the files that are exportable (unless the importAll marker is set).
   */
  fun getExportableFiles(): List<LightVirtualFile> {
    val files = filesToAssets.keys
    return if (importAll) files.toList() else files.filter { filesToAssets[it]?.isExportable ?: false }
  }

  protected fun generateColorsList() = resources.colorAssets?.map {
    it.color to it.name
  } ?: emptyList()

  /**
   * Get options associated with a file.
   */
  fun getAsset(file: LightVirtualFile): AssetModel? = filesToAssets[file]
}

class PagePresenter(private val sketchPage: SketchPage,
                    facet: AndroidFacet,
                    symbolsLibrary: SymbolsLibrary
) : ResourcesPresenter(facet) {

  lateinit var view: PageView
  override val resources: StudioResourcesModel = getResources(sketchPage, symbolsLibrary)
  override var filesToAssets = generateDrawableFiles()

  override fun populateView() {
    view.refresh(sketchPage.name, getExportableFiles().toAssets(), generateColorsList())
  }
}

class DocumentPresenter(sketchDocument: SketchDocument,
                        facet: AndroidFacet,
                        symbolsLibrary: SymbolsLibrary
) : ResourcesPresenter(facet) {

  lateinit var view: DocumentView
  override val resources: StudioResourcesModel = getResources(sketchDocument, symbolsLibrary)
  override var filesToAssets = generateDrawableFiles()

  override fun populateView() {
    view.refresh(getExportableFiles().toAssets(), generateColorsList())
  }
}