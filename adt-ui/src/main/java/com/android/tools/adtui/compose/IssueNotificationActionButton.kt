/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.adtui.compose

import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText
import com.intellij.ui.Gray
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Color
import java.awt.Insets
import javax.swing.JToolTip
import javax.swing.SwingConstants
import javax.swing.border.Border

class IssueNotificationActionButton(val action: IssueNotificationAction, presentation: Presentation, place: String) :
  ActionButtonWithText(action, presentation, place, JBUI.size(0, 20)) {

  override fun iconTextSpace() : Int {
    return if (icon == null || icon.iconWidth <= 0) 0 else 2
  }

  private val actionPresentation: ComposeStatus.Presentation?
    get() = myPresentation.getClientProperty(ComposeStatus.PRESENTATION)
  val textAlignment: Int
    get() = myPresentation.getClientProperty(ComposeStatus.TEXT_ALIGNMENT) ?: SwingConstants.LEADING

  private val font = UIUtil.getLabelFont(UIUtil.FontSize.SMALL)

  private val textColor = JBColor(Gray._110, Gray._187)

  override fun isBackgroundSet(): Boolean =
    actionPresentation != null || super.isBackgroundSet()

  override fun getBackground(): Color? =
    actionPresentation?.color ?: super.getBackground()

  override fun getFont() = font

  override fun getForeground() = textColor

  override fun getBorder(): Border =
    if (popState == POPPED)
      chipBorder(JBUI.CurrentTheme.ActionButton.hoverBorder())
    else
      actionPresentation?.border ?: JBUI.Borders.empty()

  override fun getMargins(): Insets = action.margins()

  override fun getInsets(): Insets = action.insets()

  override fun getInsets(insets: Insets?): Insets {
    val i = getInsets()
    if (insets != null) {
      insets.set(i.top, i.left, i.bottom, i.right)
      return insets
    }
    return i
  }

  override fun addNotify() {
    super.addNotify()
    addMouseListener(action.mouseListener)
    setHorizontalTextPosition(textAlignment)
  }

  override fun removeNotify() {
    removeMouseListener(action.mouseListener)
    super.removeNotify()
  }

  override fun createToolTip(): JToolTip? = null

  // Do not display the regular tooltip
  override fun updateToolTipText() {}

  init {
    setHorizontalTextPosition(textAlignment)
  }
}