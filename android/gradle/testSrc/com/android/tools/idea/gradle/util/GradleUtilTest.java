/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static com.android.tools.idea.gradle.util.GradleUtil.getDependencyDisplayName;
import static com.google.common.io.Files.createTempDir;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.util.io.FileUtil.delete;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.android.tools.idea.flags.StudioFlags;
import com.android.utils.FileUtils;
import com.google.common.collect.Lists;
import java.io.File;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Test;

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

    StudioFlags.NAV_SAFE_ARGS_SUPPORT.override(false);
    assertFalse(isRecognizedAsSafeArgClass("generated/source/navigation-args"));

    StudioFlags.NAV_SAFE_ARGS_SUPPORT.override(true);
    // Ignore generated safe arg base-class directory...
    assertTrue(isRecognizedAsSafeArgClass("generated/source/navigation-args"));
    StudioFlags.NAV_SAFE_ARGS_SUPPORT.clearOverride();
  }

  private boolean isRecognizedAsSafeArgClass(@NotNull String path) {
    File dir = new File(myTempDir, FileUtils.toSystemDependentPath(path));
    return GradleProjectSystemUtil.isSafeArgGeneratedSourcesFolder(dir, myTempDir);
  }

  @Test
  public void isDirectChild() {
    assertTrue(GradleUtil.isDirectChild(":app", ":"));
    assertTrue(GradleUtil.isDirectChild(":libs:lib1", ":libs"));
    assertTrue(GradleUtil.isDirectChild(":libs:java:lib2", ":libs:java"));

    assertFalse(GradleUtil.isDirectChild(":", ":"));
    assertFalse(GradleUtil.isDirectChild(":libs:lib1", ":"));
    assertFalse(GradleUtil.isDirectChild(":libs", ":app"));
    assertFalse(GradleUtil.isDirectChild(":libs:lib1", ":app"));
    assertFalse(GradleUtil.isDirectChild(":libs:java:lib2", ":libs"));
    assertFalse(GradleUtil.isDirectChild(":libs:android:lib3", ":libs:java"));
    assertFalse(GradleUtil.isDirectChild(":app", ":app"));
  }

  @Test
  public void getAllParentModulesPaths() {
    assertThat(GradleUtil.getAllParentModulesPaths(":foo:buz")).containsExactly(":foo");
    assertThat(GradleUtil.getAllParentModulesPaths(":foo")).isEmpty();
    assertThat(GradleUtil.getAllParentModulesPaths(":")).isEmpty();
    assertThat(GradleUtil.getAllParentModulesPaths(":foo:bar:buz:lib")).containsExactly(":foo", ":foo:bar", ":foo:bar:buz");
  }

  @Test
  public void getParentModulePath() {
    assertEquals(":foo", GradleUtil.getParentModulePath(":foo:buz"));
    assertEquals(":foo:bar", GradleUtil.getParentModulePath(":foo:bar:buz"));
    assertEquals("", GradleUtil.getParentModulePath(":"));

  }

  @Test
  public void testCreateFullTaskWithTopLevelModule() {
    assertEquals(":assemble", GradleProjectSystemUtil.createFullTaskName(":", "assemble"));
  }

  @Test
  public void testGetDependencyDisplayName() {
    assertThat(getDependencyDisplayName("com.google.guava:guava:11.0.2")).isEqualTo("guava:11.0.2");
    assertThat(getDependencyDisplayName("android.arch.lifecycle:extensions:1.0.0-beta1@aar")).isEqualTo("lifecycle:extensions:1.0.0-beta1");
    assertThat(getDependencyDisplayName("com.android.support.test.espresso:espresso-core:3.0.1@aar")).isEqualTo("espresso-core:3.0.1");
    assertThat(getDependencyDisplayName("foo:bar:1.0")).isEqualTo("foo:bar:1.0");
  }
}
