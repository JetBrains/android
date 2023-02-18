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
package com.android.tools.idea.appinspection.inspectors.network.view

import com.android.tools.adtui.stdui.ContentType
import com.android.tools.inspectors.common.api.stacktrace.StackTraceModel
import com.android.tools.inspectors.common.ui.dataviewer.DataViewer
import com.android.tools.inspectors.common.ui.dataviewer.ImageDataViewer
import com.android.tools.inspectors.common.ui.stacktrace.StackTraceGroup
import com.android.tools.inspectors.common.ui.stacktrace.StackTraceView
import java.awt.image.BufferedImage
import javax.swing.ImageIcon
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class FakeUiComponentsProvider : UiComponentsProvider {
  override fun createDataViewer(
    bytes: ByteArray,
    contentType: ContentType,
    styleHint: DataViewer.Style,
    formatted: Boolean
  ): DataViewer {
    return if (contentType.isSupportedImageType) {
      object : ImageDataViewer {
        private val SAMPLE_IMAGE = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
        private val SAMPLE_COMPONENT: JComponent = JLabel(ImageIcon(SAMPLE_IMAGE))
        override fun getImage(): BufferedImage {
          return SAMPLE_IMAGE
        }
        override fun getComponent(): JComponent {
          return SAMPLE_COMPONENT
        }
      }
    } else {
      object : DataViewer {
        private val SAMPLE_COMPONENT: JComponent = JPanel()
        override fun getComponent(): JComponent {
          return SAMPLE_COMPONENT
        }
        override fun getStyle(): DataViewer.Style {
          return DataViewer.Style.RAW
        }
      }
    }
  }

  override fun createStackGroup() = StackTraceGroupStub()
}

class StackTraceGroupStub : StackTraceGroup {
  override fun createStackView(model: StackTraceModel): StackTraceView {
    return StackTraceViewStub(model)
  }
}

class StackTraceViewStub(private val model: StackTraceModel) : StackTraceView {
  private val component = JPanel()
  override fun getModel(): StackTraceModel {
    return model
  }

  override fun getComponent(): JComponent {
    return component
  }
}
