/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.naveditor.property.inspector

import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.naveditor.model.isNavigation
import com.android.tools.idea.naveditor.model.supportsActions
import com.android.tools.idea.naveditor.dialogs.AddActionDialog
import com.android.tools.idea.naveditor.dialogs.showAndUpdateFromDialog
import com.android.tools.idea.naveditor.property.ui.ActionCellRenderer
import com.android.tools.idea.naveditor.scene.decorator.HIGHLIGHTED_CLIENT_PROPERTY
import com.android.tools.idea.uibuilder.property.NlPropertiesModel
import com.google.wireless.android.sdk.stats.NavEditorEvent
import com.intellij.ui.components.JBList
import org.jetbrains.android.dom.navigation.NavigationSchema.TAG_ACTION

class ActionListInspectorBuilder(private val model: NlPropertiesModel) : ComponentListInspectorBuilder(TAG_ACTION, ActionCellRenderer()) {
  override fun title(component: NlComponent) =
    if (component.isNavigation) {
      "Global Actions"
    }
    else {
      "Actions"
    }

  override fun onAdd(parent: NlComponent) {
    invokeDialog(null, parent)
  }

  override fun onEdit(component: NlComponent) {
    component.parent?.let { invokeDialog(component, it) }
  }

  override fun onSelectionChanged(list: JBList<NlComponent>) {
    var removed = false

    for (i in 0 until list.model.size) {
      val element = list.model.getElementAt(i)
      element.removeClientProperty(HIGHLIGHTED_CLIENT_PROPERTY)?.let { removed = true }
    }

    for (element in list.selectedValuesList) {
      element.putClientProperty(HIGHLIGHTED_CLIENT_PROPERTY, true)
    }

    if (removed || list.selectedValuesList.isNotEmpty()) {
      model.surface?.scene?.let {
        it.needsRebuildList()
        it.repaint()
      }
    }
  }

  override fun isApplicable(component: NlComponent) = component.supportsActions

  private fun invokeDialog(component: NlComponent?, parent: NlComponent) {
    val dialog = AddActionDialog(AddActionDialog.Defaults.NORMAL, component, parent, NavEditorEvent.Source.PROPERTY_INSPECTOR)
    showAndUpdateFromDialog(dialog, parent.model, component != null)
  }
}
