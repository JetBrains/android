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
import com.android.tools.idea.ui.resourcemanager.explorer.ImportResourceDelegate
import com.intellij.ide.dnd.DnDEvent
import com.intellij.ide.dnd.DnDNativeTarget
import com.intellij.ide.dnd.FileCopyPasteUtil
import com.intellij.openapi.util.SystemInfo
import org.jetbrains.android.facet.AndroidFacet
import java.awt.datatransfer.Transferable
import java.io.File

/**
 * A [DnDNativeTarget] which accepts files and try to import them as resources using the available
 * [com.android.tools.idea.ui.resourcemanager.plugin.ResourceImporter].
 */
class ResourceImportDragTarget(
  var facet: AndroidFacet,
  private val importersProvider: ImportersProvider
) : DnDNativeTarget, ImportResourceDelegate {

  override fun update(event: DnDEvent): Boolean {
    // Returns if parent should handle the event. I.e: Returns false when the import can be handled here.
    if (canImport(FileCopyPasteUtil.isFileListFlavorAvailable(event), getFilesFromEvent(event))) {
      event.setDropPossible(true, "Import Files in project resources")
      return false
    }
    return true
  }

  override fun drop(event: DnDEvent) {
    drop(getFilesFromEvent(event))
  }

  /** For [ImportResourceDelegate]. */
  override fun doImport(transferable: Transferable): Boolean {
    val files = getFilesFromTransferable(transferable)
    return canImport(FileCopyPasteUtil.isFileListFlavorAvailable(transferable.transferDataFlavors), files).also { canBeImported ->
      if (canBeImported) drop(files)
    }
  }

  private fun canImport(fileListFlavorAvailable: Boolean, filesToImport: Sequence<File>): Boolean {
    if (fileListFlavorAvailable) {
      // On mac, we don't have access to the dragged object before the drop is accepted so
      // we don't check if there is any file
      if (SystemInfo.isMac || anyFileCanBeImported(filesToImport)) {
        return true
      }
    }
    return false
  }

  /**
   * Checks if at least one of the file can be imported.
   */
  private fun anyFileCanBeImported(files: Sequence<File>) =
    files.any { file -> hasImporterForFile(file) }

  private fun hasImporterForFile(file: File): Boolean =
    importersProvider.getImportersForExtension(file.extension).isNotEmpty()

  /** Open the [ResourceImportDialog]. */
  private fun drop(files: Sequence<File>) {
    val assetSets = files.findAllDesignAssets(importersProvider)
    ResourceManagerTracking.logAssetAddedViaDnd(facet)
    ResourceImportDialog(facet, assetSets).show()
  }
}

/** Returns a flat list of all the actual files (and not directory) within the hierarchy of the dropped files. */
private fun getFilesFromEvent(event: DnDEvent?) =
  FileCopyPasteUtil.getFileListFromAttachedObject(event?.attachedObject).asSequence().flatMap(File::getAllLeafFiles)

/** Returns a flat list of all the actual files (and not directory) within the hierarchy of the dropped files. */
private fun getFilesFromTransferable(transferable: Transferable) =
  FileCopyPasteUtil.getFileList(transferable).orEmpty().asSequence().flatMap(File::getAllLeafFiles)
