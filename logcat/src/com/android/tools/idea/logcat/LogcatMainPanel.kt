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
package com.android.tools.idea.logcat

import com.android.annotations.concurrency.UiThread
import com.android.ddmlib.logcat.LogCatMessage
import com.android.tools.adtui.toolwindow.splittingtabs.state.SplittingTabsStateProvider
import com.android.tools.idea.ddms.DeviceContext
import com.intellij.execution.impl.ConsoleBuffer
import com.intellij.openapi.Disposable
import com.intellij.openapi.command.undo.UndoUtil
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.impl.EditorFactoryImpl
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.project.Project
import com.intellij.util.ui.components.BorderLayoutPanel
import org.jetbrains.annotations.VisibleForTesting
import java.time.ZoneId

/**
 * The top level Logcat panel.
 */
internal class LogcatMainPanel(
  project: Project,
  logcatColors: LogcatColors,
  state: LogcatPanelConfig?,
  zoneId: ZoneId = ZoneId.systemDefault()
) : BorderLayoutPanel(), SplittingTabsStateProvider, Disposable {

  @VisibleForTesting
  internal val editor: EditorEx = createEditor(project)
  private val deviceContext = DeviceContext()
  private val documentPrinter = LogcatDocumentPrinter(project, editor.document, logcatColors, zoneId)
  private val headerPanel = LogcatHeaderPanel(project, deviceContext)

  init {
    // TODO(aalbert): Ideally, we would like to be able to select the connected device and client in the header from the `state` but this
    //  might be challenging both technically and from a UX perspective. Since, when restoring the state, the device/client might not be
    //  available.
    //  From a UX perspective, it's not clear what we should do in this case.
    //  From a technical standpoint, the current implementation that uses DevicePanel doesn't seem to be well suited for preselecting a
    //  device/client.
    addToTop(headerPanel)
    addToCenter(editor.component)
  }

  override fun getState(): String = LogcatPanelConfig.toJson(
    LogcatPanelConfig(deviceContext.selectedDevice?.serialNumber, deviceContext.selectedClient?.clientData?.packageName))

  /**
   * This code is based on [com.intellij.execution.impl.ConsoleViewImpl]
   */
  private fun createEditor(project: Project): EditorEx {
    val editorFactory = EditorFactory.getInstance()
    val document = (editorFactory as EditorFactoryImpl).createDocument(true)
    UndoUtil.disableUndoFor(document)
    val editor = editorFactory.createViewer(document, project, EditorKind.CONSOLE) as EditorEx

    // TODO(aalbert): Install popup handler with: EditorEx#.installPopupHandler()

    editor.document.setCyclicBufferSize(if (ConsoleBuffer.useCycleBuffer()) ConsoleBuffer.getCycleBufferSize() else 0)

    val editorSettings = editor.settings
    editorSettings.isAllowSingleLogicalLineFolding = true
    editorSettings.isLineMarkerAreaShown = false
    editorSettings.isIndentGuidesShown = false
    editorSettings.isLineNumbersShown = false
    editorSettings.isFoldingOutlineShown = true
    editorSettings.isAdditionalPageAtBottom = false
    editorSettings.additionalColumnsCount = 0
    editorSettings.additionalLinesCount = 0
    editorSettings.isRightMarginShown = false
    editorSettings.isCaretRowShown = false
    editorSettings.isShowingSpecialChars = false
    editor.gutterComponentEx.isPaintBackground = false

    (editor as EditorImpl).isDisposed
    return editor
  }

  @UiThread
  internal fun print(message: LogCatMessage) {
    documentPrinter.print(message)
  }

  override fun dispose() {
    EditorFactory.getInstance().releaseEditor(editor)
  }
}