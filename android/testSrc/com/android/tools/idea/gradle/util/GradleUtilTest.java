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

import com.android.ide.common.repository.GradleVersion;
import com.google.common.collect.Lists;
import org.junit.After;
import org.junit.Test;

import java.io.File;
import java.util.List;

import static com.android.SdkConstants.FN_BUILD_GRADLE;
import static com.google.common.io.Files.createTempDir;
import static com.intellij.openapi.util.io.FileUtil.delete;
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
  public void mapConfigurationName() {
    assertEquals("compile", GradleUtil.mapConfigurationName("compile", "2.3.2", false));
    assertEquals("testCompile", GradleUtil.mapConfigurationName("testCompile", "2.3.2", false));
    assertEquals("androidTestCompile", GradleUtil.mapConfigurationName("androidTestCompile", "2.3.2", false));
    assertEquals("provided", GradleUtil.mapConfigurationName("provided", "2.3.2", false));
    assertEquals("testProvided", GradleUtil.mapConfigurationName("testProvided", "2.3.2", false));

    assertEquals("implementation", GradleUtil.mapConfigurationName("compile", "3.0.0-alpha1", false));
    assertEquals("testImplementation", GradleUtil.mapConfigurationName("testCompile", "3.0.0-alpha1", false));
    assertEquals("androidTestImplementation", GradleUtil.mapConfigurationName("androidTestCompile", "3.0.0-alpha1", false));
    assertEquals("compileOnly", GradleUtil.mapConfigurationName("provided", "3.0.0-alpha1, false", false));
    assertEquals("testCompileOnly", GradleUtil.mapConfigurationName("testProvided", "3.0.0-alpha1", false));

    assertEquals("api", GradleUtil.mapConfigurationName("compile", "3.0.0-alpha1", true));
    assertEquals("testApi", GradleUtil.mapConfigurationName("testCompile", "3.0.0-alpha1", true));
    assertEquals("androidTestApi", GradleUtil.mapConfigurationName("androidTestCompile", "3.0.0-alpha1", true));
    assertEquals("compileOnly", GradleUtil.mapConfigurationName("provided", "3.0.0-alpha1", true));
    assertEquals("testCompileOnly", GradleUtil.mapConfigurationName("testProvided", "3.0.0-alpha1", true));
  }

  @Test
  public void useCompatibilityConfigurationNames() {
    assertTrue(GradleUtil.useCompatibilityConfigurationNames(GradleVersion.parse("2.3.2")));
    assertFalse(GradleUtil.useCompatibilityConfigurationNames((GradleVersion)null));
    assertFalse(GradleUtil.useCompatibilityConfigurationNames(GradleVersion.parse("3.0.0-alpha1")));
    assertFalse(GradleUtil.useCompatibilityConfigurationNames(GradleVersion.parse("3.0.0")));
    assertFalse(GradleUtil.useCompatibilityConfigurationNames(GradleVersion.parse("4.0.0")));
  }
}
