/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.deviceManager.avdmanager

import com.android.sdklib.AndroidVersion
import com.android.sdklib.SdkVersionInfo
import com.android.sdklib.devices.Abi
import com.android.sdklib.repository.meta.DetailsTypes
import com.android.sdklib.repository.targets.SystemImage
import com.android.tools.idea.sdk.wizard.SdkQuickfixUtils
import com.google.common.collect.ComparisonChain
import com.google.common.collect.ImmutableSet
import com.google.common.collect.Lists
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.table.TableView
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.ui.ListTableModel
import java.awt.BorderLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.DefaultListSelectionModel
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.ListSelectionModel
import javax.swing.RowFilter
import javax.swing.RowSorter
import javax.swing.SortOrder
import javax.swing.event.ListSelectionEvent
import javax.swing.event.ListSelectionListener
import javax.swing.table.TableRowSorter

/**
 * Displays a list of system images currently installed and allows selection of one
 */
class SystemImageList : JPanel(), ListSelectionListener {
  private val table = TableView<SystemImageDescription>()
  private val listeners: MutableSet<SystemImageSelectionListener> = hashSetOf()
  private var model: SystemImageListModel? = null
  private var lastSelectedImage: SystemImageDescription? = null

  /**
   * Components which wish to receive a notification when the user has selected an AVD from this
   * table must implement this interface and register themselves through [.addSelectionListener]
   */
  interface SystemImageSelectionListener {
    fun onSystemImageSelected(systemImage: SystemImageDescription?)
  }

  fun setModel(model: SystemImageListModel) {
    this.model = model
    table.setModelAndUpdateColumns(model)
  }

  fun setRowFilter(filter: RowFilter<ListTableModel<SystemImageDescription>?, Int?>) {
    val sorter = table.rowSorter as TableRowSorter<ListTableModel<SystemImageDescription>>
    sorter.sortKeys = listOf(RowSorter.SortKey(1, SortOrder.DESCENDING))
    sorter.rowFilter = filter
    table.rowSorter = sorter
  }

  private fun possiblySwitchEditors(e: MouseEvent) {
    val p = e.point
    val row = table.rowAtPoint(p)
    val col = table.columnAtPoint(p)
    possiblySwitchEditors(row, col)
  }

  private fun possiblySwitchEditors(row: Int, col: Int) {
    if (row != table.editingRow || col != table.editingColumn) {
      if (row != -1 && col != -1 && table.isCellEditable(row, col)) {
        table.editCellAt(row, col)
      }
    }
  }

  fun addSelectionListener(listener: SystemImageSelectionListener) {
    listeners.add(listener)
  }

  fun setSelectedImage(selectedImage: SystemImageDescription?) {
    lastSelectedImage = selectedImage
    updateSelection(selectedImage)
  }

  private fun updateSelection(selectedImage: SystemImageDescription?) {
    if (selectedImage != null) {
      table.setSelection(setOf(selectedImage))
    }
    else {
      table.clearSelection()
    }
  }

  fun makeListCurrent() {
    notifySelectionChange()
  }

  /**
   * Restore the selection to the last selected system image.
   * If the last selected system image cannot be found choose the best image in the list.
   * @param partlyDownloaded if true we are restoring after the local images has been reloaded but not the remote.
   * When this is the case do NOT fallback to the best image if the last selection could not be found,
   * instead wait for the remote images and keep looking for the current last selected system image.
   * @param defaultSystemImage System image to use if a previous image was not already selected
   */
  fun restoreSelection(partlyDownloaded: Boolean, defaultSystemImage: SystemImageDescription?) {
    var best: SystemImageDescription? = null
    val toFind = if (lastSelectedImage != null) lastSelectedImage else defaultSystemImage
    for (index in 0 until table.rowCount) {
      val desc = model!!.getRowValue(table.convertRowIndexToModel(index))
      if (desc == toFind) {
        best = desc
        break
      }
      if (!partlyDownloaded && isBetter(desc, best)) {
        best = desc
      }
    }
    updateSelection(best)
    lastSelectedImage = if (partlyDownloaded) toFind else best
  }

  private fun installForDevice() {
    val apiLevel = SdkVersionInfo.HIGHEST_KNOWN_STABLE_API
    val requestedPackages = listOf(SystemImage.DEFAULT_TAG, SystemImage.WEAR_TAG, SystemImage.TV_TAG, SystemImage.AUTOMOTIVE_TAG)
      .map { tag -> DetailsTypes.getSysImgPath(null, AndroidVersion(apiLevel, null), tag, Abi.X86.toString()) }
    val dialog = SdkQuickfixUtils.createDialogForPaths(this, requestedPackages, false) ?: return
    dialog.show()
    model!!.refreshImages(true)
  }

  /**
   * This class implements the table selection interface and passes the selection events on to its listeners.
   */
  override fun valueChanged(event: ListSelectionEvent?) {
    if (event == null || event.valueIsAdjusting || model!!.isUpdating) {
      return
    }
    lastSelectedImage = table.selectedObject
    notifySelectionChange()
  }

  private fun notifySelectionChange() {
    for (listener in listeners) {
      listener.onSystemImageSelected(lastSelectedImage)
    }
  }

  companion object {
    private val DEFAULT_ABI_SORT_ORDER = mapOf(
      Abi.MIPS64 to 0,
      Abi.MIPS to 1,
      Abi.ARM64_V8A to 2,
      Abi.ARMEABI to 3,
      Abi.ARMEABI_V7A to 4,
      Abi.X86_64 to 5,
      Abi.X86 to 6
    )

    private fun isBetter(image: SystemImageDescription, bestSoFar: SystemImageDescription?): Boolean {
      return bestSoFar == null || 0 < ComparisonChain.start()
        .compareTrueFirst(image.isRemote, bestSoFar.isRemote)
        .compare(abiRank(image), abiRank(bestSoFar))
        .compare(image.version, bestSoFar.version)
        .compareFalseFirst(image.tag == SystemImage.GOOGLE_APIS_TAG, bestSoFar.tag == SystemImage.GOOGLE_APIS_TAG)
        .result()
    }

    private fun abiRank(image: SystemImageDescription): Int {
      val abi = Abi.getEnum(image.abiType)
      return if (abi != null && DEFAULT_ABI_SORT_ORDER.containsKey(abi)) {
        DEFAULT_ABI_SORT_ORDER.getValue(abi)
      }
      else {
        -1
      }
    }
  }

  init {
    val selectionModel: ListSelectionModel = object : DefaultListSelectionModel() {
      override fun setSelectionInterval(index0: Int, index1: Int) {
        super.setSelectionInterval(index0, index1)
        table.cellEditor?.cancelCellEditing()
        table.repaint()
        possiblySwitchEditors(index0, 0)
      }
    }
    selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
    table.selectionModel = selectionModel
    table.rowSelectionAllowed = true
    val editorListener: MouseAdapter = object : MouseAdapter() {
      override fun mouseMoved(e: MouseEvent) {
        possiblySwitchEditors(e)
      }

      override fun mouseEntered(e: MouseEvent) {
        possiblySwitchEditors(e)
      }

      override fun mouseExited(e: MouseEvent) {
        possiblySwitchEditors(e)
      }

      override fun mouseClicked(e: MouseEvent) {
        possiblySwitchEditors(e)
      }
    }
    table.addMouseListener(editorListener)
    table.addMouseMotionListener(editorListener)
    layout = BorderLayout()
    add(ScrollPaneFactory.createScrollPane(table), BorderLayout.CENTER)
    // TODO(qumeric): show this button
    val installLatestVersionButton = JButton("Install Latest Version...").apply {
      addActionListener { installForDevice() }
    }
    table.selectionModel.addListSelectionListener(this)
    table.emptyText.text = "No System Images available. Are you connected to the internet?"
  }
}