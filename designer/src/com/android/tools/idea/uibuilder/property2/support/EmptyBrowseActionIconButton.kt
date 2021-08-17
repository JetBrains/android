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
package com.android.tools.idea.uibuilder.property.support

import com.android.tools.property.panel.api.ActionIconButton
import com.intellij.openapi.actionSystem.AnAction
import icons.StudioIcons
import java.awt.image.BufferedImage
import javax.swing.Icon
import javax.swing.ImageIcon

/**
 * A lazy empty icon generator at the same size of the resource picker icon.
 */
object EmptyBrowseActionIconButton : ActionIconButton {
  private var emptyIcon: Icon? = null

  override val actionButtonFocusable: Boolean
    get() = false

  override val actionIcon: Icon
    get() {
      val boundIcon = StudioIcons.Common.PROPERTY_BOUND
      var icon = emptyIcon

      // Generate the empty icon lazily and update the icon if the size of boundIcon has changed.
      // The size of boundIcon can change with a LaF change e.g. setting the system font to a bigger/smaller size.
      if (icon == null || icon.iconHeight != boundIcon.iconHeight || icon.iconWidth != boundIcon.iconWidth) {
        icon = ImageIcon(BufferedImage(boundIcon.iconWidth, boundIcon.iconHeight, BufferedImage.TYPE_INT_ARGB))
        emptyIcon = icon
      }
      return icon
    }

  override val action: AnAction?
    get() = null
}
