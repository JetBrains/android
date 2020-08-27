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
package com.android.tools.idea.compose.preview.pickers

import com.android.tools.adtui.LightCalloutPopup
import com.android.tools.idea.compose.preview.pickers.properties.PsiPropertyItem
import com.android.tools.idea.compose.preview.pickers.properties.PsiPropertyModel
import com.android.tools.idea.compose.preview.pickers.properties.PsiPropertyView
import com.android.tools.property.panel.api.PropertiesPanel
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.util.Disposer
import java.awt.Component
import java.awt.Point

object PsiPickerManager {
  /**
   * Shows a picker for editing a [PsiPropertyModel]s. The user can modify the model using this dialog.
   */
  fun showForEvent(e: AnActionEvent, model: PsiPropertyModel) {
    val disposable = Disposer.newDisposable()
    val popup = createPopup(disposable)
    val propertiesPanel = createPropertiesPanel(disposable, model)

    val owner = e.inputEvent.component
    val location = owner.locationOnScreen
    // Center the picker in the middle of the parent width, usually a button. The popup will show up at the bottom of the owner.
    location.translate(owner.width / 2, owner.height)

    popup.show(propertiesPanel.component, null, location)
  }

  /**
   * Shows a picker for editing a [PsiPropertyModel]s. The user can modify the model using this dialog.
   */
  fun show(location: Point, model: PsiPropertyModel) {
    val disposable = Disposer.newDisposable()
    val popup = createPopup(disposable)
    val propertiesPanel = createPropertiesPanel(disposable, model)

    popup.show(propertiesPanel.component, null, location)
  }
}

private fun createPopup(disposable: Disposable) = LightCalloutPopup(closedCallback = { Disposer.dispose(disposable) },
                                                                    cancelCallBack = { Disposer.dispose(disposable) })

private fun createPropertiesPanel(disposable: Disposable, model: PsiPropertyModel) = PropertiesPanel<PsiPropertyItem>(disposable).also {
  it.addView(PsiPropertyView(model))
}