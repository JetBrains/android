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
package com.android.tools.idea.gradle.run;

import com.android.sdklib.AndroidVersion;
import com.android.sdklib.devices.Abi;
import com.android.tools.idea.run.AndroidDevice;
import com.google.common.collect.ImmutableList;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MakeBeforeRunTaskProviderTest {
  private AndroidDevice myDevice;

  @Before
  public void setup() {
    myDevice = mock(AndroidDevice.class);
  }

  @Test
  public void deviceSpecificArguments() {
    when(myDevice.getVersion()).thenReturn(new AndroidVersion(20, null));
    when(myDevice.getDensity()).thenReturn(640);
    when(myDevice.getAbis()).thenReturn(ImmutableList.of(Abi.ARMEABI, Abi.X86));

    List<String> arguments = MakeBeforeRunTaskProvider.getDeviceSpecificArguments(Collections.singletonList(myDevice));

    assertTrue(arguments.contains("-Pandroid.injected.build.api=20"));
    assertTrue(arguments.contains("-Pandroid.injected.build.abi=armeabi,x86"));
  }

  @Test
  public void previewDeviceArguments() {
    when(myDevice.getVersion()).thenReturn(new AndroidVersion(23, "N"));
    when(myDevice.getDensity()).thenReturn(640);
    when(myDevice.getAbis()).thenReturn(ImmutableList.of(Abi.ARMEABI));

    List<String> arguments = MakeBeforeRunTaskProvider.getDeviceSpecificArguments(Collections.singletonList(myDevice));

    assertTrue(arguments.contains("-Pandroid.injected.build.api=24"));
  }

  @Test
  public void multipleDeviceArguments() {
    AndroidDevice device1 = mock(AndroidDevice.class);
    AndroidDevice device2 = mock(AndroidDevice.class);

    when(device1.getVersion()).thenReturn(new AndroidVersion(23, null));
    when(device1.getDensity()).thenReturn(640);
    when(device1.getAbis()).thenReturn(ImmutableList.of(Abi.ARMEABI, Abi.X86));

    when(device2.getVersion()).thenReturn(new AndroidVersion(22, null));
    when(device2.getDensity()).thenReturn(480);
    when(device2.getAbis()).thenReturn(ImmutableList.of(Abi.X86, Abi.X86_64));

    List<String> arguments = MakeBeforeRunTaskProvider.getDeviceSpecificArguments(ImmutableList.of(device1, device2));

    assertTrue(arguments.contains("-Pandroid.injected.build.api=22"));
    assertTrue(arguments.contains("-Pandroid.injected.build.abi=armeabi,x86,x86_64"));
  }
}