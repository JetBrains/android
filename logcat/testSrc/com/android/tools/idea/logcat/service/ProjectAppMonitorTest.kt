/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.logcat.service

import com.android.processmonitor.common.ProcessEvent.ProcessAdded
import com.android.processmonitor.common.ProcessEvent.ProcessRemoved
import com.android.processmonitor.monitor.testing.FakeProcessNameMonitor
import com.android.tools.idea.logcat.FakePackageNamesProvider
import com.android.tools.idea.logcat.LogcatBundle
import com.android.tools.idea.logcat.SYSTEM_HEADER
import com.android.tools.idea.logcat.message.LogcatMessage
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * Tests for [ProjectAppMonitor]
 */
class ProjectAppMonitorTest {
  private val fakeProcessNameMonitor = FakeProcessNameMonitor()

  private val fakePackageNamesProvider = FakePackageNamesProvider("com.example.app1", "com.example.app2")

  @Test
  fun addProcesses(): Unit = runBlocking {
    val fakeProcessTracker = fakeProcessNameMonitor.getProcessTracker("device1")
    fakeProcessTracker.send(ProcessAdded(1, "com.example.app1", "com.example.app1"))
    fakeProcessTracker.send(ProcessAdded(2, "com.example.app2", "com.example.app2"))
    fakeProcessTracker.send(ProcessAdded(3, "com.example.not-my-app", "com.example.not-my-app"))
    val monitor = ProjectAppMonitor(fakeProcessNameMonitor, fakePackageNamesProvider)

    val messages = async { monitor.monitorDevice("device1").toList() }
    fakeProcessTracker.close()

    assertThat(messages.await()).containsExactly(
      startedMessage(1, "com.example.app1"),
      startedMessage(2, "com.example.app2"),
    ).inOrder()
  }

  @Test
  fun addProcesses_withNullApplicationId(): Unit = runBlocking {
    val fakeProcessTracker = fakeProcessNameMonitor.getProcessTracker("device1")
    fakeProcessTracker.send(ProcessAdded(1, null, "com.example.app1"))
    val monitor = ProjectAppMonitor(fakeProcessNameMonitor, fakePackageNamesProvider)

    val messages = async { monitor.monitorDevice("device1").toList() }
    fakeProcessTracker.close()

    assertThat(messages.await()).containsExactly(
      startedMessage(1, "com.example.app1"),
    ).inOrder()
  }

  @Test
  fun addProcesses_withDerivedApplicationId(): Unit = runBlocking {
    val fakeProcessTracker = fakeProcessNameMonitor.getProcessTracker("device1")
    fakeProcessTracker.send(ProcessAdded(1, null, "com.example.app1:service"))
    val monitor = ProjectAppMonitor(fakeProcessNameMonitor, fakePackageNamesProvider)

    val messages = async { monitor.monitorDevice("device1").toList() }
    fakeProcessTracker.close()

    assertThat(messages.await()).containsExactly(
      startedMessage(1, "com.example.app1"),
    ).inOrder()
  }

  @Test
  fun addProcesses_thenRemove(): Unit = runBlocking {
    val fakeProcessTracker = fakeProcessNameMonitor.getProcessTracker("device1")
    fakeProcessTracker.send(ProcessAdded(1, "com.example.app1", "com.example.app1"))
    fakeProcessTracker.send(ProcessAdded(2, "com.example.app2", "com.example.app2"))
    fakeProcessTracker.send(ProcessAdded(3, "com.example.not-my-app", "com.example.not-my-app"))
    fakeProcessTracker.send(ProcessRemoved(1))
    fakeProcessTracker.send(ProcessRemoved(2))
    fakeProcessTracker.send(ProcessRemoved(3))
    val monitor = ProjectAppMonitor(fakeProcessNameMonitor, fakePackageNamesProvider)

    val messages = async { monitor.monitorDevice("device1").toList() }
    fakeProcessTracker.close()

    assertThat(messages.await()).containsExactly(
      startedMessage(1, "com.example.app1"),
      startedMessage(2, "com.example.app2"),
      endedMessage(1, "com.example.app1"),
      endedMessage(2, "com.example.app2"),
    ).inOrder()
  }

  @Test
  fun addAlreadyAddedProcess(): Unit = runBlocking {
    val fakeProcessTracker = fakeProcessNameMonitor.getProcessTracker("device1")
    fakeProcessTracker.send(ProcessAdded(1, "com.example.app1", "com.example.app1"))
    fakeProcessTracker.send(ProcessAdded(1, "com.example.app1", "com.example.app1"))
    val monitor = ProjectAppMonitor(fakeProcessNameMonitor, fakePackageNamesProvider)

    val messages = async { monitor.monitorDevice("device1").toList() }
    fakeProcessTracker.close()

    assertThat(messages.await()).containsExactly(
      startedMessage(1, "com.example.app1"),
    ).inOrder()
  }

  @Test
  fun removeAlreadyRemovedProcess(): Unit = runBlocking {
    val fakeProcessTracker = fakeProcessNameMonitor.getProcessTracker("device1")
    fakeProcessTracker.send(ProcessAdded(1, "com.example.app1", "com.example.app1"))
    fakeProcessTracker.send(ProcessRemoved(1))
    fakeProcessTracker.send(ProcessRemoved(1))
    val monitor = ProjectAppMonitor(fakeProcessNameMonitor, fakePackageNamesProvider)

    val messages = async { monitor.monitorDevice("device1").toList() }
    fakeProcessTracker.close()

    assertThat(messages.await()).containsExactly(
      startedMessage(1, "com.example.app1"),
      endedMessage(1, "com.example.app1"),
    ).inOrder()
  }

  @Test
  fun removeUnknownProcess(): Unit = runBlocking {
    val fakeProcessTracker = fakeProcessNameMonitor.getProcessTracker("device1")
    fakeProcessTracker.send(ProcessRemoved(1))
    val monitor = ProjectAppMonitor(fakeProcessNameMonitor, fakePackageNamesProvider)

    val messages = async { monitor.monitorDevice("device1").toList() }
    fakeProcessTracker.close()

    assertThat(messages.await()).isEmpty()
  }
}

private fun startedMessage(pid: Int, applicationId: String) =
  LogcatMessage(SYSTEM_HEADER, LogcatBundle.message("logcat.process.started", pid, applicationId))

private fun endedMessage(pid: Int, applicationId: String) =
  LogcatMessage(SYSTEM_HEADER, LogcatBundle.message("logcat.process.ended", pid, applicationId))