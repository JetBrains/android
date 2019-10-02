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

import static com.android.SdkConstants.GRADLE_PATH_SEPARATOR;
import static com.android.builder.model.AndroidProject.PROJECT_TYPE_DYNAMIC_FEATURE;
import static com.android.tools.idea.Projects.getBaseDirPath;
import static com.android.tools.idea.testing.Facets.createAndAddAndroidFacet;
import static com.android.tools.idea.testing.Facets.createAndAddGradleFacet;
import static com.google.common.truth.Truth.assertThat;
import static java.util.Collections.emptyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import com.android.ide.common.gradle.model.IdeAndroidProject;
import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.android.tools.idea.gradle.project.model.AndroidModelFeatures;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.model.GradleModuleModel;
import com.android.tools.idea.gradle.run.MakeBeforeRunTaskProvider.GradleTaskRunnerFactory;
import com.android.tools.idea.gradle.stubs.gradle.GradleProjectStub;
import com.android.tools.idea.gradle.util.GradleVersions;
import com.android.tools.idea.model.AndroidModel;
import com.android.tools.idea.run.AndroidRunConfigurationBase;
import com.android.tools.idea.testartifacts.instrumented.AndroidTestRunConfiguration;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.intellij.execution.configurations.JavaRunConfigurationModule;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.module.Module;
import com.intellij.testFramework.PlatformTestCase;
import org.gradle.tooling.BuildAction;
import org.gradle.tooling.model.GradleProject;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.jps.android.model.impl.JpsAndroidModuleProperties;
import org.mockito.Mock;

/**
 * Tests for {@link GradleTaskRunnerFactory}.
 */
public class GradleTaskRunnerFactoryTest extends PlatformTestCase {
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

  public void testCreateTaskRunnerForDynamicFeatureInstrumentedTest() {
    when(myGradleVersions.getGradleVersion(getProject())).thenReturn(new GradleVersion(3, 5, 0));
    // Setup a base-app Module
    AndroidModuleModel androidModuleModel1 = mock(AndroidModuleModel.class);
    IdeAndroidProject ideAndroidProject1 = mock(IdeAndroidProject.class);
    setUpModuleAsAndroidModule(getModule(), androidModuleModel1, ideAndroidProject1);
    // Setup an additional Dynamic Feature module
    Module featureModule = createModule("feature1");
    AndroidModuleModel androidModuleModel2 = mock(AndroidModuleModel.class);
    IdeAndroidProject ideAndroidProject2 = mock(IdeAndroidProject.class);
    setUpModuleAsAndroidModule(featureModule, androidModuleModel2, ideAndroidProject2);
    when(ideAndroidProject2.getProjectType()).thenReturn(PROJECT_TYPE_DYNAMIC_FEATURE);
    when(ideAndroidProject1.getDynamicFeatures()).thenReturn(ImmutableList.of(":feature1"));
    when(ideAndroidProject2.getDynamicFeatures()).thenReturn(emptyList());

    JavaRunConfigurationModule configurationModule = mock(JavaRunConfigurationModule.class);
    when(configurationModule.getModule()).thenReturn(featureModule);
    AndroidTestRunConfiguration configuration = mock(AndroidTestRunConfiguration.class);
    when(configuration.getConfigurationModule()).thenReturn(configurationModule);

    GradleTaskRunner.DefaultGradleTaskRunner taskRunner = myTaskRunnerFactory.createTaskRunner(configuration);
    OutputBuildAction buildAction = (OutputBuildAction)taskRunner.myBuildAction;
    assertNotNull(buildAction);
    assertSize(2, buildAction.getMyGradlePaths());
    assertThat(buildAction.getMyGradlePaths()).containsExactly(":" + getModule().getName(), ":feature1");
  }

  private void setUpModuleAsAndroidModule(Module module, AndroidModuleModel androidModel, IdeAndroidProject ideAndroidProject) {
    setUpModuleAsGradleModule(module);

    when(androidModel.getAndroidProject()).thenReturn(ideAndroidProject);

    AndroidModelFeatures androidModelFeatures = mock(AndroidModelFeatures.class);
    when(androidModelFeatures.isTestedTargetVariantsSupported()).thenReturn(false);
    when(androidModel.getFeatures()).thenReturn(androidModelFeatures);

    AndroidFacet androidFacet = createAndAddAndroidFacet(module);
    JpsAndroidModuleProperties state = androidFacet.getConfiguration().getState();
    assertNotNull(state);
    state.ASSEMBLE_TASK_NAME = "assembleTask2";
    state.AFTER_SYNC_TASK_NAMES = Sets.newHashSet("afterSyncTask1", "afterSyncTask2");
    state.COMPILE_JAVA_TASK_NAME = "compileTask2";

    AndroidModel.set(androidFacet, androidModel);
  }

  private void setUpModuleAsGradleModule(Module module) {
    GradleFacet gradleFacet = createAndAddGradleFacet(module);
    gradleFacet.getConfiguration().GRADLE_PROJECT_PATH = GRADLE_PATH_SEPARATOR + module.getName();

    GradleProject gradleProjectStub = new GradleProjectStub(emptyList(), GRADLE_PATH_SEPARATOR + module.getName(),
                                                            getBaseDirPath(getProject()));
    GradleModuleModel model = new GradleModuleModel(module.getName(), gradleProjectStub, emptyList(), null, null, null, null);
    gradleFacet.setGradleModuleModel(model);
  }
}
