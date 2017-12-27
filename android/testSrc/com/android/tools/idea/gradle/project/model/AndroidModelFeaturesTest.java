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
package com.android.tools.idea.gradle.project.model;

import com.android.ide.common.repository.GradleVersion;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests for {@link AndroidModelFeatures}.
 */
public class AndroidModelFeaturesTest {
  @Test
  public void withoutPluginVersion() {
    AndroidModelFeatures features = new AndroidModelFeatures(null);
    assertFalse(features.isIssueReportingSupported());
    assertFalse(features.isShadersSupported());
    assertFalse(features.isTestedTargetVariantsSupported());
    assertFalse(features.isProductFlavorVersionSuffixSupported());
    assertFalse(features.isExternalBuildSupported());
    assertFalse(features.isConstraintLayoutSdkLocationSupported());
    assertFalse(features.isLayoutRenderingIssuePresent());
    assertTrue(features.shouldExportDependencies());
  }

  @Test
  public void withPluginVersion1_0_0() {
    GradleVersion version = GradleVersion.parse("1.0.0");
    AndroidModelFeatures features = new AndroidModelFeatures(version);
    assertFalse(features.isIssueReportingSupported());
    assertFalse(features.isShadersSupported());
    assertFalse(features.isTestedTargetVariantsSupported());
    assertFalse(features.isProductFlavorVersionSuffixSupported());
    assertFalse(features.isExternalBuildSupported());
    assertFalse(features.isConstraintLayoutSdkLocationSupported());
    assertFalse(features.isLayoutRenderingIssuePresent());
    assertTrue(features.shouldExportDependencies());
  }

  @Test
  public void withPluginVersion1_1_0() {
    GradleVersion version = GradleVersion.parse("1.1.0");
    AndroidModelFeatures features = new AndroidModelFeatures(version);
    assertTrue(features.isIssueReportingSupported());
    assertFalse(features.isShadersSupported());
    assertFalse(features.isTestedTargetVariantsSupported());
    assertFalse(features.isProductFlavorVersionSuffixSupported());
    assertFalse(features.isExternalBuildSupported());
    assertFalse(features.isConstraintLayoutSdkLocationSupported());
    assertFalse(features.isLayoutRenderingIssuePresent());
    assertTrue(features.shouldExportDependencies());
  }

  @Test
  public void withPluginVersion1_2_0() {
    GradleVersion version = GradleVersion.parse("1.2.0");
    AndroidModelFeatures features = new AndroidModelFeatures(version);
    assertTrue(features.isIssueReportingSupported());
    assertFalse(features.isShadersSupported());
    assertFalse(features.isTestedTargetVariantsSupported());
    assertFalse(features.isProductFlavorVersionSuffixSupported());
    assertFalse(features.isExternalBuildSupported());
    assertFalse(features.isConstraintLayoutSdkLocationSupported());
    assertTrue(features.isLayoutRenderingIssuePresent());
    assertTrue(features.shouldExportDependencies());
  }

  @Test
  public void withPluginVersion1_2_1() {
    GradleVersion version = GradleVersion.parse("1.2.1");
    AndroidModelFeatures features = new AndroidModelFeatures(version);
    assertTrue(features.isIssueReportingSupported());
    assertFalse(features.isShadersSupported());
    assertFalse(features.isTestedTargetVariantsSupported());
    assertFalse(features.isProductFlavorVersionSuffixSupported());
    assertFalse(features.isExternalBuildSupported());
    assertFalse(features.isConstraintLayoutSdkLocationSupported());
    assertTrue(features.isLayoutRenderingIssuePresent());
    assertTrue(features.shouldExportDependencies());
  }

  @Test
  public void withPluginVersion1_2_2() {
    GradleVersion version = GradleVersion.parse("1.2.2");
    AndroidModelFeatures features = new AndroidModelFeatures(version);
    assertTrue(features.isIssueReportingSupported());
    assertFalse(features.isShadersSupported());
    assertFalse(features.isTestedTargetVariantsSupported());
    assertFalse(features.isProductFlavorVersionSuffixSupported());
    assertFalse(features.isExternalBuildSupported());
    assertFalse(features.isConstraintLayoutSdkLocationSupported());
    assertTrue(features.isLayoutRenderingIssuePresent());
    assertTrue(features.shouldExportDependencies());
  }

  @Test
  public void withPluginVersion2_1_0() {
    GradleVersion version = GradleVersion.parse("2.1.0");
    AndroidModelFeatures features = new AndroidModelFeatures(version);
    assertTrue(features.isIssueReportingSupported());
    assertTrue(features.isShadersSupported());
    assertFalse(features.isTestedTargetVariantsSupported());
    assertFalse(features.isProductFlavorVersionSuffixSupported());
    assertFalse(features.isExternalBuildSupported());
    assertFalse(features.isConstraintLayoutSdkLocationSupported());
    assertFalse(features.isLayoutRenderingIssuePresent());
    assertTrue(features.shouldExportDependencies());
  }

  @Test
  public void withPluginVersion2_2_0() {
    GradleVersion version = GradleVersion.parse("2.2.0");
    AndroidModelFeatures features = new AndroidModelFeatures(version);
    assertTrue(features.isIssueReportingSupported());
    assertTrue(features.isShadersSupported());
    assertTrue(features.isTestedTargetVariantsSupported());
    assertTrue(features.isProductFlavorVersionSuffixSupported());
    assertTrue(features.isExternalBuildSupported());
    assertTrue(features.isConstraintLayoutSdkLocationSupported());
    assertFalse(features.isLayoutRenderingIssuePresent());
    assertTrue(features.shouldExportDependencies());
  }

  @Test
  public void withPluginVersion2_4_0_preview7() {
    GradleVersion version = GradleVersion.parse("2.4.0-alpha7");
    AndroidModelFeatures features = new AndroidModelFeatures(version);
    assertTrue(features.isIssueReportingSupported());
    assertTrue(features.isShadersSupported());
    assertTrue(features.isTestedTargetVariantsSupported());
    assertTrue(features.isProductFlavorVersionSuffixSupported());
    assertTrue(features.isExternalBuildSupported());
    assertTrue(features.isConstraintLayoutSdkLocationSupported());
    assertFalse(features.isPostBuildSyncSupported());
    assertFalse(features.isLayoutRenderingIssuePresent());
    assertTrue(features.shouldExportDependencies());
  }

  @Test
  public void withPluginVersion2_4_0_preview8() {
    GradleVersion version = GradleVersion.parse("2.4.0-alpha8");
    AndroidModelFeatures features = new AndroidModelFeatures(version);
    assertTrue(features.isIssueReportingSupported());
    assertTrue(features.isShadersSupported());
    assertTrue(features.isTestedTargetVariantsSupported());
    assertTrue(features.isProductFlavorVersionSuffixSupported());
    assertTrue(features.isExternalBuildSupported());
    assertTrue(features.isConstraintLayoutSdkLocationSupported());
    assertTrue(features.isPostBuildSyncSupported());
    assertFalse(features.isLayoutRenderingIssuePresent());
    assertTrue(features.shouldExportDependencies());
  }

  @Test
  public void withPluginVersion3_0_0() {
    GradleVersion version = GradleVersion.parse("3.0.0");
    AndroidModelFeatures features = new AndroidModelFeatures(version);
    assertTrue(features.isIssueReportingSupported());
    assertTrue(features.isShadersSupported());
    assertTrue(features.isTestedTargetVariantsSupported());
    assertTrue(features.isProductFlavorVersionSuffixSupported());
    assertTrue(features.isExternalBuildSupported());
    assertTrue(features.isConstraintLayoutSdkLocationSupported());
    assertTrue(features.isPostBuildSyncSupported());
    assertFalse(features.isLayoutRenderingIssuePresent());
    assertFalse(features.shouldExportDependencies());
  }
}