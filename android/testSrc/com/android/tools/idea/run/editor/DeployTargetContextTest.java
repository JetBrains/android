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
import com.android.tools.idea.testing.AndroidProjectRule;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

public final class DeployTargetContextTest {
  @Rule
  public final TestRule myRule = AndroidProjectRule.inMemory();

  private List<DeployTargetProvider> myProviders;

  @Before
  public void initProviders() {
    myProviders = Arrays.asList(DeployTargetProvider.EP_NAME.getExtensions());
  }

  @Test
  public void getCurrentDeployTargetProviderSelectDeviceSnapshotComboBoxIsVisible() {
    DeployTargetContext context = new DeployTargetContext(() -> true, myProviders);

    Object provider = context.getCurrentDeployTargetProvider();

    assertEquals(DeployTargetProvider.EP_NAME.findExtension(DeviceAndSnapshotComboBoxTargetProvider.class), provider);
  }

  @Test
  public void getCurrentDeployTargetProviderTargetSelectionModeEqualsFirebaseDeviceMatrix() {
    // Arrange
    DeployTargetProvider expectedProvider = new CloudTestMatrixTargetProvider();

    myProviders = new ArrayList<>(myProviders);
    myProviders.add(expectedProvider);

    DeployTargetContext context = new DeployTargetContext(() -> true, myProviders);
    context.setTargetSelectionMode(TargetSelectionMode.FIREBASE_DEVICE_MATRIX);

    // Act
    Object actualProvider = context.getCurrentDeployTargetProvider();

    // Assert
    assertEquals(expectedProvider, actualProvider);
  }

  private static final class CloudTestMatrixTargetProvider extends DeployTargetProvider {
    @NotNull
    @Override
    public String getId() {
      return TargetSelectionMode.FIREBASE_DEVICE_MATRIX.name();
    }

    @NotNull
    @Override
    public String getDisplayName() {
      throw new UnsupportedOperationException();
    }

    @NotNull
    @Override
    public DeployTargetState createState() {
      return new State();
    }

    private static final class State extends DeployTargetState {
    }

    @NotNull
    @Override
    public DeployTargetConfigurable createConfigurable(@NotNull Project project,
                                                       @NotNull Disposable parent,
                                                       @NotNull DeployTargetConfigurableContext context) {
      throw new UnsupportedOperationException();
    }

    @NotNull
    @Override
    public DeployTarget getDeployTarget() {
      throw new UnsupportedOperationException();
    }
  }

  @Test
  public void getCurrentDeployTargetProviderTargetSelectionModeEqualsDeviceAndSnapshotComboBox() {
    DeployTargetContext context = new DeployTargetContext(() -> false, myProviders);
    context.setTargetSelectionMode(TargetSelectionMode.DEVICE_AND_SNAPSHOT_COMBO_BOX);

    Object provider = context.getCurrentDeployTargetProvider();

    assertEquals(DeployTargetProvider.EP_NAME.findExtension(ShowChooserTargetProvider.class), provider);
  }

  @Test
  public void getCurrentDeployTargetProviderProviderIsFound() {
    DeployTargetContext context = new DeployTargetContext(() -> false, myProviders);

    Object provider = context.getCurrentDeployTargetProvider();

    assertEquals(DeployTargetProvider.EP_NAME.findExtension(ShowChooserTargetProvider.class), provider);
  }

  @Test
  public void getCurrentDeployTargetProviderProviderIsNotFound() {
    DeployTargetContext context = new DeployTargetContext(() -> false, myProviders);
    context.setTargetSelectionMode("#2-Y2Y3Ob-h72ks%");

    Object provider = context.getCurrentDeployTargetProvider();

    assertEquals(DeployTargetProvider.EP_NAME.findExtension(ShowChooserTargetProvider.class), provider);
  }
}
