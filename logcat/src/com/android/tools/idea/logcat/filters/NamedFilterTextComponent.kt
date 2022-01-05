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

import com.android.tools.idea.logcat.LogcatBundle
import com.android.tools.idea.logcat.LogcatPresenter
import com.android.tools.idea.logcat.filters.SavedFilterComboItem.Button
import com.android.tools.idea.logcat.filters.SavedFilterComboItem.SavedFilter
import com.android.tools.idea.logcat.filters.SavedFilterComboItem.Separator
import com.android.tools.idea.logcat.util.AndroidProjectDetector
import com.android.tools.idea.logcat.util.AndroidProjectDetectorImpl
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
import java.awt.Component
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.ComboBoxModel
import javax.swing.GroupLayout
import javax.swing.GroupLayout.DEFAULT_SIZE
import javax.swing.GroupLayout.PREFERRED_SIZE
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JOptionPane
import javax.swing.JOptionPane.PLAIN_MESSAGE
import javax.swing.JPanel
import javax.swing.JSeparator
import javax.swing.ListCellRenderer
import javax.swing.SwingConstants
import javax.swing.event.ListDataListener

private val CLEAR_FILTER_ITEM = Button(LogcatBundle.message("logcat.filter.clear.filter"))

// TODO(aalbert): Render this placeholder in a different color.
private val NO_SAVED_FILTERS_ITEM = Button(LogcatBundle.message("logcat.filter.clear.saved.filters.placeholder"))
private val MANAGE_FILTERS_ITEM = Button(LogcatBundle.message("logcat.filter.clear.manage.filters"))
private val SEPARATOR = Separator()

/**
 * A component containing the filter edit field, the saved filters combobox and the save button along with all the interactions between
 * them.
 */
internal class NamedFilterComponent(
  project: Project,
  logcatPresenter: LogcatPresenter,
  filterParser: LogcatFilterParser,
  initialText: String,
  androidProjectDetector: AndroidProjectDetector = AndroidProjectDetectorImpl(),
) : JPanel(), FilterTextComponent {
  private val filterTextField = FilterTextField(project, logcatPresenter, filterParser, initialText, androidProjectDetector)
  private val savedFiltersCombo = SavedFilterCombo(filterTextField)

  override var text: String
    get() = filterTextField.text
    set(value) {
      filterTextField.text = value
    }

  override val component: Component get() = this

  private val saveButton = JLabel(AllIcons.Actions.MenuSaveall).apply {
    toolTipText = LogcatBundle.message("logcat.filter.save.button.tooltip")
  }

  init {
    layout = GroupLayout(this).apply {
      autoCreateGaps = true
      setHorizontalGroup(
        createSequentialGroup()
          .addComponent(savedFiltersCombo, DEFAULT_SIZE, DEFAULT_SIZE, PREFERRED_SIZE)
          .addComponent(filterTextField)
          .addComponent(saveButton)
      )
      setVerticalGroup(
        createParallelGroup(GroupLayout.Alignment.CENTER)
          .addComponent(savedFiltersCombo, DEFAULT_SIZE, DEFAULT_SIZE, PREFERRED_SIZE)
          .addComponent(filterTextField, DEFAULT_SIZE, DEFAULT_SIZE, PREFERRED_SIZE)
          .addComponent(saveButton)
      )
    }

    filterTextField.addDocumentListener(object : DocumentListener {
      override fun documentChanged(event: com.intellij.openapi.editor.event.DocumentEvent) {
        saveButton.isEnabled = filterTextField.text.isNotEmpty()
      }
    })
    saveButton.apply {
      isEnabled = filterTextField.text.isNotEmpty()
      addMouseListener(object : MouseAdapter() {
        override fun mouseClicked(e: MouseEvent?) {
          // TODO(aalbert): Use a generic dialog for now. Will change to a more specialized dialog downstream.
          val name = JOptionPane.showInputDialog(
            this@NamedFilterComponent,
            LogcatBundle.message("logcat.filter.save.dialog.message"),
            LogcatBundle.message("logcat.filter.save.dialog.title"),
            PLAIN_MESSAGE,
            null,
            null,
            ""
          ) as? String
          if (name != null) {
            // TODO(aalbert): Implement storing saved filters in the project.
            savedFiltersCombo.addSavedFilter(name, filterTextField.text)
            savedFiltersCombo.setSelectedSavedFilter(name)
          }
        }
      })
    }
  }

  override fun addDocumentListener(listener: DocumentListener) {
    filterTextField.addDocumentListener(listener)
  }

  @TestOnly
  fun addSavedFilter(name: String, filter: String) {
    savedFiltersCombo.addSavedFilter(name, filter)
  }
}

/**
 * A class that is used as the item in the Saved Filter ComboBox.
 *
 * There are 3 types of items:
 * 1. A Saved Filter item. When chosen, apples the value to the filter text field.
 * 2. A Button item. Performs some action when chosen.
 * 3. A Separator item. Visual only.
 */
@VisibleForTesting
internal sealed class SavedFilterComboItem {
  // TODO(aalbert): Handle modified filters (when the filter text field is modified while the saved filter is selected.
  class SavedFilter(val name: String, val filter: String) : SavedFilterComboItem() {
    override val component = JLabel(name)
  }

  class Button(@VisibleForTesting val label: String) : SavedFilterComboItem() {
    override val component = JLabel(label)
  }

  class Separator : SavedFilterComboItem() {
    override val component = JSeparator(SwingConstants.HORIZONTAL)
  }

  abstract val component: Component
}

/**
 * A [JComboBox] that contains a list of saved filters items as well as some management items.
 *
 * @param filterTextField a text field whose value is set when items are selected.
 */
private class SavedFilterCombo(filterTextField: FilterTextField) : JComboBox<SavedFilterComboItem>() {
  private val savedFilterComboModel = SavedFilterComboModel(filterTextField)

  init {
    // When selectedItem is null, we render "Unsaved filter". See SavedFilterComboRenderer.
    selectedItem = null
    // Do not use "renderer = " syntax because there is also a protected field by that name that hides the method
    setRenderer(SavedFilterComboRenderer())
    model = savedFilterComboModel

    // TODO(b/218861725): The UX specifies no border on the combo which is strange. It's not trivial to do this because simply removing the
    //  border leaves the combo with the wrong background color (in Light theme) and it's not trivial to fix that. We may need to use a
    //  custom component instead of a JComboBox.
  }

  fun addSavedFilter(name: String, filter: String) {
    savedFilterComboModel.addSavedFilter(SavedFilter(name, filter))
  }

  fun setSelectedSavedFilter(name: String) {
    savedFilterComboModel.setSelectedSavedFilter(name)
  }
}

/**
 * A [ComboBoxModel] that contains a list of saved filters items as well as some management items.
 *
 * @param filterTextField a text field whose value is set when items are selected.
 */
private class SavedFilterComboModel(private val filterTextField: FilterTextField) : ComboBoxModel<SavedFilterComboItem> {
  // Filters sorted alphabetically by name
  private val savedFilters = sortedMapOf<String, SavedFilter>()
  private var selectedItem: SavedFilter? = null

  /**
   * The combo always contains a "Clear filter" item, a "Manage filters" item and 2 separators. If there are no saved filters, it contains a
   * hint item.
   */
  override fun getSize() = if (savedFilters.isEmpty()) 5 else savedFilters.size + 4

  // TODO(aalbert): Find a way to make NO_SAVED_FILTERS_ITEM & SEPARATOR items not selectable
  override fun getElementAt(index: Int): SavedFilterComboItem {
    return when {
      index == 0 -> CLEAR_FILTER_ITEM
      index == 1 || index == size - 2 -> SEPARATOR
      index == size - 1 -> MANAGE_FILTERS_ITEM
      savedFilters.isEmpty() -> NO_SAVED_FILTERS_ITEM
      else -> savedFilters.values.toList()[index - 2]
    }
  }

  override fun addListDataListener(l: ListDataListener?) {
  }

  override fun removeListDataListener(l: ListDataListener?) {
  }

  override fun setSelectedItem(anItem: Any?) {
    when {
      anItem is SavedFilter -> {
        selectedItem = anItem
        filterTextField.text = anItem.filter
      }
      anItem === CLEAR_FILTER_ITEM -> {
        selectedItem = null
        filterTextField.text = ""
      }
      anItem === MANAGE_FILTERS_ITEM -> {
        // TODO(aalbert): Implement Manage Filters dialog.
      }
    }
  }

  override fun getSelectedItem(): Any? = selectedItem

  fun addSavedFilter(savedFilter: SavedFilter) {
    savedFilters[savedFilter.name] = savedFilter
  }

  fun setSelectedSavedFilter(name: String) {
    selectedItem = savedFilters[name]
  }
}

/**
 * Renders the main text of the ComboBox. This is the name of the saved filter or the text "Unsaved filter" if no saved filter is set.
 */
private class SavedFilterComboRenderer : ListCellRenderer<SavedFilterComboItem> {
  override fun getListCellRendererComponent(
    list: JList<out SavedFilterComboItem>,
    value: SavedFilterComboItem?,
    index: Int,
    isSelected: Boolean,
    cellHasFocus: Boolean
  ): Component = value?.component ?: JLabel(LogcatBundle.message("logcat.filter.unsaved.filter"))
}
