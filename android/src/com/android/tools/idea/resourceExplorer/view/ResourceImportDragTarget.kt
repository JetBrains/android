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
package com.android.tools.idea.resourceExplorer.view

import com.android.tools.idea.resourceExplorer.importer.ImportersProvider
import com.android.tools.idea.resourceExplorer.model.DesignAssetSet
import com.intellij.ide.dnd.DnDEvent
import com.intellij.ide.dnd.DnDNativeTarget
import com.intellij.ide.dnd.FileCopyPasteUtil
import com.intellij.openapi.util.SystemInfo
import org.jetbrains.android.facet.AndroidFacet
import java.awt.Image
import java.awt.Point
import java.io.File
import java.nio.file.FileVisitOption
import java.nio.file.Files
import kotlin.streams.toList

/**
 * A [DnDNativeTarget] which accepts files and try to import them as resources using the available
 * [com.android.tools.idea.resourceExplorer.plugin.ResourceImporter].
 */
class ResourceImportDragTarget(
  var facet: AndroidFacet,
  private val importersProvider: ImportersProvider
) : DnDNativeTarget {

  override fun cleanUpOnLeave() {
  }

  override fun update(event: DnDEvent): Boolean {
    if (FileCopyPasteUtil.isFileListFlavorAvailable(event)) {

      // On mac, we don't have access to the dragged object before the drop is accepted so
      // we don't check if there is any file
      if (SystemInfo.isMac || anyFileCanBeImported(getFiles(event))) {
        event.setDropPossible(true, "Import Files in project resources")
        return false // No need to delegate to parent
      }
    }

    return true // Delegate to parent if drop is not possible
  }

  /**
   * Returns a flat list of all the actual files (and not directory) within the hierarchy of the dropped
   * files.
   */
  private fun getFiles(event: DnDEvent?) =
    (FileCopyPasteUtil.getFileListFromAttachedObject(event?.attachedObject) as List<File>)
      .flatMap(File::getAllLeafFiles)

  /**
   * Checks if at least one of the file can be imported.
   */
  private fun anyFileCanBeImported(files: List<File>) =
    files.any { file -> hasImporterForFile(file) }

  private fun hasImporterForFile(file: File): Boolean =
    importersProvider.getImportersForExtension(file.extension).isNotEmpty()

  override fun drop(event: DnDEvent) {
    val assetSets = toDesignAssetSets(getFiles(event))
    ResourceImportDialog(assetSets, facet).show()
  }

  private fun toDesignAssetSets(files: List<File>): List<DesignAssetSet> {
    return files
      .groupBy({ importersProvider.getImportersForExtension(it.extension) }, { it })
      .filter { (importers, _) -> importers.isNotEmpty() }
      .flatMap { (importers, files) -> importers[0].processFiles(files) }
      .groupBy { it.name }
      .map { (name, assets) -> DesignAssetSet(name, assets) }
  }

  override fun updateDraggedImage(image: Image?, dropPoint: Point?, imageOffset: Point?) {
  }
}

/**
 * Returns a flat list of all the actual files (and not directory) within the hierarchy of this file.
 */
private fun File.getAllLeafFiles(): List<File> {
  return Files.walk(toPath(), 10, FileVisitOption.FOLLOW_LINKS)
    .filter { file -> Files.isRegularFile(file) }
    .map { it.toFile() }
    .toList()
}

