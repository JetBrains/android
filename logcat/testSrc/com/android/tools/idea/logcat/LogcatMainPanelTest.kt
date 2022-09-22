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

import com.android.adblib.AdbSession
import com.android.adblib.DeviceState
import com.android.adblib.testing.FakeAdbSession
import com.android.ddmlib.AndroidDebugBridge
import com.android.ddmlib.Client
import com.android.ddmlib.ClientData
import com.android.ddmlib.IDevice
import com.android.ddmlib.IDevice.CHANGE_CLIENT_LIST
import com.android.testutils.MockitoKt.eq
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.swing.FakeMouse.Button.CTRL_LEFT
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.popup.PopupRule
import com.android.tools.analytics.UsageTrackerRule
import com.android.tools.idea.FakeAndroidProjectDetector
import com.android.tools.idea.concurrency.AndroidExecutors
import com.android.tools.idea.concurrency.waitForCondition
import com.android.tools.idea.logcat.LogcatPanelConfig.FormattingConfig
import com.android.tools.idea.logcat.actions.PopupActionGroupAction
import com.android.tools.idea.logcat.devices.Device
import com.android.tools.idea.logcat.filters.AndroidLogcatFilterHistory
import com.android.tools.idea.logcat.filters.LogcatFilterField.IMPLICIT_LINE
import com.android.tools.idea.logcat.filters.LogcatFilterField.LINE
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
import com.android.tools.idea.logcat.testing.TestDevice
import com.android.tools.idea.logcat.testing.setDevices
import com.android.tools.idea.logcat.testing.setupCommandsForDevice
import com.android.tools.idea.logcat.util.AndroidProjectDetector
import com.android.tools.idea.logcat.util.LogcatFilterLanguageRule
import com.android.tools.idea.logcat.util.isCaretAtBottom
import com.android.tools.idea.logcat.util.logcatEvents
import com.android.tools.idea.run.ClearLogcatListener
import com.android.tools.idea.testing.AndroidExecutorsRule
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
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.actionSystem.impl.ActionMenuItem
import com.intellij.openapi.application.ApplicationManager
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
import com.intellij.tools.SimpleActionGroup
import com.intellij.ui.EditorNotificationPanel
import com.intellij.util.ConcurrencyUtil
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
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
import javax.swing.JPanel
import javax.swing.JPopupMenu

/**
 * Tests for [LogcatMainPanel]
 */
class LogcatMainPanelTest {
  private val projectRule = ProjectRule()
  private val executor = Executors.newCachedThreadPool()
  private val popupRule = PopupRule()
  private val androidExecutorsRule = AndroidExecutorsRule(workerThreadExecutor = executor)
  private val usageTrackerRule = UsageTrackerRule()
  private val disposableRule = DisposableRule()

  @get:Rule
  val rule =
    RuleChain(projectRule, EdtRule(), androidExecutorsRule, popupRule, LogcatFilterLanguageRule(), usageTrackerRule, disposableRule)

  private val mockHyperlinkDetector = mock<HyperlinkDetector>()
  private val mockFoldingDetector = mock<FoldingDetector>()
  private val fakeAdbSession = FakeAdbSession()
  private val androidLogcatFormattingOptions = AndroidLogcatFormattingOptions()
  private val project get() = projectRule.project

  @Before
  fun setUp() {
    ApplicationManager.getApplication().replaceService(
      AndroidLogcatFormattingOptions::class.java,
      androidLogcatFormattingOptions,
      disposableRule.disposable)
  }

  @RunsInEdt
  @Test
  fun createsComponents() {
    // In prod, splitter actions are provided by the Splitting Tabs component. In tests, we create a stand-in
    val splitterActions = SimpleActionGroup().apply {
      add(object : AnAction("Splitter Action") {
        override fun actionPerformed(e: AnActionEvent) {}
      })
    }

    val logcatMainPanel = logcatMainPanel(splitterPopupActionGroup = splitterActions)

    val borderLayout = logcatMainPanel.layout as BorderLayout
    assertThat(logcatMainPanel.componentCount).isEqualTo(3)
    assertThat(borderLayout.getLayoutComponent(NORTH)).isInstanceOf(LogcatHeaderPanel::class.java)
    val centerComponent: JPanel = borderLayout.getLayoutComponent(CENTER) as JPanel
    assertThat(logcatMainPanel.findBanner("Logcat is paused").isVisible).isFalse()
    assertThat(logcatMainPanel.findBanner("Could not detect project package names. Is the project synced?").isVisible).isFalse()
    assertThat(centerComponent.components.find { it === logcatMainPanel.editor.component }).isNotNull()
    assertThat(borderLayout.getLayoutComponent(WEST)).isInstanceOf(ActionToolbar::class.java)
    val toolbar = borderLayout.getLayoutComponent(WEST) as ActionToolbar
    assertThat(toolbar.actions.mapToStrings()).containsExactly(
      "Clear Logcat",
      "Pause Logcat",
      "Restart Logcat",
      "Scroll to the End (clicking on a particular line stops scrolling and keeps that line visible)",
      "Previous Occurrence",
      "Next Occurrence",
      "Soft-Wrap",
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
    ).inOrder()
    toolbar.actions.forEach {
      assertThat(it).isInstanceOf(DumbAware::class.java)
    }
  }

  @Test
  fun setsDocumentCyclicBuffer() = runBlocking {
    val logcatMainPanel = runInEdtAndGet { logcatMainPanel(logcatSettings = AndroidLogcatSettings(bufferSize = 1024)) }
    val document = logcatMainPanel.editor.document as DocumentImpl
    val logcatMessage = logcatMessage()

    // Insert 20 log lines
    logcatMainPanel.messageProcessor.appendMessages(List(20) { logcatMessage })
    logcatMainPanel.messageProcessor.onIdle {
      assertThat(document.text.length).isAtMost(1024 + logcatMessage.length())
    }
  }

  /**
   * This test can't run in the EDT because it depends on coroutines that are launched in the UI Thread and need to be able to wait for them
   * to complete. If it runs in the EDT, it cannot wait for these tasks to execute.
   */
  @Test
  fun appendMessages() = runBlocking {
    val logcatMainPanel = runInEdtAndGet {
      logcatMainPanel(zoneId = ZoneId.of("Asia/Yerevan"))
    }

    logcatMainPanel.messageProcessor.appendMessages(listOf(
      LogcatMessage(LogcatHeader(WARN, 1, 2, "app1", "", "tag1", Instant.ofEpochMilli(1000)), "message1"),
      LogcatMessage(LogcatHeader(INFO, 1, 2, "app2", "", "tag2", Instant.ofEpochMilli(1000)), "message2"),
    ))

    logcatMainPanel.messageProcessor.onIdle {
      assertThat(logcatMainPanel.editor.document.text).isEqualTo("""
        1970-01-01 04:00:01.000     1-2     tag1                    app1                                 W  message1
        1970-01-01 04:00:01.000     1-2     tag2                    app2                                 I  message2

      """.trimIndent())
    }
  }

  @Test
  fun applyFilter() = runBlocking {
    val logcatMainPanel = runInEdtAndGet(this@LogcatMainPanelTest::logcatMainPanel)
    logcatMainPanel.processMessages(listOf(
      LogcatMessage(LogcatHeader(WARN, 1, 2, "app1", "", "tag1", Instant.ofEpochMilli(1000)), "message1"),
      LogcatMessage(LogcatHeader(INFO, 1, 2, "app2", "", "tag2", Instant.ofEpochMilli(1000)), "message2"),
    ))

    logcatMainPanel.messageProcessor.onIdle {
      logcatMainPanel.applyFilter(StringFilter("tag1", LINE, EMPTY_RANGE))
    }

    ConcurrencyUtil.awaitQuiescence(AndroidExecutors.getInstance().workerThreadExecutor as ThreadPoolExecutor, 5, SECONDS)
    logcatMainPanel.messageProcessor.onIdle {
      assertThat(logcatMainPanel.editor.document.text).isEqualTo("""
        1970-01-01 04:00:01.000     1-2     tag1                    app1                                 W  message1

      """.trimIndent())
    }
  }

  @Test
  fun appendMessages_disposedEditor(): Unit = runBlocking {
    val logcatMainPanel = runInEdtAndGet {
      logcatMainPanel().also {
        Disposer.dispose(it)
      }
    }

    logcatMainPanel.messageProcessor.appendMessages(listOf(logcatMessage()))
  }

  @Test
  fun appendMessages_scrollToEnd() = runBlocking {
    val logcatMainPanel = runInEdtAndGet(this@LogcatMainPanelTest::logcatMainPanel)

    logcatMainPanel.messageProcessor.appendMessages(listOf(
      logcatMessage(),
      logcatMessage(),
    ))

    logcatMainPanel.messageProcessor.onIdle {
      @Suppress("ConvertLambdaToReference")
      assertThat(logcatMainPanel.editor.isCaretAtBottom()).isTrue()
    }
  }

  @Test
  fun appendMessages_notAtBottom_doesNotScrollToEnd() = runBlocking {
    val logcatMainPanel = runInEdtAndGet(this@LogcatMainPanelTest::logcatMainPanel)

    logcatMainPanel.messageProcessor.appendMessages(listOf(logcatMessage()))
    logcatMainPanel.messageProcessor.onIdle {
      logcatMainPanel.editor.caretModel.moveToOffset(0)
    }
    logcatMainPanel.messageProcessor.appendMessages(listOf(logcatMessage()))

    logcatMainPanel.messageProcessor.onIdle {
      @Suppress("ConvertLambdaToReference")
      assertThat(logcatMainPanel.editor.isCaretAtBottom()).isFalse()
    }
  }

  @RunsInEdt
  @Test
  fun installPopupHandler() {
    // In prod, splitter actions are provided by the Splitting Tabs component. In tests, we create a stand-in
    val splitterActions = SimpleActionGroup().apply {
      add(object : AnAction("Splitter Action") {
        override fun actionPerformed(e: AnActionEvent) {}
      })
    }
    val logcatMainPanel = logcatMainPanel(splitterPopupActionGroup = splitterActions).apply {
      size = Dimension(100, 100)
      editor.document.setText("foo") // put some text so 'Fold Lines Like This' is enabled
    }
    val fakeUi = FakeUi(logcatMainPanel, createFakeWindow = true)

    fakeUi.rightClickOn(logcatMainPanel)

    val popupMenu = popupRule.popupContents as JPopupMenu

    // SearchWebAction does not show up in this test because its presentation depends on a CopyProvider available which it isn't in the
    // test environment for some reason.
    assertThat(popupMenu.components.map { if (it is JPopupMenu.Separator) "-" else (it as ActionMenuItem).text }).containsExactly(
      "Copy",
      "Fold Lines Like This",
      "-",
      "Splitter Action",
      "-",
      "Clear Logcat",
    )
    verify(popupRule.mockPopup).show()
    // JBPopupMenu has a Timer that is stopped when made invisible. If not stopped, checkJavaSwingTimersAreDisposed() will throw in some
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
      logcatMainPanel().also {
        it.editor.document.setText("not-empty")
      }
    }

    logcatMainPanel.clearMessageView()

    ConcurrencyUtil.awaitQuiescence(AndroidExecutors.getInstance().workerThreadExecutor as ThreadPoolExecutor, TIMEOUT_SEC, SECONDS)
    runInEdtAndWait { }
    assertThat(logcatMainPanel.editor.document.text).isEmpty()
    assertThat(logcatMainPanel.messageBacklog.get().messages).isEmpty()
    // TODO(aalbert): Test the 'logcat -c' functionality if new adb lib allows for it.
  }

  @Test
  fun clearMessageView_bySubscriptionToClearLogcatListener() {
    val testDevice = TestDevice("device1", DeviceState.ONLINE, 11, 30, "Google", "Pixel", "")
    fakeAdbSession.deviceServices.setupCommandsForDevice(testDevice)
    fakeAdbSession.hostServices.setDevices(testDevice)
    val logcatMainPanel = runInEdtAndGet {
      logcatMainPanel(adbSession = fakeAdbSession).also {
        waitForCondition { it.getConnectedDevice() != null }
        it.editor.document.setText("not-empty")
      }
    }

    project.messageBus.syncPublisher(ClearLogcatListener.TOPIC).clearLogcat("device1")

    ConcurrencyUtil.awaitQuiescence(AndroidExecutors.getInstance().workerThreadExecutor as ThreadPoolExecutor, TIMEOUT_SEC, SECONDS)
    runInEdtAndWait { }
    assertThat(logcatMainPanel.editor.document.text).isEmpty()
  }

  @Test
  fun clearMessageView_bySubscriptionToClearLogcatListener_otherDevice() {
    val testDevice1 = TestDevice("device1", DeviceState.ONLINE, 11, 30, "Google", "Pixel", "")
    val testDevice2 = TestDevice("device2", DeviceState.ONLINE, 11, 30, "Google", "Pixel", "")
    fakeAdbSession.deviceServices.setupCommandsForDevice(testDevice1)
    fakeAdbSession.deviceServices.setupCommandsForDevice(testDevice2)
    fakeAdbSession.hostServices.setDevices(testDevice1, testDevice2)

    val logcatMainPanel = runInEdtAndGet {
      logcatMainPanel(adbSession = fakeAdbSession).also {
        waitForCondition { it.getConnectedDevice() != null }
        it.editor.document.setText("not-empty")
      }
    }

    project.messageBus.syncPublisher(ClearLogcatListener.TOPIC).clearLogcat("device2")

    ConcurrencyUtil.awaitQuiescence(AndroidExecutors.getInstance().workerThreadExecutor as ThreadPoolExecutor, TIMEOUT_SEC, SECONDS)
    runInEdtAndWait { }
    assertThat(logcatMainPanel.editor.document.text).isEqualTo("not-empty")
  }

  /**
   *  The purpose this test is to ensure that we are calling the HyperlinkHighlighter with the correct line range. It does not test user on
   *  any visible effect.
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
      verify(mockHyperlinkDetector).detectHyperlinks(eq(0), eq(1))
      verify(mockHyperlinkDetector).detectHyperlinks(eq(1), eq(2))
    }
  }

  /**
   *  The purpose this test is to ensure that we are calling the HyperlinkHighlighter with the correct line range. It does not test user on
   *  any visible effect.
   */
  @Test
  fun hyperlinks_rangeWithCyclicBuffer() = runBlocking {
    val logcatMainPanel = runInEdtAndGet {
      logcatMainPanel(hyperlinkDetector = mockHyperlinkDetector, logcatSettings = AndroidLogcatSettings(bufferSize = 1024))
    }
    val longMessage = "message".padStart(1000, '-')

    logcatMainPanel.messageProcessor.appendMessages(listOf(logcatMessage(message = longMessage)))
    logcatMainPanel.messageProcessor.onIdle {} // force flush
    logcatMainPanel.messageProcessor.appendMessages(listOf(logcatMessage(message = longMessage)))

    logcatMainPanel.messageProcessor.onIdle {
      verify(mockHyperlinkDetector, times(2)).detectHyperlinks(eq(0), eq(1))
    }
  }

  /**
   *  The purpose this test is to ensure that we are calling the FoldingDetector with the correct line range. It does not test user on any
   *  visible effect.
   */
  @Test
  fun foldings_range() = runBlocking {
    val logcatMainPanel = runInEdtAndGet {
      logcatMainPanel(foldingDetector = mockFoldingDetector)
    }

    logcatMainPanel.messageProcessor.appendMessages(listOf(logcatMessage()))
    logcatMainPanel.messageProcessor.onIdle {}
    logcatMainPanel.messageProcessor.appendMessages(listOf(logcatMessage()))

    logcatMainPanel.messageProcessor.onIdle {
      verify(mockFoldingDetector).detectFoldings(eq(0), eq(1))
      verify(mockFoldingDetector).detectFoldings(eq(1), eq(2))
    }
  }

  /**
   *  The purpose this test is to ensure that we are calling the FoldingDetector with the correct line range. It does not test user on any
   *  visible effect.
   */
  @Test
  fun foldings_rangeWithCyclicBuffer() = runBlocking {
    val logcatMainPanel = runInEdtAndGet {
      logcatMainPanel(foldingDetector = mockFoldingDetector, logcatSettings = AndroidLogcatSettings(bufferSize = 1024))
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
    assertThat(logcatPanelConfig!!.formattingConfig.toFormattingOptions().tagFormat.maxLength).isEqualTo(15)
  }

  @RunsInEdt
  @Test
  fun appliesState() {
    val logcatMainPanel = logcatMainPanel(
      state = LogcatPanelConfig(
        device = null,
        FormattingConfig.Custom(FormattingOptions(tagFormat = TagFormat(17))),
        filter = "foo",
        isSoftWrap = true))

    // TODO(aalbert) : Also assert on device field when the combo is rewritten to allow testing.
    assertThat(logcatMainPanel.formattingOptions.tagFormat.maxLength).isEqualTo(17)
    assertThat(logcatMainPanel.messageProcessor.logcatFilter).isEqualTo(StringFilter("foo", IMPLICIT_LINE, TextRange(0, "foo".length)))
    assertThat(logcatMainPanel.headerPanel.filter).isEqualTo("foo")
    assertThat(logcatMainPanel.editor.settings.isUseSoftWraps).isTrue()
  }

  @RunsInEdt
  @Test
  fun appliesState_noState() {
    val logcatMainPanel = logcatMainPanel(state = null)

    assertThat(logcatMainPanel.formattingOptions).isEqualTo(FormattingOptions())
    assertThat(logcatMainPanel.messageProcessor.logcatFilter).isInstanceOf(ProjectAppFilter::class.java)
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
    val logcatMainPanel = logcatMainPanel(state = null, androidProjectDetector = FakeAndroidProjectDetector(false))

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
    logcatMainPanel.messageBacklog.get().addAll(listOf(logcatMessage(message = "message", timestamp = Instant.ofEpochSecond(10))))

    runInEdtAndWait(logcatMainPanel::reloadMessages)

    ConcurrencyUtil.awaitQuiescence(AndroidExecutors.getInstance().workerThreadExecutor as ThreadPoolExecutor, TIMEOUT_SEC, SECONDS)

    logcatMainPanel.messageProcessor.onIdle {
      assertThat(logcatMainPanel.editor.document.text)
        .isEqualTo("1970-01-01 04:00:10.000     1-2     ExampleTag              com.example.app                      I  message\n")
    }
  }

  @Test
  fun processMessage_addsUpdatesBacklog(): Unit = runBlocking {
    val logcatMainPanel = runInEdtAndGet(this@LogcatMainPanelTest::logcatMainPanel)
    val messages = listOf(
      LogcatMessage(LogcatHeader(WARN, 1, 2, "app1", "", "tag1", Instant.ofEpochMilli(1000)), "message1"),
      LogcatMessage(LogcatHeader(INFO, 1, 2, "app2", "", "tag2", Instant.ofEpochMilli(1000)), "message2"),
    )

    logcatMainPanel.processMessages(messages)

    assertThat(logcatMainPanel.messageBacklog.get().messages).containsExactlyElementsIn(messages)
    logcatMainPanel.messageProcessor.onIdle {
      assertThat(logcatMainPanel.editor.document.text).isEqualTo("""
        1970-01-01 04:00:01.000     1-2     tag1                    app1                                 W  message1
        1970-01-01 04:00:01.000     1-2     tag2                    app2                                 I  message2
        
      """.trimIndent())
    }
  }

  @Test
  fun connectDevice_readLogcat() = runBlocking {
    val message1 = LogcatMessage(LogcatHeader(WARN, 1, 2, "app1", "", "tag1", Instant.ofEpochMilli(1000)), "message1")
    val testDevice = TestDevice("device1", DeviceState.ONLINE, 11, 30, "Google", "Pixel", "")
    fakeAdbSession.deviceServices.setupCommandsForDevice(testDevice)
    fakeAdbSession.hostServices.setDevices(testDevice)
    val logcatService = FakeLogcatService()
    val logcatMainPanel = runInEdtAndGet {
      logcatMainPanel(logcatService = logcatService, adbSession = fakeAdbSession).also {
        waitForCondition { it.getConnectedDevice() != null }
      }
    }

    logcatService.logMessages(message1)

    waitForCondition { logcatMainPanel.logcatServiceJob != null }
    waitForCondition { logcatMainPanel.messageBacklog.get().messages.isNotEmpty() }
    logcatMainPanel.messageProcessor.onIdle {
      assertThat(logcatMainPanel.editor.document.text).isEqualTo("""
        1970-01-01 04:00:01.000     1-2     tag1                    app1                                 W  message1
        
      """.trimIndent())
    }
  }

  @Test
  fun pauseLogcat_jobCanceled() = runBlocking {
    val testDevice = TestDevice("device1", DeviceState.ONLINE, 11, 30, "Google", "Pixel", "")
    fakeAdbSession.deviceServices.setupCommandsForDevice(testDevice)
    fakeAdbSession.hostServices.setDevices(testDevice)
    val logcatService = FakeLogcatService()
    val logcatMainPanel = runInEdtAndGet {
      logcatMainPanel(logcatService = logcatService, adbSession = fakeAdbSession).also {
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

  @Test
  fun resumeLogcat_jobResumed() = runBlocking {
    val message1 = LogcatMessage(LogcatHeader(WARN, 1, 2, "app1", "", "tag1", Instant.ofEpochMilli(1000)), "message1")
    val testDevice = TestDevice("device1", DeviceState.ONLINE, 11, 30, "Google", "Pixel", "")
    fakeAdbSession.deviceServices.setupCommandsForDevice(testDevice)
    fakeAdbSession.hostServices.setDevices(testDevice)
    val logcatService = FakeLogcatService()
    val logcatMainPanel = runInEdtAndGet {
      logcatMainPanel(logcatService = logcatService, adbSession = fakeAdbSession).also {
        waitForCondition { it.getConnectedDevice() != null }
      }
    }
    logcatMainPanel.pauseLogcat()
    waitForCondition { logcatMainPanel.isLogcatPaused() }

    logcatMainPanel.resumeLogcat()
    waitForCondition { !logcatMainPanel.isLogcatPaused() }
    logcatService.logMessages(message1)

    assertThat(logcatMainPanel.logcatServiceJob).isNotNull()
    waitForCondition { logcatMainPanel.messageBacklog.get().messages.isNotEmpty() }
    logcatMainPanel.messageProcessor.onIdle {
      assertThat(logcatMainPanel.editor.document.text).isEqualTo("""
        1970-01-01 04:00:01.000     1-2     tag1                    app1                                 W  message1
        
      """.trimIndent())
    }
  }

  @Test
  fun processMessage_updatesTags(): Unit = runBlocking {
    val logcatMainPanel = runInEdtAndGet(this@LogcatMainPanelTest::logcatMainPanel)
    val messages = listOf(
      LogcatMessage(LogcatHeader(WARN, 1, 2, "app1", "", "tag1", Instant.ofEpochMilli(1000)), "message1"),
      LogcatMessage(LogcatHeader(INFO, 1, 2, "app2", "", "tag2", Instant.ofEpochMilli(1000)), "message2"),
    )

    logcatMainPanel.processMessages(messages)

    assertThat(logcatMainPanel.getTags()).containsExactly("tag1", "tag2")
  }

  @Test
  fun processMessage_updatesPackages(): Unit = runBlocking {
    val logcatMainPanel = runInEdtAndGet(this@LogcatMainPanelTest::logcatMainPanel)
    val messages = listOf(
      LogcatMessage(LogcatHeader(WARN, 1, 2, "app1", "", "tag1", Instant.ofEpochMilli(1000)), "message1"),
      LogcatMessage(LogcatHeader(INFO, 1, 2, "app2", "", "tag2", Instant.ofEpochMilli(1000)), "message2"),
    )

    logcatMainPanel.processMessages(messages)

    assertThat(logcatMainPanel.getPackageNames()).containsExactly("app1", "app2")
  }

  @Test
  fun applyLogcatSettings_bufferSize() = runBlocking {
    val logcatMainPanel = runInEdtAndGet { logcatMainPanel(logcatSettings = AndroidLogcatSettings(bufferSize = 1024000)) }
    val document = logcatMainPanel.editor.document as DocumentImpl
    val logcatMessage = logcatMessage(message = "foo".padStart(97, ' ')) // Make the message part exactly 100 chars long
    // Insert 20 log lines
    logcatMainPanel.processMessages(List(20) { logcatMessage })
    val logcatSettings = AndroidLogcatSettings(bufferSize = 1024)

    logcatMainPanel.applyLogcatSettings(logcatSettings)

    logcatMainPanel.messageProcessor.onIdle {
      assertThat(document.text.length).isAtMost(1024 + logcatMessage.length())
      // backlog trims by message length
      assertThat(logcatMainPanel.messageBacklog.get().messages.sumOf { it.message.length }).isLessThan(1024)
    }
  }

  @Test
  fun setFormattingOptions_reloadsMessages() = runBlocking {
    val logcatMainPanel = runInEdtAndGet(this@LogcatMainPanelTest::logcatMainPanel)
    logcatMainPanel.processMessages(listOf(
      LogcatMessage(LogcatHeader(WARN, 1, 2, "app1", "", "tag1", Instant.ofEpochMilli(1000)), "message1"),
    ))

    logcatMainPanel.messageProcessor.onIdle {
      logcatMainPanel.formattingOptions = COMPACT.formattingOptions
    }

    ConcurrencyUtil.awaitQuiescence(AndroidExecutors.getInstance().workerThreadExecutor as ThreadPoolExecutor, TIMEOUT_SEC, SECONDS)
    logcatMainPanel.messageProcessor.onIdle {
      assertThat(logcatMainPanel.editor.document.text.trim()).isEqualTo("04:00:01.000  W  message1")
    }
  }

  @RunsInEdt
  @Test
  fun usageTracking_noState_standard() {
    logcatMainPanel(state = null)

    assertThat(usageTrackerRule.logcatEvents()).containsExactly(
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
                .setPackageWidth(35))
            .setFilter(
              LogcatFilterEvent.newBuilder()
                .setPackageProjectTerms(1)))
        .build())
  }

  @RunsInEdt
  @Test
  fun usageTracking_noState_compact() {
    androidLogcatFormattingOptions.defaultFormatting = COMPACT
    logcatMainPanel(state = null)

    assertThat(usageTrackerRule.logcatEvents()).containsExactly(
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
                .setPackageWidth(35))
            .setFilter(
              LogcatFilterEvent.newBuilder()
                .setPackageProjectTerms(1)))
        .build())
  }

  @RunsInEdt
  @Test
  fun usageTracking_withState_preset() {
    logcatMainPanel(state = LogcatPanelConfig(
      device = null,
      formattingConfig = FormattingConfig.Preset(COMPACT),
      "filter",
      isSoftWrap = false))

    assertThat(usageTrackerRule.logcatEvents()).containsExactly(
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
                .setPackageWidth(35))
            .setFilter(
              LogcatFilterEvent.newBuilder()
                .setImplicitLineTerms(1)))
        .build())
  }

  @RunsInEdt
  @Test
  fun usageTracking_withState_custom() {
    logcatMainPanel(state = LogcatPanelConfig(
      device = null,
      formattingConfig = FormattingConfig.Custom(FormattingOptions(tagFormat = TagFormat(20, hideDuplicates = false, enabled = true))),
      "filter",
      isSoftWrap = false))

    assertThat(usageTrackerRule.logcatEvents()).containsExactly(
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
                .setPackageWidth(35))
            .setFilter(
              LogcatFilterEvent.newBuilder()
                .setImplicitLineTerms(1)))
        .build())
  }

  @Test
  fun clickToSetFilter_addToEmpty() = runBlocking {
    val logcatMainPanel = runInEdtAndGet {
      logcatMainPanel().apply {
        size = Dimension(100, 100)
        headerPanel.filter = ""
      }
    }
    val fakeUi = runInEdtAndGet { FakeUi(logcatMainPanel.editor.contentComponent, createFakeWindow = true) }

    logcatMainPanel.messageProcessor.appendMessages(listOf(
      LogcatMessage(LogcatHeader(INFO, 1, 2, "app2", "", "tag2", Instant.ofEpochMilli(1000)), "message2"),
    ))

    logcatMainPanel.messageProcessor.onIdle {
      runInEdtAndWait {
        val offset = logcatMainPanel.editor.document.immutableCharSequence.indexOf("app2")
        val point = logcatMainPanel.editor.offsetToXY(offset)
        fakeUi.mouse.click(point.x + 1, point.y + 1, CTRL_LEFT)
        assertThat(logcatMainPanel.headerPanel.filter).isEqualTo("package:app2")
      }
    }
  }

  @Test
  fun clickToSetFilter_addToNotEmpty() = runBlocking {
    val logcatMainPanel = runInEdtAndGet {
      logcatMainPanel().apply {
        size = Dimension(100, 100)
      }
    }
    val fakeUi = runInEdtAndGet { FakeUi(logcatMainPanel.editor.contentComponent, createFakeWindow = true) }
    logcatMainPanel.processMessages(listOf(
      LogcatMessage(LogcatHeader(INFO, 1, 2, "app1", "", "tag1", Instant.ofEpochMilli(1000)), "foo"),
      LogcatMessage(LogcatHeader(INFO, 1, 2, "app2", "", "tag2", Instant.ofEpochMilli(1000)), "bar"),
    ))
    runInEdtAndWait { logcatMainPanel.setFilter("foo") }
    waitForCondition { logcatMainPanel.editor.document.text.endsWith("foo\n") }
    logcatMainPanel.messageProcessor.onIdle {
      runInEdtAndWait {
        val offset = logcatMainPanel.editor.document.immutableCharSequence.indexOf("app1")
        val point = logcatMainPanel.editor.offsetToXY(offset)

        fakeUi.mouse.click(point.x + 1, point.y + 1, CTRL_LEFT)

        assertThat(logcatMainPanel.headerPanel.filter).isEqualTo("foo package:app1")
      }
    }
  }

  @Test
  fun clickToSetFilter_remove() = runBlocking {
    val logcatMainPanel = runInEdtAndGet {
      logcatMainPanel().apply {
        size = Dimension(100, 100)
      }
    }
    val fakeUi = runInEdtAndGet { FakeUi(logcatMainPanel.editor.contentComponent, createFakeWindow = true) }
    logcatMainPanel.processMessages(listOf(
      LogcatMessage(LogcatHeader(INFO, 1, 2, "app1", "", "tag1", Instant.ofEpochMilli(1000)), "foo"),
      LogcatMessage(LogcatHeader(DEBUG, 1, 2, "app2", "", "tag2", Instant.ofEpochMilli(1000)), "bar"),
    ))
    runInEdtAndWait { logcatMainPanel.setFilter("foo level:INFO") }
    waitForCondition { logcatMainPanel.editor.document.text.endsWith("foo\n") }
    logcatMainPanel.messageProcessor.onIdle {
      runInEdtAndWait {
        val offset = logcatMainPanel.editor.document.immutableCharSequence.indexOf(" I ")
        val point = logcatMainPanel.editor.offsetToXY(offset)
        fakeUi.mouse.click(point.x + 1, point.y + 1, CTRL_LEFT)
        assertThat(logcatMainPanel.headerPanel.filter).isEqualTo("foo")
      }
    }
  }

  @Test
  fun clickToSetFilter_removeMultiple() = runBlocking {
    val logcatMainPanel = runInEdtAndGet {
      logcatMainPanel().apply {
        size = Dimension(100, 100)
      }
    }
    val fakeUi = runInEdtAndGet { FakeUi(logcatMainPanel.editor.contentComponent, createFakeWindow = true) }
    logcatMainPanel.processMessages(listOf(
      LogcatMessage(LogcatHeader(INFO, 1, 2, "app1", "", "tag1", Instant.ofEpochMilli(1000)), "foo"),
      LogcatMessage(LogcatHeader(DEBUG, 1, 2, "app2", "", "tag2", Instant.ofEpochMilli(1000)), "bar"),
    ))
    runInEdtAndWait { logcatMainPanel.setFilter(" level:INFO foo level:INFO") }
    waitForCondition { logcatMainPanel.editor.document.text.endsWith("foo\n") }
    logcatMainPanel.messageProcessor.onIdle {
      runInEdtAndWait {
        val offset = logcatMainPanel.editor.document.immutableCharSequence.indexOf(" I ")
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
    val fakeUi = runInEdtAndGet { FakeUi(logcatMainPanel.editor.contentComponent, createFakeWindow = true) }

    logcatMainPanel.messageProcessor.appendMessages(listOf(
      LogcatMessage(LogcatHeader(LogLevel.ERROR, 1, 2, "app2", "", "tag2", Instant.ofEpochMilli(1000)), "message2"),
    ))

    logcatMainPanel.messageProcessor.onIdle {
      runInEdtAndWait {
        val offset = logcatMainPanel.editor.document.immutableCharSequence.indexOf(" I ")
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
  fun missingApplicationIds_showsBanner(): Unit = runBlocking {
    val fakePackageNamesProvider = FakeProjectApplicationIdsProvider(project)
    val logcatMainPanel = runInEdtAndGet {
      logcatMainPanel(projectApplicationIdsProvider = fakePackageNamesProvider, filter = "package:mine")
    }

    assertThat(logcatMainPanel.findBanner("Could not detect project package names. Is the project synced?").isVisible).isTrue()
  }

  @Test
  fun hasApplicationIds_doesNotShowBanner(): Unit = runBlocking {
    val fakePackageNamesProvider = FakeProjectApplicationIdsProvider(project, "app1")
    val logcatMainPanel = runInEdtAndGet {
      logcatMainPanel(projectApplicationIdsProvider = fakePackageNamesProvider, filter = "package:mine")
    }

    assertThat(logcatMainPanel.findBanner("Could not detect project package names. Is the project synced?").isVisible).isFalse()
  }

  @Test
  fun applicationIdsChange_bannerUpdates(): Unit = runBlocking {
    val fakePackageNamesProvider = FakeProjectApplicationIdsProvider(project)
    val logcatMainPanel = runInEdtAndGet {
      logcatMainPanel(projectApplicationIdsProvider = fakePackageNamesProvider, filter = "package:mine")
    }
    val banner = logcatMainPanel.findBanner("Could not detect project package names. Is the project synced?")

    assertThat(banner.isVisible).isTrue()

    runInEdtAndWait { fakePackageNamesProvider.setApplicationIds("app1") }
    assertThat(banner.isVisible).isFalse()

    runInEdtAndWait {
      fakePackageNamesProvider.setApplicationIds()
    }
    assertThat(banner.isVisible).isTrue()
  }

  @Test
  fun noLogsBanner_appearsAndDisappearsWhenAddingLogs(): Unit = runBlocking {
    val logcatMainPanel = runInEdtAndGet(::logcatMainPanel)
    val noLogsBanner = logcatMainPanel.findBanner("All logs entries are hidden by the filter")
    logcatMainPanel.processMessages(listOf(
      LogcatMessage(LogcatHeader(WARN, 1, 2, "app1", "", "tag1", Instant.ofEpochMilli(1000)), "message1"),
    ))
    waitForCondition {
      logcatMainPanel.editor.document.text.trim() == """
        1970-01-01 04:00:01.000     1-2     tag1                    app1                                 W  message1
      """.trimIndent()
    }
    runInEdtAndWait {
      logcatMainPanel.setFilter("no-match")
    }
    waitForCondition { noLogsBanner.isVisible }
    logcatMainPanel.processMessages(listOf(
      LogcatMessage(LogcatHeader(WARN, 1, 2, "app1", "", "tag1", Instant.ofEpochMilli(1000)), "no-match"),
    ))
    waitForCondition { !noLogsBanner.isVisible }
  }

  @Test
  fun noLogsBanner_appearsAndDisappearsWhenChangingFilter(): Unit = runBlocking {
    val logcatMainPanel = runInEdtAndGet(::logcatMainPanel)
    val noLogsBanner = logcatMainPanel.findBanner("All logs entries are hidden by the filter")
    logcatMainPanel.processMessages(listOf(
      LogcatMessage(LogcatHeader(WARN, 1, 2, "app1", "", "tag1", Instant.ofEpochMilli(1000)), "message1"),
    ))
    waitForCondition {
      logcatMainPanel.editor.document.text.trim() == """
        1970-01-01 04:00:01.000     1-2     tag1                    app1                                 W  message1
      """.trimIndent()
    }
    runInEdtAndWait {
      logcatMainPanel.setFilter("no-match")
    }
    waitForCondition { noLogsBanner.isVisible }
    runInEdtAndWait {
      logcatMainPanel.setFilter("tag:tag1")
    }
    waitForCondition { !noLogsBanner.isVisible }
  }

  @Test
  fun noLogsBanner_doesNotShowIfNoMessagesExist(): Unit = runBlocking {
    val logcatMainPanel = runInEdtAndGet(::logcatMainPanel)
    val noLogsBanner = logcatMainPanel.findBanner("All logs entries are hidden by the filter")
    logcatMainPanel.processMessages(listOf(
      LogcatMessage(LogcatHeader(WARN, 1, 2, "app1", "", "tag1", Instant.ofEpochMilli(1000)), "message1"),
    ))
    waitForCondition {
      logcatMainPanel.editor.document.text.trim() == """
        1970-01-01 04:00:01.000     1-2     tag1                    app1                                 W  message1
      """.trimIndent()
    }
    runInEdtAndWait {
      logcatMainPanel.setFilter("no-match")
    }
    waitForCondition { noLogsBanner.isVisible }
    runInEdtAndWait {
      logcatMainPanel.clearMessageView()
    }
    waitForCondition { !noLogsBanner.isVisible }
  }

  @Test
  fun projectAppMonitorInstalled() {
    val testDevice = TestDevice("device1", DeviceState.ONLINE, 11, 30, "Google", "Pixel", "")
    fakeAdbSession.deviceServices.setupCommandsForDevice(testDevice)
    fakeAdbSession.hostServices.setDevices(testDevice)
    val fakePackageNamesProvider = FakeProjectApplicationIdsProvider(project, "myapp")
    val logcatMainPanel = runInEdtAndGet {
      logcatMainPanel(adbSession = fakeAdbSession, projectApplicationIdsProvider = fakePackageNamesProvider).also {
        waitForCondition { it.getConnectedDevice() != null }
      }
    }
    val iDevice = mock<IDevice>()
    val client = mock<Client>()
    val clientData = mock<ClientData>()

    whenever(clientData.packageName).thenReturn("myapp")
    whenever(client.clientData).thenReturn(clientData)
    whenever(iDevice.serialNumber).thenReturn("device1")
    whenever(iDevice.clients).thenReturn(arrayOf(client))
    AndroidDebugBridge.deviceChanged(iDevice, CHANGE_CLIENT_LIST)

    waitForCondition {
      logcatMainPanel.editor.document.text.contains("PROCESS STARTED (0) for package myapp")
    }
  }

  @RunsInEdt
  @Test
  fun projectAppMonitorRemoved() {
    val logcatMainPanel = logcatMainPanel()
    assertThat(AndroidDebugBridge.getDeviceChangeListenerCount() == 1)

    Disposer.dispose(logcatMainPanel)

    assertThat(AndroidDebugBridge.getDeviceChangeListenerCount() == 0)
  }

  @Test
  fun projectApplicationIdsChange_withPackageMine_reloadsMessages() = runBlocking {
    val fakeProjectApplicationIdsProvider = FakeProjectApplicationIdsProvider(project)
    val logcatMainPanel = runInEdtAndGet {
      logcatMainPanel(projectApplicationIdsProvider = fakeProjectApplicationIdsProvider)
    }
    logcatMainPanel.processMessages(listOf(
      LogcatMessage(LogcatHeader(WARN, 1, 2, "app1", "", "tag1", Instant.ofEpochMilli(1000)), "message1"),
      LogcatMessage(LogcatHeader(WARN, 1, 2, "app2", "", "tag2", Instant.ofEpochMilli(1000)), "message2"),
    ))
    runInEdtAndWait {
      logcatMainPanel.setFilter("package:mine | tag:tag2")
    }
    waitForCondition {
      logcatMainPanel.editor.document.text.trim() == """
        1970-01-01 04:00:01.000     1-2     tag2                    app2                                 W  message2
      """.trimIndent()
    }

    runInEdtAndWait {
      fakeProjectApplicationIdsProvider.setApplicationIds("app1")
    }

    ConcurrencyUtil.awaitQuiescence(AndroidExecutors.getInstance().workerThreadExecutor as ThreadPoolExecutor, TIMEOUT_SEC, SECONDS)
    logcatMainPanel.messageProcessor.onIdle {
      assertThat(logcatMainPanel.editor.document.text.trim()).isEqualTo("""
        1970-01-01 04:00:01.000     1-2     tag1                    app1                                 W  message1
        1970-01-01 04:00:01.000     1-2     tag2                    app2                                 W  message2
      """.trimIndent())
    }
  }

  private fun logcatMainPanel(
    splitterPopupActionGroup: ActionGroup = EMPTY_GROUP,
    logcatColors: LogcatColors = LogcatColors(),
    filter: String = "",
    state: LogcatPanelConfig? = LogcatPanelConfig(device = null, FormattingConfig.Preset(STANDARD), filter = filter, isSoftWrap = false),
    logcatSettings: AndroidLogcatSettings = AndroidLogcatSettings(),
    androidProjectDetector: AndroidProjectDetector = FakeAndroidProjectDetector(true),
    hyperlinkDetector: HyperlinkDetector? = null,
    foldingDetector: FoldingDetector? = null,
    projectApplicationIdsProvider: ProjectApplicationIdsProvider = FakeProjectApplicationIdsProvider(project),
    adbSession: AdbSession = FakeAdbSession(),
    logcatService: LogcatService = FakeLogcatService(),
    zoneId: ZoneId = ZoneId.of("Asia/Yerevan"),
  ): LogcatMainPanel {
    project.replaceService(ProjectApplicationIdsProvider::class.java, projectApplicationIdsProvider, disposableRule.disposable)
    return LogcatMainPanel(
      project,
      splitterPopupActionGroup,
      logcatColors,
      state,
      { adbSession },
      logcatSettings,
      androidProjectDetector,
      hyperlinkDetector,
      foldingDetector,
      logcatService,
      zoneId,
    ).also {
      Disposer.register(disposableRule.disposable, it)
    }
  }
}

private fun LogcatMessage.length() = FormattingOptions().getHeaderWidth() + message.length

private class FakeLogcatService : LogcatService {
  private var channel: Channel<List<LogcatMessage>>? = null

  suspend fun logMessages(vararg messages: LogcatMessage) {
    channel?.send(messages.asList()) ?: throw IllegalStateException("Channel not setup. Did you call readLogcat()?")
  }

  override suspend fun readLogcat(device: Device): Flow<List<LogcatMessage>> {
    return Channel<List<LogcatMessage>>(1).also { channel = it }.consumeAsFlow()
  }

  override suspend fun clearLogcat(device: Device) {
  }

}

private fun List<AnAction>.mapToStrings(indent: String = ""): List<String> {
  return flatMap {
    when (it) {
      is Separator -> listOf("-")
      is PopupActionGroupAction -> listOf(it.templateText) + it.getPopupActions().mapToStrings("$indent  ")
      else -> listOf(it.templateText ?: "null")
    }
  }.map { "$indent$it" }
}

private fun waitForCondition(condition: () -> Boolean) = waitForCondition(TIMEOUT_SEC, SECONDS, condition)

private fun LogcatMainPanel.findBanner(text: String) =
  TreeWalker(this).descendants().first { it is EditorNotificationPanel && it.text == text } as EditorNotificationPanel
