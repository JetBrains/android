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

import com.android.adblib.testing.FakeAdbSession
import com.android.processmonitor.common.ProcessEvent.ProcessAdded
import com.android.processmonitor.monitor.ProcessNameMonitor
import com.android.processmonitor.monitor.testing.FakeProcessNameMonitor
import com.android.testutils.MockitoKt.eq
import com.android.testutils.MockitoKt.mock
import com.android.testutils.waitForCondition
import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.swing.FakeMouse.Button.CTRL_LEFT
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.popup.PopupRule
import com.android.tools.analytics.UsageTrackerRule
import com.android.tools.idea.FakeAndroidProjectDetector
import com.android.tools.idea.adblib.AdbLibService
import com.android.tools.idea.adblib.testing.TestAdbLibService
import com.android.tools.idea.concurrency.AndroidExecutors
import com.android.tools.idea.logcat.LogcatPanelConfig.FormattingConfig
import com.android.tools.idea.logcat.actions.PopupActionGroupAction
import com.android.tools.idea.logcat.devices.Device
import com.android.tools.idea.logcat.devices.DeviceComboBoxDeviceTrackerFactory
import com.android.tools.idea.logcat.devices.FakeDeviceComboBoxDeviceTracker
import com.android.tools.idea.logcat.filters.AndroidLogcatFilterHistory
import com.android.tools.idea.logcat.filters.LogcatFilterField.IMPLICIT_LINE
import com.android.tools.idea.logcat.filters.LogcatFilterField.LINE
import com.android.tools.idea.logcat.filters.LogcatFilterField.TAG
import com.android.tools.idea.logcat.filters.LogcatMasterFilter
import com.android.tools.idea.logcat.filters.ProjectAppFilter
import com.android.tools.idea.logcat.filters.StringFilter
import com.android.tools.idea.logcat.folding.FoldingDetector
import com.android.tools.idea.logcat.hyperlinks.HyperlinkDetector
import com.android.tools.idea.logcat.message.LogLevel
import com.android.tools.idea.logcat.message.LogLevel.DEBUG
import com.android.tools.idea.logcat.message.LogLevel.INFO
import com.android.tools.idea.logcat.message.LogLevel.WARN
import com.android.tools.idea.logcat.message.LogcatHeader
import com.android.tools.idea.logcat.message.LogcatMessage
import com.android.tools.idea.logcat.messages.AndroidLogcatFormattingOptions
import com.android.tools.idea.logcat.messages.FormattingOptions
import com.android.tools.idea.logcat.messages.FormattingOptions.Style.COMPACT
import com.android.tools.idea.logcat.messages.FormattingOptions.Style.STANDARD
import com.android.tools.idea.logcat.messages.LogcatColors
import com.android.tools.idea.logcat.messages.TagFormat
import com.android.tools.idea.logcat.service.LogcatService
import com.android.tools.idea.logcat.settings.AndroidLogcatSettings
import com.android.tools.idea.logcat.util.AndroidProjectDetector
import com.android.tools.idea.logcat.util.LOGGER
import com.android.tools.idea.logcat.util.TIMEOUT_SEC
import com.android.tools.idea.logcat.util.isCaretAtBottom
import com.android.tools.idea.logcat.util.logcatEvents
import com.android.tools.idea.logcat.util.logcatMessage
import com.android.tools.idea.logcat.util.onIdle
import com.android.tools.idea.logcat.util.waitForCondition
import com.android.tools.idea.projectsystem.ProjectApplicationIdsProvider
import com.android.tools.idea.run.ClearLogcatListener
import com.android.tools.idea.testing.AndroidExecutorsRule
import com.android.tools.idea.testing.ApplicationServiceRule
import com.android.tools.idea.testing.ProjectServiceRule
import com.android.tools.idea.testing.TestLoggerRule
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.LogcatUsageEvent
import com.google.wireless.android.sdk.stats.LogcatUsageEvent.LogcatFilterEvent
import com.google.wireless.android.sdk.stats.LogcatUsageEvent.LogcatFormatConfiguration
import com.google.wireless.android.sdk.stats.LogcatUsageEvent.LogcatFormatConfiguration.Preset
import com.google.wireless.android.sdk.stats.LogcatUsageEvent.LogcatPanelEvent
import com.google.wireless.android.sdk.stats.LogcatUsageEvent.Type.PANEL_ADDED
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionGroup.EMPTY_GROUP
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnAction.ACTIONS_KEY
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.actionSystem.impl.ActionMenuItem
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.TextRange.EMPTY_RANGE
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.replaceService
import com.intellij.testFramework.runInEdtAndGet
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.ui.ClientProperty
import com.intellij.ui.EditorNotificationPanel
import com.intellij.util.ConcurrencyUtil
import java.awt.BorderLayout
import java.awt.BorderLayout.CENTER
import java.awt.BorderLayout.NORTH
import java.awt.BorderLayout.WEST
import java.awt.Dimension
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.Executors
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.TimeoutException
import javax.swing.JPanel
import javax.swing.JPopupMenu
import kotlinx.coroutines.runBlocking
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.any
import org.mockito.Mockito.times
import org.mockito.Mockito.verify

/** Tests for [LogcatMainPanel] */
class LogcatMainPanelTest {
  private val projectRule = ProjectRule()
  private val executor = Executors.newCachedThreadPool()
  private val popupRule = PopupRule()
  private val androidExecutorsRule = AndroidExecutorsRule(workerThreadExecutor = executor)
  private val usageTrackerRule = UsageTrackerRule()
  private val disposableRule = DisposableRule()

  private val androidLogcatFormattingOptions = AndroidLogcatFormattingOptions()
  private val fakeLogcatService = FakeLogcatService()
  private val deviceTracker = FakeDeviceComboBoxDeviceTracker()
  private val fakeProcessNameMonitor = FakeProcessNameMonitor()

  @get:Rule
  val rule =
    RuleChain(
      projectRule,
      ApplicationServiceRule(
        AndroidLogcatFormattingOptions::class.java,
        androidLogcatFormattingOptions,
      ),
      ProjectServiceRule(
        projectRule,
        AdbLibService::class.java,
        TestAdbLibService(FakeAdbSession()),
      ),
      ProjectServiceRule(projectRule, LogcatService::class.java, fakeLogcatService),
      ProjectServiceRule(
        projectRule,
        DeviceComboBoxDeviceTrackerFactory::class.java,
        DeviceComboBoxDeviceTrackerFactory { deviceTracker },
      ),
      ProjectServiceRule(projectRule, ProcessNameMonitor::class.java, fakeProcessNameMonitor),
      EdtRule(),
      androidExecutorsRule,
      popupRule,
      usageTrackerRule,
      disposableRule,
      TestLoggerRule(),
    )

  private val mockHyperlinkDetector = mock<HyperlinkDetector>()
  private val mockFoldingDetector = mock<FoldingDetector>()
  private val project
    get() = projectRule.project

  private val disposable
    get() = disposableRule.disposable

  private val device1 = Device.createPhysical("device1", true, "11", 30, "Google", "Pixel")
  private val device2 = Device.createPhysical("device2", true, "11", 30, "Google", "Pixel")

  @RunsInEdt
  @Test
  fun createsComponents() {
    // In prod, splitter actions are provided by the Splitting Tabs component. In tests, we create a
    // stand-in
    val splitterActions =
      DefaultActionGroup().apply {
        add(
          object : AnAction("Splitter Action") {
            override fun actionPerformed(e: AnActionEvent) {}
          }
        )
      }

    val logcatMainPanel = logcatMainPanel(splitterPopupActionGroup = splitterActions)

    val borderLayout = logcatMainPanel.layout as BorderLayout
    assertThat(logcatMainPanel.componentCount).isEqualTo(3)
    assertThat(borderLayout.getLayoutComponent(NORTH)).isInstanceOf(LogcatHeaderPanel::class.java)
    val centerComponent: JPanel = borderLayout.getLayoutComponent(CENTER) as JPanel
    assertThat(logcatMainPanel.findBanner("Logcat is paused").isVisible).isFalse()
    assertThat(
        logcatMainPanel
          .findBanner("Could not detect project package names. Is the project synced?")
          .isVisible
      )
      .isFalse()
    assertThat(centerComponent.components.find { it === logcatMainPanel.editor.component })
      .isNotNull()
    assertThat(borderLayout.getLayoutComponent(WEST)).isInstanceOf(ActionToolbar::class.java)
    val toolbar = borderLayout.getLayoutComponent(WEST) as ActionToolbar
    assertThat(toolbar.actions.mapToStrings())
      .containsExactly(
        "Clear Logcat",
        "Pause Logcat",
        "Restart Logcat",
        "Scroll to the End (clicking on a particular line stops scrolling and keeps that line visible)",
        "Previous Occurrence",
        "Next Occurrence",
        "Soft-Wrap",
        "-",
        "Import Logs from a File",
        "Export Logs to a File",
        "-",
        "Configure Logcat Formatting Options",
        "  Standard View",
        "  Compact View",
        "  -",
        "  Modify Views",
        "-",
        "Split Panels",
        "  Splitter Action",
        "-",
        "Take Screenshot",
        "Record Screen",
        "-", // ActionManager.createActionToolbar() seems to add a separator at the end
      )
      .inOrder()
    toolbar.actions.forEach { assertThat(it).isInstanceOf(DumbAware::class.java) }
  }

  @Test
  fun setsDocumentCyclicBuffer() = runBlocking {
    val logcatMainPanel = runInEdtAndGet {
      logcatMainPanel(logcatSettings = AndroidLogcatSettings(bufferSize = 1024))
    }
    val document = logcatMainPanel.editor.document as DocumentImpl
    val logcatMessage = logcatMessage()

    // Insert 20 log lines
    logcatMainPanel.messageProcessor.appendMessages(List(20) { logcatMessage })
    logcatMainPanel.messageProcessor.onIdle {
      assertThat(document.immutableText().length).isAtMost(1024 + logcatMessage.length())
    }
  }

  /**
   * This test can't run in the EDT because it depends on coroutines that are launched in the UI
   * Thread and need to be able to wait for them to complete. If it runs in the EDT, it cannot wait
   * for these tasks to execute.
   */
  @Test
  fun appendMessages() = runBlocking {
    val logcatMainPanel = runInEdtAndGet { logcatMainPanel(zoneId = ZoneId.of("Asia/Yerevan")) }

    logcatMainPanel.messageProcessor.appendMessages(
      listOf(
        LogcatMessage(
          LogcatHeader(WARN, 1, 2, "app1", "", "tag1", Instant.ofEpochMilli(1000)),
          "message1",
        ),
        LogcatMessage(
          LogcatHeader(INFO, 1, 2, "app2", "", "tag2", Instant.ofEpochMilli(1000)),
          "message2",
        ),
      )
    )

    logcatMainPanel.messageProcessor.onIdle {
      assertThat(logcatMainPanel.editor.document.immutableText())
        .isEqualTo(
          """
        1970-01-01 04:00:01.000     1-2     tag1                    app1                                 W  message1
        1970-01-01 04:00:01.000     1-2     tag2                    app2                                 I  message2

      """
            .trimIndent()
        )
    }
  }

  @Test
  fun applyFilter() = runBlocking {
    val logcatMainPanel = runInEdtAndGet(this@LogcatMainPanelTest::logcatMainPanel)
    logcatMainPanel.processMessages(
      listOf(
        LogcatMessage(
          LogcatHeader(WARN, 1, 2, "app1", "", "tag1", Instant.ofEpochMilli(1000)),
          "message1",
        ),
        LogcatMessage(
          LogcatHeader(INFO, 1, 2, "app2", "", "tag2", Instant.ofEpochMilli(1000)),
          "message2",
        ),
      )
    )

    logcatMainPanel.messageProcessor.onIdle {
      logcatMainPanel.applyFilter(StringFilter("tag1", LINE, matchCase = true, EMPTY_RANGE))
    }

    ConcurrencyUtil.awaitQuiescence(
      AndroidExecutors.getInstance().workerThreadExecutor as ThreadPoolExecutor,
      5,
      SECONDS,
    )
    logcatMainPanel.messageProcessor.onIdle {
      assertThat(logcatMainPanel.editor.document.immutableText())
        .isEqualTo(
          """
        1970-01-01 04:00:01.000     1-2     tag1                    app1                                 W  message1

      """
            .trimIndent()
        )
    }
  }

  @Test
  fun appendMessages_disposedEditor(): Unit = runBlocking {
    val logcatMainPanel = runInEdtAndGet { logcatMainPanel().also { Disposer.dispose(it) } }

    logcatMainPanel.messageProcessor.appendMessages(listOf(logcatMessage()))
  }

  @Test
  fun appendMessages_scrollToEnd() = runBlocking {
    val logcatMainPanel = runInEdtAndGet(this@LogcatMainPanelTest::logcatMainPanel)

    logcatMainPanel.messageProcessor.appendMessages(listOf(logcatMessage(), logcatMessage()))

    logcatMainPanel.messageProcessor.onIdle {
      @Suppress("ConvertLambdaToReference")
      assertThat(logcatMainPanel.editor.isCaretAtBottom()).isTrue()
    }
  }

  @Test
  fun appendMessages_notAtBottom_doesNotScrollToEnd() = runBlocking {
    val logcatMainPanel = runInEdtAndGet(this@LogcatMainPanelTest::logcatMainPanel)

    logcatMainPanel.messageProcessor.appendMessages(listOf(logcatMessage()))
    logcatMainPanel.messageProcessor.onIdle { logcatMainPanel.editor.caretModel.moveToOffset(0) }
    logcatMainPanel.messageProcessor.appendMessages(listOf(logcatMessage()))

    logcatMainPanel.messageProcessor.onIdle {
      @Suppress("ConvertLambdaToReference")
      assertThat(logcatMainPanel.editor.isCaretAtBottom()).isFalse()
    }
  }

  @RunsInEdt
  @Test
  fun installPopupHandler() {
    // In prod, splitter actions are provided by the Splitting Tabs component. In tests, we create a
    // stand-in
    val splitterActions =
      DefaultActionGroup().apply {
        add(
          object : AnAction("Splitter Action") {
            override fun actionPerformed(e: AnActionEvent) {}
          }
        )
      }
    val logcatMainPanel =
      logcatMainPanel(splitterPopupActionGroup = splitterActions).apply {
        size = Dimension(100, 500)
        editor.document.setText("foo") // put some text so 'Fold Lines Like This' is enabled
      }
    val fakeUi =
      FakeUi(logcatMainPanel, createFakeWindow = true, parentDisposable = disposableRule.disposable)

    fakeUi.rightClickOn(logcatMainPanel)

    val popupMenu = popupRule.popupContents as JPopupMenu

    // SearchWebAction does not show up in this test because its presentation depends on a
    // CopyProvider available which it isn't in the
    // test environment for some reason.
    assertThat(
        popupMenu.components.map {
          if (it is JPopupMenu.Separator) "-" else (it as ActionMenuItem).text
        }
      )
      .containsExactly(
        "Copy",
        "Search with Google",
        "Fold Lines Like This",
        "-",
        "Splitter Action",
        "-",
        "Clear Logcat",
      )
    verify(popupRule.mockPopup).show()
    // JBPopupMenu has a Timer that is stopped when made invisible. If not stopped,
    // checkJavaSwingTimersAreDisposed() will throw in some
    // other test.
    popupMenu.isVisible = false
  }

  @RunsInEdt
  @Test
  fun isMessageViewEmpty_emptyDocument() {
    val logcatMainPanel = logcatMainPanel()
    logcatMainPanel.editor.document.setText("")

    assertThat(logcatMainPanel.isLogcatEmpty()).isTrue()
  }

  @Test
  fun isMessageViewEmpty_notEmptyLogcat() = runBlocking {
    val logcatMainPanel = runInEdtAndGet {
      logcatMainPanel(hyperlinkDetector = mockHyperlinkDetector)
    }

    logcatMainPanel.processMessages(listOf(logcatMessage()))

    assertThat(logcatMainPanel.isLogcatEmpty()).isFalse()
  }

  @Test
  fun clearMessageView() {
    val logcatMainPanel = runInEdtAndGet {
      logcatMainPanel().also { it.editor.document.setText("not-empty") }
    }

    logcatMainPanel.clearMessageView()

    ConcurrencyUtil.awaitQuiescence(
      AndroidExecutors.getInstance().workerThreadExecutor as ThreadPoolExecutor,
      TIMEOUT_SEC,
      SECONDS,
    )
    runInEdtAndWait {}
    assertThat(logcatMainPanel.editor.document.immutableText().isEmpty())
    assertThat(logcatMainPanel.messageBacklog.get().messages).isEmpty()
    // TODO(aalbert): Test the 'logcat -c' functionality if new adb lib allows for it.
  }

  @Test
  fun clearMessageView_bySubscriptionToClearLogcatListener() {
    deviceTracker.addDevices(device1)
    val logcatMainPanel = runInEdtAndGet {
      logcatMainPanel().also {
        waitForCondition { it.getConnectedDevice() != null }
        it.editor.document.setText("not-empty")
      }
    }

    project.messageBus.syncPublisher(ClearLogcatListener.TOPIC).clearLogcat(device1.serialNumber)

    ConcurrencyUtil.awaitQuiescence(
      AndroidExecutors.getInstance().workerThreadExecutor as ThreadPoolExecutor,
      TIMEOUT_SEC,
      SECONDS,
    )
    runInEdtAndWait {}
    assertThat(logcatMainPanel.editor.document.immutableText().isEmpty())
  }

  @Test
  fun clearMessageView_bySubscriptionToClearLogcatListener_otherDevice() {
    deviceTracker.addDevices(device1, device2)

    val logcatMainPanel = runInEdtAndGet {
      logcatMainPanel().also {
        waitForCondition { it.getConnectedDevice() != null }
        it.editor.document.setText("not-empty")
      }
    }

    project.messageBus.syncPublisher(ClearLogcatListener.TOPIC).clearLogcat(device2.serialNumber)

    ConcurrencyUtil.awaitQuiescence(
      AndroidExecutors.getInstance().workerThreadExecutor as ThreadPoolExecutor,
      TIMEOUT_SEC,
      SECONDS,
    )
    runInEdtAndWait {}
    assertThat(logcatMainPanel.editor.document.immutableText()).isEqualTo("not-empty")
  }

  /**
   * The purpose this test is to ensure that we are calling the HyperlinkHighlighter with the
   * correct line range. It does not test user on any visible effect.
   */
  @Test
  fun hyperlinks_range() = runBlocking {
    val logcatMainPanel = runInEdtAndGet {
      logcatMainPanel(hyperlinkDetector = mockHyperlinkDetector)
    }

    logcatMainPanel.messageProcessor.appendMessages(listOf(logcatMessage()))
    logcatMainPanel.messageProcessor.onIdle {}
    logcatMainPanel.messageProcessor.appendMessages(listOf(logcatMessage()))

    logcatMainPanel.messageProcessor.onIdle {
      verify(mockHyperlinkDetector).detectHyperlinks(eq(0), eq(1), any())
      verify(mockHyperlinkDetector).detectHyperlinks(eq(1), eq(2), any())
    }
  }

  /**
   * The purpose this test is to ensure that we are calling the HyperlinkHighlighter with the
   * correct line range. It does not test user on any visible effect.
   */
  @Test
  fun hyperlinks_rangeWithCyclicBuffer() = runBlocking {
    val logcatMainPanel = runInEdtAndGet {
      logcatMainPanel(
        hyperlinkDetector = mockHyperlinkDetector,
        logcatSettings = AndroidLogcatSettings(bufferSize = 1024),
      )
    }
    val longMessage = "message".padStart(1000, '-')

    logcatMainPanel.messageProcessor.appendMessages(listOf(logcatMessage(message = longMessage)))
    logcatMainPanel.messageProcessor.onIdle {} // force flush
    logcatMainPanel.messageProcessor.appendMessages(listOf(logcatMessage(message = longMessage)))

    logcatMainPanel.messageProcessor.onIdle {
      verify(mockHyperlinkDetector, times(2)).detectHyperlinks(eq(0), eq(1), any())
    }
  }

  /**
   * The purpose this test is to ensure that we are calling the FoldingDetector with the correct
   * line range. It does not test user on any visible effect.
   */
  @Test
  fun foldings_range() = runBlocking {
    val logcatMainPanel = runInEdtAndGet { logcatMainPanel(foldingDetector = mockFoldingDetector) }

    logcatMainPanel.messageProcessor.appendMessages(listOf(logcatMessage()))
    logcatMainPanel.messageProcessor.onIdle {}
    logcatMainPanel.messageProcessor.appendMessages(listOf(logcatMessage()))

    logcatMainPanel.messageProcessor.onIdle {
      verify(mockFoldingDetector).detectFoldings(eq(0), eq(1))
      verify(mockFoldingDetector).detectFoldings(eq(1), eq(2))
    }
  }

  /**
   * The purpose this test is to ensure that we are calling the FoldingDetector with the correct
   * line range. It does not test user on any visible effect.
   */
  @Test
  fun foldings_rangeWithCyclicBuffer() = runBlocking {
    val logcatMainPanel = runInEdtAndGet {
      logcatMainPanel(
        foldingDetector = mockFoldingDetector,
        logcatSettings = AndroidLogcatSettings(bufferSize = 1024),
      )
    }
    val longMessage = "message".padStart(1000, '-')

    logcatMainPanel.messageProcessor.appendMessages(listOf(logcatMessage(message = longMessage)))
    logcatMainPanel.messageProcessor.onIdle {} // force flush
    logcatMainPanel.messageProcessor.appendMessages(listOf(logcatMessage(message = longMessage)))

    logcatMainPanel.messageProcessor.onIdle {
      verify(mockFoldingDetector, times(2)).detectFoldings(eq(0), eq(1))
    }
  }

  @RunsInEdt
  @Test
  fun getState() {
    val logcatMainPanel = logcatMainPanel()
    logcatMainPanel.formattingOptions = FormattingOptions(tagFormat = TagFormat(15))

    val logcatPanelConfig = LogcatPanelConfig.fromJson(logcatMainPanel.getState())
    assertThat(logcatPanelConfig!!.formattingConfig.toFormattingOptions().tagFormat.maxLength)
      .isEqualTo(15)
  }

  @RunsInEdt
  @Test
  fun appliesState() {
    val logcatMainPanel =
      logcatMainPanel(
        state =
          LogcatPanelConfig(
            device = null,
            file = null,
            FormattingConfig.Custom(FormattingOptions(tagFormat = TagFormat(17))),
            filter = "foo",
            filterMatchCase = true,
            isSoftWrap = true,
          )
      )

    // TODO(aalbert) : Also assert on device field when the combo is rewritten to allow testing.
    assertThat(logcatMainPanel.formattingOptions.tagFormat.maxLength).isEqualTo(17)
    assertThat(logcatMainPanel.messageProcessor.logcatFilter)
      .isEqualTo(StringFilter("foo", IMPLICIT_LINE, matchCase = true, TextRange(0, "foo".length)))
    assertThat(logcatMainPanel.headerPanel.filter).isEqualTo("foo")
    assertThat(logcatMainPanel.headerPanel.filterMatchCase).isTrue()
    assertThat(logcatMainPanel.isSoftWrapEnabled()).isTrue()
  }

  @RunsInEdt
  @Test
  fun appliesState_noState() {
    val logcatMainPanel = logcatMainPanel(state = null)

    assertThat(logcatMainPanel.formattingOptions).isEqualTo(FormattingOptions())
    assertThat(logcatMainPanel.messageProcessor.logcatFilter)
      .isInstanceOf(ProjectAppFilter::class.java)
    assertThat(logcatMainPanel.headerPanel.filter).isEqualTo("package:mine ")
    assertThat(logcatMainPanel.editor.settings.isUseSoftWraps).isFalse()
  }

  @RunsInEdt
  @Test
  fun defaultFilter() {
    AndroidLogcatSettings.getInstance().defaultFilter = "foo"

    val logcatMainPanel = logcatMainPanel(state = null)

    assertThat(logcatMainPanel.headerPanel.filter).isEqualTo("foo")
  }

  @RunsInEdt
  @Test
  fun defaultFilter_mostRecentlyUsed() {
    val androidLogcatSettings = AndroidLogcatSettings.getInstance()
    androidLogcatSettings.defaultFilter = "foo"
    androidLogcatSettings.mostRecentlyUsedFilterIsDefault = true
    AndroidLogcatFilterHistory.getInstance().mostRecentlyUsed = "bar"

    val logcatMainPanel = logcatMainPanel(state = null)

    assertThat(logcatMainPanel.headerPanel.filter).isEqualTo("bar")
  }

  @RunsInEdt
  @Test
  fun appliesState_noState_nonAndroidProject() {
    val logcatMainPanel =
      logcatMainPanel(state = null, androidProjectDetector = FakeAndroidProjectDetector(false))

    assertThat(logcatMainPanel.messageProcessor.logcatFilter).isNull()
    assertThat(logcatMainPanel.headerPanel.filter).isEqualTo("")
  }

  @Test
  fun reloadMessages() {
    val logcatMainPanel = runInEdtAndGet {
      logcatMainPanel(zoneId = ZoneId.of("Asia/Yerevan")).also {
        it.editor.document.setText("Some previous text")
      }
    }
    logcatMainPanel.messageBacklog
      .get()
      .addAll(listOf(logcatMessage(message = "message", timestamp = Instant.ofEpochSecond(10))))

    runInEdtAndWait(logcatMainPanel::reloadMessages)

    ConcurrencyUtil.awaitQuiescence(
      AndroidExecutors.getInstance().workerThreadExecutor as ThreadPoolExecutor,
      TIMEOUT_SEC,
      SECONDS,
    )

    logcatMainPanel.messageProcessor.onIdle {
      assertThat(logcatMainPanel.editor.document.immutableText())
        .isEqualTo(
          "1970-01-01 04:00:10.000     1-2     ExampleTag              com.example.app                      I  message\n"
        )
    }
  }

  @Test
  fun processMessage_addsUpdatesBacklog(): Unit = runBlocking {
    val logcatMainPanel = runInEdtAndGet(this@LogcatMainPanelTest::logcatMainPanel)
    val messages =
      listOf(
        LogcatMessage(
          LogcatHeader(WARN, 1, 2, "app1", "", "tag1", Instant.ofEpochMilli(1000)),
          "message1",
        ),
        LogcatMessage(
          LogcatHeader(INFO, 1, 2, "app2", "", "tag2", Instant.ofEpochMilli(1000)),
          "message2",
        ),
      )

    logcatMainPanel.processMessages(messages)

    assertThat(logcatMainPanel.messageBacklog.get().messages).containsExactlyElementsIn(messages)
    logcatMainPanel.messageProcessor.onIdle {
      assertThat(logcatMainPanel.editor.document.immutableText())
        .isEqualTo(
          """
        1970-01-01 04:00:01.000     1-2     tag1                    app1                                 W  message1
        1970-01-01 04:00:01.000     1-2     tag2                    app2                                 I  message2
        
      """
            .trimIndent()
        )
    }
  }

  @Test
  fun connectDevice_readLogcat() = runBlocking {
    val message1 =
      LogcatMessage(
        LogcatHeader(WARN, 1, 2, "app1", "", "tag1", Instant.ofEpochMilli(1000)),
        "message1",
      )
    deviceTracker.addDevices(device1)
    val logcatMainPanel = runInEdtAndGet {
      logcatMainPanel().also { waitForCondition { it.getConnectedDevice() != null } }
    }

    fakeLogcatService.logMessages(message1)

    logcatMainPanel.editor.document.waitForCondition(logcatMainPanel) {
      immutableText().isNotEmpty()
    }
    assertThat(logcatMainPanel.editor.document.immutableText())
      .isEqualTo(
        "1970-01-01 04:00:01.000     1-2     tag1                    app1                                 W  message1\n"
      )
  }

  @Test
  fun pauseLogcat_jobCanceled() = runBlocking {
    deviceTracker.addDevices(device1)
    val logcatMainPanel = runInEdtAndGet {
      logcatMainPanel().also {
        waitForCondition { it.getConnectedDevice() != null && it.logcatServiceJob != null }
      }
    }
    // Grab logcatServiceJob now, so we can assert that it was canceled later
    val logcatServiceJob = logcatMainPanel.logcatServiceJob!!

    logcatMainPanel.pauseLogcat()
    waitForCondition { logcatMainPanel.isLogcatPaused() }

    assertThat(logcatServiceJob.isCancelled).isTrue()
    waitForCondition { logcatMainPanel.logcatServiceJob == null }
  }

  @Ignore("b/344987760")
  @Test
  fun resumeLogcat_jobResumed() = runBlocking {
    val message1 =
      LogcatMessage(
        LogcatHeader(WARN, 1, 2, "app1", "", "tag1", Instant.ofEpochMilli(1000)),
        "message1",
      )
    deviceTracker.addDevices(device1)
    val logcatMainPanel = runInEdtAndGet {
      logcatMainPanel().also {
        waitForCondition { it.getConnectedDevice() != null && it.logcatServiceJob != null }
      }
    }
    logcatMainPanel.pauseLogcat()
    waitForCondition { logcatMainPanel.logcatServiceJob == null }

    logcatMainPanel.resumeLogcat()
    waitForCondition { fakeLogcatService.invocations.get() == 2 }
    fakeLogcatService.logMessages(message1)

    waitForCondition { logcatMainPanel.editor.document.lineCount == 2 }
    logcatMainPanel.messageProcessor.onIdle {
      assertThat(logcatMainPanel.editor.document.immutableText())
        .isEqualTo(
          """
        1970-01-01 04:00:01.000     1-2     tag1                    app1                                 W  message1

      """
            .trimIndent()
        )
    }
  }

  @Test
  fun processMessage_updatesTags(): Unit = runBlocking {
    val logcatMainPanel = runInEdtAndGet(this@LogcatMainPanelTest::logcatMainPanel)
    val messages =
      listOf(
        LogcatMessage(
          LogcatHeader(WARN, 1, 2, "app1", "", "tag1", Instant.ofEpochMilli(1000)),
          "message1",
        ),
        LogcatMessage(
          LogcatHeader(INFO, 1, 2, "app2", "", "tag2", Instant.ofEpochMilli(1000)),
          "message2",
        ),
      )

    logcatMainPanel.processMessages(messages)

    assertThat(logcatMainPanel.getTags()).containsExactly("tag1", "tag2")
  }

  @Test
  fun processMessage_updatesPackages(): Unit = runBlocking {
    val logcatMainPanel = runInEdtAndGet(this@LogcatMainPanelTest::logcatMainPanel)
    val messages =
      listOf(
        LogcatMessage(
          LogcatHeader(WARN, 1, 2, "app1", "", "tag1", Instant.ofEpochMilli(1000)),
          "message1",
        ),
        LogcatMessage(
          LogcatHeader(INFO, 1, 2, "app2", "", "tag2", Instant.ofEpochMilli(1000)),
          "message2",
        ),
      )

    logcatMainPanel.processMessages(messages)

    assertThat(logcatMainPanel.getPackageNames()).containsExactly("app1", "app2")
  }

  @Test
  fun applyLogcatSettings_bufferSize() = runBlocking {
    val logcatMainPanel = runInEdtAndGet {
      logcatMainPanel(logcatSettings = AndroidLogcatSettings(bufferSize = 1024000))
    }
    val document = logcatMainPanel.editor.document as DocumentImpl
    val logcatMessage =
      logcatMessage(
        message = "foo".padStart(97, ' ')
      ) // Make the message part exactly 100 chars long
    // Insert 20 log lines
    logcatMainPanel.processMessages(List(20) { logcatMessage })
    val logcatSettings = AndroidLogcatSettings(bufferSize = 1024)

    logcatMainPanel.applyLogcatSettings(logcatSettings)

    logcatMainPanel.messageProcessor.onIdle {
      assertThat(document.immutableText().length).isAtMost(1024 + logcatMessage.length())
      // backlog trims by message length
      assertThat(logcatMainPanel.messageBacklog.get().messages.sumOf { it.message.length })
        .isLessThan(1024)
    }
  }

  @Test
  fun setFormattingOptions_reloadsMessages() = runBlocking {
    val logcatMainPanel = runInEdtAndGet(this@LogcatMainPanelTest::logcatMainPanel)
    logcatMainPanel.processMessages(
      listOf(
        LogcatMessage(
          LogcatHeader(WARN, 1, 2, "app1", "", "tag1", Instant.ofEpochMilli(1000)),
          "message1",
        )
      )
    )

    logcatMainPanel.messageProcessor.onIdle {
      logcatMainPanel.formattingOptions = COMPACT.formattingOptions
    }

    ConcurrencyUtil.awaitQuiescence(
      AndroidExecutors.getInstance().workerThreadExecutor as ThreadPoolExecutor,
      TIMEOUT_SEC,
      SECONDS,
    )
    logcatMainPanel.messageProcessor.onIdle {
      assertThat(logcatMainPanel.editor.document.immutableText().trim())
        .isEqualTo("04:00:01.000  W  message1")
    }
  }

  @RunsInEdt
  @Test
  fun usageTracking_noState_standard() {
    logcatMainPanel(state = null, logcatSettings = AndroidLogcatSettings(bufferSize = 1000))

    assertThat(usageTrackerRule.logcatEvents())
      .containsExactly(
        LogcatUsageEvent.newBuilder()
          .setType(PANEL_ADDED)
          .setPanelAdded(
            LogcatPanelEvent.newBuilder()
              .setIsRestored(false)
              .setFormatConfiguration(
                LogcatFormatConfiguration.newBuilder()
                  .setPreset(Preset.STANDARD)
                  .setIsShowTimestamp(true)
                  .setIsShowDate(true)
                  .setIsShowProcessId(true)
                  .setIsShowThreadId(true)
                  .setIsShowTags(true)
                  .setIsShowRepeatedTags(true)
                  .setTagWidth(23)
                  .setIsShowPackages(true)
                  .setIsShowRepeatedPackages(true)
                  .setPackageWidth(35)
              )
              .setFilter(LogcatFilterEvent.newBuilder().setPackageProjectTerms(1))
              .setBufferSize(1000)
          )
          .build()
      )
  }

  @RunsInEdt
  @Test
  fun usageTracking_noState_compact() {
    androidLogcatFormattingOptions.defaultFormatting = COMPACT
    logcatMainPanel(state = null, logcatSettings = AndroidLogcatSettings(bufferSize = 1000))

    assertThat(usageTrackerRule.logcatEvents())
      .containsExactly(
        LogcatUsageEvent.newBuilder()
          .setType(PANEL_ADDED)
          .setPanelAdded(
            LogcatPanelEvent.newBuilder()
              .setIsRestored(false)
              .setFormatConfiguration(
                LogcatFormatConfiguration.newBuilder()
                  .setPreset(Preset.COMPACT)
                  .setIsShowTimestamp(true)
                  .setIsShowDate(false)
                  .setIsShowProcessId(false)
                  .setIsShowThreadId(true)
                  .setIsShowTags(false)
                  .setIsShowRepeatedTags(true)
                  .setTagWidth(23)
                  .setIsShowPackages(false)
                  .setIsShowRepeatedPackages(true)
                  .setPackageWidth(35)
              )
              .setFilter(LogcatFilterEvent.newBuilder().setPackageProjectTerms(1))
              .setBufferSize(1000)
          )
          .build()
      )
  }

  @RunsInEdt
  @Test
  fun usageTracking_withState_preset() {
    logcatMainPanel(
      state =
        LogcatPanelConfig(
          device = null,
          file = null,
          formattingConfig = FormattingConfig.Preset(COMPACT),
          "filter",
          filterMatchCase = true,
          isSoftWrap = false,
        ),
      logcatSettings = AndroidLogcatSettings(bufferSize = 1000),
    )

    assertThat(usageTrackerRule.logcatEvents())
      .containsExactly(
        LogcatUsageEvent.newBuilder()
          .setType(PANEL_ADDED)
          .setPanelAdded(
            LogcatPanelEvent.newBuilder()
              .setIsRestored(true)
              .setFormatConfiguration(
                LogcatFormatConfiguration.newBuilder()
                  .setPreset(Preset.COMPACT)
                  .setIsShowTimestamp(true)
                  .setIsShowDate(false)
                  .setIsShowProcessId(false)
                  .setIsShowThreadId(true)
                  .setIsShowTags(false)
                  .setIsShowRepeatedTags(true)
                  .setTagWidth(23)
                  .setIsShowPackages(false)
                  .setIsShowRepeatedPackages(true)
                  .setPackageWidth(35)
              )
              .setFilter(LogcatFilterEvent.newBuilder().setImplicitLineTerms(1))
              .setBufferSize(1000)
          )
          .build()
      )
  }

  @RunsInEdt
  @Test
  fun usageTracking_withState_custom() {
    logcatMainPanel(
      state =
        LogcatPanelConfig(
          device = null,
          file = null,
          formattingConfig =
            FormattingConfig.Custom(
              FormattingOptions(tagFormat = TagFormat(20, hideDuplicates = false, enabled = true))
            ),
          "filter",
          filterMatchCase = true,
          isSoftWrap = false,
        ),
      logcatSettings = AndroidLogcatSettings(bufferSize = 1000),
    )

    assertThat(usageTrackerRule.logcatEvents())
      .containsExactly(
        LogcatUsageEvent.newBuilder()
          .setType(PANEL_ADDED)
          .setPanelAdded(
            LogcatPanelEvent.newBuilder()
              .setIsRestored(true)
              .setFormatConfiguration(
                LogcatFormatConfiguration.newBuilder()
                  .setIsShowTimestamp(true)
                  .setIsShowDate(true)
                  .setIsShowProcessId(true)
                  .setIsShowThreadId(true)
                  .setIsShowTags(true)
                  .setIsShowRepeatedTags(true)
                  .setTagWidth(20)
                  .setIsShowPackages(true)
                  .setIsShowRepeatedPackages(true)
                  .setPackageWidth(35)
              )
              .setFilter(LogcatFilterEvent.newBuilder().setImplicitLineTerms(1))
              .setBufferSize(1000)
          )
          .build()
      )
  }

  @Test
  fun clickToSetFilter_addToEmpty() = runBlocking {
    val logcatMainPanel = runInEdtAndGet {
      logcatMainPanel().apply {
        size = Dimension(100, 100)
        headerPanel.filter = ""
      }
    }
    val fakeUi = runInEdtAndGet {
      FakeUi(logcatMainPanel.editor.contentComponent, createFakeWindow = true)
    }

    logcatMainPanel.messageProcessor.appendMessages(
      listOf(
        LogcatMessage(
          LogcatHeader(INFO, 1, 2, "app2", "", "tag2", Instant.ofEpochMilli(1000)),
          "message2",
        )
      )
    )

    logcatMainPanel.messageProcessor.onIdle {
      runInEdtAndWait {
        val offset = logcatMainPanel.editor.document.immutableText().indexOf("app2")
        val point = logcatMainPanel.editor.offsetToXY(offset)
        fakeUi.mouse.click(point.x + 1, point.y + 1, CTRL_LEFT)
        assertThat(logcatMainPanel.headerPanel.filter).isEqualTo("package:app2")
      }
    }
  }

  @Test
  fun clickToSetFilter_addToNotEmpty() = runBlocking {
    val logcatMainPanel = runInEdtAndGet { logcatMainPanel().apply { size = Dimension(100, 100) } }
    val fakeUi = runInEdtAndGet {
      FakeUi(logcatMainPanel.editor.contentComponent, createFakeWindow = true)
    }
    logcatMainPanel.processMessages(
      listOf(
        LogcatMessage(
          LogcatHeader(INFO, 1, 2, "app1", "", "tag1", Instant.ofEpochMilli(1000)),
          "foo",
        ),
        LogcatMessage(
          LogcatHeader(INFO, 1, 2, "app2", "", "tag2", Instant.ofEpochMilli(1000)),
          "bar",
        ),
      )
    )
    runInEdtAndWait { logcatMainPanel.setFilter("foo") }
    logcatMainPanel.editor.document.waitForCondition(logcatMainPanel) {
      immutableText().endsWith("foo\n")
    }
    logcatMainPanel.messageProcessor.onIdle {
      runInEdtAndWait {
        val offset = logcatMainPanel.editor.document.immutableText().indexOf("app1")
        val point = logcatMainPanel.editor.offsetToXY(offset)

        fakeUi.mouse.click(point.x + 1, point.y + 1, CTRL_LEFT)

        assertThat(logcatMainPanel.headerPanel.filter).isEqualTo("foo package:app1")
      }
    }
  }

  @Test
  fun clickToSetFilter_remove() = runBlocking {
    val logcatMainPanel = runInEdtAndGet { logcatMainPanel().apply { size = Dimension(100, 100) } }
    val fakeUi = runInEdtAndGet {
      FakeUi(logcatMainPanel.editor.contentComponent, createFakeWindow = true)
    }
    logcatMainPanel.processMessages(
      listOf(
        LogcatMessage(
          LogcatHeader(INFO, 1, 2, "app1", "", "tag1", Instant.ofEpochMilli(1000)),
          "foo",
        ),
        LogcatMessage(
          LogcatHeader(DEBUG, 1, 2, "app2", "", "tag2", Instant.ofEpochMilli(1000)),
          "bar",
        ),
      )
    )
    runInEdtAndWait { logcatMainPanel.setFilter("foo level:INFO") }
    logcatMainPanel.editor.document.waitForCondition(logcatMainPanel) {
      immutableText().endsWith("foo\n")
    }
    logcatMainPanel.messageProcessor.onIdle {
      runInEdtAndWait {
        val offset = logcatMainPanel.editor.document.immutableText().indexOf(" I ")
        val point = logcatMainPanel.editor.offsetToXY(offset)
        fakeUi.mouse.click(point.x + 1, point.y + 1, CTRL_LEFT)
        assertThat(logcatMainPanel.headerPanel.filter).isEqualTo("foo")
      }
    }
  }

  @Test
  fun clickToSetFilter_removeMultiple() = runBlocking {
    val logcatMainPanel = runInEdtAndGet { logcatMainPanel().apply { size = Dimension(100, 100) } }
    val fakeUi = runInEdtAndGet {
      FakeUi(logcatMainPanel.editor.contentComponent, createFakeWindow = true)
    }
    logcatMainPanel.processMessages(
      listOf(
        LogcatMessage(
          LogcatHeader(INFO, 1, 2, "app1", "", "tag1", Instant.ofEpochMilli(1000)),
          "foo",
        ),
        LogcatMessage(
          LogcatHeader(DEBUG, 1, 2, "app2", "", "tag2", Instant.ofEpochMilli(1000)),
          "bar",
        ),
      )
    )
    runInEdtAndWait { logcatMainPanel.setFilter(" level:INFO foo level:INFO") }
    logcatMainPanel.editor.document.waitForCondition(logcatMainPanel) {
      immutableText().endsWith("foo\n")
    }
    logcatMainPanel.messageProcessor.onIdle {
      runInEdtAndWait {
        val offset = logcatMainPanel.editor.document.immutableText().indexOf(" I ")
        val point = logcatMainPanel.editor.offsetToXY(offset)
        fakeUi.mouse.click(point.x + 1, point.y + 1, CTRL_LEFT)
        assertThat(logcatMainPanel.headerPanel.filter).isEqualTo("foo")
      }
    }
  }

  @Test
  fun clickToSetFilter_invalidFilter() = runBlocking {
    val logcatMainPanel = runInEdtAndGet {
      logcatMainPanel().apply {
        size = Dimension(100, 100)
        headerPanel.filter = "package:mine | level:error"
      }
    }
    val fakeUi = runInEdtAndGet {
      FakeUi(logcatMainPanel.editor.contentComponent, createFakeWindow = true)
    }

    logcatMainPanel.messageProcessor.appendMessages(
      listOf(
        LogcatMessage(
          LogcatHeader(LogLevel.ERROR, 1, 2, "app2", "", "tag2", Instant.ofEpochMilli(1000)),
          "message2",
        )
      )
    )

    logcatMainPanel.messageProcessor.onIdle {
      runInEdtAndWait {
        val offset = logcatMainPanel.editor.document.immutableText().indexOf(" I ")
        val point = logcatMainPanel.editor.offsetToXY(offset)
        fakeUi.mouse.click(point.x + 1, point.y + 1, CTRL_LEFT)
        assertThat(logcatMainPanel.headerPanel.filter).isEqualTo("package:mine | level:error")
      }
    }
  }

  @Test
  fun pauseLogcat_showsBanner(): Unit = runBlocking {
    val logcatMainPanel = runInEdtAndGet(this@LogcatMainPanelTest::logcatMainPanel)

    logcatMainPanel.pauseLogcat()

    assertThat(logcatMainPanel.findBanner("Logcat is paused").isVisible).isTrue()
  }

  @Test
  fun resumeLogcat_hidesBanner(): Unit = runBlocking {
    val logcatMainPanel = runInEdtAndGet(this@LogcatMainPanelTest::logcatMainPanel)
    logcatMainPanel.pauseLogcat()

    logcatMainPanel.resumeLogcat()

    assertThat(logcatMainPanel.findBanner("Logcat is paused").isVisible).isFalse()
  }

  @Test
  fun restartLogcat_hidesBanner(): Unit = runBlocking {
    val logcatMainPanel = runInEdtAndGet(this@LogcatMainPanelTest::logcatMainPanel)
    logcatMainPanel.pauseLogcat()

    logcatMainPanel.restartLogcat()

    assertThat(logcatMainPanel.findBanner("Logcat is paused").isVisible).isFalse()
  }

  @Test
  fun switchDevice_hidesBanner(): Unit = runBlocking {
    deviceTracker.addDevices(device1, device2)
    val logcatMainPanel = runInEdtAndGet {
      logcatMainPanel().also { waitForCondition { it.getConnectedDevice() == device1 } }
    }
    val deviceComboBox = logcatMainPanel.headerPanel.deviceComboBox
    logcatMainPanel.pauseLogcat()

    waitForCondition { deviceComboBox.model.size > 0 }
    deviceComboBox.selectedIndex = 1
    waitForCondition { logcatMainPanel.getSelectedDevice() == device2 }

    val findBanner = logcatMainPanel.findBanner("Logcat is paused")
    waitForCondition { !findBanner.isVisible }
  }

  @Test
  fun missingApplicationIds_showsBanner(): Unit = runBlocking {
    val fakePackageNamesProvider = FakeProjectApplicationIdsProvider(project)
    val logcatMainPanel = runInEdtAndGet {
      logcatMainPanel(
        projectApplicationIdsProvider = fakePackageNamesProvider,
        filter = "package:mine",
      )
    }

    assertThat(
        logcatMainPanel
          .findBanner("Could not detect project package names. Is the project synced?")
          .isVisible
      )
      .isTrue()
  }

  @Test
  fun hasApplicationIds_doesNotShowBanner(): Unit = runBlocking {
    val fakePackageNamesProvider = FakeProjectApplicationIdsProvider(project, "app1")
    val logcatMainPanel = runInEdtAndGet {
      logcatMainPanel(
        projectApplicationIdsProvider = fakePackageNamesProvider,
        filter = "package:mine",
      )
    }

    assertThat(
        logcatMainPanel
          .findBanner("Could not detect project package names. Is the project synced?")
          .isVisible
      )
      .isFalse()
  }

  @Test
  fun applicationIdsChange_bannerUpdates(): Unit = runBlocking {
    val fakePackageNamesProvider = FakeProjectApplicationIdsProvider(project)
    val logcatMainPanel = runInEdtAndGet {
      logcatMainPanel(
        projectApplicationIdsProvider = fakePackageNamesProvider,
        filter = "package:mine",
      )
    }
    val banner =
      logcatMainPanel.findBanner("Could not detect project package names. Is the project synced?")

    assertThat(banner.isVisible).isTrue()

    runInEdtAndWait { fakePackageNamesProvider.setApplicationIds("app1") }
    assertThat(banner.isVisible).isFalse()

    runInEdtAndWait { fakePackageNamesProvider.setApplicationIds() }
    assertThat(banner.isVisible).isTrue()
  }

  @Test
  fun noLogsBanner_appearsAndDisappearsWhenAddingLogs(): Unit = runBlocking {
    val logcatMainPanel = runInEdtAndGet(::logcatMainPanel)
    val noLogsBanner = logcatMainPanel.findBanner("All logs entries are hidden by the filter")
    logcatMainPanel.processMessages(
      listOf(
        LogcatMessage(
          LogcatHeader(WARN, 1, 2, "app1", "", "tag1", Instant.ofEpochMilli(1000)),
          "message1",
        )
      )
    )
    logcatMainPanel.editor.document.waitForCondition(logcatMainPanel) {
      immutableText().trim() ==
        """
        1970-01-01 04:00:01.000     1-2     tag1                    app1                                 W  message1
      """
          .trimIndent()
    }
    runInEdtAndWait { logcatMainPanel.setFilter("no-match") }
    waitForCondition { noLogsBanner.isVisible }
    logcatMainPanel.processMessages(
      listOf(
        LogcatMessage(
          LogcatHeader(WARN, 1, 2, "app1", "", "tag1", Instant.ofEpochMilli(1000)),
          "no-match",
        )
      )
    )
    waitForCondition { !noLogsBanner.isVisible }
  }

  @Test
  fun noLogsBanner_appearsAndDisappearsWhenChangingFilter(): Unit = runBlocking {
    val logcatMainPanel = runInEdtAndGet(::logcatMainPanel)
    val noLogsBanner = logcatMainPanel.findBanner("All logs entries are hidden by the filter")
    logcatMainPanel.processMessages(
      listOf(
        LogcatMessage(
          LogcatHeader(WARN, 1, 2, "app1", "", "tag1", Instant.ofEpochMilli(1000)),
          "message1",
        )
      )
    )
    logcatMainPanel.editor.document.waitForCondition(logcatMainPanel) {
      immutableText().trim() ==
        """
        1970-01-01 04:00:01.000     1-2     tag1                    app1                                 W  message1
      """
          .trimIndent()
    }
    runInEdtAndWait { logcatMainPanel.setFilter("no-match") }
    waitForCondition { noLogsBanner.isVisible }
    runInEdtAndWait { logcatMainPanel.setFilter("tag:tag1") }
    waitForCondition { !noLogsBanner.isVisible }
  }

  @Test
  fun noLogsBanner_doesNotShowIfNoMessagesExist(): Unit = runBlocking {
    val logcatMainPanel = runInEdtAndGet(::logcatMainPanel)
    val noLogsBanner = logcatMainPanel.findBanner("All logs entries are hidden by the filter")
    logcatMainPanel.processMessages(
      listOf(
        LogcatMessage(
          LogcatHeader(WARN, 1, 2, "app1", "", "tag1", Instant.ofEpochMilli(1000)),
          "message1",
        )
      )
    )
    logcatMainPanel.editor.document.waitForCondition(logcatMainPanel) {
      immutableText().trim() ==
        """
        1970-01-01 04:00:01.000     1-2     tag1                    app1                                 W  message1
      """
          .trimIndent()
    }
    runInEdtAndWait { logcatMainPanel.setFilter("no-match") }
    waitForCondition { noLogsBanner.isVisible }
    runInEdtAndWait { logcatMainPanel.clearMessageView() }
    waitForCondition { !noLogsBanner.isVisible }
  }

  @Test
  fun projectAppMonitorInstalled() {
    deviceTracker.addDevices(device1)
    val fakePackageNamesProvider = FakeProjectApplicationIdsProvider(project, "myapp")
    val logcatMainPanel = runInEdtAndGet {
      logcatMainPanel(projectApplicationIdsProvider = fakePackageNamesProvider).also {
        waitForCondition { it.getConnectedDevice() != null }
      }
    }

    runBlocking {
      fakeProcessNameMonitor
        .getProcessTracker(device1.serialNumber)
        .send(ProcessAdded(0, null, "myapp"))
    }
    logcatMainPanel.editor.document.waitForCondition(logcatMainPanel) {
      immutableText().contains("PROCESS STARTED (0) for package myapp")
    }
  }

  @Test
  fun projectApplicationIdsChange_withPackageMine_reloadsMessages() = runBlocking {
    val fakeProjectApplicationIdsProvider = FakeProjectApplicationIdsProvider(project)
    val logcatMainPanel = runInEdtAndGet {
      logcatMainPanel(projectApplicationIdsProvider = fakeProjectApplicationIdsProvider)
    }
    logcatMainPanel.processMessages(
      listOf(
        LogcatMessage(
          LogcatHeader(WARN, 1, 2, "app1", "", "tag1", Instant.ofEpochMilli(1000)),
          "message1",
        ),
        LogcatMessage(
          LogcatHeader(WARN, 1, 2, "app2", "", "tag2", Instant.ofEpochMilli(1000)),
          "message2",
        ),
      )
    )
    runInEdtAndWait { logcatMainPanel.setFilter("package:mine | tag:tag2") }
    logcatMainPanel.editor.document.waitForCondition(logcatMainPanel) {
      immutableText().trim() ==
        """
        1970-01-01 04:00:01.000     1-2     tag2                    app2                                 W  message2
      """
          .trimIndent()
    }

    runInEdtAndWait { fakeProjectApplicationIdsProvider.setApplicationIds("app1") }

    ConcurrencyUtil.awaitQuiescence(
      AndroidExecutors.getInstance().workerThreadExecutor as ThreadPoolExecutor,
      TIMEOUT_SEC,
      SECONDS,
    )
    logcatMainPanel.messageProcessor.onIdle {
      assertThat(logcatMainPanel.editor.document.immutableText().trim())
        .isEqualTo(
          """
        1970-01-01 04:00:01.000     1-2     tag1                    app1                                 W  message1
        1970-01-01 04:00:01.000     1-2     tag2                    app2                                 W  message2
      """
            .trimIndent()
        )
    }
  }

  @RunsInEdt
  @Test
  fun installsUserInputHandlers() {
    val logcatMainPanel = logcatMainPanel()

    // We don't even need to check that SpecialCharHandler was installed twice etc.
    // We just want proof that UserInputHandlers.install() was called on
    // logcatMainPanel.editor.contentComponent.
    val actions =
      ClientProperty.get(logcatMainPanel.editor.contentComponent, ACTIONS_KEY)?.map {
        it::class.simpleName
      }
    assertThat(actions)
      .containsAllOf("DeleteBackspaceHandler", "SpecialCharHandler", "PasteHandler")
  }

  @RunsInEdt
  @Test
  fun countFilterMatches_excludesSystemMessages() {
    val logcatMainPanel = logcatMainPanel()
    val messageBacklog = logcatMainPanel.messageBacklog.get()
    val messages = messageBacklog.messages
    val filter = StringFilter("Foo", TAG, matchCase = true, EMPTY_RANGE)
    messageBacklog.addAll(listOf(logcatMessage(tag = "Foo"), LogcatMessage(SYSTEM_HEADER, "")))

    assertThat(LogcatMasterFilter(filter).filter(messages)).hasSize(2)
    assertThat(logcatMainPanel.countFilterMatches(filter)).isEqualTo(1)
  }

  private fun logcatMainPanel(
    splitterPopupActionGroup: ActionGroup = EMPTY_GROUP,
    logcatColors: LogcatColors = LogcatColors(),
    filter: String = "",
    state: LogcatPanelConfig? =
      LogcatPanelConfig(
        device = null,
        file = null,
        formattingConfig = FormattingConfig.Preset(STANDARD),
        filter = filter,
        filterMatchCase = false,
        isSoftWrap = false,
      ),
    logcatSettings: AndroidLogcatSettings = AndroidLogcatSettings(),
    androidProjectDetector: AndroidProjectDetector = FakeAndroidProjectDetector(true),
    hyperlinkDetector: HyperlinkDetector? = null,
    foldingDetector: FoldingDetector? = null,
    projectApplicationIdsProvider: ProjectApplicationIdsProvider =
      FakeProjectApplicationIdsProvider(project),
    zoneId: ZoneId = ZoneId.of("Asia/Yerevan"),
  ): LogcatMainPanel {
    project.replaceService(
      ProjectApplicationIdsProvider::class.java,
      projectApplicationIdsProvider,
      disposableRule.disposable,
    )
    return LogcatMainPanel(
        project,
        splitterPopupActionGroup,
        logcatColors,
        state,
        logcatSettings,
        androidProjectDetector,
        hyperlinkDetector,
        foldingDetector,
        zoneId,
      )
      .also { Disposer.register(disposable) { runInEdtAndWait { Disposer.dispose(it) } } }
  }
}

private fun LogcatMessage.length() = FormattingOptions().getHeaderWidth() + message.length

private fun List<AnAction>.mapToStrings(indent: String = ""): List<String> {
  return flatMap {
      when (it) {
        is Separator -> listOf("-")
        is PopupActionGroupAction ->
          listOf(it.templateText) + it.getPopupActions().mapToStrings("$indent  ")
        else -> listOf(it.templateText ?: "null")
      }
    }
    .map { "$indent$it" }
}

private fun LogcatMainPanel.findBanner(text: String) =
  TreeWalker(this).descendants().first { it is EditorNotificationPanel && it.text == text }
    as EditorNotificationPanel

private fun Document.immutableText() = immutableCharSequence.toString()

// Attempting to fix b/241939879. Wait for a document to satisfy a condition. If it fails, print
// some state information.
private fun Document.waitForCondition(
  logcatMainPanel: LogcatMainPanel,
  condition: Document.() -> Boolean,
) {
  try {
    waitForCondition(TIMEOUT_SEC, SECONDS) { this.condition() }
  } catch (e: TimeoutException) {
    fun List<LogcatMessage>.toLog() = joinToString("\n").prependIndent("    ")
    val backlog = logcatMainPanel.messageBacklog.get()
    val filter = logcatMainPanel.messageProcessor.logcatFilter

    LOGGER.debug("Document.waitForCondition() failed.")
    LOGGER.debug("Document text:\n${text.trim().prependIndent("    ")}")
    LOGGER.debug("Message backlog:\n${backlog.messages.toLog()}")
    LOGGER.debug("Filter: $filter")
    LOGGER.debug(
      "Filtered messages:\n${LogcatMasterFilter(filter).filter(backlog.messages).toLog()}"
    )
    throw e
  }
}
