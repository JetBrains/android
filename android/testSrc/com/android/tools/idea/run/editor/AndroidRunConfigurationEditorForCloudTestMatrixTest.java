/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.tools.idea.run.ConfigurationSpecificEditor;
import com.android.tools.idea.run.TargetSelectionMode;
import com.android.tools.idea.run.deployment.DeviceAndSnapshotComboBoxTargetProvider;
import com.android.tools.idea.testartifacts.instrumented.AndroidTestRunConfiguration;
import com.android.tools.idea.testing.AndroidProjectRule;
import java.util.Arrays;
import java.util.List;
import javax.swing.JLabel;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;

public final class AndroidRunConfigurationEditorForCloudTestMatrixTest {
  @Rule
  public final AndroidProjectRule myRule = AndroidProjectRule.inMemory();

  @Test
  public void applyEditorTo() {
    // Arrange
    AndroidDebuggerContext androidDebuggerContext = Mockito.mock(AndroidDebuggerContext.class);
    DeployTargetProvider provider = new CloudTestMatrixTargetProvider();
    List<DeployTargetProvider> providers = Arrays.asList(new DeviceAndSnapshotComboBoxTargetProvider(), provider);

    AndroidTestRunConfiguration configuration1 = Mockito.mock(AndroidTestRunConfiguration.class);
    Mockito.when(configuration1.getAndroidDebuggerContext()).thenReturn(androidDebuggerContext);
    Mockito.when(configuration1.getApplicableDeployTargetProviders()).thenReturn(providers);
    Mockito.when(configuration1.getProfilerState()).thenReturn(new ProfilerState());

    @SuppressWarnings("unchecked")
    ConfigurationSpecificEditor<AndroidTestRunConfiguration> configurationSpecificEditor = Mockito.mock(ConfigurationSpecificEditor.class);
    Mockito.when(configurationSpecificEditor.getComponent()).thenReturn(new JLabel());

    AndroidRunConfigurationEditor<AndroidTestRunConfiguration> androidRunConfigurationEditor =
      new AndroidRunConfigurationEditor<>(
        myRule.getProject(),
        facet -> false,
        configuration1,
        true,
        false,
        moduleSelector -> configurationSpecificEditor);

    DeployTargetContext deployTargetContext = new DeployTargetContext(providers);

    AndroidTestRunConfiguration configuration2 = Mockito.mock(AndroidTestRunConfiguration.class);
    Mockito.when(configuration2.getDeployTargetContext()).thenReturn(deployTargetContext);
    Mockito.when(configuration2.getProfilerState()).thenReturn(new ProfilerState());

    // Act
    androidRunConfigurationEditor.getDeploymentTargetOptions().getTargetComboBox().setSelectedItem(provider);
    androidRunConfigurationEditor.applyEditorTo(configuration2);

    // Assert
    assertEquals(TargetSelectionMode.FIREBASE_DEVICE_MATRIX, deployTargetContext.getTargetSelectionMode());
  }
}
