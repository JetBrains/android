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
package com.android.tools.profilers

import com.android.tools.adtui.model.AspectObserver
import com.google.common.truth.Truth.assertThat
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class RecordingOptionsModelTest(configs: Array<RecordingOption>) {
  val observer = AspectObserver()
  val model = RecordingOptionsModel(BuiltIns, configs)

  @Test
  fun `cannot start concurrent recordings`() {
    assertThat(model.canStart()).isFalse()
    model.selectBuiltInOption(model.builtInOptions[0])
    assertThat(model.canStart()).isTrue()
    model.start()
    assertThat(model.isRecording).isTrue()
    assertThat(model.canStart()).isFalse()
  }

  @Test
  fun `cannot manually stop ongoing recording by default`() {
    model.selectBuiltInOption(model.builtInOptions[1])
    model.start()
    assertThat(model.isRecording).isTrue()
    assertThat(model.canStop()).isFalse()
  }

  @Test
  fun `can stop recording that has specific stop action`() {
    model.selectBuiltInOption(model.builtInOptions[2])
    model.start()
    assertThat(model.isRecording).isTrue()
    assertThat(model.canStop()).isTrue()
    model.stop()
    // Because setFinished() (which sets isRecording to false) is only called when the TraceStopStatus event
    // is received, isRecording should still be true.
    assertThat(model.isRecording).isTrue()
    // The value will eventually be false once a trace stop status even is received. This is verified by
    // other tests such as MainMemoryProfilerStageTest's testToggleNativeAllocationRecordingChangesIsRecordingState()
    // test as well as CpuProfilerStageTest's receivingTraceStopStatusEventSetsRecordingToFinished() test.
  }

  @Test
  fun `selecting builtin option updates current selection`() = model.builtInOptions.forEach {
    model.selectBuiltInOption(it)
    assertThat(model.selectedOption).isEqualTo(it)
  }

  @Test
  fun `selection within custom configs does not update current option until explicitly done so`() {
    assumeTrue(model.customConfigurationModel.size > 0)
    model.selectBuiltInOption(model.builtInOptions[0])
    model.customConfigurationModel.selectedItem = CustomConfigs[1]
    assertThat(model.selectedOption).isEqualTo(model.builtInOptions[0])
    model.selectCurrentCustomConfiguration()
    assertThat(model.selectedOption).isEqualTo(CustomConfigs[1])
  }

  @Test
  fun `starting and stopping recording performs the actions`() {
    var state = ""
    var recording = false
    val config = RecordingOption("My config", "My config desc", {state = "Recording"}, {state = "Attempted to stop"})
    model.customConfigurationModel.addElement(config)
    model.customConfigurationModel.selectedItem = config
    model.selectCurrentCustomConfiguration()
    model.addDependency(observer).onChange(RecordingOptionsModel.Aspect.RECORDING_CHANGED) {recording = model.isRecording }

    assertThat(state).isEqualTo("")
    model.start()
    assertThat(state).isEqualTo("Recording")
    assertThat(recording).isTrue()
    model.stop()
    assertThat(state).isEqualTo("Attempted to stop")
    // Because setFinished() (which sets isRecording to false) is only called when the TraceStopStatus event
    // is received, isRecording should still be true.
    assertThat(recording).isTrue()
    // The value will eventually be false once a trace stop status even is received. This is verified by
    // other tests such as MainMemoryProfilerStageTest's testToggleNativeAllocationRecordingChangesIsRecordingState()
    // test as well as CpuProfilerStageTest's receivingTraceStopStatusEventSetsRecordingToFinished() test.
  }

  @Test
  fun `setting recording finished triggers observers`() {
    var recording = false
    model.selectBuiltInOption(model.builtInOptions[0])
    model.addDependency(observer).onChange(RecordingOptionsModel.Aspect.RECORDING_CHANGED) { recording = model.isRecording }

    assertThat(recording).isFalse()
    model.start()
    assertThat(recording).isTrue()
    model.setFinished()
    assertThat(recording).isFalse()
  }

  @Test
  fun `changing config list triggers observers`() {
    assumeTrue(model.customConfigurationModel.size == 0)
    var calls = 0
    model.addDependency(observer).onChange(RecordingOptionsModel.Aspect.CONFIGURATIONS_EMPTINESS_CHANGED) {calls++}

    assertThat(calls).isEqualTo(0)
    model.customConfigurationModel.addElement(CustomConfigs[0])
    assertThat(calls).isEqualTo(1)
    model.customConfigurationModel.addElement(CustomConfigs[1])
    assertThat(calls).isEqualTo(1)
    model.customConfigurationModel.removeElementAt(0)
    assertThat(calls).isEqualTo(1)
    model.customConfigurationModel.removeElementAt(0)
    assertThat(calls).isEqualTo(2)
  }

  @Test
  fun `custom configurations select first one by default`() {
    assumeTrue(model.customConfigurationModel.size == 0)
    model.customConfigurationModel.addElement(CustomConfigs[0])
    model.customConfigurationModel.addElement(CustomConfigs[1])
    assertThat(model.customConfigurationModel.selectedItem).isEqualTo(CustomConfigs[0])
    model.selectCurrentCustomConfiguration()
    assertThat(model.selectedOption).isEqualTo(CustomConfigs[0])
  }

  @Test
  fun `clear configurations clears configurations`() {
    assumeTrue(model.customConfigurationModel.size == 0)
    model.customConfigurationModel.addElement(CustomConfigs[0])
    assertThat(model.customConfigurationModel.size).isEqualTo(1)
    model.clearConfigurations()
    assertThat(model.customConfigurationModel.size).isEqualTo(0)
  }

  @Test
  fun selectOptionBy() {
    // Select built-in option.
    model.selectOptionBy { recordingOption -> recordingOption.title == "Built in 3" }
    assertThat(model.selectedOption).isEqualTo(BuiltIns[2])

    // Select custom option.
    model.selectOptionBy { recordingOption -> recordingOption.title == "Config 2" }
    if (model.customConfigurationModel.size > 0) {
      assertThat(model.selectedOption).isEqualTo(CustomConfigs[1])
    }
    else {
      // First built-in option is selected if no match.
      assertThat(model.selectedOption).isEqualTo(BuiltIns[0])
    }
  }

  @Test
  fun `switching custom option dropdown selection changes model selection`() {
    // Make the custom configs considered valid so setting the selectedOption is allowed.
    model.addConfigurations(CustomConfigs[0])
    model.addConfigurations(CustomConfigs[1])

    // Simulate selection of custom recording option.
    model.selectedOption = CustomConfigs[1]
    model.selectCurrentCustomConfiguration()

    // Change selected custom option in dropdown.
    model.customConfigurationModel.selectedItem = CustomConfigs[0]
    // Make sure that the change in custom dropdown reflects the
    // overall model option selection.
    assertThat(model.selectedOption).isEqualTo(CustomConfigs[0])

    // Change selected custom option in dropdown again.
    model.customConfigurationModel.selectedItem = CustomConfigs[1]
    // Make sure that the change in custom dropdown reflects the
    // overall model option selection.
    assertThat(model.selectedOption).isEqualTo(CustomConfigs[1])

    // Clean up custom configurations.
    model.clearConfigurations()
  }

  companion object {
    val BuiltIns = arrayOf(
      RecordingOption("Built in 1", "Description 1", {}),
      RecordingOption("Built in 2", "Description 2", {}),
      RecordingOption("Built in 3", "Description 3", {}, {})
    )
    val CustomConfigs = arrayOf(
      RecordingOption("Config 1", "Description 1'", {}, {}),
      RecordingOption("Config 2", "Description 2'", {})
    )
    @Parameterized.Parameters @JvmStatic
    fun configs(): List<Array<Array<RecordingOption>>> = listOf(arrayOf(), CustomConfigs).map {arrayOf(it)}
  }
}
