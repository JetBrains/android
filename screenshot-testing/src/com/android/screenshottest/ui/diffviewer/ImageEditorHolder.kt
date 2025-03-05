/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.screenshottest.ui.diffviewer

import com.intellij.diff.tools.holders.EditorHolder
import com.intellij.ui.components.panels.Wrapper
import org.intellij.images.editor.impl.ImageEditorManagerImpl
import java.awt.Dimension
import java.awt.image.BufferedImage
import javax.swing.JComponent

/**
 * Custom diff request for ThreesideImageDiffViewer
 */
class ImageEditorHolder(bufferedImage: BufferedImage) : EditorHolder() {
  private val imageEditorUi = MyPanel(bufferedImage)

  override fun dispose() = Unit

  // return image viewer JComponent
  override fun getComponent(): JComponent = imageEditorUi

  override fun getPreferredFocusedComponent(): JComponent = imageEditorUi

  private class MyPanel(bufferedImage: BufferedImage) : Wrapper() {
    init {
      // Set minimum width and height
      this.minimumSize = Dimension(MINIMUM_WIDTH, MINIMUM_HEIGHT)
      val imageEditorUI = ImageEditorManagerImpl.createImageEditorUI(bufferedImage)
      this.add(imageEditorUI)
    }
  }
}

private const val MINIMUM_WIDTH = 800
private const val MINIMUM_HEIGHT = 800
