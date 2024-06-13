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

import com.android.tools.idea.IdeInfo
import com.android.tools.idea.execution.common.DeployableToDevice
import com.android.tools.idea.run.LaunchCompatibility
import com.android.tools.idea.util.CommonAndroidUtil
import com.google.common.annotations.VisibleForTesting
import com.intellij.execution.RunManager
import com.intellij.ide.HelpTooltip
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.ComboBoxAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.util.Key
import com.intellij.ui.ExperimentalUI
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Font
import java.beans.PropertyChangeEvent
import java.util.function.IntUnaryOperator
import javax.swing.GroupLayout
import javax.swing.JComponent
import javax.swing.JPanel

class DeviceAndSnapshotComboBoxAction
internal constructor(
  private val devicesService: (Project) -> DeploymentTargetDevicesService = Project::service,
  private val devicesSelectedService: (Project) -> DevicesSelectedService = Project::service,
  private val executionTargetService: (Project) -> ExecutionTargetService = Project::service,
  private val runManager: (Project) -> RunManager = RunManager.Companion::getInstance,
) : ComboBoxAction() {

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
        ExperimentalUI.isNewUI() -> UIUtil.getLabelFont(UIUtil.FontSize.NORMAL)
        else -> super.getFont()
      }
  }

  override fun shouldShowDisabledActions() = true

  override fun createPopupActionGroup(
    button: JComponent,
    context: DataContext,
  ): DefaultActionGroup {
    val project = context.getData(CommonDataKeys.PROJECT)!!
    return createDeviceSelectorActionGroup(
      devicesService(project).loadedDevicesOrNull() ?: throw IllegalStateException()
    )
  }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun update(event: AnActionEvent) {
    val presentation = event.presentation
    val project = event.project
    if (project == null || !CommonAndroidUtil.getInstance().isAndroidProject(project)) {
      presentation.setVisible(false)
      return
    }
    presentation.setVisible(true)
    presentation.setDescription(null)

    val configurationAndSettings = runManager(project).selectedConfiguration
    if (configurationAndSettings == null) {
      presentation.setEnabled(false)
      presentation.setDescription("Add a run/debug configuration")
      return
    }

    val configuration = configurationAndSettings.configuration
    if (!DeployableToDevice.deploysToLocalDevice(configuration)) {
      presentation.setEnabled(false)
      if (IdeInfo.getInstance().isAndroidStudio) {
        presentation.setDescription(
          "Not applicable for the \"${configuration.name}\" configuration"
        )
      } else {
        presentation.setVisible(false)
      }
      return
    }

    if (devicesService(project).devices.value is LoadingState.Loading) {
      presentation.setEnabled(false)
      presentation.text = "Loading Devices..."
      return
    }

    presentation.setEnabled(true)

    val devicesAndTargets = devicesSelectedService(project).devicesAndTargets
    when (event.place) {
      ActionPlaces.MAIN_TOOLBAR,
      ActionPlaces.NAVIGATION_BAR_TOOLBAR -> {
        Updater(presentation, devicesAndTargets).update()
      }
      else -> {
        presentation.setIcon(null)
        presentation.text = "Select Device..."
      }
    }

    event.updateSession.compute(this, "Set active device", ActionUpdateThread.EDT) {
      if (presentation.isVisible) {
        setActiveExecutionTarget(project, devicesAndTargets.selectedTargets)
      }
    }
  }

  private fun setActiveExecutionTarget(project: Project, targets: List<DeploymentTarget>) {
    executionTargetService(project).activeTarget =
      DeviceAndSnapshotComboBoxExecutionTarget(targets, devicesService(project))
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
