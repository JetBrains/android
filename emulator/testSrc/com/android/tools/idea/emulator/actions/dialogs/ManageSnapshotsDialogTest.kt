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
package com.android.tools.idea.emulator.actions.dialogs

import com.android.testutils.TestUtils
import com.android.tools.adtui.imagediff.ImageDiffUtil
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.createDialogAndInteractWithIt
import com.android.tools.adtui.swing.enableHeadlessDialogs
import com.android.tools.adtui.swing.setPortableUiFont
import com.android.tools.adtui.ui.ImagePanel
import com.android.tools.idea.concurrency.waitForCondition
import com.android.tools.idea.emulator.DEFAULT_SNAPSHOT_AUTO_DELETION_POLICY
import com.android.tools.idea.emulator.EmulatorController
import com.android.tools.idea.emulator.EmulatorSettings
import com.android.tools.idea.emulator.EmulatorSettings.SnapshotAutoDeletionPolicy
import com.android.tools.idea.emulator.FakeEmulator
import com.android.tools.idea.emulator.FakeEmulatorRule
import com.android.tools.idea.emulator.RunningEmulatorCatalog
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
import org.junit.After
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
import javax.swing.JLayeredPane
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

  @After
  fun tearDown() {
    EmulatorSettings.getInstance().snapshotAutoDeletionPolicy = DEFAULT_SNAPSHOT_AUTO_DELETION_POLICY
  }

  @Test
  fun testDialog() {
    EmulatorSettings.getInstance().snapshotAutoDeletionPolicy = SnapshotAutoDeletionPolicy.DO_NOT_DELETE
    val invalidSnapshotId = "invalid_snapshot"
    emulator.createInvalidSnapshot(invalidSnapshotId)

    val dialogPanel = ManageSnapshotsDialog(emulatorController, emulatorView = null)
    val dialogWrapper = dialogPanel.createWrapper(projectRule.project)

    // Open the "Manage Snapshots" dialog.
    createDialogAndInteractWithIt({ dialogWrapper.show() }) { dlg ->
      val rootPane = dlg.rootPane
      val ui = FakeUi(rootPane)
      val table = ui.getComponent<TableView<SnapshotInfo>>()
      val actionsPanel = ui.getComponent<CommonActionsPanel>()
      val snapshotDetailsPanel = ui.getComponent<JEditorPane>()
      // Wait for the snapshot list to be populated.
      waitForCondition(2, TimeUnit.SECONDS) { table.items.isNotEmpty() }
      // Check that there is only a QuickBoot snapshot, which is a placeholder since it doesn't exist on disk.
      assertThat(table.items).hasSize(2)
      val quickBootSnapshot = table.items[0]
      assertThat(quickBootSnapshot.isQuickBoot).isTrue()
      assertThat(quickBootSnapshot.isCreated).isFalse() // It hasn't been created yet.
      val invalidSnapshot = table.items[1]
      assertThat(invalidSnapshot.isValid).isFalse()
      assertThat(isUseToBoot(table, 0)).isFalse() // The QuickBoot snapshot is not used to boot.
      assertThat(table.selectedObject).isEqualTo(quickBootSnapshot)
      assertThat(findPreviewImagePanel(ui)?.isVisible).isTrue()
      assertThat(findPreviewImagePanel(ui)?.image).isNull() // The QuickBoot snapshot hasn't been created yet.
      assertThat(snapshotDetailsPanel.isVisible).isTrue()
      assertThat(snapshotDetailsPanel.text).contains("Quickboot (auto-saved)")
      assertThat(snapshotDetailsPanel.text).contains("Not created yet")

      assertThat(isPresentationEnabled(getLoadSnapshotAction(actionsPanel))).isFalse()
      assertThat(isPresentationEnabled(actionsPanel.getAnActionButton(CommonActionsPanel.Buttons.EDIT))).isFalse()
      assertThat(isPresentationEnabled(actionsPanel.getAnActionButton(CommonActionsPanel.Buttons.REMOVE))).isFalse()

      val coldBootCheckBox = ui.getComponent<JCheckBox> { it.text.contains("cold boot")}
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

      assertThat(table.items.size == 3)
      assertThat(table.selectedRowCount).isEqualTo(1)
      selectedSnapshot = checkNotNull(table.selectedObject)
      assertThat(selectedSnapshot.snapshotId).isEqualTo(invalidSnapshotId)
      assertThat(isPresentationEnabled(getLoadSnapshotAction(actionsPanel))).isFalse()
      assertThat(isPresentationEnabled(actionsPanel.getAnActionButton(CommonActionsPanel.Buttons.EDIT))).isFalse()
      assertThat(isPresentationEnabled(actionsPanel.getAnActionButton(CommonActionsPanel.Buttons.REMOVE))).isTrue()

      // Remove the invalid snapshot.
      performAction(actionsPanel.getAnActionButton(CommonActionsPanel.Buttons.REMOVE))

      assertThat(table.items.size == 2)
      assertThat(table.selectedRowCount).isEqualTo(1)
      selectedSnapshot = checkNotNull(table.selectedObject)
      assertThat(selectedSnapshot.displayName).isEqualTo(firstSnapshotName)
      assertThat(isPresentationEnabled(getLoadSnapshotAction(actionsPanel))).isTrue()
      assertThat(isPresentationEnabled(actionsPanel.getAnActionButton(CommonActionsPanel.Buttons.EDIT))).isTrue()
      assertThat(isPresentationEnabled(actionsPanel.getAnActionButton(CommonActionsPanel.Buttons.REMOVE))).isTrue()

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

    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
  }

  @Test
  fun testInvalidSnapshotsConfirmedDeletion() {
    emulator.createSnapshot("valid_snapshot")
    emulator.createInvalidSnapshot("invalid_snapshot1")
    emulator.createInvalidSnapshot("invalid_snapshot2")
    emulator.pauseGrpc() // Pause emulator's gRPC to prevent the nested dialog from opening immediately.

    val dialogPanel = ManageSnapshotsDialog(emulatorController, emulatorView = null)

    // Open the "Manage Snapshots" dialog.
    createDialogAndInteractWithIt({ dialogPanel.createWrapper(projectRule.project).show() }) { dlg1 ->
      val rootPane1 = dlg1.rootPane
      val ui1 = FakeUi(rootPane1)
      val table = ui1.getComponent<TableView<SnapshotInfo>>()
      /** The "Delete incompatible snapshots?" dialog opens when gRPC is resumed. */
      createDialogAndInteractWithIt({ emulator.resumeGrpc() }) { dlg2 ->
        assertThat(table.items).hasSize(4) // 1 QuickBoot + 1 valid + 2 invalid.
        val rootPane2 = dlg2.rootPane
        val ui2 = FakeUi(rootPane2)
        val doNotAskCheckBox = ui2.getComponent<JCheckBox>()
        assertThat(doNotAskCheckBox.isSelected).isFalse()
        doNotAskCheckBox.isSelected = true
        val deleteButton = ui2.getComponent<JButton> { it.text == "Delete" }
        ui2.clickOn(deleteButton)
      }
      assertThat(table.items).hasSize(2) // 1 QuickBoot + 1 valid.
      assertThat(table.items.count { !it.isValid }).isEqualTo(0) // The two invalid snapshots were deleted.
      // Close the "Manage Snapshots" dialog.
      ui1.clickOn(rootPane1.defaultButton)
    }

    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    val snapshots = SnapshotManager(emulatorController).fetchSnapshotList()
    assertThat(snapshots.count { !it.isValid }).isEqualTo(0) // The two invalid snapshots were physically deleted.
    assertThat(EmulatorSettings.getInstance().snapshotAutoDeletionPolicy).isEqualTo(SnapshotAutoDeletionPolicy.DELETE_AUTOMATICALLY)
  }

  @Test
  fun testInvalidSnapshotsDeclinedDeletion() {
    emulator.createSnapshot("valid_snapshot")
    emulator.createInvalidSnapshot("invalid_snapshot1")
    emulator.createInvalidSnapshot("invalid_snapshot2")
    emulator.pauseGrpc() // Pause emulator's gRPC to prevent the nested dialog from opening immediately.

    val dialogPanel = ManageSnapshotsDialog(emulatorController, emulatorView = null)

    // Open the "Manage Snapshots" dialog.
    createDialogAndInteractWithIt({ dialogPanel.createWrapper(projectRule.project).show() }) { dlg1 ->
      val rootPane1 = dlg1.rootPane
      val ui1 = FakeUi(rootPane1)
      val table = ui1.getComponent<TableView<SnapshotInfo>>()
      /** The "Delete incompatible snapshots?" dialog opens when gRPC is resumed. */
      createDialogAndInteractWithIt({ emulator.resumeGrpc() }) { dlg2 ->
        assertThat(table.items).hasSize(4) // 1 QuickBoot + 1 valid + 2 invalid.
        val rootPane2 = dlg2.rootPane
        val ui2 = FakeUi(rootPane2)
        val doNotAskCheckBox = ui2.getComponent<JCheckBox>()
        assertThat(doNotAskCheckBox.isSelected).isFalse()
        doNotAskCheckBox.isSelected = true
        val keepButton = ui2.getComponent<JButton> { it.text == "Keep" }
        ui2.clickOn(keepButton)
      }
      assertThat(table.items).hasSize(4) // 1 QuickBoot + 1 valid + 2 invalid.
      assertThat(table.items.count { !it.isValid }).isEqualTo(2) // The two invalid snapshots were preserved.
      // Close the "Manage Snapshots" dialog.
      ui1.clickOn(rootPane1.defaultButton)
    }

    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
  }

  @Test
  fun testInvalidSnapshotsAutomaticDeletion() {
    EmulatorSettings.getInstance().snapshotAutoDeletionPolicy = SnapshotAutoDeletionPolicy.DELETE_AUTOMATICALLY
    emulator.createSnapshot("valid_snapshot")
    emulator.createInvalidSnapshot("invalid_snapshot1")
    emulator.createInvalidSnapshot("invalid_snapshot2")

    val dialogPanel = ManageSnapshotsDialog(emulatorController, emulatorView = null)

    // Open the "Manage Snapshots" dialog.
    createDialogAndInteractWithIt({ dialogPanel.createWrapper(projectRule.project).show() }) { dlg1 ->
      val rootPane1 = dlg1.rootPane
      val ui1 = FakeUi(rootPane1)
      val table = ui1.getComponent<TableView<SnapshotInfo>>()
      // Wait for the snapshot list to be populated.
      waitForCondition(2, TimeUnit.SECONDS) { table.items.isNotEmpty() }
      assertThat(table.items).hasSize(2) // The two invalid snapshots were deleted automatically.
      // Close the "Manage Snapshots" dialog.
      ui1.clickOn(rootPane1.defaultButton)
    }

    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
  }

  @Test
  fun testInvalidSnapshotsNoDeletion() {
    EmulatorSettings.getInstance().snapshotAutoDeletionPolicy = SnapshotAutoDeletionPolicy.DO_NOT_DELETE
    emulator.createSnapshot("valid_snapshot")
    emulator.createInvalidSnapshot("invalid_snapshot1")
    emulator.createInvalidSnapshot("invalid_snapshot2")

    val dialogPanel = ManageSnapshotsDialog(emulatorController, emulatorView = null)

    // Open the "Manage Snapshots" dialog.
    createDialogAndInteractWithIt({ dialogPanel.createWrapper(projectRule.project).show() }) { dlg1 ->
      val rootPane1 = dlg1.rootPane
      val ui1 = FakeUi(rootPane1)
      val table = ui1.getComponent<TableView<SnapshotInfo>>()
      // Wait for the snapshot list to be populated.
      waitForCondition(2, TimeUnit.SECONDS) { table.items.isNotEmpty() }
      assertThat(table.items).hasSize(4) // The two invalid snapshots were deleted automatically.
      // Close the "Manage Snapshots" dialog.
      ui1.clickOn(rootPane1.defaultButton)
    }

    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
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
  private fun getGoldenFile(name: String): File {
    return TestUtils.getWorkspaceRoot().toPath().resolve("$GOLDEN_FILE_PATH/${name}.png").toFile()
  }
}

private const val SNAPSHOT_NAME_COLUMN_INDEX = 0
private const val USE_TO_BOOT_COLUMN_INDEX = 3

private const val GOLDEN_FILE_PATH = "tools/adt/idea/emulator/testData/ManageSnapshotsDialogTest/golden"

