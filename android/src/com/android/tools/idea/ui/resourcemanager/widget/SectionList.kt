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
package com.android.tools.idea.ui.resourcemanager.widget

import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.ScrollingUtil
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import org.jetbrains.kotlin.idea.util.application.invokeLater
import java.awt.Color
import java.awt.Container
import java.awt.Dimension
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.Box
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.ListCellRenderer
import javax.swing.ListModel
import javax.swing.ListSelectionModel
import javax.swing.event.ListDataEvent
import javax.swing.event.ListDataListener
import javax.swing.event.ListSelectionListener

private val EMPTY_SELECTION_ARRAY = IntArray(0)

/**
 * A Swing component displaying multiple lists organized in [Section]s with a header.
 *
 * The component is divided into two parts: the main component (this instance) which displays
 * the lists in a [JScrollPane] and the section list ([sectionsComponent]) which displays the list of
 * the sections' names.
 *
 * The ScrollPane and the Section list are synchronized: clicking on a section name in the list will scroll
 * the corresponding section in the main component, and scrolling the main component will select the section name of
 * the section visible at the top in the section list.
 */
class SectionList(private val model: SectionListModel) : JBScrollPane() {

  private var sectionList = JBList(model)
  private val allInnerLists = mutableListOf<JList<*>>()
  private var listSelectionChanging = false
  private val innerListSelectionListener = createListSelectionListener()
  private var content: JComponent = createMultiListPanel(allInnerLists, innerListSelectionListener)

  /**
   * Gap between lists
   */
  private val listsGap = JBUI.scale(16)

  /**
   * Returns the list of [Section] name
   */
  val sectionsComponent = sectionList

  private val multiLisLayoutManager = VerticalFlowLayout(true, false)

  private val focusListener = object : FocusAdapter() {

    override fun focusGained(focusEvent: FocusEvent?) {
      val list = focusEvent?.source as JList<*>
      if (list.selectedIndex == -1) {
        list.selectedIndex = 0
        scrollToSelection()
      }
    }
  }

  init {
    model.addListDataListener(object : ListDataListener {
      override fun contentsChanged(e: ListDataEvent?) {
        allInnerLists.clear()
        sectionList.selectionModel.clearSelection()
        content = createMultiListPanel(allInnerLists, innerListSelectionListener)
        setViewportView(content)
      }

      override fun intervalRemoved(e: ListDataEvent?) {}

      override fun intervalAdded(e: ListDataEvent?) {}
    })
    sectionList.selectionMode = ListSelectionModel.SINGLE_SELECTION
    sectionList.selectedIndex = 0
    sectionList.cellRenderer = createSectionCellRenderer()
    sectionList.addListSelectionListener {
      val selectedValue = sectionList.selectedValue
      if (selectedValue != null) {
        viewport.viewPosition = selectedValue.header.location
      }
    }

    verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_ALWAYS
    addMouseListener(object : MouseAdapter() {
      override fun mouseClicked(p0: MouseEvent?) {
        focusInnerList()
      }
    })
  }

  /**
   * Focuses the first list with a selected item, or just the first list if there's no selection.
   */
  private fun focusInnerList() {
    (allInnerLists.firstOrNull { it.selectedIndex != -1 } ?: allInnerLists.firstOrNull())?.requestFocusInWindow()
  }

  /**
   * Creates a [ListSelectionListener] that clears the selection of
   * all lists except the one which is the source of the event.
   */
  private fun createListSelectionListener(): ListSelectionListener {
    return ListSelectionListener { event ->
      if (!listSelectionChanging) {
        listSelectionChanging = true
        allInnerLists
          .filterNot { it == event.source }
          .forEach { it.clearSelection() }
        listSelectionChanging = false
      }
    }
  }

  /**
   * Creates the default [ListCellRenderer] for the section list which just displays the name
   * of the section in a JLabel
   */
  private fun createSectionCellRenderer(): ColoredListCellRenderer<Section<*>> {
    return object : ColoredListCellRenderer<Section<*>>() {
      override fun customizeCellRenderer(
        list: JList<out Section<*>>,
        value: Section<*>?,
        index: Int,
        selected: Boolean,
        hasFocus: Boolean
      ) {
        append(value?.name ?: "")
      }
    }
  }

  private fun createMultiListPanel(
    allInnerLists: MutableList<JList<*>>,
    selectionListener: ListSelectionListener
  ): JComponent {
    return JPanel(multiLisLayoutManager).apply{
      background = this@SectionList.background
      for (section in model.sections) {
        allInnerLists += section.list
        section.list.addListSelectionListener(selectionListener)
        section.list.addFocusListener(focusListener)
        add(section.header)
        add(section.list)
        add(Box.createVerticalStrut(listsGap))
      }
    }
  }

  fun getLists(): List<JList<*>> {
    return allInnerLists
  }

  fun getSections() = model.sections

  var selectedValue: Any?
    get() = allInnerLists.firstOrNull { it.selectedValue != null }?.selectedValue
    set(value) = allInnerLists.forEach {
      listSelectionChanging = true
      it.setSelectedValue(value, false)
      listSelectionChanging = false
    }

  /**
   * Represent the selected indices for each inner [JList]
   * @see JList.getSelectedIndices
   * @see JList.setSelectedIndices
   */
  var selectedIndices: List<IntArray?>
    get() = allInnerLists.map { it.selectedIndices }
    set(indicesByList) = indicesByList.zip(allInnerLists).forEach { (indices, list) ->
      listSelectionChanging = true
      list.selectedIndices = indices ?: EMPTY_SELECTION_ARRAY
      listSelectionChanging = false
    }

  /**
   * Pair of the index of the first selected inner [JList] to its first selected index
   * @see JList.setSelectedIndex
   * @see JList.getSelectedIndex
   */
  var selectedIndex: Pair<Int, Int>?
    get() {
      val indexOfSelectedList = allInnerLists.indexOfFirst { it.selectedIndex != -1 }
      if (indexOfSelectedList == -1) return null
      return indexOfSelectedList to allInnerLists[indexOfSelectedList].selectedIndex
    }
    set(listToIndex) {
      if (listToIndex != null) {
        val (list, index) = listToIndex
        allInnerLists.getOrNull(list)?.selectedIndex = index
      }
      else {
        allInnerLists.forEach { it.selectedIndex = -1 }
      }
    }

  override fun setBackground(bg: Color?) {
    @Suppress("UNNECESSARY_SAFE_CALL")
    content?.background = bg
    viewport?.background = bg
    super.setBackground(bg)
  }

  fun scrollToSelection() {
    val (listIndex, itemIndex) = selectedIndex ?: return
    ScrollingUtil.ensureIndexIsVisible(allInnerLists[listIndex], itemIndex, 1)
  }

  override fun doLayout() {
    viewport.view?.takeIf { it is Container }?.let { content ->
      val contentHeight = multiLisLayoutManager.preferredLayoutSize(content as Container).height
      val scrollPrefSize = preferredSize
      if (scrollPrefSize == null || contentHeight != scrollPrefSize.height || contentHeight != maximumSize.height) {
        // Have scroll pane fit to the height of its contents.
        preferredSize = Dimension(scrollPrefSize.width, contentHeight)
        maximumSize = Dimension(Int.MAX_VALUE, contentHeight)
        // Trigger a layout in the parent for the new dimensions.
        invokeLater { parent?.revalidate() }
        return
      }
    }
    super.doLayout()
  }
}

class SectionListModel : ListModel<Section<*>> {

  val sections: MutableList<Section<*>> = mutableListOf()
  private val dataListeners = mutableListOf<ListDataListener>()

  override fun getElementAt(index: Int) = sections[index]

  override fun getSize() = sections.size

  override fun addListDataListener(listener: ListDataListener?) {
    if (!dataListeners.contains(listener ?: return)) {
      dataListeners.add(listener)
    }
  }

  override fun removeListDataListener(listener: ListDataListener?) {
    dataListeners.remove(listener ?: return)
  }

  fun addSection(section: Section<*>) {
    sections += section
    dataListeners.forEach { it.contentsChanged(ListDataEvent(this, ListDataEvent.CONTENTS_CHANGED, 0, sections.size)) }
  }

  fun addSections(newSections: List<Section<*>>) {
    sections += newSections
    dataListeners.forEach { it.contentsChanged(ListDataEvent(this, ListDataEvent.CONTENTS_CHANGED, 0, sections.size)) }
  }

  fun clear() {
    sections.clear()
    dataListeners.forEach { it.contentsChanged(ListDataEvent(this, ListDataEvent.CONTENTS_CHANGED, 0, sections.size)) }
  }
}

interface Section<T> {
  var name: String
  var list: JList<T>
  var header: JComponent
}

class SimpleSection<T>(
  override var name: String = "",
  override var list: JList<T>,
  override var header: JComponent = JLabel(name)
) : Section<T>
