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

import com.android.tools.property.panel.api.SelectedComponentModel
import icons.StudioIcons.LayoutEditor.Palette.UNKNOWN_VIEW
import javax.swing.Icon

private const val UNNAMED_COMPONENT = "<unnamed>"

/**
 * Model for supplying data to a SelectedComponentPanel.
 *
 * For displaying which component is being edited. Intended for being shown at the top of the
 * properties panel.
 */
class SelectedViewModel(selectedView: ViewNode?) : SelectedComponentModel {

  override val icon: Icon = selectedView?.let { IconProvider.getIconForView(it) } ?: UNKNOWN_VIEW

  override val id: String =
    selectedView?.viewId?.let { it.getRelativeResourceUrl(it.namespace).toString() }
      ?: if (selectedView is ComposeViewNode) "" else UNNAMED_COMPONENT

  override val description: String = selectedView?.unqualifiedName.orEmpty()
}
