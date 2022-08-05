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
package com.android.tools.idea.emulator.dialogs

import com.android.annotations.concurrency.Slow
import com.android.annotations.concurrency.UiThread
import com.android.emulator.control.SnapshotPackage
import com.android.tools.adtui.ImageUtils
import com.android.tools.adtui.ui.ImagePanel
import com.android.tools.adtui.util.getHumanizedSize
import com.android.tools.idea.concurrency.AndroidIoManager
import com.android.tools.idea.concurrency.getDoneOrNull
import com.android.tools.idea.emulator.EmptyStreamObserver
import com.android.tools.idea.emulator.EmulatorController
import com.android.tools.idea.emulator.EmulatorSettings
import com.android.tools.idea.emulator.EmulatorSettings.SnapshotAutoDeletionPolicy
import com.android.tools.idea.emulator.EmulatorView
import com.google.common.html.HtmlEscapers
import com.google.common.util.concurrent.Futures.immediateFuture
import com.intellij.CommonBundle
import com.intellij.execution.runners.ExecutionUtil.getLiveIndicator
import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.actionSystem.ActionToolbarPosition
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.DialogWrapper.IdeModalityType
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.DimensionService
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.ui.AnActionButton
import com.intellij.ui.BooleanTableCellEditor
import com.intellij.ui.BooleanTableCellRenderer
import com.intellij.ui.DoubleClickListener
import com.intellij.ui.JBColor
import com.intellij.ui.LayeredIcon
import com.intellij.ui.TableUtil
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.DialogManager
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.Label
import com.intellij.ui.components.dialog
import com.intellij.ui.components.htmlComponent
import com.intellij.ui.layout.panel
import com.intellij.ui.scale.JBUIScale
import com.intellij.ui.table.TableView
import com.intellij.util.IconUtil
import com.intellij.util.concurrency.AppExecutorUtil.createBoundedApplicationPoolExecutor
import com.intellij.util.text.JBDateFormat
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.JBImageIcon
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.ListTableModel
import com.intellij.util.ui.components.BorderLayoutPanel
import icons.StudioIcons
import org.jetbrains.annotations.NonNls
import org.jetbrains.kotlin.utils.SmartSet
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.Font
import java.awt.event.ActionEvent
import java.awt.event.MouseEvent
import java.io.IOException
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Path
import java.text.Collator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Callable
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantReadWriteLock
import javax.imageio.ImageIO
import javax.swing.AbstractAction
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.JTextField
import javax.swing.ListSelectionModel
import javax.swing.RowSorter
import javax.swing.SortOrder
import javax.swing.SwingConstants
import javax.swing.event.TableModelEvent
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.JTableHeader
import javax.swing.table.TableCellEditor
import javax.swing.table.TableCellRenderer
import javax.swing.table.TableModel
import javax.swing.table.TableRowSorter
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.math.max

/**
 * Dialog for managing emulator snapshots.
 */
internal class ManageSnapshotsDialog(
  private val emulator: EmulatorController,
  private val emulatorView: EmulatorView
) {

  private val snapshotTableModel = SnapshotTableModel()
  private val snapshotTable = SnapshotTable(snapshotTableModel)
  private val takeSnapshotButton = JButton("Take Snapshot").apply {
    addActionListener { createSnapshot() }
  }
  private val runningOperationLabel = JBLabel().apply {
    isVisible = false
    name = "runningOperationLabel"
  }
  private val snapshotImagePanel = ImagePanel(true)
  private val selectionStateLabel = Label("No snapshots selected").apply {
    name = "selectionStateLabel"
    verticalTextPosition = SwingConstants.CENTER
    horizontalAlignment = SwingConstants.CENTER
  }
  private val previewPanel = BorderLayoutPanelWithPreferredSize(270, 100)
  private val snapshotInfoPanel = htmlComponent(lineWrap = true)
  private val coldBootCheckBox = JBCheckBox("Start without using a snapshot (cold boot)").apply {
    addItemListener {
      if (isSelected != snapshotTableModel.isColdBoot) {
        val bootSnapshotRow = if (isSelected) snapshotTableModel.bootSnapshotIndex() else 0
        snapshotTableModel.setBootSnapshot(bootSnapshotRow, !isSelected)
      }
    }
  }
  /** An invisible text field used to trigger clearing of the error messages. See the [clearError] function. */
  private var validationText = JTextField()
  /** Dialog wrapper. Not null when and only when the dialog is shown. */
  private var dialogManager: DialogWrapper? = null
  private val snapshotManager = SnapshotManager(emulator)
  private val backgroundExecutor = createBoundedApplicationPoolExecutor("ManageSnapshotsDialog", 1)
  /**
   * Used to avoid deleting a snapshot folder while accessing files under that folder.
   * Particularly important on Windows.
   */
  private val snapshotIoLock = ReentrantReadWriteLock()

  init {
    // Install a double-click listener to edit snapshot on double click.
    snapshotTable.apply {
      object : DoubleClickListener() {
        override fun onDoubleClick(event: MouseEvent): Boolean {
          val point = event.point
          val row: Int = rowAtPoint(point)
          if (row <= QUICK_BOOT_SNAPSHOT_MODEL_ROW || columnAtPoint(point) < 0) {
            return false
          }
          selectionModel.setSelectionInterval(row, row)
          editSnapshot()
          return true
        }
      }.installOn(this)
    }
  }

  /**
   * Creates contents of the dialog.
   */
  private fun createPanel(): DialogPanel {
    return panel {
      row("Snapshots:") {}
      row {
        cell(isVerticalFlow = true) {
          component(createTablePanel()).constraints(growX, growY, pushX, pushY)
        }

        cell(isVerticalFlow = true) {
          component(previewPanel).constraints(growX, growY, pushX)
          component(snapshotInfoPanel).constraints(growX)
        }
      }
      row {
        cell {
          component(takeSnapshotButton)
          component(runningOperationLabel)
        }
      }
      row {
        component(coldBootCheckBox)
      }
    }.apply {
      snapshotTable.selectionModel.addListSelectionListener {
        clearError()
        updateSelectionState()
      }

      updateSelectionState()
    }
  }

  private fun updateSelectionState() {
    previewPanel.removeAll()
    val count = snapshotTable.selectedRowCount
    if (count == 1) {
      previewPanel.addToCenter(snapshotImagePanel)
      snapshotTable.selectedObject?.let { updateSnapshotDetails(it) }
      snapshotInfoPanel.isVisible = true
    }
    else {
      previewPanel.addToCenter(selectionStateLabel)
      selectionStateLabel.text = if (count == 0) "No snapshots selected" else "$count snapshots selected"
      snapshotInfoPanel.isVisible = false
    }
  }

  private fun updateSnapshotDetails(snapshot: SnapshotInfo) {
    snapshotImagePanel.image = snapshotIoLock.read {
      try {
        ImageIO.read(snapshot.screenshotFile.toFile())
      }
      catch (_: IOException) {
        null
      }
    }
    val htmlEscaper = HtmlEscapers.htmlEscaper()
    val name = htmlEscaper.escape(snapshot.displayName)
    val size = getHumanizedSize(snapshot.sizeOnDisk)
    val creationTime = JBDateFormat.getFormatter().formatDateTime(snapshot.creationTime).replace(",", "")
    val folderName = htmlEscaper.escape(snapshot.snapshotFolder.fileName.toString())
    val attributeSection = if (snapshot.creationTime == 0L) "Not created yet" else "Created $creationTime, $size"
    val fileSection = if (snapshot.isQuickBoot) "" else "<br>File: $folderName"
    val errorSection = if (snapshot.isCompatible) ""
        else "<br><font color = ${JBColor.RED.toHtmlString()}>Incompatible with the current configuration</font>"
    val descriptionSection = if (snapshot.description.isEmpty()) "" else "<br><br>${htmlEscaper.escape(snapshot.description)}"
    snapshotInfoPanel.apply {
      text = "<html><b>${name}</b><br>${attributeSection}${fileSection}${errorSection}${descriptionSection}</html>"
      val fontMetrics = getFontMetrics(font)
      val wrappedDescriptionLines = if (width == 0) 0 else fontMetrics.stringWidth(snapshot.description) / width
      preferredSize = Dimension(0, fontMetrics.height * (countLineBreaks(text) + 1 + wrappedDescriptionLines))
    }
  }

  private fun countLineBreaks(html: String): Int {
    var count = 0
    var offset = 0
    while (true) {
      val br = "<br>"
      offset = html.indexOf(br, offset)
      if (offset < 0) {
        return count
      }
      count++
      offset += br.length
    }
  }

  private fun Color.toHtmlString(): String {
    return (rgb and 0xFFFFFF).toString(16)
  }

  private fun createTablePanel(): JPanel {
    return BorderLayoutPanelWithPreferredSize(500, 450).apply {
      add(
        ToolbarDecorator.createDecorator(snapshotTable)
          .setEditAction {
            editSnapshot()
          }
          .setRemoveAction {
            removeSnapshots()
          }
          .setAddAction(null)
          .setMoveDownAction(null)
          .setMoveUpAction(null)
          .addExtraAction(LoadSnapshotAction())
          .setEditActionUpdater {
            !snapshotTable.selectionModel.isSelectedIndex(QUICK_BOOT_SNAPSHOT_MODEL_ROW) &&
            snapshotTable.selectedObject?.isCompatible ?: false
          }
          .setRemoveActionUpdater { !snapshotTable.selectionModel.isSelectedIndex(QUICK_BOOT_SNAPSHOT_MODEL_ROW) }
          .setToolbarPosition(ActionToolbarPosition.BOTTOM)
          .setButtonComparator("Load Snapshot", "Edit", "Remove")
          .createPanel()
      )
    }
  }

  private fun createSnapshot() {
    clearError()
    val snapshotId = composeSnapshotId(snapshotTableModel.items)
    val completionTracker = object : EmptyStreamObserver<SnapshotPackage>() {

      init {
        takeSnapshotButton.transferFocusBackward() // Transfer focus to the table.
        takeSnapshotButton.isEnabled = false // Disable the button temporarily.
        startLongOperation("Saving snapshot...")
        emulatorView.showLongRunningOperationIndicator("Saving state...")
      }

      override fun onCompleted() {
        invokeLaterWhileDialogIsShowing {
          finished()
        }
        backgroundExecutor.submit {
          val snapshot = snapshotIoLock.read { snapshotManager.readSnapshotInfo(snapshotId) }
          invokeLaterWhileDialogIsShowing {
            if (snapshot == null) {
              showError()
            } else {
              snapshotTableModel.addRow(snapshot)
              snapshotTable.selection = listOf(snapshot)
              TableUtil.scrollSelectionToVisible(snapshotTable)
            }
          }
        }
      }

      override fun onError(t: Throwable) {
        invokeLaterWhileDialogIsShowing {
          showError()
          finished()
        }
      }

      @UiThread
      private fun finished() {
        emulatorView.hideLongRunningOperationIndicator()
        takeSnapshotButton.isEnabled = true // Re-enable the button.
        endLongOperation()
      }

      @UiThread
      private fun showError() {
        showError("Unable to create a snapshot")
      }
    }

    emulator.saveSnapshot(snapshotId, completionTracker)
  }

  private fun loadSnapshot() {
    clearError()
    val snapshotToLoad = snapshotTable.selectedObject ?: return
    val selectedRow = snapshotTable.selectedRow
    val errorHandler = object : EmptyStreamObserver<SnapshotPackage>() {

      init {
        startLongOperation("Loading snapshot...")
        emulatorView.showLongRunningOperationIndicator("Loading snapshot...")
      }

      override fun onNext(response: SnapshotPackage) {
        if (response.success) {
          invokeLaterWhileDialogIsShowing {
            snapshotTableModel.setLoadedLastSnapshot(snapshotTable.convertRowIndexToModel(selectedRow))
          }
        }
        else {
          val error = response.err.toString(UTF_8)
          val detail = if (error.isEmpty()) "" else " - $error"
          invokeLaterWhileDialogIsShowing {
            showError("""Error loading snapshot "${snapshotToLoad.displayName}"$detail""")
          }
        }
      }

      override fun onCompleted() {
        finished()
      }

      override fun onError(t: Throwable) {
        finished()
        invokeLaterWhileDialogIsShowing {
          showError("""Error loading snapshot. See the error log""")
        }
      }

      private fun finished() {
        invokeLaterWhileDialogIsShowing {
          endLongOperation()
          emulatorView.hideLongRunningOperationIndicator()
        }
      }
    }

    emulator.loadSnapshot(snapshotToLoad.snapshotId, errorHandler)
  }

  private fun editSnapshot() {
    TableUtil.stopEditing(snapshotTable)
    clearError()
    val selectedIndex = snapshotTable.convertRowIndexToModel(snapshotTable.selectedRow)
    if (selectedIndex < 0) {
      return
    }
    val snapshot = snapshotTable.selectedObject ?: return
    if (snapshot.isQuickBoot || !snapshot.isCompatible) {
      return
    }
    val dialog = EditSnapshotDialog(snapshot.displayName, snapshot.description, snapshot == snapshotTableModel.bootSnapshot)
    if (dialog.createWrapper(parent = snapshotTable).showAndGet()) {
      if (dialog.snapshotName != snapshot.displayName || dialog.snapshotDescription != snapshot.description) {
        val updatedSnapshot = SnapshotInfo(snapshot, dialog.snapshotName, dialog.snapshotDescription)
        val selectionState = SelectionState(snapshotTable)
        snapshotTableModel.removeRow(selectedIndex)
        snapshotTableModel.insertRow(selectedIndex, updatedSnapshot)
        selectionState.restoreSelection()
        backgroundExecutor.submit {
          snapshotIoLock.read { snapshotManager.saveSnapshotProto(updatedSnapshot.snapshotFolder, updatedSnapshot.snapshot) }
        }
      }
      snapshotTableModel.setBootSnapshot(selectedIndex, dialog.useToBoot)
    }
  }

  private fun removeSnapshots() {
    TableUtil.stopEditing(snapshotTable)
    clearError()
    val selectionState = SelectionState(snapshotTable)
    val selectionModel = snapshotTable.selectionModel
    val minSelectionIndex = selectionModel.minSelectionIndex
    val foldersToDelete = mutableListOf<Path>()
    for (row in selectionModel.maxSelectionIndex downTo selectionModel.minSelectionIndex) {
      if (selectionModel.isSelectedIndex(row)) {
        val index = snapshotTable.convertRowIndexToModel(row)
        val snapshot = snapshotTableModel.getItem(index)
        if (snapshot == snapshotTableModel.bootSnapshot) {
          // The boot snapshot is being deleted. Change the boot snapshot to QuickBoot.
          snapshotTableModel.setBootSnapshot(QUICK_BOOT_SNAPSHOT_MODEL_ROW, true)
        }
        snapshotTableModel.snapshotIconMap.remove(snapshot)?.cancel(true)
        foldersToDelete.add(snapshot.snapshotFolder)
        snapshotTableModel.removeRow(index)
      }
    }
    selectionModel.clearSelection()
    // Select a single snapshot near the place where the old selection was or the QuickBoot
    // snapshot if the old selection was empty.
    val index = when {
      minSelectionIndex < 0 -> 0
      minSelectionIndex < snapshotTable.rowCount -> minSelectionIndex
      else -> snapshotTable.rowCount - 1
    }
    selectionModel.addSelectionInterval(index, index)

    deleteSnapshotFolders(foldersToDelete, selectionState, notifyWhenDone = true)
  }

  private fun deleteSnapshotFolders(foldersToDelete: List<Path>, selectionState: SelectionState? = null, notifyWhenDone: Boolean = false) {
    backgroundExecutor.submit {
      var errors = false
      for (folder in foldersToDelete) {
        try {
          snapshotIoLock.write { FileUtil.delete(folder) }
        } catch (e: IOException) {
          thisLogger().error(e)
          errors = true
        }
      }

      if (errors) {
        val snapshots = snapshotIoLock.read { snapshotManager.fetchSnapshotList() }
        invokeLaterWhileDialogIsShowing {
          snapshotTableModel.update(snapshots)
          selectionState?.restoreSelection()
          showError("Some snapshots could not be deleted")
        }
      }
      else if (notifyWhenDone) {
        val n = foldersToDelete.size
        val message = if (n == 1) "$n snapshot deleted" else "$n snapshots deleted"
        invokeLaterWhileDialogIsShowing {
          selectionStateLabel.text = message
        }
      }
    }
  }

  private fun startLongOperation(message: String) {
    runningOperationLabel.text = message
    runningOperationLabel.isVisible = true
  }

  private fun endLongOperation() {
    runningOperationLabel.text = ""
    runningOperationLabel.isVisible = false
  }

  /**
   * Shows a red error message at the bottom of the dialog.
   */
  private fun showError(message: String) {
    // Unfortunately, DialogWrapper.setErrorInfoAll and the related methods are protected
    // nd cannot be called directly since we are not subclassing DialogWrapper. To get
    // around this limitation we take advantage of the existing setErrorInfoAll call in
    // the MyDialogWrapper.performAction method in components.kt.
    (dialogManager as DialogManager?)?.performAction {
      validationText.text = message
      listOf(ValidationInfo(message), ValidationInfo(message, validationText))
    }
  }

  /**
   * Clears the error message at the bottom of the dialog.
   */
  private fun clearError() {
    // Changing validationText.text triggers a call to setErrorInfoAll(emptyList()) in
    // the MyDialogWrapper.clearErrorInfoOnFirstChange method in components.kt.
    validationText.text = ""
  }

  /**
   * Creates the dialog wrapper.
   */
  fun createWrapper(project: Project? = null, parent: Component? = null): DialogWrapper {
    return dialog(
      title = "Manage Snapshots (${emulator.emulatorId.avdName})",
      resizable = true,
      panel = createPanel(),
      project = project,
      parent = parent,
      modality = IdeModalityType.MODELESS,
      createActions = { listOf(CloseDialogAction()) })
      .apply {
        dialogManager = this

        backgroundExecutor.submit {
          readBootModeAndSnapshotList()
        }

        setInitialLocationCallback {
          val dimensionService = DimensionService.getInstance()
          val savedSize = dimensionService.getSize(DIMENSION_SERVICE_KEY, project)
          if (savedSize != null) {
            setSize(savedSize.width, savedSize.width)
          }
          return@setInitialLocationCallback dimensionService.getLocation(DIMENSION_SERVICE_KEY, project)
        }

        val connection = ApplicationManager.getApplication().messageBus.connect(disposable)
        connection.subscribe(LafManagerListener.TOPIC, LafManagerListener {
          updateSelectionState()
        })

        Disposer.register(disposable) {
          val dimensionService = DimensionService.getInstance()
          dimensionService.setLocation(DIMENSION_SERVICE_KEY, location, project)
          dimensionService.setSize(DIMENSION_SERVICE_KEY, size, project)

          // The dialog is closing but we still need to wait for all background operations to finish.
          dialogManager = null
          // Cancel unfinished icon loading.
          for (future in snapshotTableModel.snapshotIconMap.values) {
            future.cancel(true)
          }

          backgroundExecutor.shutdown()
          ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Saving changes", false) {
            override fun run(indicator: ProgressIndicator) {
              if (!backgroundExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                thisLogger().warn("Background activity is still running after 5 seconds")
              }
              snapshotIoLock.write {} // Wait for all file read operations to complete.
            }
          })
        }
      }
  }

  /*
   * Reads the boot mode and the snapshot list.
   */
  @Slow
  private fun readBootModeAndSnapshotList() {
    val bootMode = snapshotManager.readBootMode() ?: BootMode(BootType.COLD, null)
    val snapshots = snapshotIoLock.read { snapshotManager.fetchSnapshotList().toMutableList() }
    // Put the QuickBoot snapshot at the top of the list.
    snapshots.sortWith(compareByDescending(SnapshotInfo::isQuickBoot))
    if (snapshots.firstOrNull()?.isQuickBoot != true) {
      // Add a fake QuickBoot snapshot if is not present.
      snapshots.add(QUICK_BOOT_SNAPSHOT_MODEL_ROW, SnapshotInfo(snapshotManager.snapshotsFolder.resolve(QUICK_BOOT_SNAPSHOT_ID)))
    }
    val bootSnapshot = when (bootMode.bootType) {
      BootType.COLD -> null
      BootType.QUICK -> snapshots[QUICK_BOOT_SNAPSHOT_MODEL_ROW]
      else -> snapshots.find { it.snapshotId == bootMode.bootSnapshotId && it.isCompatible }
    }

    val snapshotAutoDeletionPolicy = EmulatorSettings.getInstance().snapshotAutoDeletionPolicy
    var incompatibleSnapshotsCount = 0
    var incompatibleSnapshotsSize = 0L
    if (snapshotAutoDeletionPolicy != SnapshotAutoDeletionPolicy.DO_NOT_DELETE) {
      for (snapshot in snapshots) {
        if (!snapshot.isCompatible) {
          incompatibleSnapshotsCount++
          incompatibleSnapshotsSize += snapshot.sizeOnDisk
        }
      }

      if (incompatibleSnapshotsCount != 0 && snapshotAutoDeletionPolicy == SnapshotAutoDeletionPolicy.DELETE_AUTOMATICALLY) {
        deleteIncompatibleSnapshots(snapshots)
      }
    }

    invokeLaterWhileDialogIsShowing {
      snapshotTableModel.update(snapshots, bootSnapshot)
      if (incompatibleSnapshotsCount != 0 && snapshotAutoDeletionPolicy == SnapshotAutoDeletionPolicy.ASK_BEFORE_DELETING &&
          confirmIncompatibleSnapshotsDeletion(incompatibleSnapshotsCount, incompatibleSnapshotsSize)) {
        deleteIncompatibleSnapshots(snapshots)
        snapshotTableModel.update(snapshots, bootSnapshot)
      }
    }
  }

  private fun deleteIncompatibleSnapshots(snapshots: MutableList<SnapshotInfo>) {
    val foldersToDelete = snapshots.filter { !it.isCompatible }.map { it.snapshotFolder }
    snapshots.removeIf { !it.isCompatible }
    deleteSnapshotFolders(foldersToDelete)
  }

  private fun confirmIncompatibleSnapshotsDeletion(incompatibleSnapshotsCount: Int, incompatibleSnapshotsSize: Long): Boolean {
    val dialog = IncompatibleSnapshotsDeletionConfirmationDialog(incompatibleSnapshotsCount, incompatibleSnapshotsSize)
    val dialogWrapper = dialog.createWrapper(parent = snapshotTable).apply { show() }
    when (dialogWrapper.exitCode) {
      IncompatibleSnapshotsDeletionConfirmationDialog.DELETE_EXIT_CODE -> {
        if (dialog.doNotAskAgain) {
          EmulatorSettings.getInstance().snapshotAutoDeletionPolicy = SnapshotAutoDeletionPolicy.DELETE_AUTOMATICALLY
        }
        return true
      }

      IncompatibleSnapshotsDeletionConfirmationDialog.KEEP_EXIT_CODE -> {
        if (dialog.doNotAskAgain) {
          EmulatorSettings.getInstance().snapshotAutoDeletionPolicy = SnapshotAutoDeletionPolicy.DO_NOT_DELETE
        }
      }
    }
    return false
  }

  private fun invokeLaterWhileDialogIsShowing(runnable: () -> Unit) {
    ApplicationManager.getApplication().invokeLater(runnable, ModalityState.any()) { dialogManager == null }
  }

  private inner class SnapshotTableModel : ListTableModel<SnapshotInfo>() {

    private val nameColumn = object : SnapshotColumnInfo("Name") {

      override fun getRenderer(snapshot: SnapshotInfo): TableCellRenderer {
        return SnapshotNameRenderer()
      }

      override fun getComparator(): Comparator<SnapshotInfo> {
        return compareBy(Collator.getInstance(), SnapshotInfo::displayName)
      }

      private inner class SnapshotNameRenderer : SnapshotTextColumnRenderer() {

        override fun setValue(snapshot: Any) {
          snapshot as SnapshotInfo
          icon = getIcon(snapshot)
          text = snapshot.displayName
        }
      }
    }

    private val creationTimeColumn = object : SnapshotColumnInfo("Created") {

      override fun getRenderer(snapshot: SnapshotInfo): TableCellRenderer {
        return SnapshotCreationTimeRenderer()
      }

      override fun getComparator(): Comparator<SnapshotInfo> {
        return compareBy(SnapshotInfo::creationTime)
      }
    }

    private val sizeColumn = object : SnapshotColumnInfo("Size") {

      override fun getRenderer(snapshot: SnapshotInfo): TableCellRenderer {
        return SnapshotSizeRenderer()
      }

      override fun getComparator(): Comparator<SnapshotInfo> {
        return compareBy(SnapshotInfo::sizeOnDisk).thenBy(Collator.getInstance(), SnapshotInfo::displayName)
      }
    }

    private val bootColumn = object : ColumnInfo<SnapshotInfo, Boolean>("Use to Boot") {

      override fun valueOf(snapshot: SnapshotInfo): Boolean {
        return snapshot == bootSnapshot
      }

      override fun getColumnClass(): Class<*> {
        return java.lang.Boolean::class.java
      }

      override fun isCellEditable(snapshot: SnapshotInfo): Boolean {
        return snapshot.isCompatible
      }

      override fun getEditor(snapshot: SnapshotInfo): TableCellEditor {
        return BooleanTableCellEditor()
      }

      override fun getRenderer(snapshot: SnapshotInfo): TableCellRenderer {
        return BooleanTableCellRenderer()
      }
    }

    var bootSnapshot: SnapshotInfo? = null
      private set(value) {
        field = value
        coldBootCheckBox.isSelected = value == null
      }

    val isColdBoot: Boolean
      get() = bootSnapshot == null

    val nameColumnIndex: Int
    val bootColumnIndex: Int
    val snapshotIconMap = hashMapOf<SnapshotInfo, Future<Icon?>>()
    /** An empty icon matching the size of a decorated one. */
    private val emptyIcon = createDecoratedIcon(EmptyIcon.ICON_16, EmptyIcon.ICON_16)

    init {
      columnInfos = arrayOf(nameColumn, creationTimeColumn, sizeColumn, bootColumn)
      isSortable = true
      nameColumnIndex = 0
      bootColumnIndex = columnCount - 1
    }

    override fun setValueAt(value: Any?, row: Int, column: Int) {
      if (column == bootColumnIndex) {
        setBootSnapshot(row, value as Boolean)
      }
      else {
        invalidColumn(column)
      }
    }

    fun setBootSnapshot(row: Int, value: Boolean) {
      val oldBootSnapshot = bootSnapshot
      val snapshot = getItem(row)
      bootSnapshot = when {
        value -> snapshot
        snapshot === bootSnapshot -> null
        else -> bootSnapshot
      }
      if (bootSnapshot !== oldBootSnapshot && oldBootSnapshot != null) {
        fireTableCellUpdated(indexOf(oldBootSnapshot), bootColumnIndex)
      }
      fireTableCellUpdated(row, bootColumnIndex)
      saveBootMode(bootSnapshot)
    }

    fun bootSnapshotIndex() = bootSnapshot?.let { indexOf(it) } ?: -1

    fun setLoadedLastSnapshot(row: Int) {
      for ((index, snapshot) in items.withIndex()) {
        val isLoadedLast = index == row
        if (snapshot.isLoadedLast != isLoadedLast) {
          snapshot.isLoadedLast = isLoadedLast
          val iconFuture = snapshotIconMap.remove(snapshot)
          if (iconFuture != null) {
            val baseIcon = (iconFuture.getDoneOrNull() as LayeredIcon?)?.getIcon(0)
            if (baseIcon != null) {
              snapshotIconMap[snapshot] = immediateFuture(createDecoratedIcon(snapshot, baseIcon))
            }
            else {
              iconFuture.cancel(true)
            }
          }
          snapshotTableModel.fireTableCellUpdated(index, nameColumnIndex)
        }
      }
    }

    private fun saveBootMode(bootSnapshot: SnapshotInfo?) {
      val bootMode = createBootMode((bootSnapshot))
      backgroundExecutor.submit {
        snapshotIoLock.read { snapshotManager.saveBootMode(bootMode) }
      }
    }

    private fun invalidColumn(columnIndex: Int): Nothing {
      throw IllegalArgumentException("Invalid column $columnIndex")
    }

    fun update(snapshots: List<SnapshotInfo>, newBootSnapshot: SnapshotInfo?) {
      bootSnapshot = newBootSnapshot
      update(snapshots)
    }

    fun update(snapshots: List<SnapshotInfo>) {
      val savedSelection = SelectionState(snapshotTable)
      items = snapshots
      snapshotIconMap.keys.retainAll(HashSet(snapshots)) // Clean up snapshotIconMap.
      savedSelection.restoreSelection()
      if (snapshotTable.selectedRowCount == 0 && snapshots.isNotEmpty()) {
        snapshotTable.selectionModel.addSelectionInterval(QUICK_BOOT_SNAPSHOT_MODEL_ROW, QUICK_BOOT_SNAPSHOT_MODEL_ROW)
      }
    }

    override fun getDefaultSortKey(): RowSorter.SortKey {
      return RowSorter.SortKey(nameColumnIndex, SortOrder.ASCENDING)
    }

    fun getIcon(snapshot: SnapshotInfo): Icon {
      val iconFuture = snapshotIconMap.computeIfAbsent(snapshot, ::createSnapshotIcon)

      if (iconFuture.isDone) {
        try {
          return iconFuture.get() ?: emptyIcon
        }
        catch (_: Exception) { // Ignore to return an empty icon.
        }
      }
      return emptyIcon
    }

    private fun createSnapshotIcon(snapshot: SnapshotInfo): Future<Icon?> {
      return AndroidIoManager.getInstance().getBackgroundDiskIoExecutor().submit(Callable<Icon> {
        val baseIcon = createBaseSnapshotIcon(snapshot)
        val icon = createDecoratedIcon(snapshot, baseIcon)

        // Schedule a table cell update on the UI thread.
        invokeLaterWhileDialogIsShowing {
          val index = indexOf(snapshot)
          if (index >= 0) {
            fireTableCellUpdated(index, nameColumnIndex)
          }
        }
        return@Callable icon
      })
    }

    private fun createDecoratedIcon(snapshot: SnapshotInfo, baseIcon: Icon): LayeredIcon {
      val decorator = when {
        snapshot.isLoadedLast -> getLiveIndicator(EmptyIcon.ICON_16)
        snapshot.isCompatible -> EmptyIcon.ICON_16
        else -> IconUtil.toSize(StudioIcons.Emulator.Snapshots.INVALID_SNAPSHOT_DECORATOR, baseIcon.iconWidth, baseIcon.iconHeight)
      }
      return createDecoratedIcon(baseIcon, decorator)
    }

    @Slow
    private fun createBaseSnapshotIcon(snapshot: SnapshotInfo): Icon {
      try {
        val image = snapshotIoLock.read {
          if (Thread.interrupted()) null else ImageIO.read(snapshot.screenshotFile.toFile())
        } ?: return EmptyIcon.ICON_16
        val imageScale = 16 * JBUIScale.sysScale(snapshotTable).toDouble() / max(image.width, image.height)
        val iconImage = ImageUtils.scale(image, imageScale)
        val iconSize = JBUIScale.scale(16)
        return IconUtil.toSize(JBImageIcon(iconImage), iconSize, iconSize)
      }
      catch (_: IOException) {
        return EmptyIcon.ICON_16
      }
    }

    private fun createDecoratedIcon(baseIcon: Icon, decorator: Icon): LayeredIcon {
      val icon = LayeredIcon(2)
      icon.setIcon(baseIcon, 0)
      // Shift the decorator to make it stand out visually.
      icon.setIcon(decorator, 1, baseIcon.iconWidth / 4, 0)
      return icon
    }
  }

  private inner class LoadSnapshotAction : AnActionButton("Load Snapshot", StudioIcons.Emulator.Snapshots.LOAD_SNAPSHOT) {

    override fun actionPerformed(event: AnActionEvent) {
      loadSnapshot()
    }

    override fun isEnabled(): Boolean {
      return super.isEnabled() && snapshotTable.selectionModel.isSingleItemSelected &&
             snapshotTable.selectedObject!!.isCreated && snapshotTable.selectedObject!!.isCompatible
    }
  }

  private class SnapshotTable(tableModel: SnapshotTableModel) : TableView<SnapshotInfo>(tableModel) {

    private var preferredColumnWidths: IntArray? = null

    override fun getModel(): SnapshotTableModel {
      return dataModel as SnapshotTableModel
    }

    init {
      createDefaultColumnsFromModel()
      setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS)

      addPropertyChangeListener { event ->
        if (event.propertyName == "model") {
          preferredColumnWidths = null
        }
      }
    }

    override fun createRowSorter(model: TableModel): TableRowSorter<TableModel> {
      return SnapshotRowSorter(model as SnapshotTableModel)
    }

    private fun getPreferredColumnWidth(column: Int): Int {
      var widths = preferredColumnWidths
      if (widths == null) {
        widths = calculatePreferredColumnWidths()
        preferredColumnWidths = widths
      }
      return widths[column]
    }

    private fun calculatePreferredColumnWidths(): IntArray {
      val widths = IntArray(model.columnCount)
      val tableWidth = width - insets.left - insets.right
      if (tableWidth <= 0) {
        return widths
      }
      var totalWidth = 0
      for (column in model.columnInfos.indices.reversed()) {
        val preferredColumnWidth = if (column == 0) {
          // The first column gets the remaining width.
          (tableWidth - totalWidth).coerceAtLeast(0)
        }
        else {
          calculatePreferredColumnWidth(column)
        }
        widths[column] = preferredColumnWidth
        totalWidth += preferredColumnWidth
      }
      return widths
    }

    override fun prepareRenderer(renderer: TableCellRenderer, row: Int, column: Int): Component {
      val component = super.prepareRenderer(renderer, row, column) as JComponent
      val tableColumn = columnModel.getColumn(column)
      val preferredWidth = getPreferredColumnWidth(column)
      tableColumn.preferredWidth = preferredWidth
      return component
    }

    override fun tableChanged(event: TableModelEvent) {
      preferredColumnWidths = null
      super.tableChanged(event)
    }

    private fun calculatePreferredColumnWidth(column: Int): Int {
      return getColumnHeaderWidth(column).coerceAtLeast(getColumnDataWidth(column))
    }

    private fun getColumnHeaderWidth(column: Int): Int {
      val tableHeader: JTableHeader = getTableHeader()
      val headerFontMetrics = tableHeader.getFontMetrics(tableHeader.font)
      return headerFontMetrics.stringWidth(getColumnName(column)) + JBUIScale.scale(HEADER_GAP)
    }

    private fun getColumnDataWidth(column: Int): Int {
      var dataWidth = 0
      for (row in 0 until rowCount) {
        val cellRenderer: TableCellRenderer = getCellRenderer(row, column)
        val c: Component = super.prepareRenderer(cellRenderer, row, column)
        dataWidth = dataWidth.coerceAtLeast(c.preferredSize.width + intercellSpacing.width)
      }
      return dataWidth
    }

    private class SnapshotRowSorter(model: SnapshotTableModel) : DefaultColumnInfoBasedRowSorter(model) {

      override fun getComparator(column: Int): Comparator<*> {
        // Make sure that the QuickBoot snapshot is always at the top and incompatible snapshots are
        // always at the bottom of the list.
        val quickBootComparator = if (sortKeys.firstOrNull()?.sortOrder == SortOrder.ASCENDING) {
          compareByDescending(SnapshotInfo::isQuickBoot).thenByDescending(SnapshotInfo::isCompatible)
        }
        else {
          compareBy(SnapshotInfo::isQuickBoot).thenBy(SnapshotInfo::isCompatible)
        }
        @Suppress("UNCHECKED_CAST")
        return quickBootComparator.then(super.getComparator(column) as Comparator<SnapshotInfo>)
      }
    }
  }

  private abstract class SnapshotTextColumnRenderer : DefaultTableCellRenderer() {

    override fun getTableCellRendererComponent(table: JTable,
                                               snapshot: Any,
                                               isSelected: Boolean,
                                               hasFocus: Boolean,
                                               row: Int,
                                               column: Int): Component {
      return super.getTableCellRendererComponent(table, snapshot, isSelected, false, row, column).apply {
        if ((snapshot as SnapshotInfo).isQuickBoot) {
          font = font.deriveFont(Font.ITALIC)
        }
        if (!snapshot.isCompatible) {
          toolTipText = "The snapshot is incompatible with the current configuration"
        }
      }
    }
  }

  private class SnapshotCreationTimeRenderer : SnapshotTextColumnRenderer() {

    override fun setValue(snapshot: Any) {
      text = formatPrettySnapshotDateTime((snapshot as SnapshotInfo).creationTime)
    }
  }

  private class SnapshotSizeRenderer : SnapshotTextColumnRenderer() {

    override fun setValue(snapshot: Any) {
      text = formatSnapshotSize((snapshot as SnapshotInfo).sizeOnDisk)
    }
  }

  private abstract class SnapshotColumnInfo(name: String): ColumnInfo<SnapshotInfo, SnapshotInfo>(name) {

    override fun valueOf(snapshot: SnapshotInfo): SnapshotInfo {
      return snapshot
    }
  }

  private class SelectionState(private val table: SnapshotTable) {
    private val selected = getSelectedSnapshotFolders()
    private val anchor: Path? = getSnapshotFolderAt(table.selectionModel.anchorSelectionIndex)
    private val lead: Path? = getSnapshotFolderAt(table.selectionModel.anchorSelectionIndex)

    fun restoreSelection() {
      val selectionModel: ListSelectionModel = table.selectionModel
      selectionModel.clearSelection()
      if (selected.isNotEmpty()) {
        var anchorRow = -1
        var leadRow = -1
        val model = table.model
        for (i in 0 until model.rowCount) {
          val snapshotFolder = model.getItem(i).snapshotFolder
          val row = table.convertRowIndexToView(i)
          if (selected.contains(snapshotFolder)) {
            selectionModel.addSelectionInterval(row, row)
          }
          if (snapshotFolder == anchor) {
            anchorRow = row
          }
          if (snapshotFolder == lead) {
            leadRow = row
          }
        }
        selectionModel.anchorSelectionIndex = anchorRow
        selectionModel.leadSelectionIndex = leadRow
      }

      TableUtil.scrollSelectionToVisible(table)
    }

    private fun getSelectedSnapshotFolders(): Set<Path> {
      val selectionModel: ListSelectionModel = table.selectionModel
      val minSelectionIndex = selectionModel.minSelectionIndex
      val maxSelectionIndex = selectionModel.maxSelectionIndex
      if (minSelectionIndex < 0 || maxSelectionIndex < 0) {
        return emptySet()
      }

      val result = SmartSet.create<Path>()
      for (i in minSelectionIndex..maxSelectionIndex) {
        if (selectionModel.isSelectedIndex(i)) {
          getSnapshotFolderAt(i)?.let { result.add(it) }
        }
      }
      return result
    }

    private fun getSnapshotFolderAt(row: Int): Path? {
      if (row < 0) {
        return null
      }
      val model = table.model
      val modelIndex: Int = table.convertRowIndexToModel(row)
      return if (modelIndex >= 0 && modelIndex < model.rowCount) model.getRowValue(modelIndex).snapshotFolder else null
    }
  }

  private class BorderLayoutPanelWithPreferredSize(
    private val preferredWidth: Int,
    private val preferredHeight: Int
  ) : BorderLayoutPanel() {

    override fun getPreferredSize(): Dimension? {
      return if (isPreferredSizeSet) super.getPreferredSize() else JBUI.size(preferredWidth, preferredHeight)
    }
  }

  private class CloseDialogAction : AbstractAction(CommonBundle.getCloseButtonText()) {

    init {
      putValue(DialogWrapper.DEFAULT_ACTION, true)
    }

    override fun actionPerformed(event: ActionEvent) {
      val wrapper = DialogWrapper.findInstance(event.source as? Component)
      wrapper?.close(DialogWrapper.CLOSE_EXIT_CODE)
    }
  }
}

private fun formatPrettySnapshotDateTime(time: Long): String {
  return if (time > 0) JBDateFormat.getFormatter().formatPrettyDateTime(time).replace(",", "") else "-"
}

private fun formatSnapshotSize(size: Long): String {
  return if (size > 0) getHumanizedSize(size) else "-"
}

private fun composeSnapshotId(existingSnapshots: Collection<SnapshotInfo>): String {
  val timestamp = TIMESTAMP_FORMAT.format(Date())
  for (counter in 0..existingSnapshots.size) {
    val suffix = if (counter == 0) "" else "_${counter}"
    val snapshotName = "snap_${timestamp}${suffix}"
    if (existingSnapshots.find { it.snapshotId == snapshotName } == null) {
      return snapshotName
    }
  }
  throw AssertionError("Unable to create an unique snapshot ID")
}

private val ListSelectionModel.isSingleItemSelected: Boolean
  get() = minSelectionIndex >= 0 && minSelectionIndex == maxSelectionIndex

private val TIMESTAMP_FORMAT = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)

/** Text offset in the table header. */
private const val HEADER_GAP = 17

private const val QUICK_BOOT_SNAPSHOT_MODEL_ROW = 0 // The QuickBoot snapshot is always first in the list.

@NonNls
private val DIMENSION_SERVICE_KEY = "#${ManageSnapshotsDialog::class.qualifiedName}"
