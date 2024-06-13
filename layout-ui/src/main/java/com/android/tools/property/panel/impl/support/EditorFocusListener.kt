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
package com.android.tools.property.panel.impl.support

import com.android.tools.property.panel.impl.model.BasePropertyEditorModel
import java.awt.event.FocusEvent
import java.awt.event.FocusListener
import javax.swing.JComponent

/**
 * [FocusListener] that can be used in controls using models derived from [BasePropertyEditorModel].
 */
class EditorFocusListener(
  private val component: JComponent,
  private val model: BasePropertyEditorModel,
) : FocusListener {

  override fun focusGained(event: FocusEvent) {
    model.focusGained()
    (component.parent as? JComponent)?.scrollRectToVisible(component.bounds)
  }

  override fun focusLost(event: FocusEvent) {
    model.focusLost()
  }
}
