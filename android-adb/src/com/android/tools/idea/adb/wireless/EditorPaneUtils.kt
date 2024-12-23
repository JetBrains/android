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
@file:JvmName("EditorPaneUtils")

package com.android.tools.idea.adb.wireless

import com.android.utils.HtmlBuilder
import com.intellij.ui.JBColor
import com.intellij.ui.border.CustomLineBorder
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.SwingHelper
import com.intellij.util.ui.UIUtil
import java.awt.Color
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.border.Border
import javax.swing.border.CompoundBorder

fun createHtmlEditorPane(): JEditorPane {
  val viewer: JEditorPane = SwingHelper.createHtmlViewer(true, null, JBColor.WHITE, JBColor.BLACK)
  viewer.isOpaque = false
  viewer.isFocusable = false
  UIUtil.doNotScrollToCaret(viewer)
  return viewer
}

fun JEditorPane.setHtml(htmlBuilder: HtmlBuilder, textColor: Color?) {
  SwingHelper.setHtml(this, htmlBuilder.html, textColor)
}

fun setTitlePanelBorder(panel: JComponent) {
  setPanelBorder(panel, 0, 1)
}

fun setBottomPanelBorder(panel: JComponent) {
  setPanelBorder(panel, 1, 0)
}

private fun setPanelBorder(panel: JComponent, topPixels: Int, bottomPixels: Int) {
  val line: Border = CustomLineBorder(UIColors.ONE_PIXEL_DIVIDER, topPixels, 0, bottomPixels, 0)
  val c: Border = CompoundBorder(line, JBUI.Borders.empty(5, 10))
  panel.border = c
  panel.minimumSize = JBDimension(0, 30)
}
