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
package com.android.tools.idea.layoutinspector.pipeline

import com.android.ddmlib.testing.FakeAdbRule
import com.android.tools.idea.appinspection.api.process.ProcessesModel
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.android.tools.idea.appinspection.test.TestProcessNotifier
import com.android.tools.idea.layoutinspector.LEGACY_DEVICE
import com.android.tools.idea.layoutinspector.MODERN_DEVICE
import com.android.tools.idea.layoutinspector.createProcess
import com.android.tools.idea.layoutinspector.properties.PropertiesProvider
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.DisposableRule
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import java.nio.file.Path

class InspectorClientLauncherTest {
  @get:Rule
  val adbRule = FakeAdbRule()

  @get:Rule
  val disposableRule = DisposableRule()

  private open class FakeInspectorClient(
    val name: String,
    process: ProcessDescriptor)
    : AbstractInspectorClient(process) {

    override fun startFetching() = throw NotImplementedError()
    override fun stopFetching() = throw NotImplementedError()
    override fun refresh() = throw NotImplementedError()
    override fun saveSnapshot(path: Path) = throw NotImplementedError()

    override fun doConnect(): ListenableFuture<Nothing> = Futures.immediateFuture(null)
    override fun doDisconnect(): ListenableFuture<Nothing> = Futures.immediateFuture(null)

    override val capabilities
      get() = throw NotImplementedError()
    override val treeLoader: TreeLoader get() = throw NotImplementedError()
    override val isCapturing: Boolean get() = throw NotImplementedError()
    override val provider: PropertiesProvider get() = throw NotImplementedError()
  }

  @Test
  fun initialInspectorLauncherStartsWithDisconnectedClient() {
    val processes = ProcessesModel(TestProcessNotifier()) { listOf() }
    val launcher = InspectorClientLauncher(adbRule.bridge, processes, listOf(), disposableRule.disposable, MoreExecutors.directExecutor())

    assertThat(launcher.activeClient).isInstanceOf(DisconnectedClient::class.java)
  }

  @Test
  fun emptyInspectorLauncherIgnoresProcessChanges() {
    val processes = ProcessesModel(TestProcessNotifier()) { listOf() }
    val launcher = InspectorClientLauncher(adbRule.bridge, processes, listOf(), disposableRule.disposable, MoreExecutors.directExecutor())

    var clientChangedCount = 0
    launcher.addClientChangedListener { clientChangedCount++ }

    processes.selectedProcess = MODERN_DEVICE.createProcess()

    assertThat(launcher.activeClient).isInstanceOf(DisconnectedClient::class.java)
    assertThat(clientChangedCount).isEqualTo(0)
  }

  @Test
  fun inspectorLauncherWithNoMatchReturnsDisconnectedClient() {
    val processes = ProcessesModel(TestProcessNotifier()) { listOf() }
    val launcher = InspectorClientLauncher(
      adbRule.bridge,
      processes,
      listOf { params ->
        if (params.process.device.apiLevel == MODERN_DEVICE.apiLevel) FakeInspectorClient(
          "Modern client", params.process)
        else null
      },
      disposableRule.disposable,
      MoreExecutors.directExecutor())

    assertThat(launcher.activeClient).isInstanceOf(DisconnectedClient::class.java)

    processes.selectedProcess = MODERN_DEVICE.createProcess()
    assertThat(launcher.activeClient).isInstanceOf(FakeInspectorClient::class.java)

    processes.selectedProcess = LEGACY_DEVICE.createProcess()
    assertThat(launcher.activeClient).isInstanceOf(DisconnectedClient::class.java)
  }

  @Test
  fun disposingLauncherDisconnectsAndDisposesActiveClient() {
    val processes = ProcessesModel(TestProcessNotifier()) { listOf() }

    val launcherDisposable = Disposer.newDisposable()
    var clientWasDisconnected = false
    val launcher = InspectorClientLauncher(
      adbRule.bridge,
      processes,
      listOf { params ->
        val client = FakeInspectorClient("Client", params.process)
        client.registerStateCallback { state -> if (state == InspectorClient.State.DISCONNECTED) clientWasDisconnected = true }
        client
      },
      launcherDisposable,
      MoreExecutors.directExecutor())

    processes.selectedProcess = MODERN_DEVICE.createProcess()
    assertThat(launcher.activeClient.isConnected).isTrue()

    assertThat(clientWasDisconnected).isFalse()
    Disposer.dispose(launcherDisposable)
    assertThat(clientWasDisconnected).isTrue()
    assertThat(launcher.activeClient.isConnected).isFalse()
  }

  @Test
  fun inspectorLauncherUsesFirstMatchingClient() {
    val processes = ProcessesModel(TestProcessNotifier()) { listOf() }

    var creatorCount1 = 0
    var creatorCount2 = 0
    var creatorCount3 = 0

    val launcher = InspectorClientLauncher(
      adbRule.bridge,
      processes,
      listOf(
        { params ->
          creatorCount1++
          if (params.process.device.apiLevel == MODERN_DEVICE.apiLevel) FakeInspectorClient("Modern client", params.process) else null
        },
        { params ->
          creatorCount2++
          if (params.process.device.apiLevel == LEGACY_DEVICE.apiLevel) FakeInspectorClient("Legacy client", params.process) else null
        },
        { params ->
          creatorCount3++
          FakeInspectorClient("Fallback client", params.process)
        }
      ),
      disposableRule.disposable,
      MoreExecutors.directExecutor())

    var clientChangedCount = 0
    launcher.addClientChangedListener { ++clientChangedCount }

    assertThat(!launcher.activeClient.isConnected)

    processes.selectedProcess = MODERN_DEVICE.createProcess()
    (launcher.activeClient as FakeInspectorClient).let { activeClient ->
      assertThat(activeClient.name).isEqualTo("Modern client")
    }
    assertThat(clientChangedCount).isEqualTo(1)
    assertThat(creatorCount1).isEqualTo(1)
    assertThat(creatorCount2).isEqualTo(0)
    assertThat(creatorCount3).isEqualTo(0)

    processes.selectedProcess = LEGACY_DEVICE.createProcess()
    (launcher.activeClient as FakeInspectorClient).let { activeClient ->
      assertThat(activeClient.name).isEqualTo("Legacy client")
    }

    assertThat(clientChangedCount).isEqualTo(2)
    assertThat(creatorCount1).isEqualTo(2)
    assertThat(creatorCount2).isEqualTo(1)
    assertThat(creatorCount3).isEqualTo(0)
  }

  @Test
  fun inspectorLauncherSkipsOverClientsThatFailToConnect() {
    val processes = ProcessesModel(TestProcessNotifier()) { listOf() }

    val launcher = InspectorClientLauncher(
      adbRule.bridge,
      processes,
      listOf(
        { params ->
          val client = object : FakeInspectorClient("Exploding client #1", params.process) {
            override fun doConnect() = throw IllegalStateException()
          }
          // Verify disconnect not called if connect fails
          client.registerStateCallback { state -> if (state == InspectorClient.State.DISCONNECTED) fail() }
          client
        },
        { params ->
          val client = object : FakeInspectorClient("Exploding client #2", params.process) {
            override fun doConnect() = throw IllegalStateException()
          }
          // Verify disconnect not called if connect fails
          client.registerStateCallback { state -> if (state == InspectorClient.State.DISCONNECTED) fail() }
          client
        },
        { params ->
          FakeInspectorClient("Fallback client", params.process)
        }
      ),
      disposableRule.disposable,
      MoreExecutors.directExecutor())


    processes.selectedProcess = MODERN_DEVICE.createProcess()
    (launcher.activeClient as FakeInspectorClient).let { activeClient ->
      assertThat(activeClient.name).isEqualTo("Fallback client")
    }
  }

  @Test
  fun inspectorLauncherWithNoSuccessfulConnectionsReturnsDisconnectedClient() {
    val processes = ProcessesModel(TestProcessNotifier()) { listOf() }

    val launcher = InspectorClientLauncher(
      adbRule.bridge,
      processes,
      listOf(
        { params ->
          val client = object : FakeInspectorClient("Exploding client #1", params.process) {
            override fun doConnect() = throw IllegalStateException()
          }
          // Verify disconnect not called if connect fails
          client.registerStateCallback { state -> if (state == InspectorClient.State.DISCONNECTED) fail() }
          client
        },
        { params ->
          val client = object : FakeInspectorClient("Exploding client #2", params.process) {
            override fun doConnect() = throw IllegalStateException()
          }
          // Verify disconnect not called if connect fails
          client.registerStateCallback { state -> if (state == InspectorClient.State.DISCONNECTED) fail() }
          client
        },
        { params ->
          if (params.process.device.apiLevel >= MODERN_DEVICE.apiLevel) {
            FakeInspectorClient("Modern client", params.process)
          }
          else {
            null
          }
        }
      ),
      disposableRule.disposable,
      MoreExecutors.directExecutor())

    // Set to a valid client first, so we know we actually changed correctly to a disconnected
    // client later.
    processes.selectedProcess = MODERN_DEVICE.createProcess()
    (launcher.activeClient as FakeInspectorClient).let { activeClient ->
      assertThat(activeClient.name).isEqualTo("Modern client")
    }

    processes.selectedProcess = LEGACY_DEVICE.createProcess()
    assertThat(launcher.activeClient).isInstanceOf(DisconnectedClient::class.java)
  }

  @Test
  fun inspectorLauncherCanBeDisabledAndRenabled() {
    val process1 = MODERN_DEVICE.createProcess(pid = 1)
    val process2 = MODERN_DEVICE.createProcess(pid = 2)
    val process3 = MODERN_DEVICE.createProcess(pid = 3)
    val deadProcess3 = MODERN_DEVICE.createProcess(pid = 3, isRunning = false)

    val notifier = TestProcessNotifier()
    val processes = ProcessesModel(notifier) { listOf(process1.name) } // Note: This covers all processes as they have the same name
    val launcher = InspectorClientLauncher(
      adbRule.bridge,
      processes,
      listOf { params -> FakeInspectorClient("Unused", params.process) },
      disposableRule.disposable,
      MoreExecutors.directExecutor())

    launcher.enabled = false
    notifier.fireConnected(process1)
    assertThat(launcher.activeClient).isInstanceOf(DisconnectedClient::class.java)

    launcher.enabled = true
    assertThat(launcher.activeClient.process).isSameAs(process1)

    launcher.enabled = false
    assertThat(launcher.activeClient.process).isSameAs(process1)

    launcher.activeClient.let { currClient ->
      // As a client is already running, re-enabling the launcher should be a no-op
      launcher.enabled = true
      assertThat(launcher.activeClient).isSameAs(currClient)
    }

    // If disabled, new process won't be launched until the launcher is re-enabled again.
    processes.stop()
    launcher.enabled = false
    notifier.fireConnected(process2)
    assertThat(launcher.activeClient).isInstanceOf(DisconnectedClient::class.java)
    launcher.enabled = true
    assertThat(launcher.activeClient.process).isSameAs(process2)

    // When re-enabling and finding a dead process, launcher tries to find same version of the live process
    launcher.enabled = false
    assertThat(launcher.activeClient).isNotInstanceOf(DisconnectedClient::class.java)
    processes.stop() // Stops inspecting process2, but it is still in the processes list
    assertThat(launcher.activeClient).isInstanceOf(DisconnectedClient::class.java)
    launcher.enabled = true
    assertThat(launcher.activeClient.process).isSameAs(process2)

    // .. but it gives up if it can't find a live process with the same PID
    launcher.enabled = false
    processes.selectedProcess = deadProcess3 // This emulates process3 having been stopped on the device
    assertThat(launcher.activeClient).isInstanceOf(DisconnectedClient::class.java)
    launcher.enabled = true
    assertThat(launcher.activeClient).isInstanceOf(DisconnectedClient::class.java)
  }
}