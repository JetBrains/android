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
package com.android.tools.idea.appinspection.api.process

import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.android.tools.idea.appinspection.internal.process.TransportProcessDescriptor
import com.android.tools.idea.appinspection.test.TestProcessDiscovery
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.profiler.proto.Common
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ProcessesModelTest {
  private fun createFakeStream(): Common.Stream {
    return Common.Stream.newBuilder().setDevice(FakeTransportService.FAKE_DEVICE).build()
  }

  private fun Common.Stream.createFakeProcess(
    name: String? = null,
    pid: Int = 0
  ): ProcessDescriptor {
    return TransportProcessDescriptor(
      this,
      FakeTransportService.FAKE_PROCESS.toBuilder()
        .setName(name ?: FakeTransportService.FAKE_PROCESS_NAME)
        .setPid(pid)
        .build()
    )
  }

  @Test
  fun processConnectedDisconnected_modelUpdatesProperly() {
    val testNotifier = TestProcessDiscovery()
    val model = ProcessesModel(testNotifier) { it.name == FakeTransportService.FAKE_PROCESS.name }

    val fakeStream = createFakeStream()
    val fakeProcess = fakeStream.createFakeProcess()

    testNotifier.fireConnected(fakeProcess)
    // Verify the added target.
    assertThat(model.processes.size).isEqualTo(1)
    with(model.processes.first()) {
      assertThat(this.device.model).isEqualTo(FakeTransportService.FAKE_DEVICE.model)
      assertThat(this.name).isEqualTo(FakeTransportService.FAKE_PROCESS.name)
    }
    val selectedName = model.selectedProcess!!.name
    model.selectedProcess!!.let { selectedProcess ->
      assertThat(selectedProcess.isRunning).isTrue()
      assertThat(model.isProcessPreferred(selectedProcess)).isTrue()
    }

    testNotifier.fireDisconnected(fakeProcess)

    // Once disconnected, the process remains but in a terminated state
    assertThat(model.processes.size).isEqualTo(1)
    model.selectedProcess!!.let { selectedProcess ->
      assertThat(selectedProcess.name).isEqualTo(selectedName)
      assertThat(selectedProcess.isRunning).isFalse()
      assertThat(model.isProcessPreferred(selectedProcess)).isFalse()
      assertThat(model.isProcessPreferred(selectedProcess, includeDead = true)).isTrue()
    }

    // Updating the selected process removes any dead processes
    model.selectedProcess = null
    assertThat(model.processes).isEmpty()
  }

  @Test
  fun canSetSelectedProcessDirectly() {
    val testNotifier = TestProcessDiscovery()
    val model = ProcessesModel(testNotifier) { it.name == "A" }

    val fakeStream = createFakeStream()
    val fakeProcessA = fakeStream.createFakeProcess("A", 100)
    val fakeProcessB = fakeStream.createFakeProcess("B", 101)

    testNotifier.fireConnected(fakeProcessA)
    testNotifier.fireConnected(fakeProcessB)
    assertThat(model.selectedProcess)
      .isSameAs(fakeProcessA) // Because fakeProcessB is not preferred

    model.selectedProcess = fakeProcessB
    assertThat(model.selectedProcess).isSameAs(fakeProcessB)
  }

  @Test
  fun modelListenerFiredOnProcessChanged() {
    val testNotifier = TestProcessDiscovery()
    val model = ProcessesModel(testNotifier) { it.name in listOf("A", "B") }

    val fakeStream = createFakeStream()
    val fakeProcessA = fakeStream.createFakeProcess("A", 100)
    val fakeProcessB = fakeStream.createFakeProcess("B", 101)

    var processedChangedCount = 0
    model.addSelectedProcessListeners { processedChangedCount++ }

    assertThat(processedChangedCount).isEqualTo(0)

    testNotifier.fireConnected(fakeProcessA)
    assertThat(processedChangedCount).isEqualTo(1)

    testNotifier.fireDisconnected(fakeProcessA)
    assertThat(processedChangedCount).isEqualTo(2)

    testNotifier.fireConnected(fakeProcessB)
    assertThat(processedChangedCount).isEqualTo(3)

    testNotifier.fireDisconnected(fakeProcessB)
    assertThat(processedChangedCount).isEqualTo(4)
  }

  @Test
  fun modelPrioritizesPreferredProcess() {
    val testNotifier = TestProcessDiscovery()
    val model = ProcessesModel(testNotifier) { it.name == "preferred" }

    val fakeStream = createFakeStream()

    val nonPreferredProcess = fakeStream.createFakeProcess("non-preferred", 100)
    val preferredProcess = fakeStream.createFakeProcess("preferred", 101)

    testNotifier.fireConnected(nonPreferredProcess)
    testNotifier.fireConnected(preferredProcess)

    // Verify the added target.
    assertThat(model.processes.size).isEqualTo(2)
    assertThat(model.selectedProcess!!).isSameAs(preferredProcess)
  }

  @Test
  fun newProcessDoesNotCauseSelectionToChange() {
    val testNotifier = TestProcessDiscovery()
    val model = ProcessesModel(testNotifier) { it.name in listOf("A", "B") }

    val fakeStream = createFakeStream()
    val fakeProcessA = fakeStream.createFakeProcess("A", 100)
    val fakeProcessB = fakeStream.createFakeProcess("B", 101)

    testNotifier.fireConnected(fakeProcessA)
    testNotifier.fireConnected(fakeProcessB)
    // Verify the added target.
    assertThat(model.processes.size).isEqualTo(2)
    assertThat(model.selectedProcess!!).isSameAs(fakeProcessA)
  }

  @Test
  fun noPreferredProcesses_noSelection() {
    val testNotifier = TestProcessDiscovery()
    val model = ProcessesModel(testNotifier) { false }

    val fakeStream = createFakeStream()
    val fakeProcessA = fakeStream.createFakeProcess("A")

    testNotifier.fireConnected(fakeProcessA)
    // Verify the added target.
    assertThat(model.processes.size).isEqualTo(1)
    assertThat(model.selectedProcess).isNull()
  }

  @Test
  fun stop() {
    val testNotifier = TestProcessDiscovery()
    val model = ProcessesModel(testNotifier) { it.name in listOf("A", "B") }

    val fakeStream = createFakeStream()
    val fakeProcessA = fakeStream.createFakeProcess("A", 100)
    val fakeProcessB = fakeStream.createFakeProcess("B", 101)

    testNotifier.fireConnected(fakeProcessA)
    testNotifier.fireConnected(fakeProcessB)

    // Select a process normally
    model.selectedProcess = fakeProcessB
    assertThat(model.selectedProcess).isSameAs(fakeProcessB)

    // Stop inspection and check the new selected process is dead
    model.stop()
    val deadProcess = model.selectedProcess!!
    assertThat(deadProcess.name).isEqualTo(fakeProcessB.name)
    assertThat(deadProcess.isRunning).isFalse()

    // Restart a new process by changing the selected process
    model.selectedProcess = fakeProcessB
    assertThat(model.selectedProcess).isNotSameAs(deadProcess)
    assertThat(model.selectedProcess).isSameAs(fakeProcessB)
  }

  @Test
  fun processFilteringWorks() {
    val testNotifier = TestProcessDiscovery()

    val fakeStream = createFakeStream()
    val fakeProcessA = fakeStream.createFakeProcess("A")
    val fakeProcessB = fakeStream.createFakeProcess("B")
    val fakeProcessC = fakeStream.createFakeProcess("C")
    val fakeProcessD = fakeStream.createFakeProcess("D")

    val model =
      ProcessesModel(
        processDiscovery = testNotifier,
        acceptProcess = { it === fakeProcessB || it === fakeProcessD },
        isPreferred = { it.name == FakeTransportService.FAKE_PROCESS.name }
      )

    testNotifier.fireConnected(fakeProcessA)
    testNotifier.fireConnected(fakeProcessB)
    testNotifier.fireConnected(fakeProcessC)
    testNotifier.fireConnected(fakeProcessD)
    assertThat(model.processes.map { it.name }).containsExactly("B", "D")

    testNotifier.fireDisconnected(fakeProcessA)
    assertThat(model.processes.filter { it.isRunning }.map { it.name }).containsExactly("B", "D")
    testNotifier.fireDisconnected(fakeProcessB)
    assertThat(model.processes.filter { it.isRunning }.map { it.name }).containsExactly("D")
    testNotifier.fireDisconnected(fakeProcessC)
    assertThat(model.processes.filter { it.isRunning }.map { it.name }).containsExactly("D")
    testNotifier.fireDisconnected(fakeProcessD)
    assertThat(model.processes.filter { it.isRunning }.map { it.name }).isEmpty()
  }
}
