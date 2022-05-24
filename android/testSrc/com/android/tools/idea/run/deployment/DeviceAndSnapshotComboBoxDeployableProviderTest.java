/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.run.deployment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.android.tools.idea.run.AndroidDevice;
import com.android.tools.idea.run.AndroidRunConfigurationBase;
import com.android.tools.idea.run.ApkProvisionException;
import com.android.tools.idea.run.ApplicationIdProvider;
import com.android.tools.idea.run.deployable.DeployableProvider;
import com.android.tools.idea.run.deployment.Device.Type;
import com.android.tools.idea.run.deployment.DeviceAndSnapshotComboBoxDeployableProvider.DeployableDevice;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import java.util.Collections;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class DeviceAndSnapshotComboBoxDeployableProviderTest {
  private final @NotNull Device myDevice;

  private final @NotNull DeviceAndSnapshotComboBoxAction myAction;
  private final @NotNull ApplicationIdProvider myApplicationIdProvider;
  private final @NotNull AndroidRunConfigurationBase myConfiguration;

  public DeviceAndSnapshotComboBoxDeployableProviderTest() {
    Project project = Mockito.mock(Project.class);

    myDevice = new VirtualDevice.Builder()
      .setName("Pixel 5 API 31")
      .setKey(new VirtualDevicePath("/home/user/.android/avd/Pixel_5_API_31.avd"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .setType(Type.PHONE)
      .build();

    myAction = Mockito.mock(DeviceAndSnapshotComboBoxAction.class);
    Mockito.when(myAction.getSelectedDevices(project)).thenReturn(Collections.singletonList(myDevice));

    myApplicationIdProvider = Mockito.mock(ApplicationIdProvider.class);

    myConfiguration = Mockito.mock(AndroidRunConfigurationBase.class);
    Mockito.when(myConfiguration.getProject()).thenReturn(project);
    Mockito.when(myConfiguration.getApplicationIdProvider()).thenReturn(myApplicationIdProvider);
  }

  @Test
  public void getDeployable() throws Exception {
    // Arrange
    Logger logger = Logger.getInstance(DeviceAndSnapshotComboBoxDeployableProvider.class);
    DeployableProvider deployableProvider = new DeviceAndSnapshotComboBoxDeployableProvider(() -> myAction, () -> logger);

    Mockito.when(myApplicationIdProvider.getPackageName()).thenReturn("com.google.myapplication");

    // Act
    Object deployable = deployableProvider.getDeployable(myConfiguration);

    // Assert
    assertEquals(new DeployableDevice(myDevice, "com.google.myapplication"), deployable);
  }

  @Test
  public void getDeployableStackTraceIsLoggedOnce() throws Exception {
    // Arrange
    Logger logger = Mockito.mock(Logger.class);
    DeployableProvider deployableProvider = new DeviceAndSnapshotComboBoxDeployableProvider(() -> myAction, () -> logger);

    Throwable throwable = new ApkProvisionException("[My_Application.app] Unable to obtain main package from manifest.");
    Mockito.when(myApplicationIdProvider.getPackageName()).thenThrow(throwable);

    // Act
    Object deployable1 = deployableProvider.getDeployable(myConfiguration);
    Object deployable2 = deployableProvider.getDeployable(myConfiguration);

    // Assert
    Mockito.verify(logger).warn(throwable);

    Mockito.verify(logger).warn("An ApkProvisionException has been thrown more than once: com.android.tools.idea.run.ApkProvisionException:"
                                + " [My_Application.app] Unable to obtain main package from manifest.");

    assertNull(deployable1);
    assertNull(deployable2);
  }
}
