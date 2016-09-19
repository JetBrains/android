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
import com.google.common.collect.Lists;
import org.gradle.tooling.model.UnsupportedMethodException;
import org.junit.After;
import org.junit.Test;

import java.io.File;
import java.util.Collection;
import java.util.List;

import static com.android.SdkConstants.FN_BUILD_GRADLE;
import static com.google.common.io.Files.createTempDir;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.util.io.FileUtil.delete;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

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

  @Test
  public void getGeneratedSources() {
    Collection<File> folders = Lists.newArrayList(new File(""));
    BaseArtifact baseArtifact = mock(BaseArtifact.class);
    when(baseArtifact.getGeneratedSourceFolders()).thenReturn(folders);

    Collection<File> actual = GradleUtil.getGeneratedSourceFolders(baseArtifact);
    assertThat(actual).isSameAs(folders);
  }

  @Test
  public void getGeneratedSourcesWithOldModel() {
    BaseArtifact baseArtifact = mock(BaseArtifact.class);
    when(baseArtifact.getGeneratedSourceFolders()).thenThrow(new UnsupportedMethodException(""));

    Collection<File> actual = GradleUtil.getGeneratedSourceFolders(baseArtifact);
    assertThat(actual).isEmpty();
  }

  @Test
  public void supportsDependencyGraph() {
    assertTrue(GradleUtil.androidModelSupportsDependencyGraph(GradleVersion.parse("2.2.0-dev")));
    assertTrue(GradleUtil.androidModelSupportsDependencyGraph(GradleVersion.parse("2.2.0")));
    assertTrue(GradleUtil.androidModelSupportsDependencyGraph(GradleVersion.parse("2.2.1")));
    assertTrue(GradleUtil.androidModelSupportsDependencyGraph(GradleVersion.parse("2.3.0")));
    assertTrue(GradleUtil.androidModelSupportsDependencyGraph(GradleVersion.parse("2.3+")));
    assertTrue(GradleUtil.androidModelSupportsDependencyGraph(GradleVersion.parse("3.0.0")));
  }

  @Test
  public void supportsDependencyGraphWithTextVersion() {
    assertFalse(GradleUtil.androidModelSupportsDependencyGraph("abc."));
    assertTrue(GradleUtil.androidModelSupportsDependencyGraph("2.2.0-dev"));
    assertTrue(GradleUtil.androidModelSupportsDependencyGraph("2.2.0"));
    assertTrue(GradleUtil.androidModelSupportsDependencyGraph("2.2.1"));
    assertTrue(GradleUtil.androidModelSupportsDependencyGraph("2.3.0"));
    assertTrue(GradleUtil.androidModelSupportsDependencyGraph("2.3+"));
    assertTrue(GradleUtil.androidModelSupportsDependencyGraph("3.0.0"));
  }

  @Test
  public void getDependenciesWithModelThatSupportsDependencyGraph() {
    BaseArtifact artifact = mock(BaseArtifact.class);
    Dependencies dependencies = mock(Dependencies.class);

    when(artifact.getCompileDependencies()).thenReturn(dependencies);

    Dependencies actual = GradleUtil.getDependencies(artifact, GradleVersion.parse("2.2.0"));
    assertSame(dependencies, actual);

    verify(artifact).getCompileDependencies();
  }

  @SuppressWarnings("deprecation")
  @Test
  public void getDependenciesWithModelThatDoesNotSupportDependencyGraph() {
    BaseArtifact artifact = mock(BaseArtifact.class);
    Dependencies dependencies = mock(Dependencies.class);

    when(artifact.getDependencies()).thenReturn(dependencies);

    Dependencies actual = GradleUtil.getDependencies(artifact, GradleVersion.parse("1.2.0"));
    assertSame(dependencies, actual);

    verify(artifact).getDependencies();
  }

  @Test
  public void getGradleInvocationJvmArgWithNullBuildMode() {
    assertNull(GradleUtil.getGradleInvocationJvmArg(null));
  }

  @Test
  public void getGradleInvocationJvmArgWithAssembleTranslateBuildMode() {
    assertEquals("-DenableTranslation=true", GradleUtil.getGradleInvocationJvmArg(BuildMode.ASSEMBLE_TRANSLATE));
  }

  @Test
  public void getPathSegments() {
    List<String> pathSegments = GradleUtil.getPathSegments("foo:bar:baz");
    assertEquals(Lists.newArrayList("foo", "bar", "baz"), pathSegments);
  }

  @Test
  public void getPathSegmentsWithEmptyString() {
    List<String> pathSegments = GradleUtil.getPathSegments("");
    assertEquals(0, pathSegments.size());
  }

  @Test
  public void getGradleBuildFilePath() {
    myTempDir = createTempDir();
    File buildFilePath = GradleUtil.getGradleBuildFilePath(myTempDir);
    assertEquals(new File(myTempDir, FN_BUILD_GRADLE), buildFilePath);
  }

  @Test
  public void getGradleWrapperVersionWithUrl() {
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
  public void hasLayoutRenderingIssue() {
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
