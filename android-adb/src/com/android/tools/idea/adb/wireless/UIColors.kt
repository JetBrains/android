/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.adb.wireless

import com.intellij.openapi.ui.OnePixelDivider
import com.intellij.util.ui.NamedColorUtil
import com.intellij.util.ui.UIUtil
import java.awt.Color
import javax.swing.UIManager

/**
 * List of colors used for various UI parts of the "Wi-Fi pairing" window
 */
object UIColors {
  /**
   * The background of a QR code is always white, since a QR code should
   * always be displayed with black dots on white background to be "scanner friendly".
   */
  @JvmField
  val QR_CODE_BACKGROUND: Color = Color.WHITE

  /**
   * The foreground (i.e. "dots") of a QR code are always black, since a QR code should
   * always be displayed with black dots on white background to be "scanner friendly".
   */
  @JvmField
  val QR_CODE_FOREGROUND: Color = Color.BLACK

  /**
   * Color for various "one pixel" line dividers
   */
  @JvmField
  val ONE_PIXEL_DIVIDER: Color = UIManager.getColor("DialogWrapper.southPanelDivider") ?: OnePixelDivider.BACKGROUND

  /**
   * The background color for the "pairing" contents (QR code and pairing code panels)
   */
  @JvmField
  val PAIRING_CONTENT_BACKGROUND: Color = UIUtil.getTreeBackground()

  @JvmField
  val HEADER_LABEL: Color = UIUtil.getLabelForeground()

  @JvmField
  val PAIRING_STATUS_LABEL: Color = UIUtil.getLabelForeground()

  @JvmField
  val PAIRING_HINT_LABEL: Color = UIUtil.getLabelForeground()

  @JvmField
  val ERROR_TEXT: Color = NamedColorUtil.getInactiveTextColor()

  @JvmField
  val LIGHT_LABEL: Color = NamedColorUtil.getInactiveTextColor()
}
