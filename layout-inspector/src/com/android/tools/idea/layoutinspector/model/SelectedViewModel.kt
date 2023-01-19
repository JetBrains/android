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
package com.android.tools.idea.layoutinspector.model

import com.android.tools.idea.layoutinspector.properties.InspectorPropertyItem
import com.android.tools.property.panel.api.SelectedComponentModel
import javax.swing.Icon

private const val UNNAMED_COMPONENT = "<unnamed>"

/**
 * Model for supplying data to a SelectedComponentPanel.
 *
 * For displaying which component is being edited.
 * Intended for being shown at the top of the properties panel.
 */
class SelectedViewModel(name: InspectorPropertyItem, id: InspectorPropertyItem?) : SelectedComponentModel {
  private val unqualifiedName = name.value?.substringAfterLast('.') ?: ""

  override val icon: Icon = IconProvider.getIconForView(unqualifiedName, id?.value?.isEmpty() == true)

  override val id: String = id?.value ?: UNNAMED_COMPONENT

  override val description: String = unqualifiedName
}
