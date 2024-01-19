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
package com.android.tools.idea.layoutinspector.properties

import com.android.SdkConstants.ATTR_ID
import com.android.SdkConstants.ATTR_NAME
import com.android.tools.idea.layoutinspector.model.SelectedViewModel
import com.android.tools.property.panel.api.InspectorBuilder
import com.android.tools.property.panel.api.InspectorPanel
import com.android.tools.property.panel.api.PropertiesTable
import com.android.tools.property.panel.api.SelectedComponentPanel

object SelectedViewBuilder : InspectorBuilder<InspectorPropertyItem> {

  override fun attachToInspector(
    inspector: InspectorPanel,
    properties: PropertiesTable<InspectorPropertyItem>,
  ) {
    val name = properties.getOrNull(NAMESPACE_INTERNAL, ATTR_NAME) ?: return
    val id = properties.getOrNull(NAMESPACE_INTERNAL, ATTR_ID)
    val panel = SelectedComponentPanel(SelectedViewModel(name, id))
    inspector.addComponent(panel, null)
  }
}
