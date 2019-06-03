/*
 * Copyright (C) 2018 The Android Open Source Project
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

import static com.android.tools.idea.testartifacts.TestConfigurationTesting.createAndroidTestConfigurationFromClass;
import static com.android.tools.idea.testing.TestProjectPaths.DYNAMIC_APP;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.ddmlib.IDevice;
import com.android.ddmlib.IShellOutputReceiver;
import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.google.common.base.Charsets;
import com.google.common.util.concurrent.Futures;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AndroidTestRunConfigurationTest extends AndroidGradleTestCase {

  private static final String TEST_APP_CLASS_NAME = "google.simpleapplication.ApplicationTest";

  @Override
  public void setUp() throws Exception {
    // Flag has to be overridden as early as possible, since the run configuration type is initialized
    // during test setup (see org.jetbrains.android.AndroidPlugin).
    StudioFlags.RUNDEBUG_ANDROID_BUILD_BUNDLE_ENABLED.override(true);

    super.setUp();
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      StudioFlags.RUNDEBUG_ANDROID_BUILD_BUNDLE_ENABLED.clearOverride();
    }
    finally {
      super.tearDown();
    }
  }

  public void testApkProviderForPreLDevice() throws Exception {
    loadProject(DYNAMIC_APP);

    AndroidRunConfigurationBase androidTestRunConfiguration =
      createAndroidTestConfigurationFromClass(getProject(), TEST_APP_CLASS_NAME);
    assertNotNull(androidTestRunConfiguration);

    List<AndroidDevice> devices = new ArrayList<>();
    AndroidDevice device = createMockDevice("test", 19);
    devices.add(device);

    ApkProvider provider = androidTestRunConfiguration.getApkProvider(myAndroidFacet, new MyApplicationIdProvider(), devices);
    assertThat(provider).isNotNull();
    assertThat(provider).isInstanceOf(GradleApkProvider.class);
    assertThat(((GradleApkProvider)provider).isTest()).isTrue();
    assertThat(((GradleApkProvider)provider).getOutputKind()).isEqualTo(GradleApkProvider.OutputKind.AppBundleOutputModel);
  }

  public void testApkProviderForPostLDevice() throws Exception {
    loadProject(DYNAMIC_APP);

    AndroidRunConfigurationBase androidTestRunConfiguration =
      createAndroidTestConfigurationFromClass(getProject(), TEST_APP_CLASS_NAME);
    assertNotNull(androidTestRunConfiguration);

    List<AndroidDevice> devices = new ArrayList<>();
    AndroidDevice device = createMockDevice("test", 24);
    devices.add(device);

    ApkProvider provider = androidTestRunConfiguration.getApkProvider(myAndroidFacet, new MyApplicationIdProvider(), devices);
    assertThat(provider).isNotNull();
    assertThat(provider).isInstanceOf(GradleApkProvider.class);
    assertThat(((GradleApkProvider)provider).isTest()).isTrue();
    assertThat(((GradleApkProvider)provider).getOutputKind()).isEqualTo(GradleApkProvider.OutputKind.Default);
  }

  public void testApkProviderForDynamicFeatureInstrumentedTest() throws Exception {
    loadProject(DYNAMIC_APP, "feature1");

    AndroidRunConfigurationBase androidTestRunConfiguration =
      createAndroidTestConfigurationFromClass(getProject(), TEST_APP_CLASS_NAME);
    assertNotNull(androidTestRunConfiguration);

    List<AndroidDevice> devices = new ArrayList<>();
    AndroidDevice device = createMockDevice("test", 24);
    devices.add(device);

    ApkProvider provider = androidTestRunConfiguration.getApkProvider(myAndroidFacet, new MyApplicationIdProvider(), devices);
    assertThat(provider).isNotNull();
    assertThat(provider).isInstanceOf(GradleApkProvider.class);
    assertThat(((GradleApkProvider)provider).isTest()).isTrue();
    assertThat(((GradleApkProvider)provider).getOutputKind()).isEqualTo(GradleApkProvider.OutputKind.AppBundleOutputModel);
  }

  @SuppressWarnings("SameParameterValue")
  private static AndroidDevice createMockDevice(String id, int apiLevel) throws Exception {
    IDevice device = mock(IDevice.class);
    when(device.getSerialNumber()).thenReturn(id);
    when(device.getVersion()).thenReturn(new AndroidVersion(apiLevel));

    AndroidDevice androidDevice = mock(AndroidDevice.class);
    when(androidDevice.getLaunchedDevice()).thenReturn(Futures.immediateFuture(device));
    when(androidDevice.getVersion()).thenAnswer(invocation -> device.getVersion());

    setupDeviceConfig(device, "  config: mcc310-mnc410-es-rUS,fr-rFR-ldltr-sw411dp-w411dp-h746dp-normal-long-notround" +
                              "-lowdr-nowidecg-port-notnight-560dpi-finger-keysexposed-nokeys-navhidden-nonav-v27");
    return androidDevice;
  }

  @SuppressWarnings("SameParameterValue")
  private static void setupDeviceConfig(IDevice device, String config) throws Exception {
    doAnswer(invocation -> {
      // get the 2nd arg (the receiver to feed it the lines).
      IShellOutputReceiver receiver = invocation.getArgument(1);
      byte[] byteArray = (config + "\n").getBytes(Charsets.UTF_8);
      receiver.addOutput(byteArray, 0, byteArray.length);
      return null;
    }).when(device).executeShellCommand(anyString(), any(), anyLong(), any());
  }

  private static class MyApplicationIdProvider implements ApplicationIdProvider {
    @NotNull
    @Override
    public String getPackageName() {
      return TEST_APP_CLASS_NAME;
    }

    @Nullable
    @Override
    public String getTestPackageName() {
      return TEST_APP_CLASS_NAME + ".test";
    }
  }
}
