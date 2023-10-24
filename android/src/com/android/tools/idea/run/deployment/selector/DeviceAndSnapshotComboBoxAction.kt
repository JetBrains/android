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
package com.android.tools.idea.run.deployment.selector

import com.android.tools.idea.run.LaunchCompatibility
import com.android.tools.idea.run.deployment.selector.Target.Companion.filterDevices
import com.google.common.annotations.VisibleForTesting
import com.intellij.execution.RunManager
import com.intellij.ide.HelpTooltip
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.ComboBoxAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.util.Key
import com.intellij.ui.NewUI
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Font
import java.beans.PropertyChangeEvent
import java.util.Optional
import java.util.function.IntUnaryOperator
import javax.swing.GroupLayout
import javax.swing.JComponent
import javax.swing.JPanel

class DeviceAndSnapshotComboBoxAction
internal constructor(
  private val getDevicesGetter: (Project) -> AsyncDevicesGetter = Project::service,
  private val devicesSelectedService: (Project) -> DevicesSelectedService = Project::service,
  private val executionTargetService: (Project) -> ExecutionTargetService = Project::service,
  private val newSelectMultipleDevicesDialog: (Project, List<Device>) -> DialogWrapper =
    { project, devices ->
      SelectMultipleDevicesDialog(project, devices)
    },
  private val runManager: (Project) -> RunManager = RunManager.Companion::getInstance,
) : ComboBoxAction() {

  internal fun getDevices(project: Project): Optional<List<Device>> =
    getDevicesGetter(project).get()

  internal fun setTargetSelectedWithComboBox(project: Project, target: Target) {
    devicesSelectedService(project).setTargetSelectedWithComboBox(target)
    setActiveExecutionTarget(project, setOf(target))
  }

  internal fun getSelectedDevices(project: Project): List<Device> {
    val devices = getDevices(project).orElse(emptyList())
    return filterDevices(getSelectedTargets(project, devices), devices)
  }

  internal fun getSelectedTargets(project: Project): Optional<Set<Target>> {
    return getDevices(project).map { devices -> getSelectedTargets(project, devices) }
  }

  internal fun getSelectedTargets(project: Project, devices: List<Device>): Set<Target> {
    val service = devicesSelectedService(project)
    return when {
      service.isMultipleDevicesSelectedInComboBox -> service.getTargetsSelectedWithDialog(devices)
      else ->
        service.getTargetSelectedWithComboBox(devices).map { setOf(it) }.orElseGet { emptySet() }
    }
  }

  fun selectMultipleDevices(project: Project) {
    val devices: List<Device> = getDevicesGetter(project).get().orElseThrow { AssertionError() }
    if (!newSelectMultipleDevicesDialog(project, devices).showAndGet()) {
      return
    }
    val service = devicesSelectedService(project)
    service.isMultipleDevicesSelectedInComboBox =
      service.getTargetsSelectedWithDialog(devices).isNotEmpty()
    setActiveExecutionTarget(project, getSelectedTargets(project, devices))
  }

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    return createCustomComponent(presentation) { JBUI.scale(it) }
  }

  @VisibleForTesting
  fun createCustomComponent(presentation: Presentation, scale: IntUnaryOperator): JComponent {
    val panel: JComponent = JPanel(null)
    val layout = GroupLayout(panel)
    val button = createComboBoxButton(presentation)
    val horizontalGroup =
      layout
        .createSequentialGroup()
        .addComponent(button, 0, GroupLayout.DEFAULT_SIZE, scale.applyAsInt(250))
        .addGap(scale.applyAsInt(3))
    val verticalGroup: GroupLayout.Group =
      layout
        .createSequentialGroup()
        .addGap(0, 0, Short.MAX_VALUE.toInt())
        .addComponent(button)
        .addGap(0, 0, Short.MAX_VALUE.toInt())
    layout.setHorizontalGroup(horizontalGroup)
    layout.setVerticalGroup(verticalGroup)
    panel.setLayout(layout)
    panel.setOpaque(false)
    return panel
  }

  override fun createComboBoxButton(presentation: Presentation): ComboBoxButton {
    return DeviceAndSnapshotComboBoxButton(presentation)
  }

  private inner class DeviceAndSnapshotComboBoxButton(presentation: Presentation) :
    ComboBoxButton(presentation) {
    init {
      setName("deviceAndSnapshotComboBoxButton")
    }

    override fun presentationChanged(event: PropertyChangeEvent) {
      super.presentationChanged(event)
      val name = event.propertyName
      if (name != LAUNCH_COMPATIBILITY_KEY.toString()) {
        return
      }
      HelpTooltip.dispose(this)
      val value = event.newValue ?: return
      val tooltip = HelpTooltip()
      if (!updateTooltip((value as LaunchCompatibility), tooltip)) {
        return
      }
      tooltip.installOn(this)
    }

    override fun createPopup(runnable: Runnable?): JBPopup {
      val context = dataContext
      return Popup(createPopupActionGroup(this, context), context, runnable!!)
    }

    @Suppress("UnstableApiUsage")
    override fun getFont(): Font =
      when {
        NewUI.isEnabled() -> UIUtil.getLabelFont(UIUtil.FontSize.NORMAL)
        else -> super.getFont()
      }
  }

  override fun shouldShowDisabledActions() = true

  override fun createPopupActionGroup(
    button: JComponent,
    context: DataContext
  ): DefaultActionGroup {
    val project = context.getData(CommonDataKeys.PROJECT)!!
    return PopupActionGroup(getDevices(project).orElseThrow { AssertionError() }, this)
  }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun update(event: AnActionEvent) {
    val presentation = event.presentation
    val project = event.project
    if (project == null) {
      presentation.setVisible(false)
      return
    }
    val optionalDevices = getDevices(project)
    if (optionalDevices.isEmpty) {
      presentation.setEnabled(false)
      presentation.text = "Loading Devices..."
      return
    }
    val devices = optionalDevices.get()
    val updater =
      Updater(
        project = project,
        presentation = presentation,
        place = event.place,
        devicesSelectedService = devicesSelectedService(project),
        devices = devices,
        configurationAndSettings = runManager(project).selectedConfiguration
      )
    updater.update()
    event.updateSession.compute(this, "Set active device", ActionUpdateThread.EDT) {
      if (presentation.isVisible) {
        setActiveExecutionTarget(project, getSelectedTargets(project, devices))
      }
    }
  }

  private fun setActiveExecutionTarget(project: Project, targets: Set<Target>) {
    executionTargetService(project).activeTarget =
      DeviceAndSnapshotComboBoxExecutionTarget(targets, getDevicesGetter(project))
  }

  companion object {
    /** The key for the LaunchCompatibility presentation client property */
    @JvmField
    val LAUNCH_COMPATIBILITY_KEY =
      Key<LaunchCompatibility>("DeviceAndSnapshotComboBoxAction.launchCompatibility")

    @JvmStatic
    val instance: DeviceAndSnapshotComboBoxAction
      get() =
        ActionManager.getInstance().getAction("DeviceAndSnapshotComboBox")
          as DeviceAndSnapshotComboBoxAction
  }
}
