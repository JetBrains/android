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
package com.android.tools.adtui

import com.android.tools.adtui.model.filter.Filter
import com.android.tools.adtui.model.filter.FilterHandler
import com.android.tools.adtui.model.filter.FilterResult
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.awt.BorderLayout
import javax.swing.JPanel

class FilterComponentTest {

  class FilterComponentUi {
    val panel = JPanel(BorderLayout())
    val filterComponent = FilterComponent(245, 5, 0)
    val filterButton = FilterComponent.createFilterToggleButton()

    init {
      panel.add(filterButton, BorderLayout.EAST)
      panel.add(filterComponent, BorderLayout.SOUTH)
      filterComponent.isVisible = false
      FilterComponent.configureKeyBindingAndFocusBehaviors(panel, filterComponent, filterButton)
    }
  }

  @Test
  fun filterComponentCanBeInitializedWithFilter() {
    val filter = Filter("XYZ", true, true)
    val filterComponent = FilterComponent(filter, 123, 4, 0)

    assertThat(filterComponent.searchField.text).isEqualTo(filter.filterString)
  }

  @Test
  fun clicksFilterButton() {
    val ui = FilterComponentUi()

    ui.filterButton.doClick()
    assertThat(ui.filterComponent.isVisible).isTrue()
    ui.filterButton.doClick()
    assertThat(ui.filterComponent.isVisible).isFalse()
  }

  @Test
  fun changeFilterResult() {
    val ui = FilterComponentUi()

    ui.filterComponent.model.setFilterHandler(object : FilterHandler() {
      override fun applyFilter(filter: Filter): FilterResult {
        return if (filter.isEmpty) {
          // The FilterResult should not be enabled with empty filter.
          // We return an enabled filter intentionally to test if FilterHandler can correct it.
          FilterResult(0, 0, true)
        }
        else {
          FilterResult(Integer.parseInt(filter.filterString), 0, true)
        }
      }
    })
    val normalBackground = ui.filterComponent.searchField.textEditor.background;
    ui.filterComponent.model.filter = Filter("")
    assertThat(ui.filterComponent.countLabel.text).isEqualTo("")
    assertThat(ui.filterComponent.searchField.textEditor.background).isEqualTo(normalBackground)
    ui.filterComponent.model.filter = Filter("0")
    assertThat(ui.filterComponent.countLabel.text).isEqualTo("No matches")
    assertThat(ui.filterComponent.searchField.textEditor.background).isEqualTo(FilterComponent.NO_MATCHES_COLOR)
    ui.filterComponent.model.filter = Filter("1")
    assertThat(ui.filterComponent.countLabel.text).isEqualTo("One match")
    assertThat(ui.filterComponent.searchField.textEditor.background).isEqualTo(normalBackground)
    ui.filterComponent.model.filter = Filter("1234567")
    assertThat(ui.filterComponent.countLabel.text).isEqualTo("1,234,567 matches")
    assertThat(ui.filterComponent.searchField.textEditor.background).isEqualTo(normalBackground)
  }

  @Test
  fun canHideCountLabel() {
    val ui = FilterComponentUi()

    ui.filterComponent.setMatchCountVisibility(false)
    ui.filterComponent.model.setFilterHandler(object: FilterHandler() {
      override fun applyFilter(filter: Filter): FilterResult {
        return FilterResult(Integer.parseInt(filter.filterString), 0, true)
      }
    })
    ui.filterComponent.model.filter = Filter("1234567")
    assertThat(ui.filterComponent.countLabel.text).isNotEmpty()
    assertThat(ui.filterComponent.countLabel.isVisible).isFalse()
  }
}