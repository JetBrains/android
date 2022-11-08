/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.adddevicedialog

import com.android.tools.idea.grouplayout.GroupLayout.Companion.groupLayout
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBTextField
import javax.swing.GroupLayout

internal class NumberOfBytesField internal constructor() : JBPanel<NumberOfBytesField>(null) {
  init {
    val textField = JBTextField()
    val comboBox = ComboBox<Any>()

    layout = groupLayout(this) {
      horizontalGroup {
        sequentialGroup {
          component(textField)
          component(comboBox)
        }
      }

      verticalGroup {
        parallelGroup {
          component(textField, max = GroupLayout.PREFERRED_SIZE)
          component(comboBox, max = GroupLayout.PREFERRED_SIZE)
        }
      }
    }
  }
}
