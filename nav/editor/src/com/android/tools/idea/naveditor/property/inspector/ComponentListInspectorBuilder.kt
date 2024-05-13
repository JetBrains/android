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
package com.android.tools.idea.naveditor.property.inspector

import com.android.tools.adtui.model.stdui.ValueChangedListener
import com.android.tools.idea.common.editor.SPLIT_TEXT_EDITOR_KEY
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.naveditor.property.ui.ComponentList
import com.android.tools.idea.uibuilder.property.NlPropertyItem
import com.android.tools.property.panel.api.InspectorBuilder
import com.android.tools.property.panel.api.InspectorLineModel
import com.android.tools.property.panel.api.InspectorPanel
import com.android.tools.property.panel.api.PropertiesTable
import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.components.JBList
import com.intellij.openapi.application.invokeLater
import java.awt.event.HierarchyEvent
import java.awt.event.HierarchyListener
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.DefaultListModel

/**
 * Adds a ComponentList component to an [InspectorPanel] to display groups of subtags in a list format within an expandable title.
 * Assumes that the currently selected component is a destination.
 * Parameters:
 * [tagName]: the tag name of the child elements to be displayed
 * [title]: the caption for the expandable title
 * [cellRenderer]: the cell renderer to be used for the list items
 */
abstract class ComponentListInspectorBuilder(val tagName: String,
                                             private val cellRenderer: ColoredListCellRenderer<NlComponent>)
  : InspectorBuilder<NlPropertyItem> {
  abstract fun title(component: NlComponent): String

  protected open fun addActionText(component: NlComponent): String = "Add Component"

  protected open fun deleteActionText(component: NlComponent): String = "Delete Component"

  override fun attachToInspector(inspector: InspectorPanel, properties: PropertiesTable<NlPropertyItem>) {
    val component = properties.first?.components?.singleOrNull() ?: return
    if (!isApplicable(component)) {
      return
    }

    val model = DefaultListModel<NlComponent>()
    refresh(component, model)

    val componentList = ComponentList(model, cellRenderer)
    val list = componentList.list

    val addAction = AddAction(this, component, model, addActionText(component))
    val deleteAction = DeleteAction(this, component, model, list, deleteActionText(component))
    val actions = listOf(addAction, deleteAction)

    val titleModel = inspector.addExpandableTitle(title(component), model.size > 0, actions)
    addAction.model = titleModel
    deleteAction.model = titleModel

    list.addMouseListener(object : MouseAdapter() {
      override fun mouseClicked(e: MouseEvent) {
        if (e.clickCount == 2 && list.selectedValuesList.size == 1) {
          onEdit(list.selectedValue)
          titleModel.refresh()
        }
      }
    })

    list.addListSelectionListener {
      onSelectionChanged(list)
    }

    list.addKeyListener(object : KeyAdapter() {
      override fun keyTyped(e: KeyEvent?) {
        if (e?.keyChar != '\n' && e?.keyCode != KeyEvent.VK_ENTER) {
          return
        }

        if (list.selectedValuesList.size == 1) {
          onEdit(list.selectedValue)
          titleModel.refresh()
        }
      }
    })

    val lineModel = inspector.addComponent(componentList, titleModel)
    lineModel.addValueChangedListener { refresh(component, model) }

    val caretListener = object : CaretListener {
      override fun caretAdded(event: CaretEvent) {
        caretPositionChanged(event)
      }

      override fun caretPositionChanged(event: CaretEvent) {
        val nlModel = component.model
        val offset = event.caret?.offset ?: return
        val view = nlModel.treeReader.findByOffset(offset).firstOrNull() ?: nlModel.treeReader.components.firstOrNull() ?: return
        list.setSelectedValue(view, true)
        if (list.selectedIndex >= 0 && !titleModel.expanded) {
          // If the section is collapsed we need to try again once it's open.
          lateinit var listener: ValueChangedListener
          listener = ValueChangedListener {
            titleModel.removeValueChangedListener(listener)
            invokeLater { caretPositionChanged(event) }
          }
          titleModel.addValueChangedListener(listener)
          titleModel.expanded = true
        }
      }
    }

    val initCaret = { editor: TextEditor ->
      editor.editor.caretModel.addCaretListener(caretListener)
      val currentCaret = editor.editor.caretModel.currentCaret
      caretListener.caretAdded(CaretEvent(currentCaret, currentCaret.logicalPosition, currentCaret.logicalPosition))
    }

    val textEditor = DataManager.getInstance().getDataContext(componentList).getData(SPLIT_TEXT_EDITOR_KEY)
    if (textEditor != null) {
      initCaret(textEditor)
    }
    else {
      // We won't be able to get the text editor if the nav editor is just being opened. Wait until it's hooked up to try.
      lateinit var hierarchyListener: HierarchyListener
      hierarchyListener = HierarchyListener { event ->
        if (event.changeFlags and HierarchyEvent.PARENT_CHANGED.toLong() > 0) {
          list.removeHierarchyListener(hierarchyListener)
          DataManager.getInstance().getDataContext(componentList).getData(SPLIT_TEXT_EDITOR_KEY)?.let { initCaret(it) }
        }
      }
      list.addHierarchyListener(hierarchyListener)
    }
    // Remove the CaretListener when this component is removed.
    list.addHierarchyListener {
      if (it.changeFlags and HierarchyEvent.PARENT_CHANGED.toLong() > 0 &&
          componentList.parent == it.changed &&
          !it.changedParent.components.contains(it.changed)) {
        textEditor?.editor?.caretModel?.removeCaretListener(caretListener)
      }
    }
  }

  protected abstract fun onAdd(parent: NlComponent)
  protected abstract fun onEdit(component: NlComponent)
  protected open fun onSelectionChanged(list: JBList<NlComponent>) {}
  protected abstract fun isApplicable(component: NlComponent): Boolean

  private fun refresh(component: NlComponent, model: DefaultListModel<NlComponent>) {
    model.clear()
    for (item in component.children.filter { it.tagName == tagName }) {
      model.addElement(item)
    }
  }

  private class AddAction(private val builder: ComponentListInspectorBuilder,
                          private val component: NlComponent,
                          private val listModel: DefaultListModel<NlComponent>,
                          text: String)
    : AnAction(text, text, AllIcons.General.Add) {
    var model: InspectorLineModel? = null
    override fun actionPerformed(e: AnActionEvent) {
      builder.onAdd(component)
      builder.refresh(component, listModel)
      model?.refresh()
      model?.expanded = listModel.size > 0
    }
  }

  private class DeleteAction(private val builder: ComponentListInspectorBuilder,
                             private val component: NlComponent,
                             private val listModel: DefaultListModel<NlComponent>,
                             private val list: JBList<NlComponent>,
                             text: String)
    : AnAction(text, text, AllIcons.General.Remove) {
    var model: InspectorLineModel? = null
    override fun actionPerformed(e: AnActionEvent) {
      component.model.delete(list.selectedValuesList)
      builder.refresh(component, listModel)
      model?.refresh()
      model?.expanded = listModel.size > 0
    }
  }
}
