/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, GradleVersion 2.0 (the "License");
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

import com.android.builder.model.BaseArtifact;
import com.android.builder.model.Dependencies;
import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.gradle.stubs.android.AndroidProjectStub;
import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import org.junit.After;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Properties;

import static com.android.SdkConstants.*;
import static com.android.tools.idea.gradle.util.PropertiesUtil.getProperties;
import static com.google.common.io.Files.createTempDir;
import static com.google.common.io.Files.write;
import static com.intellij.openapi.util.io.FileUtil.*;
import static org.mockito.Mockito.*;
import static org.junit.Assert.*;

/**
 * Tests for {@link GradleUtil}.
 */
public class GradleUtilTest {
  private File myTempDir;

  @After
  public void tearDown() {
    if (myTempDir != null) {
      delete(myTempDir);
    }
  }

  @Ignore("Enable when BaseArtifact#getCompileDependencies is submitted")
  @Test
  public void testSupportsDependencyGraph() {
    assertTrue(GradleUtil.androidModelSupportsDependencyGraph(GradleVersion.parse("2.2.0-dev")));
    assertTrue(GradleUtil.androidModelSupportsDependencyGraph(GradleVersion.parse("2.2.0")));
    assertTrue(GradleUtil.androidModelSupportsDependencyGraph(GradleVersion.parse("2.2.1")));
    assertTrue(GradleUtil.androidModelSupportsDependencyGraph(GradleVersion.parse("2.3.0")));
    assertTrue(GradleUtil.androidModelSupportsDependencyGraph(GradleVersion.parse("2.3+")));
    assertTrue(GradleUtil.androidModelSupportsDependencyGraph(GradleVersion.parse("3.0.0")));
  }

  @Ignore("Enable when BaseArtifact#getCompileDependencies is submitted")
  @Test
  public void testSupportsDependencyGraphWithTextVersion() {
    assertFalse(GradleUtil.androidModelSupportsDependencyGraph("abc."));
    assertTrue(GradleUtil.androidModelSupportsDependencyGraph("2.2.0-dev"));
    assertTrue(GradleUtil.androidModelSupportsDependencyGraph("2.2.0"));
    assertTrue(GradleUtil.androidModelSupportsDependencyGraph("2.2.1"));
    assertTrue(GradleUtil.androidModelSupportsDependencyGraph("2.3.0"));
    assertTrue(GradleUtil.androidModelSupportsDependencyGraph("2.3+"));
    assertTrue(GradleUtil.androidModelSupportsDependencyGraph("3.0.0"));
  }

  @Ignore("Enable when BaseArtifact#getCompileDependencies is submitted")
  @Test
  public void testGetDependenciesWithModelThatSupportsDependencyGraph() {
    BaseArtifact artifact = mock(BaseArtifact.class);
    Dependencies dependencies = mock(Dependencies.class);

    when(artifact.getCompileDependencies()).thenReturn(dependencies);

    Dependencies actual = GradleUtil.getDependencies(artifact, GradleVersion.parse("2.2.0"));
    assertSame(dependencies, actual);

    verify(artifact).getCompileDependencies();
  }

  @Test
  public void testGetDependenciesWithModelThatDoesNotSupportDependencyGraph() {
    BaseArtifact artifact = mock(BaseArtifact.class);
    Dependencies dependencies = mock(Dependencies.class);

    when(artifact.getDependencies()).thenReturn(dependencies);

    Dependencies actual = GradleUtil.getDependencies(artifact, GradleVersion.parse("1.2.0"));
    assertSame(dependencies, actual);

    verify(artifact).getDependencies();
  }

  @Test
  public void testGetGradleInvocationJvmArgWithNullBuildMode() {
    assertNull(GradleUtil.getGradleInvocationJvmArg(null));
  }

  @Test
  public void testGetGradleInvocationJvmArgWithAssembleTranslateBuildMode() {
    assertEquals("-DenableTranslation=true", GradleUtil.getGradleInvocationJvmArg(BuildMode.ASSEMBLE_TRANSLATE));
  }

  @Test
  public void testGetGradleWrapperPropertiesFilePath() throws IOException {
    myTempDir = createTempDir();
    File wrapper = new File(myTempDir, FN_GRADLE_WRAPPER_PROPERTIES);
    createIfNotExists(wrapper);
    GradleUtil.updateGradleDistributionUrl("1.6", wrapper);

    Properties properties = getProperties(wrapper);
    String distributionUrl = properties.getProperty("distributionUrl");
    assertEquals("https://services.gradle.org/distributions/gradle-1.6-all.zip", distributionUrl);
  }

  @Test
  public void testLeaveGradleWrapperAloneBin() throws IOException {
    // Ensure that if we already have the right version, we don't replace a -bin.zip with a -all.zip
    myTempDir = createTempDir();
    File wrapper = new File(myTempDir, FN_GRADLE_WRAPPER_PROPERTIES);
    write("#Wed Apr 10 15:27:10 PDT 2013\n" +
          "distributionBase=GRADLE_USER_HOME\n" +
          "distributionPath=wrapper/dists\n" +
          "zipStoreBase=GRADLE_USER_HOME\n" +
          "zipStorePath=wrapper/dists\n" +
          "distributionUrl=https\\://services.gradle.org/distributions/gradle-1.9-bin.zip", wrapper, Charsets.UTF_8);
    GradleUtil.updateGradleDistributionUrl("1.9", wrapper);

    Properties properties = getProperties(wrapper);
    String distributionUrl = properties.getProperty("distributionUrl");
    assertEquals("https://services.gradle.org/distributions/gradle-1.9-bin.zip", distributionUrl);
  }

  @Test
  public void testLeaveGradleWrapperAloneAll() throws IOException {
    // Ensure that if we already have the right version, we don't replace a -all.zip with a -bin.zip
    myTempDir = createTempDir();
    File wrapper = new File(myTempDir, FN_GRADLE_WRAPPER_PROPERTIES);
    write("#Wed Apr 10 15:27:10 PDT 2013\n" +
          "distributionBase=GRADLE_USER_HOME\n" +
          "distributionPath=wrapper/dists\n" +
          "zipStoreBase=GRADLE_USER_HOME\n" +
          "zipStorePath=wrapper/dists\n" +
          "distributionUrl=https\\://services.gradle.org/distributions/gradle-1.9-all.zip", wrapper, Charsets.UTF_8);
    GradleUtil.updateGradleDistributionUrl("1.9", wrapper);

    Properties properties = getProperties(wrapper);
    String distributionUrl = properties.getProperty("distributionUrl");
    assertEquals("https://services.gradle.org/distributions/gradle-1.9-all.zip", distributionUrl);
  }

  @Test
  public void testReplaceGradleWrapper() throws IOException {
    // Test that when we replace to a new version we use -all.zip
    myTempDir = createTempDir();
    File wrapper = new File(myTempDir, FN_GRADLE_WRAPPER_PROPERTIES);
    write("#Wed Apr 10 15:27:10 PDT 2013\n" +
          "distributionBase=GRADLE_USER_HOME\n" +
          "distributionPath=wrapper/dists\n" +
          "zipStoreBase=GRADLE_USER_HOME\n" +
          "zipStorePath=wrapper/dists\n" +
          "distributionUrl=https\\://services.gradle.org/distributions/gradle-1.9-bin.zip", wrapper, Charsets.UTF_8);
    GradleUtil.updateGradleDistributionUrl("1.6", wrapper);

    Properties properties = getProperties(wrapper);
    String distributionUrl = properties.getProperty("distributionUrl");
    assertEquals("https://services.gradle.org/distributions/gradle-1.6-all.zip", distributionUrl);
  }

  @Test
  public void testUpdateGradleDistributionUrl() {
    myTempDir = createTempDir();
    File wrapperPath = GradleUtil.getGradleWrapperPropertiesFilePath(myTempDir);

    List<String> expected = Lists.newArrayList(splitPath(myTempDir.getPath()));
    expected.addAll(splitPath(FD_GRADLE_WRAPPER));
    expected.add(FN_GRADLE_WRAPPER_PROPERTIES);

    assertEquals(expected, splitPath(wrapperPath.getPath()));
  }

  @Test
  public void testGetPathSegments() {
    List<String> pathSegments = GradleUtil.getPathSegments("foo:bar:baz");
    assertEquals(Lists.newArrayList("foo", "bar", "baz"), pathSegments);
  }

  @Test
  public void testGetPathSegmentsWithEmptyString() {
    List<String> pathSegments = GradleUtil.getPathSegments("");
    assertEquals(0, pathSegments.size());
  }

  @Test
  public void testGetGradleBuildFilePath() {
    myTempDir = createTempDir();
    File buildFilePath = GradleUtil.getGradleBuildFilePath(myTempDir);
    assertEquals(new File(myTempDir, FN_BUILD_GRADLE), buildFilePath);
  }

  @Test
  public void testGetGradleVersionFromJarUsingGradleLibraryJar() {
    File jarFile = new File("gradle-core-2.0.jar");
    GradleVersion gradleVersion = GradleUtil.getGradleVersionFromJar(jarFile);
    assertNotNull(gradleVersion);
    assertEquals(GradleVersion.parse("2.0"), gradleVersion);
  }

  @Test
  public void testRc() {
    // Regression test for https://code.google.com/p/android/issues/detail?id=179838
    File jarFile = new File("gradle-messaging-2.5-rc-1.jar");
    GradleVersion gradleVersion = GradleUtil.getGradleVersionFromJar(jarFile);
    assertNotNull(gradleVersion);
    assertEquals(GradleVersion.parse("2.5"), gradleVersion);
  }

  @Test
  public void testNightly() {
    File jarFile = new File("gradle-messaging-2.10-20151029230024+0000.jar");
    GradleVersion gradleVersion = GradleUtil.getGradleVersionFromJar(jarFile);
    assertNotNull(gradleVersion);
    assertEquals(GradleVersion.parse("2.10"), gradleVersion);
  }

  @Test
  public void testGetGradleVersionFromJarUsingNonGradleLibraryJar() {
    File jarFile = new File("ant-1.9.3.jar");
    GradleVersion gradleVersion = GradleUtil.getGradleVersionFromJar(jarFile);
    assertNull(gradleVersion);
  }

  @Test
  public void testAddInitScriptCommandLineOption() throws IOException {
    List<String> cmdOptions = Lists.newArrayList();

    String contents = "The contents of the init script file";
    File initScriptPath = GradleUtil.addInitScriptCommandLineOption("name", contents, cmdOptions);
    assertNotNull(initScriptPath);

    assertEquals(2, cmdOptions.size());
    assertEquals("--init-script", cmdOptions.get(0));
    assertEquals(initScriptPath.getPath(), cmdOptions.get(1));

    String initScript = loadFile(initScriptPath);
    assertEquals(contents, initScript);
  }

  @Test
  public void testGetGradleWrapperVersionWithUrl() {
    // Tries both http and https, bin and all. Also versions 2.2.1, 2.2 and 1.12
    String url = "https://services.gradle.org/distributions/gradle-2.2.1-all.zip";
    String version = GradleUtil.getGradleWrapperVersionOnlyIfComingForGradleDotOrg(url);
    assertEquals("2.2.1", version);

    url = "https://services.gradle.org/distributions/gradle-2.2.1-bin.zip";
    version = GradleUtil.getGradleWrapperVersionOnlyIfComingForGradleDotOrg(url);
    assertEquals("2.2.1", version);

    url = "http://services.gradle.org/distributions/gradle-2.2.1-all.zip";
    version = GradleUtil.getGradleWrapperVersionOnlyIfComingForGradleDotOrg(url);
    assertEquals("2.2.1", version);

    url = "http://services.gradle.org/distributions/gradle-2.2.1-bin.zip";
    version = GradleUtil.getGradleWrapperVersionOnlyIfComingForGradleDotOrg(url);
    assertEquals("2.2.1", version);

    url = "https://services.gradle.org/distributions/gradle-2.2-all.zip";
    version = GradleUtil.getGradleWrapperVersionOnlyIfComingForGradleDotOrg(url);
    assertEquals("2.2", version);

    url = "https://services.gradle.org/distributions/gradle-2.2-bin.zip";
    version = GradleUtil.getGradleWrapperVersionOnlyIfComingForGradleDotOrg(url);
    assertEquals("2.2", version);

    url = "http://services.gradle.org/distributions/gradle-2.2-all.zip";
    version = GradleUtil.getGradleWrapperVersionOnlyIfComingForGradleDotOrg(url);
    assertEquals("2.2", version);

    url = "http://services.gradle.org/distributions/gradle-2.2-bin.zip";
    version = GradleUtil.getGradleWrapperVersionOnlyIfComingForGradleDotOrg(url);
    assertEquals("2.2", version);

    url = "https://services.gradle.org/distributions/gradle-1.12-all.zip";
    version = GradleUtil.getGradleWrapperVersionOnlyIfComingForGradleDotOrg(url);
    assertEquals("1.12", version);

    url = "https://services.gradle.org/distributions/gradle-1.12-bin.zip";
    version = GradleUtil.getGradleWrapperVersionOnlyIfComingForGradleDotOrg(url);
    assertEquals("1.12", version);

    url = "http://services.gradle.org/distributions/gradle-1.12-all.zip";
    version = GradleUtil.getGradleWrapperVersionOnlyIfComingForGradleDotOrg(url);
    assertEquals("1.12", version);

    url = "http://services.gradle.org/distributions/gradle-1.12-bin.zip";
    version = GradleUtil.getGradleWrapperVersionOnlyIfComingForGradleDotOrg(url);
    assertEquals("1.12", version);

    // Use custom URL.
    url = "http://myown.com/gradle-2.2.1-bin.zip";
    version = GradleUtil.getGradleWrapperVersionOnlyIfComingForGradleDotOrg(url);
    assertNull(version);
  }

  @Test
  public void testHasLayoutRenderingIssue() {
    AndroidProjectStub model = new AndroidProjectStub("app");

    model.setModelVersion("1.1.0");
    assertFalse(GradleUtil.hasLayoutRenderingIssue(model));

    model.setModelVersion("1.2.0");
    assertTrue(GradleUtil.hasLayoutRenderingIssue(model));

    model.setModelVersion("1.2.1");
    assertTrue(GradleUtil.hasLayoutRenderingIssue(model));

    model.setModelVersion("1.2.2");
    assertTrue(GradleUtil.hasLayoutRenderingIssue(model));

    model.setModelVersion("1.2.3");
    assertFalse(GradleUtil.hasLayoutRenderingIssue(model));
  }
}
