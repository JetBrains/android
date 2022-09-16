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

import static com.android.tools.idea.instantapp.AIAProjectStructureAssertions.assertModuleIsValidAIABaseFeature;
import static com.android.tools.idea.instantapp.AIAProjectStructureAssertions.assertModuleIsValidAIAInstantApp;
import static com.android.tools.idea.run.AndroidRunConfiguration.LAUNCH_DEEP_LINK;
import static com.android.tools.idea.testartifacts.TestConfigurationTesting.createAndroidTestConfigurationFromClass;
import static com.android.tools.idea.testing.HighlightInfos.assertFileHasNoErrors;
import static com.android.tools.idea.testing.TestProjectPaths.INSTANT_APP;
import static com.intellij.testFramework.UsefulTestCase.assertInstanceOf;
import static com.intellij.testFramework.UsefulTestCase.assertThrows;
import static org.junit.Assert.assertEquals;

import com.android.testutils.junit4.OldAgpTest;
import com.android.tools.idea.run.AndroidRunConfiguration;
import com.android.tools.idea.run.AndroidRunConfigurationType;
import com.android.tools.idea.run.activity.launch.ActivityLaunchOptionState;
import com.android.tools.idea.run.activity.launch.DeepLinkLaunch;
import com.android.tools.idea.testartifacts.instrumented.AndroidTestRunConfiguration;
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor;
import com.android.tools.idea.testing.AndroidGradleProjectRule;
import com.google.common.collect.ImmutableList;
import com.intellij.execution.RunManager;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RuntimeConfigurationWarning;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.EdtRule;
import com.intellij.testFramework.RunsInEdt;
import java.io.File;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

@OldAgpTest(agpVersions = "3.5.0", gradleVersions = "5.5")
public class InstantAppSupportTest {

  private final AndroidGradleProjectRule projectRule = new AndroidGradleProjectRule();

  @Rule
  public final RuleChain ruleChain = RuleChain.outerRule(projectRule).around(new EdtRule());

  @Test
  @RunsInEdt
  public void testLoadInstantAppProject() throws Exception {
    // Use a plugin with instant app support
    projectRule.loadProject(INSTANT_APP, null, AgpVersionSoftwareEnvironmentDescriptor.AGP_35);
    projectRule.generateSources();

    assertModuleIsValidAIAInstantApp(projectRule.getModule("instant-app"), ImmutableList.of(":feature"));
    assertModuleIsValidAIABaseFeature(projectRule.getModule("feature"), ImmutableList.of());

    Project project = projectRule.getProject();
    assertFileHasNoErrors(project, new File("feature/src/main/java/com/example/instantapp/MainActivity.java"));
    assertFileHasNoErrors(project, new File("feature/src/androidTest/java/com/example/instantapp/ExampleInstrumentedTest.java"));
    assertFileHasNoErrors(project, new File("feature/src/test/java/com/example/instantapp/ExampleUnitTest.java"));
  }

  @Test
  @RunsInEdt
  @Ignore("b/203803107")
  public void testCorrectRunConfigurationsCreated() throws Exception {
    // Use a plugin with instant app support
    projectRule.loadProject(INSTANT_APP, "instant-app", AgpVersionSoftwareEnvironmentDescriptor.AGP_35);

    // Create one run configuration
    List<RunConfiguration> configurations =
      RunManager.getInstance(projectRule.getProject()).getConfigurationsList(AndroidRunConfigurationType.getInstance().getFactory().getType());
    assertEquals(1, configurations.size());
    RunConfiguration configuration = configurations.get(0);
    assertInstanceOf(configuration, AndroidRunConfiguration.class);
    AndroidRunConfiguration runConfig = (AndroidRunConfiguration)configuration;

    // Check it is a deep link with the correct URL
    assertEquals(LAUNCH_DEEP_LINK, runConfig.MODE);
    ActivityLaunchOptionState activityLaunchOptionState = runConfig.getLaunchOptionState(LAUNCH_DEEP_LINK);
    assertInstanceOf(activityLaunchOptionState, DeepLinkLaunch.State.class);
    DeepLinkLaunch.State deepLinkLaunchState = (DeepLinkLaunch.State)activityLaunchOptionState;
    assertEquals("http://example.com/example", deepLinkLaunchState.DEEP_LINK);
  }

  @Test
  @RunsInEdt
  public void testAndroidRunConfigurationWithoutError() throws Exception {
    // Use a plugin with instant app support
    projectRule.loadProject(INSTANT_APP, "feature", AgpVersionSoftwareEnvironmentDescriptor.AGP_35);
    AndroidTestRunConfiguration
      runConfiguration = createAndroidTestConfigurationFromClass(projectRule.getProject(), "com.example.instantapp.ExampleInstrumentedTest");
    runConfiguration.checkConfiguration();
  }

  @Test
  @RunsInEdt
  @Ignore("b/203803107")
  public void testRunConfigurationFailsIfWrongURL() throws Throwable {
    // Use a plugin with instant app support
    projectRule.loadProject(INSTANT_APP, "instant-app", AgpVersionSoftwareEnvironmentDescriptor.AGP_35);

    // Create one run configuration
    List<RunConfiguration> configurations =
      RunManager.getInstance(projectRule.getProject()).getConfigurationsList(AndroidRunConfigurationType.getInstance().getFactory().getType());
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

  private void assertExceptionInCheckingConfig(@NotNull AndroidRunConfiguration runConfig, @NotNull String url) {
    assertThrows(RuntimeConfigurationWarning.class, "URL \"" + url + "\" not defined in the manifest.", runConfig::checkConfiguration);
  }
}
