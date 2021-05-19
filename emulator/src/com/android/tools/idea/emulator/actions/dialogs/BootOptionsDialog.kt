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

import com.android.tools.idea.concurrency.executeOnPooledThread
import com.android.tools.idea.emulator.invokeLaterInAnyModalityState
import com.google.common.util.concurrent.ListenableFuture
import com.intellij.ide.actions.RevealFileAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBRadioButton
import com.intellij.ui.components.dialog
import com.intellij.ui.layout.GrowPolicy
import com.intellij.ui.layout.buttonGroup
import com.intellij.ui.layout.panel
import com.intellij.ui.layout.selected
import com.intellij.util.concurrency.EdtExecutorService
import java.awt.Component
import java.awt.Container
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.FocusListener
import java.text.Collator
import javax.swing.JRootPane

/**
 * Dialog for specifying AVD boot snapshot.
 */
internal class BootOptionsDialog(
  private val bootMode: BootMode,
  private val snapshotsFuture: ListenableFuture<List<SnapshotInfo>>,
  private val snapshotManager: SnapshotManager
) {

  var bootType = bootMode.bootType
    private set
  val bootSnapshot
    get() = snapshotListModel.selected?.snapshotId

  private val snapshotListModel = CollectionComboBoxModel<SnapshotInfo>()
  private lateinit var bootFromSnapshotRadio: JBRadioButton
  private lateinit var snapshotListCombo: ComboBox<SnapshotInfo>
  private var snapshotListUpdateCount = 0
  private val snapshotComparator = compareBy(Collator.getInstance(), SnapshotInfo::displayName)

  /**
   * Creates contents of the dialog.
   */
  private fun createPanel(): DialogPanel {
    return panel {
      row {
        label("Choose how the virtual device boots on restart")
      }
      buttonGroup(::bootType) {
        row {
          radioButton("Cold boot", BootType.COLD,
                      comment = "Start the device as if it were completely shut down, clearing both RAM and cache contents")
            .withValidationOnInput { validate() }
        }
        row {
          radioButton("Quick boot (default)", BootType.QUICK,
                      comment = "Start from the state that was saved when the device last shut down")
            .withValidationOnInput { validate() }
        }
        row {
          bootFromSnapshotRadio = radioButton("Boot from snapshot", BootType.SNAPSHOT,
                                              comment = "Restore state from the selected snapshot on start-up")
            .withValidationOnInput { validate() }
            .component

          row {
            snapshotListCombo = comboBox(snapshotListModel,
                     { snapshotListModel.selected },
                     { snapshotListModel.selectedItem = it },
                     renderer = SimpleListCellRenderer.create("Please select a snapshot") { it?.displayName })
              .withValidationOnInput { validate() }
              .withValidationOnApply { validate() }
              .growPolicy(GrowPolicy.MEDIUM_TEXT)
              .enableIf(bootFromSnapshotRadio.selected)
              .component
          }
        }
      }
      if (RevealFileAction.isSupported()) {
        val fileManagerName = RevealFileAction.getFileManagerName()
        // Two spaces before the <a> tag are required to make the label appear correctly on Mac.
        noteRow("To delete or rename snapshots, open in  <a href=''>${fileManagerName}</a>.") {
          RevealFileAction.openDirectory(snapshotManager.avdFolder.resolve("snapshots").toFile())
        }
      }
    }.also {
      snapshotsFuture.addListener(Runnable { updateSnapshotList(snapshotsFuture.get()) }, EdtExecutorService.getInstance())
    }
  }

  /**
   * Creates the dialog wrapper.
   */
  fun createWrapper(project: Project): DialogWrapper {
    return dialog(
        title = "Boot Options",
        resizable = true,
        panel = createPanel(),
        project = project)
      .also {
        invokeLaterInAnyModalityState {
          // Install a focus listener to update the snapshot list when the focus comes from
          // outside of the current JVM. Since the focus listener is not triggered when
          // installed on the dialog window, we install it on all focusable components.
          installFocusListener(it.rootPane)
        }
      }
  }

  /**
   * Installs a focus listener on all focusable components under [rootPane].
   */
  private fun installFocusListener(rootPane: JRootPane) {
    val listener = object : FocusAdapter() {
      override fun focusGained(event: FocusEvent) {
        if (event.oppositeComponent == null) {
          // Focus came from outside of the current JVM.
          if (snapshotListUpdateCount > 0) {
            executeOnPooledThread {
              val snapshots = snapshotManager.fetchSnapshotList(excludeQuickBoot = true)
              invokeLaterInAnyModalityState {
                updateSnapshotList(snapshots)
              }
            }
          }
        }
      }
    }

    installFocusListener(rootPane, listener)
  }

  private fun installFocusListener(component: Component, listener: FocusListener) {
    if (component.isFocusable) {
      component.addFocusListener(listener)
    }
    if (component is Container) {
      for (child in component.components) {
        installFocusListener(child, listener)
      }
    }
  }

  private fun validate(): ValidationInfo? {
    if (bootFromSnapshotRadio.isSelected && snapshotListModel.selected == null) {
      return ValidationInfo("No snapshot selected", snapshotListCombo).asWarning()
    }
    return null
  }

  private fun updateSnapshotList(snapshots: List<SnapshotInfo>) {
    val selectedId = if (snapshotListUpdateCount == 0) bootMode.bootSnapshotId else snapshotListModel.selected?.snapshotId
    snapshotListModel.removeAll()
    snapshotListModel.addAll(0, snapshots.sortedWith(snapshotComparator))
    snapshotListModel.selectedItem = snapshots.find { it.snapshotId == selectedId }
    bootFromSnapshotRadio.isEnabled = snapshots.isNotEmpty()
    snapshotListUpdateCount++
  }
}