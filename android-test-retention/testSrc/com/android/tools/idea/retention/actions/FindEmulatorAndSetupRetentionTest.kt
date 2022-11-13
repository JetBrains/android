/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.retention.actions

import com.android.prefs.AndroidLocationsSingleton
import com.android.sdklib.internal.avd.AvdInfo
import com.android.sdklib.internal.avd.AvdManager
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.testutils.ignore.IgnoreWithCondition
import com.android.testutils.ignore.OnMac
import com.android.testutils.truth.PathSubject.assertThat
import com.android.tools.idea.concurrency.waitForCondition
import com.android.tools.idea.streaming.emulator.EmulatorController
import com.android.tools.idea.streaming.emulator.FakeEmulator
import com.android.tools.idea.streaming.emulator.FakeEmulatorRule
import com.android.tools.idea.streaming.emulator.RunningEmulatorCatalog
import com.android.tools.idea.testartifacts.instrumented.AVD_NAME_KEY
import com.android.tools.idea.testartifacts.instrumented.EMULATOR_SNAPSHOT_FILE_KEY
import com.android.tools.idea.testartifacts.instrumented.EMULATOR_SNAPSHOT_ID_KEY
import com.android.tools.idea.testartifacts.instrumented.EMULATOR_SNAPSHOT_LAUNCH_PARAMETERS
import com.android.tools.idea.testartifacts.instrumented.IS_MANAGED_DEVICE
import com.android.tools.idea.testartifacts.instrumented.PACKAGE_NAME_KEY
import com.android.tools.idea.testartifacts.instrumented.RETENTION_AUTO_CONNECT_DEBUGGER_KEY
import com.android.tools.idea.testartifacts.instrumented.RETENTION_ON_FINISH_KEY
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.mockito.Mockito.any
import org.mockito.Mockito.anyBoolean
import org.mockito.Mockito.anyString
import org.mockito.Mockito.doReturn
import java.io.File
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@IgnoreWithCondition(reason = "b/156983404", condition = OnMac::class)
@RunsInEdt
class FindEmulatorAndSetupRetentionTest {
  private val projectRule = AndroidProjectRule.inMemory()
  private val emulatorRule = FakeEmulatorRule()
  @get:Rule
  val ruleChain: RuleChain = RuleChain.outerRule(projectRule).around(emulatorRule).around(EdtRule())
  lateinit var tempFolder: Path
  lateinit var snapshotFile: File
  lateinit var emulator: FakeEmulator
  lateinit var dataContext: DataContext
  lateinit var parentDataContext : DataContext
  val snapshotId = "snapshot_id"
  lateinit var retentionDoneSignal: CountDownLatch

  @Before
  fun setUp() {
    tempFolder = emulatorRule.root
    snapshotFile = File(tempFolder.resolve("snapshot.tar").toUri())
    snapshotFile.writeText("file content")
    emulator = emulatorRule.newEmulator(FakeEmulator.createPhoneAvd(AndroidLocationsSingleton.avdLocation))

    parentDataContext = DataContext { projectRule.project }
    dataContext = object : DataContext {
      override fun getData(dataId: String): Any? {
        if (EMULATOR_SNAPSHOT_ID_KEY.`is`(dataId)) {
          return snapshotId
        }
        if (EMULATOR_SNAPSHOT_FILE_KEY.`is`(dataId)) {
          assertThat(snapshotFile).exists()
          assertThat(snapshotFile).isReadable()
          return snapshotFile
        }
        if (dataId == PACKAGE_NAME_KEY.name) {
          return projectRule.project.name
        }
        if (dataId == RETENTION_ON_FINISH_KEY.name) {
          return Runnable { retentionDoneSignal.countDown() }
        }
        if (dataId == RETENTION_AUTO_CONNECT_DEBUGGER_KEY.name) {
          return false
        }
        if (dataId == AVD_NAME_KEY.name) {
          return emulator.avdId
        }
        if (dataId == IS_MANAGED_DEVICE.name) {
          return false
        }
        if (dataId == EMULATOR_SNAPSHOT_LAUNCH_PARAMETERS.name) {
          return null
        }
        return parentDataContext.getData(dataId)
      }
    }
    retentionDoneSignal = CountDownLatch(1)
  }

  @Test
  fun filterBootParameters1() {
    val parameters = listOf("./emulator", "-netdelay", "123", "-netspeed", "456", "-avd", "abc", "-grpc-use-token", "-feature", "Vulkan")
    val filtered = filterEmulatorBootParameters(parameters)
    assertThat(filtered).isEqualTo(listOf("-feature", "Vulkan"))
  }

  @Test
  fun filterBootParameters2() {
    val parameters = listOf("./emulator", "@abc", "-feature", "Vulkan")
    val filtered = filterEmulatorBootParameters(parameters)
    assertThat(filtered).isEqualTo(listOf("-feature", "Vulkan"))
  }

  @Test
  fun pushAndLoad() {
    emulator.start()
    val emulators = RunningEmulatorCatalog.getInstance().updateNow().get()
    assertThat(emulators).hasSize(1)
    val emulatorController = emulators.first()
    waitForCondition(2, TimeUnit.SECONDS) { emulatorController.connectionState == EmulatorController.ConnectionState.CONNECTED }

    val anActionEvent = AnActionEvent(null, dataContext,
                                      ActionPlaces.UNKNOWN, Presentation(),
                                      ActionManager.getInstance(), 0)
    val action = FindEmulatorAndSetupRetention { androidSdkHandler, path, iLogger ->
      val mockAvdManager = mock<AvdManager>()
      doReturn(mock<AvdInfo>()).whenever(mockAvdManager).getAvd(anyString(), anyBoolean())
      doReturn(true).whenever(mockAvdManager).isAvdRunning(any())
      mockAvdManager
    }
    action.actionPerformed(anActionEvent)
    retentionDoneSignal.await()
    // It pushes a header message, followed by a content message
    assertThat(emulator.getNextGrpcCall(5, TimeUnit.SECONDS).methodName)
      .matches("android.emulation.control.SnapshotService/PushSnapshot")
    assertThat(emulator.getNextGrpcCall(5, TimeUnit.SECONDS).methodName)
      .matches("android.emulation.control.SnapshotService/LoadSnapshot")
  }

  @Test
  fun pushAndLoadFolder() {
    snapshotFile = File(tempFolder.resolve("snapshot_folder").toUri())
    snapshotFile.mkdir()
    emulator.start()
    val emulators = RunningEmulatorCatalog.getInstance().updateNow().get()
    assertThat(emulators).hasSize(1)
    val emulatorController = emulators.first()
    waitForCondition(2, TimeUnit.SECONDS) { emulatorController.connectionState == EmulatorController.ConnectionState.CONNECTED }

    val anActionEvent = AnActionEvent(null, dataContext,
                                      ActionPlaces.UNKNOWN, Presentation(),
                                      ActionManager.getInstance(), 0)
    val action = FindEmulatorAndSetupRetention { androidSdkHandler, path, iLogger ->
      val mockAvdManager = mock<AvdManager>()
      doReturn(mock<AvdInfo>()).whenever(mockAvdManager).getAvd(anyString(), anyBoolean())
      doReturn(true).whenever(mockAvdManager).isAvdRunning(any())
      mockAvdManager
    }
    action.actionPerformed(anActionEvent)
    retentionDoneSignal.await()
    // It pushes a header message, followed by a content message
    assertThat(emulator.getNextGrpcCall(5, TimeUnit.SECONDS).methodName)
      .matches("android.emulation.control.SnapshotService/PushSnapshot")
    assertThat(emulator.getNextGrpcCall(5, TimeUnit.SECONDS).methodName)
      .matches("android.emulation.control.SnapshotService/LoadSnapshot")
  }

  @Test
  fun actionEnabled() {
    val anActionEvent = AnActionEvent(null, dataContext,
                                      ActionPlaces.UNKNOWN, Presentation(),
                                      ActionManager.getInstance(), 0)
    FindEmulatorAndSetupRetention().update(anActionEvent)
    assertThat(anActionEvent.presentation.isEnabled).isTrue()
  }

  @Test
  fun actionDisabled() {
    val noSnapshotDataContext = object : DataContext {
      override fun getData(dataId: String): Any? {
        if (EMULATOR_SNAPSHOT_ID_KEY.`is`(dataId) || EMULATOR_SNAPSHOT_FILE_KEY.`is`(dataId)) {
          return null
        }
        return parentDataContext.getData(dataId)
      }
    }
    val anActionEvent = AnActionEvent(null, noSnapshotDataContext,
                                      ActionPlaces.UNKNOWN, Presentation(),
                                      ActionManager.getInstance(), 0)
    FindEmulatorAndSetupRetention().update(anActionEvent)
    assertThat(anActionEvent.presentation.isEnabled).isFalse()
  }
}