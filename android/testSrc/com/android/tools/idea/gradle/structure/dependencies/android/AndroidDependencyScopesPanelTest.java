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
package com.android.tools.idea.gradle.structure.dependencies.android;

import com.android.tools.idea.gradle.structure.model.android.PsBuildType;
import com.android.tools.idea.gradle.structure.model.android.PsProductFlavor;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static com.android.tools.idea.gradle.structure.dependencies.android.Configuration.*;
import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link AndroidDependencyScopesPanel}.
 */
public class AndroidDependencyScopesPanelTest {
  private PsBuildType myDebugBuildType;
  private PsBuildType myReleaseBuildType;

  @Before
  public void setUp() {
    myDebugBuildType = buildType("debug");
    myReleaseBuildType = buildType("release");
  }
  
  @Test
  public void deduceScopes1() {
    List<Configuration> configurations = Lists.newArrayList(MAIN);
    List<PsBuildType> buildTypes = Lists.newArrayList(myDebugBuildType, myReleaseBuildType);
    List<PsProductFlavor> productFlavors = Collections.emptyList();

    List<String> scopes = AndroidDependencyScopesPanel.deduceScopes(configurations, buildTypes, productFlavors, true, true);
    assertThat(scopes).containsOnly("compile");
  }

  @Test
  public void deduceScopes2() {
    List<Configuration> configurations = Lists.newArrayList(ANDROID_TEST);
    List<PsBuildType> buildTypes = Lists.newArrayList(myDebugBuildType, myReleaseBuildType);
    List<PsProductFlavor> productFlavors = Collections.emptyList();

    List<String> scopes = AndroidDependencyScopesPanel.deduceScopes(configurations, buildTypes, productFlavors, true, true);
    assertThat(scopes).containsOnly("androidTestCompile");
  }

  @Test
  public void deduceScopes4() {
    List<Configuration> configurations = Lists.newArrayList(UNIT_TEST);
    List<PsBuildType> buildTypes = Lists.newArrayList(myDebugBuildType, myReleaseBuildType);
    List<PsProductFlavor> productFlavors = Collections.emptyList();

    List<String> scopes = AndroidDependencyScopesPanel.deduceScopes(configurations, buildTypes, productFlavors, true, true);
    assertThat(scopes).containsOnly("testCompile");
  }

  @Test
  public void deduceScopes5() {
    List<Configuration> configurations = Lists.newArrayList(ANDROID_TEST, UNIT_TEST);
    List<PsBuildType> buildTypes = Lists.newArrayList(myDebugBuildType, myReleaseBuildType);
    List<PsProductFlavor> productFlavors = Collections.emptyList();

    List<String> scopes = AndroidDependencyScopesPanel.deduceScopes(configurations, buildTypes, productFlavors, true, true);
    assertThat(scopes).containsOnly("androidTestCompile", "testCompile");
  }

  @Test
  public void deduceScopes6() {
    List<Configuration> configurations = Lists.newArrayList(MAIN);
    List<PsBuildType> buildTypes = Lists.newArrayList(myDebugBuildType, myReleaseBuildType);
    List<PsProductFlavor> productFlavors = Lists.newArrayList(productFlavor("flavor1"));

    List<String> scopes = AndroidDependencyScopesPanel.deduceScopes(configurations, buildTypes, productFlavors, true, false);
    assertThat(scopes).containsOnly("flavor1Compile");
  }

  @Test
  public void deduceScopes7() {
    List<Configuration> configurations = Lists.newArrayList(ANDROID_TEST);
    List<PsBuildType> buildTypes = Lists.newArrayList(myDebugBuildType, myReleaseBuildType);
    List<PsProductFlavor> productFlavors = Lists.newArrayList(productFlavor("flavor1"));

    List<String> scopes = AndroidDependencyScopesPanel.deduceScopes(configurations, buildTypes, productFlavors, true, false);
    assertThat(scopes).containsOnly("androidTestFlavor1Compile");
  }

  @Test
  public void deduceScopes8() {
    List<Configuration> configurations = Lists.newArrayList(UNIT_TEST);
    List<PsBuildType> buildTypes = Lists.newArrayList(myDebugBuildType, myReleaseBuildType);
    List<PsProductFlavor> productFlavors = Lists.newArrayList(productFlavor("flavor1"));

    List<String> scopes = AndroidDependencyScopesPanel.deduceScopes(configurations, buildTypes, productFlavors, true, false);
    assertThat(scopes).containsOnly("testFlavor1Compile");
  }

  @Test
  public void deduceScopes9() {
    List<Configuration> configurations = Lists.newArrayList(ANDROID_TEST);
    List<PsBuildType> buildTypes = Lists.newArrayList(myDebugBuildType);
    List<PsProductFlavor> productFlavors = Lists.newArrayList(productFlavor("flavor1"));

    List<String> scopes = AndroidDependencyScopesPanel.deduceScopes(configurations, buildTypes, productFlavors, false, false);
    assertThat(scopes).containsOnly("androidTestFlavor1Compile");
  }

  @Test
  public void deduceScopes10() {
    List<Configuration> configurations = Lists.newArrayList(UNIT_TEST);
    List<PsBuildType> buildTypes = Lists.newArrayList(myDebugBuildType);
    List<PsProductFlavor> productFlavors = Lists.newArrayList(productFlavor("flavor1"));

    List<String> scopes = AndroidDependencyScopesPanel.deduceScopes(configurations, buildTypes, productFlavors, false, false);
    assertThat(scopes).containsOnly("testDebugCompile", "testFlavor1Compile");
  }
  @Test
  public void deduceScopes11() {
    List<Configuration> configurations = Lists.newArrayList(ANDROID_TEST);
    List<PsBuildType> buildTypes = Lists.newArrayList(myReleaseBuildType);
    List<PsProductFlavor> productFlavors = Lists.newArrayList(productFlavor("flavor1"));

    List<String> scopes = AndroidDependencyScopesPanel.deduceScopes(configurations, buildTypes, productFlavors, false, false);
    assertThat(scopes).isEmpty();
  }

  @NotNull
  private static PsBuildType buildType(@NotNull String name) {
    PsBuildType buildType = mock(PsBuildType.class);
    when(buildType.getName()).thenReturn(name);
    return buildType;
  }

  @NotNull
  private static PsProductFlavor productFlavor(@NotNull String name) {
    PsProductFlavor productFlavor = mock(PsProductFlavor.class);
    when(productFlavor.getName()).thenReturn(name);
    return productFlavor;
  }}