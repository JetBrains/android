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
import org.junit.Before;
import org.junit.Test;

import static com.android.tools.idea.gradle.project.PostProjectSetupTasksExecutor.checkCompatibility;
import static org.junit.Assert.*;

/**
 * Tests for {@link PostProjectSetupTasksExecutor#checkCompatibility(GradleVersion, AndroidGradleModelVersions)}.
 */
public class PluginAndGradleCompatibilityTest {

  private GradleVersion mySecureGradleVersion;
  private GradleVersion myOldGradleVersion;

  private GradleVersion myLatestStablePluginVersion;
  private AndroidGradleModelVersions mySecureStablePluginVersions;
  private AndroidGradleModelVersions myOldStablePluginVersions;

  private GradleVersion myLatestStableExperimentalPluginVersion;
  private AndroidGradleModelVersions mySecureStableExperimentalPluginVersions;
  private AndroidGradleModelVersions myOldStableExperimentalPluginVersions;

  private GradleVersion myLatestPreviewPluginVersion;
  private AndroidGradleModelVersions mySecurePreviewPluginVersions;
  private AndroidGradleModelVersions myOldPreviewPluginVersions;

  private GradleVersion myLatestPreviewExperimentalPluginVersion;
  private AndroidGradleModelVersions mySecurePreviewExperimentalPluginVersions;
  private AndroidGradleModelVersions myOldPreviewExperimentalPluginVersions;

  @Before
  public void setUp() {
    mySecureGradleVersion = GradleVersion.parse("2.14.1");
    myOldGradleVersion = GradleVersion.parse("2.10");

    myLatestStablePluginVersion = GradleVersion.parse("2.1.3");
    mySecureStablePluginVersions = new AndroidGradleModelVersions(myLatestStablePluginVersion, myLatestStablePluginVersion, false);
    myOldStablePluginVersions = new AndroidGradleModelVersions(GradleVersion.parse("2.1.2"), myLatestStablePluginVersion, false);

    myLatestStableExperimentalPluginVersion = GradleVersion.parse("0.7.3");
    mySecureStableExperimentalPluginVersions =
      new AndroidGradleModelVersions(myLatestStableExperimentalPluginVersion, myLatestStableExperimentalPluginVersion, true);
    myOldStableExperimentalPluginVersions =
      new AndroidGradleModelVersions(GradleVersion.parse("0.7.2"), myLatestStableExperimentalPluginVersion, true);

    myLatestPreviewPluginVersion = GradleVersion.parse("2.2.0-alpha7");
    mySecurePreviewPluginVersions = new AndroidGradleModelVersions(myLatestPreviewPluginVersion, myLatestPreviewPluginVersion, false);
    myOldPreviewPluginVersions = new AndroidGradleModelVersions(GradleVersion.parse("2.2.0-alpha6"), myLatestPreviewPluginVersion, false);

    myLatestPreviewExperimentalPluginVersion = GradleVersion.parse("0.8.0-alpha7");
    mySecurePreviewExperimentalPluginVersions =
      new AndroidGradleModelVersions(myLatestPreviewExperimentalPluginVersion, myLatestPreviewExperimentalPluginVersion, true);
    myOldPreviewExperimentalPluginVersions =
      new AndroidGradleModelVersions(GradleVersion.parse("0.8.0-alpha6"), myLatestPreviewExperimentalPluginVersion, true);
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
}