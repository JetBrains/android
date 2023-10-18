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
package com.android.tools.idea.flags

import com.intellij.ide.BrowserUtil
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.ui.StatusText
import java.awt.Dimension
import java.awt.Graphics
import javax.swing.JPanel

internal class DeviceStreamingSignUpPanel : JPanel() {
  private val text: StatusText = object : StatusText() {
    override fun isStatusVisible() = true

    init {
      appendText(
        "Enable only if you are enrolled in the Device Streaming Alpha program. ",
        SimpleTextAttributes.GRAY_ATTRIBUTES
      )
      appendText(
        "Click here", SimpleTextAttributes.LINK_ATTRIBUTES
      ) { BrowserUtil.browse("https://services.google.com/fb/forms/androiddevicestreaming") }
      appendText(" to sign up.", SimpleTextAttributes.GRAY_ATTRIBUTES)
      attachTo(this@DeviceStreamingSignUpPanel)
    }
  }

  override fun getPreferredSize(): Dimension = text.preferredSize

  override fun paint(g: Graphics) {
    super.paint(g)
    text.paint(this, g)
  }
}
