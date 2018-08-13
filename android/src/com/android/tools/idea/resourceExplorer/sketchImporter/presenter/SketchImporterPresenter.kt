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
import com.android.tools.idea.resourceExplorer.sketchImporter.logic.DrawableGenerator
import com.android.tools.idea.resourceExplorer.sketchImporter.logic.VectorDrawable
import com.android.tools.idea.resourceExplorer.sketchImporter.model.ImportOptions
import com.android.tools.idea.resourceExplorer.sketchImporter.model.PageOptions
import com.android.tools.idea.resourceExplorer.sketchImporter.model.SketchFile
import com.android.tools.idea.resourceExplorer.sketchImporter.structure.SketchPage
import com.android.tools.idea.resourceExplorer.sketchImporter.view.SketchImporterView
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import org.jetbrains.android.facet.AndroidFacet

val PAGE_TYPES = arrayOf("Icons", "Symbols", "Colors", "Text", "Mixed")

class SketchImporterPresenter(private val sketchFile: SketchFile,
                              private val importOptions: ImportOptions,
                              private val designAssetImporter: DesignAssetImporter) {

  private val filesToExport = mutableSetOf<VirtualFile>()

  /**
   * Create assets and add a preview for each page in the view.
   */
  fun populatePages(project: Project, view: SketchImporterView) {
    filesToExport.clear()
    val pageIdToFiles = generateFiles(project)
    pageIdToFiles.forEach { pageId, files ->
      val pageOptions = importOptions.getPageOptions(pageId) ?: return@forEach

      val exportableFiles = if (!importOptions.isImportAll) getExportableFiles(files) else files
      filesToExport.addAll(exportableFiles)
      view.addIconPage(pageOptions.name, PAGE_TYPES, pageOptions.pageType.ordinal, exportableFiles)
    }
  }

  /**
   * Filter only the files that are exportable.
   */
  private fun getExportableFiles(files: List<LightVirtualFile>): List<LightVirtualFile> {
    return files.filter {
      importOptions.getIconOptions(it.name)?.isExportable ?: false
    }
  }

  /**
   * After populating pages, the files that were created can be added to the project.
   */
  fun importFilesIntoProject(facet: AndroidFacet) {
    val assets = filesToExport
      .associate { it to it.nameWithoutExtension }  // to be replaced with the name from options
      .map { (file, name) ->
        DesignAssetSet(name, listOf(DesignAsset(file, listOf(DensityQualifier(Density.ANYDPI)), ResourceType.DRAWABLE, name)))
      }
    designAssetImporter.importDesignAssets(assets, facet)
  }

  /**
   * @return mapping of [SketchPage.objectId] to lists of [LightVirtualFile] assets parsed from the page.
   */
  private fun generateFiles(project: Project): Map<String, List<LightVirtualFile>> {
    return sketchFile.pages
      .mapNotNull { sketchPage -> getPageOptions(importOptions, sketchPage)?.let { sketchPage to it } }
      .associate { (page, option) -> page.objectId to generateFilesFromPage(page, option, project) }
  }

  /**
   * Fetch options corresponding to [sketchPage] from the [importOptions].
   */
  private fun getPageOptions(importOptions: ImportOptions, sketchPage: SketchPage) =
    importOptions.getPageOptions(sketchPage.objectId)

  /**
   * @return a list of [LightVirtualFile] assets based on the content in the [SketchPage] and the [PageOptions].
   */
  private fun generateFilesFromPage(page: SketchPage,
                                    pageOptions: PageOptions,
                                    project: Project): List<LightVirtualFile> {

    return when (pageOptions.pageType) {
      PageOptions.PageType.ICONS -> createIconFiles(page, project)
      else -> emptyList()
    }
  }

  /**
   * @return a list of [LightVirtualFile] Vector Drawables corresponding to each artboard in [page].
   */
  private fun createIconFiles(page: SketchPage, project: Project) = page.artboards
    .associate { it to importOptions.getIconOptions(it.objectId)?.name }
    .map { (artboard, name) ->
      DrawableGenerator(project, VectorDrawable(artboard.createAllDrawableShapes(), artboard.frame)).generateFile(name)
    }
}