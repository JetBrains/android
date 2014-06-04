/*
 * Copyright (C) 2013 The Android Open Source Project
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

import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.google.common.io.Closeables;
import com.google.common.io.Files;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import junit.framework.TestCase;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Properties;

import static com.android.SdkConstants.*;

/**
 * Tests for {@link GradleUtil}.
 */
public class GradleUtilTest extends TestCase {
  private File myTempDir;

  @Override
  protected void tearDown() throws Exception {
    if (myTempDir != null) {
      FileUtil.delete(myTempDir);
    }
    super.tearDown();
  }

  public void testGetGradleInvocationJvmArgWithNullBuildMode() {
    assertNull(GradleUtil.getGradleInvocationJvmArg(null));
  }

  public void testGetGradleInvocationJvmArgWithAssembleTranslateBuildMode() {
    assertEquals("-DenableTranslation=true" ,GradleUtil.getGradleInvocationJvmArg(BuildMode.ASSEMBLE_TRANSLATE));
  }

  public void testGetGradleWrapperPropertiesFilePath() throws IOException {
    myTempDir = Files.createTempDir();
    File wrapper = new File(myTempDir, FN_GRADLE_WRAPPER_PROPERTIES);
    FileUtilRt.createIfNotExists(wrapper);
    GradleUtil.updateGradleDistributionUrl("1.6", wrapper);

    Properties properties = new Properties();
    FileInputStream fileInputStream = null;
    try {
      //noinspection IOResourceOpenedButNotSafelyClosed
      fileInputStream = new FileInputStream(wrapper);
      properties.load(fileInputStream);
      String distributionUrl = properties.getProperty("distributionUrl");
      assertEquals("http://services.gradle.org/distributions/gradle-1.6-all.zip", distributionUrl);
    }
    finally {
      Closeables.closeQuietly(fileInputStream);
    }
  }

  public void testLeaveGradleWrapperAloneBin() throws IOException {
    // Ensure that if we already have the right version, we don't replace a -bin.zip with a -all.zip
    myTempDir = Files.createTempDir();
    File wrapper = new File(myTempDir, FN_GRADLE_WRAPPER_PROPERTIES);
    Files.write("#Wed Apr 10 15:27:10 PDT 2013\n" +
                "distributionBase=GRADLE_USER_HOME\n" +
                "distributionPath=wrapper/dists\n" +
                "zipStoreBase=GRADLE_USER_HOME\n" +
                "zipStorePath=wrapper/dists\n" +
                "distributionUrl=http\\://services.gradle.org/distributions/gradle-1.9-bin.zip", wrapper, Charsets.UTF_8);
    GradleUtil.updateGradleDistributionUrl("1.9", wrapper);

    Properties properties = new Properties();
    FileInputStream fileInputStream = null;
    try {
      //noinspection IOResourceOpenedButNotSafelyClosed
      fileInputStream = new FileInputStream(wrapper);
      properties.load(fileInputStream);
      String distributionUrl = properties.getProperty("distributionUrl");
      assertEquals("http://services.gradle.org/distributions/gradle-1.9-bin.zip", distributionUrl);
    }
    finally {
      Closeables.closeQuietly(fileInputStream);
    }
  }

  public void testLeaveGradleWrapperAloneAll() throws IOException {
    // Ensure that if we already have the right version, we don't replace a -all.zip with a -bin.zip
    myTempDir = Files.createTempDir();
    File wrapper = new File(myTempDir, FN_GRADLE_WRAPPER_PROPERTIES);
    Files.write("#Wed Apr 10 15:27:10 PDT 2013\n" +
                "distributionBase=GRADLE_USER_HOME\n" +
                "distributionPath=wrapper/dists\n" +
                "zipStoreBase=GRADLE_USER_HOME\n" +
                "zipStorePath=wrapper/dists\n" +
                "distributionUrl=http\\://services.gradle.org/distributions/gradle-1.9-all.zip", wrapper, Charsets.UTF_8);
    GradleUtil.updateGradleDistributionUrl("1.9", wrapper);

    Properties properties = new Properties();
    FileInputStream fileInputStream = null;
    try {
      //noinspection IOResourceOpenedButNotSafelyClosed
      fileInputStream = new FileInputStream(wrapper);
      properties.load(fileInputStream);
      String distributionUrl = properties.getProperty("distributionUrl");
      assertEquals("http://services.gradle.org/distributions/gradle-1.9-all.zip", distributionUrl);
    }
    finally {
      Closeables.closeQuietly(fileInputStream);
    }
  }

  public void testReplaceGradleWrapper() throws IOException {
    // Test that when we replace to a new version we use -all.zip
    myTempDir = Files.createTempDir();
    File wrapper = new File(myTempDir, FN_GRADLE_WRAPPER_PROPERTIES);
    Files.write("#Wed Apr 10 15:27:10 PDT 2013\n" +
                "distributionBase=GRADLE_USER_HOME\n" +
                "distributionPath=wrapper/dists\n" +
                "zipStoreBase=GRADLE_USER_HOME\n" +
                "zipStorePath=wrapper/dists\n" +
                "distributionUrl=http\\://services.gradle.org/distributions/gradle-1.9-bin.zip", wrapper, Charsets.UTF_8);
    GradleUtil.updateGradleDistributionUrl("1.6", wrapper);

    Properties properties = new Properties();
    FileInputStream fileInputStream = null;
    try {
      //noinspection IOResourceOpenedButNotSafelyClosed
      fileInputStream = new FileInputStream(wrapper);
      properties.load(fileInputStream);
      String distributionUrl = properties.getProperty("distributionUrl");
      assertEquals("http://services.gradle.org/distributions/gradle-1.6-all.zip", distributionUrl);
    }
    finally {
      Closeables.closeQuietly(fileInputStream);
    }
  }

  public void testUpdateGradleDistributionUrl() {
    myTempDir = Files.createTempDir();
    File wrapperPath = GradleUtil.getGradleWrapperPropertiesFilePath(myTempDir);

    List<String> expected = Lists.newArrayList(FileUtil.splitPath(myTempDir.getPath()));
    expected.addAll(FileUtil.splitPath(FD_GRADLE_WRAPPER));
    expected.add(FN_GRADLE_WRAPPER_PROPERTIES);

    assertEquals(expected, FileUtil.splitPath(wrapperPath.getPath()));
  }

  public void testGetPathSegments() {
    List<String> pathSegments = GradleUtil.getPathSegments("foo:bar:baz");
    assertEquals(Lists.newArrayList("foo", "bar", "baz"), pathSegments);
  }

  public void testGetPathSegmentsWithEmptyString() {
    List<String> pathSegments = GradleUtil.getPathSegments("");
    assertEquals(0, pathSegments.size());
  }

  public void testGetGradleBuildFilePath() {
    myTempDir = Files.createTempDir();
    File buildFilePath = GradleUtil.getGradleBuildFilePath(myTempDir);
    assertEquals(new File(myTempDir, FN_BUILD_GRADLE), buildFilePath);
  }
}
