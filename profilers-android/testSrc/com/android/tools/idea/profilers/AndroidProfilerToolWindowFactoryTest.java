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
package com.android.tools.idea.profilers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.ddmlib.Client;
import com.android.ddmlib.IDevice;
import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.profilers.perfd.ProfilerServiceProxy;
import com.android.tools.idea.transport.TransportDeviceManager;
import com.android.tools.idea.transport.TransportProxy;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.testFramework.PlatformTestCase;
import io.grpc.netty.NettyChannelBuilder;
import java.util.HashMap;
import java.util.concurrent.LinkedBlockingDeque;

public class AndroidProfilerToolWindowFactoryTest extends PlatformTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    StudioFlags.PROFILER_ENERGY_PROFILER_ENABLED.override(false);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      StudioFlags.PROFILER_ENERGY_PROFILER_ENABLED.clearOverride();
    }
    finally {
      super.tearDown();
    }
  }

  public void testProfilerServiceStartsCorrectlyAfterToolWindowInit() {
    TransportProxy mockProxy = mockTransportProxy();
    ToolWindowManager windowManager = ToolWindowManagerEx.getInstance(myProject);
    ToolWindow toolWindow = windowManager.registerToolWindow(AndroidProfilerToolWindowFactory.ID, false, ToolWindowAnchor.BOTTOM);
    AndroidProfilerToolWindowFactory factory = new AndroidProfilerToolWindowFactory();
    factory.init(toolWindow);

    ApplicationManager.getApplication().getMessageBus().syncPublisher(TransportDeviceManager.TOPIC).customizeProxyService(mockProxy);

    verify(mockProxy).registerProxyService(isA(ProfilerServiceProxy.class));
  }

  public void testProfilerServiceNotStartedWithoutToolWindow() {
    TransportProxy mockProxy = mockTransportProxy();

    ApplicationManager.getApplication().getMessageBus().syncPublisher(TransportDeviceManager.TOPIC).customizeProxyService(mockProxy);

    verify(mockProxy, never()).registerProxyService(any());
  }

  private TransportProxy mockTransportProxy() {
    TransportProxy mockProxy = mock(TransportProxy.class);

    when(mockProxy.getTransportChannel()).thenReturn(NettyChannelBuilder.forTarget("someTarget").build());
    when(mockProxy.getBytesCache()).thenReturn(new HashMap<>());
    when(mockProxy.getEventQueue()).thenReturn(new LinkedBlockingDeque<>());

    IDevice mockDevice = mock(IDevice.class);
    when(mockDevice.getSerialNumber()).thenReturn("Serial");
    when(mockDevice.getName()).thenReturn("Device");
    when(mockDevice.getVersion()).thenReturn(new AndroidVersion(1, "API"));
    when(mockDevice.isOnline()).thenReturn(true);
    when(mockDevice.getClients()).thenReturn(new Client[0]);
    when(mockProxy.getDevice()).thenReturn(mockDevice);

    return mockProxy;
  }
}
