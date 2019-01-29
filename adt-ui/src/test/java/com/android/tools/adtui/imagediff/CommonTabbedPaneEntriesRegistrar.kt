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
package com.android.tools.adtui.imagediff

import com.android.tools.adtui.imagediff.ImageDiffUtil.DEFAULT_IMAGE_DIFF_THRESHOLD_PERCENT
import com.android.tools.adtui.stdui.CommonTabbedPane
import java.awt.BorderLayout
import java.awt.image.BufferedImage
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants

// Temporary make the similarity threshold larger for 2 images that have been failing a lot on Mac post submit
// b/116180969
private const val TEMP_IMAGE_DIFF_THRESHOLD_PERCENT_FOR_B116180969 = 0.8

private class CommonTabbedPaneEntriesRegistrar : ImageDiffEntriesRegistrar() {
  init {
    register(CommonTabbedPaneEntry("common_tabbed_pane_top.png", SwingConstants.TOP, DEFAULT_IMAGE_DIFF_THRESHOLD_PERCENT))
    register(CommonTabbedPaneEntry("common_tabbed_pane_left.png", SwingConstants.LEFT, TEMP_IMAGE_DIFF_THRESHOLD_PERCENT_FOR_B116180969))
    register(CommonTabbedPaneEntry("common_tabbed_pane_bottom.png", SwingConstants.BOTTOM, DEFAULT_IMAGE_DIFF_THRESHOLD_PERCENT))
    register(CommonTabbedPaneEntry("common_tabbed_pane_right.png", SwingConstants.RIGHT, TEMP_IMAGE_DIFF_THRESHOLD_PERCENT_FOR_B116180969))
  }

  private class CommonTabbedPaneEntry(fileName: String, tabPlacement: Int, threshold: Double) : ImageDiffEntry(fileName, threshold) {
    private val panel = JPanel(BorderLayout())

    init {
      val tabbedPane = CommonTabbedPane()
      tabbedPane.font = ImageDiffUtil.getDefaultFont()
      tabbedPane.tabPlacement = tabPlacement
      tabbedPane.addTab("tab1", JLabel())
      tabbedPane.addTab("tab2", JLabel())
      tabbedPane.tabPlacement = tabPlacement
      panel.add(tabbedPane, BorderLayout.CENTER)
      panel.setSize(200, 200)
    }

    override fun generateComponentImage(): BufferedImage {
      return ImageDiffUtil.getImageFromComponent(panel)
    }
  }
}
