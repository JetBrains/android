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

import com.android.adblib.AdbLibSession
import com.android.annotations.concurrency.UiThread
import com.android.ddmlib.IDevice
import com.android.ddmlib.logcat.LogCatMessage
import com.android.tools.adtui.toolwindow.splittingtabs.state.SplittingTabsStateProvider
import com.android.tools.idea.adb.processnamemonitor.ProcessNameMonitor
import com.android.tools.idea.adblib.AdbLibService
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.concurrency.AndroidDispatchers.workerThread
import com.android.tools.idea.ddms.DeviceContext
import com.android.tools.idea.ddms.actions.ScreenRecorderAction
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.logcat.LogcatMainPanel.LogcatServiceEvent.StartLogcat
import com.android.tools.idea.logcat.LogcatMainPanel.LogcatServiceEvent.StopLogcat
import com.android.tools.idea.logcat.LogcatPanelConfig.FormattingConfig
import com.android.tools.idea.logcat.LogcatPanelConfig.FormattingConfig.Custom
import com.android.tools.idea.logcat.LogcatPanelConfig.FormattingConfig.Preset
import com.android.tools.idea.logcat.actions.ClearLogcatAction
import com.android.tools.idea.logcat.actions.LogcatFoldLinesLikeThisAction
import com.android.tools.idea.logcat.actions.LogcatFormatAction
import com.android.tools.idea.logcat.actions.LogcatSplitterActions
import com.android.tools.idea.logcat.actions.LogcatToggleUseSoftWrapsToolbarAction
import com.android.tools.idea.logcat.actions.NextOccurrenceToolbarAction
import com.android.tools.idea.logcat.actions.PreviousOccurrenceToolbarAction
import com.android.tools.idea.logcat.actions.RestartLogcatAction
import com.android.tools.idea.logcat.devices.Device
import com.android.tools.idea.logcat.filters.AndroidLogcatFilterHistory
import com.android.tools.idea.logcat.filters.LogcatFilter
import com.android.tools.idea.logcat.filters.LogcatFilterParser
import com.android.tools.idea.logcat.filters.LogcatMasterFilter
import com.android.tools.idea.logcat.folding.EditorFoldingDetector
import com.android.tools.idea.logcat.folding.FoldingDetector
import com.android.tools.idea.logcat.hyperlinks.EditorHyperlinkDetector
import com.android.tools.idea.logcat.hyperlinks.HyperlinkDetector
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
import com.android.tools.idea.logcat.service.LogcatServiceImpl
import com.android.tools.idea.logcat.settings.AndroidLogcatSettings
import com.android.tools.idea.logcat.util.AdbAdapter
import com.android.tools.idea.logcat.util.AdbAdapterImpl
import com.android.tools.idea.logcat.util.AndroidProjectDetector
import com.android.tools.idea.logcat.util.AndroidProjectDetectorImpl
import com.android.tools.idea.logcat.util.LogcatUsageTracker
import com.android.tools.idea.logcat.util.MostRecentlyAddedSet
import com.android.tools.idea.logcat.util.createLogcatEditor
import com.android.tools.idea.logcat.util.isCaretAtBottom
import com.android.tools.idea.logcat.util.isScrollAtBottom
import com.android.tools.idea.run.ClearLogcatListener
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
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.editor.actions.ScrollToTheEndToolbarAction
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.impl.ContextMenuPopupHandler
import com.intellij.openapi.project.Project
import com.intellij.tools.SimpleActionGroup
import com.intellij.util.ui.components.BorderLayoutPanel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.VisibleForTesting
import java.awt.Cursor
import java.awt.Point
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseEvent.BUTTON1
import java.awt.event.MouseWheelEvent
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicReference
import javax.swing.Icon
import kotlin.math.max
import kotlin.text.RegexOption.LITERAL

// This is probably a massive overkill as we do not expect this many tags/packages in a real Logcat
private const val MAX_TAGS = 1000
private const val MAX_PACKAGE_NAMES = 1000

private val HAND_CURSOR = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
private val TEXT_CURSOR = Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR)

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
internal class LogcatMainPanel(
  private val project: Project,
  private val splitterPopupActionGroup: ActionGroup,
  logcatColors: LogcatColors,
  state: LogcatPanelConfig?,
  private var logcatSettings: AndroidLogcatSettings = AndroidLogcatSettings.getInstance(),
  androidProjectDetector: AndroidProjectDetector = AndroidProjectDetectorImpl(),
  hyperlinkDetector: HyperlinkDetector? = null,
  foldingDetector: FoldingDetector? = null,
  packageNamesProvider: PackageNamesProvider = ProjectPackageNamesProvider(project),
  private val adbAdapter: AdbAdapter = AdbAdapterImpl(project),
  adbSession: AdbLibSession = AdbLibService.getInstance(project).session,
  private val logcatService: LogcatService =
    LogcatServiceImpl(project, { AdbLibService.getInstance(project).session.deviceServices }, ProcessNameMonitor.getInstance(project)),
  zoneId: ZoneId = ZoneId.systemDefault()
) : BorderLayoutPanel(), LogcatPresenter, SplittingTabsStateProvider, DataProvider, Disposable {

  @VisibleForTesting
  internal val editor: EditorEx = createLogcatEditor(project)
  private val document = editor.document
  private val documentAppender = DocumentAppender(project, document, logcatSettings.bufferSize)
  private val coroutineScope = AndroidCoroutineScope(this)

  // TODO(aalbert): We still need a DeviceContext for screenshot & screen record actions.
  @VisibleForTesting
  val deviceContext = DeviceContext()

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

  @VisibleForTesting
  val headerPanel = LogcatHeaderPanel(
    project,
    logcatPresenter = this,
    packageNamesProvider,
    state?.filter ?: getDefaultFilter(project, androidProjectDetector),
    state?.device,
    adbSession,
  )

  private val logcatFilterParser = LogcatFilterParser(project, packageNamesProvider, androidProjectDetector)

  @VisibleForTesting
  internal val messageProcessor = MessageProcessor(
    this,
    ::formatMessages,
    logcatFilterParser.parse(headerPanel.filter))

  private val toolbar = ActionManager.getInstance().createActionToolbar("LogcatMainPanel", createToolbarActions(project), false)
  private val hyperlinkDetector = hyperlinkDetector ?: EditorHyperlinkDetector(project, editor)
  private val foldingDetector = foldingDetector ?: EditorFoldingDetector(project, editor)
  private var ignoreCaretAtBottom = false // Derived from similar code in ConsoleViewImpl. See initScrollToEndStateHandling()
  private val connectedDevice = AtomicReference<Device?>()
  private val logcatServiceChannel = Channel<LogcatServiceEvent>(1)

  init {
    editor.apply {
      installPopupHandler(object : ContextMenuPopupHandler() {
        override fun getActionGroup(event: EditorMouseEvent): ActionGroup = getPopupActionGroup(splitterPopupActionGroup.getChildren(null))
      })
      settings.isUseSoftWraps = state?.isSoftWrap ?: false
      if (StudioFlags.LOGCAT_CLICK_TO_ADD_FILTER.get()) {
        addFilterHintHandlers()
      }
    }

    toolbar.targetComponent = this

    // TODO(aalbert): Ideally, we would like to be able to select the connected device and client in the header from the `state` but this
    //  might be challenging both technically and from a UX perspective. Since, when restoring the state, the device/client might not be
    //  available.
    //  From a UX perspective, it's not clear what we should do in this case.
    //  From a technical standpoint, the current implementation that uses DevicePanel doesn't seem to be well suited for preselecting a
    //  device/client.
    addToTop(headerPanel)
    addToLeft(toolbar.component)
    addToCenter(editor.component)

    initScrollToEndStateHandling()

    LogcatUsageTracker.log(
      LogcatUsageEvent.newBuilder()
        .setType(PANEL_ADDED)
        .setPanelAdded(
          LogcatPanelEvent.newBuilder()
            .setIsRestored(state != null)
            .setFilter(logcatFilterParser.getUsageTrackingEvent(headerPanel.filter))
            .setFormatConfiguration(state?.formattingConfig.toUsageTracking())))

    project.messageBus.connect(this).subscribe(ClearLogcatListener.TOPIC, ClearLogcatListener {
      if (connectedDevice.get()?.serialNumber == it.serialNumber) {
        clearMessageView()
      }
    })

    coroutineScope.launch(workerThread) {
      headerPanel.trackSelectedDevice().collect { device ->
        when {
          device.isOnline -> logcatServiceChannel.send(StartLogcat(device))
          else -> logcatServiceChannel.send(StopLogcat)
        }
      }
    }

    coroutineScope.launch {
      var job : Job? = null
      logcatServiceChannel.consumeEach {
        job?.cancel()
        job = when (it) {
          is StartLogcat -> startLogcat(it.device)
          StopLogcat -> stopLogcat().let { null }
        }
      }
    }
  }

  private fun getPopupActionGroup(actions: Array<AnAction>): ActionGroup {
    return SimpleActionGroup().apply {
      add(CopyAction().withText(ActionsBundle.message("action.EditorCopy.text")).withIcon(AllIcons.Actions.Copy))
      add(SearchWebAction().withText(ActionsBundle.message("action.\$SearchWeb.text")))
      add(LogcatFoldLinesLikeThisAction(editor))
      add(Separator.create())
      actions.forEach { add(it) }
      add(Separator.create())
      add(ClearLogcatAction(this@LogcatMainPanel))
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

  override suspend fun processMessages(messages: List<LogCatMessage>) {
    messageBacklog.get().addAll(messages)
    tags.addAll(messages.map { it.header.tag })
    packages.addAll(messages.map(LogCatMessage::getPackageNameOrPid))
    messageProcessor.appendMessages(messages)
  }

  override fun getState(): String {
    val formattingOptionsStyle = formattingOptions.getStyle()
    return LogcatPanelConfig.toJson(
      LogcatPanelConfig(
        headerPanel.getSelectedDevice()?.copy(isOnline = false),
        if (formattingOptionsStyle == null) Custom(formattingOptions) else Preset(formattingOptionsStyle),
        headerPanel.filter,
        editor.settings.isUseSoftWraps))
  }

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

  override fun applyLogcatSettings(logcatSettings: AndroidLogcatSettings) {
    this.logcatSettings = logcatSettings
    val bufferSize = logcatSettings.bufferSize
    documentAppender.setMaxDocumentSize(bufferSize)
    messageBacklog.get().setMaxSize(bufferSize)
  }

  @UiThread
  override fun applyFilter(logcatFilter: LogcatFilter?) {
    messageProcessor.logcatFilter = logcatFilter
    reloadMessages()
  }

  @UiThread
  override fun reloadMessages() {
    document.setText("")
    coroutineScope.launch(workerThread) {
      messageProcessor.appendMessages(messageBacklog.get().messages)
    }
  }

  override fun getConnectedDevice() = connectedDevice.get()

  override fun selectDevice(serialNumber: String) {
    headerPanel.selectDevice(serialNumber)
  }

  override fun countFilterMatches(filter: String): Int {
    return LogcatMasterFilter(logcatFilterParser.parse(filter)).filter(messageBacklog.get().messages).size
  }

  override fun getTags(): Set<String> = tags

  override fun getPackageNames(): Set<String> = packages

  private fun createToolbarActions(project: Project): ActionGroup {
    return SimpleActionGroup().apply {
      add(ClearLogcatAction(this@LogcatMainPanel))
      add(RestartLogcatAction(this@LogcatMainPanel))
      add(ScrollToTheEndToolbarAction(editor).apply {
        @Suppress("DialogTitleCapitalization")
        templatePresentation.text = LogcatBundle.message("logcat.scroll.to.end.action.text")
      })
      add(PreviousOccurrenceToolbarAction(LogcatOccurrenceNavigator(project, editor)))
      add(NextOccurrenceToolbarAction(LogcatOccurrenceNavigator(project, editor)))
      add(LogcatToggleUseSoftWrapsToolbarAction(editor))
      add(Separator.create())
      add(LogcatFormatAction(project, this@LogcatMainPanel))
      add(Separator.create())
      add(LogcatSplitterActions(splitterPopupActionGroup))
      add(Separator.create())
      //add(DeviceScreenshotAction(project, deviceContext))
      add(ScreenshotAction())
      add(ScreenRecorderAction(project, deviceContext))
    }
  }

  @UiThread
  override fun clearMessageView() {
    coroutineScope.launch(workerThread) {
      val device = connectedDevice.get()
      if (device != null) {
        if (device.sdk != 26) {
          // See http://b/issues/37109298#comment9.
          // TL/DR:
          // On API 26, "logcat -c" will hand for a couple of seconds and then crash any running logcat processes.
          //
          // Theoretically, we could stop the running logcat here before sending "logcat -c" to the device but this is not trivial. And we
          // have to do this for all active Logcat panels listening on this device, not only in the current project but across all projects.
          // A much easier and safer workaround is to not send a "logcat -c" command on this particular API level.
          logcatService.clearLogcat(device)

        }
      }
      messageBacklog.set(MessageBacklog(logcatSettings.bufferSize))
      withContext(uiThread) {
        document.setText("")
        if (connectedDevice.get()?.sdk == 26) {
          processMessages(listOf(LogCatMessage(
            SYSTEM_HEADER,
            "WARNING: Logcat was not cleared on the device itself because of a bug in Android 8.0 (Oreo).")))
        }
      }
    }
  }

  @UiThread
  override fun restartLogcat() {
    val device = connectedDevice.get() ?: return
    coroutineScope.launch {
      logcatServiceChannel.send(StartLogcat(device))
    }
  }

  override fun isLogcatEmpty() = messageBacklog.get().messages.isEmpty()

  override fun getData(dataId: String): Any? {
    return when (dataId) {
      ScreenshotAction.SERIAL_NUMBER_KEY.name -> connectedDevice.get()?.serialNumber
      ScreenshotAction.SDK_KEY.name -> connectedDevice.get()?.sdk
      ScreenshotAction.MODEL_KEY.name -> connectedDevice.get()?.model
      EDITOR.name -> editor
      else -> null
    }
  }

  // Derived from similar code in ConsoleViewImpl. See initScrollToEndStateHandling()
  @UiThread
  private fun updateScrollToEndState(useImmediatePosition: Boolean) {
    val scrollAtBottom = editor.isScrollAtBottom(useImmediatePosition)
    val caretAtBottom = editor.isCaretAtBottom()
    if (!scrollAtBottom && caretAtBottom) {
      ignoreCaretAtBottom = true
    }
  }

  private suspend fun startLogcat(device: Device): Job {
    withContext(uiThread) {
      document.setText("")
    }
    messageBacklog.get().clear()
    connectedDevice.set(device)
    return coroutineScope.launch(Dispatchers.IO) {
      // TODO(aalbert) : Get rid of this when we have our own Screenshot/Screen record actions
      deviceContext.fireDeviceSelected(findIDevice(device))
      logcatService.readLogcat(device).collect {
        processMessages(it)
      }
    }
  }

  private fun stopLogcat() {
    // TODO(aalbert) : Get rid of this when we have our own Screenshot/Screen record actions
    deviceContext.fireDeviceSelected(null)
    connectedDevice.set(null)
  }

  private fun scrollToEnd() {
    EditorUtil.scrollToTheEnd(editor, true)
    ignoreCaretAtBottom = false
  }

  private fun formatMessages(textAccumulator: TextAccumulator, messages: List<LogCatMessage>) {
    messageFormatter.formatMessages(formattingOptions, textAccumulator, messages)
  }

  private suspend fun findIDevice(device: Device): IDevice? {
    val devices = adbAdapter.getDevices()
    return if (device.isEmulator) {
      devices.find { device.deviceId == it.avdData?.await()?.name } ?: devices.find { device.deviceId == it.serialNumber }
    }
    else {
      devices.find { device.deviceId == it.serialNumber }
    }
  }

  private fun MouseEvent.getHintFilter(): String? {
    val position = editor.xyToLogicalPosition(Point(x, y))
    val offset = editor.logicalPositionToOffset(position)
    var filterHint: FilterHint? = null
    document.processRangeMarkersOverlappingWith(offset, offset) {
      filterHint = it.getUserData(LOGCAT_FILTER_HINT_KEY)
      false
    }
    return filterHint?.getFilter()
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
          val hintFilter = e.getHintFilter()
          if (hintFilter != null) {
            val newFilter = calculateNewFilter(hintFilter)
            if (newFilter != null) {
              headerPanel.filter = newFilter
            }
          }
        }
      }
    })
    contentComponent.addMouseMotionListener(object : MouseAdapter() {
      override fun mouseMoved(e: MouseEvent) {
        if (e.isControlDown) {
          val hintFilter = e.getHintFilter()
          if (hintFilter != null && calculateNewFilter(hintFilter) != null) {
            contentComponent.cursor = HAND_CURSOR
            return
          }
        }
        contentComponent.cursor = TEXT_CURSOR
      }
    })
  }

  private fun calculateNewFilter(filter: String): String? {
    val oldFilter = headerPanel.filter
    val newFilter = when {
      oldFilter.contains(filter) -> oldFilter.replace("""\b+${filter.toRegex(LITERAL)}\b+""".toRegex(), " ").trim()
      oldFilter.isEmpty() -> filter
      else -> headerPanel.filter + " $filter"
    }

    return if (logcatFilterParser.isValid(newFilter)) newFilter else null
  }

  private sealed class LogcatServiceEvent {
    class StartLogcat(val device: Device) : LogcatServiceEvent()
    object StopLogcat : LogcatServiceEvent()
  }
}

private fun LogCatMessage.getPackageNameOrPid() = if (header.appName == "?") "pid-${header.pid}" else header.appName

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

private fun getDefaultFilter(project: Project, androidProjectDetector: AndroidProjectDetector): String {
  val logcatSettings = AndroidLogcatSettings.getInstance()
  val filter = when {
    logcatSettings.mostRecentlyUsedFilterIsDefault -> AndroidLogcatFilterHistory.getInstance().mostRecentlyUsed
    else -> logcatSettings.defaultFilter
  }
  return if (!androidProjectDetector.isAndroidProject(project) && filter.contains("package:mine")) "" else filter
}

private fun AnAction.withText(text: String): AnAction {
  templatePresentation.text = text
  return this
}

private fun AnAction.withIcon(icon: Icon): AnAction {
  templatePresentation.icon = icon
  return this
}
