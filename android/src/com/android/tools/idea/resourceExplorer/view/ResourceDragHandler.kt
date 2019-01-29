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

import com.android.resources.ResourceUrl
import com.android.tools.idea.resourceExplorer.model.DesignAssetSet
import java.awt.GraphicsEnvironment
import java.awt.Point
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.dnd.*
import java.awt.image.BufferedImage
import javax.swing.JList

/**
 * [DataFlavor] for [ResourceUrl]
 */
@JvmField
val RESOURCE_URL_FLAVOR = DataFlavor(ResourceUrl::class.java, "Resource Url")

private val SUPPORTED_DATA_FLAVORS = arrayOf(RESOURCE_URL_FLAVOR, DataFlavor.stringFlavor)

/**
 * Create a new [ResourceDragHandler]
 */
fun resourceDragHandler() = if (GraphicsEnvironment.isHeadless()) {
  HeadlessDragHandler()
}
else {
  ResourceDragHandlerImpl()
}


interface ResourceDragHandler {
  fun registerSource(assetList: JList<DesignAssetSet>)
}

/**
 * DragHandler in headless mode
 */
class HeadlessDragHandler internal constructor() : ResourceDragHandler {
  override fun registerSource(assetList: JList<DesignAssetSet>) {
    // Do Nothing
  }
}

/**
 * Drag handler for the resources list in the resource explorer.
 */
private class ResourceDragHandlerImpl internal constructor() : ResourceDragHandler {

  private val dragGestureListener: DragGestureListener = ResourceDragGestureListener()
  private val dragSource = DragSource()

  override fun registerSource(assetList: JList<DesignAssetSet>) {
    dragSource.createDefaultDragGestureRecognizer(assetList,
                                                  DnDConstants.ACTION_COPY_OR_MOVE,
                                                  dragGestureListener)
  }
}

private class ResourceDragGestureListener : DragGestureListener {

  private val dragSourceListener = object : DragSourceAdapter() {}

  override fun dragGestureRecognized(dragGestureEvent: DragGestureEvent) {
    @Suppress("UNCHECKED_CAST")
    val assetsList = dragGestureEvent.component as? JList<DesignAssetSet> ?: return
    val index = assetsList.locationToIndex(dragGestureEvent.dragOrigin)
    if (index == -1) {
      return
    }
    val assetSet = assetsList.model.getElementAt(index)
    val image = createDragPreview(assetsList, assetSet, index)

    dragGestureEvent.dragSource.startDrag(
      dragGestureEvent,
      DragSource.DefaultLinkDrop,
      image,
      Point(0, 0),
      createTransferable(assetSet),
      dragSourceListener
    )
  }

  private fun createDragPreview(jList: JList<DesignAssetSet>,
                                assetSet: DesignAssetSet?,
                                index: Int): BufferedImage {
    val component = jList.cellRenderer.getListCellRendererComponent(jList, assetSet, index, false, false)
    val image = BufferedImage(component.preferredSize.width, component.preferredSize.height, BufferedImage.TYPE_INT_ARGB)
    with(image.createGraphics()) {
      component.paint(this)
      dispose()
    }
    return image
  }
}

private fun createTransferable(assetSet: DesignAssetSet): Transferable {
  return object : Transferable {
    override fun getTransferData(flavor: DataFlavor?): Any? = when (flavor) {
      RESOURCE_URL_FLAVOR -> getResourceUrl(assetSet)
      DataFlavor.stringFlavor -> getResourceUrl(assetSet).toString()
      else -> null
    }

    override fun isDataFlavorSupported(flavor: DataFlavor?): Boolean = flavor in SUPPORTED_DATA_FLAVORS

    override fun getTransferDataFlavors(): Array<DataFlavor> = SUPPORTED_DATA_FLAVORS
  }
}

private fun getResourceUrl(assetSet: DesignAssetSet) =
  assetSet.getHighestDensityAsset().resourceItem.referenceToSelf.resourceUrl
