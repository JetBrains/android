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
package com.android.tools.idea.common.property2.impl.ui

import java.awt.BorderLayout
import java.awt.Color
import javax.swing.JPanel

open class CellPanel: JPanel(BorderLayout()) {

  override fun setBackground(color: Color?) {
    super.setBackground(color)
    // Note: Do not use Container.getComponents since it allocates an array...
    for (index in 0 until componentCount) {
      getComponent(index).background = color
    }
  }

  override fun setForeground(color: Color?) {
    super.setForeground(color)
    // Note: Do not use Container.getComponents since it allocates an array...
    for (index in 0 until componentCount) {
      getComponent(index).foreground = color
    }
  }
}
