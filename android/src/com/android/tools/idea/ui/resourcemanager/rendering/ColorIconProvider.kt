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
package com.android.tools.idea.ui.resourcemanager.rendering

import com.android.ide.common.resources.ResourceResolver
import com.android.tools.idea.res.resolveMultipleColors
import com.android.tools.idea.ui.resourcemanager.model.DesignAsset
import com.android.tools.idea.ui.resourcemanager.model.resolveValue
import com.intellij.openapi.project.Project
import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import javax.swing.Icon

/**
 * Create a reusable [Icon] that is updated each time [getIcon] is called
 * and represent a color or a set of color if the provided [DesignAsset]
 * is a state list.
 */
class ColorIconProvider(
  private val project: Project,
  private val resourceResolver: ResourceResolver
) : AssetIconProvider {

  override var supportsTransparency: Boolean = true

  private val icon = ColorIcon()
  val colors get() = icon.colors

  override fun getIcon(assetToRender: DesignAsset,
                       width: Int,
                       height: Int,
                       refreshCallback: () -> Unit,
                       shouldBeRendered: () -> Boolean): Icon {
    icon.colors = resourceResolver.resolveMultipleColors(resourceResolver.resolveValue(assetToRender), project).toSet()
    icon.width = width
    icon.height = height
    return icon
  }
}

private class ColorIcon : Icon {
  var width: Int = 0
  var height: Int = 0
  var colors: Set<Color> = emptySet()

  override fun getIconHeight(): Int = height

  override fun getIconWidth(): Int = width

  override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
    if (colors.isEmpty()) return

    val splitSize = iconWidth / colors.size
    colors.forEachIndexed { i, color ->
      g.color = color
      g.fillRect(i * splitSize, 0, splitSize, iconHeight)
    }
  }
}