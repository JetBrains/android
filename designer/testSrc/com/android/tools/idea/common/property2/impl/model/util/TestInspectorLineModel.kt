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
package com.android.tools.idea.common.property2.impl.model.util

import com.android.tools.adtui.ptable2.PTableModel
import com.android.tools.idea.common.property2.api.InspectorLineModel
import com.android.tools.idea.common.property2.api.PropertyEditorModel
import com.intellij.openapi.actionSystem.AnAction

enum class TestLineType {
  TITLE, PROPERTY, TABLE, PANEL, SEPARATOR
}

open class TestInspectorLineModel(val type: TestLineType) : InspectorLineModel {
  override var visible = true
  override var hidden = false
  override var focusable = true
  override var parent: InspectorLineModel? = null
  var actions = listOf<AnAction>()
  open val tableModel: PTableModel? = null
  var title: String? = null
  var editorModel: PropertyEditorModel? = null
  var expandable = false
  var expanded = false
  val children = mutableListOf<InspectorLineModel>()
  val childProperties: List<String>
    get() = children.map { it as TestInspectorLineModel }.map { it.editorModel!!.property.name }

  var focusWasRequested = false
    private set

  override fun requestFocus() {
    focusWasRequested = true
  }

  override fun makeExpandable(initiallyExpanded: Boolean) {
    expandable = true
    expanded = initiallyExpanded
  }
}
