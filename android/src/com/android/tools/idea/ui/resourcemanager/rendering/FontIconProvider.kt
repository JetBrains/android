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

import com.android.ide.common.fonts.FontFamily
import com.android.tools.idea.fonts.DownloadableFontCacheService
import com.android.tools.idea.fonts.ProjectFonts
import com.android.tools.idea.res.ResourceRepositoryManager
import com.android.tools.idea.ui.resourcemanager.model.Asset
import com.intellij.util.ui.JBUI
import org.jetbrains.android.facet.AndroidFacet
import java.awt.Component
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.Rectangle2D
import javax.swing.Icon

/**
 * Create a reusable [Icon] that is updated each time [getIcon] is called.
 * It paints a sample of all font styles defined in the resource's [FontFamily], It will try to fit the samples in the given dimensions.
 * Usually, for most Android Projects, a [FontFamily] will have up to two styles.
 */
class FontIconProvider(
  facet: AndroidFacet
) : AssetIconProvider {

  private val fontIcon = FontFamilyIcon()

  private val projectFonts = ProjectFonts(ResourceRepositoryManager.getInstance(facet))

  override val supportsTransparency: Boolean = false

  override fun getIcon(
    assetToRender: Asset,
    width: Int,
    height: Int,
    component: Component,
    refreshCallback: () -> Unit,
    shouldBeRendered: () -> Boolean
  ): Icon {
    val resource = assetToRender.resourceItem
    val fontFamily = projectFonts.getFont(resource.referenceToSelf.resourceUrl.toString())

    fontIcon.width = width
    fontIcon.height = height
    fontIcon.setFontFamily(fontFamily)
    return fontIcon
  }
}

/**
 * Creates an Icon that draws samples of each style within a [FontFamily].
 */
private class FontFamilyIcon: Icon {
  var width: Int = 0
  var height: Int = 0

  private var fonts: List<FontIconData> = emptyList()
  private val fontService = DownloadableFontCacheService.getInstance()

  fun setFontFamily(fontFamily: FontFamily) {
    fonts = fontFamily.fonts.mapNotNull{ fontDetail ->
      fontService.loadDetailFont(fontDetail)?.let { font ->
        FontIconData(fontDetail.styleName, font)
      }
    }
  }

  override fun getIconWidth(): Int = width

  override fun getIconHeight(): Int = height

  override fun paintIcon(c: Component?, g: Graphics?, x: Int, y: Int) {
    g as Graphics2D
    var maxWidth = 0
    var maxHeight = 0
    var fontSize = 10f // Need an initial size value

    // Save current configuration.
    val originalFont = g.font
    val antiAliasValue = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING)
    val renderValue = g.getRenderingHint(RenderingHints.KEY_RENDERING)

    // MEASURE - horizontal
    fonts.forEach { fontToRender ->
      val fontBounds = fontToRender.getFontBounds(g, fontSize)
      maxWidth = maxOf(maxWidth, fontBounds.width.toInt())
    }
    fontSize *= width.toFloat() / (maxWidth + JBUI.scale(4)).toFloat()

    // MEASURE - vertical
    fonts.forEach { fontToRender ->
      val fontBounds = fontToRender.getFontBounds(g, fontSize)
      maxHeight += fontBounds.height.toInt()
    }
    fontSize *= minOf((height.toFloat() / (maxHeight + JBUI.scale(4)).toFloat()), 1f)

    // PAINT
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
    var yOffset = 0
    fonts.forEach { fontToRender ->
      val textBounds = fontToRender.getFontBounds(g, fontSize)
      yOffset += textBounds.height.toInt()
      g.font = fontToRender.font.deriveFont(fontSize)
      g.drawString(fontToRender.name, x + ((width - textBounds.width) / 2).toInt(), yOffset)
    }

    // Restore configuration.
    g.font = originalFont
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, antiAliasValue)
    g.setRenderingHint(RenderingHints.KEY_RENDERING, renderValue)
  }
}

private data class FontIconData(
  val name: String,
  val font: Font
)

private fun FontIconData.getFontBounds(g: Graphics, fontSize: Float): Rectangle2D {
  return g.getFontMetrics(font.deriveFont(fontSize)).getStringBounds(name, g)
}