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
package com.android.tools.idea.layoutinspector.snapshots

import com.android.tools.adtui.actions.ZoomType
import com.android.tools.adtui.workbench.WorkBench
import com.android.tools.idea.concurrency.createCoroutineScope
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.idea.layoutinspector.LayoutInspectorBundle
import com.android.tools.idea.layoutinspector.metrics.LayoutInspectorSessionMetrics
import com.android.tools.idea.layoutinspector.metrics.statistics.SessionStatistics
import com.android.tools.idea.layoutinspector.metrics.statistics.SessionStatisticsImpl
import com.android.tools.idea.layoutinspector.model.AndroidWindow
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.model.NotificationModel
import com.android.tools.idea.layoutinspector.pipeline.DisconnectedClient
import com.android.tools.idea.layoutinspector.pipeline.InspectorClient
import com.android.tools.idea.layoutinspector.pipeline.InspectorClientSettings
import com.android.tools.idea.layoutinspector.properties.LayoutInspectorPropertiesPanelDefinition
import com.android.tools.idea.layoutinspector.properties.PropertiesProvider
import com.android.tools.idea.layoutinspector.runningdevices.actions.UiConfig
import com.android.tools.idea.layoutinspector.runningdevices.ui.ToolbarState
import com.android.tools.idea.layoutinspector.runningdevices.ui.createLayoutInspectorPanel
import com.android.tools.idea.layoutinspector.runningdevices.ui.createToolbarPanel
import com.android.tools.idea.layoutinspector.runningdevices.ui.rendering.EmbeddedRendererModel
import com.android.tools.idea.layoutinspector.runningdevices.ui.rendering.StandaloneRendererPanel
import com.android.tools.idea.layoutinspector.runningdevices.ui.rendering.navigateToSelectedViewFromRendererDoubleClick
import com.android.tools.idea.layoutinspector.tree.EditorTreeSettings
import com.android.tools.idea.layoutinspector.tree.LayoutInspectorTreePanelDefinition
import com.android.tools.idea.layoutinspector.ui.DeviceViewPanel
import com.android.tools.idea.layoutinspector.ui.InspectorBanner
import com.android.tools.idea.layoutinspector.ui.LayoutInspectorRootPanel
import com.android.tools.idea.layoutinspector.ui.ZoomableContainer
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorAttachToProcess.ClientType.SNAPSHOT_CLIENT
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorEvent
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorEvent.DynamicLayoutInspectorEventType.SNAPSHOT_LOADED
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorEvent.DynamicLayoutInspectorEventType.SNAPSHOT_LOAD_ERROR
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorLocation
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.StatusText
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.BorderLayout
import java.awt.Graphics
import java.beans.PropertyChangeListener
import java.nio.file.Path
import javax.swing.JComponent
import javax.swing.JPanel
import kotlinx.coroutines.launch

private const val LAYOUT_INSPECTOR_SNAPSHOT_ID = "Layout Inspector Snapshot"
private const val SNAPSHOT_OUTDATED_ID = "snapshot.outdated"

class FileEditorInspectorClient(
  private val model: InspectorModel,
  private val snapshotLoader: SnapshotLoader,
  override val stats: SessionStatistics,
) : InspectorClient by DisconnectedClient {
  override val provider: PropertiesProvider
    get() = snapshotLoader.propertiesProvider

  override val capabilities: Set<InspectorClient.Capability>
    get() =
      mutableSetOf<InspectorClient.Capability>().apply {
        if (model.pictureType == AndroidWindow.ImageType.SKP) {
          add(InspectorClient.Capability.SUPPORTS_SKP)
        }
        addAll(snapshotLoader.capabilities)
      }

  override val process = snapshotLoader.processDescriptor
  override val isConnected = true
}

class LayoutInspectorFileEditor(val project: Project, private val path: Path) :
  UserDataHolderBase(), FileEditor {
  private var metrics: LayoutInspectorSessionMetrics? = null
  private var stats: SessionStatistics = DisconnectedClient.stats

  override fun getFile() = VfsUtil.findFile(path, true)

  override fun dispose() {
    metrics?.logEvent(
      DynamicLayoutInspectorEvent.DynamicLayoutInspectorEventType.SESSION_DATA,
      stats,
    )
  }

  private var component: JComponent? = null
  private var modificationCount = 0L

  override fun getComponent(): JComponent {
    if (modificationCount < (file?.modificationCount ?: -1)) {
      component = null
    }
    component?.let {
      return it
    }
    modificationCount = file?.modificationCount ?: -1

    val rootPanel: LayoutInspectorRootPanel

    var snapshotLoader: SnapshotLoader? = null
    val startTime = System.currentTimeMillis()
    var metadata: SnapshotMetadata? = null
    try {
      val scope = createCoroutineScope()
      val model = InspectorModel(project, scope)
      val notificationModel = NotificationModel(project)

      snapshotLoader = SnapshotLoader.createSnapshotLoader(path)
      stats = SessionStatisticsImpl(SNAPSHOT_CLIENT)
      metadata =
        snapshotLoader?.loadFile(path, model, notificationModel, stats) ?: throw Exception()
      val client = FileEditorInspectorClient(model, snapshotLoader, stats)

      // TODO: persisted tree setting scoped to file
      val treeSettings = EditorTreeSettings(client.capabilities)
      val inspectorClientSettings = InspectorClientSettings(project)
      val layoutInspector =
        LayoutInspector(
          coroutineScope = scope,
          layoutInspectorClientSettings = inspectorClientSettings,
          client = client,
          layoutInspectorModel = model,
          notificationModel = notificationModel,
          treeSettings = treeSettings,
        )

      rootPanel =
        if (StudioFlags.DYNAMIC_LAYOUT_INSPECTOR_STANDALONE_V2.get()) {
          createNewLayoutInspectorUi(this, project, layoutInspector)
        } else {
          createOldLayoutInspectorUi(this, project, layoutInspector)
        }

      val hasBitmapImage =
        when (model.pictureType) {
          AndroidWindow.ImageType.BITMAP_AS_REQUESTED -> true
          AndroidWindow.ImageType.UNKNOWN,
          AndroidWindow.ImageType.SKP_PENDING,
          AndroidWindow.ImageType.SKP -> false
        }

      if (!hasBitmapImage && StudioFlags.DYNAMIC_LAYOUT_INSPECTOR_STANDALONE_V2.get()) {
        notificationModel.addNotification(
          id = SNAPSHOT_OUTDATED_ID,
          text = LayoutInspectorBundle.message(SNAPSHOT_OUTDATED_ID),
          sticky = true,
        )
      }

      metadata.loadDuration = System.currentTimeMillis() - startTime
      model.updateConnection(client)
      metrics =
        LayoutInspectorSessionMetrics(
          project,
          snapshotLoader.processDescriptor,
          snapshotMetadata = metadata,
        )
      metrics?.logEvent(SNAPSHOT_LOADED, stats)
    } catch (exception: Exception) {
      // TODO: better error panel
      Logger.getInstance(LayoutInspectorFileEditor::class.java)
        .warn("Error loading snapshot", exception)
      LayoutInspectorSessionMetrics(project, snapshotLoader?.processDescriptor, metadata)
        .logEvent(SNAPSHOT_LOAD_ERROR, stats)
      val status =
        object : StatusText() {
          override fun isStatusVisible() = true
        }
      // TODO these "gap" calls can be removed after the images in
      //  com.android.tools.idea.layoutinspector.snapshots.LayoutInspectorFileEditorTest.editorShowsVersionError
      //  are updated accordingly (there was a bug in StatusText that added the gap after the second line,
      //  but not after the first line, and the test images were created with that bug)
      status.forceGapAfterLastLine()
      status.withUnscaledGapAfter(0).appendLine("Error loading snapshot")
      (exception as? SnapshotLoaderException)?.message?.let { status.withUnscaledGapAfter(2).appendLine(it) }

      return object : JPanel() {
        init {
          status.attachTo(this)
          component = this
        }

        override fun paintComponent(g: Graphics?) {
          super.paintComponent(g)
          status.paint(this, g)
        }
      }
    }
    component = rootPanel
    return rootPanel
  }

  private fun createNewLayoutInspectorUi(
    disposable: Disposable,
    project: Project,
    layoutInspector: LayoutInspector,
  ): LayoutInspectorRootPanel {
    val scope = disposable.createCoroutineScope()

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
        processPicker = null,
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

    scope.launch { toolbarState.overlayImage.collect { renderModel.setOverlay(it) } }

    scope.launch {
      toolbarState.overlayTransparency.collect { renderModel.setOverlayTransparency(it) }
    }

    // Since the model was updated before the panel was created, we need to zoom to fit explicitly.
    // If startup is in progress we have to wait until after so tools windows are opened and the
    // window is its final size.
    // TODO: save zoom in editor state
    StartupManager.getInstance(project).runAfterOpened {
      invokeLater(ModalityState.any()) { container.zoom(ZoomType.FIT) }
    }

    return rootPanel
  }

  private fun createOldLayoutInspectorUi(
    disposable: Disposable,
    project: Project,
    layoutInspector: LayoutInspector,
  ): LayoutInspectorRootPanel {
    val deviceViewPanel =
      DeviceViewPanel(layoutInspector = layoutInspector, disposableParent = disposable)

    val workbench =
      WorkBench<LayoutInspector>(project, LAYOUT_INSPECTOR_SNAPSHOT_ID, null, disposable).apply {
        init(
          deviceViewPanel,
          layoutInspector,
          listOf(LayoutInspectorTreePanelDefinition(), LayoutInspectorPropertiesPanelDefinition()),
          false,
        )
      }

    val rootPanel =
      JPanel(BorderLayout()).apply {
        add(InspectorBanner(disposable, layoutInspector.notificationModel), BorderLayout.NORTH)
        add(workbench, BorderLayout.CENTER)
      }

    // Since the model was updated before the panel was created, we need to zoom to fit explicitly.
    // If startup is in progress we have to wait until after so tools windows are opened and the
    // window is its final size.
    // TODO: save zoom in editor state
    StartupManager.getInstance(project).runAfterOpened {
      invokeLater(ModalityState.any()) { deviceViewPanel.zoom(ZoomType.FIT) }
    }

    return LayoutInspectorRootPanel(content = rootPanel, layoutInspector)
  }

  override fun getPreferredFocusedComponent(): JComponent? = null

  override fun getName() = "LayoutInspectorSnapshotViewer"

  override fun setState(state: FileEditorState) {
    // TODO: we should restore the selection state at least
  }

  override fun isModified() = false

  override fun isValid() = true

  override fun addPropertyChangeListener(listener: PropertyChangeListener) {}

  override fun removePropertyChangeListener(listener: PropertyChangeListener) {}

  override fun getCurrentLocation(): FileEditorLocation? {
    // TODO: we should save the selection state at least
    return null
  }

  /** Factory for [LayoutInspectorFileEditor]s. */
  class Provider : FileEditorProvider, DumbAware {
    override fun accept(project: Project, file: VirtualFile): Boolean {
      return FileTypeRegistry.getInstance().getFileTypeByExtension(file.extension ?: "") ==
        LayoutInspectorFileType
    }

    override fun createEditor(project: Project, file: VirtualFile) =
      LayoutInspectorFileEditor(project, file.toNioPath())

    override fun getEditorTypeId() = "dynamic-layout-inspector"

    override fun getPolicy() = FileEditorPolicy.HIDE_DEFAULT_EDITOR
  }
}
