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
package com.android.tools.idea.streaming.emulator.dialogs

import com.android.testutils.ImageDiffUtil
import com.android.testutils.TestUtils
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.PortableUiFontRule
import com.android.tools.adtui.swing.createModalDialogAndInteractWithIt
import com.android.tools.adtui.swing.enableHeadlessDialogs
import com.android.tools.adtui.ui.ImagePanel
import com.android.tools.idea.concurrency.waitForCondition
import com.android.tools.idea.protobuf.TextFormat
import com.android.tools.idea.streaming.DEFAULT_SNAPSHOT_AUTO_DELETION_POLICY
import com.android.tools.idea.streaming.EmulatorSettings
import com.android.tools.idea.streaming.EmulatorSettings.SnapshotAutoDeletionPolicy
import com.android.tools.idea.streaming.emulator.EmulatorView
import com.android.tools.idea.streaming.emulator.EmulatorViewRule
import com.android.tools.idea.streaming.emulator.FakeEmulator
import com.android.tools.idea.streaming.emulator.actions.findManageSnapshotDialog
import com.google.common.truth.Truth.assertThat
import com.intellij.diagnostic.ThreadDumper
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.DialogWrapper.CLOSE_EXIT_CODE
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.TestActionEvent
import com.intellij.ui.AnActionButton
import com.intellij.ui.CommonActionsPanel
import com.intellij.ui.table.TableView
import com.intellij.util.ui.UIUtil
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JEditorPane
import javax.swing.JLabel
import javax.swing.JLayeredPane
import javax.swing.JTextField
import javax.swing.JTextPane
import javax.swing.table.DefaultTableCellRenderer

/**
 * Tests for [ManageSnapshotsDialog].
 */
@RunsInEdt
class ManageSnapshotsDialogTest {
  private val emulatorViewRule = EmulatorViewRule()
  private val timeoutRule = Timeout.builder().withTimeout(60, TimeUnit.SECONDS).withLookingForStuckThread(true).build()

  @get:Rule
  val ruleChain = RuleChain(timeoutRule, emulatorViewRule, EdtRule())

  @get:Rule
  val portableUiFontRule = PortableUiFontRule()

  private var nullableEmulator: FakeEmulator? = null
  private var nullableEmulatorView: EmulatorView? = null

  private var emulator: FakeEmulator
    get() = nullableEmulator ?: throw IllegalStateException()
    set(value) { nullableEmulator = value }

  private var emulatorView: EmulatorView
    get() = nullableEmulatorView ?: throw IllegalStateException()
    set(value) { nullableEmulatorView = value }

  private val testRootDisposable
    get() = emulatorViewRule.testRootDisposable

  @Before
  fun setUp() {
    enableHeadlessDialogs(testRootDisposable)
    emulatorView = emulatorViewRule.newEmulatorView()
    emulator = emulatorViewRule.getFakeEmulator(emulatorView)
    Disposer.register(testRootDisposable) {
      val dialog = findManageSnapshotDialog(emulatorView)
      dialog?.let { thisLogger().warn("The dialog was not closed by the test") }
      dialog?.disposeIfNeeded()
      if (findManageSnapshotDialog(emulatorView) != null) {
        thisLogger().warn("The dialog is still not closed after calling disposeIfNeeded")
      }

      EmulatorSettings.getInstance().snapshotAutoDeletionPolicy = DEFAULT_SNAPSHOT_AUTO_DELETION_POLICY
    }
  }

  @Test
  fun testDialog() {
    EmulatorSettings.getInstance().snapshotAutoDeletionPolicy = SnapshotAutoDeletionPolicy.DO_NOT_DELETE
    val incompatibleSnapshotId = "incompatible_snapshot"
    emulator.createIncompatibleSnapshot(incompatibleSnapshotId)

    val dialog = showManageSnapshotsDialog()
    val rootPane = dialog.rootPane
    val ui = FakeUi(rootPane)
    val table = ui.getComponent<TableView<SnapshotInfo>>()
    val actionsPanel = ui.getComponent<CommonActionsPanel>()
    val snapshotDetailsPanel = ui.getComponent<JEditorPane>()
    // Wait for the snapshot list to be populated.
    waitForCondition(4, TimeUnit.SECONDS) { table.items.isNotEmpty() }
    // Check that there is only a QuickBoot snapshot, which is a placeholder since it doesn't exist on disk.
    assertThat(table.items).hasSize(2)
    val quickBootSnapshot = table.items[0]
    assertThat(quickBootSnapshot.isQuickBoot).isTrue()
    assertThat(quickBootSnapshot.isCreated).isFalse() // It hasn't been created yet.
    val incompatibleSnapshot = table.items[1]
    assertThat(incompatibleSnapshot.isCompatible).isFalse()
    assertThat(isUseToBoot(table, 0)).isFalse() // The QuickBoot snapshot is not used to boot.
    assertThat(table.selectedObject).isEqualTo(quickBootSnapshot)
    assertThat(findPreviewImagePanel(ui)?.isVisible).isTrue()
    assertThat(findPreviewImagePanel(ui)?.image).isNull() // The QuickBoot snapshot hasn't been created yet.
    assertThat(snapshotDetailsPanel.isVisible).isTrue()
    assertThat(snapshotDetailsPanel.text).contains("Quickboot (auto-saved)")
    assertThat(snapshotDetailsPanel.text).contains("Not created yet")

    assertThat(isPresentationEnabled(getLoadSnapshotAction(actionsPanel))).isFalse()
/* b/265712465
    assertThat(isPresentationEnabled(actionsPanel.getAnActionButton(CommonActionsPanel.Buttons.EDIT))).isFalse()
    assertThat(isPresentationEnabled(actionsPanel.getAnActionButton(CommonActionsPanel.Buttons.REMOVE))).isFalse()

    val coldBootCheckBox = ui.getComponent<JCheckBox> { it.text.contains("cold boot") }
    assertThat(coldBootCheckBox.isSelected).isTrue()

    emulator.clearGrpcCallLog()
    val takeSnapshotButton = ui.getComponent<JButton> { it.text == "Take Snapshot" }
    // Create a snapshot.
    ui.clickOn(takeSnapshotButton)
    var call = emulator.getNextGrpcCall(2, TimeUnit.SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.SnapshotService/SaveSnapshot")

    // Wait for the snapshot to be created and the snapshot list to be updated.
    waitForCondition(2, TimeUnit.SECONDS) { table.items.size == 3 }
    var selectedSnapshot = checkNotNull(table.selectedObject)
    assertThat(selectedSnapshot.isQuickBoot).isFalse()
    assertThat(selectedSnapshot.creationTime).isNotEqualTo(0)
    assertThat(selectedSnapshot.sizeOnDisk).isGreaterThan(0)
    assertThat(selectedSnapshot.displayName).startsWith("snap_")
    assertThat(selectedSnapshot.description).isEmpty()
    assertThat(isUseToBoot(table, table.selectedRow)).isFalse()

    assertThat(findPreviewImagePanel(ui)?.isVisible).isTrue()
    assertThat(findPreviewImagePanel(ui)?.image).isNotNull()
    ui.layout()
    ImageDiffUtil.assertImageSimilar(getGoldenFile("SnapshotPreview"), ui.render(findPreviewImagePanel(ui)!!), 0.0)
    assertThat(snapshotDetailsPanel.isVisible).isTrue()

    assertThat(isPresentationEnabled(getLoadSnapshotAction(actionsPanel))).isTrue()
    assertThat(isPresentationEnabled(actionsPanel.getAnActionButton(CommonActionsPanel.Buttons.EDIT))).isTrue()
    assertThat(isPresentationEnabled(actionsPanel.getAnActionButton(CommonActionsPanel.Buttons.REMOVE))).isTrue()

    // Rename the newly created snapshot, add a description and assign it to be used to boot.
    val firstSnapshotName = "First Snapshot"
    val description = "The first snapshot created by the test"
    editSnapshot(actionsPanel, firstSnapshotName, description, true)
    selectedSnapshot = checkNotNull(table.selectedObject)
    assertThat(selectedSnapshot.displayName).isEqualTo(firstSnapshotName)
    assertThat(selectedSnapshot.description).isEqualTo(description)
    assertThat(isUseToBoot(table, table.selectedRow)).isTrue()
    assertThat(snapshotDetailsPanel.text).contains(firstSnapshotName)
    assertThat(snapshotDetailsPanel.text).contains(description)

    // Create second snapshot.
    ui.clickOn(takeSnapshotButton)
    // Wait for the snapshot to be created and the snapshot list to be updated.
    waitForCondition(2, TimeUnit.SECONDS) { table.items.size == 4 }
    val secondSnapshot = checkNotNull(table.selectedObject)
    // Edit the second snapshot without making any changes.
    editSnapshot(actionsPanel, secondSnapshot.displayName, secondSnapshot.description, false)
    assertThat(isUseToBoot(table, 1)).isTrue() // The first snapshot is still used to boot.
    // Create third snapshot.
    ui.clickOn(takeSnapshotButton)
    // Wait for the snapshot to be created and the snapshot list to be updated.
    waitForCondition(2, TimeUnit.SECONDS) { table.items.size == 5 }
    assertThat(table.selectedRowCount).isEqualTo(1)
    assertThat(table.selectedRow).isEqualTo(3)
    // Add the second snapshot to the selection.
    val row = table.convertRowIndexToView(table.listTableModel.indexOf(secondSnapshot))
    table.selectionModel.addSelectionInterval(row, row)
    assertThat(table.selectedRowCount).isEqualTo(2)
    assertThat(findSelectionStateLabel(ui)?.isVisible).isTrue()
    assertThat(findSelectionStateLabel(ui)?.text).isEqualTo("2 snapshots selected")
    assertThat(findPreviewImagePanel(ui)).isNull()
    assertThat(snapshotDetailsPanel.isVisible).isFalse()

    assertThat(isPresentationEnabled(getLoadSnapshotAction(actionsPanel))).isFalse()
    assertThat(isPresentationEnabled(actionsPanel.getAnActionButton(CommonActionsPanel.Buttons.EDIT))).isFalse()
    assertThat(isPresentationEnabled(actionsPanel.getAnActionButton(CommonActionsPanel.Buttons.REMOVE))).isTrue()

    // Remove the two selected snapshots.
    performAction(actionsPanel.getAnActionButton(CommonActionsPanel.Buttons.REMOVE))

    assertThat(table.items.size).isEqualTo(3)
    assertThat(table.selectedRowCount).isEqualTo(1)
    selectedSnapshot = checkNotNull(table.selectedObject)
    assertThat(selectedSnapshot.snapshotId).isEqualTo(incompatibleSnapshotId)
    assertThat(isPresentationEnabled(getLoadSnapshotAction(actionsPanel))).isFalse()
    assertThat(isPresentationEnabled(actionsPanel.getAnActionButton(CommonActionsPanel.Buttons.EDIT))).isFalse()
    assertThat(isPresentationEnabled(actionsPanel.getAnActionButton(CommonActionsPanel.Buttons.REMOVE))).isTrue()

    // Remove the incompatible snapshot.
    performAction(actionsPanel.getAnActionButton(CommonActionsPanel.Buttons.REMOVE))

    assertThat(table.items.size).isEqualTo(2)
    assertThat(table.selectedRowCount).isEqualTo(1)
    selectedSnapshot = checkNotNull(table.selectedObject)
    assertThat(selectedSnapshot.displayName).isEqualTo(firstSnapshotName)
    assertThat(isPresentationEnabled(getLoadSnapshotAction(actionsPanel))).isTrue()
    assertThat(isPresentationEnabled(actionsPanel.getAnActionButton(CommonActionsPanel.Buttons.EDIT))).isTrue()
    assertThat(isPresentationEnabled(actionsPanel.getAnActionButton(CommonActionsPanel.Buttons.REMOVE))).isTrue()

    // Load the selected snapshot.
    emulator.clearGrpcCallLog()
    selectedSnapshot = checkNotNull(table.selectedObject)
    assertThat(selectedSnapshot.isLoadedLast).isFalse()
    performAction(getLoadSnapshotAction(actionsPanel))
    call = emulator.getNextGrpcCall(2, TimeUnit.SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.SnapshotService/LoadSnapshot")
    assertThat(TextFormat.shortDebugString(call.request)).isEqualTo("""snapshot_id: "${selectedSnapshot.snapshotId}"""")
    waitForCondition(200, TimeUnit.SECONDS, selectedSnapshot::isLoadedLast)

    assertThat(coldBootCheckBox.isSelected).isFalse()
    assertThat(isUseToBoot(table, 0)).isFalse()
    assertThat(isUseToBoot(table, 1)).isTrue() // The first snapshot is used to boot.

    // Select the cold boot option.
    ui.clickOn(coldBootCheckBox)
    assertThat(coldBootCheckBox.isSelected).isTrue()
    assertThat(isUseToBoot(table, 0)).isFalse()
    assertThat(isUseToBoot(table, 1)).isFalse()

    // Unselect the cold boot option.
    ui.clickOn(coldBootCheckBox)
    assertThat(coldBootCheckBox.isSelected).isFalse()
    assertThat(isUseToBoot(table, 0)).isTrue() // The QuickBoot snapshot is used to boot.
    assertThat(isUseToBoot(table, 1)).isFalse()
b/265712465 */

    // Check that the snapshots have icons.
    assertThat(getIcon(table, 0)).isNotNull()
    assertThat(getIcon(table, 1)).isNotNull()

    // Close the dialog.
    assertThat(dialog.isShowing).isTrue()
    val closeButton = rootPane.defaultButton
    assertThat(closeButton.text).isEqualTo("Close")
    ui.clickOn(closeButton)
    assertThat(dialog.isShowing).isFalse()
  }

  @Test
  fun testIncompatibleSnapshotsConfirmedDeletion() {
    assertThat(EmulatorSettings.getInstance().snapshotAutoDeletionPolicy).isEqualTo(SnapshotAutoDeletionPolicy.ASK_BEFORE_DELETING)
    emulator.createSnapshot("valid_snapshot")
    emulator.createIncompatibleSnapshot("incompatible_snapshot1")
    emulator.createIncompatibleSnapshot("incompatible_snapshot2")
    emulator.pauseGrpc() // Pause emulator's gRPC to prevent the nested dialog from opening immediately.

    val dialog = showManageSnapshotsDialog()
    val rootPane1 = dialog.rootPane
    val ui1 = FakeUi(rootPane1)
    val table = ui1.getComponent<TableView<SnapshotInfo>>()
    /** The "Delete incompatible snapshots?" dialog opens when gRPC is resumed. */
    createModalDialogAndInteractWithIt(emulator::resumeGrpc) { confirmationDialog ->
      assertThat(table.items).hasSize(4) // 1 QuickBoot + 1 valid + 2 incompatible.
      val rootPane2 = confirmationDialog.rootPane
      val ui2 = FakeUi(rootPane2)
      val doNotAskCheckBox = ui2.getComponent<JCheckBox>()
      assertThat(doNotAskCheckBox.isSelected).isFalse()
      doNotAskCheckBox.isSelected = true
      val deleteButton = ui2.getComponent<JButton> { it.text == "Delete" }
      deleteButton.doClick()
      assertThat(confirmationDialog.isShowing).isFalse() // The dialog is closed.
    }
    assertThat(table.items).hasSize(2) // 1 QuickBoot + 1 valid.
    assertThat(table.items.count { !it.isCompatible }).isEqualTo(0) // The two incompatible snapshots were deleted.
    // Close the "Manage Snapshots" dialog.
    ui1.clickOn(rootPane1.defaultButton)

    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    val snapshots = SnapshotManager(emulatorView.emulator).fetchSnapshotList()
    assertThat(snapshots.count { !it.isCompatible }).isEqualTo(0) // The two incompatible snapshots were physically deleted.
    assertThat(EmulatorSettings.getInstance().snapshotAutoDeletionPolicy).isEqualTo(SnapshotAutoDeletionPolicy.DELETE_AUTOMATICALLY)
  }

  @Test
  fun testIncompatibleSnapshotsDeclinedDeletion() {
    assertThat(EmulatorSettings.getInstance().snapshotAutoDeletionPolicy).isEqualTo(SnapshotAutoDeletionPolicy.ASK_BEFORE_DELETING)
    emulator.createSnapshot("valid_snapshot")
    emulator.createIncompatibleSnapshot("incompatible_snapshot1")
    emulator.createIncompatibleSnapshot("incompatible_snapshot2")
    emulator.pauseGrpc() // Pause emulator's gRPC to prevent the nested dialog from opening immediately.

    val dialog = showManageSnapshotsDialog()
    val rootPane1 = dialog.rootPane
    val ui1 = FakeUi(rootPane1)
    val table = ui1.getComponent<TableView<SnapshotInfo>>()
    /** The "Delete incompatible snapshots?" dialog opens when gRPC is resumed. */
    createModalDialogAndInteractWithIt(emulator::resumeGrpc) { confirmationDialog ->
      assertThat(table.items).hasSize(4) // 1 QuickBoot + 1 valid + 2 incompatible.
      val rootPane2 = confirmationDialog.rootPane
      val ui2 = FakeUi(rootPane2)
      val doNotAskCheckBox = ui2.getComponent<JCheckBox>()
      assertThat(doNotAskCheckBox.isSelected).isFalse()
      doNotAskCheckBox.isSelected = true
      val keepButton = ui2.getComponent<JButton> { it.text == "Keep" }
      keepButton.doClick()
      assertThat(confirmationDialog.isShowing).isFalse() // The dialog is closed.
    }
    assertThat(table.items).hasSize(4) // 1 QuickBoot + 1 valid + 2 incompatible.
    assertThat(table.items.count { !it.isCompatible }).isEqualTo(2) // The two incompatible snapshots were preserved.
    // Close the "Manage Snapshots" dialog.
    ui1.clickOn(rootPane1.defaultButton)
  }

  @Test
  fun testIncompatibleSnapshotsAutomaticDeletion() {
    EmulatorSettings.getInstance().snapshotAutoDeletionPolicy = SnapshotAutoDeletionPolicy.DELETE_AUTOMATICALLY
    emulator.createSnapshot("valid_snapshot")
    emulator.createIncompatibleSnapshot("incompatible_snapshot1")
    emulator.createIncompatibleSnapshot("incompatible_snapshot2")

    val dialog = showManageSnapshotsDialog()
    val rootPane = dialog.rootPane
    val ui = FakeUi(rootPane)
    val table = ui.getComponent<TableView<SnapshotInfo>>()
    // Wait for the snapshot list to be populated.
    try {
      waitForCondition(10, TimeUnit.SECONDS) { table.items.isNotEmpty() }
    }
    catch (e: TimeoutException) {
      Assert.fail(e.javaClass.name + '\n' + ThreadDumper.dumpThreadsToString())
    }
    assertThat(table.items).hasSize(2) // The two incompatible snapshots were deleted automatically.
    // Close the "Manage Snapshots" dialog.
    ui.clickOn(rootPane.defaultButton)
  }

  @Test
  fun testIncompatibleSnapshotsNoDeletion() {
    EmulatorSettings.getInstance().snapshotAutoDeletionPolicy = SnapshotAutoDeletionPolicy.DO_NOT_DELETE
    emulator.createSnapshot("valid_snapshot")
    emulator.createIncompatibleSnapshot("incompatible_snapshot1")
    emulator.createIncompatibleSnapshot("incompatible_snapshot2")

    val dialog = showManageSnapshotsDialog()
    val rootPane = dialog.rootPane
    val ui = FakeUi(rootPane)
    val table = ui.getComponent<TableView<SnapshotInfo>>()
    // Wait for the snapshot list to be populated.
    try {
      waitForCondition(10, TimeUnit.SECONDS) { table.items.isNotEmpty() }
    }
    catch (e: TimeoutException) {
      Assert.fail(e.javaClass.name + '\n' + ThreadDumper.dumpThreadsToString())
    }
    assertThat(table.items).hasSize(4) // No snapshots were deleted.
    // Close the "Manage Snapshots" dialog.
    ui.clickOn(rootPane.defaultButton)
  }

  private fun showManageSnapshotsDialog(): DialogWrapper {
    emulatorViewRule.executeAction("android.emulator.snapshots", emulatorView)
    val dialog = findManageSnapshotDialog(emulatorView)!!
    Disposer.register(testRootDisposable) {
      dialog.close(CLOSE_EXIT_CODE)
    }
    return dialog
  }

  private fun findPreviewImagePanel(ui: FakeUi) = ui.findComponent<ImagePanel>()

  private fun findSelectionStateLabel(ui: FakeUi) = ui.findComponent<JLabel> { it.name == "selectionStateLabel" }

  private fun getLoadSnapshotAction(actionsPanel: CommonActionsPanel) = actionsPanel.toolbar.actions[0] as AnActionButton

  private fun isUseToBoot(tableView: TableView<SnapshotInfo>, row: Int): Boolean {
    val cellRenderer = tableView.getCellRenderer(row, USE_TO_BOOT_COLUMN_INDEX)
    val checkBox = tableView.prepareRenderer(cellRenderer, row, USE_TO_BOOT_COLUMN_INDEX) as JCheckBox
    return checkBox.isSelected
  }

  private fun getIcon(tableView: TableView<SnapshotInfo>, row: Int): Icon? {
    val cellRenderer = tableView.getCellRenderer(row, SNAPSHOT_NAME_COLUMN_INDEX)
    return (tableView.prepareRenderer(cellRenderer, row, SNAPSHOT_NAME_COLUMN_INDEX) as DefaultTableCellRenderer).icon
  }

  @Suppress("SameParameterValue")
  private fun editSnapshot(actionsPanel: CommonActionsPanel, name: String?, description: String?, useToBoot: Boolean?) {
/* b/265712465
    val editAction = actionsPanel.getAnActionButton(CommonActionsPanel.Buttons.EDIT)
    createModalDialogAndInteractWithIt({ performAction(editAction) }) { dialog ->
      val rootPane = dialog.rootPane
      val ui = FakeUi(rootPane)
      if (name != null) {
        val nameField = ui.getComponent<JTextField>()
        nameField.text = name
      }

      if (description != null) {
        val descriptionField = ui.getComponent<JTextPane>()
        descriptionField.text = description
      }

      if (useToBoot != null) {
        val useToBootCheckbox = ui.getComponent<JCheckBox>()
        useToBootCheckbox.isSelected = useToBoot
      }

      ui.clickOn(rootPane.defaultButton)
    }
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
b/265712465 */
  }

  private fun performAction(action: AnActionButton) {
    assertThat(isPresentationEnabled(action)).isTrue()
    action.actionPerformed(TestActionEvent(action))
  }

  private fun isPresentationEnabled(action: AnActionButton): Boolean {
    val contextComponent = action.contextComponent
    try {
      action.contextComponent = object : JLayeredPane() {
        override fun isShowing(): Boolean {
          return true
        }
      }
      val event = TestActionEvent(action)
      action.update(event)
      return event.presentation.isEnabled
    }
    finally {
      action.contextComponent = contextComponent
    }
  }

  @Suppress("SameParameterValue")
  private fun getGoldenFile(name: String): Path {
    // The image is slightly taller on Mac due to a slight layout difference.
    val platformSuffix = if (UIUtil.isRetina()) "_Mac" else ""
    return TestUtils.resolveWorkspacePathUnchecked("$GOLDEN_FILE_PATH/$name$platformSuffix.png")
  }
}

private const val SNAPSHOT_NAME_COLUMN_INDEX = 0
private const val USE_TO_BOOT_COLUMN_INDEX = 3

private const val GOLDEN_FILE_PATH = "tools/adt/idea/streaming/testData/ManageSnapshotsDialogTest/golden"
