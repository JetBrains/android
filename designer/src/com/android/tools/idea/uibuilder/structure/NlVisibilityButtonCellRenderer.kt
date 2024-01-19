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
package com.android.tools.idea.uibuilder.structure

import java.awt.Component
import javax.swing.JList
import javax.swing.ListCellRenderer

class NlVisibilityButtonCellRenderer : ListCellRenderer<ButtonPresentation> {

  val button = NlVisibilityButton()

  override fun getListCellRendererComponent(
    list: JList<out ButtonPresentation>?,
    value: ButtonPresentation,
    index: Int,
    isSelected: Boolean,
    cellHasFocus: Boolean,
  ): Component {
    list as NlVisibilityJBList
    value.isHovered = index == list.currHovered
    // isSelected here is not trust worthy. It unselects at random places.
    value.isClicked = index == list.currClicked

    button.update(value)
    return button
  }
}
