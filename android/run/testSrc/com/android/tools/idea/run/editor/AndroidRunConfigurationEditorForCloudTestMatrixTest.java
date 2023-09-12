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

import com.android.tools.idea.run.TargetSelectionMode;
import com.android.tools.idea.testartifacts.instrumented.AndroidTestRunConfiguration;
import com.android.tools.idea.testing.AndroidProjectRule;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;

public final class AndroidRunConfigurationEditorForCloudTestMatrixTest {
  @Rule
  public final AndroidProjectRule myRule = AndroidProjectRule.inMemory();
  private AndroidRunConfigurationEditor<AndroidTestRunConfiguration> androidRunConfigurationEditor;

  @Test
  public void applyEditorTo() {
    // Arrange
    DeployTargetProvider provider = new CloudTestMatrixTargetProvider();
    AndroidRunConfigurationEditorTest runConfigEditorTest = new AndroidRunConfigurationEditorTest();
    List<DeployTargetProvider> providers = runConfigEditorTest.getTargetProviders(provider);

    androidRunConfigurationEditor = runConfigEditorTest.getAndroidRunConfigurationEditor(provider, myRule.getProject());
    DeployTargetContext deployTargetContext = new DeployTargetContext(providers);
    AndroidTestRunConfiguration configuration = Mockito.mock(AndroidTestRunConfiguration.class);
    Mockito.when(configuration.getDeployTargetContext()).thenReturn(deployTargetContext);
    Mockito.when(configuration.getProfilerState()).thenReturn(new ProfilerState());

    // Act
    androidRunConfigurationEditor.getDeploymentTargetOptions().getTargetComboBox().setSelectedItem(provider);
    androidRunConfigurationEditor.applyEditorTo(configuration);

    // Assert
    assertEquals(TargetSelectionMode.FIREBASE_DEVICE_MATRIX, deployTargetContext.getTargetSelectionMode());
  }
}
