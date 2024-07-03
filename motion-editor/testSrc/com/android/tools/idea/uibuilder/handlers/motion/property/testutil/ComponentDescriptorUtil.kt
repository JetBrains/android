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
package com.android.tools.idea.uibuilder.handlers.motion.property.testutil

import com.android.SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_X
import com.android.SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_Y
import com.android.SdkConstants.TOOLS_URI
import com.android.SdkConstants.XMLNS_PREFIX
import com.android.tools.idea.common.fixtures.ComponentDescriptor
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag

private const val LAYOUT_EDITOR_WIDTH = "layout_editor_width"
private const val LAYOUT_EDITOR_HEIGHT = "layout_editor_height"

object ComponentDescriptorUtil {

  fun component(file: XmlFile): ComponentDescriptor {
    return component(file.rootTag!!)
  }

  private fun component(tag: XmlTag): ComponentDescriptor {
    val x = getToolsCoordinate(tag, ATTR_LAYOUT_EDITOR_ABSOLUTE_X)
    val y = getToolsCoordinate(tag, ATTR_LAYOUT_EDITOR_ABSOLUTE_Y)
    val w = getToolsCoordinate(tag, LAYOUT_EDITOR_WIDTH)
    val h = getToolsCoordinate(tag, LAYOUT_EDITOR_HEIGHT)
    val descriptor = ComponentDescriptor(tag.name).withBounds(x, y, w, h).withMockView()
    tag.attributes
      .filter { !it.name.startsWith(XMLNS_PREFIX) }
      .forEach { descriptor.withAttribute(it.namespace, it.localName, it.value!!) }
    tag.subTags.forEach { descriptor.addChild(component(it), null) }
    return descriptor
  }

  private fun getToolsCoordinate(tag: XmlTag, name: String): Int {
    val dimen = tag.getAttributeValue(name, TOOLS_URI)!!
    return Integer.parseInt(dimen.substring(0, dimen.length - 2))
  }
}
