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
import com.android.ddmlib.AndroidDebugBridge
import com.android.tools.adtui.toolwindow.splittingtabs.state.SplittingTabsStateProvider
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.concurrency.AndroidDispatchers.workerThread
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.logcat.LogcatMainPanel.LogcatServiceEvent.PauseLogcat
import com.android.tools.idea.logcat.LogcatMainPanel.LogcatServiceEvent.StartLogcat
import com.android.tools.idea.logcat.LogcatMainPanel.LogcatServiceEvent.StopLogcat
import com.android.tools.idea.logcat.LogcatPanelConfig.FormattingConfig
import com.android.tools.idea.logcat.LogcatPanelConfig.FormattingConfig.Custom
import com.android.tools.idea.logcat.LogcatPanelConfig.FormattingConfig.Preset
import com.android.tools.idea.logcat.LogcatPresenter.Companion.LOGCAT_PRESENTER_ACTION
import com.android.tools.idea.logcat.ProjectApplicationIdsProvider.Companion.PROJECT_APPLICATION_IDS_CHANGED_TOPIC
import com.android.tools.idea.logcat.ProjectApplicationIdsProvider.ProjectApplicationIdsListener
import com.android.tools.idea.logcat.actions.ClearLogcatAction
import com.android.tools.idea.logcat.actions.CopyMessageTextAction
import com.android.tools.idea.logcat.actions.CreateScratchFileAction
import com.android.tools.idea.logcat.actions.IgnoreTagAction
import com.android.tools.idea.logcat.actions.LogcatFoldLinesLikeThisAction
import com.android.tools.idea.logcat.actions.LogcatFormatAction
import com.android.tools.idea.logcat.actions.LogcatScrollToTheEndToolbarAction
import com.android.tools.idea.logcat.actions.LogcatSplitterActions
import com.android.tools.idea.logcat.actions.LogcatToggleUseSoftWrapsToolbarAction
import com.android.tools.idea.logcat.actions.NextOccurrenceToolbarAction
import com.android.tools.idea.logcat.actions.PauseLogcatAction
import com.android.tools.idea.logcat.actions.PreviousOccurrenceToolbarAction
import com.android.tools.idea.logcat.actions.RestartLogcatAction
import com.android.tools.idea.logcat.actions.ToggleFilterAction
import com.android.tools.idea.logcat.devices.Device
import com.android.tools.idea.logcat.filters.LogcatFilter
import com.android.tools.idea.logcat.filters.LogcatFilter.Companion.MY_PACKAGE
import com.android.tools.idea.logcat.filters.LogcatFilterParser
import com.android.tools.idea.logcat.filters.LogcatMasterFilter
import com.android.tools.idea.logcat.folding.EditorFoldingDetector
import com.android.tools.idea.logcat.folding.FoldingDetector
import com.android.tools.idea.logcat.hyperlinks.EditorHyperlinkDetector
import com.android.tools.idea.logcat.hyperlinks.HyperlinkDetector
import com.android.tools.idea.logcat.message.LogcatMessage
import com.android.tools.idea.logcat.messages.AndroidLogcatFormattingOptions
import com.android.tools.idea.logcat.messages.DocumentAppender
import com.android.tools.idea.logcat.messages.FormattingOptions
import com.android.tools.idea.logcat.messages.LOGCAT_FILTER_HINT_KEY
import com.android.tools.idea.logcat.messages.LogcatColors
import com.android.tools.idea.logcat.messages.MessageBacklog
import com.android.tools.idea.logcat.messages.MessageFormatter
import com.android.tools.idea.logcat.messages.MessageProcessor
import com.android.tools.idea.logcat.messages.ProcessThreadFormat
import com.android.tools.idea.logcat.messages.TextAccumulator
import com.android.tools.idea.logcat.messages.TextAccumulator.FilterHint
import com.android.tools.idea.logcat.messages.TimestampFormat
import com.android.tools.idea.logcat.service.LogcatService
import com.android.tools.idea.logcat.settings.AndroidLogcatSettings
import com.android.tools.idea.logcat.util.AndroidProjectDetector
import com.android.tools.idea.logcat.util.AndroidProjectDetectorImpl
import com.android.tools.idea.logcat.util.LOGGER
import com.android.tools.idea.logcat.util.LogcatUsageTracker
import com.android.tools.idea.logcat.util.MostRecentlyAddedSet
import com.android.tools.idea.logcat.util.createLogcatEditor
import com.android.tools.idea.logcat.util.getDefaultFilter
import com.android.tools.idea.logcat.util.isCaretAtBottom
import com.android.tools.idea.logcat.util.isScrollAtBottom
import com.android.tools.idea.logcat.util.toggleFilterTerm
import com.android.tools.idea.projectsystem.ProjectSystemService
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager.SyncReason.Companion.USER_REQUEST
import com.android.tools.idea.run.ClearLogcatListener
import com.android.tools.idea.ui.screenrecording.ScreenRecorderAction
import com.android.tools.idea.ui.screenshot.DeviceArtScreenshotOptions
import com.android.tools.idea.ui.screenshot.ScreenshotAction
import com.google.wireless.android.sdk.stats.LogcatUsageEvent
import com.google.wireless.android.sdk.stats.LogcatUsageEvent.LogcatFormatConfiguration
import com.google.wireless.android.sdk.stats.LogcatUsageEvent.LogcatFormatConfiguration.Preset.COMPACT
import com.google.wireless.android.sdk.stats.LogcatUsageEvent.LogcatFormatConfiguration.Preset.STANDARD
import com.google.wireless.android.sdk.stats.LogcatUsageEvent.LogcatPanelEvent
import com.google.wireless.android.sdk.stats.LogcatUsageEvent.Type.PANEL_ADDED
import com.intellij.icons.AllIcons
import com.intellij.ide.actions.CopyAction
import com.intellij.ide.actions.SearchWebAction
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.CommonDataKeys.EDITOR
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.impl.ContextMenuPopupHandler
import com.intellij.openapi.project.Project
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI.Borders
import com.intellij.util.ui.JBUI.CurrentTheme.Banner
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
import java.awt.Cursor
import java.awt.Point
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseEvent.BUTTON1
import java.awt.event.MouseWheelEvent
import java.time.ZoneId
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicReference
import javax.swing.BorderFactory
import javax.swing.GroupLayout
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.math.max

// This is probably a massive overkill as we do not expect this many tags/packages in a real Logcat
private const val MAX_TAGS = 1000
private const val MAX_PACKAGE_NAMES = 1000
private const val MAX_PROCESS_NAMES = 1000

private val handCursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
private val textCursor = Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR)

// To avoid exposing LogcatMainPanel, we needed to make a factory that will return a JComponent. We
// initially tried making LogcatMainPanel public, but that caused a chain-reaction of "package
// protection level" issues.
class LogcatMainPanelFactory {
  companion object {
    fun create(project: Project): JComponent {
      return LogcatMainPanel(project, DefaultActionGroup(), LogcatColors(), null)
    }
  }
}

/**
 * The top level Logcat panel.
 *
 * @param project the [Project]
 * @param splitterPopupActionGroup An [ActionGroup] to add to the right-click menu of the panel and the toolbar
 * @param logcatColors Provides colors for rendering messages
 * @param state State to restore or null to use the default state
 * @param hyperlinkDetector A [HyperlinkDetector] or null to create the default one. For testing.
 * @param foldingDetector A [FoldingDetector] or null to create the default one. For testing.
 * @param zoneId A [ZoneId] or null to create the default one. For testing.
 */
internal class LogcatMainPanel @TestOnly constructor(
  private val project: Project,
  private val splitterPopupActionGroup: ActionGroup,
  logcatColors: LogcatColors,
  state: LogcatPanelConfig?,
  private var logcatSettings: AndroidLogcatSettings,
  private var androidProjectDetector: AndroidProjectDetector,
  hyperlinkDetector: HyperlinkDetector?,
  foldingDetector: FoldingDetector?,
  zoneId: ZoneId = ZoneId.systemDefault()
) : BorderLayoutPanel(), LogcatPresenter, SplittingTabsStateProvider, DataProvider, Disposable {

  constructor(
    project: Project,
    splitterPopupActionGroup: ActionGroup,
    logcatColors: LogcatColors,
    state: LogcatPanelConfig?,
  ) : this(
    project,
    splitterPopupActionGroup,
    logcatColors,
    state,
    AndroidLogcatSettings.getInstance(),
    AndroidProjectDetectorImpl(),
    hyperlinkDetector = null,
    foldingDetector = null,
  )

  private var isLogcatPaused: Boolean = false

  private var isSoftWrapEnabled: Boolean = false

  private var caretLine = 0

  @VisibleForTesting
  internal val editor: EditorEx = createLogcatEditor(project)
  private val pausedBanner = WarningNotificationPanel()
  private val noApplicationIdsBanner = WarningNotificationPanel()
  private val noLogsBanner = WarningNotificationPanel()
  private val document = editor.document
  private val documentAppender = DocumentAppender(project, document, logcatSettings.bufferSize)
  private val coroutineScope = AndroidCoroutineScope(this)

  override var formattingOptions: FormattingOptions = state.getFormattingOptions()
    set(value) {
      field = value
      reloadMessages()
    }

  private val messageFormatter = MessageFormatter(logcatColors, zoneId)

  @VisibleForTesting
  internal val messageBacklog = AtomicReference(MessageBacklog(logcatSettings.bufferSize))
  private val tags = MostRecentlyAddedSet<String>(MAX_TAGS)
  private val packages = MostRecentlyAddedSet<String>(MAX_PACKAGE_NAMES)
  private val processNames = MostRecentlyAddedSet<String>(MAX_PROCESS_NAMES)
  private val packageNamesProvider: ProjectApplicationIdsProvider = ProjectApplicationIdsProvider.getInstance(project)
  private val logcatFilterParser = LogcatFilterParser(project, packageNamesProvider, androidProjectDetector)

  @VisibleForTesting
  val headerPanel = LogcatHeaderPanel(
    project,
    logcatPresenter = this,
    logcatFilterParser,
    state?.filter ?: getDefaultFilter(project, androidProjectDetector),
    state?.device,
  )


  @VisibleForTesting
  internal val messageProcessor = MessageProcessor(
    this,
    ::formatMessages,
    logcatFilterParser.parse(headerPanel.filter))

  private val toolbar = ActionManager.getInstance().createActionToolbar("LogcatMainPanel", createToolbarActions(project), false)
  private val hyperlinkDetector = hyperlinkDetector ?: EditorHyperlinkDetector(project, editor)
  private val foldingDetector = foldingDetector ?: EditorFoldingDetector(project, editor)
  private val logcatService = LogcatService.getInstance(project)
  private var ignoreCaretAtBottom = false // Derived from similar code in ConsoleViewImpl. See initScrollToEndStateHandling()
  private val connectedDevice = AtomicReference<Device?>()
  private val logcatServiceChannel = Channel<LogcatServiceEvent>(1)
  private val clientListener = ProjectAppMonitor(this, packageNamesProvider)

  @VisibleForTesting
  internal var logcatServiceJob: Job? = null
  private var editorWidth = 0

  init {
    editor.apply {
      installPopupHandler(object : ContextMenuPopupHandler() {
        override fun getActionGroup(event: EditorMouseEvent): ActionGroup = getPopupActionGroup(splitterPopupActionGroup.getChildren(null))
      })
      if (StudioFlags.LOGCAT_CLICK_TO_ADD_FILTER.get()) {
        addFilterHintHandlers()
      }

      // Keep track of the caret because calling caretModel.getOffset() is not reliable (b/239095674).
      caretModel.addCaretListener(object : CaretListener {
        override fun caretPositionChanged(event: CaretEvent) {
          caretLine = event.newPosition.line
        }
      }, this@LogcatMainPanel)

      scrollPane.border = Borders.customLine(JBColor.border(), 1, 1, 0, 0)
      UserInputHandlers(this).install()
    }

    isSoftWrapEnabled = state?.isSoftWrap ?: false
    addComponentListener(object : ComponentAdapter() {
      override fun componentResized(e: ComponentEvent) {
        val width = editor.scrollingModel.visibleArea.width
        val column = editor.xyToLogicalPosition(Point(width, 0)).column
        if (editorWidth != column) {
          editorWidth = column
          if (messageBacklog.get().messages.isNotEmpty()) {
            reloadMessages()
          }
        }
      }
    })

    toolbar.targetComponent = this

    // TODO(aalbert): Ideally, we would like to be able to select the connected device and client in the header from the `state` but this
    //  might be challenging both technically and from a UX perspective. Since, when restoring the state, the device/client might not be
    //  available.
    //  From a UX perspective, it's not clear what we should do in this case.
    //  From a technical standpoint, the current implementation that uses DevicePanel doesn't seem to be well suited for preselecting a
    //  device/client.
    addToTop(headerPanel)
    addToLeft(toolbar.component)

    pausedBanner.apply {
      text = LogcatBundle.message("logcat.main.panel.pause.banner.text")
      isVisible = false
    }
    noApplicationIdsBanner.apply {
      text = LogcatBundle.message("logcat.main.panel.no.application.ids.banner.text")
      createActionLabel(LogcatBundle.message("logcat.main.panel.no.application.ids.banner.sync.now")) {
        ProjectSystemService.getInstance(project).projectSystem.getSyncManager().syncProject(USER_REQUEST)
      }
      isVisible = isMissingApplicationIds()
    }
    noLogsBanner.apply {
      noLogsBanner.text = LogcatBundle.message("logcat.main.panel.no.logs.banner.text")
      createActionLabel(LogcatBundle.message("logcat.main.panel.no.logs.banner.clear.filter")) {
        setFilter("")
      }
      isVisible = isLogsMissing()
    }
    val centerPanel = JPanel(null).apply {
      layout = GroupLayout(this).apply {
        val height = pausedBanner.preferredSize.height
        setVerticalGroup(
          createSequentialGroup()
            .addComponent(noLogsBanner, height, height, height)
            .addComponent(noApplicationIdsBanner, height, height, height)
            .addComponent(pausedBanner, height, height, height)
            .addComponent(editor.component))
        setHorizontalGroup(
          createParallelGroup(GroupLayout.Alignment.CENTER)
            .addComponent(noLogsBanner)
            .addComponent(noApplicationIdsBanner)
            .addComponent(pausedBanner)
            .addComponent(editor.component))
      }
    }
    addToCenter(centerPanel)

    initScrollToEndStateHandling()

    LogcatUsageTracker.log(
      LogcatUsageEvent.newBuilder()
        .setType(PANEL_ADDED)
        .setPanelAdded(
          LogcatPanelEvent.newBuilder()
            .setIsRestored(state != null)
            .setFilter(logcatFilterParser.getUsageTrackingEvent(headerPanel.filter))
            .setFormatConfiguration(state?.formattingConfig.toUsageTracking())))

    project.messageBus.let { messageBus ->
      messageBus.connect(this).subscribe(ClearLogcatListener.TOPIC, ClearLogcatListener {
        if (connectedDevice.get()?.serialNumber == it) {
          clearMessageView()
        }
      })
      messageBus.connect(this).subscribe(PROJECT_APPLICATION_IDS_CHANGED_TOPIC, ProjectApplicationIdsListener {
        if (getFilter().contains(MY_PACKAGE)) {
          UIUtil.invokeLaterIfNeeded {
            noApplicationIdsBanner.isVisible = isMissingApplicationIds()
            reloadMessages()
          }
        }
      })
    }

    coroutineScope.launch(workerThread) {
      headerPanel.trackSelectedDevice().collect { device ->
        when {
          device.isOnline -> logcatServiceChannel.send(StartLogcat(device))
          else -> logcatServiceChannel.send(StopLogcat)
        }
      }
    }

    coroutineScope.launch {
      logcatServiceChannel.consumeEach {
        logcatServiceJob?.cancel()
        logcatServiceJob = when (it) {
          is StartLogcat -> startLogcat(it.device).also { isLogcatPaused = false }
          StopLogcat -> connectedDevice.set(null).let { null }
          PauseLogcat -> null.also { isLogcatPaused = true }
        }
      }
    }

    AndroidDebugBridge.addDeviceChangeListener(clientListener)
    AndroidDebugBridge.addClientChangeListener(clientListener)
  }

  private fun getPopupActionGroup(actions: Array<AnAction>): ActionGroup {
    return DefaultActionGroup().apply {
      add(CopyAction().withText(ActionsBundle.message("action.EditorCopy.text")).withIcon(AllIcons.Actions.Copy))
      add(CopyMessageTextAction())
      add(SearchWebAction().withText(ActionsBundle.message("action.\$SearchWeb.text")))
      add(LogcatFoldLinesLikeThisAction(editor))
      add(ToggleFilterAction(this@LogcatMainPanel, logcatFilterParser))
      add(IgnoreTagAction())
      add(CreateScratchFileAction())
      add(Separator.create())
      actions.forEach { add(it) }
      add(Separator.create())
      add(ClearLogcatAction())
    }
  }

  override fun foldImmediately() {
    foldingDetector.detectFoldings(0, editor.document.lineCount - 1)
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

  override suspend fun processMessages(messages: List<LogcatMessage>) {
    messageBacklog.get().addAll(messages)
    messages.forEach {
      val (_, _, _, applicationId, processName, tag, _) = it.header
      tags.add(tag)
      packages.add(applicationId)
      processNames.add(processName)
    }
    messageProcessor.appendMessages(messages)
  }

  override fun getState(): String {
    val formattingOptionsStyle = formattingOptions.getStyle()
    return LogcatPanelConfig.toJson(
      LogcatPanelConfig(
        headerPanel.getSelectedDevice()?.copy(isOnline = false),
        if (formattingOptionsStyle == null) Custom(formattingOptions) else Preset(formattingOptionsStyle),
        headerPanel.filter,
        isSoftWrapEnabled))
  }

  override suspend fun appendMessages(textAccumulator: TextAccumulator) = withContext(uiThread(ModalityState.any())) {
    LOGGER.debug { "Appending ${textAccumulator.text.length} bytes. isActive=$isActive" }
    if (!isActive) {
      return@withContext
    }
    // Derived from similar code in ConsoleViewImpl. See initScrollToEndStateHandling()
    val shouldStickToEnd = !ignoreCaretAtBottom && isCaretAtBottom()
    ignoreCaretAtBottom = false // The 'ignore' only needs to last for one update. Next time, isCaretAtBottom() will be false.
    // Mark the end for post-processing. Adding text changes the lines due to the cyclic buffer.
    val endMarker: RangeMarker = document.createRangeMarker(document.textLength, document.textLength)

    documentAppender.appendToDocument(textAccumulator)
    noLogsBanner.isVisible = isLogsMissing()

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
    AndroidDebugBridge.removeDeviceChangeListener(clientListener)
    AndroidDebugBridge.removeClientChangeListener(clientListener)
  }

  override fun applyLogcatSettings(logcatSettings: AndroidLogcatSettings) {
    this.logcatSettings = logcatSettings
    val bufferSize = logcatSettings.bufferSize
    documentAppender.setMaxDocumentSize(bufferSize)
    messageBacklog.get().setMaxSize(bufferSize)
    runInEdt {
      reloadMessages()
    }
  }

  @UiThread
  override fun applyFilter(logcatFilter: LogcatFilter?) {
    LOGGER.debug { "Applying filter $logcatFilter" }
    messageProcessor.logcatFilter = logcatFilter
    noApplicationIdsBanner.isVisible = isMissingApplicationIds()
    reloadMessages()
  }

  private fun isMissingApplicationIds(): Boolean {
    return when {
      !androidProjectDetector.isAndroidProject(project) -> false
      getFilter().contains(MY_PACKAGE) && packageNamesProvider.getPackageNames().isEmpty() -> true
      else -> false
    }
  }

  private fun isLogsMissing(): Boolean {
    return document.immutableCharSequence.isEmpty()
           && messageBacklog.get().messages.isNotEmpty()
           && !isMissingApplicationIds()
           && headerPanel.filter.isNotEmpty()
  }

  @UiThread
  override fun reloadMessages() {
    document.setText("")
    coroutineScope.launch(workerThread) {
      messageProcessor.appendMessages(messageBacklog.get().messages)
      withContext(uiThread) {
        noLogsBanner.isVisible = isLogsMissing()
      }
    }
  }

  override fun getConnectedDevice() = connectedDevice.get()

  override fun getSelectedDevice() = headerPanel.getSelectedDevice()

  override fun countFilterMatches(filter: LogcatFilter?): Int {
    return LogcatMasterFilter(filter).filter(messageBacklog.get().messages).size
  }

  override fun getTags(): Set<String> = tags

  override fun getPackageNames(): Set<String> = packages

  override fun getProcessNames(): Set<String> = processNames

  private fun createToolbarActions(project: Project): ActionGroup {
    return DefaultActionGroup().apply {
      add(ClearLogcatAction())
      add(PauseLogcatAction())
      add(RestartLogcatAction())
      add(LogcatScrollToTheEndToolbarAction(editor))
      add(PreviousOccurrenceToolbarAction(LogcatOccurrenceNavigator(project, editor)))
      add(NextOccurrenceToolbarAction(LogcatOccurrenceNavigator(project, editor)))
      add(LogcatToggleUseSoftWrapsToolbarAction())
      add(Separator.create())
      add(LogcatFormatAction(project, this@LogcatMainPanel))
      add(Separator.create())
      add(LogcatSplitterActions(splitterPopupActionGroup))
      add(Separator.create())
      add(ScreenshotAction())
      add(ScreenRecorderAction())
    }
  }

  @UiThread
  override fun isLogcatPaused(): Boolean = isLogcatPaused

  @UiThread
  override fun pauseLogcat() {
    coroutineScope.launch {
      logcatServiceChannel.send(PauseLogcat)
    }
    pausedBanner.isVisible = true
  }

  @UiThread
  override fun resumeLogcat() {
    restartLogcat()
  }


  override fun isSoftWrapEnabled(): Boolean  = isSoftWrapEnabled

  override fun setSoftWrapEnabled(state: Boolean) {
    isSoftWrapEnabled = state
    reloadMessages()
  }

  override fun clearMessageView() {
    coroutineScope.launch(workerThread) {
      val device = connectedDevice.get()
      val systemMessages = mutableListOf<LogcatMessage>()
      if (device != null) {
        if (device.sdk == 26) {
          // See http://b/issues/37109298#comment9.
          // TL/DR:
          // On API 26, "logcat -c" will hand for a couple of seconds and then crash any running logcat processes.
          //
          // Theoretically, we could stop the running logcat here before sending "logcat -c" to the device but this is not trivial. And we
          // have to do this for all active Logcat panels listening on this device, not only in the current project but across all projects.
          // A much easier and safer workaround is to not send a "logcat -c" command on this particular API level.
          systemMessages.add(LogcatMessage(SYSTEM_HEADER, LogcatBundle.message("logcat.clear.skipped")))
        }
        else {
          try {
            logcatService.clearLogcat(device)
          }
          catch (e: TimeoutException) {
            LOGGER.warn("Timed out executing logcat -c")
            systemMessages.add(LogcatMessage(SYSTEM_HEADER, LogcatBundle.message("logcat.clear.timeout")))
          }
        }
      }
      messageBacklog.set(MessageBacklog(logcatSettings.bufferSize))
      withContext(uiThread) {
        document.setText("")
        noLogsBanner.isVisible = isLogsMissing()
        processMessages(systemMessages)
      }
    }
  }

  @UiThread
  override fun restartLogcat() {
    pausedBanner.isVisible = false
    val device = connectedDevice.get() ?: return
    coroutineScope.launch {
      logcatServiceChannel.send(StartLogcat(device))
    }
  }

  override fun isLogcatEmpty() = messageBacklog.get().messages.isEmpty()

  override fun getData(dataId: String): Any? {
    val device = connectedDevice.get()
    return when (dataId) {
      LOGCAT_PRESENTER_ACTION.name -> this
      ScreenshotAction.SCREENSHOT_OPTIONS_KEY.name -> device?.let { DeviceArtScreenshotOptions(it.serialNumber, it.sdk, it.model) }
      ScreenRecorderAction.SCREEN_RECORDER_PARAMETERS_KEY.name -> device?.let {
        ScreenRecorderAction.Parameters(it.name, it.serialNumber, it.sdk, if (it.isEmulator) it.deviceId else null, this)
      }

      EDITOR.name -> editor
      else -> null
    }
  }

  // Derived from similar code in ConsoleViewImpl. See initScrollToEndStateHandling()
  @UiThread
  private fun updateScrollToEndState(useImmediatePosition: Boolean) {
    val scrollAtBottom = editor.isScrollAtBottom(useImmediatePosition)
    val caretAtBottom = isCaretAtBottom()
    if (!scrollAtBottom && caretAtBottom) {
      ignoreCaretAtBottom = true
    }
  }

  private suspend fun startLogcat(device: Device): Job {
    withContext(uiThread) {
      document.setText("")
    }
    messageBacklog.get().clear()

    return coroutineScope.launch(Dispatchers.IO) {
      logcatService.readLogcat(device).also {
        // Set the device after we start the service so that the service will be running when we
        // are reporting an active device.
        connectedDevice.set(device)
        it.collect { message -> processMessages(message) }
      }
    }
  }

  private fun scrollToEnd() {
    EditorUtil.scrollToTheEnd(editor, true)
    caretLine = document.lineCount
    ignoreCaretAtBottom = false
  }

  private fun formatMessages(textAccumulator: TextAccumulator, messages: List<LogcatMessage>) {
    messageFormatter.formatMessages(formattingOptions, textAccumulator, messages, if (isSoftWrapEnabled) editorWidth else null)
  }

  private fun MouseEvent.getFilterHint(): FilterHint? {
    val position = editor.xyToLogicalPosition(Point(x, y))
    val offset = editor.logicalPositionToOffset(position)
    var filterHint: FilterHint? = null
    document.processRangeMarkersOverlappingWith(offset, offset) {
      filterHint = it.getUserData(LOGCAT_FILTER_HINT_KEY)
      filterHint == null
    }
    return filterHint
  }

  override fun getFilter(): String = headerPanel.filter

  override fun setFilter(filter: String) {
    headerPanel.filter = filter
  }

  private fun EditorEx.addFilterHintHandlers() {
    // Note that adding & removing a filter to an existing filter properly is not trivial. For example, if the existing filter contains
    // logical operators & parens, just appending/deleting the filter term can result in unexpected results or even in an invalid filter.
    // For example: If the existing filter is "package:mine | level:error", trying to remove "level:error" will result in an invalid filter
    // "package:mine |".
    // Since this is an extreme use case, we don't attempt to be 100% correct when add/removing filters. We just verify that the resulting
    // filter is a valid filter. If it's not, we disable the action.
    contentComponent.addMouseListener(object : MouseAdapter() {
      override fun mouseClicked(e: MouseEvent) {
        if (e.isControlDown && e.button == BUTTON1) {
          val filterHint = e.getFilterHint()
          if (filterHint != null) {
            val newFilter = toggleFilterTerm(logcatFilterParser, headerPanel.filter, filterHint.getFilter())
            if (newFilter != null) {
              headerPanel.filter = newFilter
            }
          }
        }
      }
    })
    contentComponent.addMouseMotionListener(object : MouseAdapter() {
      override fun mouseMoved(e: MouseEvent) {
        val filterHint = e.getFilterHint()
        if (e.isControlDown) {
          if (filterHint != null && toggleFilterTerm(logcatFilterParser, headerPanel.filter, filterHint.getFilter()) != null) {
            contentComponent.cursor = handCursor
            return
          }
        }
        contentComponent.toolTipText = if (filterHint?.isElided() == true) filterHint.text else null

        contentComponent.cursor = textCursor
      }
    })
  }

  private sealed class LogcatServiceEvent {
    class StartLogcat(val device: Device) : LogcatServiceEvent()
    object StopLogcat : LogcatServiceEvent()
    object PauseLogcat : LogcatServiceEvent()
  }

  private fun isCaretAtBottom(): Boolean {
    return try {
      editor.isCaretAtBottom()
    }
    catch (t: Throwable) {
      // Logging as error in order to see how prevalent this is in the wild. See b/239095674
      LOGGER.error("Failed to check caret position directly. Using backup method.", t)
      caretLine >= document.lineCount - 1
    }
  }

  private class WarningNotificationPanel : EditorNotificationPanel(Banner.WARNING_BACKGROUND) {
    init {
      border = BorderFactory.createCompoundBorder(Borders.customLine(JBColor.border(), 1, 1, 0, 0), border)
    }
  }
}

private fun LogcatPanelConfig?.getFormattingOptions(): FormattingOptions =
  this?.formattingConfig?.toFormattingOptions() ?: AndroidLogcatFormattingOptions.getDefaultOptions()

private fun FormattingConfig?.toUsageTracking(): LogcatFormatConfiguration {
  val builder = LogcatFormatConfiguration.newBuilder()
  val formattingOptions: FormattingOptions
  when {
    this == null -> {
      val defaultFormatting = AndroidLogcatFormattingOptions.getInstance().defaultFormatting
      builder.preset = defaultFormatting.toUsageTracking()
      formattingOptions = defaultFormatting.formattingOptions
    }

    this is Preset -> {
      builder.preset = style.toUsageTracking()
      formattingOptions = style.formattingOptions
    }

    else -> {
      formattingOptions = this.toFormattingOptions()
    }
  }
  return builder
    .setIsShowTimestamp(formattingOptions.timestampFormat.enabled)
    .setIsShowDate(formattingOptions.timestampFormat.style == TimestampFormat.Style.DATETIME)
    .setIsShowProcessId(formattingOptions.processThreadFormat.enabled)
    .setIsShowThreadId(formattingOptions.processThreadFormat.style == ProcessThreadFormat.Style.BOTH)
    .setIsShowTags(formattingOptions.tagFormat.enabled)
    .setIsShowRepeatedTags(!formattingOptions.tagFormat.hideDuplicates)
    .setTagWidth(formattingOptions.tagFormat.maxLength)
    .setIsShowPackages(formattingOptions.appNameFormat.enabled)
    .setIsShowRepeatedPackages(!formattingOptions.appNameFormat.hideDuplicates)
    .setPackageWidth(formattingOptions.appNameFormat.maxLength)
    .build()
}

private fun FormattingOptions.Style.toUsageTracking() = if (this == FormattingOptions.Style.STANDARD) STANDARD else COMPACT

private fun AnAction.withText(text: String): AnAction {
  templatePresentation.text = text
  return this
}

private fun AnAction.withIcon(icon: Icon): AnAction {
  templatePresentation.icon = icon
  return this
}
