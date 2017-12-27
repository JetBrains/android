/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.sampledata

import com.android.ide.common.resources.sampledata.CSVReader
import com.intellij.codeHighlighting.BackgroundEditorHighlighter
import com.intellij.ide.structureView.StructureViewBuilder
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorLocation
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.psi.*
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.table.JBTable
import java.beans.PropertyChangeListener
import java.io.StringReader
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.table.AbstractTableModel

private class CsvTableModel(val myEditor: CsvEditor, val myFile: PsiPlainTextFile, val myProject: Project) : AbstractTableModel() {
  lateinit var myHeaders: Array<String>
  lateinit var myContent: MutableList<Array<String>>

  init {
    reloadFromFile()

    // Listen for file changes to reload the model
    PsiManager.getInstance(myProject).addPsiTreeChangeListener(object : PsiTreeChangeAdapter() {
      override fun childAdded(event: PsiTreeChangeEvent) {
        if (event.file == myFile) reloadFromFile()
      }

      override fun childRemoved(event: PsiTreeChangeEvent) {
        if (event.file == myFile) reloadFromFile()
      }

      override fun childReplaced(event: PsiTreeChangeEvent) {
        if (event.file == myFile) reloadFromFile()
      }

      override fun childrenChanged(event: PsiTreeChangeEvent) {
        if (event.file == myFile) reloadFromFile()
      }

      override fun childMoved(event: PsiTreeChangeEvent) {
        if (event.file == myFile) reloadFromFile()
      }
    }, myEditor)

  }

  private fun reloadFromFile() {
    val csvReader = CSVReader(StringReader(myFile.text))
    val headers = csvReader.readNext()

    if (headers != null) {
      myHeaders = headers
      myContent = csvReader.readAll()
    }
    else {
      // File is empty
      myHeaders = Array(0, {""})
      myContent = ArrayList<Array<String>>()
    }

    fireTableDataChanged()
  }

  override fun getRowCount(): Int = myContent.size

  override fun getColumnName(columnIndex: Int): String = myHeaders[columnIndex]
  override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = myHeaders.isNotEmpty()
  override fun getColumnClass(columnIndex: Int): Class<*> = String::class.java
  override fun setValueAt(aValue: Any?, rowIndex: Int, columnIndex: Int) {
    val oldValue = getValueAt(rowIndex, columnIndex)
    if (oldValue == aValue) {
      return
    }

    if (columnIndex >= myContent[rowIndex].size) {
      // The row needs more columns
      myContent[rowIndex] = myContent[rowIndex].copyOf(myHeaders.size).map { it ?: ""}.toTypedArray()
    }

    myContent[rowIndex][columnIndex] = aValue as String

    val builder = StringBuilder().apply {
      append(myHeaders.joinToString(",")).append("\n")
      myContent.map { it.joinToString(",") }.forEach({ append(it).append("\n") })
    }

    val document = PsiDocumentManager.getInstance(myProject).getDocument(myFile)

    WriteCommandAction.runWriteCommandAction(myProject, "Set CSV value", null, Runnable {
      document?.setText(builder)
    }, myFile)
  }

  override fun getColumnCount(): Int = myHeaders.size
  override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
    if (rowIndex >= myContent.size || columnIndex >= myContent[rowIndex].size) {
      return ""
    }

    return myContent[rowIndex][columnIndex]
  }

  fun insertRow(after: Int) {
    myContent.add(after + 1, Array(myHeaders.size, { "" }))
    fireTableRowsInserted(after, after)
  }

  fun removeRow(selectedRow: Int) {
    myContent.removeAt(selectedRow)
    fireTableRowsDeleted(selectedRow, selectedRow)
  }
}

/**
 * Simple editor for sample data CSV files
 */
class CsvEditor(private val myFile: PsiPlainTextFile, private val myProject: Project) : UserDataHolderBase(), FileEditor {
  private var myEditorPanel: JPanel? = null

  override fun getComponent(): JComponent {
    if (myEditorPanel == null) {
      val model = CsvTableModel(this, myFile, myProject)
      val table = JBTable(model).apply {
        setShowColumns(true)
        setEnableAntialiasing(true)
        setShowGrid(true)
        isStriped = true
        autoResizeMode = JTable.AUTO_RESIZE_OFF
      }
      val toolbar = ToolbarDecorator.createDecorator(table)
      toolbar.setAddAction {
        model.insertRow(table.selectedRow)
      }
      toolbar.setRemoveAction {
        model.removeRow(table.selectedRow)
      }
      myEditorPanel = toolbar.createPanel()
    }
    return myEditorPanel!!
  }

  override fun getPreferredFocusedComponent(): JComponent? = myEditorPanel
  override fun getName() = "CSV Editor"
  override fun setState(state: FileEditorState) {}
  override fun dispose() {}
  override fun selectNotify() {}
  override fun deselectNotify() {}
  override fun isValid() = myFile.isValid
  override fun isModified() = false
  override fun addPropertyChangeListener(listener: PropertyChangeListener) {}
  override fun removePropertyChangeListener(listener: PropertyChangeListener) {}
  override fun getBackgroundHighlighter(): BackgroundEditorHighlighter? = null
  override fun getCurrentLocation(): FileEditorLocation? = null
  override fun getStructureViewBuilder(): StructureViewBuilder? = null
}
