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
package com.android.tools.inspectors.common.ui.dataviewer

import com.android.tools.adtui.stdui.ResizableImage
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.concurrency.AndroidDispatchers.workerThread
import com.intellij.openapi.Disposable
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.components.BorderLayoutPanel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.BorderLayout.CENTER
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import javax.swing.JLabel
import javax.swing.SwingConstants

/**
 * An [DataViewer] that displays an image loaded from a byte array
 *
 * The data is parsed in a background coroutine to avoid UI lag. Once the image is loaded, the component will refresh itself.
 */
class IntellijImageDataViewer(imageBytes: ByteArray, parentDisposable: Disposable) : DataViewer {
  private val panel = BorderLayoutPanel()

  init {
    AndroidCoroutineScope(parentDisposable, workerThread).launch {
      val image = ImageIO.read(ByteArrayInputStream(imageBytes))
      withContext(uiThread) {
        val contents = if (image != null) {
          ResizableImage(image).apply {
            toolTipText = "Dimension: ${image.width} x ${image.height}"
          }
        } else {
          JLabel("No preview available", SwingConstants.CENTER).apply {
            setFont(JBFont.label().asPlain())
          }
        }
        panel.add(contents, CENTER)
        panel.revalidate()
      }
    }
  }

  override fun getComponent() = panel

  override fun getStyle() = DataViewer.Style.RAW
}