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

import com.android.SdkConstants.TAG_DEEP_LINK
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.naveditor.analytics.NavUsageTracker
import com.android.tools.idea.naveditor.model.supportsDeeplinks
import com.android.tools.idea.naveditor.dialogs.AddDeeplinkDialog
import com.android.tools.idea.naveditor.property.ui.DeepLinkCellRenderer
import com.google.wireless.android.sdk.stats.NavEditorEvent
import com.google.wireless.android.sdk.stats.NavEditorEvent.NavEditorEventType.CREATE_DEEP_LINK
import com.google.wireless.android.sdk.stats.NavEditorEvent.NavEditorEventType.EDIT_DEEP_LINK

class DeepLinkInspectorBuilder : ComponentListInspectorBuilder(TAG_DEEP_LINK, DeepLinkCellRenderer()) {
  override fun title(component: NlComponent) = "Deep Links"
  override fun onAdd(parent: NlComponent) {
    invokeDialog(null, parent)
  }

  override fun onEdit(component: NlComponent) {
    component.parent?.let { invokeDialog(component, it) }
  }

  override fun isApplicable(component: NlComponent) = component.supportsDeeplinks

  private fun invokeDialog(component: NlComponent?, parent: NlComponent) {
    val dialog = AddDeeplinkDialog(component, parent)

    if (dialog.showAndGet()) {
      dialog.save()
      NavUsageTracker.getInstance(parent.model).createEvent(if (component == null) CREATE_DEEP_LINK else EDIT_DEEP_LINK)
        .withSource(NavEditorEvent.Source.PROPERTY_INSPECTOR).log()
    }
  }
}

