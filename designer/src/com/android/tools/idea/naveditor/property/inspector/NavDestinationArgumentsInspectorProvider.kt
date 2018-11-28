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
package com.android.tools.idea.naveditor.property.inspector

import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.naveditor.analytics.NavUsageTracker
import com.android.tools.idea.naveditor.property.NavDestinationArgumentsProperty
import com.android.tools.idea.naveditor.surface.NavDesignSurface
import com.google.wireless.android.sdk.stats.NavEditorEvent
import com.google.wireless.android.sdk.stats.NavEditorEvent.NavEditorEventType.CREATE_ARGUMENT
import com.google.wireless.android.sdk.stats.NavEditorEvent.NavEditorEventType.EDIT_ARGUMENT
import icons.StudioIcons
import org.jetbrains.annotations.TestOnly

class NavDestinationArgumentsInspectorProvider(
  @TestOnly private val dialogProvider: (NlComponent?, NlComponent) -> AddArgumentDialog =
    { existing: NlComponent?, parent: NlComponent -> AddArgumentDialog(existing, parent) })
  : NavListInspectorProvider<NavDestinationArgumentsProperty>(
  NavDestinationArgumentsProperty::class.java,
  StudioIcons.NavEditor.Properties.ARGUMENT,
  "Argument"
) {

  override fun doAddItem(existing: NlComponent?, parents: List<NlComponent>, surface: DesignSurface?) {
    assert(parents.size == 1)

    val argumentDialog = dialogProvider(existing, parents[0])

    if (argumentDialog.showAndGet()) {
      argumentDialog.save()
      NavUsageTracker.getInstance(surface).createEvent(if (existing == null) CREATE_ARGUMENT else EDIT_ARGUMENT)
        .withSource(NavEditorEvent.Source.PROPERTY_INSPECTOR).log()
    }
  }

  override fun getTitle(components: List<NlComponent>, surface: NavDesignSurface?) = "Arguments"
}