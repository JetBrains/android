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
package com.android.tools.idea.gradle.project;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import junit.framework.TestCase;

import java.io.File;
import java.io.IOException;

/**
 * Tests for {@link GradleVersions}.
 */
public class GradleVersionsTest extends TestCase {
  private File myTempDir;

  @Override
  protected void tearDown() throws Exception {
    if (myTempDir != null) {
      FileUtil.delete(myTempDir);
    }
    super.tearDown();
  }

  public void testGetGradleVersionWithHomeDirectoryFromRelease() {
    File gradleHome = new File("gradle-1.7");
    String gradleVersion = GradleVersions.getGradleVersion(gradleHome);
    assertEquals("1.7", gradleVersion);
  }

  public void testGetGradleVersionWithHomeDirectoryFromNightly() {
    File gradleHome = new File("gradle-1.8-20130829182645+0000");
    String gradleVersion = GradleVersions.getGradleVersion(gradleHome);
    assertEquals("1.8", gradleVersion);
  }

  public void testGetGradleVersionWithHomeDirectoryFromReleaseCandidate() {
    File gradleHome = new File("gradle-1.8-rc1");
    String gradleVersion = GradleVersions.getGradleVersion(gradleHome);
    assertEquals("1.8", gradleVersion);
  }

  public void testGetGradleVersionByCheckingJarsInHomeDirectory() throws IOException {
    myTempDir = FileUtil.createTempDirectory("test", null);
    File libDir = new File(myTempDir, "lib");
    FileUtil.createDirectory(libDir);
    FileUtilRt.createIfNotExists(new File(libDir, "asm-4.0.jar"));
    FileUtilRt.createIfNotExists(new File(libDir, "gradle-core-1.6.jar"));
    String gradleVersion = GradleVersions.getGradleVersion(myTempDir);
    assertEquals("1.6", gradleVersion);
  }

  public void testGetGradleVersionFromJarFileFromRelease() {
    File jarFile = new File("gradle-core-1.6.jar");
    String gradleVersion = GradleVersions.getGradleVersionFromJarFile(jarFile);
    assertEquals("1.6", gradleVersion);
  }

  public void testGetGradleVersionFromJarFileFromNightly() {
    File jarFile = new File("gradle-core-1.8-20130829182645+0000.jar");
    String gradleVersion = GradleVersions.getGradleVersionFromJarFile(jarFile);
    assertEquals("1.8", gradleVersion);
  }

  public void testGetGradleVersionFromJarFileFromReleaseCandidate() {
    File jarFile = new File("gradle-core-1.7-rc1.jar");
    String gradleVersion = GradleVersions.getGradleVersionFromJarFile(jarFile);
    assertEquals("1.7", gradleVersion);
  }
}
