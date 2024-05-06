/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static com.google.common.io.Files.createTempDir;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.util.io.FileUtil.delete;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.android.utils.FileUtils;
import com.google.common.collect.Lists;
import java.io.File;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Test;

/**
 * Tests for {@link GradleProjectSystemUtil}.
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
  public void getPathSegments() {
    List<String> pathSegments = GradleProjectSystemUtil.getPathSegments("foo:bar:baz");
    assertEquals(Lists.newArrayList("foo", "bar", "baz"), pathSegments);
  }

  @Test
  public void getPathSegmentsWithEmptyString() {
    List<String> pathSegments = GradleProjectSystemUtil.getPathSegments("");
    assertEquals(0, pathSegments.size());
  }

  @Test
  public void getGradleWrapperVersionWithUrl() {
    // Tries both http and https, bin and all. Also versions 2.2.1, 2.2 and 1.12
    String url = "https://services.gradle.org/distributions/gradle-2.2.1-all.zip";
    String version = GradleProjectSystemUtil.getGradleWrapperVersionOnlyIfComingForGradleDotOrg(url);
    assertEquals("2.2.1", version);

    url = "https://services.gradle.org/distributions/gradle-2.2.1-bin.zip";
    version = GradleProjectSystemUtil.getGradleWrapperVersionOnlyIfComingForGradleDotOrg(url);
    assertEquals("2.2.1", version);

    url = "http://services.gradle.org/distributions/gradle-2.2.1-all.zip";
    version = GradleProjectSystemUtil.getGradleWrapperVersionOnlyIfComingForGradleDotOrg(url);
    assertEquals("2.2.1", version);

    url = "http://services.gradle.org/distributions/gradle-2.2.1-bin.zip";
    version = GradleProjectSystemUtil.getGradleWrapperVersionOnlyIfComingForGradleDotOrg(url);
    assertEquals("2.2.1", version);

    url = "https://services.gradle.org/distributions/gradle-2.2-all.zip";
    version = GradleProjectSystemUtil.getGradleWrapperVersionOnlyIfComingForGradleDotOrg(url);
    assertEquals("2.2", version);

    url = "https://services.gradle.org/distributions/gradle-2.2-bin.zip";
    version = GradleProjectSystemUtil.getGradleWrapperVersionOnlyIfComingForGradleDotOrg(url);
    assertEquals("2.2", version);

    url = "http://services.gradle.org/distributions/gradle-2.2-all.zip";
    version = GradleProjectSystemUtil.getGradleWrapperVersionOnlyIfComingForGradleDotOrg(url);
    assertEquals("2.2", version);

    url = "http://services.gradle.org/distributions/gradle-2.2-bin.zip";
    version = GradleProjectSystemUtil.getGradleWrapperVersionOnlyIfComingForGradleDotOrg(url);
    assertEquals("2.2", version);

    url = "https://services.gradle.org/distributions/gradle-1.12-all.zip";
    version = GradleProjectSystemUtil.getGradleWrapperVersionOnlyIfComingForGradleDotOrg(url);
    assertEquals("1.12", version);

    url = "https://services.gradle.org/distributions/gradle-1.12-bin.zip";
    version = GradleProjectSystemUtil.getGradleWrapperVersionOnlyIfComingForGradleDotOrg(url);
    assertEquals("1.12", version);

    url = "http://services.gradle.org/distributions/gradle-1.12-all.zip";
    version = GradleProjectSystemUtil.getGradleWrapperVersionOnlyIfComingForGradleDotOrg(url);
    assertEquals("1.12", version);

    url = "http://services.gradle.org/distributions/gradle-1.12-bin.zip";
    version = GradleProjectSystemUtil.getGradleWrapperVersionOnlyIfComingForGradleDotOrg(url);
    assertEquals("1.12", version);

    // Use custom URL.
    url = "http://myown.com/gradle-2.2.1-bin.zip";
    version = GradleProjectSystemUtil.getGradleWrapperVersionOnlyIfComingForGradleDotOrg(url);
    assertNull(version);
  }

  @Test
  public void isAaptGeneratedSourceFolder() {
    myTempDir = createTempDir();

    // 3.1 and below:
    checkIfRecognizedAsAapt("generated/source/r/debug");
    checkIfRecognizedAsAapt("generated/source/r/flavorOneFlavorTwo/debug");
    checkIfRecognizedAsAapt("generated/source/r/androidTest/debug");
    checkIfRecognizedAsAapt("generated/source/r/androidTest/flavorOneFlavorTwo/debug");

    // 3.2:
    checkIfRecognizedAsAapt("generated/not_namespaced_r_class_sources/debug/processDebugResources/r");
    checkIfRecognizedAsAapt("generated/not_namespaced_r_class_sources/flavorOneFlavorTwoDebug/processDebugResources/r");
    checkIfRecognizedAsAapt("generated/not_namespaced_r_class_sources/debug/generateDebugRFile/out"); // Library projects.
    checkIfRecognizedAsAapt("generated/not_namespaced_r_class_sources/debugAndroidTest/processDebugAndroidTestResources/r");
    checkIfRecognizedAsAapt("generated/not_namespaced_r_class_sources/flavorOneFlavorTwoDebugAndroidTest/processFlavorOneFlavorTwoDebugAndroidTestResources/r");
  }

  private void checkIfRecognizedAsAapt(@NotNull String path) {
    File dir = new File(myTempDir, FileUtils.toSystemDependentPath(path));
    assertTrue(dir + " not recognized as R classes directory.", GradleProjectSystemUtil.isAaptGeneratedSourcesFolder(dir, myTempDir));
  }

  @Test
  public void isDataBindingGeneratedSourceFolder() {
    myTempDir = createTempDir();

    // Ignore generated data binding base-class directory...
    assertTrue(isRecognizedAsDataBindingBaseClass("generated/data_binding_base_class_source_out/debug/dataBindingGenBaseClassesDebug/out"));

    // Do NOT ignore generated data binding classes under other locations
    assertFalse(isRecognizedAsDataBindingBaseClass("generated/source/apt/debug"));
    assertFalse(isRecognizedAsDataBindingBaseClass("generated/source/kapt/debug"));
  }

  private boolean isRecognizedAsDataBindingBaseClass(@NotNull String path) {
    File dir = new File(myTempDir, FileUtils.toSystemDependentPath(path));
    return GradleProjectSystemUtil.isDataBindingGeneratedBaseClassesFolder(dir, myTempDir);
  }

  @Test
  public void isSafeArgGeneratedSourceFolder() {
    myTempDir = createTempDir();

    // Ignore generated safe arg base-class directory...
    assertTrue(isRecognizedAsSafeArgClass("generated/source/navigation-args"));
  }

  private boolean isRecognizedAsSafeArgClass(@NotNull String path) {
    File dir = new File(myTempDir, FileUtils.toSystemDependentPath(path));
    return GradleProjectSystemUtil.isSafeArgGeneratedSourcesFolder(dir, myTempDir);
  }

  @Test
  public void isDirectChild() {
    assertTrue(GradleProjectSystemUtil.isDirectChild(":app", ":"));
    assertTrue(GradleProjectSystemUtil.isDirectChild(":libs:lib1", ":libs"));
    assertTrue(GradleProjectSystemUtil.isDirectChild(":libs:java:lib2", ":libs:java"));

    assertFalse(GradleProjectSystemUtil.isDirectChild(":", ":"));
    assertFalse(GradleProjectSystemUtil.isDirectChild(":libs:lib1", ":"));
    assertFalse(GradleProjectSystemUtil.isDirectChild(":libs", ":app"));
    assertFalse(GradleProjectSystemUtil.isDirectChild(":libs:lib1", ":app"));
    assertFalse(GradleProjectSystemUtil.isDirectChild(":libs:java:lib2", ":libs"));
    assertFalse(GradleProjectSystemUtil.isDirectChild(":libs:android:lib3", ":libs:java"));
    assertFalse(GradleProjectSystemUtil.isDirectChild(":app", ":app"));
  }

  @Test
  public void getAllParentModulesPaths() {
    assertThat(GradleProjectSystemUtil.getAllParentModulesPaths(":foo:buz")).containsExactly(":foo");
    assertThat(GradleProjectSystemUtil.getAllParentModulesPaths(":foo")).isEmpty();
    assertThat(GradleProjectSystemUtil.getAllParentModulesPaths(":")).isEmpty();
    assertThat(GradleProjectSystemUtil.getAllParentModulesPaths(":foo:bar:buz:lib")).containsExactly(":foo", ":foo:bar", ":foo:bar:buz");
  }

  @Test
  public void getParentModulePath() {
    assertEquals(":foo", GradleProjectSystemUtil.getParentModulePath(":foo:buz"));
    assertEquals(":foo:bar", GradleProjectSystemUtil.getParentModulePath(":foo:bar:buz"));
    assertEquals("", GradleProjectSystemUtil.getParentModulePath(":"));

  }

  @Test
  public void testCreateFullTaskWithTopLevelModule() {
    assertEquals(":assemble", GradleProjectSystemUtil.createFullTaskName(":", "assemble"));
  }
}
