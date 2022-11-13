/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.streaming

import com.android.SdkConstants
import com.android.repository.Revision
import com.android.repository.impl.meta.RepositoryPackages
import com.android.repository.testframework.FakePackage.FakeLocalPackage
import com.android.repository.testframework.FakeRepoManager
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.concurrency.AndroidExecutors
import com.android.tools.idea.sdk.AndroidSdks
import com.android.tools.idea.streaming.device.settings.DeviceMirroringSettingsUi
import com.android.tools.idea.streaming.emulator.settings.EmulatorSettingsUi
import com.android.tools.idea.testing.AndroidExecutorsRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.ActionManagerEx
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.replaceService
import com.intellij.util.ConcurrencyUtil
import com.intellij.util.ui.UIUtil
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import javax.swing.JEditorPane
import javax.swing.event.HyperlinkEvent

/**
 * Test for [EmptyStatePanel].
 */
@RunsInEdt
class EmptyStatePanelTest {

  private val projectRule = ProjectRule()
  private val androidExecutorsRule = AndroidExecutorsRule(workerThreadExecutor = Executors.newCachedThreadPool())
  @get:Rule
  val rule = RuleChain(projectRule, androidExecutorsRule, EdtRule())

  private val testRootDisposable by lazy { @Suppress("UnstableApiUsage") projectRule.project.earlyDisposable }
  private val emptyStatePanel by lazy { createEmptyStatePanel() }
  private val ui by lazy { FakeUi(emptyStatePanel) }
  private val emulatorPackage = FakeLocalPackage(SdkConstants.FD_EMULATOR).apply { setRevision(Revision(31, 3, 9)) }
  private var savedLaunchInToolWindow = false
  private var savedDeviceMirroringEnabled = false
  private val executedActions = mutableListOf<String>()

  @Before
  fun setUp() {
    val mockActionManager = mock<ActionManagerEx>()
    whenever(mockActionManager.getAction(any())).thenAnswer { TestAction(it.getArgument(0)) }
    ApplicationManager.getApplication().replaceService(ActionManager::class.java, mockActionManager, testRootDisposable)

    val repoManager = FakeRepoManager(RepositoryPackages(listOf(emulatorPackage), emptyList()))
    val sdkHandler = AndroidSdkHandler(null, null, repoManager)
    val mockAndroidSdks = mock<AndroidSdks>()
    whenever(mockAndroidSdks.tryToChooseSdkHandler()).thenReturn(sdkHandler)
    ApplicationManager.getApplication().replaceService(AndroidSdks::class.java, mockAndroidSdks, testRootDisposable)

    savedLaunchInToolWindow = EmulatorSettings.getInstance().launchInToolWindow
    savedDeviceMirroringEnabled = DeviceMirroringSettings.getInstance().deviceMirroringEnabled
  }

  @After
  fun tearDown() {
    Disposer.dispose(emptyStatePanel)
    EmulatorSettings.getInstance().launchInToolWindow = savedLaunchInToolWindow
    DeviceMirroringSettings.getInstance().deviceMirroringEnabled = savedDeviceMirroringEnabled
  }

  @Test
  fun testEverythingEnabled() {
    EmulatorSettings.getInstance().launchInToolWindow = true
    DeviceMirroringSettings.getInstance().deviceMirroringEnabled = true
    val htmlComponent = ui.getComponent<JEditorPane>()
    val text = htmlComponent.text.replace(Regex("&#160;|\\s+"), " ")
    assertThat(text).contains("To launch a virtual device, use the" +
                              " <font color=\"589df6\"><a href=\"DeviceManager\">Device Manager</a></font>" +
                              " or run your app while targeting a virtual device.")
    assertThat(text).contains("To mirror a physical device, connect it via USB cable or over WiFi.")
    htmlComponent.clickOnHyperlink("DeviceManager")
    assertThat(executedActions).containsExactly("Android.DeviceManager")
  }

  @Test
  fun testEverythingDisabled() {
    EmulatorSettings.getInstance().launchInToolWindow = false
    DeviceMirroringSettings.getInstance().deviceMirroringEnabled = false
    val htmlComponent = ui.getComponent<JEditorPane>()
    val text = htmlComponent.text.replace(Regex("&#160;|\\s+"), " ")
    assertThat(text).contains("To launch virtual devices in this window, select the <i>Launch in a tool window</i> option" +
                              " in the <font color=\"589df6\"><a href=\"EmulatorSettings\">Emulator settings</a></font>.")
    assertThat(text).contains("To mirror physical devices, select the <i>Enable mirroring of physical Android devices</i> option" +
                              " in the <font color=\"589df6\"><a href=\"DeviceMirroringSettings\">Device Mirroring settings</a></font>.")

    val shownSettings = mutableListOf<Class<Configurable>>()
    val mockSettings = mock<ShowSettingsUtil>()
    whenever(mockSettings.showSettingsDialog(any(), any<Class<Configurable>>())).thenAnswer { shownSettings.add(it.getArgument(1)) }
    ApplicationManager.getApplication().replaceService(ShowSettingsUtil::class.java, mockSettings, testRootDisposable)

    htmlComponent.clickOnHyperlink("EmulatorSettings")
    assertThat(shownSettings).containsExactly(EmulatorSettingsUi::class.java)
    shownSettings.clear()
    htmlComponent.clickOnHyperlink("DeviceMirroringSettings")
    assertThat(shownSettings).containsExactly(DeviceMirroringSettingsUi::class.java)
  }

  @Test
  fun testLaunchInToolWindowDisabled() {
    EmulatorSettings.getInstance().launchInToolWindow = false
    DeviceMirroringSettings.getInstance().deviceMirroringEnabled = true
    val htmlComponent = ui.getComponent<JEditorPane>()
    val text = htmlComponent.text.replace(Regex("&#160;|\\s+"), " ")
    assertThat(text).contains("To launch virtual devices in this window, select the <i>Launch in a tool window</i> option" +
                              " in the <font color=\"589df6\"><a href=\"EmulatorSettings\">Emulator settings</a></font>.")
    assertThat(text).contains("To mirror a physical device, connect it via USB cable or over WiFi.")
  }

  @Test
  fun testMirroringDisabled() {
    EmulatorSettings.getInstance().launchInToolWindow = true
    DeviceMirroringSettings.getInstance().deviceMirroringEnabled = false
    val htmlComponent = ui.getComponent<JEditorPane>()
    val text = htmlComponent.text.replace(Regex("&#160;|\\s+"), " ")
    assertThat(text).contains("To launch a virtual device, use the" +
                              " <font color=\"589df6\"><a href=\"DeviceManager\">Device Manager</a></font>" +
                              " or run your app while targeting a virtual device.")
    assertThat(text).contains("To mirror physical devices, select the <i>Enable mirroring of physical Android devices</i> option" +
                              " in the <font color=\"589df6\"><a href=\"DeviceMirroringSettings\">Device Mirroring settings</a></font>.")
  }

  @Test
  fun testEmulatorIsTooOldMirroringEnabled() {
    EmulatorSettings.getInstance().launchInToolWindow = true
    DeviceMirroringSettings.getInstance().deviceMirroringEnabled = true
    emulatorPackage.setRevision(Revision(30, 6))
    val htmlComponent = ui.getComponent<JEditorPane>()
    val text = htmlComponent.text.replace(Regex("&#160;|\\s+"), " ")
    assertThat(text).contains("To launch virtual devices in this window, install Android Emulator 30.7.4 or higher." +
                              " Please <font color=\"589df6\"><a href=\"CheckForUpdate\">check for updates</a></font>" +
                              " and install the latest version of the Android Emulator.")
    assertThat(text).contains("To mirror a physical device, connect it via USB cable or over WiFi.")
    htmlComponent.clickOnHyperlink("CheckForUpdate")
    assertThat(executedActions).containsExactly("CheckForUpdate")
  }

  @Test
  fun testEmulatorIsTooOldMirroringDisabled() {
    EmulatorSettings.getInstance().launchInToolWindow = true
    DeviceMirroringSettings.getInstance().deviceMirroringEnabled = false
    emulatorPackage.setRevision(Revision(30, 6))
    val htmlComponent = ui.getComponent<JEditorPane>()
    val text = htmlComponent.text.replace(Regex("&#160;|\\s+"), " ")
    assertThat(text).contains("To launch virtual devices in this window, install Android Emulator 30.7.4 or higher." +
                              " Please <font color=\"589df6\"><a href=\"CheckForUpdate\">check for updates</a></font>" +
                              " and install the latest version of the Android Emulator.")
    assertThat(text).contains("To mirror physical devices, select the <i>Enable mirroring of physical Android devices</i> option" +
                              " in the <font color=\"589df6\"><a href=\"DeviceMirroringSettings\">Device Mirroring settings</a></font>.")
  }

  private fun createEmptyStatePanel(): EmptyStatePanel {
    val panel = EmptyStatePanel(projectRule.project).apply { setSize(500, 1000) }

    // Allow the panel to update itself.
    ConcurrencyUtil.awaitQuiescence(AndroidExecutors.getInstance().workerThreadExecutor as ThreadPoolExecutor, 2, TimeUnit.SECONDS)
    UIUtil.dispatchAllInvocationEvents()

    return panel
  }

  private fun JEditorPane.clickOnHyperlink(hyperlink: String) {
    fireHyperlinkUpdate(HyperlinkEvent(this, HyperlinkEvent.EventType.ACTIVATED, null, hyperlink))
  }

  private inner class TestAction(val id: String) : DumbAwareAction() {
    override fun actionPerformed(event: AnActionEvent) {
      executedActions.add(id)
    }
  }
}