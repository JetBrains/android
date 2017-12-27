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
package com.android.tools.idea.gradle.util;

import com.android.SdkConstants;
import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.intellij.testFramework.IdeaTestCase;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Properties;

import static com.android.SdkConstants.FD_GRADLE_WRAPPER;
import static com.android.SdkConstants.FN_GRADLE_WRAPPER_PROPERTIES;
import static com.android.tools.idea.gradle.util.PropertiesFiles.getProperties;
import static com.google.common.io.Files.write;
import static com.intellij.openapi.util.io.FileUtil.notNullize;
import static com.intellij.openapi.util.io.FileUtil.splitPath;
import static com.intellij.openapi.util.io.FileUtilRt.createIfNotExists;
import static org.gradle.wrapper.WrapperExecutor.DISTRIBUTION_URL_PROPERTY;

public class GradleWrapperTest extends IdeaTestCase {
  public void testUpdateDistributionUrl() throws IOException {
    File projectPath = Projects.getBaseDirPath(myProject);
    File wrapperFilePath = new File(projectPath, FN_GRADLE_WRAPPER_PROPERTIES);
    createIfNotExists(wrapperFilePath);

    GradleWrapper gradleWrapper = GradleWrapper.get(wrapperFilePath);
    gradleWrapper.updateDistributionUrlAndDisplayFailure("1.6");

    Properties properties = getProperties(wrapperFilePath);
    String distributionUrl = properties.getProperty("distributionUrl");
    assertEquals("https://services.gradle.org/distributions/gradle-1.6-all.zip", distributionUrl);
  }

  public void testUpdateDistributionUrlLeavesGradleWrapperAloneAll() throws IOException {
    // Ensure that if we already have the right version, we don't replace a -all.zip with a -bin.zip
    File projectPath = Projects.getBaseDirPath(myProject);
    File wrapperFilePath = new File(projectPath, FN_GRADLE_WRAPPER_PROPERTIES);
    write("#Wed Apr 10 15:27:10 PDT 2013\n" +
          "distributionBase=GRADLE_USER_HOME\n" +
          "distributionPath=wrapper/dists\n" +
          "zipStoreBase=GRADLE_USER_HOME\n" +
          "zipStorePath=wrapper/dists\n" +
          "distributionUrl=https\\://services.gradle.org/distributions/gradle-1.9-all.zip", wrapperFilePath, Charsets.UTF_8);

    GradleWrapper gradleWrapper = GradleWrapper.get(wrapperFilePath);
    gradleWrapper.updateDistributionUrlAndDisplayFailure("1.9");

    Properties properties = getProperties(wrapperFilePath);
    String distributionUrl = properties.getProperty("distributionUrl");
    assertEquals("https://services.gradle.org/distributions/gradle-1.9-all.zip", distributionUrl);
  }


  public void testUpdateDistributionUrlLeavesGradleWrapperAloneBin() throws IOException {
    // Ensure that if we already have the right version, we don't replace a -bin.zip with a -all.zip
    File projectPath = Projects.getBaseDirPath(myProject);
    File wrapperFilePath = new File(projectPath, FN_GRADLE_WRAPPER_PROPERTIES);
    write("#Wed Apr 10 15:27:10 PDT 2013\n" +
          "distributionBase=GRADLE_USER_HOME\n" +
          "distributionPath=wrapper/dists\n" +
          "zipStoreBase=GRADLE_USER_HOME\n" +
          "zipStorePath=wrapper/dists\n" +
          "distributionUrl=https\\://services.gradle.org/distributions/gradle-1.9-bin.zip", wrapperFilePath, Charsets.UTF_8);

    GradleWrapper gradleWrapper = GradleWrapper.get(wrapperFilePath);
    gradleWrapper.updateDistributionUrlAndDisplayFailure("1.9");

    Properties properties = getProperties(wrapperFilePath);
    String distributionUrl = properties.getProperty("distributionUrl");
    assertEquals("https://services.gradle.org/distributions/gradle-1.9-bin.zip", distributionUrl);
  }

  public void testUpdateDistributionUrlReplacesGradleWrapper() throws IOException {
    // Test that when we replace to a new version we use -all.zip
    File projectPath = Projects.getBaseDirPath(myProject);
    File wrapperFilePath = new File(projectPath, FN_GRADLE_WRAPPER_PROPERTIES);
    write("#Wed Apr 10 15:27:10 PDT 2013\n" +
          "distributionBase=GRADLE_USER_HOME\n" +
          "distributionPath=wrapper/dists\n" +
          "zipStoreBase=GRADLE_USER_HOME\n" +
          "zipStorePath=wrapper/dists\n" +
          "distributionUrl=https\\://services.gradle.org/distributions/gradle-1.9-bin.zip", wrapperFilePath, Charsets.UTF_8);

    GradleWrapper gradleWrapper = GradleWrapper.get(wrapperFilePath);
    gradleWrapper.updateDistributionUrlAndDisplayFailure("1.6");

    Properties properties = getProperties(wrapperFilePath);
    String distributionUrl = properties.getProperty("distributionUrl");
    assertEquals("https://services.gradle.org/distributions/gradle-1.6-all.zip", distributionUrl);
  }

  public void testGetPropertiesFilePath() {
    File projectPath = Projects.getBaseDirPath(myProject);
    File wrapperPath = GradleWrapper.getDefaultPropertiesFilePath(projectPath);

    List<String> expected = Lists.newArrayList(splitPath(projectPath.getPath()));
    expected.addAll(splitPath(FD_GRADLE_WRAPPER));
    expected.add(FN_GRADLE_WRAPPER_PROPERTIES);

    assertEquals(expected, splitPath(wrapperPath.getPath()));
  }

  public void testCreateWithSpecificGradleVersion() throws IOException {
    File projectPath = Projects.getBaseDirPath(myProject);
    File projectWrapperDirPath = new File(projectPath, FD_GRADLE_WRAPPER);
    assertFalse(projectWrapperDirPath.exists());

    String gradleVersion = "1.5";
    GradleWrapper gradleWrapper = GradleWrapper.create(projectPath, gradleVersion);
    assertNotNull(gradleWrapper);
    assertWrapperCreated(projectWrapperDirPath, gradleVersion);
  }

  // http://b.android.com/218575
  public void testCreateWithoutSpecificGradleVersion() throws IOException {
    File projectPath = Projects.getBaseDirPath(myProject);
    File projectWrapperDirPath = new File(projectPath, FD_GRADLE_WRAPPER);
    assertFalse(projectWrapperDirPath.exists());

    GradleWrapper gradleWrapper = GradleWrapper.create(projectPath);
    assertNotNull(gradleWrapper);
    assertWrapperCreated(projectWrapperDirPath, SdkConstants.GRADLE_LATEST_VERSION);
  }

  // See https://code.google.com/p/android/issues/detail?id=357944
  private static void assertWrapperCreated(@NotNull File projectWrapperFolderPath, @NotNull String gradleVersion) throws IOException {
    assertTrue(projectWrapperFolderPath.isDirectory());
    File[] wrapperFiles = notNullize(projectWrapperFolderPath.listFiles());
    assertEquals(2, wrapperFiles.length);

    Properties gradleProperties = getProperties(new File(projectWrapperFolderPath, FN_GRADLE_WRAPPER_PROPERTIES));
    String distributionUrl = gradleProperties.getProperty(DISTRIBUTION_URL_PROPERTY);
    String folderName = GradleWrapper.isSnapshot(gradleVersion) ? "distributions-snapshots" : "distributions";
    assertEquals("https://services.gradle.org/" + folderName + "/gradle-" + gradleVersion + "-all.zip", distributionUrl);
  }

  public void testGetDistributionUrlWithBinReleaseVersion() {
    String url = GradleWrapper.getDistributionUrl("4.0", true /* bin only */);
    assertEquals("https://services.gradle.org/distributions/gradle-4.0-bin.zip", url);
  }

  public void testGetDistributionUrlWithAllReleaseVersion() {
    String url = GradleWrapper.getDistributionUrl("4.0", false /* all */);
    assertEquals("https://services.gradle.org/distributions/gradle-4.0-all.zip", url);
  }

  public void testGetDistributionUrlWithBinSnapshotVersion() {
    String url = GradleWrapper.getDistributionUrl("4.0-20170406000015+0000", true /* bin only */);
    assertEquals("https://services.gradle.org/distributions-snapshots/gradle-4.0-20170406000015+0000-bin.zip", url);
  }

  public void testGetDistributionUrlWithAllSnapshotVersion() {
    String url = GradleWrapper.getDistributionUrl("4.0-20170406000015+0000", false /* all */);
    assertEquals("https://services.gradle.org/distributions-snapshots/gradle-4.0-20170406000015+0000-all.zip", url);
  }
}