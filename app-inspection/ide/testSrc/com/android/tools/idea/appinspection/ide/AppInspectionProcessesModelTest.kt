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
package com.android.tools.idea.appinspection.ide

import com.android.tools.idea.appinspection.ide.model.AppInspectionProcessModel
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.android.tools.idea.appinspection.internal.process.TransportProcessDescriptor
import com.android.tools.idea.appinspection.test.TestProcessNotifier
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.profiler.proto.Common
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AppInspectionProcessesModelTest {
  private fun createFakeStream(): Common.Stream {
    return Common.Stream.newBuilder()
      .setDevice(FakeTransportService.FAKE_DEVICE)
      .build()
  }
  private fun Common.Stream.createFakeProcess(name: String? = null, pid: Int = 0): ProcessDescriptor {
    return TransportProcessDescriptor(this, FakeTransportService.FAKE_PROCESS.toBuilder()
      .setName(name ?: FakeTransportService.FAKE_PROCESS_NAME)
      .setPid(pid)
      .build())
  }

  @Test
  fun processConnectedDisconnected_modelUpdatesProperly() {
    val testNotifier = TestProcessNotifier()
    val model = AppInspectionProcessModel(testNotifier) { listOf(FakeTransportService.FAKE_PROCESS.name) }

    val fakeStream = createFakeStream()
    val fakeProcess = fakeStream.createFakeProcess()

    testNotifier.fireConnected(fakeProcess)
    // Verify the added target.
    assertThat(model.processes.size).isEqualTo(1)
    with(model.processes.first()) {
      assertThat(this.model).isEqualTo(FakeTransportService.FAKE_DEVICE.model)
      assertThat(this.processName).isEqualTo(FakeTransportService.FAKE_PROCESS.name)
    }
    val selectedName = model.selectedProcess!!.processName
    model.selectedProcess!!.let { selectedProcess ->
      assertThat(selectedProcess.isRunning).isTrue()
      assertThat(model.isProcessPreferred(selectedProcess)).isTrue()
    }

    testNotifier.fireDisconnected(fakeProcess)

    // Once disconnected, the process remains but in a terminated state
    assertThat(model.processes.size).isEqualTo(1)
    model.selectedProcess!!.let { selectedProcess ->
      assertThat(selectedProcess.processName).isEqualTo(selectedName)
      assertThat(selectedProcess.isRunning).isFalse()
      assertThat(model.isProcessPreferred(selectedProcess)).isFalse()
      assertThat(model.isProcessPreferred(selectedProcess, includeDead = true)).isTrue()
    }

    // Updating the selected process removes any dead processes
    model.setSelectedProcess(null)
    assertThat(model.processes).isEmpty()
  }

  @Test
  fun canSetSelectedProcessDirectly() {
    val testNotifier = TestProcessNotifier()
    val model = AppInspectionProcessModel(testNotifier) { listOf("A") }

    val fakeStream = createFakeStream()
    val fakeProcessA = fakeStream.createFakeProcess("A", 100)
    val fakeProcessB = fakeStream.createFakeProcess("B", 101)

    testNotifier.fireConnected(fakeProcessA)
    testNotifier.fireConnected(fakeProcessB)
    assertThat(model.selectedProcess).isSameAs(fakeProcessA) // Because fakeProcessB is not preferred

    model.setSelectedProcess(fakeProcessB)
    assertThat(model.selectedProcess).isSameAs(fakeProcessB)
  }

  @Test
  fun modelListenerFiredOnProcessChanged() {
    val testNotifier = TestProcessNotifier()
    val model = AppInspectionProcessModel(testNotifier) { listOf("A", "B") }

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
    val testNotifier = TestProcessNotifier()
    val model = AppInspectionProcessModel(testNotifier) { listOf("preferred") }

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
    val testNotifier = TestProcessNotifier()
    val model = AppInspectionProcessModel(testNotifier) { listOf("A", "B") }

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
    val testNotifier = TestProcessNotifier()
    val model = AppInspectionProcessModel(testNotifier) { emptyList() }

    val fakeStream = createFakeStream()
    val fakeProcessA = fakeStream.createFakeProcess("A")

    testNotifier.fireConnected(fakeProcessA)
    // Verify the added target.
    assertThat(model.processes.size).isEqualTo(1)
    assertThat(model.selectedProcess).isNull()
  }

  @Test
  fun stopAndStartInspection() {
    val testNotifier = TestProcessNotifier()
    val model = AppInspectionProcessModel(testNotifier) { listOf("A", "B") }

    val fakeStream = createFakeStream()
    val fakeProcessA = fakeStream.createFakeProcess("A", 100)
    val fakeProcessB = fakeStream.createFakeProcess("B", 101)

    testNotifier.fireConnected(fakeProcessA)
    testNotifier.fireConnected(fakeProcessB)

    // Select a process normally
    model.setSelectedProcess(fakeProcessB)
    assertThat(model.selectedProcess).isSameAs(fakeProcessB)

    // Stop inspection and check the new selected process is dead
    model.stopInspection(fakeProcessB)
    val deadProcess = model.selectedProcess
    assertThat(deadProcess!!.isRunning).isFalse()

    // Try to set selected process with isUserAction=false. Check selected process doesn't change.
    model.setSelectedProcess(fakeProcessA)
    assertThat(model.selectedProcess).isSameAs(deadProcess)

    // Set selected process with isUserAction=true. Check selected process changed.
    model.setSelectedProcess(fakeProcessA, isUserAction = true)
    assertThat(model.selectedProcess).isSameAs(fakeProcessA)
  }
}