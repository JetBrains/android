/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector

import com.android.tools.adtui.workbench.WorkBench
import com.android.tools.idea.flags.ExperimentalSettingsConfigurable
import com.android.tools.idea.layoutinspector.metrics.LayoutInspectorMetrics
import com.android.tools.idea.layoutinspector.model.NotificationModel
import com.android.tools.idea.layoutinspector.model.StatusNotificationAction
import com.android.tools.idea.layoutinspector.pipeline.InspectorClient
import com.android.tools.idea.layoutinspector.pipeline.InspectorClientLauncher
import com.android.tools.idea.layoutinspector.properties.LayoutInspectorPropertiesPanelDefinition
import com.android.tools.idea.layoutinspector.runningdevices.actions.EMBEDDED_LAYOUT_INSPECTOR_MIN_API
import com.android.tools.idea.layoutinspector.runningdevices.getRunningDevicesExistingTabsDeviceSerialNumber
import com.android.tools.idea.layoutinspector.settings.LayoutInspectorSettings
import com.android.tools.idea.layoutinspector.tree.LayoutInspectorTreePanelDefinition
import com.android.tools.idea.layoutinspector.ui.DeviceViewPanel
import com.android.tools.idea.layoutinspector.ui.InspectorBanner
import com.google.common.annotations.VisibleForTesting
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorEvent.DynamicLayoutInspectorEventType
import com.intellij.ide.DataManager
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.ui.EditorNotificationPanel
import java.awt.BorderLayout
import javax.swing.JPanel
import javax.swing.event.HyperlinkEvent

const val LAYOUT_INSPECTOR_TOOL_WINDOW_ID = "Layout Inspector"

/** ToolWindowFactory: For creating a layout inspector tool window for the project. */
class LayoutInspectorToolWindowFactory : ToolWindowFactory {

  override fun isApplicable(project: Project): Boolean {
    return !LayoutInspectorSettings.getInstance().embeddedLayoutInspectorEnabled
  }

  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    val disposable = toolWindow.disposable
    val layoutInspector = LayoutInspectorProjectService.getInstance(project).getLayoutInspector()
    val devicePanel = createDevicePanel(disposable, layoutInspector)

    layoutInspector.inspectorModel.addConnectionListener { client ->
      if (client != null) {
        showTryEmbeddedLayoutInspectorBanner(
          layoutInspector.inspectorModel.project,
          layoutInspector.notificationModel,
          client
        )
      }
    }

    val workbench =
      WorkBench<LayoutInspector>(project, LAYOUT_INSPECTOR_TOOL_WINDOW_ID, null, disposable).apply {
        init(
          devicePanel,
          layoutInspector,
          listOf(LayoutInspectorTreePanelDefinition(), LayoutInspectorPropertiesPanelDefinition()),
          false
        )
      }

    DataManager.registerDataProvider(workbench, dataProviderForLayoutInspector(layoutInspector))

    val contentPanel =
      JPanel(BorderLayout()).apply {
        add(InspectorBanner(disposable, layoutInspector.notificationModel), BorderLayout.NORTH)
        add(workbench, BorderLayout.CENTER)
      }

    val content = toolWindow.contentManager.factory.createContent(contentPanel, "", true)
    toolWindow.contentManager.addContent(content)

    project.messageBus
      .connect(toolWindow.disposable)
      .subscribe(
        ToolWindowManagerListener.TOPIC,
        LayoutInspectorToolWindowManagerListener(
          project,
          toolWindow,
          layoutInspector,
          layoutInspector.launcher!!
        )
      )
  }

  private fun createDevicePanel(
    disposable: Disposable,
    layoutInspector: LayoutInspector
  ): DeviceViewPanel {
    val deviceViewPanel =
      DeviceViewPanel(layoutInspector = layoutInspector, disposableParent = disposable)

    // notify DeviceViewPanel that a new foreground process showed up
    layoutInspector.foregroundProcessDetection?.addForegroundProcessListener { _, _, isDebuggable ->
      deviceViewPanel.onNewForegroundProcess(isDebuggable)
    }

    return deviceViewPanel
  }
}

/** Listen to state changes for the create layout inspector tool window. */
class LayoutInspectorToolWindowManagerListener
@VisibleForTesting
constructor(
  private val project: Project,
  private val clientLauncher: InspectorClientLauncher,
  private val stopInspectors: () -> Unit = {},
  private var wasWindowVisible: Boolean = false,
) : ToolWindowManagerListener {

  internal constructor(
    project: Project,
    toolWindow: ToolWindow,
    layoutInspector: LayoutInspector,
    clientLauncher: InspectorClientLauncher,
  ) : this(project, clientLauncher, { layoutInspector.stopInspector() }, toolWindow.isVisible)

  override fun stateChanged(toolWindowManager: ToolWindowManager) {
    val window = toolWindowManager.getToolWindow(LAYOUT_INSPECTOR_TOOL_WINDOW_ID) ?: return
    val isWindowVisible = window.isVisible // Layout Inspector tool window is expanded.
    val windowVisibilityChanged = isWindowVisible != wasWindowVisible
    wasWindowVisible = isWindowVisible
    if (windowVisibilityChanged) {
      if (isWindowVisible) {
        LayoutInspectorMetrics.logEvent(DynamicLayoutInspectorEventType.OPEN)
      } else if (clientLauncher.activeClient.isConnected) {
        toolWindowManager.notifyByBalloon(
          LAYOUT_INSPECTOR_TOOL_WINDOW_ID,
          MessageType.INFO,
          """
          <b>Layout Inspection</b> is running in the background.<br>
          You can either <a href="stop">stop</a> it, or leave it running and resume your session later.
        """
            .trimIndent(),
          null
        ) { hyperlinkEvent ->
          if (hyperlinkEvent.eventType == HyperlinkEvent.EventType.ACTIVATED) {
            stopInspectors()
          }
        }
      }
      clientLauncher.enabled = isWindowVisible
    }
  }
}

private const val SHOW_TRY_EMBEDDED_INSPECTOR_KEY =
  "com.android.tools.idea.layoutinspector.try.embedded.layout.inspector.key"
const val TRY_EMBEDDED_INSPECTOR_BANNER_ID = "try.embedded.layout.inspector"

private fun showTryEmbeddedLayoutInspectorBanner(
  project: Project,
  notificationModel: NotificationModel,
  client: InspectorClient
) {
  val defaultValue = true
  val shouldShowBanner = {
    PropertiesComponent.getInstance().getBoolean(SHOW_TRY_EMBEDDED_INSPECTOR_KEY, defaultValue)
  }
  val setValue: (Boolean) -> Unit = {
    PropertiesComponent.getInstance().setValue(SHOW_TRY_EMBEDDED_INSPECTOR_KEY, it, defaultValue)
  }

  val deviceSerialNumber = client.process.device.serial
  val deviceApiLevel = client.process.device.apiLevel

  if (
    shouldShowBanner() &&
      isDeviceInRunningDevicesToolWindow(project, deviceSerialNumber) &&
      deviceApiLevel >= EMBEDDED_LAYOUT_INSPECTOR_MIN_API
  ) {
    notificationModel.addNotification(
      id = TRY_EMBEDDED_INSPECTOR_BANNER_ID,
      text = LayoutInspectorBundle.message(TRY_EMBEDDED_INSPECTOR_BANNER_ID),
      status = EditorNotificationPanel.Status.Info,
      actions =
        listOf(
          StatusNotificationAction(LayoutInspectorBundle.message("do.not.show.again")) {
            notification ->
            setValue(false)
            notificationModel.removeNotification(notification.id)
          },
          StatusNotificationAction(LayoutInspectorBundle.message("turn.on")) {
            ShowSettingsUtil.getInstance()
              .showSettingsDialog(project, ExperimentalSettingsConfigurable::class.java)
          }
        )
    )
  }
}

private fun isDeviceInRunningDevicesToolWindow(
  project: Project,
  requiredDeviceId: String
): Boolean {
  val devicesIds = project.getRunningDevicesExistingTabsDeviceSerialNumber()
  return devicesIds.map { it.serialNumber }.contains(requiredDeviceId)
}
