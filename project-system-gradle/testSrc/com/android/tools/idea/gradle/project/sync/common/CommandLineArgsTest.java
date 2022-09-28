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
package com.android.tools.idea.gradle.project.sync.common;

import static com.android.builder.model.AndroidProject.MODEL_LEVEL_3_VARIANT_OUTPUT_POST_BUILD;
import static com.android.builder.model.AndroidProject.PROPERTY_BUILD_MODEL_DISABLE_SRC_DOWNLOAD;
import static com.android.builder.model.AndroidProject.PROPERTY_BUILD_MODEL_ONLY;
import static com.android.builder.model.AndroidProject.PROPERTY_BUILD_MODEL_ONLY_ADVANCED;
import static com.android.builder.model.AndroidProject.PROPERTY_BUILD_MODEL_ONLY_VERSIONED;
import static com.android.builder.model.InjectedProperties.PROPERTY_INVOKED_FROM_IDE;
import static com.android.builder.model.InjectedProperties.PROPERTY_REFRESH_EXTERNAL_NATIVE_MODEL;
import static com.android.tools.idea.gradle.project.sync.idea.AndroidGradleProjectResolverKeys.REFRESH_EXTERNAL_NATIVE_MODELS_KEY;
import static com.android.tools.idea.gradle.project.sync.hyperlink.SyncProjectWithExtraCommandLineOptionsHyperlink.EXTRA_GRADLE_COMMAND_LINE_OPTIONS_KEY;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.gradle.project.GradleExperimentalSettings;
import com.android.tools.idea.gradle.project.GradleProjectInfo;
import com.android.tools.idea.gradle.project.build.compiler.AndroidGradleBuildConfiguration;
import com.android.tools.idea.gradle.project.common.GradleInitScripts;
import com.android.tools.idea.testing.IdeComponents;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.HeavyPlatformTestCase;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.mockito.Mock;

/**
 * Tests for {@link CommandLineArgs}.
 */
public class CommandLineArgsTest extends HeavyPlatformTestCase {
  @Mock private IdeInfo myIdeInfo;
  @Mock private GradleInitScripts myInitScripts;
  @Mock private GradleProjectInfo myGradleProjectInfo;

  private CommandLineArgs myArgs;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    initMocks(this);
    new IdeComponents(getProject(), getTestRootDisposable()).replaceProjectService(GradleProjectInfo.class, myGradleProjectInfo);

    myArgs = new CommandLineArgs(myIdeInfo, myInitScripts);
  }

  public void testGetWithDefaultOptions() {
    List<String> args = myArgs.get(getProject());
    check(args);
    verify(myInitScripts, times(1)).addAndroidStudioToolingPluginInitScriptCommandLineArg(args);
    assertThat(args).doesNotContain("-Dorg.gradle.internal.GradleProjectBuilderOptions=omit_all_tasks");
  }

  public void testGetWhenIncludingLocalMavenRepo() {
    when(myGradleProjectInfo.isNewProject()).thenReturn(true);

    Project project = getProject();
    List<String> args = myArgs.get(project);
    check(args);
    verify(myInitScripts, times(1)).addAndroidStudioToolingPluginInitScriptCommandLineArg(args);
    verify(myInitScripts, times(0)).addLocalMavenRepoInitScriptCommandLineArg(args);
  }

  public void testGetWithAndroidStudio() {
    when(myIdeInfo.isAndroidStudio()).thenReturn(true);
    List<String> args = myArgs.get(getProject());
    check(args);
    assertThat(args).contains("-Dorg.gradle.internal.GradleProjectBuilderOptions=omit_all_tasks");
  }

  public void testGetBuildConfigurationCommandlineOptions() {
    Project project = getProject();
    AndroidGradleBuildConfiguration configuration = project.getService(AndroidGradleBuildConfiguration.class);
    configuration.COMMAND_LINE_OPTIONS = "-Doption1=true -Doption2=true -Poption3=true";
    List<String> args = myArgs.get(project);
    check(args);
    assertThat(args).contains("-Doption1=true");
    assertThat(args).contains("-Doption2=true");
    assertThat(args).contains("-Poption3=true");
  }

  public void testGetWithIdeNotAndroidStudio() {
    when(myIdeInfo.isAndroidStudio()).thenReturn(false);
    List<String> args = myArgs.get(getProject());
    check(args);
  }

  public void testGetWithExtraCommandLineOptions() {
    Project project = getProject();
    String[] options = {"-Doption1=true", "-Doption2=true"};
    project.putUserData(EXTRA_GRADLE_COMMAND_LINE_OPTIONS_KEY, options);

    List<String> args = myArgs.get(getProject());
    check(args);
    assertThat(args).contains("-Doption1=true");
    assertThat(args).contains("-Doption2=true");

    assertNull(project.getUserData(EXTRA_GRADLE_COMMAND_LINE_OPTIONS_KEY));
  }

  public void testGetWithRefreshExternalNativeModelsOption() {
    Project project = getProject();
    project.putUserData(REFRESH_EXTERNAL_NATIVE_MODELS_KEY, true);

    List<String> args = myArgs.get(getProject());
    check(args);
    assertThat(args).contains("-P" + PROPERTY_REFRESH_EXTERNAL_NATIVE_MODEL + "=true");
  }

  public void testStacktraceArgumentApplied() {
    assertThat(myArgs.get(getProject())).contains("--stacktrace");
  }

  public void testGetWithoutSkipTasksList() {
    boolean skipTasks = GradleExperimentalSettings.getInstance().SKIP_GRADLE_TASKS_LIST;
    try {
      GradleExperimentalSettings.getInstance().SKIP_GRADLE_TASKS_LIST = false;
      List<String> args = myArgs.get(getProject());
      check(args);
      assertThat(args).contains("-Pidea.gradle.do.not.build.tasks=false");
    }
    finally {
      // restore settings.
      GradleExperimentalSettings.getInstance().SKIP_GRADLE_TASKS_LIST = skipTasks;
    }
  }

  public void testGetWithSkipTasksList() {
    boolean skipTasks = GradleExperimentalSettings.getInstance().SKIP_GRADLE_TASKS_LIST;
    try {
      GradleExperimentalSettings.getInstance().SKIP_GRADLE_TASKS_LIST = true;
      List<String> args = myArgs.get(getProject());
      check(args);
      assertThat(args).contains("-Pidea.gradle.do.not.build.tasks=true");
    }
    finally {
      // restore settings.
      GradleExperimentalSettings.getInstance().SKIP_GRADLE_TASKS_LIST = skipTasks;
    }
  }

  private static void check(@NotNull List<String> args) {
    assertThat(args).contains("-P" + PROPERTY_BUILD_MODEL_ONLY + "=true");
    assertThat(args).contains("-P" + PROPERTY_BUILD_MODEL_ONLY_ADVANCED + "=true");
    assertThat(args).contains("-P" + PROPERTY_INVOKED_FROM_IDE + "=true");
    assertThat(args).contains("-P" + PROPERTY_BUILD_MODEL_ONLY_ADVANCED + "=true");
    assertThat(args).contains("-P" + PROPERTY_BUILD_MODEL_ONLY_VERSIONED + "=" + MODEL_LEVEL_3_VARIANT_OUTPUT_POST_BUILD);
    //noinspection deprecation Still needs to be injected for AGP 3.5.
    assertThat(args).contains("-P" + PROPERTY_BUILD_MODEL_DISABLE_SRC_DOWNLOAD + "=true");
  }
}
