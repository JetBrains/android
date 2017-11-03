/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.run;

import com.android.ddmlib.IDevice;
import com.intellij.openapi.project.Project;
import org.junit.Test;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ApkInstallerTest {
  @Test
  public void getPmInstallCommand() throws Exception {
    assertThat(FullApkInstaller.ApkInstaller.getPmInstallCommand("/path/to/f.apk", "")).isEqualTo("pm install -t -r \"/path/to/f.apk\"");
  }

  @Test
  public void getPmInstallOptionsOnEmbeddedHardwareWithNullPmOptions() throws Exception {
    Project project = mock(Project.class);
    LaunchOptions options = LaunchOptions.builder().build();
    InstalledApkCache installedApkCache = mock(InstalledApkCache.class);
    ConsolePrinter printer = mock(ConsolePrinter.class);
    FullApkInstaller installer = new FullApkInstaller(project, options, installedApkCache, printer);

    IDevice device = mock(IDevice.class);
    when(device.supportsFeature(IDevice.HardwareFeature.EMBEDDED)).thenReturn(true);
    assertThat(installer.getPmInstallOptions(device)).isEqualTo("-g");
  }

  @Test
  public void getPmInstallOptionsOnEmbeddedHardwareWithNonNullPmOptions() throws Exception {
    Project project = mock(Project.class);
    LaunchOptions options = LaunchOptions.builder().setPmInstallOptions("-v").build();
    InstalledApkCache installedApkCache = mock(InstalledApkCache.class);
    ConsolePrinter printer = mock(ConsolePrinter.class);
    FullApkInstaller installer = new FullApkInstaller(project, options, installedApkCache, printer);

    IDevice device = mock(IDevice.class);
    when(device.supportsFeature(IDevice.HardwareFeature.EMBEDDED)).thenReturn(true);
    assertThat(installer.getPmInstallOptions(device)).isEqualTo("-v -g");
  }
}