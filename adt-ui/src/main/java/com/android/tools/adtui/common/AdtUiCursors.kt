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
package com.android.tools.adtui.common

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.ui.icons.CachedImageIcon
import com.intellij.util.ui.ImageUtil
import com.intellij.util.ui.UIUtil
import icons.StudioIcons
import java.awt.Cursor
import java.awt.GraphicsEnvironment
import java.awt.Point
import java.awt.Toolkit
import java.awt.image.BufferedImage
import javax.swing.Icon
import javax.swing.JPanel

/**
 * The types of custom cursor in Studio. This is used by [AdtUiCursorsProvider.getCursor] to get the proper cursors.
 */
enum class AdtUiCursorType(val icon: Icon, internal val hotSpotMapFun: (BufferedImage) -> Point = { Point(it.width / 2, it.height / 2) }) {
  GRAB(StudioIcons.Cursors.GRAB),
  MOVE(StudioIcons.Cursors.MOVE),
  GRABBING(StudioIcons.Cursors.GRABBING),
  // Some of resizing cursors may use same icons. Do not merge them since they are for different purposes.
  SW_RESIZE(StudioIcons.Cursors.NESW_RESIZE),
  SE_RESIZE(StudioIcons.Cursors.NWSE_RESIZE),
  NW_RESIZE(StudioIcons.Cursors.NWSE_RESIZE),
  NE_RESIZE(StudioIcons.Cursors.NESW_RESIZE),
  N_RESIZE(StudioIcons.Cursors.NS_RESIZE),
  S_RESIZE(StudioIcons.Cursors.NS_RESIZE),
  W_RESIZE(StudioIcons.Cursors.EW_RESIZE),
  E_RESIZE(StudioIcons.Cursors.EW_RESIZE),
}

/**
 * The service provides the custom cursors of Studio.
 */
interface AdtUiCursorsProvider {
  /**
   * Return the custom [Cursor] of Android Studio for the given [AdtUiCursorType].
   */
  fun getCursor(type: AdtUiCursorType): Cursor

  companion object {
    @JvmStatic
    fun getInstance(): AdtUiCursorsProvider {
      return ApplicationManager.getApplication().getService(AdtUiCursorsProvider::class.java)
    }
  }
}

private class AdtUiCursorProviderImpl: AdtUiCursorsProvider {
  private val cursorMap = mutableMapOf<AdtUiCursorType, Cursor>()

  override fun getCursor(type: AdtUiCursorType) = cursorMap.getOrPut(type) { makeCursor(type) }

  private fun makeCursor(type: AdtUiCursorType): Cursor {
    if (GraphicsEnvironment.isHeadless()) {
      Logger.getInstance(AdtUiCursorProviderImpl::class.java)
        .warn("Cannot create a custom cursor in headless environment, use default cursor instead")
      return Cursor.getDefaultCursor()
    }
    val name = "${type.javaClass.name}.${type.name}"
    val icon = type.icon
    val hotSpotMapFunc = type.hotSpotMapFun

    // Icons are loaded at 2x for retina displays. For cursors we don't want to use this double sized icon so we scale it down.
    val scaleFactor = if (UIUtil.isRetina()) 0.5f else 1.0f
    val scaledIcon = (icon as CachedImageIcon).scale(scaleFactor)
    val image = ImageUtil.createImage(scaledIcon.iconWidth, scaledIcon.iconHeight, BufferedImage.TYPE_INT_ARGB)
    scaledIcon.paintIcon(JPanel(), image.graphics, 0, 0)
    // We offset the icon center from the upper left to the center for a more natural placement with existing cursors.
    return Toolkit.getDefaultToolkit().createCustomCursor(image, hotSpotMapFunc(image), name)
  }
}
