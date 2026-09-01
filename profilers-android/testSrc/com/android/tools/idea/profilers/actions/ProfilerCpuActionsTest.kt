/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.profilers.actions

import com.android.tools.adtui.model.FakeTimer
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.profilers.AndroidProfilerToolWindow
import com.android.tools.idea.profilers.AndroidProfilerToolWindowFactory
import com.android.tools.idea.run.profiler.CpuProfilerConfig
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.idea.transport.faketransport.commands.StartTrace
import com.android.tools.idea.transport.faketransport.commands.StopTrace
import com.android.tools.profiler.proto.Commands
import com.android.tools.profiler.proto.Trace
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.cpu.CpuProfilerStage
import com.android.tools.profilers.cpu.config.PerfettoSystemTraceConfiguration
import com.android.tools.profilers.cpu.config.ProfilingConfiguration
import com.google.common.truth.Truth.assertThat
import com.intellij.mock.MockProjectEx
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.TestActionEvent
import com.intellij.ui.content.ContentManager
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever

@RunsInEdt
class ProfilerCpuActionsTest {
  @get:Rule val applicationRule = ApplicationRule()
  @get:Rule val disposableRule = DisposableRule()
  @get:Rule val edtRule = EdtRule()

  private val timer = FakeTimer()
  private val ideServices = FakeIdeProfilerServices()
  private val transportService = FakeTransportService(timer, true, com.android.sdklib.AndroidVersion.VersionCodes.P)

  @get:Rule val grpcChannel = FakeGrpcChannel("ProfilerCpuActionsTestChannel", transportService)

  private lateinit var project: MockProjectEx
  private lateinit var profilers: StudioProfilers
  private lateinit var mockProfilerToolWindow: AndroidProfilerToolWindow

  @Before
  fun setUp() {
    project = MockProjectEx(disposableRule.disposable)
    val mockToolWindowManager = mock(ToolWindowManager::class.java)
    val mockToolWindow = mock(ToolWindow::class.java)
    val mockContentManager = mock(ContentManager::class.java)
    whenever(mockContentManager.contentCount).thenReturn(1)
    whenever(mockToolWindow.contentManager).thenReturn(mockContentManager)
    whenever(mockToolWindowManager.getToolWindow(AndroidProfilerToolWindowFactory.ID)).thenReturn(mockToolWindow)
    project.registerService(ToolWindowManager::class.java, mockToolWindowManager)

    ideServices.enableTaskBasedUx(false)
    ideServices.addCustomProfilingConfiguration(
      CpuProfilerConfig.Technology.SYSTEM_TRACE.getName(),
      ProfilingConfiguration.TraceType.PERFETTO,
    )
    val startTrace = transportService.getRegisteredCommand(Commands.Command.CommandType.START_TRACE) as StartTrace
    startTrace.startStatus = Trace.TraceStartStatus.newBuilder().setStatus(Trace.TraceStartStatus.Status.SUCCESS).build()
    val stopTrace = transportService.getRegisteredCommand(Commands.Command.CommandType.STOP_TRACE) as StopTrace
    stopTrace.stopStatus = Trace.TraceStopStatus.newBuilder().setStatus(Trace.TraceStopStatus.Status.SUCCESS).build()

    profilers = StudioProfilers(ProfilerClient(grpcChannel.channel), ideServices, timer)
    profilers.setPreferredProcess(FakeTransportService.FAKE_DEVICE_NAME, FakeTransportService.FAKE_PROCESS_NAME, null)
    timer.tick(FakeTimer.ONE_SECOND_IN_NS)
    mockProfilerToolWindow = mock(AndroidProfilerToolWindow::class.java)
    whenever(mockProfilerToolWindow.profilers).thenReturn(profilers)
    AndroidProfilerToolWindowFactory.PROJECT_PROFILER_MAP[project] = mockProfilerToolWindow
  }

  @After
  fun tearDown() {
    AndroidProfilerToolWindowFactory.PROJECT_PROFILER_MAP.remove(project)
    StudioFlags.PROFILER_TRACEBOX.clearOverride()
  }

  @Test
  fun testStartSystemTraceAction() {
    StudioFlags.PROFILER_TRACEBOX.override(true)
    val action = StartSystemTraceAction()
    val event = TestActionEvent.createTestEvent(SimpleDataContext.getProjectContext(project))

    action.actionPerformed(event)

    assertThat(profilers.stage).isInstanceOf(CpuProfilerStage::class.java)
    val stage = profilers.stage as CpuProfilerStage
    val config = stage.profilerConfigModel.profilingConfiguration
    assertThat(config).isInstanceOf(PerfettoSystemTraceConfiguration::class.java)
    assertThat(config.name).isEqualTo(CpuProfilerConfig.Technology.SYSTEM_TRACE.getName())
    assertThat(stage.recordingModel.isRecording).isTrue()
  }

  @Test
  fun testStopCpuCaptureAction() {
    StudioFlags.PROFILER_TRACEBOX.override(true)
    val startAction = StartSystemTraceAction()
    val stopAction = StopCpuCaptureAction()
    val event = TestActionEvent.createTestEvent(SimpleDataContext.getProjectContext(project))

    startAction.actionPerformed(event)
    val stage = profilers.stage as CpuProfilerStage
    assertThat(stage.recordingModel.isRecording).isTrue()

    timer.tick(FakeTimer.ONE_SECOND_IN_NS)
    assertThat(stage.captureState).isEqualTo(CpuProfilerStage.CaptureState.CAPTURING)

    stopAction.actionPerformed(event)
    assertThat(stage.captureState).isEqualTo(CpuProfilerStage.CaptureState.STOPPING)
  }
}
