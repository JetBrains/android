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
package com.android.tools.idea.layoutinspector

import com.android.SdkConstants.CLASS_VIEW
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.resources.ResourceType
import com.android.testutils.MockitoKt.mock
import com.android.tools.idea.layoutinspector.model.AndroidWindow.ImageType
import com.android.tools.idea.layoutinspector.model.ComposeViewNode
import com.android.tools.idea.layoutinspector.model.DrawViewChild
import com.android.tools.idea.layoutinspector.model.DrawViewImage
import com.android.tools.idea.layoutinspector.model.FakeAndroidWindow
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.android.tools.idea.layoutinspector.tree.TreeSettings
import com.android.tools.idea.layoutinspector.util.ConfigurationParamsBuilder
import com.android.tools.idea.layoutinspector.util.TestStringTable
import com.android.tools.idea.layoutinspector.util.FakeTreeSettings
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import java.awt.Rectangle
import java.awt.Shape
import java.awt.image.BufferedImage

// TODO: find a way to indicate that this is a api 29+ model without having to specify an image on a subnode
fun model(project: Project = mock(), treeSettings: TreeSettings = FakeTreeSettings(), body: InspectorModelDescriptor.() -> Unit) =
  InspectorModelDescriptor(project).also(body).build(treeSettings)

fun window(windowId: Any,
           rootViewDrawId: Long,
           x: Int = 0,
           y: Int = 0,
           width: Int = 0,
           height: Int = 0,
           rootViewQualifiedName: String = CLASS_VIEW,
           imageType: ImageType = ImageType.BITMAP_AS_REQUESTED,
           layoutFlags: Int = 0,
           body: InspectorViewDescriptor.() -> Unit = {}) =
  FakeAndroidWindow(
    InspectorViewDescriptor(rootViewDrawId, rootViewQualifiedName, x, y, width, height, null, null, "", layoutFlags, null)
      .also(body).build(), windowId, imageType) { _, window ->
    ViewNode.writeDrawChildren { drawChildren ->
      window.root.flatten().forEach {
        it.drawChildren().clear()
        it.children.mapTo(it.drawChildren()) { child -> DrawViewChild(child) }
      }
    }
  }

private val defaultLayout = ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.LAYOUT, "defaultLayout")

fun view(drawId: Long,
         x: Int = 0,
         y: Int = 0,
         width: Int = 0,
         height: Int = 0,
         bounds: Shape? = null,
         qualifiedName: String = CLASS_VIEW,
         viewId: ResourceReference? = null,
         textValue: String = "",
         layoutFlags: Int = 0,
         layout: ResourceReference? = defaultLayout,
         body: InspectorViewDescriptor.() -> Unit = {}) =
  InspectorViewDescriptor(drawId, qualifiedName, x, y, width, height, bounds, viewId, textValue, layoutFlags, layout)
    .also(body).build()

fun compose(drawId: Long,
            name: String,
            composeFilename: String = "",
            composePackageHash: Int = -1,
            composeOffset: Int = 0,
            composeLineNumber: Int = 0,
            x: Int = 0,
            y: Int = 0,
            width: Int = 0,
            height: Int = 0,
            body: InspectorViewDescriptor.() -> Unit = {}) =
  InspectorViewDescriptor(drawId, name, x, y, width, height, null, null, "", 0,
                          composeFilename = composeFilename, composePackageHash = composePackageHash, composeOffset = composeOffset,
                          composeLineNumber = composeLineNumber).also(body)

interface InspectorNodeDescriptor
class InspectorImageDescriptor(internal val image: BufferedImage): InspectorNodeDescriptor

class InspectorViewDescriptor(private val drawId: Long,
                              private val qualifiedName: String,
                              private val x: Int,
                              private val y: Int,
                              private val width: Int,
                              private val height: Int,
                              private val bounds: Shape?,
                              private val viewId: ResourceReference?,
                              private val textValue: String,
                              private val layoutFlags: Int,
                              private val layout: ResourceReference? = defaultLayout,
                              private val composeFilename: String = "",
                              private val composePackageHash: Int = 0,
                              private val composeOffset: Int = 0,
                              private val composeLineNumber: Int = 0,
                              private val composeFlags: Int = 0,
                              val imageType: ImageType = ImageType.BITMAP_AS_REQUESTED): InspectorNodeDescriptor {
  private val children = mutableListOf<InspectorNodeDescriptor>()

  fun image(image: BufferedImage = mock()) {
    children.add(InspectorImageDescriptor(image))
  }

  fun view(drawId: Long,
           x: Int = 0,
           y: Int = 0,
           width: Int = 0,
           height: Int = 0,
           bounds: Shape? = null,
           qualifiedName: String = CLASS_VIEW,
           viewId: ResourceReference? = null,
           textValue: String = "",
           layoutFlags: Int = 0,
           layout: ResourceReference? = defaultLayout,
           body: InspectorViewDescriptor.() -> Unit = {}) =
    children.add(InspectorViewDescriptor(
      drawId, qualifiedName, x, y, width, height, bounds, viewId, textValue, layoutFlags, layout).apply(body))

  fun view(drawId: Long,
           rect: Rectangle?,
           qualifiedName: String = CLASS_VIEW,
           viewId: ResourceReference? = null,
           textValue: String = "",
           layout: ResourceReference? = null,
           body: InspectorViewDescriptor.() -> Unit = {}) =
    view(drawId, rect?.x ?: 0, rect?.y ?: 0, rect?.width ?: 0, rect?.height ?: 0, null, qualifiedName, viewId, textValue, 0, layout, body)

  fun compose(drawId: Long,
              name: String,
              composeFilename: String = "",
              composePackageHash: Int = -1,
              composeOffset: Int = 0,
              composeLineNumber: Int = 0,
              composeFlags: Int = 0,
              x: Int = 0,
              y: Int = 0,
              width: Int = 0,
              height: Int = 0,
              body: InspectorViewDescriptor.() -> Unit = {}) =
    children.add(InspectorViewDescriptor(drawId, name, x, y, width, height, null, null, "", 0,
                                         composeFilename = composeFilename, composePackageHash = composePackageHash,
                                         composeOffset = composeOffset, composeLineNumber = composeLineNumber,
                                         composeFlags = composeFlags).apply(body))

  fun build(): ViewNode {
    val result =
      if (composePackageHash == 0) ViewNode(drawId, qualifiedName, layout, x, y, width, height, bounds, viewId, textValue, layoutFlags)
      else ComposeViewNode(drawId, qualifiedName, null, x, y, width, height, null, null, textValue, 0,
                           composeFilename, composePackageHash, composeOffset, composeLineNumber, composeFlags)
    ViewNode.writeDrawChildren { drawChildren ->
      children.forEach {
        when (it) {
          is InspectorViewDescriptor -> {
            val viewNode = it.build()
            result.children.add(viewNode)
            result.drawChildren().add(DrawViewChild(viewNode))
          }
          is InspectorImageDescriptor -> {
            result.drawChildren().add(DrawViewImage(it.image, result))
          }
        }
      }
    }
    result.children.forEach { it.parent = result }
    return result
  }
}

class InspectorModelDescriptor(val project: Project) {
  private var root: InspectorViewDescriptor? = null

  fun view(drawId: Long,
           x: Int = 0,
           y: Int = 0,
           width: Int = 0,
           height: Int = 0,
           bounds: Shape? = null,
           qualifiedName: String = CLASS_VIEW,
           viewId: ResourceReference? = null,
           textValue: String = "",
           layoutFlags: Int = 0,
           layout: ResourceReference? = defaultLayout,
           imageType: ImageType = ImageType.BITMAP_AS_REQUESTED,
           body: InspectorViewDescriptor.() -> Unit = {}) {
    root = InspectorViewDescriptor(
      drawId, qualifiedName, x, y, width, height, bounds, viewId, textValue, layoutFlags, layout, imageType = imageType).apply(body)
  }

  fun view(drawId: Long,
           rect: Rectangle?,
           qualifiedName: String = CLASS_VIEW,
           viewId: ResourceReference? = null,
           textValue: String = "",
           imageType: ImageType = ImageType.BITMAP_AS_REQUESTED,
           layout: ResourceReference? = defaultLayout,
           body: InspectorViewDescriptor.() -> Unit = {}) =
    view(drawId, rect?.x ?: 0, rect?.y ?: 0, rect?.width ?: 0, rect?.height ?: 0, rect, qualifiedName, viewId, textValue, 0, layout,
         imageType, body)

  fun build(treeSettings: TreeSettings): InspectorModel {
    val model = InspectorModel(project)
    val windowRoot = root?.build() ?: return model
    val newWindow = FakeAndroidWindow(windowRoot, windowRoot.drawId, root?.imageType ?: ImageType.UNKNOWN) { _, window ->
      ViewNode.writeDrawChildren { getDrawChildren ->
        window.root.flatten().forEach {
          val drawChildren = it.getDrawChildren()
          val children = it.children
          if (drawChildren.any { drawChild -> drawChild is DrawViewImage }) {
            // We can't support changes to the child list when there are also images, currently, since we can't know where in the order
            // of children it should be, or if it should still be there at all.
            if (drawChildren.filterIsInstance<DrawViewChild>().map { drawChild -> drawChild.findFilteredOwner(treeSettings) } == children) {
              // No changes, great.
            }
            else {
              throw UnsupportedOperationException("TestLayoutInspectorModelBuilder doesn't support updating children of nodes with images.")
            }
          }
          else {
            // We don't have any images
            drawChildren.clear()
            children.mapTo(drawChildren) { child -> DrawViewChild(child) }
          }
        }
      }
    }
    model.update(newWindow, listOf(windowRoot.drawId), 0)
    if (ModuleManager.getInstance(project) != null) {
      val strings = TestStringTable()
      val config = ConfigurationParamsBuilder(strings)
      model.resourceLookup.updateConfiguration(config.makeSampleContext(project), strings)
    }
    // This is usually added by DeviceViewPanel
    model.modificationListeners.add { _, new, _ ->
      new?.refreshImages(1.0)
    }

    return model
  }
}
