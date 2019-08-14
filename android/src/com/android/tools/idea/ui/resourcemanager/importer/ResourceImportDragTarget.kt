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

import com.android.tools.idea.ui.resourcemanager.ResourceManagerTracking
import com.intellij.ide.dnd.DnDEvent
import com.intellij.ide.dnd.DnDNativeTarget
import com.intellij.ide.dnd.FileCopyPasteUtil
import com.intellij.openapi.util.SystemInfo
import org.jetbrains.android.facet.AndroidFacet
import java.awt.Image
import java.awt.Point
import java.io.File

/**
 * A [DnDNativeTarget] which accepts files and try to import them as resources using the available
 * [com.android.tools.idea.ui.resourcemanager.plugin.ResourceImporter].
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
  private fun getFiles(event: DnDEvent?) = FileCopyPasteUtil
    .getFileListFromAttachedObject(event?.attachedObject)
    .asSequence()
    .flatMap(File::getAllLeafFiles)


  /**
   * Checks if at least one of the file can be imported.
   */
  private fun anyFileCanBeImported(files: Sequence<File>) =
    files.any { file -> hasImporterForFile(file) }

  private fun hasImporterForFile(file: File): Boolean =
    importersProvider.getImportersForExtension(file.extension).isNotEmpty()

  override fun drop(event: DnDEvent) {
    val assetSets = getFiles(event)
      .findAllDesignAssets(importersProvider)
    ResourceManagerTracking.logAssetAddedViaDnd()
    ResourceImportDialog(facet, assetSets).show()
  }

  override fun updateDraggedImage(image: Image?, dropPoint: Point?, imageOffset: Point?) {
  }
}

