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
package com.android.tools.idea.ui.resourcemanager.sketchImporter.ui

import com.android.ide.common.resources.configuration.DensityQualifier
import com.android.resources.Density
import com.android.resources.ResourceType
import com.android.tools.idea.projectsystem.SourceProviderManager
import com.android.tools.idea.ui.resourcemanager.importer.DesignAssetImporter
import com.android.tools.idea.ui.resourcemanager.model.DesignAsset
import com.android.tools.idea.ui.resourcemanager.model.ResourceAssetSet
import com.android.tools.idea.ui.resourcemanager.rendering.AssetPreviewManager
import com.android.tools.idea.ui.resourcemanager.sketchImporter.converter.builders.SketchToStudioConverter.getResources
import com.android.tools.idea.ui.resourcemanager.sketchImporter.converter.models.ColorAssetModel
import com.android.tools.idea.ui.resourcemanager.sketchImporter.converter.models.DrawableAssetModel
import com.android.tools.idea.ui.resourcemanager.sketchImporter.converter.models.StudioResourcesModel
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.testFramework.LightVirtualFile
import org.jetbrains.android.facet.AndroidFacet
import java.awt.Color
import java.awt.event.ItemEvent

private fun LightVirtualFile.toAsset(name: String) = ResourceAssetSet(name, listOf(
  DesignAsset(this, listOf(DensityQualifier(Density.ANYDPI)), ResourceType.DRAWABLE)))

private const val DEFAULT_IMPORT_ALL = true
private const val valuesFolder = "values"
private const val colorsFileName = "sketch_colors.xml"

/**
 * The presenter in the MVP pattern developed for the Sketch Importer UI, connects the view to the model and deals with the logic behind the
 * user interface.
 */
class SketchImporterPresenter(private val sketchImporterView: SketchImporterView,
                              sketchFile: com.android.tools.idea.ui.resourcemanager.sketchImporter.ui.SketchFile,
                              private val designAssetImporter: DesignAssetImporter,
                              val facet: AndroidFacet,
                              assetPreviewManager: AssetPreviewManager) {

  private val project = facet.module.project
  private var importAll = DEFAULT_IMPORT_ALL
  private val presenters: MutableList<ResourcesPresenter> = sketchFile.pages
    .mapNotNull { page ->
      val pagePresenter = PagePresenter(page, project, sketchFile.library, assetPreviewManager)
      sketchImporterView.addPageView(pagePresenter)
      pagePresenter
    }.toMutableList()
  private val drawableFileGenerator = com.android.tools.idea.ui.resourcemanager.sketchImporter.converter.builders.ResourceFileGenerator(
    project)

  init {
    val documentPresenter = DocumentPresenter(sketchFile.document, project, sketchFile.library, assetPreviewManager)
    presenters.add(documentPresenter)
    sketchImporterView.addDocumentView(documentPresenter)
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
    val sourceProvider = SourceProviderManager.getInstance(facet).mainIdeaSourceProvider
    val resFolder = sourceProvider.resDirectories.firstOrNull()
                    ?: VfsUtil.createDirectories(VfsUtilCore.urlToPath(sourceProvider.resDirectoryUrls.first()))

    WriteCommandAction.runWriteCommandAction(facet.module.project) {
      val directory = VfsUtil.createDirectoryIfMissing(resFolder, valuesFolder)
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

abstract class ResourcesPresenter(
  project: Project,
  val assetPreviewManager: AssetPreviewManager
) {
  lateinit var view: ChildView
  var importAll = DEFAULT_IMPORT_ALL
  private val drawableFileGenerator = com.android.tools.idea.ui.resourcemanager.sketchImporter.converter.builders.ResourceFileGenerator(
    project)
  abstract val resources: com.android.tools.idea.ui.resourcemanager.sketchImporter.converter.models.StudioResourcesModel
  protected abstract val filesToDrawableAssets: Map<ResourceAssetSet, com.android.tools.idea.ui.resourcemanager.sketchImporter.converter.models.DrawableAssetModel>
  protected abstract val colorsToColorAssets: Map<Pair<Color, String>, com.android.tools.idea.ui.resourcemanager.sketchImporter.converter.models.ColorAssetModel>

  /**
   * Refresh preview panel in the associated view.
   */
  abstract fun populateView()

  /**
   * @return a mapping from [ResourceAssetSet] assets to [DrawableAssetModel] based on the content in the [StudioResourcesModel].
   */
  protected fun generateDrawableFiles() = resources.drawableAssets?.associate {
    drawableFileGenerator.generateDrawableFile(it).toAsset(it.name) to it
  } ?: emptyMap()

  /**
   * Filter only the files that are exportable (unless the importAll marker is set).
   */
  fun getDisplayableDrawables(): List<ResourceAssetSet> {
    val files = filesToDrawableAssets.keys
    return if (importAll) files.toList() else files.filter { filesToDrawableAssets[it]?.isExportable ?: false }
  }

  fun getSelectedDrawables(): List<ResourceAssetSet> {
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
  fun getAsset(file: ResourceAssetSet): com.android.tools.idea.ui.resourcemanager.sketchImporter.converter.models.AssetModel? = filesToDrawableAssets[file]
}

class PagePresenter(private val sketchPage: com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.pages.SketchPage,
                    val project: Project,
                    library: com.android.tools.idea.ui.resourcemanager.sketchImporter.converter.SketchLibrary,
                    assetPreviewManager: AssetPreviewManager
) : ResourcesPresenter(project, assetPreviewManager) {
  override val resources: com.android.tools.idea.ui.resourcemanager.sketchImporter.converter.models.StudioResourcesModel = getResources(sketchPage, library)
  override var filesToDrawableAssets = generateDrawableFiles()
  override val colorsToColorAssets = generateColorPairs()

  override fun populateView() {
    (view as PageView).refresh(sketchPage.name, getDisplayableDrawables(), getDisplayableColors())
  }
}

class DocumentPresenter(sketchDocument: com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.document.SketchDocument,
                        val project: Project,
                        library: com.android.tools.idea.ui.resourcemanager.sketchImporter.converter.SketchLibrary,
                        assetPreviewManager: AssetPreviewManager
) : ResourcesPresenter(project, assetPreviewManager) {
  override val resources: com.android.tools.idea.ui.resourcemanager.sketchImporter.converter.models.StudioResourcesModel = getResources(sketchDocument, library)
  override var filesToDrawableAssets = generateDrawableFiles()
  override val colorsToColorAssets = generateColorPairs()

  override fun populateView() {
    (view as DocumentView).refresh(getDisplayableDrawables(), getDisplayableColors())
  }
}