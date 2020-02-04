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
package org.jetbrains.android.actions.widgets

import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SimpleTextAttributes
import javax.swing.JList

/**
 * Cell renderer to display **SourceSets** with an specific **res** directory on a ComboBox.
 */
class SourceSetCellRenderer : ColoredListCellRenderer<SourceSetItem>() {
  override fun customizeCellRenderer(list: JList<out SourceSetItem>,
                                     value: SourceSetItem,
                                     index: Int,
                                     selected: Boolean,
                                     hasFocus: Boolean) {
    append(value.sourceSetName)
    append(" ")
    append(value.displayableResDir, SimpleTextAttributes.GRAYED_ITALIC_ATTRIBUTES, false)
  }
}