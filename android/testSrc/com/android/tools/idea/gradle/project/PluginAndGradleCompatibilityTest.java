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
package com.android.tools.idea.gradle.project;

import com.android.annotations.Nullable;
import com.android.ide.common.repository.GradleVersion;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests for {@link PostProjectSetupTasksExecutor#checkCompatibility(GradleVersion, GradleVersion, boolean)}.
 */
public class PluginAndGradleCompatibilityTest {
  private GradleVersion mySecureGradleVersion;
  private GradleVersion myOldGradleVersion;

  private GradleVersion myLatestStablePluginVersion;
  private PluginVersions mySecureStablePluginVersions;
  private PluginVersions myOldStablePluginVersions;

  private GradleVersion myLatestStableExperimentalPluginVersion;
  private PluginVersions mySecureStableExperimentalPluginVersions;
  private PluginVersions myOldStableExperimentalPluginVersions;

  private GradleVersion myLatestPreviewPluginVersion;
  private PluginVersions mySecurePreviewPluginVersions;
  private PluginVersions myOldPreviewPluginVersions;

  private GradleVersion myLatestPreviewExperimentalPluginVersion;
  private PluginVersions mySecurePreviewExperimentalPluginVersions;
  private PluginVersions myOldPreviewExperimentalPluginVersions;

  @Before
  public void setUp() {
    mySecureGradleVersion = GradleVersion.parse("2.14.1");
    myOldGradleVersion = GradleVersion.parse("2.10");

    myLatestStablePluginVersion = GradleVersion.parse("2.1.3");
    mySecureStablePluginVersions = new PluginVersions(myLatestStablePluginVersion, false);
    myOldStablePluginVersions = new PluginVersions(GradleVersion.parse("2.1.2"), false);

    myLatestStableExperimentalPluginVersion = GradleVersion.parse("0.7.3");
    mySecureStableExperimentalPluginVersions = new PluginVersions(myLatestStableExperimentalPluginVersion, true);
    myOldStableExperimentalPluginVersions = new PluginVersions(GradleVersion.parse("0.7.2"), true);

    myLatestPreviewPluginVersion = GradleVersion.parse("2.2.0-alpha7");
    mySecurePreviewPluginVersions = new PluginVersions(myLatestPreviewPluginVersion, false);
    myOldPreviewPluginVersions = new PluginVersions(GradleVersion.parse("2.2.0-alpha6"), false);

    myLatestPreviewExperimentalPluginVersion = GradleVersion.parse("0.8.0-alpha7");
    mySecurePreviewExperimentalPluginVersions = new PluginVersions(myLatestPreviewExperimentalPluginVersion, true);
    myOldPreviewExperimentalPluginVersions = new PluginVersions(GradleVersion.parse("0.8.0-alpha6"), true);
  }

  @Test
  public void stableNonExperimentalCompatibleWithSecurityFix() {
    Pair<GradleVersion, GradleVersion> result = checkCompatibility(mySecureGradleVersion, mySecureStablePluginVersions);
    assertNull(result);
  }

  @Test
  public void stableExperimentalCompatibleWithSecurityFix() {
    Pair<GradleVersion, GradleVersion> result = checkCompatibility(mySecureGradleVersion, mySecureStableExperimentalPluginVersions);
    assertNull(result);
  }

  @Test
  public void stableNonExperimentalCompatibleWithoutSecurityFix() {
    Pair<GradleVersion, GradleVersion> result = checkCompatibility(myOldGradleVersion, myOldStablePluginVersions);
    assertNull(result);
  }

  @Test
  public void stableExperimentalCompatibleWithoutSecurityFix() {
    Pair<GradleVersion, GradleVersion> result = checkCompatibility(myOldGradleVersion, myOldStableExperimentalPluginVersions);
    assertNull(result);
  }

  @Test
  public void stableNonExperimentalWithSecureGradleAndOldPlugin() {
    Pair<GradleVersion, GradleVersion> result = checkCompatibility(mySecureGradleVersion, myOldStablePluginVersions);
    verifyIncompatibilityUsingStablePlugin(result);
  }

  @Test
  public void stableExperimentalWithSecureGradleAndOldPlugin() {
    Pair<GradleVersion, GradleVersion> result = checkCompatibility(mySecureGradleVersion, myOldStableExperimentalPluginVersions);
    verifyIncompatibilityUsingStableExperimentalPlugin(result);
  }

  @Test
  public void stableNonExperimentalWithSecurePluginAndOldGradle() {
    Pair<GradleVersion, GradleVersion> result = checkCompatibility(myOldGradleVersion, mySecureStablePluginVersions);
    verifyIncompatibilityUsingStablePlugin(result);
  }

  @Test
  public void stableExperimentalWithSecurePluginAndOldGradle() {
    Pair<GradleVersion, GradleVersion> result = checkCompatibility(myOldGradleVersion, mySecureStableExperimentalPluginVersions);
    verifyIncompatibilityUsingStableExperimentalPlugin(result);
  }

  private void verifyIncompatibilityUsingStablePlugin(@Nullable Pair<GradleVersion, GradleVersion> result) {
    assertNotNull(result);
    assertEquals(myLatestStablePluginVersion, result.getFirst());
    assertEquals(mySecureGradleVersion, result.getSecond());
  }

  private void verifyIncompatibilityUsingStableExperimentalPlugin(@Nullable Pair<GradleVersion, GradleVersion> result) {
    assertNotNull(result);
    assertEquals(myLatestStableExperimentalPluginVersion, result.getFirst());
    assertEquals(mySecureGradleVersion, result.getSecond());
  }

  //
  @Test
  public void previewNonExperimentalCompatibleWithSecurityFix() {
    Pair<GradleVersion, GradleVersion> result = checkCompatibility(mySecureGradleVersion, mySecurePreviewPluginVersions);
    assertNull(result);
  }

  @Test
  public void previewExperimentalCompatibleWithSecurityFix() {
    Pair<GradleVersion, GradleVersion> result = checkCompatibility(mySecureGradleVersion, mySecurePreviewExperimentalPluginVersions);
    assertNull(result);
  }

  @Test
  public void previewNonExperimentalCompatibleWithoutSecurityFix() {
    Pair<GradleVersion, GradleVersion> result = checkCompatibility(myOldGradleVersion, myOldPreviewPluginVersions);
    assertNull(result);
  }

  @Test
  public void previewExperimentalCompatibleWithoutSecurityFix() {
    Pair<GradleVersion, GradleVersion> result = checkCompatibility(myOldGradleVersion, myOldPreviewExperimentalPluginVersions);
    assertNull(result);
  }

  @Test
  public void previewNonExperimentalWithSecureGradleAndOldPlugin() {
    Pair<GradleVersion, GradleVersion> result = checkCompatibility(mySecureGradleVersion, myOldPreviewPluginVersions);
    verifyIncompatibilityUsingPreviewPlugin(result);
  }

  @Test
  public void previewExperimentalWithSecureGradleAndOldPlugin() {
    Pair<GradleVersion, GradleVersion> result = checkCompatibility(mySecureGradleVersion, myOldPreviewExperimentalPluginVersions);
    verifyIncompatibilityUsingPreviewExperimentalPlugin(result);
  }

  @Test
  public void previewNonExperimentalWithSecurePluginAndOldGradle() {
    Pair<GradleVersion, GradleVersion> result = checkCompatibility(myOldGradleVersion, mySecurePreviewPluginVersions);
    verifyIncompatibilityUsingPreviewPlugin(result);
  }

  @Test
  public void previewExperimentalWithSecurePluginAndOldGradle() {
    Pair<GradleVersion, GradleVersion> result = checkCompatibility(myOldGradleVersion, mySecurePreviewExperimentalPluginVersions);
    verifyIncompatibilityUsingPreviewExperimentalPlugin(result);
  }

  @Nullable
  private static Pair<GradleVersion, GradleVersion> checkCompatibility(@NotNull GradleVersion gradleVersion,
                                                                       @NotNull PluginVersions pluginVersions) {
    return PostProjectSetupTasksExecutor.checkCompatibility(gradleVersion, pluginVersions.current, pluginVersions.experimental);
  }

  private void verifyIncompatibilityUsingPreviewPlugin(@Nullable Pair<GradleVersion, GradleVersion> result) {
    assertNotNull(result);
    assertEquals(myLatestPreviewPluginVersion, result.getFirst());
    assertEquals(mySecureGradleVersion, result.getSecond());
  }

  private void verifyIncompatibilityUsingPreviewExperimentalPlugin(@Nullable Pair<GradleVersion, GradleVersion> result) {
    assertNotNull(result);
    assertEquals(myLatestPreviewExperimentalPluginVersion, result.getFirst());
    assertEquals(mySecureGradleVersion, result.getSecond());
  }

  private static class PluginVersions {
    @NotNull final GradleVersion current;
    final boolean experimental;

    PluginVersions(@NotNull GradleVersion current, boolean experimental) {
      this.current = current;
      this.experimental = experimental;
    }
  }
}