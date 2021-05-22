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

import com.android.tools.adtui.workbench.WorkBench
import com.android.tools.idea.editors.layoutInspector.LayoutInspectorFileType
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.layoutinspector.LAYOUT_INSPECTOR_TOOL_WINDOW_ID
import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.idea.layoutinspector.dataProviderForLayoutInspector
import com.android.tools.idea.layoutinspector.metrics.statistics.SessionStatistics
import com.android.tools.idea.layoutinspector.model.AndroidWindow
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.pipeline.DisconnectedClient
import com.android.tools.idea.layoutinspector.pipeline.InspectorClient
import com.android.tools.idea.layoutinspector.properties.LayoutInspectorPropertiesPanelDefinition
import com.android.tools.idea.layoutinspector.properties.PropertiesProvider
import com.android.tools.idea.layoutinspector.tree.LayoutInspectorTreePanelDefinition
import com.android.tools.idea.layoutinspector.tree.TreeSettings
import com.android.tools.idea.layoutinspector.ui.DeviceViewPanel
import com.android.tools.idea.layoutinspector.ui.DeviceViewSettings
import com.android.tools.idea.layoutinspector.ui.InspectorBanner
import com.intellij.ide.DataManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorLocation
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBLabel
import java.awt.BorderLayout
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingConstants

class LayoutInspectorFileEditor(val project: Project, file: VirtualFile) : UserDataHolderBase(), FileEditor {
  private val _file = file
  override fun getFile() = _file

  override fun dispose() {
    // TODO if necessary
  }

  private var workbench: WorkBench<LayoutInspector>? = null
  private var modificationCount = -1L

  override fun getComponent(): JComponent {
    if (modificationCount < file.modificationCount) {
      workbench = null
    }
    workbench?.let { return it }
    modificationCount = file.modificationCount

    val workbench = WorkBench<LayoutInspector>(project, LAYOUT_INSPECTOR_TOOL_WINDOW_ID, null, this)
    try {
      val viewSettings = DeviceViewSettings()

      val contentPanel = JPanel(BorderLayout())
      contentPanel.add(InspectorBanner(project), BorderLayout.NORTH)
      contentPanel.add(workbench, BorderLayout.CENTER)

      // TODO: error handling
      val snapshotLoader = SnapshotLoader.createSnapshotLoader(file) ?: throw Exception()
      val model = InspectorModel(project)
      snapshotLoader.loadFile(file, model)

      // TODO: persisted tree setting scoped to file
      val treeSettings = object : TreeSettings {
        override var hideSystemNodes = false
        override var composeAsCallstack = false
        override var mergedSemanticsTree = false
        override var unmergedSemanticsTree = false
        override var supportLines = true
      }
      // TODO: indicate this is a snapshot session in the stats
      val stats = SessionStatistics(model, treeSettings)
      val client = object : InspectorClient by DisconnectedClient {
        override val provider: PropertiesProvider
          get() = snapshotLoader.propertiesProvider

        override val capabilities: Set<InspectorClient.Capability>
          get() = if (model.pictureType == AndroidWindow.ImageType.SKP) setOf(InspectorClient.Capability.SUPPORTS_SKP) else setOf()
      }
      val layoutInspector = LayoutInspector(client, model, stats, treeSettings)
      val deviceViewPanel = DeviceViewPanel(null, layoutInspector, viewSettings, workbench)
      DataManager.registerDataProvider(workbench, dataProviderForLayoutInspector(layoutInspector, deviceViewPanel))
      workbench.init(deviceViewPanel, layoutInspector, listOf(
        LayoutInspectorTreePanelDefinition(), LayoutInspectorPropertiesPanelDefinition()), false)

      model.updateConnection(client)
    }
    catch (exception: Exception) {
      // TODO: better error panel
      Logger.getInstance(LayoutInspectorFileEditor::class.java).warn("Error loading snapshot", exception)
      return JBLabel("Error loading snapshot", SwingConstants.CENTER)
    }
    this.workbench = workbench
    return workbench
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

  /**
   * Factory for [LayoutInspectorFileEditor]s. Note that for now [StudioFlags.DYNAMIC_LAYOUT_INSPECTOR_ENABLE_SNAPSHOTS] needs to be on or
   * else [com.android.tools.idea.profiling.capture.CaptureEditorProvider] will be used to create
   * `com.android.tools.idea.editors.layoutInspector.LayoutInspectorEditor`s
   */
  class Provider : FileEditorProvider, DumbAware {
    override fun accept(project: Project, file: VirtualFile): Boolean {
      return FileTypeRegistry.getInstance().getFileTypeByExtension(file.extension ?: "") == LayoutInspectorFileType.INSTANCE &&
             StudioFlags.DYNAMIC_LAYOUT_INSPECTOR_ENABLE_SNAPSHOTS.get()
    }

    override fun createEditor(project: Project, file: VirtualFile) = LayoutInspectorFileEditor(project, file)

    override fun getEditorTypeId() = "dynamic-layout-inspector"

    override fun getPolicy() = FileEditorPolicy.HIDE_DEFAULT_EDITOR
  }
}
