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
package com.android.tools.idea.ui.resourcemanager.explorer

import com.android.tools.idea.ui.resourcemanager.model.ResourceAssetSet
import com.android.tools.idea.ui.resourcemanager.model.createTransferable
import com.intellij.ide.dnd.FileCopyPasteUtil
import com.intellij.util.ui.UIUtil
import java.awt.Cursor
import java.awt.GraphicsEnvironment
import java.awt.datatransfer.Transferable
import java.awt.image.BufferedImage
import javax.swing.DropMode
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.TransferHandler

/**
 * Create a new [ResourceDragHandler].
 *
 * Handles dragging out [ResourceAssetSet]s in a list.
 *
 * E.g: Drag a Drawable [ResourceAssetSet] into the LayoutEditor.
 *
 * @param importResourceDelegate Object to which [TransferHandler.importData] is delegated.
 */
fun resourceDragHandler(importResourceDelegate: ImportResourceDelegate) = if (GraphicsEnvironment.isHeadless()) {
  HeadlessDragHandler()
}
else {
  ResourceDragHandlerImpl(importResourceDelegate)
}

interface ResourceDragHandler {
  fun registerSource(assetList: JList<ResourceAssetSet>)
}

/**
 * An object that implements this interface consumes [TransferHandler.importData] in [ResourceDragHandler].
 */
interface ImportResourceDelegate {
  fun doImport(transferable: Transferable): Boolean
}

/**
 * DragHandler in headless mode
 */
class HeadlessDragHandler internal constructor() : ResourceDragHandler {
  override fun registerSource(assetList: JList<ResourceAssetSet>) {
    // Do Nothing
  }
}

/**
 * Drag handler for the resources list in the resource explorer.
 *
 * It doesn't deal with importing, but since it may consume the event, delegates the import operation to a given [ImportResourceDelegate].
 */
internal class ResourceDragHandlerImpl (private val importDelegate: ImportResourceDelegate) : ResourceDragHandler {

  override fun registerSource(assetList: JList<ResourceAssetSet>) {
    assetList.dragEnabled = true
    assetList.dropMode = DropMode.ON
    assetList.transferHandler = object : TransferHandler() {

      override fun canImport(support: TransferSupport?): Boolean {
        if (support == null) return false
        if (support.sourceDropActions and COPY != COPY) return false
        return FileCopyPasteUtil.isFileListFlavorAvailable(support.dataFlavors)
      }

      override fun importData(comp: JComponent?, t: Transferable?): Boolean {
        if (t == null) return false
        return importDelegate.doImport(t)
      }

      override fun getSourceActions(c: JComponent?) = TransferHandler.LINK

      override fun getDragImage() = createDragPreview(assetList, assetList.selectedValue, assetList.selectedIndex)

      override fun createTransferable(c: JComponent?): Transferable {
        c?.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
        return createTransferable(assetList.selectedValue.getHighestDensityAsset())
      }

      override fun exportDone(source: JComponent?, data: Transferable?, action: Int) {
        source?.cursor = Cursor.getDefaultCursor()
      }
    }
  }
}

private fun createDragPreview(jList: JList<ResourceAssetSet>,
                              assetSet: ResourceAssetSet?,
                              index: Int): BufferedImage {
  val component = jList.cellRenderer.getListCellRendererComponent(jList, assetSet, index, false, false)
  // The component having no parent to lay it out an set its size, we need to manually to it, otherwise
  // validate() won't be executed.
  component.setSize(component.preferredSize.width, component.preferredSize.height)
  component.validate()
  val image = UIUtil.createImage(component.width, component.height, BufferedImage.TYPE_INT_ARGB)
  with(image.createGraphics()) {
    color = jList.background
    fillRect(0, 0, component.width, component.height)
    component.paint(this)
    dispose()
  }
  return image
}