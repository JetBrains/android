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
import com.android.tools.idea.resourceExplorer.sketchImporter.converter.SketchLibrary
import com.android.tools.idea.resourceExplorer.sketchImporter.converter.builders.ResourceFileGenerator
import com.android.tools.idea.resourceExplorer.sketchImporter.converter.builders.SketchToStudioConverter.getResources
import com.android.tools.idea.resourceExplorer.sketchImporter.converter.models.AssetModel
import com.android.tools.idea.resourceExplorer.sketchImporter.converter.models.ColorAssetModel
import com.android.tools.idea.resourceExplorer.sketchImporter.converter.models.DrawableAssetModel
import com.android.tools.idea.resourceExplorer.sketchImporter.converter.models.StudioResourcesModel
import com.android.tools.idea.resourceExplorer.sketchImporter.parser.document.SketchDocument
import com.android.tools.idea.resourceExplorer.sketchImporter.parser.pages.SketchPage
import com.google.common.util.concurrent.ListenableFuture
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.LightVirtualFile
import org.jetbrains.android.facet.AndroidFacet
import java.awt.Color
import java.awt.Dimension
import java.awt.Image
import java.awt.event.ItemEvent

private fun LightVirtualFile.toAsset(name: String) = DesignAssetSet(name, listOf(
  DesignAsset(this, listOf(DensityQualifier(Density.ANYDPI)), ResourceType.DRAWABLE)))

private const val DEFAULT_IMPORT_ALL = true
private const val valuesFolder = "values"
private const val colorsFileName = "sketch_colors.xml"

/**
 * The presenter in the MVP pattern developed for the Sketch Importer UI, connects the view to the model and deals with the logic behind the
 * user interface.
 */
class SketchImporterPresenter(private val sketchImporterView: SketchImporterView,
                              sketchFile: SketchFile,
                              private val designAssetImporter: DesignAssetImporter,
                              val facet: AndroidFacet) {

  private var importAll = DEFAULT_IMPORT_ALL
  private val presenters: MutableList<ResourcesPresenter> = sketchFile.pages
    .mapNotNull { page ->
      val pagePresenter = PagePresenter(page, facet, sketchFile.library)
      sketchImporterView.createPageView(pagePresenter)
      pagePresenter
    }.toMutableList()
  private val drawableFileGenerator = ResourceFileGenerator(
    facet.module.project)

  init {
    val documentPresenter = DocumentPresenter(sketchFile.document, facet, sketchFile.library)
    presenters.add(documentPresenter)
    sketchImporterView.createDocumentView(documentPresenter)
    sketchImporterView.addFilterExportableButton(!importAll)
    populateViews()
  }

  /**
   * Add previews in each [PageView] associated to the [PagePresenter]s and refresh the [SketchImporterView].
   */
  private fun populateViews() {
    presenters.forEach {
      it.importAll = importAll
      it.populateView()
    }
  }

  /**
   * Add selected resources to the project.
   */
  fun importFilesIntoProject() {
    val drawables = presenters.flatMap { presenter ->
      presenter.getSelectedDrawables()
    }
    designAssetImporter.importDesignAssets(drawables, facet)

    val colors = presenters.flatMap { presenter ->
      presenter.getSelectedColors()
    }
    generateSketchColorsFile(colors)
  }

  /**
   * Add all displayed resources to the project.
   */
  fun importAllFilesIntoProject() {
    val drawables = presenters.flatMap { presenter ->
      presenter.getDisplayableDrawables()
    }
    designAssetImporter.importDesignAssets(drawables, facet)

    val colors = presenters.flatMap { presenter ->
      presenter.getDisplayableColors()
    }
    generateSketchColorsFile(colors)
  }

  private fun generateSketchColorsFile(colors: List<Pair<Color, String>>) {
    if (colors.isEmpty())
      return

    val virtualFile = drawableFileGenerator.generateColorsFile(colors.toMutableList())
    val resFolder = facet.mainSourceProvider.resDirectories.let { resDirs ->
      resDirs.firstOrNull { it.exists() }
      ?: resDirs.first().also { it.createNewFile() }
    }

    WriteCommandAction.runWriteCommandAction(facet.module.project) {
      val folder = VfsUtil.findFileByIoFile(resFolder, true)
      val directory = VfsUtil.createDirectoryIfMissing(folder, valuesFolder)
      if (virtualFile.fileSystem.protocol != LocalFileSystem.getInstance().protocol) {
        directory.findChild(colorsFileName)?.delete(this)
        val projectFile = directory.createChildData(this, colorsFileName)
        val contentsToByteArray = virtualFile.contentsToByteArray()
        projectFile.setBinaryContent(contentsToByteArray)
      }
      else {
        directory.findChild(colorsFileName)?.delete(this)
        virtualFile.copy(this, directory, colorsFileName)
      }
    }
  }

  /**
   * Change the importAll setting and refresh all the previews.
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
  lateinit var view: ChildView
  var importAll = DEFAULT_IMPORT_ALL
  private val drawableFileGenerator = ResourceFileGenerator(
    facet.module.project)
  abstract val resources: StudioResourcesModel
  protected abstract val filesToDrawableAssets: Map<DesignAssetSet, DrawableAssetModel>
  protected abstract val colorsToColorAssets: Map<Pair<Color, String>, ColorAssetModel>
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
   * @return a mapping from [DesignAssetSet] assets to [DrawableAssetModel] based on the content in the [StudioResourcesModel].
   */
  protected fun generateDrawableFiles() = resources.drawableAssets?.associate {
    drawableFileGenerator.generateDrawableFile(it).toAsset(it.name) to it
  } ?: emptyMap()

  /**
   * Filter only the files that are exportable (unless the importAll marker is set).
   */
  fun getDisplayableDrawables(): List<DesignAssetSet> {
    val files = filesToDrawableAssets.keys
    return if (importAll) files.toList() else files.filter { filesToDrawableAssets[it]?.isExportable ?: false }
  }

  fun getSelectedDrawables(): List<DesignAssetSet> {
    return view.getSelectedDrawables()
  }

  /**
   * @return a mapping from [Pair]<[Color], [String]> assets to [ColorAssetModel] based on the content in the [StudioResourcesModel].
   */
  protected fun generateColorPairs() = resources.colorAssets?.associate {
    (it.color to it.name) to it
  } ?: emptyMap()

  /**
   * Filter only the colors that are exportable (unless the importAll marker is set).
   */
  fun getDisplayableColors(): List<Pair<Color, String>> {
    val colors = colorsToColorAssets.keys
    return if (importAll) colors.toList() else colors.filter { colorsToColorAssets[it]?.isExportable ?: false }
  }

  fun getSelectedColors(): List<Pair<Color, String>> {
    return view.getSelectedColors()
  }

  /**
   * Get options associated with an asset.
   */
  fun getAsset(file: DesignAssetSet): AssetModel? = filesToDrawableAssets[file]
}

class PagePresenter(private val sketchPage: SketchPage,
                    facet: AndroidFacet,
                    library: SketchLibrary
) : ResourcesPresenter(facet) {
  override val resources: StudioResourcesModel = getResources(sketchPage, library)
  override var filesToDrawableAssets = generateDrawableFiles()
  override val colorsToColorAssets = generateColorPairs()

  override fun populateView() {
    (view as PageView).refresh(sketchPage.name, getDisplayableDrawables(), getDisplayableColors())
  }
}

class DocumentPresenter(sketchDocument: SketchDocument,
                        facet: AndroidFacet,
                        library: SketchLibrary
) : ResourcesPresenter(facet) {
  override val resources: StudioResourcesModel = getResources(sketchDocument, library)
  override var filesToDrawableAssets = generateDrawableFiles()
  override val colorsToColorAssets = generateColorPairs()

  override fun populateView() {
    (view as DocumentView).refresh(getDisplayableDrawables(), getDisplayableColors())
  }
}