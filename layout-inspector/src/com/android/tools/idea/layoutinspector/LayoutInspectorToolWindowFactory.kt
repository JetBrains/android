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
import com.android.tools.idea.layoutinspector.metrics.LayoutInspectorMetrics
import com.android.tools.idea.layoutinspector.pipeline.InspectorClientLauncher
import com.android.tools.idea.layoutinspector.properties.LayoutInspectorPropertiesPanelDefinition
import com.android.tools.idea.layoutinspector.runningdevices.LayoutInspectorManager
import com.android.tools.idea.layoutinspector.settings.LayoutInspectorSettings
import com.android.tools.idea.layoutinspector.tree.LayoutInspectorTreePanelDefinition
import com.android.tools.idea.layoutinspector.ui.DeviceViewPanel
import com.android.tools.idea.layoutinspector.ui.InspectorBanner
import com.google.common.annotations.VisibleForTesting
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorEvent.DynamicLayoutInspectorEventType
import com.intellij.ide.DataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import icons.StudioIcons
import java.awt.BorderLayout
import javax.swing.JPanel
import javax.swing.event.HyperlinkEvent

const val LAYOUT_INSPECTOR_TOOL_WINDOW_ID = "Layout Inspector"

/** Registers Standalone Layout Inspector tool window and disables embedded Layout Inspector. */
fun registerLayoutInspectorToolWindow(project: Project) {
  val layoutInspector = LayoutInspectorProjectService.getInstance(project).getLayoutInspector()
  layoutInspector.stopInspector()

  // Disable embedded Layout Inspector UI.
  LayoutInspectorManager.getInstance(project).disable()

  // TODO can this be moved to ToolWindowFactory?
  // Start foreground process detection.
  layoutInspector.foregroundProcessDetection?.start()

  val toolWindowManager = ToolWindowManager.getInstance(project)
  toolWindowManager.registerToolWindow(LAYOUT_INSPECTOR_TOOL_WINDOW_ID) {
    anchor = ToolWindowAnchor.BOTTOM
    icon = StudioIcons.Shell.ToolWindows.CAPTURES
    contentFactory = LayoutInspectorToolWindowFactory()
    canCloseContent = false
  }
}

/** Unregisters Standalone Layout Inspector tool window. */
fun unregisterLayoutInspectorToolWindow(project: Project) {
  val layoutInspector = LayoutInspectorProjectService.getInstance(project).getLayoutInspector()
  layoutInspector.stopInspector()

  val toolWindowManager = ToolWindowManager.getInstance(project)
  toolWindowManager.unregisterToolWindow(LAYOUT_INSPECTOR_TOOL_WINDOW_ID)
}

/** ToolWindowFactory: For creating a layout inspector tool window for the project. */
class LayoutInspectorToolWindowFactory : ToolWindowFactory {

  override fun isApplicable(project: Project): Boolean {
    return !LayoutInspectorSettings.getInstance().embeddedLayoutInspectorEnabled
  }

  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    val disposable = toolWindow.disposable
    val layoutInspector = LayoutInspectorProjectService.getInstance(project).getLayoutInspector()
    val devicePanel = createDevicePanel(disposable, layoutInspector)

    val workbench =
      WorkBench<LayoutInspector>(project, LAYOUT_INSPECTOR_TOOL_WINDOW_ID, null, disposable).apply {
        init(
          devicePanel,
          layoutInspector,
          listOf(LayoutInspectorTreePanelDefinition(), LayoutInspectorPropertiesPanelDefinition()),
          false,
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
          layoutInspector.launcher!!,
        ),
      )
  }

  private fun createDevicePanel(
    disposable: Disposable,
    layoutInspector: LayoutInspector,
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
          null,
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
