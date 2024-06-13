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
package com.android.tools.profilers.taskbased.task.interim

import com.android.tools.adtui.model.FakeTimer
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.RecordingOption
import com.android.tools.profilers.RecordingOptionsModel
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.cpu.CpuProfilerStage
import com.android.tools.profilers.event.FakeEventService
import com.android.tools.profilers.memory.MainMemoryProfilerStage
import com.android.tools.profilers.sessions.SessionsManager
import com.android.tools.profilers.tasks.taskhandlers.ProfilerTaskHandlerFactory
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class RecordingScreenModelTest {
  private val myTimer = FakeTimer()
  private val myTransportService = FakeTransportService(myTimer, false)

  @get:Rule
  var myGrpcChannel = FakeGrpcChannel("RecordingListModelTestChannel", myTransportService, FakeEventService())

  private lateinit var myProfilers: StudioProfilers
  private lateinit var myManager: SessionsManager
  private lateinit var ideProfilerServices: FakeIdeProfilerServices
  private lateinit var cpuProfilerStage: CpuProfilerStage
  private lateinit var memoryProfilerStage: MainMemoryProfilerStage
  private lateinit var cpuRecordingScreenModel: RecordingScreenModel<CpuProfilerStage>
  private lateinit var memoryRecordingScreenModel: RecordingScreenModel<MainMemoryProfilerStage>

  @Before
  fun setup() {
    ideProfilerServices = FakeIdeProfilerServices()
    ideProfilerServices.enableTaskBasedUx(true)
    myProfilers = StudioProfilers(
      ProfilerClient(myGrpcChannel.channel),
      ideProfilerServices,
      myTimer
    )
    myManager = myProfilers.sessionsManager
    val taskHandlers = ProfilerTaskHandlerFactory.createTaskHandlers(myManager)
    taskHandlers.forEach {  myProfilers.addTaskHandler(it.key, it.value)  }
    cpuProfilerStage = CpuProfilerStage(myProfilers)
    cpuRecordingScreenModel = cpuProfilerStage.recordingScreenModel!!
    memoryProfilerStage = MainMemoryProfilerStage(myProfilers)
    memoryRecordingScreenModel = memoryProfilerStage.recordingScreenModel!!
  }

  @Test
  fun `test formatElapsedTime with time greater than 1 min`() {
    // 90 seconds == 1 min and 30 sec
    val elapsedNs = FakeTimer.ONE_SECOND_IN_NS * 90
    val formattedTime = cpuRecordingScreenModel.formatElapsedTime(elapsedNs)
    assertThat(formattedTime).isEqualTo("1 min, 30 sec")
  }

  @Test
  fun `test formatElapsedTime with time less than 1 min`() {
    // 30 seconds == 0 min and 30 sec
    val elapsedNs = FakeTimer.ONE_SECOND_IN_NS * 30
    val formattedTime = cpuRecordingScreenModel.formatElapsedTime(elapsedNs)
    assertThat(formattedTime).isEqualTo("0 min, 30 sec")
  }

  @Test
  fun `elapsed nanoseconds updates with timer registered in CpuProfilerStage`() {
    myProfilers.stage = cpuProfilerStage
    assertThat(cpuRecordingScreenModel.elapsedNs.value).isEqualTo(0)
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)
    assertThat(cpuRecordingScreenModel.elapsedNs.value).isEqualTo(FakeTimer.ONE_SECOND_IN_NS)
  }

  @Test
  fun `elapsed nanoseconds updates with timer registered in MainMemoryProfilerStage`() {
    myProfilers.stage = memoryProfilerStage
    assertThat(memoryRecordingScreenModel.elapsedNs.value).isEqualTo(0)
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)
    assertThat(memoryRecordingScreenModel.elapsedNs.value).isEqualTo(FakeTimer.ONE_SECOND_IN_NS)
  }

  @Test
  fun `test is stop button enabled with RecordingOptionsModel's canStop = false`() {
    // The RecordingScreenModel.isStopRecordingButtonEnabled state is directly controlled by RecordingOptionModel's canStop return value.
    // There are two conditions for canStop to return true: isRecording == true && selectedOption?.stopAction != null
    // By default, isRecording should be set to false, and thus canStop should be false. This means the recording screen model will have
    // the stop button disabled.
    assertThat(cpuRecordingScreenModel.isStopRecordingButtonEnabled.value).isEqualTo(false)

    // The following call to startRecording will simulate a recording with a valid stopAction, fulfilling all canStop conditions.
    startFakeRecording(cpuProfilerStage.recordingModel)
    assertThat(cpuRecordingScreenModel.isStopRecordingButtonEnabled.value).isEqualTo(true)
  }

  companion object {
    /**
     * Creates a "valid" (see: RecordingOptionsModel#isValid) RecordingOption, selects it, and then sets isRecording is true, fulfilling
     * all conditions for RecordingOptionsModel#canStop to return true.
     */
    fun startFakeRecording(recordingModel: RecordingOptionsModel) {
      val recordingOption = RecordingOption("", "", {}, {})
      recordingModel.customConfigurationModel.addElement(recordingOption)
      recordingModel.selectedOption = recordingOption
      recordingModel.setRecording()
    }
  }
}