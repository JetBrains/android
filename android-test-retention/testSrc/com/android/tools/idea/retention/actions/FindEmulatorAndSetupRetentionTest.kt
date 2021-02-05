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

import com.android.testutils.truth.PathSubject.assertThat
import com.android.tools.idea.concurrency.waitForCondition
import com.android.tools.idea.emulator.EmulatorController
import com.android.tools.idea.emulator.FakeEmulator
import com.android.tools.idea.emulator.FakeEmulatorRule
import com.android.tools.idea.emulator.RunningEmulatorCatalog
import com.android.tools.idea.testartifacts.instrumented.DEVICE_NAME_KEY
import com.android.tools.idea.testartifacts.instrumented.EMULATOR_SNAPSHOT_FILE_KEY
import com.android.tools.idea.testartifacts.instrumented.EMULATOR_SNAPSHOT_ID_KEY
import com.android.tools.idea.testartifacts.instrumented.PACKAGE_NAME_KEY
import com.android.tools.idea.testartifacts.instrumented.RETENTION_AUTO_CONNECT_DEBUGGER_KEY
import com.android.tools.idea.testartifacts.instrumented.RETENTION_ON_FINISH_KEY
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RunsInEdt
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@Ignore("b/156983404")
@RunsInEdt
class FindEmulatorAndSetupRetentionTest {
  private val projectRule = ProjectRule()
  private val emulatorRule = FakeEmulatorRule()
  @get:Rule
  val ruleChain: RuleChain = RuleChain.outerRule(projectRule).around(emulatorRule).around(EdtRule())
  lateinit var tempFolder: Path
  lateinit var snapshotFile: Path
  lateinit var emulator: FakeEmulator
  lateinit var dataContext: DataContext
  lateinit var parentDataContext : DataContext
  val snapshotId = "snapshot_id"
  lateinit var retentionDoneSignal: CountDownLatch

  @Before
  fun setUp() {
    tempFolder = emulatorRule.root
    snapshotFile = emulatorRule.newPath()
    Files.writeString(snapshotFile, "file content")
    emulator = emulatorRule.newEmulator(FakeEmulator.createPhoneAvd(tempFolder), 8554)

    parentDataContext = DataContext { projectRule.project }
    dataContext = object : DataContext {
      override fun getData(dataId: String): Any? {
        if (dataId == EMULATOR_SNAPSHOT_ID_KEY.name) {
          return snapshotId
        }
        if (dataId == EMULATOR_SNAPSHOT_FILE_KEY.name) {
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
        if (dataId == DEVICE_NAME_KEY.name) {
          return emulator.avdId
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
    emulator.getNextGrpcCall(2, TimeUnit.SECONDS) // Skip the initial "getVmState" call

    val anActionEvent = AnActionEvent(null, dataContext,
                                      ActionPlaces.UNKNOWN, Presentation(),
                                      ActionManager.getInstance(), 0)
    val action = FindEmulatorAndSetupRetention()
    action.actionPerformed(anActionEvent)
    retentionDoneSignal.await()
    // It pushes a header message, followed by a content message
    assertThat(emulator.getNextGrpcCall(2, TimeUnit.SECONDS).methodName)
      .matches("android.emulation.control.SnapshotService/PushSnapshot")
    assertThat(emulator.getNextGrpcCall(2, TimeUnit.SECONDS).methodName)
      .matches("android.emulation.control.SnapshotService/PushSnapshot")
    assertThat(emulator.getNextGrpcCall(2, TimeUnit.SECONDS).methodName)
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
        if (dataId == EMULATOR_SNAPSHOT_ID_KEY.name || dataId == EMULATOR_SNAPSHOT_FILE_KEY.name) {
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