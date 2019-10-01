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
package com.android.tools.idea.layoutinspector.model

import com.android.ide.common.rendering.api.ResourceReference
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.xml.XmlTag
import java.awt.Image
import java.awt.Rectangle

/**
 * A view node represents a view in the view hierarchy as seen on the device.
 *
 * @param drawId the View.getUniqueDrawingId which is also the id found in the skia image
 * @param qualifiedName the qualified class name of the view
 * @param x the left edge of the view from the device left edge
 * @param y the top edge of the view from the device top edge
 * @param viewId the id set by the developer in the View.id attribute
 * @param textValue the text value if present
 */
class ViewNode(val drawId: Long,
               val qualifiedName: String,
               val layout: ResourceReference?,
               var x: Int,
               var y: Int,
               var width: Int,
               var height: Int,
               var viewId: ResourceReference?,
               var textValue: String) {

  val bounds: Rectangle
    get() = Rectangle(x, y, width, height)

  private var tagPointer: SmartPsiElementPointer<XmlTag>? = null

  val children = mutableListOf<ViewNode>()
  var parent: ViewNode? = null

  // imageBottom: the image painted before the sub views
  var imageBottom: Image? = null

  // imageTop: the image painted after the sub views
  var imageTop: Image? = null

  var tag: XmlTag?
    get() = tagPointer?.element
    set(value) {
      tagPointer = value?.let { SmartPointerManager.getInstance(value.project).createSmartPsiElementPointer(value) }
    }

  val unqualifiedName: String
    get() = qualifiedName.substringAfterLast('.')

  fun flatten(): Collection<ViewNode> {
    return children.flatMap { it.flatten() }.plus(this)
  }
}
