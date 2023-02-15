/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.appinspection.inspectors.network.model.rules

import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.ListTableModel
import javax.swing.JTable

class RulesTableModel : ListTableModel<RuleData>() {

  init {
    columnInfos =
      arrayOf(
        object : ColumnInfo<RuleData, Boolean>("Active") {
          override fun valueOf(item: RuleData): Boolean {
            return item.isActive
          }

          override fun setValue(item: RuleData, value: Boolean) {
            item.isActive = value
          }

          override fun getWidth(table: JTable) = 60
          override fun isCellEditable(item: RuleData) = true
          override fun getColumnClass() = Boolean::class.java
        },
        object : ColumnInfo<RuleData, String>("Name") {
          override fun valueOf(item: RuleData): String {
            return item.name
          }
        },
        object : ColumnInfo<RuleData, String>("URL") {
          override fun valueOf(item: RuleData): String {
            return item.criteria.url
          }
        }
      )
  }
}
