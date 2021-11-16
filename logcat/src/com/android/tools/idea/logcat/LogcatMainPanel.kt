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
import com.android.ddmlib.IDevice
import com.android.ddmlib.logcat.LogCatMessage
import com.android.tools.adtui.toolwindow.splittingtabs.state.SplittingTabsStateProvider
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.AndroidDispatchers.ioThread
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.concurrency.AndroidDispatchers.workerThread
import com.android.tools.idea.ddms.DeviceContext
import com.android.tools.idea.logcat.actions.ClearLogcatAction
import com.android.tools.idea.logcat.actions.HeaderFormatOptionsAction
import com.android.tools.idea.logcat.filters.LogcatFilter
import com.android.tools.idea.logcat.filters.LogcatFilterParser
import com.android.tools.idea.logcat.folding.EditorFoldingDetector
import com.android.tools.idea.logcat.folding.FoldingDetector
import com.android.tools.idea.logcat.hyperlinks.EditorHyperlinkDetector
import com.android.tools.idea.logcat.hyperlinks.HyperlinkDetector
import com.android.tools.idea.logcat.messages.DocumentAppender
import com.android.tools.idea.logcat.messages.FormattingOptions
import com.android.tools.idea.logcat.messages.LogcatColors
import com.android.tools.idea.logcat.messages.MessageBacklog
import com.android.tools.idea.logcat.messages.MessageFormatter
import com.android.tools.idea.logcat.messages.MessageProcessor
import com.android.tools.idea.logcat.messages.TextAccumulator
import com.android.tools.idea.logcat.util.createLogcatEditor
import com.android.tools.idea.logcat.util.isCaretAtBottom
import com.android.tools.idea.logcat.util.isScrollAtBottom
import com.intellij.execution.impl.ConsoleBuffer
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.editor.actions.ScrollToTheEndToolbarAction
import com.intellij.openapi.editor.actions.ToggleUseSoftWrapsToolbarAction
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.impl.ContextMenuPopupHandler
import com.intellij.openapi.editor.impl.softwrap.SoftWrapAppliancePlaces
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.StringUtil
import com.intellij.tools.SimpleActionGroup
import com.intellij.util.ui.components.BorderLayoutPanel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.VisibleForTesting
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import java.time.ZoneId
import kotlin.math.max

/**
 * The top level Logcat panel.
 *
 * @param project the [Project]
 * @param popupActionGroup An [ActionGroup] to add to the right-click menu of the panel
 * @param logcatColors Provides colors for rendering messages
 * @param state State to restore or null to use the default state
 * @param hyperlinkDetector A [HyperlinkDetector] or null to create the default one. For testing.
 * @param foldingDetector A [FoldingDetector] or null to create the default one. For testing.
 * @param zoneId A [ZoneId] or null to create the default one. For testing.
 */
internal class LogcatMainPanel(
  project: Project,
  private val popupActionGroup: ActionGroup,
  logcatColors: LogcatColors,
  state: LogcatPanelConfig?,
  hyperlinkDetector: HyperlinkDetector? = null,
  foldingDetector: FoldingDetector? = null,
  packageNamesProvider: PackageNamesProvider = ProjectPackageNamesProvider(project),
  zoneId: ZoneId = ZoneId.systemDefault()
) : BorderLayoutPanel(), LogcatPresenter, SplittingTabsStateProvider, Disposable {

  @VisibleForTesting
  internal val editor: EditorEx = createLogcatEditor(project)
  private val document = editor.document
  private val documentAppender = DocumentAppender(project, document, ConsoleBuffer.getCycleBufferSize())
  private val deviceContext = DeviceContext()

  @VisibleForTesting
  internal val formattingOptions = state?.formattingOptions ?: FormattingOptions()
  private val messageFormatter = MessageFormatter(formattingOptions, logcatColors, zoneId)

  @VisibleForTesting
  internal val messageBacklog = MessageBacklog(ConsoleBuffer.getCycleBufferSize())

  @VisibleForTesting
  val headerPanel = LogcatHeaderPanel(
    project,
    logcatPresenter = this,
    deviceContext, packageNamesProvider,
    state?.filter ?: "",
    state?.showOnlyProjectApps ?: true)

  @VisibleForTesting
  internal val messageProcessor = MessageProcessor(
    this,
    messageFormatter::formatMessages,
    packageNamesProvider,
    LogcatFilterParser(project).parse(headerPanel.getFilterText()),
    headerPanel.isShowProjectApps())
  private var logcatReader: LogcatReader? = null
  private val toolbar = ActionManager.getInstance().createActionToolbar("LogcatMainPanel", createToolbarActions(project), false)
  private val hyperlinkDetector = hyperlinkDetector ?: EditorHyperlinkDetector(project, editor)
  private val foldingDetector = foldingDetector ?: EditorFoldingDetector(project, editor)
  private var ignoreCaretAtBottom = false // Derived from similar code in ConsoleViewImpl. See initScrollToEndStateHandling()

  init {
    editor.installPopupHandler(object : ContextMenuPopupHandler() {
      override fun getActionGroup(event: EditorMouseEvent): ActionGroup = popupActionGroup
    })

    toolbar.setTargetComponent(this)

    // TODO(aalbert): Ideally, we would like to be able to select the connected device and client in the header from the `state` but this
    //  might be challenging both technically and from a UX perspective. Since, when restoring the state, the device/client might not be
    //  available.
    //  From a UX perspective, it's not clear what we should do in this case.
    //  From a technical standpoint, the current implementation that uses DevicePanel doesn't seem to be well suited for preselecting a
    //  device/client.
    addToTop(headerPanel)
    addToLeft(toolbar.component)
    addToCenter(editor.component)

    deviceContext.addListener(object : DeviceConnectionListener() {
      override fun onDeviceConnected(device: IDevice) {
        logcatReader?.let {
          Disposer.dispose(it)
        }
        document.setText("")
        logcatReader = LogcatReader(device, this@LogcatMainPanel).also(LogcatReader::start)
      }

      override fun onDeviceDisconnected(device: IDevice) {
        logcatReader?.let {
          Disposer.dispose(it)
        }
        logcatReader = null
      }
    }, this)

    initScrollToEndStateHandling()
  }

  /**
   * Derived from similar code in ConsoleViewImpl.
   *
   * The purpose of this code is to 'not scroll to end' when the caret is at the end **but** the user has scrolled away from the bottom of
   * the file.
   *
   * aalbert: In theory, it seems like it should be possible to determine the state of the scroll bar directly and see if it's at the
   * bottom, but when I attempted that, it did not quite work. The code in `isScrollAtBottom()` doesn't always return the expected result.
   *
   * Specifically, on the second batch of text appended to the document, the expression "`scrollBar.maximum - scrollBar.visibleAmount`" is
   * equal to "`position + <some small number>`" rather than to "`position`" exactly.
   */
  private fun initScrollToEndStateHandling() {
    val mouseListener: MouseAdapter = object : MouseAdapter() {
      override fun mousePressed(e: MouseEvent) {
        updateScrollToEndState(true)
      }

      override fun mouseDragged(e: MouseEvent) {
        updateScrollToEndState(false)
      }

      override fun mouseWheelMoved(e: MouseWheelEvent) {
        if (e.isShiftDown) return  // ignore horizontal scrolling
        updateScrollToEndState(false)
      }
    }
    val scrollPane = editor.scrollPane
    scrollPane.addMouseWheelListener(mouseListener)
    scrollPane.verticalScrollBar.addMouseListener(mouseListener)
    scrollPane.verticalScrollBar.addMouseMotionListener(mouseListener)
  }

  override suspend fun processMessages(messages: List<LogCatMessage>) {
    messageBacklog.addAll(messages)
    messageProcessor.appendMessages(messages)
  }

  override fun getState(): String = LogcatPanelConfig.toJson(
    LogcatPanelConfig(
      deviceContext.selectedDevice?.serialNumber,
      formattingOptions,
      headerPanel.getFilterText(),
      headerPanel.isShowProjectApps()))

  override suspend fun appendMessages(textAccumulator: TextAccumulator) = withContext(uiThread(ModalityState.any())) {
    if (!isActive) {
      return@withContext
    }
    // Derived from similar code in ConsoleViewImpl. See initScrollToEndStateHandling()
    val shouldStickToEnd = !ignoreCaretAtBottom && editor.isCaretAtBottom()
    ignoreCaretAtBottom = false // The 'ignore' only needs to last for one update. Next time, isCaretAtBottom() will be false.
    // Mark the end for post-processing. Adding text changes the lines due to the cyclic buffer.
    val endMarker: RangeMarker = document.createRangeMarker(document.textLength, document.textLength)

    documentAppender.appendToDocument(textAccumulator)

    val startLine = if (endMarker.isValid) document.getLineNumber(endMarker.endOffset) else 0
    endMarker.dispose()
    val endLine = max(0, document.lineCount - 1)
    hyperlinkDetector.detectHyperlinks(startLine, endLine)
    foldingDetector.detectFoldings(startLine, endLine)

    if (shouldStickToEnd) {
      scrollToEnd()
    }
  }

  override fun dispose() {
    EditorFactory.getInstance().releaseEditor(editor)
  }

  @UiThread
  override fun applyFilter(logcatFilter: LogcatFilter) {
    messageProcessor.logcatFilter = logcatFilter
    reloadMessages()
  }

  @UiThread
  override fun setShowOnlyProjectApps(enabled: Boolean) {
    messageProcessor.showOnlyProjectApps = enabled
    reloadMessages()
  }

  @UiThread
  override fun reloadMessages() {
    document.setText("")
    AndroidCoroutineScope(this, workerThread).launch {
      messageProcessor.appendMessages(messageBacklog.messages)
    }
  }

  private fun createToolbarActions(project: Project): ActionGroup {
    return SimpleActionGroup().apply {
      add(ClearLogcatAction(this@LogcatMainPanel))
      add(ScrollToTheEndToolbarAction(editor).apply {
        val text = LogcatBundle.message("logcat.scroll.to.end.text")
        templatePresentation.text = StringUtil.toTitleCase(text)
        templatePresentation.description = text
      })
      add(object : ToggleUseSoftWrapsToolbarAction(SoftWrapAppliancePlaces.CONSOLE) {
        override fun getEditor(e: AnActionEvent) = this@LogcatMainPanel.editor
      })
      add(HeaderFormatOptionsAction(project, this@LogcatMainPanel, formattingOptions))
    }
  }

  @UiThread
  override fun clearMessageView() {
    AndroidCoroutineScope(this, ioThread).launch {
      logcatReader?.let {
        it.stop()
        it.clearLogcat()
      }
      messageBacklog.clear()
      logcatReader?.start()
      withContext(uiThread) {
        document.setText("")
        withContext(workerThread) {
          messageProcessor.appendMessages(messageBacklog.messages)
        }
      }
    }
  }

  override fun isMessageViewEmpty() = document.textLength == 0

  // Derived from similar code in ConsoleViewImpl. See initScrollToEndStateHandling()
  @UiThread
  private fun updateScrollToEndState(useImmediatePosition: Boolean) {
    val scrollAtBottom = editor.isScrollAtBottom(useImmediatePosition)
    val caretAtBottom = editor.isCaretAtBottom()
    if (!scrollAtBottom && caretAtBottom) {
      ignoreCaretAtBottom = true
    }
  }

  private fun scrollToEnd() {
    EditorUtil.scrollToTheEnd(editor, true)
    ignoreCaretAtBottom = false
  }
}
