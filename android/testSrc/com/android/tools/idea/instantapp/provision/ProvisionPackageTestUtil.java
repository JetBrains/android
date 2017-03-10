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
package com.android.tools.idea.instantapp.provision;

import com.android.ddmlib.*;
import com.android.sdklib.AndroidVersion;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.nio.charset.Charset;

import static org.jetbrains.android.AndroidTestBase.getTestDataPath;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Utils for creating mocks for testing {@link ProvisionPackage}s.
 */
class ProvisionPackageTestUtil {
  @NotNull
  static File getInstantAppSdk() {
    File testData = new File(getTestDataPath());
    return new File(testData, "whsdk");
  }

  @NotNull
  static IDevice getMockDevice(@NotNull String arch, int apiLevel, @NotNull String osBuildType, @NotNull String pkgName, @NotNull String pkgVersion)
    throws Throwable {
    return new DeviceGenerator()
      .setArchitectures(arch)
      .setApiLevel(apiLevel)
      .setOsBuildType(osBuildType)
      .setVersionOfPackage(pkgName, pkgVersion)
      .getDevice();
  }

  static class DeviceGenerator {
    @NotNull private final IDevice myDevice;

    DeviceGenerator() {
      myDevice = mock(IDevice.class);
    }

    @NotNull
    DeviceGenerator setArchitectures(@NotNull String... archs) {
      when(myDevice.getAbis()).thenReturn(Lists.newArrayList(archs));
      return this;
    }

    @NotNull
    DeviceGenerator setApiLevel(int apiLevel) {
      when(myDevice.getVersion()).thenReturn(new AndroidVersion(apiLevel, null));
      return this;
    }

    @NotNull
    DeviceGenerator setVersionOfPackage(@NotNull String pkgName, @NotNull String version) throws Throwable {
      String shellCommand = "dumpsys package " + pkgName;
      doAnswer(invocation -> {
        IShellOutputReceiver receiver = invocation.getArgument(1);
        byte[] output = ("versionName=" + version + "\n").getBytes(Charset.defaultCharset());
        receiver.addOutput(output, 0, output.length);
        receiver.flush();
        return null;
      }).when(myDevice).executeShellCommand(eq(shellCommand), notNull());
      return this;
    }

    @NotNull
    DeviceGenerator setOsBuildType(@NotNull String osBuildType) {
      when(myDevice.getProperty("ro.build.tags")).thenReturn(osBuildType);
      return this;
    }

    @NotNull
    IDevice getDevice() {
      return myDevice;
    }
  }
}
