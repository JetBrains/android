/*
 * Copyright (C) 2022 The Android Open Source Project
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

import org.gradle.util.GradleVersion;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * Tests for {@link GradleVersions}.
 */
public class GradleVersionsUnitTest {
  @Test
  public void getGradleVersionFromJarUsingGradleLibraryJar() {
    File jarFile = new File("gradle-core-2.0.jar");
    GradleVersion gradleVersion = GradleVersions.getGradleVersionFromJar(jarFile);
    assertNotNull(gradleVersion);
    assertEquals(GradleVersion.version("2.0"), gradleVersion);
  }

  @Test
  public void rc() {
    // Regression test for https://code.google.com/p/android/issues/detail?id=179838
    File jarFile = new File("gradle-core-2.5-rc-1.jar");
    GradleVersion gradleVersion = GradleVersions.getGradleVersionFromJar(jarFile);
    assertNotNull(gradleVersion);
    assertEquals(GradleVersion.version("2.5"), gradleVersion);
  }

  @Test
  public void nightly() {
    File jarFile = new File("gradle-core-2.10-20151029230024+0000.jar");
    GradleVersion gradleVersion = GradleVersions.getGradleVersionFromJar(jarFile);
    assertNotNull(gradleVersion);
    assertEquals(GradleVersion.version("2.10"), gradleVersion);
  }

  @Test
  public void getGradleVersionFromJarUsingNonGradleLibraryJar() {
    File jarFile = new File("ant-1.9.3.jar");
    GradleVersion gradleVersion = GradleVersions.getGradleVersionFromJar(jarFile);
    assertNull(gradleVersion);
  }

  @Test
  public void getGradleVersionFromApiJar() {
    File jarFile = new File("gradle-core-api-6.0.jar");
    GradleVersion gradleVersion = GradleVersions.getGradleVersionFromJar(jarFile);
    assertNull(gradleVersion);
  }

  @Test
  public void getGradleVersionWithMultipleMinors() {
    File jarFile = new File("gradle-core-6.2.3.jar");
    GradleVersion gradleVersion = GradleVersions.getGradleVersionFromJar(jarFile);
    assertNotNull(gradleVersion);
    assertEquals(GradleVersion.version("6.2.3"), gradleVersion);
  }

  @Test
  public void getGradleVersionWithoutMinors() {
    File jarFile = new File("gradle-core-6.jar");
    GradleVersion gradleVersion = GradleVersions.getGradleVersionFromJar(jarFile);
    assertNull(gradleVersion);
  }
}
