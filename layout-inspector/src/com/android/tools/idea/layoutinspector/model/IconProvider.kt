/*
 * Copyright (C) 2021 The Android Open Source Project
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

import icons.StudioIcons.Compose.Editor.COMPOSABLE_FUNCTION
import icons.StudioIcons.LayoutEditor.Palette.APP_BAR_LAYOUT
import icons.StudioIcons.LayoutEditor.Palette.BUTTON
import icons.StudioIcons.LayoutEditor.Palette.CARD_VIEW
import icons.StudioIcons.LayoutEditor.Palette.CHECK_BOX
import icons.StudioIcons.LayoutEditor.Palette.COORDINATOR_LAYOUT
import icons.StudioIcons.LayoutEditor.Palette.HORIZONTAL_DIVIDER
import icons.StudioIcons.LayoutEditor.Palette.IMAGE_BUTTON
import icons.StudioIcons.LayoutEditor.Palette.IMAGE_VIEW
import icons.StudioIcons.LayoutEditor.Palette.LINEAR_LAYOUT_HORZ
import icons.StudioIcons.LayoutEditor.Palette.LINEAR_LAYOUT_VERT
import icons.StudioIcons.LayoutEditor.Palette.NESTED_SCROLL_VIEW
import icons.StudioIcons.LayoutEditor.Palette.SPACE
import icons.StudioIcons.LayoutEditor.Palette.TAB_ITEM
import icons.StudioIcons.LayoutEditor.Palette.TEXT_VIEW
import icons.StudioIcons.LayoutEditor.Palette.UNKNOWN_VIEW
import icons.StudioIcons.LayoutEditor.Palette.VIEW
import org.jetbrains.android.dom.AndroidDomElementDescriptorProvider
import javax.swing.Icon

const val ROOT_NAME = "root"

object IconProvider {

  fun getIconForView(qualifiedName: String, isCompose: Boolean): Icon =
    if (isCompose) getIconForComposeViewNode(qualifiedName) else getIconForViewNode(qualifiedName)

  private fun getIconForViewNode(viewName: String): Icon {
    // Remove "AppCompat" and "Material" prefixes from the simple tag name such that we get
    // e.g. the ImageView icon for an AppCompatImageIcon etc.
    val simpleName = viewName.substringAfterLast('.').removePrefix("AppCompat").removePrefix("Material")
    if (simpleName == ROOT_NAME) {
      return UNKNOWN_VIEW
    }
    return AndroidDomElementDescriptorProvider.getIconForViewTag(simpleName) ?: UNKNOWN_VIEW
  }

  private fun getIconForComposeViewNode(nodeName: String): Icon = when (nodeName) {
    "AppBar" -> APP_BAR_LAYOUT
    "BasicText" -> TEXT_VIEW
    "Box" -> VIEW
    "Button" -> BUTTON
    "Card" -> CARD_VIEW
    "Checkbox" -> CHECK_BOX
    "Column" -> LINEAR_LAYOUT_VERT
    "CoreText" -> TEXT_VIEW
    "Divider" -> HORIZONTAL_DIVIDER
    "Icon" -> IMAGE_VIEW
    "IconButton" -> IMAGE_BUTTON
    "Image" -> IMAGE_VIEW
    "Layout"-> VIEW
    "Row" -> LINEAR_LAYOUT_HORZ
    "Scaffold" -> COORDINATOR_LAYOUT
    "ScrollableColumn" -> NESTED_SCROLL_VIEW
    "Surface" -> VIEW
    "Spacer" -> SPACE
    "Tab" -> TAB_ITEM
    "Text" -> TEXT_VIEW
    else -> COMPOSABLE_FUNCTION
  }
}
