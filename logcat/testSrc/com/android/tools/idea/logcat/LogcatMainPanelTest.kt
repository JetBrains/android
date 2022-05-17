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
import com.android.adblib.DeviceState
import com.android.adblib.testing.FakeAdbLibSession
import com.android.ddmlib.AvdData
import com.android.ddmlib.IDevice
import com.android.ddmlib.IDevice.DeviceState.ONLINE
import com.android.ddmlib.Log.LogLevel.ERROR
import com.android.ddmlib.Log.LogLevel.INFO
import com.android.ddmlib.Log.LogLevel.WARN
import com.android.ddmlib.logcat.LogCatHeader
import com.android.ddmlib.logcat.LogCatMessage
import com.android.sdklib.AndroidVersion
import com.android.testutils.MockitoKt.eq
import com.android.testutils.MockitoKt.mock
import com.android.tools.adtui.swing.FakeMouse.Button.CTRL_LEFT
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.popup.PopupRule
import com.android.tools.analytics.UsageTrackerRule
import com.android.tools.idea.FakeAndroidProjectDetector
import com.android.tools.idea.concurrency.AndroidExecutors
import com.android.tools.idea.concurrency.waitForCondition
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.logcat.LogcatPanelConfig.FormattingConfig
import com.android.tools.idea.logcat.devices.Device
import com.android.tools.idea.logcat.actions.PopupActionGroupAction
import com.android.tools.idea.logcat.filters.AndroidLogcatFilterHistory
import com.android.tools.idea.logcat.filters.LogcatFilterField.IMPLICIT_LINE
import com.android.tools.idea.logcat.filters.LogcatFilterField.LINE
import com.android.tools.idea.logcat.filters.ProjectAppFilter
import com.android.tools.idea.logcat.filters.StringFilter
import com.android.tools.idea.logcat.folding.FoldingDetector
import com.android.tools.idea.logcat.hyperlinks.HyperlinkDetector
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
import com.android.tools.idea.logcat.util.AdbAdapter
import com.android.tools.idea.logcat.util.AndroidProjectDetector
import com.android.tools.idea.logcat.util.FakeAdbAdapter
import com.android.tools.idea.logcat.util.LogcatFilterLanguageRule
import com.android.tools.idea.logcat.util.isCaretAtBottom
import com.android.tools.idea.logcat.util.logcatEvents
import com.android.tools.idea.run.ClearLogcatListener
import com.android.tools.idea.testing.AndroidExecutorsRule
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.Futures.immediateFuture
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
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.replaceService
import com.intellij.testFramework.runInEdtAndGet
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.tools.SimpleActionGroup
import com.intellij.util.ConcurrencyUtil
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
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

  @get:Rule
  val rule = RuleChain(projectRule, EdtRule(), androidExecutorsRule, popupRule, LogcatFilterLanguageRule(), usageTrackerRule)

  private val mockHyperlinkDetector = mock<HyperlinkDetector>()
  private val mockFoldingDetector = mock<FoldingDetector>()
  private val fakeAdbAdapter = FakeAdbAdapter()
  private val fakeAdbLibSession = FakeAdbLibSession()
  private val androidLogcatFormattingOptions = AndroidLogcatFormattingOptions()

  @Before
  fun setUp() {
    ApplicationManager.getApplication().replaceService(
      AndroidLogcatFormattingOptions::class.java,
      androidLogcatFormattingOptions,
      projectRule.project)
    StudioFlags.LOGCAT_CLICK_TO_ADD_FILTER.override(true)
  }

  @After
  fun tearDown() {
    StudioFlags.LOGCAT_CLICK_TO_ADD_FILTER.clearOverride()
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
    assertThat(borderLayout.getLayoutComponent(CENTER)).isSameAs(logcatMainPanel.editor.component)
    assertThat(borderLayout.getLayoutComponent(WEST)).isInstanceOf(ActionToolbar::class.java)
    val toolbar = borderLayout.getLayoutComponent(WEST) as ActionToolbar
    assertThat(toolbar.actions.mapToStrings()).containsExactly(
      "Clear Logcat",
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
      "Screen Capture",
      "Screen Record",
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
    val logCatMessage = logCatMessage()

    // Insert 20 log lines
    logcatMainPanel.messageProcessor.appendMessages(List(20) { logCatMessage })
    logcatMainPanel.messageProcessor.onIdle {
      assertThat(document.text.length).isAtMost(1024 + logCatMessage.length())
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
      LogCatMessage(LogCatHeader(WARN, 1, 2, "app1", "tag1", Instant.ofEpochMilli(1000)), "message1"),
      LogCatMessage(LogCatHeader(INFO, 1, 2, "app2", "tag2", Instant.ofEpochMilli(1000)), "message2"),
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
      LogCatMessage(LogCatHeader(WARN, 1, 2, "app1", "tag1", Instant.ofEpochMilli(1000)), "message1"),
      LogCatMessage(LogCatHeader(INFO, 1, 2, "app2", "tag2", Instant.ofEpochMilli(1000)), "message2"),
    ))

    logcatMainPanel.messageProcessor.onIdle {
      logcatMainPanel.applyFilter(StringFilter("tag1", LINE))
    }

    ConcurrencyUtil.awaitQuiescence(AndroidExecutors.getInstance().workerThreadExecutor as ThreadPoolExecutor, 5, SECONDS)
    logcatMainPanel.messageProcessor.onIdle {
      assertThat(logcatMainPanel.editor.document.text).isEqualTo("""
        1970-01-01 04:00:01.000     1-2     tag1                    app1                                 W  message1

      """.trimIndent())
    }
  }

  @Test
  fun appendMessages_disposedEditor() = runBlocking {
    val logcatMainPanel = runInEdtAndGet {
      logcatMainPanel().also {
        Disposer.dispose(it)
      }
    }

    logcatMainPanel.messageProcessor.appendMessages(listOf(logCatMessage()))
  }

  @Test
  fun appendMessages_scrollToEnd() = runBlocking {
    val logcatMainPanel = runInEdtAndGet(this@LogcatMainPanelTest::logcatMainPanel)

    logcatMainPanel.messageProcessor.appendMessages(listOf(
      logCatMessage(),
      logCatMessage(),
    ))

    logcatMainPanel.messageProcessor.onIdle {
      @Suppress("ConvertLambdaToReference")
      assertThat(logcatMainPanel.editor.isCaretAtBottom()).isTrue()
    }
  }

  @Test
  fun appendMessages_notAtBottom_doesNotScrollToEnd() = runBlocking {
    val logcatMainPanel = runInEdtAndGet(this@LogcatMainPanelTest::logcatMainPanel)

    logcatMainPanel.messageProcessor.appendMessages(listOf(logCatMessage()))
    logcatMainPanel.messageProcessor.onIdle {
      logcatMainPanel.editor.caretModel.moveToOffset(0)
    }
    logcatMainPanel.messageProcessor.appendMessages(listOf(logCatMessage()))

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

    logcatMainPanel.processMessages(listOf(logCatMessage()))

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
    val device = mockDevice("device1")
    val testDevice = TestDevice(device.serialNumber, DeviceState.ONLINE, 11, 30, "Google", "Pixel", "")
    fakeAdbAdapter.mutableDevices.add(device)
    fakeAdbLibSession.deviceServices.setupCommandsForDevice(testDevice)
    fakeAdbLibSession.hostServices.setDevices(testDevice)
    val logcatMainPanel = runInEdtAndGet {
      logcatMainPanel(adbAdapter = fakeAdbAdapter, adbSession = fakeAdbLibSession).also {
        waitForCondition(TIMEOUT_SEC, SECONDS) { it.getConnectedDevice() != null }
        it.editor.document.setText("not-empty")
      }
    }

    projectRule.project.messageBus.syncPublisher(ClearLogcatListener.TOPIC).clearLogcat(device)

    ConcurrencyUtil.awaitQuiescence(AndroidExecutors.getInstance().workerThreadExecutor as ThreadPoolExecutor, TIMEOUT_SEC, SECONDS)
    runInEdtAndWait { }
    assertThat(logcatMainPanel.editor.document.text).isEmpty()
  }

  @Test
  fun clearMessageView_bySubscriptionToClearLogcatListener_otherDevice() {
    val device1 = mockDevice("device1")
    val device2 = mockDevice("device2")
    val testDevice1 = TestDevice(device1.serialNumber, DeviceState.ONLINE, 11, 30, "Google", "Pixel", "")
    val testDevice2 = TestDevice(device2.serialNumber, DeviceState.ONLINE, 11, 30, "Google", "Pixel", "")
    fakeAdbAdapter.mutableDevices.addAll(listOf(device1, device2))
    fakeAdbLibSession.deviceServices.setupCommandsForDevice(testDevice1)
    fakeAdbLibSession.deviceServices.setupCommandsForDevice(testDevice2)
    fakeAdbLibSession.hostServices.setDevices(testDevice1, testDevice2)

    val logcatMainPanel = runInEdtAndGet {
      logcatMainPanel(adbAdapter = fakeAdbAdapter, adbSession = fakeAdbLibSession).also {
        waitForCondition(TIMEOUT_SEC, SECONDS) { it.getConnectedDevice() != null }
        it.editor.document.setText("not-empty")
      }
    }

    projectRule.project.messageBus.syncPublisher(ClearLogcatListener.TOPIC).clearLogcat(device2)

    ConcurrencyUtil.awaitQuiescence(AndroidExecutors.getInstance().workerThreadExecutor as ThreadPoolExecutor, TIMEOUT_SEC, SECONDS)
    runInEdtAndWait { }
    assertThat(logcatMainPanel.editor.document.text).isEqualTo("not-empty")
  }

  @Test
  fun identifiesIDeviceFromDevice() {
    val device = mockDevice("device1")
    val testDevice = TestDevice(device.serialNumber, DeviceState.ONLINE, 11, 30, "Google", "Pixel", "")
    fakeAdbAdapter.mutableDevices.add(device)
    fakeAdbLibSession.deviceServices.setupCommandsForDevice(testDevice)
    fakeAdbLibSession.hostServices.setDevices(testDevice)
    val logcatMainPanel = runInEdtAndGet {
      logcatMainPanel(adbAdapter = fakeAdbAdapter, adbSession = fakeAdbLibSession).also {
        waitForCondition(TIMEOUT_SEC, SECONDS) { it.deviceContext.selectedDevice != null }
      }
    }
    assertThat(logcatMainPanel.deviceContext.selectedDevice).isEqualTo(device)
  }

  @Test
  fun identifiesIDeviceFromDevice_emulator() {
    val device = mockDevice("emulator-1", "avd1")
    val testDevice = TestDevice(device.serialNumber, DeviceState.ONLINE, 11, 30, "", "", avdName = "avd1")
    fakeAdbAdapter.mutableDevices.add(device)
    fakeAdbLibSession.deviceServices.setupCommandsForDevice(testDevice)
    fakeAdbLibSession.hostServices.setDevices(testDevice)
    val logcatMainPanel = runInEdtAndGet {
      logcatMainPanel(adbAdapter = fakeAdbAdapter, adbSession = fakeAdbLibSession).also {
        waitForCondition(TIMEOUT_SEC, SECONDS) { it.deviceContext.selectedDevice != null }
      }
    }
    assertThat(logcatMainPanel.deviceContext.selectedDevice).isEqualTo(device)
  }

  @Test
  fun identifiesIDeviceFromDevice_emulatorWithLegacyAvdName() {
    val device = mockDevice("emulator-1", "avd1")
    val testDevice = TestDevice(device.serialNumber, DeviceState.ONLINE, 11, 30, "", "", avdName = "", avdNamePre31 = "avd1")
    fakeAdbAdapter.mutableDevices.add(device)
    fakeAdbLibSession.deviceServices.setupCommandsForDevice(testDevice)
    fakeAdbLibSession.hostServices.setDevices(testDevice)
    val logcatMainPanel = runInEdtAndGet {
      logcatMainPanel(adbAdapter = fakeAdbAdapter, adbSession = fakeAdbLibSession).also {
        waitForCondition(TIMEOUT_SEC, SECONDS) { it.deviceContext.selectedDevice != null }
      }
    }
    assertThat(logcatMainPanel.deviceContext.selectedDevice).isEqualTo(device)
  }

  @Test
  fun identifiesIDeviceFromDevice_emulatorWithoutAvdName() {
    val device = mockDevice("emulator-1", "avd1")
    val testDevice = TestDevice(device.serialNumber, DeviceState.ONLINE, 11, 30, "", "", avdName = "", avdNamePre31 = "")
    fakeAdbAdapter.mutableDevices.add(device)
    fakeAdbLibSession.deviceServices.setupCommandsForDevice(testDevice)
    fakeAdbLibSession.hostServices.setDevices(testDevice)
    val logcatMainPanel = runInEdtAndGet {
      logcatMainPanel(adbAdapter = fakeAdbAdapter, adbSession = fakeAdbLibSession).also {
        waitForCondition(TIMEOUT_SEC, SECONDS) { it.deviceContext.selectedDevice != null }
      }
    }
    assertThat(logcatMainPanel.deviceContext.selectedDevice).isEqualTo(device)
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

    logcatMainPanel.messageProcessor.appendMessages(listOf(logCatMessage()))
    logcatMainPanel.messageProcessor.onIdle {}
    logcatMainPanel.messageProcessor.appendMessages(listOf(logCatMessage()))

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

    logcatMainPanel.messageProcessor.appendMessages(listOf(logCatMessage(message = longMessage)))
    logcatMainPanel.messageProcessor.onIdle {} // force flush
    logcatMainPanel.messageProcessor.appendMessages(listOf(logCatMessage(message = longMessage)))

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

    logcatMainPanel.messageProcessor.appendMessages(listOf(logCatMessage()))
    logcatMainPanel.messageProcessor.onIdle {}
    logcatMainPanel.messageProcessor.appendMessages(listOf(logCatMessage()))

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

    logcatMainPanel.messageProcessor.appendMessages(listOf(logCatMessage(message = longMessage)))
    logcatMainPanel.messageProcessor.onIdle {} // force flush
    logcatMainPanel.messageProcessor.appendMessages(listOf(logCatMessage(message = longMessage)))

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
        "filter",
        isSoftWrap = true))

    // TODO(aalbert) : Also assert on device field when the combo is rewritten to allow testing.
    assertThat(logcatMainPanel.formattingOptions.tagFormat.maxLength).isEqualTo(17)
    assertThat(logcatMainPanel.messageProcessor.logcatFilter).isEqualTo(StringFilter("filter", IMPLICIT_LINE))
    assertThat(logcatMainPanel.headerPanel.filter).isEqualTo("filter")
    assertThat(logcatMainPanel.editor.settings.isUseSoftWraps).isTrue()
  }

  @RunsInEdt
  @Test
  fun appliesState_noState() {
    val logcatMainPanel = logcatMainPanel(state = null)

    assertThat(logcatMainPanel.formattingOptions).isEqualTo(FormattingOptions())
    assertThat(logcatMainPanel.messageProcessor.logcatFilter).isInstanceOf(ProjectAppFilter::class.java)
    assertThat(logcatMainPanel.headerPanel.filter).isEqualTo("package:mine")
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
    logcatMainPanel.messageBacklog.get().addAll(listOf(logCatMessage(message = "message", timestamp = Instant.ofEpochSecond(10))))

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
      LogCatMessage(LogCatHeader(WARN, 1, 2, "app1", "tag1", Instant.ofEpochMilli(1000)), "message1"),
      LogCatMessage(LogCatHeader(INFO, 1, 2, "app2", "tag2", Instant.ofEpochMilli(1000)), "message2"),
    )

    logcatMainPanel.processMessages(messages)

    assertThat(logcatMainPanel.messageBacklog.get().messages).containsExactlyElementsIn(messages)
  }

  @Test
  fun processMessage_updatesTags(): Unit = runBlocking {
    val logcatMainPanel = runInEdtAndGet(this@LogcatMainPanelTest::logcatMainPanel)
    val messages = listOf(
      LogCatMessage(LogCatHeader(WARN, 1, 2, "app1", "tag1", Instant.ofEpochMilli(1000)), "message1"),
      LogCatMessage(LogCatHeader(INFO, 1, 2, "app2", "tag2", Instant.ofEpochMilli(1000)), "message2"),
    )

    logcatMainPanel.processMessages(messages)

    assertThat(logcatMainPanel.getTags()).containsExactly("tag1", "tag2")
  }

  @Test
  fun processMessage_updatesPackages(): Unit = runBlocking {
    val logcatMainPanel = runInEdtAndGet(this@LogcatMainPanelTest::logcatMainPanel)
    val messages = listOf(
      LogCatMessage(LogCatHeader(WARN, 1, 2, "app1", "tag1", Instant.ofEpochMilli(1000)), "message1"),
      LogCatMessage(LogCatHeader(INFO, 1, 2, "?", "tag2", Instant.ofEpochMilli(1000)), "message2"),
    )

    logcatMainPanel.processMessages(messages)

    assertThat(logcatMainPanel.getPackageNames()).containsExactly("app1", "pid-1")
  }

  @Test
  fun applyLogcatSettings_bufferSize() = runBlocking {
    val logcatMainPanel = runInEdtAndGet { logcatMainPanel(logcatSettings = AndroidLogcatSettings(bufferSize = 1024000)) }
    val document = logcatMainPanel.editor.document as DocumentImpl
    val logCatMessage = logCatMessage(message = "foo".padStart(97, ' ')) // Make the message part exactly 100 chars long
    // Insert 20 log lines
    logcatMainPanel.processMessages(List(20) { logCatMessage })
    val logcatSettings = AndroidLogcatSettings(bufferSize = 1024)

    logcatMainPanel.applyLogcatSettings(logcatSettings)

    logcatMainPanel.messageProcessor.onIdle {
      assertThat(document.text.length).isAtMost(1024 + logCatMessage.length())
      // backlog trims by message length
      assertThat(logcatMainPanel.messageBacklog.get().messages.sumOf { it.message.length }).isLessThan(1024)
    }
  }

  @Test
  fun setFormattingOptions_reloadsMessages() = runBlocking {
    val logcatMainPanel = runInEdtAndGet(this@LogcatMainPanelTest::logcatMainPanel)
    logcatMainPanel.processMessages(listOf(
      LogCatMessage(LogCatHeader(WARN, 1, 2, "app1", "tag1", Instant.ofEpochMilli(1000)), "message1"),
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
      LogCatMessage(LogCatHeader(INFO, 1, 2, "app2", "tag2", Instant.ofEpochMilli(1000)), "message2"),
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
        headerPanel.filter = "foo"
      }
    }
    val fakeUi = runInEdtAndGet { FakeUi(logcatMainPanel.editor.contentComponent, createFakeWindow = true) }

    logcatMainPanel.messageProcessor.appendMessages(listOf(
      LogCatMessage(LogCatHeader(INFO, 1, 2, "app2", "tag2", Instant.ofEpochMilli(1000)), "message2"),
    ))

    logcatMainPanel.messageProcessor.onIdle {
      runInEdtAndWait {
        val offset = logcatMainPanel.editor.document.immutableCharSequence.indexOf("app2")
        val point = logcatMainPanel.editor.offsetToXY(offset)
        fakeUi.mouse.click(point.x + 1, point.y + 1, CTRL_LEFT)
        assertThat(logcatMainPanel.headerPanel.filter).isEqualTo("foo package:app2")
      }
    }
  }

  @Test
  fun clickToSetFilter_remove() = runBlocking {
    val logcatMainPanel = runInEdtAndGet {
      logcatMainPanel().apply {
        size = Dimension(100, 100)
        headerPanel.filter = "package:mine level:INFO"
      }
    }
    val fakeUi = runInEdtAndGet { FakeUi(logcatMainPanel.editor.contentComponent, createFakeWindow = true) }

    logcatMainPanel.messageProcessor.appendMessages(listOf(
      LogCatMessage(LogCatHeader(INFO, 1, 2, "app2", "tag2", Instant.ofEpochMilli(1000)), "message2"),
    ))

    logcatMainPanel.messageProcessor.onIdle {
      runInEdtAndWait {
        val offset = logcatMainPanel.editor.document.immutableCharSequence.indexOf(" I ")
        val point = logcatMainPanel.editor.offsetToXY(offset)
        fakeUi.mouse.click(point.x + 1, point.y + 1, CTRL_LEFT)
        assertThat(logcatMainPanel.headerPanel.filter).isEqualTo("package:mine")
      }
    }
  }

  @Test
  fun clickToSetFilter_removeMultiple() = runBlocking {
    val logcatMainPanel = runInEdtAndGet {
      logcatMainPanel().apply {
        size = Dimension(100, 100)
        headerPanel.filter = "level:INFO package:mine level:INFO"
      }
    }
    val fakeUi = runInEdtAndGet { FakeUi(logcatMainPanel.editor.contentComponent, createFakeWindow = true) }

    logcatMainPanel.messageProcessor.appendMessages(listOf(
      LogCatMessage(LogCatHeader(INFO, 1, 2, "app2", "tag2", Instant.ofEpochMilli(1000)), "message2"),
    ))

    logcatMainPanel.messageProcessor.onIdle {
      runInEdtAndWait {
        val offset = logcatMainPanel.editor.document.immutableCharSequence.indexOf(" I ")
        val point = logcatMainPanel.editor.offsetToXY(offset)
        fakeUi.mouse.click(point.x + 1, point.y + 1, CTRL_LEFT)
        assertThat(logcatMainPanel.headerPanel.filter).isEqualTo("package:mine")
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
      LogCatMessage(LogCatHeader(ERROR, 1, 2, "app2", "tag2", Instant.ofEpochMilli(1000)), "message2"),
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

  private fun logcatMainPanel(
    splitterPopupActionGroup: ActionGroup = EMPTY_GROUP,
    logcatColors: LogcatColors = LogcatColors(),
    state: LogcatPanelConfig? = LogcatPanelConfig(device = null, FormattingConfig.Preset(STANDARD), filter = "", isSoftWrap = false),
    logcatSettings: AndroidLogcatSettings = AndroidLogcatSettings(),
    androidProjectDetector: AndroidProjectDetector = FakeAndroidProjectDetector(true),
    hyperlinkDetector: HyperlinkDetector? = null,
    foldingDetector: FoldingDetector? = null,
    packageNamesProvider: PackageNamesProvider = FakePackageNamesProvider(),
    adbAdapter: AdbAdapter = FakeAdbAdapter(),
    adbSession: AdbLibSession = FakeAdbLibSession(),
    zoneId: ZoneId = ZoneId.of("Asia/Yerevan"),
  ) =
    LogcatMainPanel(
      projectRule.project,
      splitterPopupActionGroup,
      logcatColors,
      state,
      logcatSettings,
      androidProjectDetector,
      hyperlinkDetector,
      foldingDetector,
      packageNamesProvider,
      adbAdapter,
      adbSession,
      FakeLogcatService(),
      zoneId,
    ).also {
      Disposer.register(projectRule.project, it)
    }
}

private fun LogCatMessage.length() = FormattingOptions().getHeaderWidth() + message.length

private fun mockDevice(serialNumber: String, avdName: String = ""): IDevice {
  return mock<IDevice>().also {
    // Set up a mock device with just enough information to get the test to work. We still get a bunch of errors in the log.
    // TODO(aalbert): Extract an interface from LogcatDeviceManager so we can pass a factory into LogcatMainPanel to make it easier to
    //  test.
    `when`(it.state).thenReturn(ONLINE)
    `when`(it.clients).thenReturn(emptyArray())
    `when`(it.serialNumber).thenReturn(serialNumber)
    `when`(it.version).thenReturn(AndroidVersion(30))
    `when`(it.avdData).thenReturn(immediateFuture(AvdData(avdName, avdName)))
  }
}

private class FakeLogcatService : LogcatService {
  override suspend fun readLogcat(device: Device): Flow<List<LogCatMessage>> = flowOf(emptyList())

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
