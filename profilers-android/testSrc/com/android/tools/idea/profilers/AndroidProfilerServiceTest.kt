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

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.profilers.perfd.ProfilerServiceProxy
import com.android.tools.idea.transport.TransportDeviceManager
import com.android.tools.idea.transport.TransportProxy
import com.android.tools.profiler.proto.Agent
import com.android.tools.profiler.proto.Transport
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ex.ToolWindowManagerEx
import com.intellij.testFramework.PlatformTestCase
import com.intellij.testFramework.registerServiceInstance
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.isA
import org.mockito.Mockito.clearInvocations
import org.mockito.Mockito.times
import org.mockito.Mockito.verify

/**
 * Test cases that verify behavior of the profiler service. A PlatformTestCase is used for access to the Application and Project
 * structures created in setup.
 */
class AndroidProfilerServiceTest : PlatformTestCase() {

  override fun setUp() {
    super.setUp()
    if (AndroidProfilerService.getInstance() == null) {
      ApplicationManager.getApplication().registerServiceInstance(AndroidProfilerService::class.java, AndroidProfilerService())
    }
  }

  fun testProfilerServiceStartsCorrectlyAfterToolWindowInit() {
    StudioFlags.PROFILER_ENERGY_PROFILER_ENABLED.override(false)
    val mockProxy = mockTransportProxy()
    val windowManager = ToolWindowManagerEx.getInstance(myProject)
    val toolWindow = windowManager.registerToolWindow(AndroidProfilerToolWindowFactory.ID, false, ToolWindowAnchor.BOTTOM)
    val factory = AndroidProfilerToolWindowFactory()
    factory.init(toolWindow)

    ApplicationManager.getApplication().messageBus.syncPublisher<TransportDeviceManager.TransportDeviceManagerListener>(
      TransportDeviceManager.TOPIC).customizeProxyService(mockProxy)

    verify<TransportProxy>(mockProxy).registerProxyService(isA<ProfilerServiceProxy>(ProfilerServiceProxy::class.java))
  }

  fun testProfilerServiceTriggeredOnceForMultipleToolWindows() {
    StudioFlags.PROFILER_ENERGY_PROFILER_ENABLED.override(false)
    val mockProxy = mockTransportProxy()
    val windowManager = ToolWindowManagerEx.getInstance(myProject)
    val toolWindow = windowManager.registerToolWindow(AndroidProfilerToolWindowFactory.ID, false, ToolWindowAnchor.BOTTOM)
    val factory = AndroidProfilerToolWindowFactory()
    factory.init(toolWindow)
    ApplicationManager.getApplication().messageBus.syncPublisher<TransportDeviceManager.TransportDeviceManagerListener>(
      TransportDeviceManager.TOPIC).customizeProxyService(mockProxy)
    verify(mockProxy, times(1)).registerDataPreprocessor(any())
    clearInvocations(mockProxy)
    factory.init(toolWindow)
    ApplicationManager.getApplication().messageBus.syncPublisher<TransportDeviceManager.TransportDeviceManagerListener>(
      TransportDeviceManager.TOPIC).customizeProxyService(mockProxy)
    verify(mockProxy, times(1)).registerDataPreprocessor(any())

  }

  fun testCustomizeDaemonConfig() {
    val configBuilder = Transport.DaemonConfig.newBuilder()
    AndroidProfilerService.getInstance().customizeDaemonConfig(configBuilder)
    assertThat(configBuilder.hasCommon()).isTrue()
    assertThat(configBuilder.hasCpu()).isTrue()
  }

  fun testCustomizeAgentConfigNoRunConfig() {
    val configBuilder = Agent.AgentConfig.newBuilder()
    AndroidProfilerService.getInstance().customizeAgentConfig(configBuilder, null)
    assertThat(configBuilder.hasCommon()).isTrue()
    assertThat(configBuilder.hasMem()).isTrue()
  }

  fun testCustomizeProxyService() {
    // We don't mock out the TransportService so we need to disable the energy profiler to prevent a null pointer exception.
    StudioFlags.PROFILER_ENERGY_PROFILER_ENABLED.override(false)
    val mockProxy = mockTransportProxy()
    AndroidProfilerService.getInstance().customizeProxyService(mockProxy)
    verify(mockProxy, times(1)).registerDataPreprocessor(any())
    verify(mockProxy, times(1)).registerEventPreprocessor(any())
  }
}