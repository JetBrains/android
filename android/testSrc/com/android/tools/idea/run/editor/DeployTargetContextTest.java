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
import java.util.Collection;
import java.util.Collections;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;

public final class DeployTargetContextTest {
  private boolean mySelectDeviceSnapshotComboBoxVisible;

  private DeployTargetProvider myDeviceAndSnapshotComboBoxTargetProvider;
  private DeployTargetProvider myShowChooserTargetProvider;
  private DeployTargetProvider myEmulatorTargetProvider;
  private DeployTargetProvider myUsbDeviceTargetProvider;
  private DeployTargetProvider myCloudTestMatrixTargetProvider;

  private DeployTargetContext myContext;

  @Before
  public void initProviders() {
    myDeviceAndSnapshotComboBoxTargetProvider = new DeviceAndSnapshotComboBoxTargetProvider();
    myShowChooserTargetProvider = new ShowChooserTargetProvider();
    myEmulatorTargetProvider = new EmulatorTargetProvider();
    myUsbDeviceTargetProvider = new UsbDeviceTargetProvider();
    myCloudTestMatrixTargetProvider = new CloudTestMatrixTargetProvider();
  }

  @Before
  public void initContext() {
    myContext = new DeployTargetContext(() -> mySelectDeviceSnapshotComboBoxVisible, this::getDeployTargetProviders);
  }

  @NotNull
  private Collection<DeployTargetProvider> getDeployTargetProviders(boolean deviceSnapshotComboBoxVisible) {
    if (!deviceSnapshotComboBoxVisible) {
      return Arrays.asList(
        myShowChooserTargetProvider,
        myEmulatorTargetProvider,
        myUsbDeviceTargetProvider,
        myCloudTestMatrixTargetProvider,
        new CloudDebuggingTargetProvider());
    }

    return Arrays.asList(
      myDeviceAndSnapshotComboBoxTargetProvider,
      myShowChooserTargetProvider,
      myEmulatorTargetProvider,
      myUsbDeviceTargetProvider,
      myCloudTestMatrixTargetProvider,
      new CloudDebuggingTargetProvider());
  }

  @Test
  public void getApplicableDeployTargetProvidersVisibleComboBoxAppConfiguration() {
    // Arrange
    mySelectDeviceSnapshotComboBoxVisible = true;

    // Act
    Object actualProviders = myContext.getApplicableDeployTargetProviders(false);

    // Assert
    assertEquals(Collections.singletonList(myDeviceAndSnapshotComboBoxTargetProvider), actualProviders);
  }

  @Test
  public void getApplicableDeployTargetProvidersVisibleComboBoxTestConfiguration() {
    // Arrange
    mySelectDeviceSnapshotComboBoxVisible = true;

    // Act
    Object actualProviders = myContext.getApplicableDeployTargetProviders(true);

    // Assert
    assertEquals(Arrays.asList(myDeviceAndSnapshotComboBoxTargetProvider, myCloudTestMatrixTargetProvider), actualProviders);
  }

  @Test
  public void getApplicableDeployTargetProvidersInvisibleComboBoxAppConfiguration() {
    // Act
    Object actualProviders = myContext.getApplicableDeployTargetProviders(false);

    // Assert
    assertEquals(Arrays.asList(myShowChooserTargetProvider, myEmulatorTargetProvider, myUsbDeviceTargetProvider), actualProviders);
  }

  @Test
  public void getApplicableDeployTargetProvidersInvisibleComboBoxTestConfiguration() {
    // Act
    Object actualProviders = myContext.getApplicableDeployTargetProviders(true);

    // Assert
    Object expectedProviders = Arrays.asList(
      myShowChooserTargetProvider,
      myEmulatorTargetProvider,
      myUsbDeviceTargetProvider,
      myCloudTestMatrixTargetProvider);

    assertEquals(expectedProviders, actualProviders);
  }

  @Test
  public void getCurrentDeployTargetProviderSelectDeviceSnapshotComboBoxIsVisible() {
    mySelectDeviceSnapshotComboBoxVisible = true;

    Object provider = myContext.getCurrentDeployTargetProvider();

    assertEquals(myDeviceAndSnapshotComboBoxTargetProvider, provider);
  }

  @Test
  public void getCurrentDeployTargetProviderTargetSelectionModeEqualsFirebaseDeviceMatrix() {
    // Arrange
    mySelectDeviceSnapshotComboBoxVisible = true;
    myContext.setTargetSelectionMode(TargetSelectionMode.FIREBASE_DEVICE_MATRIX);

    // Act
    Object actualProvider = myContext.getCurrentDeployTargetProvider();

    // Assert
    assertEquals(myCloudTestMatrixTargetProvider, actualProvider);
  }

  @Test
  public void getCurrentDeployTargetProviderTargetSelectionModeEqualsDeviceAndSnapshotComboBox() {
    myContext.setTargetSelectionMode(TargetSelectionMode.DEVICE_AND_SNAPSHOT_COMBO_BOX);

    Object provider = myContext.getCurrentDeployTargetProvider();

    assertEquals(myShowChooserTargetProvider, provider);
  }

  @Test
  public void getCurrentDeployTargetProviderProviderIsFound() {
    Object provider = myContext.getCurrentDeployTargetProvider();

    assertEquals(myShowChooserTargetProvider, provider);
  }

  @Test
  public void getCurrentDeployTargetProviderProviderIsNotFound() {
    myContext.setTargetSelectionMode("#2-Y2Y3Ob-h72ks%");

    Object provider = myContext.getCurrentDeployTargetProvider();

    assertEquals(myShowChooserTargetProvider, provider);
  }

  @Test
  public void getCurrentDeployTargetProviderSetSelectDeviceSnapshotComboBoxVisibleToTrue() {
    // Act
    myContext.setTargetSelectionMode(TargetSelectionMode.DEVICE_AND_SNAPSHOT_COMBO_BOX);
    mySelectDeviceSnapshotComboBoxVisible = true;

    // Assert
    assertEquals(myDeviceAndSnapshotComboBoxTargetProvider, myContext.getCurrentDeployTargetProvider());
  }
}
