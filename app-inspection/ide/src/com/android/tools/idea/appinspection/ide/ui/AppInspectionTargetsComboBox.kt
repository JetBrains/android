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
import com.android.tools.idea.appinspection.ide.model.AppInspectionTargetsComboBoxModel
import java.awt.Component
import java.awt.Dimension
import javax.swing.DefaultListCellRenderer
import javax.swing.JList

class AppInspectionTargetsComboBox(model: AppInspectionTargetsComboBoxModel)
  : CommonComboBox<ProcessDescriptor, AppInspectionTargetsComboBoxModel>(model) {
  init {
    // Initial size chosen for a reasonable amount width to fit many longish target names
    // as well as a height so text fits comfortable.
    preferredSize = Dimension(400, 30)
    renderer = object : DefaultListCellRenderer() {
      override fun getListCellRendererComponent(list: JList<*>?,
                                                value: Any?,
                                                index: Int,
                                                isSelected: Boolean,
                                                cellHasFocus: Boolean): Component {
        if (value is ProcessDescriptor) {
          var text = value.stream.device.model.replace('_', ' ');
          if (value.process.name != null) {
            text += " -> " + value.process.name
          }
          return super.getListCellRendererComponent(list, text, index, isSelected, cellHasFocus)
        }
        return super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
      }
    }
  }
}