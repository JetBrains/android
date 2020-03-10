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
package com.android.tools.idea.appinspection.ide.ui

import com.android.tools.adtui.stdui.CommonComboBox
import com.android.tools.idea.appinspection.api.ProcessDescriptor
import com.android.tools.idea.appinspection.ide.model.AppInspectionProcessesComboBoxModel
import java.awt.Component
import java.awt.Dimension
import javax.swing.DefaultListCellRenderer
import javax.swing.JList

//TODO(b/148546243): separate view and model code into independent modules.
class AppInspectionProcessesComboBox(model: AppInspectionProcessesComboBoxModel) :
  CommonComboBox<ProcessDescriptor, AppInspectionProcessesComboBoxModel>(model) {
  init {
    // Initial size chosen for a reasonable amount width to fit many longish target names
    // as well as a height so text fits comfortable.
    preferredSize = Dimension(400, 30)
    renderer = object : DefaultListCellRenderer() {
      override fun getListCellRendererComponent(
        list: JList<*>?,
        value: Any?,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean
      ): Component {
        val content = if (value is ProcessDescriptor) "${value.model.replace('_', ' ')} -> ${value.processName}" else value
        return super.getListCellRendererComponent(list, content, index, isSelected, cellHasFocus)
      }
    }
  }
}