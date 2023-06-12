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
package com.android.tools.idea.streaming

import com.android.tools.adtui.common.primaryPanelBackground
import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.LoadingDecorator
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.util.ui.AsyncProcessIcon
import com.intellij.util.ui.JBEmptyBorder
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil.findComponentOfType
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.Graphics
import java.awt.GridBagLayout
import java.awt.LayoutManager
import javax.swing.JLabel
import javax.swing.JPanel

/**
 * Loading panel with a rounded rectangle around the icon and the text.
 */
class StreamingLoadingPanel(
  parentDisposable: Disposable
) : JBLoadingPanel(BorderLayout(), { panel -> EmulatorLoadingDecorator(panel, parentDisposable, startDelayMs = 200) }) {

  private var roundedPanel: RoundedPanel? = null

  override fun startLoading() {
    findRoundedPanel()?.isVisible = true
    super.startLoading()
  }

  /**
   * Similar to [stopLoading] but without a fade-out animation.
   */
  fun stopLoadingInstantly() {
    stopLoading()
    findRoundedPanel()?.isVisible = false
  }

  private fun findRoundedPanel(): RoundedPanel? {
    if (roundedPanel == null) {
      // We may not be able to find the RoundedPanel yet because the loading layer in the loading
      // decorator is created with a delay.
      roundedPanel = findComponentOfType(this, RoundedPanel::class.java)
    }
    return roundedPanel
  }

  private class EmulatorLoadingDecorator(
    panel: JPanel,
    parentDisposable: Disposable,
    startDelayMs: Int = 0
  ) : LoadingDecorator(panel, parentDisposable, startDelayMs, false, AsyncProcessIcon.Big("Loading")) {

    override fun customizeLoadingLayer(parent: JPanel, text: JLabel, icon: AsyncProcessIcon): NonOpaquePanel {
      val roundedPanel = RoundedPanel(FlowLayout(FlowLayout.CENTER, JLabel().iconTextGap * 3, 0), 8).apply {
        border = JBEmptyBorder(8)
        background = primaryPanelBackground
        add(icon)
        add(text)
      }

      val containerPanel = NonOpaquePanel(GridBagLayout()).apply {
        border = JBUI.Borders.empty()
        add(roundedPanel)
      }

      parent.layout = BorderLayout()
      parent.add(containerPanel)

      return containerPanel
    }
  }

  private class RoundedPanel(layoutManager: LayoutManager, private val radius: Int) : JPanel(layoutManager) {
    init {
      isOpaque = false
    }

    override fun paintComponent(g: Graphics) {
      g.color = background
      g.fillRoundRect(0, 0, width, height, 2 * radius, 2 * radius)
    }
  }
}
