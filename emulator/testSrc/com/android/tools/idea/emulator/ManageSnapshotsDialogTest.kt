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
package com.android.tools.idea.emulator

import com.android.testutils.TestUtils
import com.android.tools.adtui.imagediff.ImageDiffUtil
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.createDialogAndInteractWithIt
import com.android.tools.adtui.swing.enableHeadlessDialogs
import com.android.tools.adtui.swing.setPortableUiFont
import com.android.tools.adtui.ui.ImagePanel
import com.android.tools.idea.concurrency.waitForCondition
import com.android.tools.idea.emulator.actions.SnapshotInfo
import com.android.tools.idea.emulator.actions.dialogs.ManageSnapshotsDialog
import com.android.tools.idea.protobuf.TextFormat
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.TestActionEvent
import com.intellij.ui.AnActionButton
import com.intellij.ui.CommonActionsPanel
import com.intellij.ui.table.TableView
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import java.io.File
import java.util.concurrent.TimeUnit
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JEditorPane
import javax.swing.JLabel
import javax.swing.JTextField
import javax.swing.JTextPane
import javax.swing.table.DefaultTableCellRenderer

/**
 * Tests for [ManageSnapshotsDialog].
 */
@RunsInEdt
class ManageSnapshotsDialogTest {
  private val projectRule = AndroidProjectRule.inMemory()
  private val emulatorRule = FakeEmulatorRule()
  private var nullableEmulator: FakeEmulator? = null
  private var nullableEmulatorController: EmulatorController? = null
  @get:Rule
  val ruleChain: RuleChain = RuleChain.outerRule(projectRule).around(emulatorRule).around(EdtRule())

  private var emulator: FakeEmulator
    get() = nullableEmulator ?: throw IllegalStateException()
    set(value) { nullableEmulator = value }

  private var emulatorController: EmulatorController
    get() = nullableEmulatorController ?: throw IllegalStateException()
    set(value) { nullableEmulatorController = value }

  private val testRootDisposable
    get() = projectRule.fixture.testRootDisposable

  @Before
  fun setUp() {
    setPortableUiFont()
    enableHeadlessDialogs(testRootDisposable)

    val catalog = RunningEmulatorCatalog.getInstance()
    val tempFolder = emulatorRule.root.toPath()
    emulator = emulatorRule.newEmulator(FakeEmulator.createPhoneAvd(tempFolder), 8554)
    emulator.start()
    val emulators = catalog.updateNow().get()
    assertThat(emulators).hasSize(1)
    emulatorController = emulators.first()
    waitForCondition(5, TimeUnit.SECONDS) { emulatorController.connectionState == EmulatorController.ConnectionState.CONNECTED }
    emulator.getNextGrpcCall(2, TimeUnit.SECONDS) // Skip the initial "getVmState" call.
  }

  @Test
  fun testDialog() {
    val dialogPanel = ManageSnapshotsDialog(emulatorController, emulatorView = null)
    val dialogWrapper = dialogPanel.createWrapper(projectRule.project)

    createDialogAndInteractWithIt({ dialogWrapper.show() }) { dlg ->
      val rootPane = dlg.rootPane
      val ui = FakeUi(rootPane)
      val table = ui.getComponent<TableView<SnapshotInfo>>()
      val tableModel = table.listTableModel
      val actionsPanel = ui.getComponent<CommonActionsPanel>()
      val selectionStateLabel = ui.getComponent<JLabel> { it.name == "selectionStateLabel"}
      val previewImagePanel = ui.getComponent<ImagePanel>()
      val snapshotDetailsPanel = ui.getComponent<JEditorPane>()
      // Wait for the snapshot list to be populated.
      waitForCondition(2, TimeUnit.SECONDS) { tableModel.items.isNotEmpty() }
      // Check that there is only a QuickBoot snapshot, which is a placeholder since it doesn't exist on disk.
      assertThat(tableModel.items).hasSize(1)
      val quickBootSnapshot = tableModel.items[0]
      assertThat(quickBootSnapshot.isQuickBoot).isTrue()
      assertThat(quickBootSnapshot.creationTime).isEqualTo(0) // It is a placeholder.
      assertThat(isUseToBoot(table, 0)).isFalse() // The QuickBoot snapshot is not used to boot.
      assertThat(table.selectedObjects).isEmpty()
      assertThat(selectionStateLabel.isVisible).isTrue()
      assertThat(selectionStateLabel.text).isEqualTo("No snapshots selected")
      assertThat(previewImagePanel.isVisible).isFalse()
      assertThat(snapshotDetailsPanel.isVisible).isFalse()

      assertThat(getLoadSnapshotAction(actionsPanel).isEnabled).isFalse()
      assertThat(actionsPanel.getAnActionButton(CommonActionsPanel.Buttons.EDIT).isEnabled).isFalse()
      assertThat(actionsPanel.getAnActionButton(CommonActionsPanel.Buttons.REMOVE).isEnabled).isFalse()

      val coldBootCheckBox = ui.getComponent<JCheckBox> { it.text.contains("cold boot")}
      assertThat(coldBootCheckBox.isSelected).isTrue()

      val takeSnapshotButton = ui.getComponent<JButton> { it.text == "Take Snapshot" }
      // Create a snapshot.
      ui.clickOn(takeSnapshotButton)
      var call = emulator.getNextGrpcCall(2, TimeUnit.SECONDS)
      assertThat(call.methodName).isEqualTo("android.emulation.control.SnapshotService/SaveSnapshot")

      // Wait for the snapshot to be created and the snapshot list to be updated.
      waitForCondition(2, TimeUnit.SECONDS) { tableModel.items.size == 2 }
      var selectedSnapshot = checkNotNull(table.selectedObject)
      assertThat(selectedSnapshot.isQuickBoot).isFalse()
      assertThat(selectedSnapshot.creationTime).isNotEqualTo(0)
      assertThat(selectedSnapshot.sizeOnDisk).isGreaterThan(0)
      assertThat(selectedSnapshot.displayName).startsWith("snap_")
      assertThat(selectedSnapshot.description).isEmpty()
      assertThat(isUseToBoot(table, table.selectedRow)).isFalse()

      assertThat(selectionStateLabel.isVisible).isFalse()
      assertThat(previewImagePanel.isVisible).isTrue()
      assertThat(previewImagePanel.image).isNotNull()
      ui.layout()
      ImageDiffUtil.assertImageSimilar(getGoldenFile("SnapshotPreview"), ui.render(previewImagePanel), 0.0)
      assertThat(snapshotDetailsPanel.isVisible).isTrue()

      assertThat(getLoadSnapshotAction(actionsPanel).isEnabled).isTrue()
      assertThat(actionsPanel.getAnActionButton(CommonActionsPanel.Buttons.EDIT).isEnabled).isTrue()
      assertThat(actionsPanel.getAnActionButton(CommonActionsPanel.Buttons.REMOVE).isEnabled).isTrue()

      // Rename the newly created snapshot, add a description and assign it to be used to boot.
      val name = "First Snapshot"
      val description = "The first snapshot created by the test"
      editSnapshot(actionsPanel, name, description, true)
      selectedSnapshot = checkNotNull(table.selectedObject)
      assertThat(selectedSnapshot.displayName).isEqualTo(name)
      assertThat(selectedSnapshot.description).isEqualTo(description)
      assertThat(isUseToBoot(table, table.selectedRow)).isTrue()
      assertThat(snapshotDetailsPanel.text).contains(name)
      assertThat(snapshotDetailsPanel.text).contains(description)

      // Create second snapshot.
      ui.clickOn(takeSnapshotButton)
      // Wait for the snapshot to be created and the snapshot list to be updated.
      waitForCondition(2, TimeUnit.SECONDS) { tableModel.items.size == 3 }
      val secondSnapshot = checkNotNull(table.selectedObject)
      // Create third snapshot.
      ui.clickOn(takeSnapshotButton)
      // Wait for the snapshot to be created and the snapshot list to be updated.
      waitForCondition(2, TimeUnit.SECONDS) { tableModel.items.size == 4 }
      // Add the second snapshot to the selection.
      val row = table.convertRowIndexToView(tableModel.items.indexOf(secondSnapshot))
      table.selectionModel.addSelectionInterval(row, row)
      assertThat(table.selectedObjects).hasSize(2)
      assertThat(selectionStateLabel.isVisible).isTrue()
      assertThat(selectionStateLabel.text).isEqualTo("2 snapshots selected")
      assertThat(previewImagePanel.isVisible).isFalse()
      assertThat(snapshotDetailsPanel.isVisible).isFalse()

      assertThat(getLoadSnapshotAction(actionsPanel).isEnabled).isFalse()
      assertThat(actionsPanel.getAnActionButton(CommonActionsPanel.Buttons.EDIT).isEnabled).isFalse()
      assertThat(actionsPanel.getAnActionButton(CommonActionsPanel.Buttons.REMOVE).isEnabled).isTrue()

      // Remove the two selected snapshots.
      performAction(actionsPanel.getAnActionButton(CommonActionsPanel.Buttons.REMOVE))

      assertThat(tableModel.items.size == 2)
      assertThat(selectionStateLabel.isVisible).isTrue()
      assertThat(selectionStateLabel.text).isEqualTo("No snapshots selected")
      assertThat(getLoadSnapshotAction(actionsPanel).isEnabled).isFalse()
      assertThat(actionsPanel.getAnActionButton(CommonActionsPanel.Buttons.EDIT).isEnabled).isFalse()
      assertThat(actionsPanel.getAnActionButton(CommonActionsPanel.Buttons.REMOVE).isEnabled).isFalse()

      // Select the first snapshot.
      table.selectionModel.setSelectionInterval(1, 1)
      assertThat(checkNotNull(table.selectedObject).displayName).isEqualTo(name)
      assertThat(getLoadSnapshotAction(actionsPanel).isEnabled).isTrue()
      assertThat(actionsPanel.getAnActionButton(CommonActionsPanel.Buttons.EDIT).isEnabled).isTrue()
      assertThat(actionsPanel.getAnActionButton(CommonActionsPanel.Buttons.REMOVE).isEnabled).isTrue()

      // Load the selected snapshot.
      emulator.clearGrpcCallLog()
      performAction(getLoadSnapshotAction(actionsPanel))
      selectedSnapshot = checkNotNull(table.selectedObject)
      call = emulator.getNextGrpcCall(2, TimeUnit.SECONDS)
      assertThat(call.methodName).isEqualTo("android.emulation.control.SnapshotService/LoadSnapshot")
      assertThat(TextFormat.shortDebugString(call.request)).isEqualTo("""snapshot_id: "${selectedSnapshot.snapshotId}"""")

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

      // Check that the snapshots have icons.
      assertThat(getIcon(table, 0)).isNotNull()
      assertThat(getIcon(table, 1)).isNotNull()

      // Close the dialog.
      val closeButton = rootPane.defaultButton
      assertThat(closeButton.text).isEqualTo("Close")
      ui.clickOn(closeButton)
    }
  }

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
    val editAction = actionsPanel.getAnActionButton(CommonActionsPanel.Buttons.EDIT)
    createDialogAndInteractWithIt({ performAction(editAction) }) { dlg ->
      val rootPane = dlg.rootPane
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
  }

  private fun performAction(action: AnActionButton) {
    assertThat(action.isEnabled).isTrue()
    action.actionPerformed(TestActionEvent(action))
  }

  @Suppress("SameParameterValue")
  private fun getGoldenFile(name: String): File {
    return TestUtils.getWorkspaceRoot().toPath().resolve("${GOLDEN_FILE_PATH}/${name}.png").toFile()
  }
}

private const val SNAPSHOT_NAME_COLUMN_INDEX = 0
private const val USE_TO_BOOT_COLUMN_INDEX = 3

private const val GOLDEN_FILE_PATH = "tools/adt/idea/emulator/testData/ManageSnapshotsDialogTest/golden"

