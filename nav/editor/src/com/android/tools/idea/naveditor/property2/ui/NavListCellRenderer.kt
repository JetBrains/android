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
package com.android.tools.idea.naveditor.property.ui

import com.android.tools.adtui.common.ColoredIconGenerator
import com.android.tools.adtui.common.secondaryPanelBackground
import com.android.tools.idea.common.model.NlComponent
import com.intellij.ui.ColoredListCellRenderer
import javax.swing.Icon
import javax.swing.JList

/*
 * A cell renderer that sets the icon and background for elements in the property inspector
 */
open class NavListCellRenderer(private val regularIcon: Icon) : ColoredListCellRenderer<NlComponent>() {
  private val whiteIcon = ColoredIconGenerator.generateWhiteIcon(regularIcon)

  override fun customizeCellRenderer(list: JList<out NlComponent>, value: NlComponent?, index: Int, selected: Boolean, hasFocus: Boolean) {
    icon = if (selected && hasFocus) whiteIcon else regularIcon

    if (!selected) {
      background = secondaryPanelBackground
    }
  }
}