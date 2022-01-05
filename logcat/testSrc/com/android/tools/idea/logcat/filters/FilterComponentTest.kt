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
import com.android.tools.idea.logcat.filters.SavedFilterComboItem.Button
import com.android.tools.idea.logcat.filters.SavedFilterComboItem.SavedFilter
import com.android.tools.idea.logcat.filters.SavedFilterComboItem.Separator
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
  fun rendersItems_noSavedFilters() {
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
  fun rendersItems_withSavedFilters() {
    val filterComponent = filterComponent()

    filterComponent.addSavedFilter("Foo", "foo")
    filterComponent.addSavedFilter("Bar", "bar")

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
  fun noSavedFilter_rendersCorrectly() {
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
  fun selectSavedFilter() {
    val filterComponent = filterComponent()
    filterComponent.addSavedFilter("Foo", "foo")

    filterComponent.getComboBox().selectItem("Foo")

    assertThat(filterComponent.getComboBox().getRenderedText()).isEqualTo("Foo")
    assertThat(filterComponent.text).isEqualTo("foo")
  }

  @Test
  fun selectSavedFilterAndThenClear() {
    val filterComponent = filterComponent()
    filterComponent.addSavedFilter("Foo", "foo")

    filterComponent.getComboBox().selectItem("Foo")
    filterComponent.getComboBox().selectItem("Clear filter bar")

    assertThat(filterComponent.getComboBox().getRenderedText()).isEqualTo("Unsaved filter")
    assertThat(filterComponent.text).isEmpty()
  }

  @Test
  fun saveButton_showsDialogAndAddsSavedFilter() {
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

private fun NamedFilterComponent.getComboBox(): JComboBox<SavedFilterComboItem> =
  TreeWalker(this).descendants().filterIsInstance<JComboBox<SavedFilterComboItem>>()[0]

// This is the only way I found to get the text of the combobox.
private fun JComboBox<SavedFilterComboItem>.getRenderedText(): String {
  // In our renderer implementation, only the selectedItem param is used.
  val component = renderer.getListCellRendererComponent(
    JList(),
    selectedItem as SavedFilterComboItem?,
    selectedIndex,
    false,
    false)
  return (component as JLabel).text
}

private fun JComboBox<SavedFilterComboItem>.getItems(): List<ItemInfo> {
  val items = mutableListOf<ItemInfo>()
  for (i in 0 until itemCount) {
    val itemInfo = when (val item = getItemAt(i)) {
      is SavedFilter -> ItemInfo(item.name, item.filter)
      is Button -> ItemInfo(item.label)
      is Separator -> ItemInfo("separator")
      else -> throw IllegalStateException("Unknown item found: ${this::class.simpleName}")
    }
    items.add(itemInfo)
  }
  return items
}

private fun JComboBox<SavedFilterComboItem>.selectItem(label: String) {
  for (i in 0 until itemCount) {
    val item = getItemAt(i)
    val itemToSelect = when {
      item is SavedFilter && item.name == label -> item
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

// An encapsulation of SavedFilterComboItem classes that is easily testable
private data class ItemInfo(val name: String, val filter: String? = null)