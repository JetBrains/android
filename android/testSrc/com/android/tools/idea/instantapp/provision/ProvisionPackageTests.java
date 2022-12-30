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

import static org.jetbrains.android.AndroidTestBase.getTestDataPath;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.notNull;
import static org.mockito.Mockito.when;

import com.android.ddmlib.IDevice;
import com.android.ddmlib.IShellOutputReceiver;
import com.android.sdklib.AndroidVersion;
import com.google.common.collect.Lists;
import java.io.File;
import java.nio.charset.Charset;
import org.jetbrains.annotations.NotNull;

/**
 * Utils for creating mocks for testing {@link ProvisionPackage}s.
 */
public final class ProvisionPackageTests {
  @NotNull
  static File getInstantAppSdk() {
    File testData = new File(getTestDataPath());
    return new File(testData, "whsdk");
  }

  @NotNull
  static IDevice getMockDevice(@NotNull String arch, int apiLevel, @NotNull String osBuildType, @NotNull String pkgName, long pkgVersion)
    throws Throwable {
    return new DeviceGenerator()
      .setArchitectures(arch)
      .setApiLevel(apiLevel)
      .setOsBuildType(osBuildType)
      .setVersionOfPackage(pkgName, pkgVersion)
      .getDevice();
  }

  public static class DeviceGenerator {
    @NotNull private final IDevice myDevice;

    public DeviceGenerator() {
      myDevice = mock(IDevice.class);
    }

    @NotNull
    public DeviceGenerator setArchitectures(@NotNull String... archs) {
      when(myDevice.getAbis()).thenReturn(Lists.newArrayList(archs));
      return this;
    }

    @NotNull
    public DeviceGenerator setApiLevel(int apiLevel) {
      when(myDevice.getVersion()).thenReturn(new AndroidVersion(apiLevel, null));
      return this;
    }

    @NotNull
    public DeviceGenerator setVersionOfPackage(@NotNull String pkgName, long version) throws Throwable {
      String shellCommand = "dumpsys package " + pkgName;
      doAnswer(invocation -> {
        IShellOutputReceiver receiver = invocation.getArgument(1);
        byte[] output = ("versionCode=" + version + " \n").getBytes(Charset.defaultCharset());
        receiver.addOutput(output, 0, output.length);
        receiver.flush();
        return null;
      }).when(myDevice).executeShellCommand(eq(shellCommand), notNull());
      return this;
    }

    @NotNull
    public DeviceGenerator setGoogleAccountLogged() throws Throwable {
      String shellCommand = "dumpsys account";
      doAnswer(invocation -> {
        IShellOutputReceiver receiver = invocation.getArgument(1);
        byte[] output = ("Account {name=bla@google.com, type=com.google}\n").getBytes(Charset.defaultCharset());
        receiver.addOutput(output, 0, output.length);
        receiver.flush();
        return null;
      }).when(myDevice).executeShellCommand(eq(shellCommand), notNull());
      return this;
    }

    @NotNull
    public DeviceGenerator setOsBuildType(@NotNull String osBuildType) {
      when(myDevice.getProperty("ro.build.tags")).thenReturn(osBuildType);
      return this;
    }

    @NotNull
    public IDevice getDevice() {
      return myDevice;
    }
  }
}
