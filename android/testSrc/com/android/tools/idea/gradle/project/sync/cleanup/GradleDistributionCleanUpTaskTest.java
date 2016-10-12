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
package com.android.tools.idea.gradle.project.sync.cleanup;

import com.android.annotations.Nullable;
import com.android.tools.idea.gradle.util.GradleProjectSettingsFinder;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.android.tools.idea.testing.TestMessagesDialog;
import com.intellij.openapi.ui.Messages;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;

import java.io.File;
import java.util.UUID;

import static com.android.SdkConstants.FD_GRADLE;
import static com.android.tools.idea.testing.FileSubject.file;
import static com.google.common.truth.Truth.assertAbout;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.util.io.FileUtil.createTempDirectory;
import static com.intellij.openapi.util.io.FileUtil.delete;
import static org.jetbrains.plugins.gradle.settings.DistributionType.DEFAULT_WRAPPED;
import static org.jetbrains.plugins.gradle.settings.DistributionType.LOCAL;

/**
 * Tests for {@link GradleDistributionCleanUpTask}
 */
public class GradleDistributionCleanUpTaskTest extends AndroidGradleTestCase {
  private TestMessagesDialog myTestDialog;
  private GradleDistributionCleanUpTask myCleanUpTask;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myTestDialog = new TestMessagesDialog(Messages.OK);
    Messages.setTestDialog(myTestDialog);

    myCleanUpTask = new GradleDistributionCleanUpTask();
  }

  // See https://code.google.com/p/android/issues/detail?id=66880
  public void testAutomaticCreationOfMissingWrapper() throws Exception {
    loadSimpleApplication();
    deleteGradleWrapper();

    GradleProjectSettings settings = doGetGradleProjectSettings();
    settings.setDistributionType(DEFAULT_WRAPPED);

    myCleanUpTask.cleanUp(getProject());

    verifyGradleWrapperExists();
  }

  public void testShouldCreateWrapperWhenLocalDistributionPathIsNotSet() throws Exception {
    loadSimpleApplication();

    setGradleLocalDistribution("");
    deleteGradleWrapper();

    myCleanUpTask.cleanUp(getProject());

    String message = myTestDialog.getDisplayedMessage();
    assertThat(message).contains("The path of the local Gradle distribution to use is not set.");
    verifyUserIsAskedToUseGradleWrapper(message);

    verifyGradleWrapperExists();
  }

  public void testShouldCreateWrapperWhenLocalDistributionPathDoesNotExist() throws Exception {
    loadSimpleApplication();

    String nonExistingPath = new File(SystemProperties.getUserHome(), UUID.randomUUID().toString()).getPath();
    setGradleLocalDistribution(nonExistingPath);
    deleteGradleWrapper();

    myCleanUpTask.cleanUp(getProject());

    String message = myTestDialog.getDisplayedMessage();
    assertThat(message).contains("'" + nonExistingPath + "'");
    assertThat(message).contains("set as a local Gradle distribution, does not belong to an existing directory.");
    verifyUserIsAskedToUseGradleWrapper(message);

    verifyGradleWrapperExists();
  }

  public void testShouldCreateWrapperWhenPathIsNotGradleDistribution() throws Exception {
    loadSimpleApplication();

    String gradlePath = createTempDirectory("gradle", null).getPath();
    setGradleLocalDistribution(gradlePath);
    deleteGradleWrapper();

    myCleanUpTask.cleanUp(getProject());

    String message = myTestDialog.getDisplayedMessage();
    assertThat(message).contains("'" + gradlePath + "'");
    assertThat(message).contains("does not belong to a Gradle distribution.");
    verifyUserIsAskedToUseGradleWrapper(message);

    verifyGradleWrapperExists();
  }

  private void setGradleLocalDistribution(@NotNull String gradleLocalDistributionPath) {
    GradleProjectSettings settings = doGetGradleProjectSettings();
    settings.setDistributionType(LOCAL);
    settings.setGradleHome(gradleLocalDistributionPath);
  }

  @NotNull
  private GradleProjectSettings doGetGradleProjectSettings() {
    GradleProjectSettings settings = GradleProjectSettingsFinder.getInstance().findGradleProjectSettings(getProject());
    assertNotNull(settings);
    return settings;
  }

  private void deleteGradleWrapper() {
    File gradleWrapperFolderPath = getGradleWrapperFolderPath();
    delete(gradleWrapperFolderPath);
    assertAbout(file()).that(gradleWrapperFolderPath).named("Gradle wrapper").doesNotExist();
  }

  @NotNull
  private File getGradleWrapperFolderPath() {
    return new File(getProjectFolderPath(), FD_GRADLE);
  }

  private void verifyGradleWrapperExists() {
    File gradleWrapperFolderPath = getGradleWrapperFolderPath();
    assertAbout(file()).that(gradleWrapperFolderPath).named("Gradle wrapper").isDirectory();
  }

  private static void verifyUserIsAskedToUseGradleWrapper(@Nullable String message) {
    assertThat(message).contains("Would you like the project to use the Gradle wrapper?");
  }
}