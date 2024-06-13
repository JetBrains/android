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

/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.android.sdklib.AndroidVersion
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.idea.transport.faketransport.FakeGrpcServer
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Common.AgentData
import com.google.common.truth.Truth
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunsInEdt
@RunWith(Parameterized::class)
class TaskProfilersViewTest(private val isTestingProfileable: Boolean) {

  private val timer = FakeTimer()
  private val service = if (isTestingProfileable) FakeTransportService(timer, true,
                                                                       AndroidVersion.VersionCodes.S,
                                                                       Common.Process.ExposureLevel.PROFILEABLE)
                        else FakeTransportService(timer)
  private val ideProfilerServices = FakeIdeProfilerServices()

  @JvmField
  @Rule
  val grpcChannel: FakeGrpcServer = FakeGrpcServer.createFakeGrpcServer("TaskProfilersViewTestChannel", service)

  @JvmField
  @Rule
  val edtRule = EdtRule()

  @JvmField
  @Rule
  val appRule = ApplicationRule() // For initializing HelpTooltip.

  @JvmField
  @Rule
  val disposableRule = DisposableRule()

  private lateinit var studioProfilers: StudioProfilers
  private lateinit var view: TaskProfilersView

  @Before
  fun setUp() {
    ideProfilerServices.enableTaskBasedUx(true)
    studioProfilers = StudioProfilers(ProfilerClient(grpcChannel.channel), ideProfilerServices, timer)
    studioProfilers.setPreferredProcess(FakeTransportService.FAKE_DEVICE_NAME, FakeTransportService.FAKE_PROCESS_NAME, null)
    view = TaskProfilersView(studioProfilers, FakeIdeProfilerComponents(), disposableRule.disposable)
    view.bind(FakeStage::class.java) { profilersView: TaskProfilersView?, stage: FakeStage? -> FakeStageView(profilersView!!, stage!!) }
    timer.tick(FakeTimer.ONE_SECOND_IN_NS)

    if (isTestingProfileable) {
      // We setup and profile a process, we assume that process has an agent attached by default.
      updateAgentStatus(FakeTransportService.FAKE_PROCESS.pid, ProfilersTestData.DEFAULT_AGENT_ATTACHED_RESPONSE)
    }

    val component = view.component
    component.setSize(1024, 450)
  }

  @Test
  fun testSameStageTransition() {
    val stage = FakeStage(studioProfilers, "Really?", false)
    studioProfilers.stage = stage
    val view = view.stageView

    studioProfilers.stage = stage

    Truth.assertThat(this.view.stageView).isEqualTo(view)
  }

  @Test
  fun testViewHasNoExceptionsWhenProfilersStop() {
    val stage = FakeStage(studioProfilers, "Really?", false)
    studioProfilers.stage = stage

    studioProfilers.stop()

    // Make sure no exceptions
    val view = view.stageView
  }

  private fun updateAgentStatus(pid: Int, agentData: AgentData) {
    val sessionStreamId = studioProfilers.session.streamId
    service.addEventToStream(sessionStreamId, Common.Event.newBuilder()
      .setPid(pid)
      .setKind(Common.Event.Kind.AGENT)
      .setAgentData(agentData)
      .build())
    timer.tick(FakeTimer.ONE_SECOND_IN_NS)
  }

  companion object {
    @JvmStatic
    @Parameterized.Parameters
    fun isTestingProfileable() = listOf(false, true)
  }
}