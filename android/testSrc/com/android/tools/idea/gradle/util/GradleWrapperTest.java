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

import static com.android.SdkConstants.FD_GRADLE_WRAPPER;
import static com.android.SdkConstants.FN_GRADLE_WRAPPER_PROPERTIES;
import static com.android.tools.idea.gradle.util.PropertiesFiles.getProperties;
import static com.intellij.openapi.util.io.FileUtil.notNullize;
import static com.intellij.openapi.util.io.FileUtil.splitPath;
import static com.intellij.openapi.util.io.FileUtilRt.createIfNotExists;
import static org.gradle.wrapper.WrapperExecutor.DISTRIBUTION_URL_PROPERTY;

import com.android.SdkConstants;
import com.android.Version;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.gradle.plugin.LatestKnownPluginVersionProvider;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.project.ProjectStoreOwner;
import com.intellij.testFramework.PlatformTestCase;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.jetbrains.annotations.NotNull;

public class GradleWrapperTest extends PlatformTestCase {
  public void testUpdateDistributionUrl() throws IOException {
    File projectPath = getProjectBaseDir();
    File wrapperFilePath = new File(projectPath, FN_GRADLE_WRAPPER_PROPERTIES);
    createIfNotExists(wrapperFilePath);

    GradleWrapper gradleWrapper = GradleWrapper.get(wrapperFilePath, myProject);
    gradleWrapper.updateDistributionUrlAndDisplayFailure("1.6");

    Properties properties = getProperties(wrapperFilePath);
    String distributionUrl = properties.getProperty("distributionUrl");
    assertEquals("https://services.gradle.org/distributions/gradle-1.6-bin.zip", distributionUrl);
  }

  public void testUpdateDistributionUrlLeavesGradleWrapperAloneAll() throws IOException {
    // Ensure that if we already have the right version, we don't replace a -all.zip with a -bin.zip
    File projectPath = getProjectBaseDir();
    File wrapperFilePath = new File(projectPath, FN_GRADLE_WRAPPER_PROPERTIES);
    writeWrapperProperties(
      "#Wed Apr 10 15:27:10 PDT 2013\n" +
      "distributionBase=GRADLE_USER_HOME\n" +
      "distributionPath=wrapper/dists\n" +
      "zipStoreBase=GRADLE_USER_HOME\n" +
      "zipStorePath=wrapper/dists\n" +
      "distributionUrl=https\\://services.gradle.org/distributions/gradle-1.9-all.zip",
      wrapperFilePath
    );

    GradleWrapper gradleWrapper = GradleWrapper.get(wrapperFilePath, myProject);
    gradleWrapper.updateDistributionUrlAndDisplayFailure("1.9");

    Properties properties = getProperties(wrapperFilePath);
    String distributionUrl = properties.getProperty("distributionUrl");
    assertEquals("https://services.gradle.org/distributions/gradle-1.9-bin.zip", distributionUrl);
  }


  public void testUpdateDistributionUrlLeavesGradleWrapperAloneBin() throws IOException {
    // Ensure that if we already have the right version, we don't replace a -bin.zip with a -all.zip
    File projectPath = getProjectBaseDir();
    File wrapperFilePath = new File(projectPath, FN_GRADLE_WRAPPER_PROPERTIES);
    writeWrapperProperties(
      "#Wed Apr 10 15:27:10 PDT 2013\n" +
      "distributionBase=GRADLE_USER_HOME\n" +
      "distributionPath=wrapper/dists\n" +
      "zipStoreBase=GRADLE_USER_HOME\n" +
      "zipStorePath=wrapper/dists\n" +
      "distributionUrl=https\\://services.gradle.org/distributions/gradle-1.9-bin.zip",
      wrapperFilePath
    );

    GradleWrapper gradleWrapper = GradleWrapper.get(wrapperFilePath, myProject);
    gradleWrapper.updateDistributionUrlAndDisplayFailure("1.9");

    Properties properties = getProperties(wrapperFilePath);
    String distributionUrl = properties.getProperty("distributionUrl");
    assertEquals("https://services.gradle.org/distributions/gradle-1.9-bin.zip", distributionUrl);
  }

  public void testUpdateDistributionUrlReplacesGradleWrapper() throws IOException {
    // Test that when we replace to a new version we use -all.zip
    File projectPath = getProjectBaseDir();
    File wrapperFilePath = new File(projectPath, FN_GRADLE_WRAPPER_PROPERTIES);
    writeWrapperProperties(
      "#Wed Apr 10 15:27:10 PDT 2013\n" +
      "distributionBase=GRADLE_USER_HOME\n" +
      "distributionPath=wrapper/dists\n" +
      "zipStoreBase=GRADLE_USER_HOME\n" +
      "zipStorePath=wrapper/dists\n" +
      "distributionUrl=https\\://services.gradle.org/distributions/gradle-1.9-bin.zip",
      wrapperFilePath
    );

    GradleWrapper gradleWrapper = GradleWrapper.get(wrapperFilePath, myProject);
    gradleWrapper.updateDistributionUrlAndDisplayFailure("1.6");

    Properties properties = getProperties(wrapperFilePath);
    String distributionUrl = properties.getProperty("distributionUrl");
    assertEquals("https://services.gradle.org/distributions/gradle-1.6-bin.zip", distributionUrl);
  }

  public void testAgpVersionToUse() {
    String specifiedVersion = "7.2.0-alpha03";
    StudioFlags.AGP_VERSION_TO_USE.override(specifiedVersion);

    assertEquals(specifiedVersion, LatestKnownPluginVersionProvider.INSTANCE.get());
    assertEquals("7.3.3", GradleWrapper.getGradleVersionToUse());

    StudioFlags.AGP_VERSION_TO_USE.override("");
    assertEquals(Version.ANDROID_GRADLE_PLUGIN_VERSION, LatestKnownPluginVersionProvider.INSTANCE.get());
    assertEquals(SdkConstants.GRADLE_LATEST_VERSION, GradleWrapper.getGradleVersionToUse());
  }

  public void testLocalDistributionUrl() {
    String version = "1.2.3";
    boolean binOnly = true;

    String localPath = "file:///some/local/path/";
    StudioFlags.GRADLE_LOCAL_DISTRIBUTION_URL.override(localPath);
    assertEquals(localPath + "gradle-1.2.3-bin.zip", GradleWrapper.getDistributionUrl(version, binOnly));

    StudioFlags.GRADLE_LOCAL_DISTRIBUTION_URL.override("");
    assertEquals("https://services.gradle.org/distributions/gradle-1.2.3-bin.zip", GradleWrapper.getDistributionUrl(version, binOnly));
  }

  public void testUpdateDistributionUrlUpgradeGradleWrapper() throws IOException {
    // Test when we have a local/unofficial Gradle version, we can upgrade to a new official version.
    File projectPath = getProjectBaseDir();
    File wrapperFilePath = new File(projectPath, FN_GRADLE_WRAPPER_PROPERTIES);
    writeWrapperProperties(
      "#Tue Feb 19 10:20:30 PDT 2019\n" +
      "distributionBase=GRADLE_USER_HOME\n" +
      "distributionPath=wrapper/dists\n" +
      "zipStoreBase=GRADLE_USER_HOME\n" +
      "zipStorePath=wrapper/dists\n" +
      "distributionUrl=file\\:/usr/local/home/studio-master-dev/tools/external/gradle/gradle-4.5-bin.zip",
      wrapperFilePath
    );

    GradleWrapper gradlewrapper = GradleWrapper.get(wrapperFilePath, myProject);
    gradlewrapper.updateDistributionUrlAndDisplayFailure("6.1.1");

    Properties properties = getProperties(wrapperFilePath);
    String distributionUrl = properties.getProperty("distributionUrl");
    assertEquals("https://services.gradle.org/distributions/gradle-6.1.1-bin.zip", distributionUrl);
  }

  public void testUpdatedDistributionUrlMissing() throws IOException {
    // Test when we have no Gradle version specified, we can upgrade to a new official version.
    File projectPath = getProjectBaseDir();
    File wrapperFilePath = new File(projectPath, FN_GRADLE_WRAPPER_PROPERTIES);
    writeWrapperProperties(
      "#Tue Feb 19 10:20:30 PDT 2019\n" +
      "distributionBase=GRADLE_USER_HOME\n" +
      "distributionPath=wrapper/dists\n" +
      "zipStoreBase=GRADLE_USER_HOME\n" +
      "zipStorePath=wrapper/dists\n",
      wrapperFilePath
    );

    GradleWrapper gradlewrapper = GradleWrapper.get(wrapperFilePath, myProject);
    String updatedUrl = gradlewrapper.getUpdatedDistributionUrl("6.1.1", true);
    assertEquals("https://services.gradle.org/distributions/gradle-6.1.1-bin.zip", updatedUrl);
    String updatedPreviewUrl = gradlewrapper.getUpdatedDistributionUrl("6.7-rc-4", true);
    assertEquals("https://services.gradle.org/distributions/gradle-6.7-rc-4-bin.zip", updatedPreviewUrl);
    String updatedSnapshotUrl = gradlewrapper.getUpdatedDistributionUrl("7.0-alpha-1+0000", true);
    assertEquals("https://services.gradle.org/distributions-snapshots/gradle-7.0-alpha-1+0000-bin.zip", updatedSnapshotUrl);

    updatedUrl = gradlewrapper.getUpdatedDistributionUrl("6.1.1", false);
    assertEquals("https://services.gradle.org/distributions/gradle-6.1.1-all.zip", updatedUrl);
    updatedPreviewUrl = gradlewrapper.getUpdatedDistributionUrl("6.7-rc-4", false);
    assertEquals("https://services.gradle.org/distributions/gradle-6.7-rc-4-all.zip", updatedPreviewUrl);
    updatedSnapshotUrl = gradlewrapper.getUpdatedDistributionUrl("7.0-alpha-1+0000", false);
    assertEquals("https://services.gradle.org/distributions-snapshots/gradle-7.0-alpha-1+0000-all.zip", updatedSnapshotUrl);
  }

  public void testUpdatedDistributionUrlFromStandard() throws IOException {
    // Test when we have a standard Gradle version, we can upgrade to a new official version.
    File projectPath = getProjectBaseDir();
    File wrapperFilePath = new File(projectPath, FN_GRADLE_WRAPPER_PROPERTIES);
    writeWrapperProperties(
      "#Tue Feb 19 10:20:30 PDT 2019\n" +
      "distributionBase=GRADLE_USER_HOME\n" +
      "distributionPath=wrapper/dists\n" +
      "zipStoreBase=GRADLE_USER_HOME\n" +
      "zipStorePath=wrapper/dists\n" +
      "distributionUrl=https\\://services.gradle.org/distributions/gradle-1.9-bin.zip",
      wrapperFilePath
    );

    GradleWrapper gradlewrapper = GradleWrapper.get(wrapperFilePath, myProject);
    String updatedUrl = gradlewrapper.getUpdatedDistributionUrl("6.1.1", true);
    assertEquals("https://services.gradle.org/distributions/gradle-6.1.1-bin.zip", updatedUrl);
    String updatedPreviewUrl = gradlewrapper.getUpdatedDistributionUrl("6.7-rc-4", true);
    assertEquals("https://services.gradle.org/distributions/gradle-6.7-rc-4-bin.zip", updatedPreviewUrl);
    String updatedSnapshotUrl = gradlewrapper.getUpdatedDistributionUrl("7.0-alpha-1+0000", true);
    assertEquals("https://services.gradle.org/distributions-snapshots/gradle-7.0-alpha-1+0000-bin.zip", updatedSnapshotUrl);

    updatedUrl = gradlewrapper.getUpdatedDistributionUrl("6.1.1", false);
    assertEquals("https://services.gradle.org/distributions/gradle-6.1.1-bin.zip", updatedUrl);
    updatedPreviewUrl = gradlewrapper.getUpdatedDistributionUrl("6.7-rc-4", false);
    assertEquals("https://services.gradle.org/distributions/gradle-6.7-rc-4-bin.zip", updatedPreviewUrl);
    updatedSnapshotUrl = gradlewrapper.getUpdatedDistributionUrl("7.0-alpha-1+0000", false);
    assertEquals("https://services.gradle.org/distributions-snapshots/gradle-7.0-alpha-1+0000-bin.zip", updatedSnapshotUrl);
  }

  public void testUpdatedDistributionUrlFromStandardAll() throws IOException {
    // Test when we have a local/unofficial Gradle version, we can upgrade to a new official version.
    File projectPath = getProjectBaseDir();
    File wrapperFilePath = new File(projectPath, FN_GRADLE_WRAPPER_PROPERTIES);
    writeWrapperProperties(
      "#Tue Feb 19 10:20:30 PDT 2019\n" +
      "distributionBase=GRADLE_USER_HOME\n" +
      "distributionPath=wrapper/dists\n" +
      "zipStoreBase=GRADLE_USER_HOME\n" +
      "zipStorePath=wrapper/dists\n" +
      "distributionUrl=https\\://services.gradle.org/distributions/gradle-1.9-all.zip",
      wrapperFilePath
    );

    GradleWrapper gradlewrapper = GradleWrapper.get(wrapperFilePath, myProject);
    String updatedUrl = gradlewrapper.getUpdatedDistributionUrl("6.1.1", true);
    assertEquals("https://services.gradle.org/distributions/gradle-6.1.1-all.zip", updatedUrl);
    String updatedPreviewUrl = gradlewrapper.getUpdatedDistributionUrl("6.7-rc-4", true);
    assertEquals("https://services.gradle.org/distributions/gradle-6.7-rc-4-all.zip", updatedPreviewUrl);
    String updatedSnapshotUrl = gradlewrapper.getUpdatedDistributionUrl("7.0-alpha-1+0000", true);
    assertEquals("https://services.gradle.org/distributions-snapshots/gradle-7.0-alpha-1+0000-all.zip", updatedSnapshotUrl);

    updatedUrl = gradlewrapper.getUpdatedDistributionUrl("6.1.1", false);
    assertEquals("https://services.gradle.org/distributions/gradle-6.1.1-all.zip", updatedUrl);
    updatedPreviewUrl = gradlewrapper.getUpdatedDistributionUrl("6.7-rc-4", false);
    assertEquals("https://services.gradle.org/distributions/gradle-6.7-rc-4-all.zip", updatedPreviewUrl);
    updatedSnapshotUrl = gradlewrapper.getUpdatedDistributionUrl("7.0-alpha-1+0000", false);
    assertEquals("https://services.gradle.org/distributions-snapshots/gradle-7.0-alpha-1+0000-all.zip", updatedSnapshotUrl);
  }

  public void testUpdatedDistributionUrlFromStandardPreview() throws IOException {
    // Test when we have a preview Gradle version, we can upgrade to a new official version.
    File projectPath = getProjectBaseDir();
    File wrapperFilePath = new File(projectPath, FN_GRADLE_WRAPPER_PROPERTIES);
    writeWrapperProperties(
      "#Tue Feb 19 10:20:30 PDT 2019\n" +
      "distributionBase=GRADLE_USER_HOME\n" +
      "distributionPath=wrapper/dists\n" +
      "zipStoreBase=GRADLE_USER_HOME\n" +
      "zipStorePath=wrapper/dists\n" +
      "distributionUrl=https\\://services.gradle.org/distributions/gradle-1.9-rc-3-bin.zip",
      wrapperFilePath
    );

    GradleWrapper gradlewrapper = GradleWrapper.get(wrapperFilePath, myProject);
    String updatedUrl = gradlewrapper.getUpdatedDistributionUrl("6.1.1", true);
    assertEquals("https://services.gradle.org/distributions/gradle-6.1.1-bin.zip", updatedUrl);
    String updatedPreviewUrl = gradlewrapper.getUpdatedDistributionUrl("6.7-rc-4", true);
    assertEquals("https://services.gradle.org/distributions/gradle-6.7-rc-4-bin.zip", updatedPreviewUrl);
    String updatedSnapshotUrl = gradlewrapper.getUpdatedDistributionUrl("7.0-alpha-1+0000", true);
    assertEquals("https://services.gradle.org/distributions-snapshots/gradle-7.0-alpha-1+0000-bin.zip", updatedSnapshotUrl);

    updatedUrl = gradlewrapper.getUpdatedDistributionUrl("6.1.1", false);
    assertEquals("https://services.gradle.org/distributions/gradle-6.1.1-bin.zip", updatedUrl);
    updatedPreviewUrl = gradlewrapper.getUpdatedDistributionUrl("6.7-rc-4", false);
    assertEquals("https://services.gradle.org/distributions/gradle-6.7-rc-4-bin.zip", updatedPreviewUrl);
    updatedSnapshotUrl = gradlewrapper.getUpdatedDistributionUrl("7.0-alpha-1+0000", false);
    assertEquals("https://services.gradle.org/distributions-snapshots/gradle-7.0-alpha-1+0000-bin.zip", updatedSnapshotUrl);
  }

  public void testUpdatedDistributionUrlFromStandardSnapshot() throws IOException {
    // Test when we have a snapshot Gradle version, we can upgrade to a new official version.
    File projectPath = getProjectBaseDir();
    File wrapperFilePath = new File(projectPath, FN_GRADLE_WRAPPER_PROPERTIES);
    writeWrapperProperties(
      "#Tue Feb 19 10:20:30 PDT 2019\n" +
      "distributionBase=GRADLE_USER_HOME\n" +
      "distributionPath=wrapper/dists\n" +
      "zipStoreBase=GRADLE_USER_HOME\n" +
      "zipStorePath=wrapper/dists\n" +
      "distributionUrl=https\\://services.gradle.org/distributions-snapshots/gradle-1.9-rc-3+0000-bin.zip",
      wrapperFilePath
    );

    GradleWrapper gradlewrapper = GradleWrapper.get(wrapperFilePath, myProject);
    String updatedUrl = gradlewrapper.getUpdatedDistributionUrl("6.1.1", true);
    assertEquals("https://services.gradle.org/distributions/gradle-6.1.1-bin.zip", updatedUrl);
    String updatedPreviewUrl = gradlewrapper.getUpdatedDistributionUrl("6.7-rc-4", true);
    assertEquals("https://services.gradle.org/distributions/gradle-6.7-rc-4-bin.zip", updatedPreviewUrl);
    String updatedSnapshotUrl = gradlewrapper.getUpdatedDistributionUrl("7.0-alpha-1+0000", true);
    assertEquals("https://services.gradle.org/distributions-snapshots/gradle-7.0-alpha-1+0000-bin.zip", updatedSnapshotUrl);

    updatedUrl = gradlewrapper.getUpdatedDistributionUrl("6.1.1", false);
    assertEquals("https://services.gradle.org/distributions/gradle-6.1.1-bin.zip", updatedUrl);
    updatedPreviewUrl = gradlewrapper.getUpdatedDistributionUrl("6.7-rc-4", false);
    assertEquals("https://services.gradle.org/distributions/gradle-6.7-rc-4-bin.zip", updatedPreviewUrl);
    updatedSnapshotUrl = gradlewrapper.getUpdatedDistributionUrl("7.0-alpha-1+0000", false);
    assertEquals("https://services.gradle.org/distributions-snapshots/gradle-7.0-alpha-1+0000-bin.zip", updatedSnapshotUrl);
  }

  public void testUpdatedDistributionUrlFromStandardUnparsed() throws IOException {
    // Test when we have a distributionUrl pointing to gradle.org but not of the form that we understand, we
    // produce our standard form.
    File projectPath = getProjectBaseDir();
    File wrapperFilePath = new File(projectPath, FN_GRADLE_WRAPPER_PROPERTIES);
    writeWrapperProperties(
      "#Tue Feb 19 10:20:30 PDT 2019\n" +
      "distributionBase=GRADLE_USER_HOME\n" +
      "distributionPath=wrapper/dists\n" +
      "zipStoreBase=GRADLE_USER_HOME\n" +
      "zipStorePath=wrapper/dists\n" +
      "distributionUrl=https\\://services.gradle.org/gradle.zip",
      wrapperFilePath
    );

    GradleWrapper gradlewrapper = GradleWrapper.get(wrapperFilePath, myProject);
    String updatedUrl = gradlewrapper.getUpdatedDistributionUrl("6.1.1", true);
    assertEquals("https://services.gradle.org/distributions/gradle-6.1.1-bin.zip", updatedUrl);
    String updatedPreviewUrl = gradlewrapper.getUpdatedDistributionUrl("6.7-rc-4", true);
    assertEquals("https://services.gradle.org/distributions/gradle-6.7-rc-4-bin.zip", updatedPreviewUrl);
    String updatedSnapshotUrl = gradlewrapper.getUpdatedDistributionUrl("7.0-alpha-1+0000", true);
    assertEquals("https://services.gradle.org/distributions-snapshots/gradle-7.0-alpha-1+0000-bin.zip", updatedSnapshotUrl);

    updatedUrl = gradlewrapper.getUpdatedDistributionUrl("6.1.1", false);
    assertEquals("https://services.gradle.org/distributions/gradle-6.1.1-all.zip", updatedUrl);
    updatedPreviewUrl = gradlewrapper.getUpdatedDistributionUrl("6.7-rc-4", false);
    assertEquals("https://services.gradle.org/distributions/gradle-6.7-rc-4-all.zip", updatedPreviewUrl);
    updatedSnapshotUrl = gradlewrapper.getUpdatedDistributionUrl("7.0-alpha-1+0000", false);
    assertEquals("https://services.gradle.org/distributions-snapshots/gradle-7.0-alpha-1+0000-all.zip", updatedSnapshotUrl);
  }

  public void testUpdatedDistributionUrlFromFile() throws IOException {
    // Test when we have a local/unofficial Gradle version, we can upgrade to a new local version.
    File projectPath = getProjectBaseDir();
    File wrapperFilePath = new File(projectPath, FN_GRADLE_WRAPPER_PROPERTIES);
    writeWrapperProperties(
      "#Tue Feb 19 10:20:30 PDT 2019\n" +
      "distributionBase=GRADLE_USER_HOME\n" +
      "distributionPath=wrapper/dists\n" +
      "zipStoreBase=GRADLE_USER_HOME\n" +
      "zipStorePath=wrapper/dists\n" +
      "distributionUrl=file\\:/usr/local/home/studio-master-dev/tools/external/gradle/gradle-4.5-bin.zip",
      wrapperFilePath
    );

    GradleWrapper gradlewrapper = GradleWrapper.get(wrapperFilePath, myProject);
    String updatedUrl = gradlewrapper.getUpdatedDistributionUrl("6.1.1", true);
    assertEquals("file:/usr/local/home/studio-master-dev/tools/external/gradle/gradle-6.1.1-bin.zip", updatedUrl);
    String updatedPreviewUrl = gradlewrapper.getUpdatedDistributionUrl("6.7-rc-4", true);
    assertEquals("file:/usr/local/home/studio-master-dev/tools/external/gradle/gradle-6.7-rc-4-bin.zip", updatedPreviewUrl);
    String updatedSnapshotUrl = gradlewrapper.getUpdatedDistributionUrl("7.0-alpha-1+0000", true);
    assertEquals("file:/usr/local/home/studio-master-dev/tools/external/gradle/gradle-7.0-alpha-1+0000-bin.zip", updatedSnapshotUrl);

    updatedUrl = gradlewrapper.getUpdatedDistributionUrl("6.1.1", false);
    assertEquals("file:/usr/local/home/studio-master-dev/tools/external/gradle/gradle-6.1.1-bin.zip", updatedUrl);
    updatedPreviewUrl = gradlewrapper.getUpdatedDistributionUrl("6.7-rc-4", false);
    assertEquals("file:/usr/local/home/studio-master-dev/tools/external/gradle/gradle-6.7-rc-4-bin.zip", updatedPreviewUrl);
    updatedSnapshotUrl = gradlewrapper.getUpdatedDistributionUrl("7.0-alpha-1+0000", false);
    assertEquals("file:/usr/local/home/studio-master-dev/tools/external/gradle/gradle-7.0-alpha-1+0000-bin.zip", updatedSnapshotUrl);
  }

  public void testUpdatedDistributionUrlFromFileAll() throws IOException {
    // Test when we have a local/unofficial Gradle version, we can upgrade to a new official version.
    File projectPath = getProjectBaseDir();
    File wrapperFilePath = new File(projectPath, FN_GRADLE_WRAPPER_PROPERTIES);
    writeWrapperProperties(
      "#Tue Feb 19 10:20:30 PDT 2019\n" +
      "distributionBase=GRADLE_USER_HOME\n" +
      "distributionPath=wrapper/dists\n" +
      "zipStoreBase=GRADLE_USER_HOME\n" +
      "zipStorePath=wrapper/dists\n" +
      "distributionUrl=file\\:/usr/local/home/studio-master-dev/tools/external/gradle/gradle-4.5-all.zip",
      wrapperFilePath
    );

    GradleWrapper gradlewrapper = GradleWrapper.get(wrapperFilePath, myProject);
    String updatedUrl = gradlewrapper.getUpdatedDistributionUrl("6.1.1", true);
    assertEquals("file:/usr/local/home/studio-master-dev/tools/external/gradle/gradle-6.1.1-all.zip", updatedUrl);
    String updatedPreviewUrl = gradlewrapper.getUpdatedDistributionUrl("6.7-rc-4", true);
    assertEquals("file:/usr/local/home/studio-master-dev/tools/external/gradle/gradle-6.7-rc-4-all.zip", updatedPreviewUrl);
    String updatedSnapshotUrl = gradlewrapper.getUpdatedDistributionUrl("7.0-alpha-1+0000", true);
    assertEquals("file:/usr/local/home/studio-master-dev/tools/external/gradle/gradle-7.0-alpha-1+0000-all.zip", updatedSnapshotUrl);

    updatedUrl = gradlewrapper.getUpdatedDistributionUrl("6.1.1", false);
    assertEquals("file:/usr/local/home/studio-master-dev/tools/external/gradle/gradle-6.1.1-all.zip", updatedUrl);
    updatedPreviewUrl = gradlewrapper.getUpdatedDistributionUrl("6.7-rc-4", false);
    assertEquals("file:/usr/local/home/studio-master-dev/tools/external/gradle/gradle-6.7-rc-4-all.zip", updatedPreviewUrl);
    updatedSnapshotUrl = gradlewrapper.getUpdatedDistributionUrl("7.0-alpha-1+0000", false);
    assertEquals("file:/usr/local/home/studio-master-dev/tools/external/gradle/gradle-7.0-alpha-1+0000-all.zip", updatedSnapshotUrl);
  }

  public void testUpdatedDistributionUrlFromUnparsedFile() throws IOException {
    // Test when we have a local/unofficial Gradle version, we can upgrade to a new local version.
    File projectPath = getProjectBaseDir();
    File wrapperFilePath = new File(projectPath, FN_GRADLE_WRAPPER_PROPERTIES);
    writeWrapperProperties(
      "#Tue Feb 19 10:20:30 PDT 2019\n" +
      "distributionBase=GRADLE_USER_HOME\n" +
      "distributionPath=wrapper/dists\n" +
      "zipStoreBase=GRADLE_USER_HOME\n" +
      "zipStorePath=wrapper/dists\n" +
      "distributionUrl=file\\:/usr/local/home/studio-master-dev/tools/external/gradle/gradle.zip",
      wrapperFilePath
    );

    GradleWrapper gradlewrapper = GradleWrapper.get(wrapperFilePath, myProject);
    String updatedUrl = gradlewrapper.getUpdatedDistributionUrl("6.1.1", true);
    assertEquals("https://services.gradle.org/distributions/gradle-6.1.1-bin.zip", updatedUrl);
    String updatedPreviewUrl = gradlewrapper.getUpdatedDistributionUrl("6.7-rc-4", true);
    assertEquals("https://services.gradle.org/distributions/gradle-6.7-rc-4-bin.zip", updatedPreviewUrl);
    String updatedSnapshotUrl = gradlewrapper.getUpdatedDistributionUrl("7.0-alpha-1+0000", true);
    assertEquals("https://services.gradle.org/distributions-snapshots/gradle-7.0-alpha-1+0000-bin.zip", updatedSnapshotUrl);

    updatedUrl = gradlewrapper.getUpdatedDistributionUrl("6.1.1", false);
    assertEquals("https://services.gradle.org/distributions/gradle-6.1.1-all.zip", updatedUrl);
    updatedPreviewUrl = gradlewrapper.getUpdatedDistributionUrl("6.7-rc-4", false);
    assertEquals("https://services.gradle.org/distributions/gradle-6.7-rc-4-all.zip", updatedPreviewUrl);
    updatedSnapshotUrl = gradlewrapper.getUpdatedDistributionUrl("7.0-alpha-1+0000", false);
    assertEquals("https://services.gradle.org/distributions-snapshots/gradle-7.0-alpha-1+0000-all.zip", updatedSnapshotUrl);
  }

  public void testGetPropertiesFilePath() {
    File projectPath = getProjectBaseDir();
    File wrapperPath = GradleWrapper.getDefaultPropertiesFilePath(projectPath);

    List<String> expected = new ArrayList<>(splitPath(projectPath.getPath()));
    expected.addAll(splitPath(FD_GRADLE_WRAPPER));
    expected.add(FN_GRADLE_WRAPPER_PROPERTIES);

    assertEquals(expected, splitPath(wrapperPath.getPath()));
  }

  public void testCreateWithSpecificGradleVersion() throws IOException {
    File projectPath = getProjectBaseDir();
    File projectWrapperDirPath = new File(projectPath, FD_GRADLE_WRAPPER);
    assertFalse(projectWrapperDirPath.exists());

    String gradleVersion = "1.5";
    GradleWrapper gradleWrapper = GradleWrapper.create(projectPath, gradleVersion, myProject);
    assertNotNull(gradleWrapper);
    assertWrapperCreated(projectWrapperDirPath, gradleVersion);
  }

  // http://b.android.com/218575
  public void testCreateWithoutSpecificGradleVersion() throws IOException {
    File projectPath = getProjectBaseDir();
    File projectWrapperDirPath = new File(projectPath, FD_GRADLE_WRAPPER);
    assertFalse(projectWrapperDirPath.exists());

    GradleWrapper gradleWrapper = GradleWrapper.create(projectPath, myProject);
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
    assertEquals("https://services.gradle.org/" + folderName + "/gradle-" + gradleVersion + "-bin.zip", distributionUrl);
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

  @NotNull
  private File getProjectBaseDir() {
    //noinspection UnstableApiUsage
    Path path = ((ProjectStoreOwner)myProject).getComponentStore().getProjectBasePath();
    if (Files.notExists(path)) {
      try {
        Files.createDirectories(path);
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
    return path.toFile();
  }

  private void writeWrapperProperties(String text, File to) throws IOException {
    VirtualFile directory = VfsUtil.createDirectoryIfMissing(to.getParent());
    VirtualFile file = createChildData(directory, to.getName());
    setFileText(file, text);
  }
}
