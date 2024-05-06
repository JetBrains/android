/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.build.compiler;

import static com.android.tools.idea.gradle.util.GradleBuilds.CONTINUE_BUILD_OPTION;
import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.Lists;
import junit.framework.TestCase;

import java.util.Collection;

/**
 * Tests for {@link AndroidGradleBuildConfiguration}.
 */
public class AndroidGradleBuildConfigurationTest extends TestCase {
  public void testGetCommandLineOptionsDefaultHasContinue() {
    AndroidGradleBuildConfiguration configuration = new AndroidGradleBuildConfiguration();
    configuration.COMMAND_LINE_OPTIONS = "--stacktrace   --offline  --debug --all";
    assertThat(configuration.CONTINUE_FAILED_BUILD).isTrue();
    Collection<String> options = Lists.newArrayList(configuration.getCommandLineOptions());
    assertThat(options).containsExactly("--stacktrace", "--offline", "--debug", "--all", CONTINUE_BUILD_OPTION);
  }

  public void testGetCommandLineOptionsNoContinue() {
    AndroidGradleBuildConfiguration configuration = new AndroidGradleBuildConfiguration();
    configuration.COMMAND_LINE_OPTIONS = "--stacktrace   --offline  --debug --all";
    configuration.CONTINUE_FAILED_BUILD = false;
    Collection<String> options = Lists.newArrayList(configuration.getCommandLineOptions());
    assertThat(options).containsExactly("--stacktrace", "--offline", "--debug", "--all");
  }

  public void testGetCommandLineOptionsContinueAlreadyThere() {
    AndroidGradleBuildConfiguration configuration = new AndroidGradleBuildConfiguration();
    configuration.COMMAND_LINE_OPTIONS = "--stacktrace   --offline  --debug --all --continue";
    configuration.CONTINUE_FAILED_BUILD = false;
    Collection<String> options = Lists.newArrayList(configuration.getCommandLineOptions());
    assertThat(options).containsExactly("--stacktrace", "--offline", "--debug", "--all", CONTINUE_BUILD_OPTION);
  }

  public void testGetCommandLineOptionsContinueOnlyOnce() {
    AndroidGradleBuildConfiguration configuration = new AndroidGradleBuildConfiguration();
    configuration.COMMAND_LINE_OPTIONS = "--stacktrace   --offline  --debug --all --continue";
    configuration.CONTINUE_FAILED_BUILD = true;
    Collection<String> options = Lists.newArrayList(configuration.getCommandLineOptions());
    assertThat(options).containsExactly("--stacktrace", "--offline", "--debug", "--all", CONTINUE_BUILD_OPTION);
    assertThat(options).containsNoDuplicates();
  }
}
