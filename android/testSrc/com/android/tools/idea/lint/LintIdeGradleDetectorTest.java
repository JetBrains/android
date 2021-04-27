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
package com.android.tools.idea.lint;

import com.android.tools.idea.lint.common.AndroidLintGradleDependencyInspection;
import com.android.tools.idea.lint.common.AndroidLintGradleDeprecatedConfigurationInspection;
import com.android.tools.idea.lint.common.AndroidLintGradleDynamicVersionInspection;
import com.android.tools.idea.lint.common.AndroidLintGradleIdeErrorInspection;
import com.android.tools.idea.lint.common.AndroidLintGradlePathInspection;
import com.android.tools.idea.lint.common.AndroidLintInspectionBase;
import com.android.tools.idea.lint.common.AndroidLintJavaPluginLanguageLevelInspection;
import com.android.tools.idea.lint.common.AndroidLintJcenterRepositoryObsoleteInspection;
import com.android.tools.lint.checks.GradleDetector;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LintIdeGradleDetectorTest extends AndroidTestCase {
  private static final String BASE_PATH = "gradle/";

  public void testDependencies() throws Exception {
    AndroidLintGradleDependencyInspection inspection = new AndroidLintGradleDependencyInspection();
    doTest(inspection, null);
  }

  public void testDependenciesWithTask() throws Exception {
    AndroidLintGradleDependencyInspection inspection = new AndroidLintGradleDependencyInspection();
    doTest(inspection, null);
  }

  public void testIncompatiblePlugin() throws Exception {
    AndroidLintGradlePluginVersionInspection inspection = new AndroidLintGradlePluginVersionInspection();
    doTest(inspection, null);
  }

  public void testPaths() throws Exception {
    if (SystemInfo.isWindows) {
      // This test doesn't work on Windows; the data file supplies what looks like an absolute elsewhere,
      // and flags it; on Windows the File#isAbsolute() call will return false and will not flag the issue.
      return;
    }
    AndroidLintGradlePathInspection inspection = new AndroidLintGradlePathInspection();
    doTest(inspection, null);
  }

  public void testSetter() throws Exception {
    AndroidLintGradleGetterInspection inspection = new AndroidLintGradleGetterInspection();
    doTest(inspection, null);
  }

  public void testCompatibility() throws Exception {
    AndroidLintGradleCompatibleInspection inspection = new AndroidLintGradleCompatibleInspection();
    doTest(inspection, null);
  }

  public void testPlus() throws Exception {
    GradleDetector.PLUS.setEnabledByDefault(true);
    AndroidLintGradleDynamicVersionInspection inspection = new AndroidLintGradleDynamicVersionInspection();
    doTest(inspection, null);
  }

  public void testIdSuffix() throws Exception {
    AndroidLintGradlePathInspection inspection = new AndroidLintGradlePathInspection();
    doTest(inspection, null);
  }

  public void testPackage() throws Exception {
    AndroidLintGradleDeprecatedInspection inspection = new AndroidLintGradleDeprecatedInspection();
    doTest(inspection, null);
  }

  public void testPackage2() throws Exception {
    AndroidLintGradleDeprecatedInspection inspection = new AndroidLintGradleDeprecatedInspection();
    doTest(inspection, null);
  }

  public void testMinSdkAssignment() throws Exception {
    AndroidLintGradleIdeErrorInspection inspection = new AndroidLintGradleIdeErrorInspection();
    doTest(inspection, null);
  }

  public void testMinSdkAssignment2() throws Exception {
    AndroidLintGradleIdeErrorInspection inspection = new AndroidLintGradleIdeErrorInspection();
    doTest(inspection, null);
  }

  public void testMinSdkAssignment3() throws Exception {
    AndroidLintGradleIdeErrorInspection inspection = new AndroidLintGradleIdeErrorInspection();
    doTest(inspection, null);
  }

  public void testPathSuppress() throws Exception {
    AndroidLintGradlePathInspection inspection = new AndroidLintGradlePathInspection();
    doTest(inspection, "Suppress: Add //noinspection GradlePath");
  }

  public void testPathSuppressJoin() throws Exception {
    AndroidLintGradlePathInspection inspection = new AndroidLintGradlePathInspection();
    doTest(inspection, "Suppress: Add //noinspection GradlePath");
  }

  public void testBadPlayServicesVersion() throws Exception {
    AndroidLintGradleCompatibleInspection inspection = new AndroidLintGradleCompatibleInspection();
    doTest(inspection, "Change to 12.0.1");
  }

  public void testStringInt() throws Exception {
    AndroidLintStringShouldBeIntInspection inspection = new AndroidLintStringShouldBeIntInspection();
    doTest(inspection, null);
  }

  public void testDeprecatedPluginId() throws Exception {
    AndroidLintGradleDeprecatedInspection inspection = new AndroidLintGradleDeprecatedInspection();
    doTest(inspection, null);
  }

  public void testSuppressLine2() throws Exception {
    // Tests that issues on line 2, which are suppressed with a comment on line 1, are correctly
    // handled (until recently, the suppression comment on the first line did not work)
    AndroidLintGradleDeprecatedInspection inspection = new AndroidLintGradleDeprecatedInspection();
    doTest(inspection, null);
  }

  public void testIgnoresGStringsInDependencies() throws Exception {
    AndroidLintGradlePluginVersionInspection inspection = new AndroidLintGradlePluginVersionInspection();
    doTest(inspection, null);
  }

  public void testDataBindingWithoutKaptUsingApplyPlugin() throws Exception {
    AndroidLintDataBindingWithoutKaptInspection inspection = new AndroidLintDataBindingWithoutKaptInspection();
    doTest(inspection, null);
  }

  public void testDataBindingWithKaptUsingApplyPlugin() throws Exception {
    AndroidLintDataBindingWithoutKaptInspection inspection = new AndroidLintDataBindingWithoutKaptInspection();
    doTest(inspection, null);
  }

  public void testDataBindingWithoutKaptUsingPluginsBlock() throws Exception {
    AndroidLintDataBindingWithoutKaptInspection inspection = new AndroidLintDataBindingWithoutKaptInspection();
    doTest(inspection, null);
  }

  public void testDataBindingWithKaptUsingPluginsBlock() throws Exception {
    AndroidLintDataBindingWithoutKaptInspection inspection = new AndroidLintDataBindingWithoutKaptInspection();
    doTest(inspection, null);
  }

  public void testDeprecatedConfigurationUse() throws Exception {
    AndroidLintGradleDeprecatedConfigurationInspection inspection = new AndroidLintGradleDeprecatedConfigurationInspection();
    doTest(inspection, null);
  }

  public void testJavaNoLanguageLevel() throws Exception {
    AndroidLintJavaPluginLanguageLevelInspection inspection = new AndroidLintJavaPluginLanguageLevelInspection();
    doTest(inspection, null);
  }

  public void testJavaLanguageLevelBlock() throws Exception {
    AndroidLintJavaPluginLanguageLevelInspection inspection = new AndroidLintJavaPluginLanguageLevelInspection();
    doTest(inspection, null);
  }

  public void testJavaLanguageLevelReceiver() throws Exception {
    AndroidLintJavaPluginLanguageLevelInspection inspection = new AndroidLintJavaPluginLanguageLevelInspection();
    doTest(inspection, null);
  }

  public void testJavaLanguageLevelToplevel() throws Exception {
    AndroidLintJavaPluginLanguageLevelInspection inspection = new AndroidLintJavaPluginLanguageLevelInspection();
    doTest(inspection, null);
  }

  public void testJavaLanguageLevelToplevelMissing() throws Exception {
    AndroidLintJavaPluginLanguageLevelInspection inspection = new AndroidLintJavaPluginLanguageLevelInspection();
    doTest(inspection, null);
  }

  public void testJcenterCall() throws Exception {
    AndroidLintJcenterRepositoryObsoleteInspection inspection = new AndroidLintJcenterRepositoryObsoleteInspection();
    doTest(inspection, null);
  }

  public void testJcenterBlock() throws Exception {
    AndroidLintJcenterRepositoryObsoleteInspection inspection = new AndroidLintJcenterRepositoryObsoleteInspection();
    doTest(inspection, null);
  }

  private void doTest(@NotNull final AndroidLintInspectionBase inspection, @Nullable String quickFixName) throws Exception {
    createManifest();
    myFixture.enableInspections(inspection);
    VirtualFile file = myFixture.copyFileToProject(BASE_PATH + getTestName(false) + ".gradle", "build.gradle");
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.checkHighlighting(true, false, false);

    if (quickFixName != null) {
      final IntentionAction quickFix = myFixture.getAvailableIntention(quickFixName);
      assertNotNull(quickFix);
      myFixture.launchAction(quickFix);
      myFixture.checkResultByFile(BASE_PATH + getTestName(false) + "_after.gradle");
    }
  }
}
