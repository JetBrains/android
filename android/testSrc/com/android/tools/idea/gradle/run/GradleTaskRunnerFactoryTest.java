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
package com.android.tools.idea.gradle.run;

import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.gradle.run.MakeBeforeRunTaskProvider.GradleTaskRunnerFactory;
import com.android.tools.idea.gradle.util.GradleVersions;
import com.android.tools.idea.run.AndroidRunConfigurationBase;
import com.intellij.execution.configurations.JavaRunConfigurationModule;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.testFramework.IdeaTestCase;
import org.gradle.tooling.BuildAction;
import org.mockito.Mock;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link GradleTaskRunnerFactory}.
 */
public class GradleTaskRunnerFactoryTest extends IdeaTestCase {
  @Mock private GradleVersions myGradleVersions;
  private GradleTaskRunnerFactory myTaskRunnerFactory;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);
    myTaskRunnerFactory = new GradleTaskRunnerFactory(getProject(), myGradleVersions);
  }

  public void testCreateTaskRunnerWithAndroidRunConfigurationBaseAndGradle3Dot5() {
    when(myGradleVersions.getGradleVersion(getProject())).thenReturn(new GradleVersion(3, 5, 0));

    JavaRunConfigurationModule configurationModule = mock(JavaRunConfigurationModule.class);
    when(configurationModule.getModule()).thenReturn(null);

    AndroidRunConfigurationBase configuration = mock(AndroidRunConfigurationBase.class);
    when(configuration.getConfigurationModule()).thenReturn(configurationModule);

    GradleTaskRunner.DefaultGradleTaskRunner taskRunner = myTaskRunnerFactory.createTaskRunner(configuration);

    BuildAction buildAction = taskRunner.getBuildAction();
    assertNotNull(buildAction);
  }

  public void testCreateTaskRunnerWithAndroidRunConfigurationBaseAndGradleOlderThan3Dot5() {
    when(myGradleVersions.getGradleVersion(getProject())).thenReturn(new GradleVersion(3, 4, 1));
    AndroidRunConfigurationBase configuration = mock(AndroidRunConfigurationBase.class);

    GradleTaskRunner.DefaultGradleTaskRunner taskRunner = myTaskRunnerFactory.createTaskRunner(configuration);

    BuildAction buildAction = taskRunner.getBuildAction();
    assertNull(buildAction);
  }

  public void testCreateTaskRunnerWithConfigurationNotAndroidRunConfigurationBase() {
    RunConfiguration configuration = mock(RunConfiguration.class);

    GradleTaskRunner.DefaultGradleTaskRunner taskRunner = myTaskRunnerFactory.createTaskRunner(configuration);

    BuildAction buildAction = taskRunner.getBuildAction();
    assertNull(buildAction);
  }
}