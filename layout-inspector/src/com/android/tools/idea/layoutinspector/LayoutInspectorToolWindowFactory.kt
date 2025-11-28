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

import com.android.tools.adtui.actions.ZoomType
import com.android.tools.adtui.workbench.WorkBench
import com.android.tools.idea.concurrency.createCoroutineScope
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.layoutinspector.metrics.LayoutInspectorMetrics
import com.android.tools.idea.layoutinspector.model.AndroidWindow
import com.android.tools.idea.layoutinspector.model.NotificationModel
import com.android.tools.idea.layoutinspector.model.StatusNotificationAction
import com.android.tools.idea.layoutinspector.pipeline.InspectorClientLauncher
import com.android.tools.idea.layoutinspector.properties.LayoutInspectorPropertiesPanelDefinition
import com.android.tools.idea.layoutinspector.runningdevices.LayoutInspectorManager
import com.android.tools.idea.layoutinspector.runningdevices.SPLITTER_KEY
import com.android.tools.idea.layoutinspector.runningdevices.actions.UiConfig
import com.android.tools.idea.layoutinspector.runningdevices.ui.STATE_READ_SPLITTER_NAME
import com.android.tools.idea.layoutinspector.runningdevices.ui.ToolbarState
import com.android.tools.idea.layoutinspector.runningdevices.ui.createLayoutInspectorPanel
import com.android.tools.idea.layoutinspector.runningdevices.ui.createToolbarPanel
import com.android.tools.idea.layoutinspector.runningdevices.ui.rendering.EmbeddedRendererModel
import com.android.tools.idea.layoutinspector.runningdevices.ui.rendering.StandaloneRendererPanel
import com.android.tools.idea.layoutinspector.runningdevices.ui.rendering.navigateToSelectedViewFromRendererDoubleClick
import com.android.tools.idea.layoutinspector.settings.LayoutInspectorConfigurable
import com.android.tools.idea.layoutinspector.settings.LayoutInspectorSettings
import com.android.tools.idea.layoutinspector.stateinspection.createStateInspectionPanel
import com.android.tools.idea.layoutinspector.tree.LayoutInspectorTreePanelDefinition
import com.android.tools.idea.layoutinspector.ui.DeviceViewPanel
import com.android.tools.idea.layoutinspector.ui.InspectorBanner
import com.android.tools.idea.layoutinspector.ui.LayoutInspectorRootPanel
import com.android.tools.idea.layoutinspector.ui.ZoomableContainer
import com.android.tools.idea.layoutinspector.ui.toolbar.actions.TargetSelectionActionFactory
import com.google.common.annotations.VisibleForTesting
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorEvent.DynamicLayoutInspectorEventType
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.JBColor
import com.intellij.ui.OnePixelSplitter
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import icons.StudioIcons
import java.awt.BorderLayout
import javax.swing.JPanel
import javax.swing.event.HyperlinkEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    val layoutInspectorUi =
      if (StudioFlags.DYNAMIC_LAYOUT_INSPECTOR_STANDALONE_V2.get()) {
        createNewStandaloneLayoutInspectorUi(disposable, project, layoutInspector)
      } else {
        createOldStandaloneLayoutInspectorUi(disposable, project, layoutInspector)
      }

    val content = toolWindow.contentManager.factory.createContent(layoutInspectorUi, "", true)
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

    showEmbeddedLayoutInspectorBanner(
      layoutInspector.inspectorModel.project,
      layoutInspector.notificationModel,
      layoutInspector.coroutineScope,
    )
  }

  private fun createOldStandaloneLayoutInspectorUi(
    disposable: Disposable,
    project: Project,
    layoutInspector: LayoutInspector,
  ): JPanel {
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

    val splitPanel =
      OnePixelSplitter(true, SPLITTER_KEY, 0.65f).apply {
        name = STATE_READ_SPLITTER_NAME
        firstComponent = workbench
        secondComponent = createStateInspectionPanel(layoutInspector, disposable)
        setBlindZone { JBUI.insets(0, 1) }
      }

    val rootPanel = LayoutInspectorRootPanel(splitPanel, layoutInspector)

    return JPanel(BorderLayout()).apply {
      add(InspectorBanner(disposable, layoutInspector.notificationModel), BorderLayout.NORTH)
      add(rootPanel, BorderLayout.CENTER)
    }
  }

  private fun createNewStandaloneLayoutInspectorUi(
    disposable: Disposable,
    project: Project,
    layoutInspector: LayoutInspector,
  ): LayoutInspectorRootPanel {
    val scope = disposable.createCoroutineScope()
    val processPicker = TargetSelectionActionFactory.getAction(layoutInspector)

    val renderModel =
      EmbeddedRendererModel(
        parentDisposable = disposable,
        // In on-device rendering we don't want to filter nodes by display id. There is no
        // concept of display there, since everything is rendered on-top of the views.
        displayId = null,
        inspectorModel = layoutInspector.inspectorModel,
        treeSettings = layoutInspector.treeSettings,
        renderSettings = layoutInspector.renderSettings,
        navigateToSelectedViewOnDoubleClick = {
          layoutInspector.navigateToSelectedViewFromRendererDoubleClick()
        },
      )

    val renderPanel =
      StandaloneRendererPanel(disposable = disposable, scope = scope, renderModel = renderModel)

    val container =
      ZoomableContainer(
        disposable = disposable,
        contentPanel = renderPanel,
        getZoomPercent = { layoutInspector.renderSettings.scalePercent },
        setZoomPercent = { layoutInspector.renderSettings.scalePercent = it },
      )

    // The main panel is passed as target component to createToolbarPanel. This is needed to make
    // sure that all actions in the toolbar can resolve Layout Inspector from the data context
    // provided by LayoutInspectorRootPanel.
    val mainPanel = BorderLayoutPanel()

    val toolbarState = ToolbarState(showTitle = false, leftAlightToolbar = true)
    val toolbar =
      createToolbarPanel(
        disposable = disposable,
        targetComponent = mainPanel,
        layoutInspector = layoutInspector,
        processPicker = processPicker?.dropDownAction,
        extraActions = emptyList(),
        toolbarState = toolbarState,
      )
    toolbar.border = JBUI.Borders.customLineBottom(JBColor.border())

    mainPanel.apply {
      addToTop(toolbar)
      addToCenter(container)
    }

    val rootPanel =
      createLayoutInspectorPanel(
        project = project,
        disposable = disposable,
        layoutInspector = layoutInspector,
        uiConfig = UiConfig.VERTICAL,
        centerPanel = mainPanel,
        toolbarPanel = null,
      )

    scope.launch {
      toolbarState.isDeepInspectEnabled.collect { renderModel.setInterceptClicks(it) }
    }

    scope.launch { toolbarState.overlayImage.collect { renderModel.setOverlay(it) } }

    scope.launch {
      toolbarState.overlayTransparency.collect { renderModel.setOverlayTransparency(it) }
    }

    layoutInspector.inspectorModel.addModificationListener { oldWindow, newWindow, _ ->
      if (oldWindow == null && newWindow != null) {
        // Zoom to fit each time we go from rendering nothing to something
        container.zoom(ZoomType.FIT)
      }
    }

    val renderSettings = layoutInspector.renderSettings
    renderSettings.modificationListeners.add {
      val client = layoutInspector.currentClient
      if (client.inLiveMode) {
        // The current agent protocol requires bitmaps to be resized based on to the current scale
        client.updateScreenshotType(
          type = AndroidWindow.ImageType.BITMAP_AS_REQUESTED,
          scale = renderSettings.scaleFraction.toFloat(),
        )
      }
    }

    layoutInspector.inspectorModel.addConnectionListener { client ->
      if (client.isConnected) {
        // Right after connecting the agent has a default scale of 1.0, we should update it to the
        // scale of the rendering
        client.updateScreenshotType(
          type = AndroidWindow.ImageType.BITMAP_AS_REQUESTED,
          scale = renderSettings.scaleFraction.toFloat(),
        )
      }
    }

    return rootPanel
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

private const val showBannerDefaultValue: Boolean = true
private const val SHOW_BANNER_KEY =
  "com.android.tools.idea.layoutinspector.try.embedded.layout.inspector.key"

@VisibleForTesting const val BANNER_STRING_ID = "enable.embedded.layout.inspector.banner"

@VisibleForTesting
fun showEmbeddedLayoutInspectorBanner(
  project: Project,
  notificationModel: NotificationModel,
  scope: CoroutineScope,
  shouldShowBanner: () -> Boolean = {
    PropertiesComponent.getInstance().getBoolean(SHOW_BANNER_KEY, showBannerDefaultValue)
  },
  setShouldShowBanner: (Boolean) -> Unit = {
    PropertiesComponent.getInstance().setValue(SHOW_BANNER_KEY, it, showBannerDefaultValue)
  },
  activateEmbeddedLayoutInspector: (Project) -> Unit = {
    activateEmbeddedLayoutInspectorToolWindow(project)
  },
) {
  if (!shouldShowBanner()) {
    return
  }

  notificationModel.addNotification(
    id = BANNER_STRING_ID,
    text = LayoutInspectorBundle.message(BANNER_STRING_ID),
    status = EditorNotificationPanel.Status.Info,
    sticky = true,
    actions =
      listOf(
        StatusNotificationAction(LayoutInspectorBundle.message("do.not.show.again")) { notification
          ->
          setShouldShowBanner(false)
          notificationModel.removeNotification(notification.id)
        },
        StatusNotificationAction(LayoutInspectorBundle.message("enable")) {
          // launch the coroutine first, since showSettingsDialog is blocking
          scope.launch {
            val settings = LayoutInspectorSettings.getInstance()
            settings.embeddedLayoutInspectorChanges.collect { enabled ->
              if (enabled == true) {
                // if embedded LI is enabled, activate running devices toolbar
                withContext(Dispatchers.EDT) { activateEmbeddedLayoutInspector(project) }
              }
            }
          }

          // show settings screen
          ShowSettingsUtil.getInstance()
            .showSettingsDialog(project, LayoutInspectorConfigurable::class.java)
        },
      ),
  )
}
