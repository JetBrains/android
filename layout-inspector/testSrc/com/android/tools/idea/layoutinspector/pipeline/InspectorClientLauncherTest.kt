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
import com.android.testutils.MockitoKt.mock
import com.android.testutils.waitForCondition
import com.android.tools.idea.appinspection.api.process.ProcessesModel
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.android.tools.idea.appinspection.test.TestProcessDiscovery
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.layoutinspector.AdbServiceRule
import com.android.tools.idea.layoutinspector.LEGACY_DEVICE
import com.android.tools.idea.layoutinspector.MODERN_DEVICE
import com.android.tools.idea.layoutinspector.createProcess
import com.android.tools.idea.layoutinspector.metrics.LayoutInspectorSessionMetrics
import com.android.tools.idea.layoutinspector.model.NotificationModel
import com.android.tools.idea.layoutinspector.pipeline.adb.FakeShellCommandHandler
import com.android.tools.idea.layoutinspector.properties.PropertiesProvider
import com.android.tools.idea.layoutinspector.util.ReportingCountDownLatch
import com.android.tools.idea.metrics.MetricsTrackerRule
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorAttachToProcess.ClientType
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorEvent
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.ProjectRule
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

class InspectorClientLauncherTest {
  private val disposableRule = DisposableRule()
  private val projectRule = ProjectRule()
  private val adbRule = FakeAdbRule().withDeviceCommandHandler(FakeShellCommandHandler())
  private val adbService = AdbServiceRule(projectRule::project, adbRule)

  @get:Rule
  val ruleChain =
    RuleChain.outerRule(projectRule).around(disposableRule).around(adbRule).around(adbService)!!

  @Before
  fun before() {
    for (device in setOf(MODERN_DEVICE, LEGACY_DEVICE)) {
      adbRule.attachDevice(
        device.serial,
        device.manufacturer,
        device.model,
        device.version,
        device.apiLevel.toString(),
      )
    }
  }

  @Test
  fun initialInspectorLauncherStartsWithDisconnectedClient() {
    val processes = ProcessesModel(TestProcessDiscovery())
    val launcher =
      InspectorClientLauncher(
        processes,
        listOf(),
        projectRule.project,
        NotificationModel(projectRule.project),
        AndroidCoroutineScope(disposableRule.disposable),
        disposableRule.disposable,
        metrics = mock(),
        executor = MoreExecutors.directExecutor(),
      )

    assertThat(launcher.activeClient).isInstanceOf(DisconnectedClient::class.java)
  }

  @Test
  fun emptyInspectorLauncherIgnoresProcessChanges() {
    val processes = ProcessesModel(TestProcessDiscovery())
    val launcher =
      InspectorClientLauncher(
        processes,
        listOf(),
        projectRule.project,
        NotificationModel(projectRule.project),
        AndroidCoroutineScope(disposableRule.disposable),
        disposableRule.disposable,
        metrics = mock(),
        executor = MoreExecutors.directExecutor(),
      )

    var clientChangedCount = 0
    launcher.addClientChangedListener { clientChangedCount++ }

    processes.selectedProcess = MODERN_DEVICE.createProcess()

    assertThat(launcher.activeClient).isInstanceOf(DisconnectedClient::class.java)
    assertThat(clientChangedCount).isEqualTo(0)
  }

  @Test
  fun inspectorLauncherWithNoMatchReturnsDisconnectedClient() {
    val processes = ProcessesModel(TestProcessDiscovery())
    val launcher =
      InspectorClientLauncher(
        processes,
        listOf(
          ClientFactory { params ->
            if (params.process.device.apiLevel == MODERN_DEVICE.apiLevel)
              FakeInspectorClient(
                "Modern client",
                projectRule.project,
                params.process,
                disposableRule.disposable,
              )
            else null
          }
        ),
        projectRule.project,
        NotificationModel(projectRule.project),
        AndroidCoroutineScope(disposableRule.disposable),
        disposableRule.disposable,
        metrics = mock(),
        executor = MoreExecutors.directExecutor(),
      )

    assertThat(launcher.activeClient).isInstanceOf(DisconnectedClient::class.java)
    assertThat(processes.selectedProcess).isNull()

    processes.selectedProcess = MODERN_DEVICE.createProcess()
    assertThat(launcher.activeClient).isInstanceOf(FakeInspectorClient::class.java)

    processes.selectedProcess = LEGACY_DEVICE.createProcess()
    assertThat(launcher.activeClient).isInstanceOf(DisconnectedClient::class.java)
    assertThat(processes.selectedProcess).isNull()
  }

  @Test
  fun disposingLauncherDisconnectsAndDisposesActiveClient() {
    val processes = ProcessesModel(TestProcessDiscovery())

    val launcherDisposable = Disposer.newDisposable()
    var clientWasDisconnected = false
    val disconnectLatch = CountDownLatch(1)
    val launcher =
      InspectorClientLauncher(
        processes,
        listOf(
          ClientFactory { params ->
            val client =
              FakeInspectorClient(
                "Client",
                projectRule.project,
                params.process,
                disposableRule.disposable,
              )
            client.registerStateCallback { state ->
              if (state == InspectorClient.State.DISCONNECTED) {
                clientWasDisconnected = true
                disconnectLatch.countDown()
              }
            }
            client
          }
        ),
        projectRule.project,
        NotificationModel(projectRule.project),
        AndroidCoroutineScope(disposableRule.disposable),
        launcherDisposable,
        metrics = mock(),
        executor = MoreExecutors.directExecutor(),
      )

    processes.selectedProcess = MODERN_DEVICE.createProcess()
    assertThat(launcher.activeClient.isConnected).isTrue()

    assertThat(clientWasDisconnected).isFalse()
    Disposer.dispose(launcherDisposable)
    disconnectLatch.await(10, TimeUnit.SECONDS)
    assertThat(clientWasDisconnected).isTrue()
    assertThat(launcher.activeClient.isConnected).isFalse()
  }

  @Test
  fun inspectorLauncherUsesFirstMatchingClient() {
    val processes = ProcessesModel(TestProcessDiscovery())

    var creatorCount1 = 0
    var creatorCount2 = 0
    var creatorCount3 = 0

    val launcher =
      InspectorClientLauncher(
        processes,
        listOf(
          ClientFactory { params ->
            creatorCount1++
            if (params.process.device.apiLevel == MODERN_DEVICE.apiLevel)
              FakeInspectorClient(
                "Modern client",
                projectRule.project,
                params.process,
                disposableRule.disposable,
              )
            else null
          },
          ClientFactory { params ->
            creatorCount2++
            if (params.process.device.apiLevel == LEGACY_DEVICE.apiLevel)
              FakeInspectorClient(
                "Legacy client",
                projectRule.project,
                params.process,
                disposableRule.disposable,
              )
            else null
          },
          ClientFactory { params ->
            creatorCount3++
            FakeInspectorClient(
              "Fallback client",
              projectRule.project,
              params.process,
              disposableRule.disposable,
            )
          },
        ),
        projectRule.project,
        NotificationModel(projectRule.project),
        AndroidCoroutineScope(disposableRule.disposable),
        disposableRule.disposable,
        metrics = mock(),
        executor = MoreExecutors.directExecutor(),
      )

    var clientChangedCount = 0
    launcher.addClientChangedListener { ++clientChangedCount }

    assertThat(launcher.activeClient.isConnected).isFalse()

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
    val processes = ProcessesModel(TestProcessDiscovery())

    val launcher =
      InspectorClientLauncher(
        processes,
        listOf(
          ClientFactory { params ->
            object :
              FakeInspectorClient(
                "Exploding client #1",
                projectRule.project,
                params.process,
                disposableRule.disposable,
              ) {
              override suspend fun doConnect() = throw IllegalStateException()
            }
          },
          ClientFactory { params ->
            object :
              FakeInspectorClient(
                "Exploding client #2",
                projectRule.project,
                params.process,
                disposableRule.disposable,
              ) {
              override suspend fun doConnect() = throw IllegalStateException()
            }
          },
          ClientFactory { params ->
            FakeInspectorClient(
              "Fallback client",
              projectRule.project,
              params.process,
              disposableRule.disposable,
            )
          },
        ),
        projectRule.project,
        NotificationModel(projectRule.project),
        AndroidCoroutineScope(disposableRule.disposable),
        disposableRule.disposable,
        metrics = mock(),
        executor = MoreExecutors.directExecutor(),
      )

    processes.selectedProcess = MODERN_DEVICE.createProcess()
    (launcher.activeClient as FakeInspectorClient).let { activeClient ->
      assertThat(activeClient.name).isEqualTo("Fallback client")
    }
  }

  @Test
  fun inspectorLauncherWithNoSuccessfulConnectionsReturnsDisconnectedClient() {
    val processes = ProcessesModel(TestProcessDiscovery())

    val launcher =
      InspectorClientLauncher(
        processes,
        listOf(
          ClientFactory { params ->
            object :
              FakeInspectorClient(
                "Exploding client #1",
                projectRule.project,
                params.process,
                disposableRule.disposable,
              ) {
              override suspend fun doConnect() = throw IllegalStateException()
            }
          },
          ClientFactory { params ->
            object :
              FakeInspectorClient(
                "Exploding client #2",
                projectRule.project,
                params.process,
                disposableRule.disposable,
              ) {
              override suspend fun doConnect() = throw IllegalStateException()
            }
          },
          ClientFactory { params ->
            if (params.process.device.apiLevel >= MODERN_DEVICE.apiLevel) {
              FakeInspectorClient(
                "Modern client",
                projectRule.project,
                params.process,
                disposableRule.disposable,
              )
            } else {
              null
            }
          },
        ),
        projectRule.project,
        NotificationModel(projectRule.project),
        AndroidCoroutineScope(disposableRule.disposable),
        disposableRule.disposable,
        metrics = mock(),
        executor = MoreExecutors.directExecutor(),
      )

    // Set to a valid client first, so we know we actually changed correctly to a disconnected
    // client later.
    processes.selectedProcess = MODERN_DEVICE.createProcess()
    (launcher.activeClient as FakeInspectorClient).let { activeClient ->
      assertThat(activeClient.name).isEqualTo("Modern client")
    }

    processes.selectedProcess = LEGACY_DEVICE.createProcess()
    assertThat(launcher.activeClient).isInstanceOf(DisconnectedClient::class.java)
    assertThat(processes.selectedProcess).isNull()
  }

  @Test
  fun inspectorLauncherCanBeDisabledAndReenabled() {
    val process1 = MODERN_DEVICE.createProcess(pid = 1)
    val process2 = MODERN_DEVICE.createProcess(pid = 2)
    val deadProcess3 = MODERN_DEVICE.createProcess(pid = 3, isRunning = false)

    val notifier = TestProcessDiscovery()
    val processes =
      ProcessesModel(notifier) {
        it.name == process1.name
      } // Note: This covers all processes as they have the same name
    val launcher =
      InspectorClientLauncher(
        processes,
        listOf(
          ClientFactory { params ->
            FakeInspectorClient(
              "Unused",
              projectRule.project,
              params.process,
              disposableRule.disposable,
            )
          }
        ),
        projectRule.project,
        NotificationModel(projectRule.project),
        AndroidCoroutineScope(disposableRule.disposable),
        disposableRule.disposable,
        metrics = mock(),
        executor = MoreExecutors.directExecutor(),
      )

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

    // When re-enabling and finding a dead process, launcher tries to find same version of the live
    // process
    launcher.enabled = false
    assertThat(launcher.activeClient).isNotInstanceOf(DisconnectedClient::class.java)
    processes.stop() // Stops inspecting process2, but it is still in the processes list
    assertThat(launcher.activeClient).isInstanceOf(DisconnectedClient::class.java)
    launcher.enabled = true
    assertThat(launcher.activeClient.process).isSameAs(process2)

    // ... but it gives up if it can't find a live process with the same PID
    launcher.enabled = false
    processes.selectedProcess =
      deadProcess3 // This emulates process3 having been stopped on the device
    assertThat(launcher.activeClient).isInstanceOf(DisconnectedClient::class.java)
    launcher.enabled = true
    assertThat(launcher.activeClient).isInstanceOf(DisconnectedClient::class.java)
  }

  @Test
  fun launcherStopsAfterNewRequest() {
    val processes = ProcessesModel(TestProcessDiscovery())
    val process1 = MODERN_DEVICE.createProcess(pid = 1)
    val process2 = MODERN_DEVICE.createProcess(pid = 2)

    val firstClientStarted = ReportingCountDownLatch(1)
    val secondClientStarted = ReportingCountDownLatch(1)
    val successfulClientStarted = ReportingCountDownLatch(1)
    var failureMessage: String? = null
    var successfulClient: InspectorClient? = null
    val launcher =
      InspectorClientLauncher(
        processes,
        listOf(
          ClientFactory { params ->
            object :
              FakeInspectorClient(
                "Initial failing client",
                projectRule.project,
                params.process,
                disposableRule.disposable,
              ) {
              override suspend fun doConnect() {
                if (process == process1) {
                  firstClientStarted.countDown()
                  secondClientStarted.await(2, TimeUnit.SECONDS)
                } else {
                  secondClientStarted.countDown()
                }
                throw IllegalStateException()
              }

              override fun toString() = "Initial client for pid ${process.pid}"
            }
          },
          ClientFactory { params ->
            object :
              FakeInspectorClient(
                "Only connect to process 2",
                projectRule.project,
                params.process,
                disposableRule.disposable,
              ) {
              override suspend fun doConnect() {
                if (process == process1) {
                  failureMessage = "First connection shouldn't get to second creator"
                  throw IllegalStateException()
                } else {
                  successfulClient = this
                  successfulClientStarted.countDown()
                }
              }

              override fun toString() = "Second client for pid ${process.pid}"
            }
          },
        ),
        projectRule.project,
        NotificationModel(projectRule.project),
        AndroidCoroutineScope(disposableRule.disposable),
        disposableRule.disposable,
        metrics = mock(),
      )

    processes.selectedProcess = process1
    firstClientStarted.await(2, TimeUnit.SECONDS)
    processes.selectedProcess = process2
    successfulClientStarted.await(2, TimeUnit.SECONDS)

    assertThat(failureMessage).isNull()
    assertThat(launcher.activeClient).isEqualTo(successfulClient)
    assertThat(processes.selectedProcess).isEqualTo(process2)
  }

  @Test
  fun launchJobIsCancelled() {
    val processes = ProcessesModel(TestProcessDiscovery())
    val process1 = MODERN_DEVICE.createProcess(pid = 1)
    val process2 = MODERN_DEVICE.createProcess(pid = 2)

    val firstProcessLatch = ReportingCountDownLatch(1)
    val secondProcessLatch = ReportingCountDownLatch(1)

    val clientFactory = ClientFactory { params ->
      object :
        FakeInspectorClient(
          "First Client",
          projectRule.project,
          params.process,
          disposableRule.disposable,
        ) {
        override suspend fun doConnect() {
          when (params.process) {
            process1 -> {
              // Notify that we're starting the connection process
              firstProcessLatch.countDown()
              // Fake connection time
              delay(10000)
            }
            process2 -> secondProcessLatch.countDown()
            else -> throw IllegalArgumentException("Unexpected process: ${params.process}")
          }
        }
      }
    }

    val launcher =
      InspectorClientLauncher(
        processes,
        listOf(clientFactory),
        projectRule.project,
        NotificationModel(projectRule.project),
        AndroidCoroutineScope(disposableRule.disposable),
        disposableRule.disposable,
        metrics = mock(),
      )

    processes.selectedProcess = process1
    // Await for connection to start
    firstProcessLatch.await(2, TimeUnit.SECONDS)
    val client1LaunchJob = launcher.launchJob

    // Connecting the new process should cancel the previous launch
    processes.selectedProcess = process2
    secondProcessLatch.await(2, TimeUnit.SECONDS)
    val client2LaunchJob = launcher.launchJob

    assertThat(client1LaunchJob).isNotEqualTo(client2LaunchJob)
    assertThat(client1LaunchJob?.isCancelled).isTrue()
    assertThat(client2LaunchJob?.isCancelled).isFalse()
  }
}

class InspectorClientLauncherMetricsTest {

  @get:Rule val usageTrackerRule = MetricsTrackerRule()

  private val disposableRule = DisposableRule()
  private val projectRule = ProjectRule()
  private val adbRule = FakeAdbRule().withDeviceCommandHandler(FakeShellCommandHandler())
  private val adbService = AdbServiceRule(projectRule::project, adbRule)

  @get:Rule
  val ruleChain =
    RuleChain.outerRule(projectRule).around(disposableRule).around(adbRule).around(adbService)!!

  @Before
  fun before() {
    val device = MODERN_DEVICE
    adbRule.attachDevice(
      device.serial,
      device.manufacturer,
      device.model,
      device.version,
      device.apiLevel.toString(),
    )
  }

  @Test
  fun attachRequestOnlyLoggedOnce() {
    val processes = ProcessesModel(TestProcessDiscovery())
    val metrics = LayoutInspectorSessionMetrics(projectRule.project)
    val launcher =
      InspectorClientLauncher(
        processes,
        listOf(
          ClientFactory { params ->
            object :
              FakeInspectorClient(
                "Exploding client #1",
                projectRule.project,
                params.process,
                disposableRule.disposable,
              ) {
              override suspend fun doConnect() {
                metrics.logEvent(
                  DynamicLayoutInspectorEvent.DynamicLayoutInspectorEventType.ATTACH_REQUEST,
                  stats,
                )
                throw IllegalStateException()
              }
            }
          },
          ClientFactory { params ->
            object :
              FakeInspectorClient(
                "Exploding client #2",
                projectRule.project,
                params.process,
                disposableRule.disposable,
              ) {
              override suspend fun doConnect() {
                metrics.logEvent(
                  DynamicLayoutInspectorEvent.DynamicLayoutInspectorEventType.COMPATIBILITY_REQUEST,
                  stats,
                )
                throw IllegalStateException()
              }
            }
          },
          ClientFactory { params ->
            object :
              FakeInspectorClient(
                "Fallback client",
                projectRule.project,
                params.process,
                disposableRule.disposable,
              ) {
              override suspend fun doConnect() {
                metrics.logEvent(
                  DynamicLayoutInspectorEvent.DynamicLayoutInspectorEventType.COMPATIBILITY_REQUEST,
                  stats,
                )
                metrics.logEvent(
                  DynamicLayoutInspectorEvent.DynamicLayoutInspectorEventType.COMPATIBILITY_SUCCESS,
                  stats,
                )
              }
            }
          },
        ),
        projectRule.project,
        NotificationModel(projectRule.project),
        AndroidCoroutineScope(disposableRule.disposable),
        disposableRule.disposable,
        metrics,
        MoreExecutors.directExecutor(),
      )

    processes.selectedProcess = MODERN_DEVICE.createProcess()
    waitForCondition(1, TimeUnit.SECONDS) { launcher.activeClient.isConnected }
    val usages =
      usageTrackerRule.testTracker.usages.filter {
        it.studioEvent.kind == AndroidStudioEvent.EventKind.DYNAMIC_LAYOUT_INSPECTOR_EVENT
      }

    assertThat(usages).hasSize(2)
    // ATTACH_REQUEST should be logged only once
    assertThat(usages[0].studioEvent.dynamicLayoutInspectorEvent.type)
      .isEqualTo(DynamicLayoutInspectorEvent.DynamicLayoutInspectorEventType.ATTACH_REQUEST)
    assertThat(usages[1].studioEvent.dynamicLayoutInspectorEvent.type)
      .isEqualTo(DynamicLayoutInspectorEvent.DynamicLayoutInspectorEventType.COMPATIBILITY_SUCCESS)
  }

  @Test
  fun attachCancelLogged() {
    val process1 = MODERN_DEVICE.createProcess()
    val process2 = MODERN_DEVICE.createProcess(pid = 2)
    val processes = ProcessesModel(TestProcessDiscovery())
    val metrics = LayoutInspectorSessionMetrics(projectRule.project)
    val changedProcessLatch = ReportingCountDownLatch(1)
    val startedWaitingLatch = ReportingCountDownLatch(1)
    InspectorClientLauncher(
      processes,
      listOf(
        ClientFactory { params ->
          object :
            FakeInspectorClient(
              "Hangs on initial connect",
              projectRule.project,
              params.process,
              disposableRule.disposable,
            ) {
            override suspend fun doConnect() {
              metrics.logEvent(
                DynamicLayoutInspectorEvent.DynamicLayoutInspectorEventType.ATTACH_REQUEST,
                stats,
              )
              if (params.process == process1) {
                startedWaitingLatch.countDown()
                changedProcessLatch.await(1, TimeUnit.SECONDS)
              }
            }
          }
        }
      ),
      projectRule.project,
      NotificationModel(projectRule.project),
      AndroidCoroutineScope(disposableRule.disposable),
      disposableRule.disposable,
      metrics,
    )

    processes.selectedProcess = process1
    startedWaitingLatch.await(1, TimeUnit.SECONDS)
    processes.selectedProcess = process2
    changedProcessLatch.countDown()

    waitForCondition(1, TimeUnit.SECONDS) {
      usageTrackerRule.testTracker.usages.count {
        it.studioEvent.kind == AndroidStudioEvent.EventKind.DYNAMIC_LAYOUT_INSPECTOR_EVENT
      } == 3
    }

    val usages =
      usageTrackerRule.testTracker.usages.filter {
        it.studioEvent.kind == AndroidStudioEvent.EventKind.DYNAMIC_LAYOUT_INSPECTOR_EVENT
      }
    assertThat(usages).hasSize(3)
    assertThat(usages[0].studioEvent.dynamicLayoutInspectorEvent.type)
      .isEqualTo(DynamicLayoutInspectorEvent.DynamicLayoutInspectorEventType.ATTACH_REQUEST)
    val otherUsages = usages.subList(1, 3).map { it.studioEvent.dynamicLayoutInspectorEvent.type }
    assertThat(otherUsages)
      .containsExactly(
        DynamicLayoutInspectorEvent.DynamicLayoutInspectorEventType.ATTACH_CANCELLED,
        DynamicLayoutInspectorEvent.DynamicLayoutInspectorEventType.ATTACH_REQUEST,
      )
  }
}

private open class FakeInspectorClient(
  val name: String,
  project: Project,
  process: ProcessDescriptor,
  parentDisposable: Disposable,
) :
  AbstractInspectorClient(
    ClientType.UNKNOWN_CLIENT_TYPE,
    project,
    NotificationModel(project),
    process,
    DisconnectedClient.stats,
    AndroidCoroutineScope(parentDisposable),
    parentDisposable,
  ) {

  override suspend fun startFetching() = throw NotImplementedError()

  override suspend fun stopFetching() = throw NotImplementedError()

  override fun refresh() = throw NotImplementedError()

  override suspend fun saveSnapshot(path: Path) = throw NotImplementedError()

  override suspend fun doConnect() {}

  override suspend fun doDisconnect() {}

  override val capabilities
    get() = throw NotImplementedError()

  override val treeLoader: TreeLoader
    get() = throw NotImplementedError()

  override val inLiveMode: Boolean
    get() = false

  override val provider: PropertiesProvider
    get() = throw NotImplementedError()
}
