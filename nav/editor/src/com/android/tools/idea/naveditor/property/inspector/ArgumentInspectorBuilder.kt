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
import com.android.tools.idea.naveditor.analytics.NavUsageTracker
import com.android.tools.idea.naveditor.model.isAction
import com.android.tools.idea.naveditor.model.isNavigation
import com.android.tools.idea.naveditor.model.supportsArguments
import com.android.tools.idea.naveditor.dialogs.AddArgumentDialog
import com.android.tools.idea.naveditor.property.ui.ArgumentCellRenderer
import com.google.wireless.android.sdk.stats.NavEditorEvent
import com.google.wireless.android.sdk.stats.NavEditorEvent.NavEditorEventType.CREATE_ARGUMENT
import com.google.wireless.android.sdk.stats.NavEditorEvent.NavEditorEventType.EDIT_ARGUMENT
import org.jetbrains.android.dom.navigation.NavigationSchema.TAG_ARGUMENT

class ArgumentInspectorBuilder
  : ComponentListInspectorBuilder(TAG_ARGUMENT, ArgumentCellRenderer()) {
  override fun title(component: NlComponent): String = "Arguments"
  override fun addActionText(component: NlComponent) = "Add argument"
  override fun deleteActionText(component: NlComponent) = "Remove argument"
  override fun onAdd(parent: NlComponent) {
    invokeDialog(null, parent)
  }

  override fun onEdit(component: NlComponent) {
    component.parent?.let { invokeDialog(component, it) }
  }

  override fun isApplicable(component: NlComponent) = component.supportsArguments && !component.isAction

  private fun invokeDialog(component: NlComponent?, parent: NlComponent) {
    val argumentDialog = AddArgumentDialog(component, parent)

    if (argumentDialog.showAndGet()) {
      argumentDialog.save()
      NavUsageTracker.getInstance(parent.model).createEvent(if (component == null) CREATE_ARGUMENT else EDIT_ARGUMENT)
        .withSource(NavEditorEvent.Source.PROPERTY_INSPECTOR).log()
    }
  }
}

