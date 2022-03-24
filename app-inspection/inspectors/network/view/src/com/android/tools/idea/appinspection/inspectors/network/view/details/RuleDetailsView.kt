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
package com.android.tools.idea.appinspection.inspectors.network.view.details

import com.android.tools.idea.appinspection.inspectors.network.model.rules.RulesTableModel
import com.intellij.ui.components.panels.VerticalLayout
import javax.swing.JPanel

class RuleDetailsView : JPanel(VerticalLayout(15)) {
  var selectedRule = RulesTableModel.RuleInfo()

  fun setRule(rule: RulesTableModel.RuleInfo) {
    if (rule == selectedRule) {
      return
    }
    selectedRule = rule
  }
}
