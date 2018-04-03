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
import com.android.tools.idea.run.AndroidAppRunConfigurationBase;
import com.android.tools.idea.run.AndroidBundleRunConfiguration;
import com.android.tools.idea.run.AndroidDevice;
import com.android.tools.idea.run.AndroidRunConfiguration;
import com.android.tools.idea.testing.AndroidProjectRule;
import com.google.common.collect.ImmutableList;
import com.intellij.ide.util.PropertiesComponent;
import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MakeBeforeRunTaskProviderTest {
  private AndroidDevice myDevice;
  private AndroidAppRunConfigurationBase myRunConfiguration;

  @Rule
  public AndroidProjectRule projectRule = AndroidProjectRule.inMemory();


  @Before
  public void setup() {
    myDevice = mock(AndroidDevice.class);
    myRunConfiguration = mock(AndroidRunConfiguration.class);
    PropertiesComponent propertiesComponent = mock(PropertiesComponent.class);
    projectRule.replaceProjectService(PropertiesComponent.class, propertiesComponent);
  }

  @Test
  public void deviceSpecificArguments() throws IOException {
    when(myDevice.getVersion()).thenReturn(new AndroidVersion(20, null));
    when(myDevice.getDensity()).thenReturn(640);
    when(myDevice.getAbis()).thenReturn(ImmutableList.of(Abi.ARMEABI, Abi.X86));

    List<String> arguments = MakeBeforeRunTaskProvider.getDeviceSpecificArguments(myRunConfiguration, Collections.singletonList(myDevice));

    assertTrue(arguments.contains("-Pandroid.injected.build.api=20"));
    assertTrue(arguments.contains("-Pandroid.injected.build.abi=armeabi,x86"));
    for (String argument : arguments) {
      assertFalse("codename should not be set for a released version", argument.startsWith("-Pandroid.injected.build.codename"));
    }
  }

  @Test
  public void previewDeviceArguments() throws IOException {
    when(myDevice.getVersion()).thenReturn(new AndroidVersion(23, "N"));
    when(myDevice.getDensity()).thenReturn(640);
    when(myDevice.getAbis()).thenReturn(ImmutableList.of(Abi.ARMEABI));

    List<String> arguments = MakeBeforeRunTaskProvider.getDeviceSpecificArguments(myRunConfiguration, Collections.singletonList(myDevice));

    assertTrue(arguments.contains("-Pandroid.injected.build.api=23"));
    assertTrue(arguments.contains("-Pandroid.injected.build.codename=N"));
  }

  @Test
  public void previewDeviceArgumentsForBundleConfiguration() throws IOException {
    myRunConfiguration = mock(AndroidBundleRunConfiguration.class);
    when(myDevice.getVersion()).thenReturn(new AndroidVersion(23, "N"));
    when(myDevice.getDensity()).thenReturn(640);
    when(myDevice.getAbis()).thenReturn(ImmutableList.of(Abi.ARMEABI));

    List<String> arguments = MakeBeforeRunTaskProvider.getDeviceSpecificArguments(myRunConfiguration, Collections.singletonList(myDevice));

    assertThat(arguments.size()).isEqualTo(1);
    String args = arguments.get(0);
    assertThat(args).startsWith("-Pandroid.inject.apkselect.config=");
    String path = args.substring(args.lastIndexOf('=') + 1);
    assertThat(path).isNotEmpty();
    File jsonFile = new File(path);
    assertThat(jsonFile.exists()).isTrue();
    assertThat(FileUtils.readFileToString(jsonFile)).isEqualTo("{\"sdk_version\":23,\"screen_density\":640,\"supported_abis\":[\"armeabi\"]}");
    //noinspection ResultOfMethodCallIgnored  // Test code only
    jsonFile.delete();
  }

  @Test
  public void multipleDeviceArguments() throws IOException {
    AndroidDevice device1 = mock(AndroidDevice.class);
    AndroidDevice device2 = mock(AndroidDevice.class);

    when(device1.getVersion()).thenReturn(new AndroidVersion(23, null));
    when(device1.getDensity()).thenReturn(640);
    when(device1.getAbis()).thenReturn(ImmutableList.of(Abi.ARMEABI, Abi.X86));

    when(device2.getVersion()).thenReturn(new AndroidVersion(22, null));
    when(device2.getDensity()).thenReturn(480);
    when(device2.getAbis()).thenReturn(ImmutableList.of(Abi.X86, Abi.X86_64));

    List<String> arguments = MakeBeforeRunTaskProvider.getDeviceSpecificArguments(myRunConfiguration, ImmutableList.of(device1, device2));

    assertTrue(arguments.contains("-Pandroid.injected.build.api=22"));
    for (String argument : arguments) {
      assertFalse("ABIs should not be passed to Gradle when there are multiple devices", argument.startsWith("-Pandroid.injected.build.abi"));
    }
  }
}