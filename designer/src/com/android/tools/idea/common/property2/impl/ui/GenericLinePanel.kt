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

import com.android.tools.adtui.model.stdui.ValueChangedListener
import com.android.tools.idea.common.property2.impl.model.GenericInspectorLineModel
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * An inspector line that wraps a custom component.
 */
class GenericLinePanel(component: JComponent, private val model: GenericInspectorLineModel): JPanel(BorderLayout()) {
  init {
    add(component)
    model.addValueChangedListener(ValueChangedListener { valueChanged() })
  }

  private fun valueChanged() {
    isVisible = model.visible
  }
}
