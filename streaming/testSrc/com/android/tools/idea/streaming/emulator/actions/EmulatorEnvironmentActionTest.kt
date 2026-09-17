/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.streaming.emulator.actions

import com.android.testutils.waitForCondition
import com.android.tools.adtui.actions.executeAction
import com.android.tools.idea.avdmanager.EnvironmentsUpdater
import com.android.tools.idea.protobuf.TextFormat.shortDebugString
import com.android.tools.idea.streaming.emulator.EMULATOR_CONTROLLER_KEY
import com.android.tools.idea.streaming.emulator.EmulatorController
import com.android.tools.idea.streaming.emulator.FakeEmulator
import com.android.tools.idea.streaming.emulator.FakeEmulatorRule
import com.android.tools.idea.streaming.emulator.RunningEmulatorCatalog
import com.android.tools.idea.testing.disposable
import com.android.tools.idea.testing.file.registerFakeFileChooserFactory
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DataSnapshotProvider
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.replaceService
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunsInEdt
class EmulatorEnvironmentActionTest {

  private val projectRule = ProjectRule()
  private val emulatorRule = FakeEmulatorRule()
  @get:Rule val rule = RuleChain(projectRule, emulatorRule, EdtRule())

  val testRootDisposable
    get() = projectRule.disposable

  private val emulator by lazy {
    val avdFolder = FakeEmulator.createAiGlassesAvd(emulatorRule.avdRoot)
    emulatorRule.newEmulator(avdFolder)
  }
  private val emulatorController by lazy {
    emulator.start()
    val catalog = RunningEmulatorCatalog.getInstance()
    val controller = runBlocking { catalog.updateNow().await() }.first()
    waitForCondition(5.seconds) { controller.connectionState == EmulatorController.ConnectionState.CONNECTED }
    controller
  }
  private val dataSnapshotProvider by lazy { DataSnapshotProvider { sink -> sink[EMULATOR_CONTROLLER_KEY] = emulatorController } }

  @Test
  fun testEmptyEnvironment() {
    val action = ActionManager.getInstance().getAction("android.emulator.environment.empty")
    executeAction(action, project = projectRule.project, extra = dataSnapshotProvider)

    val call = emulator.getNextGrpcCall(2.seconds)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/setEnvironment")
    assertThat(shortDebugString(call.request)).isEqualTo("")
  }

  @Test
  fun testDefaultEnvironment() {
    val environmentsUpdater = mock<EnvironmentsUpdater>()
    runBlocking {
      doAnswer { Path.of("/Sdk/environments/${it.getArgument<String>(0)}") }.whenever(environmentsUpdater).getUpdatedFile(any())
    }
    ApplicationManager.getApplication().replaceService(EnvironmentsUpdater::class.java, environmentsUpdater, testRootDisposable)
    val action = ActionManager.getInstance().getAction("android.emulator.environment.default")
    executeAction(action, project = projectRule.project, extra = dataSnapshotProvider)

    val call = emulator.getNextGrpcCall(2.seconds)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/setEnvironment")
    assertThat(shortDebugString(call.request))
      .isEqualTo("environment { key: \"scene.mode\" value: \"imagefile:/Sdk/environments/default-background-1.png\" }")
  }

  @Test
  fun testCustomEnvironment() {
    val imageFile = mock<VirtualFile>()
    whenever(imageFile.path).thenReturn("/tmp/test_image.png")
    testRootDisposable.registerFakeFileChooserFactory(imageFile)

    val action = ActionManager.getInstance().getAction("android.emulator.environment.custom")
    executeAction(action, project = projectRule.project, extra = dataSnapshotProvider)

    val call = emulator.getNextGrpcCall(2.seconds)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/setEnvironment")
    assertThat(shortDebugString(call.request)).isEqualTo("environment { key: \"scene.mode\" value: \"imagefile:${imageFile.path}\" }")
  }
}
