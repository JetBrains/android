/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.common.property2.impl.model

import com.android.tools.adtui.model.stdui.ValueChangedListener
import com.android.tools.idea.common.property2.api.InspectorLineModel
import com.intellij.psi.codeStyle.NameUtil

/**
 * A model for a property inspector.
 *
 * Contains implementations for search/filtering of properties in the inspector.
 * @property lines All line models displayed in the inspector.
 * @property filter The search term or an empty string. The panel will hide all lines where the label text doesn't match.
 */
class InspectorPanelModel {
  private var listeners = mutableListOf<ValueChangedListener>()
  private val lines = mutableListOf<InspectorLineModel>()

  var filter: String = ""
    set(value) {
      field = value
      updateFiltering()
    }

  fun clear() {
    lines.clear()
  }

  /**
   * Moves the focus to the editor in the next line.
   *
   * @return true if such an editor is found.
   */
  fun moveToNextLineEditor(line: InspectorLineModel) {
    val index = lines.indexOf(line)
    if (index < 0) return
    val nextLine = findClosestNextLine(index) ?: return
    nextLine.requestFocus()
  }

  /**
   * Search for the closest line after [lineIndex] that can take focus. Wrap to the first line if we get to the end.
   */
  private fun findClosestNextLine(lineIndex: Int): InspectorLineModel? {
    var index = (lineIndex + 1) % lines.size
    while (index != lineIndex) {
      val line = lines[index]
      if (line.visible && line.focusable) {
        return line
      }
      index = (index + 1) % lines.size
    }
    return null
  }

  fun propertyValuesChanged() {
    lines.forEach { it.refresh() }
  }

  fun add(line: InspectorLineModel) {
    lines.add(line)
  }

  fun addValueChangedListener(listener: ValueChangedListener) {
    listeners.add(listener)
  }

  fun removeValueChangedListener(listener: ValueChangedListener) {
    listeners.add(listener)
  }

  fun enterInFilter(): Boolean {
    if (filter.isEmpty()) {
      return false
    }
    val visibleLabels = lines.filter{ it.visible }
    if (visibleLabels.size != 1) {
      return false
    }
    val label = visibleLabels[0] as? CollapsibleLabelModel
    val editor = label?.editorModel ?: return false
    editor.requestFocus()
    return true
  }

  private fun updateFiltering() {
    if (filter.isNotEmpty()) {
      applyFilter()
    }
    else {
      restoreGroups()
    }
    fireValueChanged()
  }

  private fun applyFilter() {
    // Place a "*" in front of the filter to allow the typed filter to match other places than just the beginning of a string.
    val matcher = NameUtil.buildMatcher("*$filter").build()
    lines.forEach { line ->
      if (line is CollapsibleLabelModel) {
        line.expandable = false
      }
      line.visible = line.isMatch(matcher)
    }
  }

  private fun restoreGroups() {
    lines.reversed().forEach { line ->
      if (line is CollapsibleLabelModel) {
        line.expandable = line.hasChildren
        if (line.parent == null) {
          line.visible = true
        }
      }
      else {
        line.visible = true
      }
    }
  }

  private fun fireValueChanged() {
    listeners.forEach { it.valueChanged() }
  }
}
