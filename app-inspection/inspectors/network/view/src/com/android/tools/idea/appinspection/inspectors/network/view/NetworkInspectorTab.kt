/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.appinspection.inspectors.network.view

import com.android.tools.adtui.common.AdtUiUtils.DEFAULT_BOTTOM_BORDER
import com.android.tools.adtui.flat.FlatSeparator
import com.android.tools.adtui.model.AspectObserver
import com.android.tools.adtui.model.Range
import com.android.tools.adtui.model.StreamingTimeline
import com.android.tools.adtui.model.Timeline
import com.android.tools.adtui.stdui.CommonButton
import com.android.tools.adtui.stdui.CommonToggleButton
import com.android.tools.adtui.stdui.DefaultContextMenuItem
import com.android.tools.adtui.stdui.TooltipLayeredPane
import com.android.tools.idea.appinspection.inspector.api.AppInspectionIdeServices.Severity
import com.android.tools.idea.appinspection.inspectors.network.model.NetworkInspectorDataSource
import com.android.tools.idea.appinspection.inspectors.network.model.NetworkInspectorModel
import com.android.tools.idea.appinspection.inspectors.network.model.NetworkInspectorServices
import com.android.tools.idea.appinspection.inspectors.network.view.constants.DEFAULT_BACKGROUND
import com.android.tools.idea.appinspection.inspectors.network.view.constants.H4_FONT
import com.android.tools.idea.appinspection.inspectors.network.view.constants.TOOLBAR_HEIGHT
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.flags.StudioFlags
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.client.ClientSystemInfo
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ThreeComponentsSplitter
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.IconLoader
import com.intellij.util.ui.JBUI
import icons.StudioIcons
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.GridBagLayout
import java.awt.event.InputEvent.CTRL_DOWN_MASK
import java.awt.event.InputEvent.META_DOWN_MASK
import java.awt.event.KeyEvent
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import javax.swing.JPanel
import javax.swing.KeyStroke
import javax.swing.LayoutFocusTraversalPolicy
import javax.swing.SwingConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.VisibleForTesting

private const val ZOOM_IN = "Zoom in"
private const val ZOOM_OUT = "Zoom out"
private const val RESET_ZOOM = "Reset zoom"
private const val ZOOM_TO_SELECTION = "Zoom to selection"
private const val CLEAR_DATA = "Clear data"
private const val EXPORT_CONNECTIONS = "Export connections"
private const val ATTACH_LIVE = "Attach to live"
private const val DETACH_LIVE = "Detach live"
private val SHORTCUT_MODIFIER_MASK_NUMBER
  get() = if (ClientSystemInfo.isMac()) META_DOWN_MASK else CTRL_DOWN_MASK

class NetworkInspectorTab(
  private val project: Project,
  componentsProvider: UiComponentsProvider,
  dataSource: NetworkInspectorDataSource,
  private val services: NetworkInspectorServices,
  private val scope: CoroutineScope,
  parentDisposable: Disposable,
) : AspectObserver(), Disposable {

  val component: TooltipLayeredPane

  @VisibleForTesting val model: NetworkInspectorModel
  private val view: NetworkInspectorView
  private val goLiveButton = CommonToggleButton("", StudioIcons.Profiler.Toolbar.GOTO_LIVE)
  @VisibleForTesting val launchJob: Job
  @VisibleForTesting val actionsToolBar: JPanel

  init {
    Disposer.register(parentDisposable, this)
    val parentPanel = JPanel(BorderLayout())
    parentPanel.background = DEFAULT_BACKGROUND
    val splitter = ThreeComponentsSplitter()
    splitter.focusTraversalPolicy = LayoutFocusTraversalPolicy()
    splitter.dividerWidth = 0
    splitter.setDividerMouseZoneSize(-1)
    splitter.setHonorComponentsMinimumSize(true)
    splitter.lastComponent = parentPanel

    component = TooltipLayeredPane(splitter)
    val stagePanel = JPanel(BorderLayout())
    val toolbar = JPanel(BorderLayout())
    toolbar.border = DEFAULT_BOTTOM_BORDER
    toolbar.preferredSize = Dimension(0, TOOLBAR_HEIGHT)

    parentPanel.add(toolbar, BorderLayout.NORTH)
    parentPanel.add(stagePanel, BorderLayout.CENTER)

    model = NetworkInspectorModel(services, dataSource, scope)
    launchJob =
      scope.launch(services.workerDispatcher) {
        val response = services.client.startInspection()
        if (StudioFlags.NETWORK_INSPECTOR_STATIC_TIMELINE.get()) {
          val startTimeStampUs = TimeUnit.NANOSECONDS.toMicros(response.timestamp).toDouble()
          model.timeline.dataRange.set(startTimeStampUs, startTimeStampUs)
        } else {
          val startTimeStampNs = response.timestamp
          (model.timeline as StreamingTimeline).reset(startTimeStampNs, startTimeStampNs)
        }
        withContext(AndroidDispatchers.uiThread) {
          if (!response.speedCollectionStarted) {
            services.ideServices.showNotification(
              "Failed to collect speed data. See device Logcat for more information",
              "Network Inspector",
              Severity.ERROR,
            )
          }
        }
      }

    view =
      NetworkInspectorView(
        project,
        model,
        componentsProvider,
        component,
        services,
        scope,
        this@NetworkInspectorTab,
      )
    stagePanel.add(view.component)

    actionsToolBar = JPanel(GridBagLayout())
    toolbar.add(actionsToolBar, BorderLayout.EAST)
    actionsToolBar.border = JBUI.Borders.emptyRight(2)

    val exportConnectionsButton = CommonButton(StudioIcons.Common.EXPORT)
    exportConnectionsButton.name = EXPORT_CONNECTIONS
    exportConnectionsButton.disabledIcon = IconLoader.getDisabledIcon(StudioIcons.Common.EXPORT)
    exportConnectionsButton.addActionListener { exportConnections() }
    val exportConnectionsAction =
      DefaultContextMenuItem.Builder(EXPORT_CONNECTIONS)
        .setContainerComponent(splitter)
        .setActionRunnable { exportConnectionsButton.doClick(0) }
        .build()
    exportConnectionsButton.toolTipText = exportConnectionsAction.defaultToolTipText
    actionsToolBar.add(exportConnectionsButton)

    val clearDataButton = CommonButton(StudioIcons.Common.DELETE)
    clearDataButton.name = CLEAR_DATA
    clearDataButton.disabledIcon = IconLoader.getDisabledIcon(StudioIcons.Common.DELETE)
    clearDataButton.addActionListener { clearData() }
    val clearDataAction =
      DefaultContextMenuItem.Builder(CLEAR_DATA)
        .setContainerComponent(splitter)
        .setActionRunnable { clearDataButton.doClick(0) }
        .build()
    clearDataButton.toolTipText = clearDataAction.defaultToolTipText
    actionsToolBar.add(clearDataButton)

    val zoomOut = CommonButton(AllIcons.General.ZoomOut)
    zoomOut.name = ZOOM_OUT
    zoomOut.disabledIcon = IconLoader.getDisabledIcon(AllIcons.General.ZoomOut)
    zoomOut.addActionListener { model.timeline.zoomOut() }
    val zoomOutAction =
      DefaultContextMenuItem.Builder(ZOOM_OUT)
        .setContainerComponent(splitter)
        .setActionRunnable { zoomOut.doClick(0) }
        .setKeyStrokes(
          KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, SHORTCUT_MODIFIER_MASK_NUMBER),
          KeyStroke.getKeyStroke(KeyEvent.VK_SUBTRACT, SHORTCUT_MODIFIER_MASK_NUMBER),
        )
        .build()

    zoomOut.toolTipText = zoomOutAction.defaultToolTipText
    actionsToolBar.add(zoomOut)

    val zoomIn = CommonButton(AllIcons.General.ZoomIn)
    zoomIn.name = ZOOM_IN
    zoomIn.disabledIcon = IconLoader.getDisabledIcon(AllIcons.General.ZoomIn)
    zoomIn.addActionListener { model.timeline.zoomIn() }
    val zoomInAction =
      DefaultContextMenuItem.Builder(ZOOM_IN)
        .setContainerComponent(splitter)
        .setActionRunnable { zoomIn.doClick(0) }
        .setKeyStrokes(
          KeyStroke.getKeyStroke(KeyEvent.VK_PLUS, SHORTCUT_MODIFIER_MASK_NUMBER),
          KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, SHORTCUT_MODIFIER_MASK_NUMBER),
          KeyStroke.getKeyStroke(KeyEvent.VK_ADD, SHORTCUT_MODIFIER_MASK_NUMBER),
        )
        .build()
    zoomIn.toolTipText = zoomInAction.defaultToolTipText
    actionsToolBar.add(zoomIn)

    val resetZoom = CommonButton(StudioIcons.Common.RESET_ZOOM)
    resetZoom.name = RESET_ZOOM
    resetZoom.disabledIcon = IconLoader.getDisabledIcon(StudioIcons.Common.RESET_ZOOM)
    resetZoom.addActionListener { model.timeline.resetZoom() }
    val resetZoomAction =
      DefaultContextMenuItem.Builder(RESET_ZOOM)
        .setContainerComponent(splitter)
        .setActionRunnable { resetZoom.doClick(0) }
        .setKeyStrokes(
          KeyStroke.getKeyStroke(KeyEvent.VK_NUMPAD0, 0),
          KeyStroke.getKeyStroke(KeyEvent.VK_0, 0),
        )
        .build()
    resetZoom.toolTipText = resetZoomAction.defaultToolTipText
    actionsToolBar.add(resetZoom)

    val zoomToSelection = CommonButton(StudioIcons.Common.ZOOM_SELECT)
    zoomToSelection.name = ZOOM_TO_SELECTION
    zoomToSelection.disabledIcon = IconLoader.getDisabledIcon(StudioIcons.Common.ZOOM_SELECT)
    zoomToSelection.addActionListener {
      model.timeline.frameViewToRange(model.timeline.selectionRange)
    }
    val zoomToSelectionAction =
      DefaultContextMenuItem.Builder(ZOOM_TO_SELECTION)
        .setContainerComponent(splitter)
        .setActionRunnable { zoomToSelection.doClick(0) }
        .setEnableBooleanSupplier { !model.timeline.selectionRange.isEmpty }
        .setKeyStrokes(KeyStroke.getKeyStroke(KeyEvent.VK_M, 0))
        .build()
    zoomToSelection.toolTipText = zoomToSelectionAction.defaultToolTipText

    model.timeline.selectionRange.addDependency(this).onChange(Range.Aspect.RANGE) {
      // DefaultContextMenuItem.setEnableBooleanSupplier() seems like it would cause auto updates,
      // but it doesn't
      zoomToSelection.isEnabled = !model.timeline.selectionRange.isEmpty
    }
    actionsToolBar.add(zoomToSelection)

    if (!StudioFlags.NETWORK_INSPECTOR_STATIC_TIMELINE.get()) {
      val goLiveToolbar = JPanel(GridBagLayout())
      goLiveToolbar.add(FlatSeparator())

      goLiveButton.disabledIcon = IconLoader.getDisabledIcon(StudioIcons.Profiler.Toolbar.GOTO_LIVE)
      goLiveButton.font = H4_FONT
      goLiveButton.horizontalTextPosition = SwingConstants.LEFT
      goLiveButton.horizontalAlignment = SwingConstants.LEFT
      goLiveButton.border = JBUI.Borders.empty(3, 7)
      val attachAction =
        DefaultContextMenuItem.Builder(ATTACH_LIVE)
          .setContainerComponent(splitter)
          .setActionRunnable { goLiveButton.doClick(0) }
          .setEnableBooleanSupplier { goLiveButton.isEnabled && !goLiveButton.isSelected }
          .setKeyStrokes(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, SHORTCUT_MODIFIER_MASK_NUMBER))
          .build()
      val detachAction =
        DefaultContextMenuItem.Builder(DETACH_LIVE)
          .setContainerComponent(splitter)
          .setActionRunnable { goLiveButton.doClick(0) }
          .setEnableBooleanSupplier { goLiveButton.isEnabled && goLiveButton.isSelected }
          .setKeyStrokes(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0))
          .build()

      goLiveButton.toolTipText = detachAction.defaultToolTipText
      goLiveButton.addActionListener {
        val currentStageTimeline: Timeline = model.timeline
        assert(currentStageTimeline is StreamingTimeline)
        (currentStageTimeline as StreamingTimeline).toggleStreaming()
      }
      goLiveButton.addChangeListener {
        val isSelected: Boolean = goLiveButton.isSelected
        goLiveButton.icon =
          if (isSelected) StudioIcons.Profiler.Toolbar.PAUSE_LIVE
          else StudioIcons.Profiler.Toolbar.GOTO_LIVE
        goLiveButton.toolTipText =
          if (isSelected) detachAction.defaultToolTipText else attachAction.defaultToolTipText
      }
      val streamingTimeline = model.timeline as StreamingTimeline
      streamingTimeline.addDependency(this@NetworkInspectorTab).onChange(
        StreamingTimeline.Aspect.STREAMING
      ) {
        goLiveButton.isSelected = streamingTimeline.isStreaming
      }
      goLiveToolbar.add(goLiveButton)
      actionsToolBar.add(goLiveToolbar)
    }
    zoomOut.isEnabled = true
    zoomIn.isEnabled = true
    resetZoom.isEnabled = true
    zoomToSelection.isEnabled = zoomToSelectionAction.isEnabled
  }

  private fun clearData() {
    model.reset()
  }

  private fun exportConnections() {
    val descriptor = FileSaverDescriptor("Export Connections", "", "json")
    val dialog = FileChooserFactory.getInstance().createSaveFileDialog(descriptor, project)
    val baseDir = Path.of(System.getProperty("user.home"), "Downloads")
    val fileWrapper = dialog.save(baseDir, "connections.json") ?: return
    scope.launch {
      val path = fileWrapper.file.toPath()
      view.connectionsView.exportConnections(path)
    }
  }

  fun stopInspection() {
    assert(!StudioFlags.NETWORK_INSPECTOR_STATIC_TIMELINE.get())
    (model.timeline as StreamingTimeline).setIsPaused(true)
    goLiveButton.isEnabled = false
  }

  override fun dispose() {
    services.updater.stop()
  }
}
