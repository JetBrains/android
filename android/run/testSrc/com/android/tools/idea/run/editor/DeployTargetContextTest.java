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
package com.android.tools.idea.run.editor;

import static org.junit.Assert.assertEquals;

import com.android.tools.idea.run.TargetSelectionMode;
import com.android.tools.idea.run.deployment.DeviceAndSnapshotComboBoxTargetProvider;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;

public final class DeployTargetContextTest {
  private DeployTargetProvider myDeviceAndSnapshotComboBoxTargetProvider;
  private DeployTargetProvider myCloudTestMatrixTargetProvider;

  private DeployTargetContext myContext;

  @Before
  public void initProviders() {
    myDeviceAndSnapshotComboBoxTargetProvider = DeviceAndSnapshotComboBoxTargetProvider.getInstance();
    myCloudTestMatrixTargetProvider = new CloudTestMatrixTargetProvider();
  }

  @Before
  public void initContext() {
    myContext = new DeployTargetContext(Arrays.asList(
      myDeviceAndSnapshotComboBoxTargetProvider,
      myCloudTestMatrixTargetProvider));
  }

  @Test
  public void getApplicableDeployTargetProvidersVisibleComboBoxAppConfiguration() {
    // Act
    Object actualProviders = myContext.getApplicableDeployTargetProviders(false);

    // Assert
    assertEquals(Collections.singletonList(myDeviceAndSnapshotComboBoxTargetProvider), actualProviders);
  }

  @Test
  public void getApplicableDeployTargetProvidersVisibleComboBoxTestConfiguration() {
    // Act
    Object actualProviders = myContext.getApplicableDeployTargetProviders(true);

    // Assert
    assertEquals(Arrays.asList(myDeviceAndSnapshotComboBoxTargetProvider, myCloudTestMatrixTargetProvider), actualProviders);
  }

  @Test
  public void getCurrentDeployTargetProviderSelectDeviceSnapshotComboBoxIsVisible() {
    Object provider = myContext.getCurrentDeployTargetProvider();

    assertEquals(myDeviceAndSnapshotComboBoxTargetProvider, provider);
  }

  @Test
  public void getCurrentDeployTargetProviderTargetSelectionModeEqualsFirebaseDeviceMatrix() {
    // Arrange
    myContext.setTargetSelectionMode(TargetSelectionMode.FIREBASE_DEVICE_MATRIX);

    // Act
    Object actualProvider = myContext.getCurrentDeployTargetProvider();

    // Assert
    assertEquals(myCloudTestMatrixTargetProvider, actualProvider);
  }
}
