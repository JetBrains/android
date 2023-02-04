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
package com.android.tools.idea.ui.resourcemanager.plugin

import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.ui.resourcemanager.rendering.ImageCache
import com.android.tools.idea.ui.resourcemanager.importer.DesignAssetImporter
import com.android.tools.idea.ui.resourcemanager.model.DesignAsset
import com.android.tools.idea.ui.resourcemanager.rendering.AssetPreviewManagerImpl
import com.android.tools.idea.ui.resourcemanager.sketchImporter.ui.IMPORT_DIALOG_TITLE
import com.android.tools.idea.ui.resourcemanager.sketchImporter.ui.SketchImporterPresenter
import com.android.tools.idea.ui.resourcemanager.sketchImporter.ui.SketchImporterView
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import org.apache.commons.io.FilenameUtils
import org.jetbrains.android.facet.AndroidFacet
import javax.swing.JOptionPane

private val SUPPORTED_FILE_TYPES = setOf("sketch")
private const val OLDEST_SUPPORTED_SKETCH_VERSION = 50.0
private const val INVALID_SKETCH_FILE_ID = "Invalid Sketch file"
private const val IMPORT_ALL_OPTION = "Import all"
private const val IMPORT_SELECTED_OPTION = "Import selected"
private const val CANCEL_OPTION = "Cancel"
val DIALOG_OPTIONS = arrayOf(IMPORT_ALL_OPTION, IMPORT_SELECTED_OPTION, CANCEL_OPTION)

/**
 * [ResourceImporter] for Sketch files
 */
class SketchImporter : ResourceImporter {
  override val presentableName = "Sketch Importer"

  override val supportsBatchImport: Boolean = false

  override fun invokeCustomImporter(facet: AndroidFacet, filePaths: Collection<String>) {
    val filePath = filePaths.firstOrNull() ?: return
    val sketchFile = com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.SketchParser.read(filePath)
    if (sketchFile == null || sketchFile.meta.appVersion < OLDEST_SUPPORTED_SKETCH_VERSION) {
      showInvalidSketchFileNotification(filePath, sketchFile?.meta?.appVersion, facet.module.project)
    }
    else {
      val view = SketchImporterView()
      val disposable = Disposer.newDisposable("SketchImporter")
      val imageCache = ImageCache.createImageCache(disposable)
      // FIXME(b/140494768): Currently unused, get the ResourceResolver asynchronously to use this class.
      val resourceResolver = facet.module.project.projectFile?.let {
        ConfigurationManager.getOrCreateInstance(facet.module).getConfiguration(it).resourceResolver
      }?: return
      val assetPreviewManager = AssetPreviewManagerImpl(facet, imageCache, resourceResolver)
      view.presenter = SketchImporterPresenter(view, sketchFile, DesignAssetImporter(), facet, assetPreviewManager)
      showImportDialog(view)
      Disposer.dispose(disposable)
    }
  }

  override val hasCustomImport = true

  /**
   * Create a dialog allowing the user to preview and choose which assets they would like to import from the sketch file.
   */
  private fun showImportDialog(view: SketchImporterView) {
    val option = JOptionPane.showOptionDialog(null, view, IMPORT_DIALOG_TITLE, JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE,
                                              null, DIALOG_OPTIONS, DIALOG_OPTIONS[0])

    when (option) {
      0 -> view.presenter.importAllFilesIntoProject()
      1 -> view.presenter.importFilesIntoProject()
    }
  }

  override val userCanEditQualifiers get() = true

  override fun getSupportedFileTypes() = SUPPORTED_FILE_TYPES

  override fun getSourcePreview(asset: DesignAsset): DesignAssetRenderer? =
    DesignAssetRendererManager.getInstance().getViewer(SVGAssetRenderer::class.java)

  /**
   * Show a notification containing information about the [version] that was used to create the file at [path]. If [version] is null,
   * that means that either the file is not valid or it has been saved using a version of Sketch older than 43.0 (which is when the open
   * file format - the zip archive format that the Sketch Importer plugin is based upon - was introduced.
   */
  private fun showInvalidSketchFileNotification(path: String, version: Double?, project: Project) {
    val fileName = FilenameUtils.getName(path)
    val generalInfo = "Please make sure you use Sketch 50.0 or higher to save your sketch file."
    val versionInfo = if (version == null) {
      "$fileName seems to not be a valid Sketch file or has been saved with a version of Sketch older than 43.0."
    }
    else {
      "$fileName seems to have been saved using Sketch $version."
    }
    val notificationContent = "$generalInfo<br/>$versionInfo"
    val notificationTitle = "Invalid sketch file"

    Notification(INVALID_SKETCH_FILE_ID, notificationTitle, notificationContent, NotificationType.ERROR)
      .setSubtitle(fileName)
      .notify(project)
  }
}
