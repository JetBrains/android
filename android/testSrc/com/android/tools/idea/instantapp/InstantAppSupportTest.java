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
package com.android.tools.idea.instantapp;

import com.android.tools.idea.fd.gradle.InstantRunGradleSupport;
import com.android.tools.idea.run.AndroidRunConfiguration;
import com.android.tools.idea.run.AndroidRunConfigurationType;
import com.android.tools.idea.run.editor.DeepLinkLaunch;
import com.android.tools.idea.run.editor.LaunchOptionState;
import com.android.tools.idea.testartifacts.instrumented.AndroidTestRunConfiguration;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.google.common.collect.ImmutableList;
import com.intellij.execution.RunManager;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RuntimeConfigurationWarning;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.exceptionCases.AbstractExceptionCase;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;

import static com.android.tools.idea.fd.gradle.InstantRunGradleUtils.getIrSupportStatus;
import static com.android.tools.idea.instantapp.AIAProjectStructureAssertions.assertModuleIsValidAIABaseFeature;
import static com.android.tools.idea.instantapp.AIAProjectStructureAssertions.assertModuleIsValidAIAInstantApp;
import static com.android.tools.idea.run.AndroidRunConfiguration.LAUNCH_DEEP_LINK;
import static com.android.tools.idea.testartifacts.TestConfigurationTesting.createAndroidTestConfigurationFromClass;
import static com.android.tools.idea.testing.HighlightInfos.assertFileHasNoErrors;
import static com.android.tools.idea.testing.TestProjectPaths.INSTANT_APP;

public class InstantAppSupportTest extends AndroidGradleTestCase {

  public void testLoadInstantAppProject() throws Exception {
    loadProject(INSTANT_APP);
    generateSources();

    assertModuleIsValidAIAInstantApp(getModule("instant-app"), ImmutableList.of(":feature"));
    assertModuleIsValidAIABaseFeature(getModule("feature"), ImmutableList.of());

    Project project = getProject();
    assertFileHasNoErrors(project, new File("feature/src/main/java/com/example/instantapp/MainActivity.java"));
    assertFileHasNoErrors(project, new File("feature/src/androidTest/java/com/example/instantapp/ExampleInstrumentedTest.java"));
    assertFileHasNoErrors(project, new File("feature/src/test/java/com/example/instantapp/ExampleUnitTest.java"));
  }

  public void testInstantRunDisabled() throws Exception {
    loadProject(INSTANT_APP, "instant-app");

    // by definition, InstantRunGradleSupport.INSTANT_APP != InstantRunGradleSupport.SUPPORTED
    assertEquals(InstantRunGradleSupport.INSTANT_APP, getIrSupportStatus(getModel(), null));
  }

  public void testCorrectRunConfigurationsCreated() throws Exception {
    loadProject(INSTANT_APP, "instant-app");

    // Create one run configuration
    List<RunConfiguration> configurations =
      RunManager.getInstance(getProject()).getConfigurationsList(AndroidRunConfigurationType.getInstance().getFactory().getType());
    assertEquals(1, configurations.size());
    RunConfiguration configuration = configurations.get(0);
    assertInstanceOf(configuration, AndroidRunConfiguration.class);
    AndroidRunConfiguration runConfig = (AndroidRunConfiguration)configuration;

    // Check it is a deep link with the correct URL
    assertEquals(LAUNCH_DEEP_LINK, runConfig.MODE);
    LaunchOptionState launchOptionState = runConfig.getLaunchOptionState(LAUNCH_DEEP_LINK);
    assertInstanceOf(launchOptionState, DeepLinkLaunch.State.class);
    DeepLinkLaunch.State deepLinkLaunchState = (DeepLinkLaunch.State)launchOptionState;
    assertEquals("http://example.com/example", deepLinkLaunchState.DEEP_LINK);
  }

  public void testAndroidRunConfigurationWithoutError() throws Exception {
    loadProject(INSTANT_APP, "feature");
    AndroidTestRunConfiguration
      runConfiguration = createAndroidTestConfigurationFromClass(getProject(), "com.example.instantapp.ExampleInstrumentedTest");
    assertNotNull(runConfiguration);
    runConfiguration.checkConfiguration();
  }

  public void testRunConfigurationFailsIfWrongURL() throws Throwable {
    loadProject(INSTANT_APP, "instant-app");

    // Create one run configuration
    List<RunConfiguration> configurations =
      RunManager.getInstance(getProject()).getConfigurationsList(AndroidRunConfigurationType.getInstance().getFactory().getType());
    assertEquals(1, configurations.size());
    RunConfiguration configuration = configurations.get(0);
    assertInstanceOf(configuration, AndroidRunConfiguration.class);
    AndroidRunConfiguration runConfig = (AndroidRunConfiguration)configuration;

    runConfig.setLaunchUrl("UrlNotInTheManifest");
    assertExceptionInCheckingConfig(runConfig, "UrlNotInTheManifest");

    runConfig.setLaunchUrl("http://example.co");
    assertExceptionInCheckingConfig(runConfig, "http://example.co");

    runConfig.setLaunchUrl("http://example.com/");
    runConfig.checkConfiguration();
    // No exception

    runConfig.setLaunchUrl("http://example.com/test");
    runConfig.checkConfiguration();
    // No exception
  }

  private void assertExceptionInCheckingConfig(@NotNull AndroidRunConfiguration runConfig, @NotNull String url) throws Throwable {
    assertException(new AbstractExceptionCase() {
      @Override
      public Class getExpectedExceptionClass() {
        return RuntimeConfigurationWarning.class;
      }

      @Override
      public void tryClosure() throws Throwable {
        runConfig.checkConfiguration();
      }
    }, "URL \"" + url + "\" not defined in the manifest.");
  }
}
