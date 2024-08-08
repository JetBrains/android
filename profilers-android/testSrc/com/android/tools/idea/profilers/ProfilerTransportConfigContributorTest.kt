/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.profilers

import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.profilers.commands.LeakCanaryLogcatCommandHandler
import com.android.tools.idea.run.AndroidRunConfigurationBase
import com.android.tools.idea.run.editor.ProfilerState
import com.android.tools.idea.transport.TransportConfigContributor
import com.android.tools.profiler.proto.Agent
import com.android.tools.profiler.proto.Commands
import com.android.tools.profiler.proto.Transport
import com.android.tools.profilers.memory.BaseStreamingMemoryProfilerStage.LiveAllocationSamplingMode
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.testFramework.HeavyPlatformTestCase
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.any
import org.mockito.Mockito.clearInvocations
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify

/**
 * Test cases that verify behavior of the profiler service. A PlatformTestCase is used for access to the Application and Project
 * structures created in setup.
 */
class ProfilerTransportConfigContributorTest : HeavyPlatformTestCase() {

  override fun tearDown() {
    super.tearDown()
    // We need to clear any override we use inside tests here.
    // We should do this during tear down, in case any test case fails or throws an exception.
    StudioFlags.PROFILER_ENERGY_PROFILER_ENABLED.clearOverride()
    StudioFlags.PROFILER_TRACEBOX.clearOverride()
  }

  fun testProfilerServiceTriggeredOnceForMultipleToolWindows() {
    StudioFlags.PROFILER_ENERGY_PROFILER_ENABLED.override(false)
    val mockProxy = mockTransportProxy()
    val windowManager = ToolWindowManager.getInstance(myProject)
    val toolWindow = windowManager.registerToolWindow(AndroidProfilerToolWindowFactory.ID, false, ToolWindowAnchor.BOTTOM)
    val factory = AndroidProfilerToolWindowFactory()
    factory.init(toolWindow)
    for (extension in TransportConfigContributor.EP_NAME.extensions) {
      extension.customizeProxyService(mockProxy)
    }
    verify(mockProxy, times(1)).registerDataPreprocessor(any())
    clearInvocations(mockProxy)
    factory.init(toolWindow)
    for (extension in TransportConfigContributor.EP_NAME.extensions) {
      extension.customizeProxyService(mockProxy)
    }
    verify(mockProxy, times(1)).registerDataPreprocessor(any())

  }

  fun testCustomizeDaemonConfig() {
    val configBuilder = getCustomizeDaemonConfig()
    assertThat(configBuilder.hasCommon()).isTrue()
    assertThat(configBuilder.hasCpu()).isTrue()
  }

  fun testCustomizeDaemonConfigTraceboxflag() {
    StudioFlags.PROFILER_TRACEBOX.override(true)
    val configBuilderTraceboxTrue = getCustomizeDaemonConfig()
    assertTrue(configBuilderTraceboxTrue.hasCpu())
    assertTrue(configBuilderTraceboxTrue.cpu.useTracebox)

    StudioFlags.PROFILER_TRACEBOX.override(false)
    val configBuilderTraceboxFalse = getCustomizeDaemonConfig()
    assertTrue(configBuilderTraceboxFalse.hasCpu())
    assertFalse(configBuilderTraceboxFalse.cpu.useTracebox)
  }

  private fun getCustomizeDaemonConfig(): Transport.DaemonConfig.Builder {
    val configBuilder = Transport.DaemonConfig.newBuilder()
    ProfilerTransportConfigContributor().customizeDaemonConfig(configBuilder)
    return configBuilder
  }

  fun testCustomizeAgentConfigNoRunConfig() {
    val configBuilder = Agent.AgentConfig.newBuilder()
    ProfilerTransportConfigContributor().customizeAgentConfig(configBuilder, null)
    assertThat(configBuilder.hasCommon()).isTrue()
    assertThat(configBuilder.hasMem()).isTrue()
  }

  fun testAllocationTrackingIsFullByDefault() {
    val configBuilder = Agent.AgentConfig.newBuilder()
    val runConfig = mock(AndroidRunConfigurationBase::class.java)
    val state = ProfilerState();
    whenever(runConfig.profilerState).thenReturn(state);
    ProfilerTransportConfigContributor().customizeAgentConfig(configBuilder, runConfig)
    assertThat(configBuilder.mem.samplingRate.samplingNumInterval).isEqualTo(LiveAllocationSamplingMode.FULL.value)

    // Note startup profiling should not change the default default mode because live allocation tracking can be
    // started only by an explicit user operation which is impossible while startup profiling is in progress.
    state.STARTUP_PROFILING_ENABLED = true;
    state.STARTUP_NATIVE_MEMORY_PROFILING_ENABLED = true;
    ProfilerTransportConfigContributor().customizeAgentConfig(configBuilder, runConfig)
    assertThat(configBuilder.mem.samplingRate.samplingNumInterval).isEqualTo(LiveAllocationSamplingMode.FULL.value)
    assertThat(state.isNativeMemoryStartupProfilingEnabled).isTrue()
  }

  fun testNoRunConfigSetsAttachTypeInstant() {
    val configBuilder = Agent.AgentConfig.newBuilder()
    ProfilerTransportConfigContributor().customizeAgentConfig(configBuilder, null)
    assertThat(configBuilder.attachMethod).isEqualTo(Agent.AgentConfig.AttachAgentMethod.INSTANT);
  }

  fun testMemoryRunConfigSetsAttachTypeAndCommand() {
    val configBuilder = Agent.AgentConfig.newBuilder()
    val runConfig = mock(AndroidRunConfigurationBase::class.java)
    val state = ProfilerState();
    state.STARTUP_NATIVE_MEMORY_PROFILING_ENABLED = true;
    state.STARTUP_PROFILING_ENABLED = true;
    whenever(runConfig.profilerState).thenReturn(state);
    ProfilerTransportConfigContributor().customizeAgentConfig(configBuilder, runConfig)
    assertThat(configBuilder.attachMethod).isEqualTo(Agent.AgentConfig.AttachAgentMethod.ON_COMMAND);
    assertThat(configBuilder.attachCommand).isEqualTo(Commands.Command.CommandType.STOP_TRACE);
  }

  fun testCpuRunConfigSetsAttachTypeAndCommand() {
    // TODO: migrate test to intellij test, or simplify api.
    // This flag and the CPU_PROFILING_ENABLED flag enabled
    // require a project + project service to test.
    val configBuilder = Agent.AgentConfig.newBuilder()
    val runConfig = mock(AndroidRunConfigurationBase::class.java)
    val state = ProfilerState();
    state.STARTUP_CPU_PROFILING_ENABLED = true;
    state.STARTUP_PROFILING_ENABLED = true;
    whenever(runConfig.profilerState).thenReturn(state);
    ProfilerTransportConfigContributor().customizeAgentConfig(configBuilder, runConfig)
    assertThat(configBuilder.attachMethod).isEqualTo(Agent.AgentConfig.AttachAgentMethod.ON_COMMAND);
    assertThat(configBuilder.attachCommand).isEqualTo(Commands.Command.CommandType.STOP_TRACE);
  }

  fun testCustomizeProxyService() {
    // We don't mock out the TransportService so we need to disable the energy profiler to prevent a null pointer exception.
    StudioFlags.PROFILER_ENERGY_PROFILER_ENABLED.override(false)
    val mockProxy = mockTransportProxy()
    ProfilerTransportConfigContributor().customizeProxyService(mockProxy)
    verify(mockProxy, times(1)).registerDataPreprocessor(any())
    verify(mockProxy, times(1)).registerEventPreprocessor(any())
    verify(mockProxy, times(1))
      .registerProxyCommandHandler(eq(Commands.Command.CommandType.STOP_LOGCAT_TRACKING), any(LeakCanaryLogcatCommandHandler::class.java))
    verify(mockProxy, times(1))
      .registerProxyCommandHandler(eq(Commands.Command.CommandType.START_LOGCAT_TRACKING), any(LeakCanaryLogcatCommandHandler::class.java))
  }
}