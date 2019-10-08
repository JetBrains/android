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
package com.android.tools.property.panel.impl.model

import com.android.tools.adtui.model.stdui.ValueChangedListener
import com.android.tools.property.panel.api.InspectorLineModel
import com.android.tools.property.panel.api.TableLineModel
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.actionSystem.DataProvider
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

  @VisibleForTesting
  val lines = mutableListOf<InspectorLineModel>()

  var dataProviderDelegate: DataProvider? = null

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
    val visibleLabels = lines.filter{ it.visible && (it is CollapsibleLabelModel || it is TableLineModel) }
    if (visibleLabels.count() != 1) {
      return false
    }
    val label = visibleLabels[0]
    if (label is CollapsibleLabelModel) {
      val editor = label.editorModel ?: return false
      editor.requestFocus()
      return true
    }
    else if (label is TableLineModel) {
      label.requestFocusInBestMatch()
      return true
    }
    return false
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
      when {
        !line.isSearchable -> line.visible = false
        line is CollapsibleLabelModel -> line.hideForSearch(line.isMatch(matcher))
        line is TableLineModel -> applyFilterToTable(line)
        else -> line.visible = line.isMatch(matcher)
      }
    }
  }

  private fun applyFilterToTable(line: TableLineModel) {
    line.filter = filter
    line.visible = line.itemCount > 0
  }

  private fun restoreGroups() {
    lines.reversed().forEach { line ->
      when {
        line is CollapsibleLabelModel -> line.restoreAfterSearch()
        line.isSearchable -> line.filter = ""
        else -> line.visible = true
      }
    }
  }

  private fun fireValueChanged() {
    listeners.toTypedArray().forEach { it.valueChanged() }
  }
}
