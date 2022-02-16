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
package com.android.tools.idea.logcat.filters

import com.android.tools.adtui.TreeWalker
import com.android.tools.idea.FakeAndroidProjectDetector
import com.android.tools.idea.logcat.FakeLogcatPresenter
import com.android.tools.idea.logcat.FakePackageNamesProvider
import com.android.tools.idea.logcat.filters.NamedFilterComboItem.Button
import com.android.tools.idea.logcat.filters.NamedFilterComboItem.NamedFilter
import com.android.tools.idea.logcat.filters.NamedFilterComboItem.Separator
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import org.junit.Rule
import org.junit.Test
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JList

/**
 * Tests for [NamedFilterComponent]
 */
@RunsInEdt
class FilterComponentTest {
  private val projectRule = ProjectRule()

  @get:Rule
  val rule = RuleChain(projectRule, EdtRule())

  @Test
  fun constructor_setsText() {
    val filterComponent = filterComponent("foo")

    assertThat(filterComponent.getFilterTextField().text).isEqualTo("foo")
  }

  @Test
  fun rendersItems_noNamedFilters() {
    val filterComponent = filterComponent()

    assertThat(filterComponent.getComboBox().getItems()).containsExactly(
      ItemInfo("Clear filter bar"),
      ItemInfo("separator"),
      ItemInfo("No saved filters"),
      ItemInfo("separator"),
      ItemInfo("Manage filters"),
    ).inOrder()
  }

  @Test
  fun rendersItems_withNamedFilters() {
    val filterComponent = filterComponent()

    filterComponent.addNamedFilter("Foo", "foo")
    filterComponent.addNamedFilter("Bar", "bar")

    assertThat(filterComponent.getComboBox().getItems()).containsExactly(
      ItemInfo("Clear filter bar"),
      ItemInfo("separator"),
      ItemInfo("Bar", "bar"), // Sorted alphabetically
      ItemInfo("Foo", "foo"),
      ItemInfo("separator"),
      ItemInfo("Manage filters"),
    ).inOrder()
  }

  @Test
  fun noNamedFilter_rendersCorrectly() {
    val filterComponent = filterComponent()

    assertThat(filterComponent.getComboBox().getRenderedText()).isEqualTo("Unsaved filter")
  }

  @Test
  fun clearFilter() {
    val filterComponent = filterComponent("foo")

    filterComponent.getComboBox().selectItem("Clear filter bar")

    assertThat(filterComponent.text).isEmpty()
  }

  @Test
  fun selectNamedFilter() {
    val filterComponent = filterComponent()
    filterComponent.addNamedFilter("Foo", "foo")

    filterComponent.getComboBox().selectItem("Foo")

    assertThat(filterComponent.getComboBox().getRenderedText()).isEqualTo("Foo")
    assertThat(filterComponent.text).isEqualTo("foo")
  }

  @Test
  fun selectNamedFilterAndThenClear() {
    val filterComponent = filterComponent()
    filterComponent.addNamedFilter("Foo", "foo")

    filterComponent.getComboBox().selectItem("Foo")
    filterComponent.getComboBox().selectItem("Clear filter bar")

    assertThat(filterComponent.getComboBox().getRenderedText()).isEqualTo("Unsaved filter")
    assertThat(filterComponent.text).isEmpty()
  }

  @Test
  fun saveButton_showsDialogAndAddsNamedFilter() {
    // TODO(aalbert): Will add tests for save button and dialog later because right now, we're using generic JOptionPane.showInputDialog()
    //  which doesn't use a testable DialogWrapper. We need to change to a more specialized dialog which will use a DialogWrapper.
  }

  private fun filterComponent(
    initialText: String = "",
  ): NamedFilterComponent {
    val logcatPresenter = FakeLogcatPresenter().apply {
      Disposer.register(projectRule.project, this)
    }
    return NamedFilterComponent(
      projectRule.project,
      logcatPresenter,
      LogcatFilterParser(projectRule.project, FakePackageNamesProvider()),
      initialText,
      FakeAndroidProjectDetector(true))
  }
}

private fun NamedFilterComponent.getFilterTextField(): FilterTextField =
  TreeWalker(this).descendants().filterIsInstance<FilterTextField>()[0]

private fun NamedFilterComponent.getComboBox(): JComboBox<NamedFilterComboItem> =
  TreeWalker(this).descendants().filterIsInstance<JComboBox<NamedFilterComboItem>>()[0]

// This is the only way I found to get the text of the combobox.
private fun JComboBox<NamedFilterComboItem>.getRenderedText(): String {
  // In our renderer implementation, only the selectedItem param is used.
  val component = renderer.getListCellRendererComponent(
    JList(),
    selectedItem as NamedFilterComboItem?,
    selectedIndex,
    false,
    false)
  return (component as JLabel).text
}

private fun JComboBox<NamedFilterComboItem>.getItems(): List<ItemInfo> {
  val items = mutableListOf<ItemInfo>()
  for (i in 0 until itemCount) {
    val itemInfo = when (val item = getItemAt(i)) {
      is NamedFilter -> ItemInfo(item.name, item.filter)
      is Button -> ItemInfo(item.label)
      is Separator -> ItemInfo("separator")
      else -> throw IllegalStateException("Unknown item found: ${this::class.simpleName}")
    }
    items.add(itemInfo)
  }
  return items
}

private fun JComboBox<NamedFilterComboItem>.selectItem(label: String) {
  for (i in 0 until itemCount) {
    val item = getItemAt(i)
    val itemToSelect = when {
      item is NamedFilter && item.name == label -> item
      item is Button && item.label == label -> item
      else -> null
    }
    if (itemToSelect != null) {
      selectedItem = itemToSelect
      return
    }
  }
  throw IllegalArgumentException("Item '$label' not found")
}

// An encapsulation of NamedFilterComboItem classes that is easily testable
private data class ItemInfo(val name: String, val filter: String? = null)