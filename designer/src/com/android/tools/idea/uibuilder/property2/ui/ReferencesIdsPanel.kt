/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.property.ui

import com.android.tools.idea.uibuilder.property.NlPropertyItem
import com.android.tools.property.ptable2.PFormTable
import com.intellij.util.ui.JBUI
import icons.StudioIcons
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.awt.dnd.DragSource
import javax.swing.DropMode
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.LayoutFocusTraversalPolicy
import javax.swing.ListSelectionModel
import javax.swing.TransferHandler
import javax.swing.table.AbstractTableModel

/**
 * Panel showing the list of references for ConstraintHelpers
 */
class ReferencesIdsPanel : JPanel(BorderLayout()) {
  private var table: PFormTable // supports keyboard navigation
  private lateinit var referencesIds: NlPropertyItem

  private val dataModel = DataModel(this)

  init {
    focusTraversalPolicy = LayoutFocusTraversalPolicy()
    table = PFormTable(dataModel)
    table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    table.autoResizeMode = JTable.AUTO_RESIZE_LAST_COLUMN
    table.dragEnabled = true;
    table.dropMode = DropMode.INSERT_ROWS;
    table.transferHandler = ReferencesTransferHandler(table);
    add(table)
    updateUI()
  }

  override fun updateUI() {
    super.updateUI()
    if (table != null) {
      val reorderIcon =  StudioIcons.Common.REORDER
      var padding = JBUI.scale(4) * 2
      var size = reorderIcon.iconWidth + padding
      table.columnModel.getColumn(0).maxWidth = size
    }
  }

  /**
   * JTable data model for the panel
   */
  class DataModel(var panel: ReferencesIdsPanel) : AbstractTableModel() {
    private val references = ArrayList<String>()

    override fun getColumnCount(): Int {
      return 2
    }

    override fun getRowCount(): Int {
      return references.size
    }

    override fun getValueAt(row: Int, col: Int): Any {
      if (col == 0) {
        return StudioIcons.Common.REORDER
      }
      return references[row]
    }

    override fun getColumnClass(columnIndex: Int): Class<*>? {
      return if (columnIndex == 0) Icon::class.java else Any::class.java
    }

    /**
     * Reorder elements
     */
    fun moveRow(sourceRowIndex: Int, targetRowIndex: Int) {
      val element = references[sourceRowIndex]
      references.add(targetRowIndex, element)
      var rowIndex = sourceRowIndex
      if (targetRowIndex < sourceRowIndex) {
        rowIndex++
      }
      references.removeAt(rowIndex)
      fireTableDataChanged()
      panel.updateReferences(modifiedReferences(references))
    }

    /**
     * Rebuild the constraint_referenced_ids string from the current data model
     */
    private fun modifiedReferences(references: ArrayList<String>): String {
      val builder = StringBuilder()
      for (i in 0 until references.size) {
        builder.append(references[i])
        if (i != references.size - 1) {
          builder.append(',')
        }
      }
      return builder.toString()
    }

    /**
     * Update the model from a given constraint_referenced_ids string
     * containing views' ids comma-separated.
     */
    fun updateModel(referencesString: String?) {
      references.clear()
      if (referencesString != null) {
        val refs = referencesString.split(",")
        refs.forEach {
          val ref = it.trim()
          if (ref.isNotEmpty()) {
            references.add(ref)
          }
        }
      }
      fireTableDataChanged()
    }

    fun removeRow(selectedRowIndex: Int) {
      if (selectedRowIndex >= 0 && selectedRowIndex < references.size) {
        references.removeAt(selectedRowIndex)
        fireTableDataChanged()
        panel.updateReferences(modifiedReferences(references))
      }
    }

    fun addReference(s: String) {
      references.add(s)
      fireTableDataChanged()
      panel.updateReferences(modifiedReferences(references))
    }

    fun getCurrentReferences(): ArrayList<String> {
      return references
    }
  }

  /**
   * DnD transferable (allowing items reordering)
   */
  class ReferenceIdTransferable(reference: String, row: Int) : Transferable {
    val reference = Pair(reference, row)

    override fun getTransferData(flavor: DataFlavor?): Any {
      if (REFERENCE_ID_FLAVOR.equals(flavor)) {
        return reference
      }
      throw UnsupportedFlavorException(flavor)
    }

    override fun isDataFlavorSupported(flavor: DataFlavor?): Boolean {
      return REFERENCE_ID_FLAVOR.equals(flavor)
    }

    override fun getTransferDataFlavors(): Array<DataFlavor> {
      return arrayOf(REFERENCE_ID_FLAVOR)
    }

    companion object {
      val REFERENCE_ID_FLAVOR = DataFlavor(ReferenceIdTransferable::class.java, "Referenced View")
    }
  }

  /**
   * DnD handling for rows reordering
   */
  class ReferencesTransferHandler(private val table: JTable) : TransferHandler() {

    override fun createTransferable(c: JComponent): Transferable {
      val reference = table.getValueAt(table.selectedRow, 1) as String
      return ReferenceIdTransferable(reference, table.selectedRow)
    }

    override fun canImport(info: TransferSupport): Boolean {
      val valid = info.component === table
                  && info.isDrop
                  && info.isDataFlavorSupported(ReferenceIdTransferable.REFERENCE_ID_FLAVOR)
      table.cursor = if (valid) DragSource.DefaultMoveDrop else DragSource.DefaultMoveNoDrop
      return valid
    }

    override fun getSourceActions(c: JComponent): Int {
      // Only supports reordering
      return MOVE
    }

    override fun importData(info: TransferSupport): Boolean {
      val target = info.component as JTable
      target.cursor = Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR)

      val dl = info.dropLocation as JTable.DropLocation
      var targetRowIndex = dl.row
      val max = table.model.rowCount

      if (targetRowIndex < 0 || targetRowIndex > max) {
        targetRowIndex = max
      }

      val payload = (info.transferable.getTransferData(ReferenceIdTransferable.REFERENCE_ID_FLAVOR) as Pair<*, *>)
      val sourceRowIndex = payload.second as Int
      if (sourceRowIndex != -1 && sourceRowIndex != targetRowIndex) {
        (table.model as DataModel).moveRow(sourceRowIndex, targetRowIndex)
        if (targetRowIndex > sourceRowIndex) {
          targetRowIndex--
        }
        target.selectionModel.addSelectionInterval(targetRowIndex, targetRowIndex)
        return true
      }
      return false
    }

    override fun exportDone(c: JComponent, t: Transferable, act: Int) {
      if (act == MOVE || act == NONE) {
        table.cursor = Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR)
      }
    }
  }

  //////////////////////////////////////////////////////
  // Dealing with NelePropertyItem
  //////////////////////////////////////////////////////

  fun setProperty(property: NlPropertyItem) {
    referencesIds = property
    dataModel.updateModel(referencesIds.value)
  }

  fun updateReferences(references : String) {
    referencesIds.value = references
  }

  fun getDataModel(): DataModel {
    return dataModel
  }

  fun getSelectedRowIndex() : Int {
    return table.selectedRow
  }

  fun getListIds(): ArrayList<String> {
    val ids = arrayListOf<String>()
    val currentIds = dataModel.getCurrentReferences()
    if (referencesIds.components.isNotEmpty()) {
      val component = referencesIds.components[0]
      val helperId = component.id
      val candidates = component.parent?.children
      candidates?.forEach { it.id?.let { id ->
        if (id != helperId && !currentIds.contains(id)) ids.add(id)
      }}
    }
    return ids
  }

  //////////////////////////////////////////////////////
  // Simple utility to check the UI
  //////////////////////////////////////////////////////

  companion object {

    @JvmStatic
    fun main(args: Array<String>) {
      val f = JFrame("test table")
      f.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
      val base = ReferencesIdsPanel()
      base.dataModel.updateModel("test,image1,image2,")
      f.contentPane = base
      f.setBounds(100, 100, 280, 400)
      f.isVisible = true
    }

  }
}