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
package com.android.tools.idea.memorysettings;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.tools.analytics.HostData;
import com.android.tools.analytics.stubs.StubOperatingSystemMXBean;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.testFramework.PlatformTestCase;

public class MemorySettingsRecommendationTest extends PlatformTestCase {
  public void testRecommend() {
    assertEquals(-1, getRecommended(1280, 5120, 20));
    assertEquals(SystemInfo.isWindows ? -1 : 1536, getRecommended(1280, 5120, 100));
    assertEquals(SystemInfo.isWindows ? -1 : 1536, getRecommended(1280, 5120, 200));
    assertEquals(SystemInfo.isWindows ? -1 : 2048, getRecommended(1280, 8192, 20));
    assertEquals(SystemInfo.isWindows ? 1536 : 2048, getRecommended(1280, 8192, 120));
    assertEquals(SystemInfo.isWindows ? 1536 : 2048, getRecommended(1280, 8192, 200));
    assertEquals(SystemInfo.isWindows ? -1 : 2048, getRecommended(1280, 16 * 1024, 20));
    assertEquals(2048, getRecommended(1280, 16 * 1024, 50));
    assertEquals(3072, getRecommended(1280, 16 * 1024, 100));
    assertEquals(4096, getRecommended(1280, 16 * 1024, 200));
  }

  private int getRecommended(int currentXmxInMB, int machineMemInMB, int moduleCount) {
    Project project = mock(Project.class);
    ModuleManager moduleManager = mock(ModuleManager.class);

    when(project.getService(ModuleManager.class)).thenReturn(moduleManager);
    Module[] modules = new Module[moduleCount];
    when(moduleManager.getModules()).thenReturn(modules);
    stubHostData(machineMemInMB);
    return MemorySettingsRecommendation.getRecommended(project, currentXmxInMB);
  }

  private void stubHostData(int machineMemInMB) {
    HostData.setOsBean(new StubOperatingSystemMXBean() {
      @Override
      public long getTotalPhysicalMemorySize() {
        return machineMemInMB * 1024 * 1024L;
      }
    });
  }
}
