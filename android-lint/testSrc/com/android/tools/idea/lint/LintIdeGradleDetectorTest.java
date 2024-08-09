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

import static org.junit.Assume.assumeTrue;

import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.lint.common.AndroidLintGradleDependencyInspection;
import com.android.tools.idea.lint.common.AndroidLintGradleDeprecatedConfigurationInspection;
import com.android.tools.idea.lint.common.AndroidLintGradleDynamicVersionInspection;
import com.android.tools.idea.lint.common.AndroidLintGradleIdeErrorInspection;
import com.android.tools.idea.lint.common.AndroidLintGradlePathInspection;
import com.android.tools.idea.lint.common.AndroidLintInspectionBase;
import com.android.tools.idea.lint.common.AndroidLintJavaPluginLanguageLevelInspection;
import com.android.tools.idea.lint.common.AndroidLintJcenterRepositoryObsoleteInspection;
import com.android.tools.idea.lint.inspections.AndroidLintDataBindingWithoutKaptInspection;
import com.android.tools.idea.lint.inspections.AndroidLintGradleCompatibleInspection;
import com.android.tools.idea.lint.inspections.AndroidLintGradleDeprecatedInspection;
import com.android.tools.idea.lint.inspections.AndroidLintGradleGetterInspection;
import com.android.tools.idea.lint.inspections.AndroidLintGradlePluginVersionInspection;
import com.android.tools.idea.lint.inspections.AndroidLintStringShouldBeIntInspection;
import com.android.tools.lint.checks.GradleDetector;
import com.intellij.codeInsight.daemon.ProblemHighlightFilter;
import com.intellij.codeInsight.daemon.impl.analysis.DefaultHighlightingSettingProvider;
import com.intellij.codeInsight.daemon.impl.analysis.FileHighlightingSetting;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.ExtensionTestUtil;
import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class LintIdeGradleDetectorTest extends AndroidTestCase {
  private static final String BASE_PATH = "gradle/";

  @Parameterized.Parameter public String extension;
  @Parameterized.Parameters
  public static Collection<String[]> getParameters() {
    return Arrays.asList(new String[][] {
      {".gradle"},
      {".gradle.kts"},
      {".gradle.dcl"}
    });
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();

    myFixture.setTestDataPath(TestDataPaths.TEST_DATA_ROOT);

    // We mask (in particular) the KotlinProblemHighlightFilter and KotlinDefaultHighlightingSettingsProvider which can cause
    // Kotlin Script files not to get any highlighting at all.
    ExtensionTestUtil.maskExtensions(ProblemHighlightFilter.EP_NAME, List.of(new ProblemHighlightFilter() {
      @Override
      public boolean shouldHighlight(@NotNull PsiFile psiFile) {
        return true;
      }
    }), myFixture.getProjectDisposable());
    ExtensionTestUtil.maskExtensions(DefaultHighlightingSettingProvider.EP_NAME, List.of(new DefaultHighlightingSettingProvider() {
      @Override
      public FileHighlightingSetting getDefaultSetting(@NotNull Project project, @NotNull VirtualFile file) {
        return FileHighlightingSetting.FORCE_HIGHLIGHTING;
      }
    }), myFixture.getProjectDisposable());

    // However, we are not interested in Kotlin compiler diagnostics or resolution failures, as we are running with a
    // simplified and unrealistic project structure: so mask away the Kotlin highlighting visitors.
    unmaskKotlinHighlightVisitor();
    StudioFlags.GRADLE_DECLARATIVE_IDE_SUPPORT.override(true);
  }

  @Override
  public void tearDown() throws Exception {
    super.tearDown();
    StudioFlags.GRADLE_DECLARATIVE_IDE_SUPPORT.clearOverride();
  }

  @Test
  public void testDependencies() throws Exception {
    AndroidLintGradleDependencyInspection inspection = new AndroidLintGradleDependencyInspection();
    doTest(inspection, null);
  }

  @Test
  public void testDependenciesWithTask() throws Exception {
    AndroidLintGradleDependencyInspection inspection = new AndroidLintGradleDependencyInspection();
    doTest(inspection, null);
  }

  @Test
  public void testIncompatiblePlugin() throws Exception {
    AndroidLintGradlePluginVersionInspection inspection = new AndroidLintGradlePluginVersionInspection();
    doTest(inspection, null);
  }

  @Test
  public void testPaths() throws Exception {
    if (SystemInfo.isWindows) {
      // This test doesn't work on Windows; the data file supplies what looks like an absolute elsewhere,
      // and flags it; on Windows the File#isAbsolute() call will return false and will not flag the issue.
      return;
    }
    AndroidLintGradlePathInspection inspection = new AndroidLintGradlePathInspection();
    doTest(inspection, null);
  }

  @Test
  public void testSetter() throws Exception {
    AndroidLintGradleGetterInspection inspection = new AndroidLintGradleGetterInspection();
    doTest(inspection, null);
  }

  @Test
  public void testPlus() throws Exception {
    GradleDetector.PLUS.setEnabledByDefault(true);
    AndroidLintGradleDynamicVersionInspection inspection = new AndroidLintGradleDynamicVersionInspection();
    doTest(inspection, null);
  }

  @Test
  public void testIdSuffix() throws Exception {
    AndroidLintGradlePathInspection inspection = new AndroidLintGradlePathInspection();
    doTest(inspection, null);
  }

  @Test
  public void testPackage() throws Exception {
    AndroidLintGradleDeprecatedInspection inspection = new AndroidLintGradleDeprecatedInspection();
    doTest(inspection, null);
  }

  @Test
  public void testPackage2() throws Exception {
    AndroidLintGradleDeprecatedInspection inspection = new AndroidLintGradleDeprecatedInspection();
    doTest(inspection, null);
  }

  @Test
  public void testPackage3() throws Exception {
    AndroidLintGradleDeprecatedInspection inspection = new AndroidLintGradleDeprecatedInspection();
    doTest(inspection, null);
  }

  @Test
  public void testPackage4() throws Exception {
    AndroidLintGradleDeprecatedInspection inspection = new AndroidLintGradleDeprecatedInspection();
    doTest(inspection, null);
  }

  @Test
  public void testMinSdkAssignment() throws Exception {
    if (extension.equals(".gradle.kts")) return; // no need for this check in Kotlin Script
    AndroidLintGradleIdeErrorInspection inspection = new AndroidLintGradleIdeErrorInspection();
    doTest(inspection, null);
  }

  @Test
  public void testMinSdkAssignment2() throws Exception {
    if (extension.equals(".gradle.kts")) return; // no need for this check in Kotlin Script
    AndroidLintGradleIdeErrorInspection inspection = new AndroidLintGradleIdeErrorInspection();
    doTest(inspection, null);
  }

  @Test
  public void testMinSdkAssignment3() throws Exception {
    if (extension.equals(".gradle.kts")) return; // no need for this check in Kotlin Script
    AndroidLintGradleIdeErrorInspection inspection = new AndroidLintGradleIdeErrorInspection();
    doTest(inspection, null);
  }

  @Test
  public void testMinSdkAssignment4() throws Exception {
    if (extension.equals(".gradle.kts")) return; // no need for this check in Kotlin Script
    AndroidLintGradleIdeErrorInspection inspection = new AndroidLintGradleIdeErrorInspection();
    doTest(inspection, null);
  }

  @Test
  public void testPathSuppress() throws Exception {
    AndroidLintGradlePathInspection inspection = new AndroidLintGradlePathInspection();
    doTest(inspection, "Suppress GradlePath with a comment");
  }

  @Test
  public void testPathSuppressJoin() throws Exception {
    AndroidLintGradlePathInspection inspection = new AndroidLintGradlePathInspection();
    doTest(inspection, "Suppress GradlePath with a comment");
  }

  @Test
  public void testBadPlayServicesVersion() throws Exception {
    AndroidLintGradleCompatibleInspection inspection = new AndroidLintGradleCompatibleInspection();
    doTest(inspection, "Change to 12.0.1");
  }

  @Test
  public void testStringInt() throws Exception {
    AndroidLintStringShouldBeIntInspection inspection = new AndroidLintStringShouldBeIntInspection();
    doTest(inspection, null);
  }

  @Test
  public void testDeprecatedPluginId() throws Exception {
    AndroidLintGradleDeprecatedInspection inspection = new AndroidLintGradleDeprecatedInspection();
    doTest(inspection, null);
  }

  @Test
  public void testSuppressLine2() throws Exception {
    // Tests that issues on line 2, which are suppressed with a comment on line 1, are correctly
    // handled (until recently, the suppression comment on the first line did not work)
    AndroidLintGradleDeprecatedInspection inspection = new AndroidLintGradleDeprecatedInspection();
    doTest(inspection, null);
  }

  @Test
  public void testSuppressWithAnnotation() throws Exception {
    // Same as testSuppressLine2, except we suppress using an annotation in Kotlin Script.
    if (!extension.equals(".gradle.kts")) return;
    AndroidLintGradleDeprecatedInspection inspection = new AndroidLintGradleDeprecatedInspection();
    doTest(inspection, null);
  }

  @Test
  public void testIgnoresGStringsInDependencies() throws Exception {
    AndroidLintGradlePluginVersionInspection inspection = new AndroidLintGradlePluginVersionInspection();
    doTest(inspection, null);
  }

  @Test
  public void testDataBindingWithoutKaptUsingApplyPlugin() throws Exception {
    if (extension.equals(".gradle.kts")) return; // no need for this check in Kotlin Script
    AndroidLintDataBindingWithoutKaptInspection inspection = new AndroidLintDataBindingWithoutKaptInspection();
    doTest(inspection, null);
  }

  @Test
  public void testDataBindingWithKaptUsingApplyPlugin() throws Exception {
    if (extension.equals(".gradle.kts")) return; // no need for this check in Kotlin Script
    AndroidLintDataBindingWithoutKaptInspection inspection = new AndroidLintDataBindingWithoutKaptInspection();
    doTest(inspection, null);
  }

  @Test
  public void testDataBindingWithoutKaptUsingPluginsBlock() throws Exception {
    AndroidLintDataBindingWithoutKaptInspection inspection = new AndroidLintDataBindingWithoutKaptInspection();
    doTest(inspection, null);
  }

  @Test
  public void testDataBindingWithKaptUsingPluginsBlock() throws Exception {
    AndroidLintDataBindingWithoutKaptInspection inspection = new AndroidLintDataBindingWithoutKaptInspection();
    doTest(inspection, null);
  }

  @Test
  public void testDeprecatedConfigurationUse() throws Exception {
    AndroidLintGradleDeprecatedConfigurationInspection inspection = new AndroidLintGradleDeprecatedConfigurationInspection();
    doTest(inspection, null);
  }

  @Test
  public void testJavaNoLanguageLevel() throws Exception {
    AndroidLintJavaPluginLanguageLevelInspection inspection = new AndroidLintJavaPluginLanguageLevelInspection();
    doTest(inspection, null);
  }

  @Test
  public void testJavaLanguageLevelBlock() throws Exception {
    AndroidLintJavaPluginLanguageLevelInspection inspection = new AndroidLintJavaPluginLanguageLevelInspection();
    doTest(inspection, null);
  }

  @Test
  public void testJavaLanguageLevelReceiver() throws Exception {
    AndroidLintJavaPluginLanguageLevelInspection inspection = new AndroidLintJavaPluginLanguageLevelInspection();
    doTest(inspection, null);
  }

  @Test
  public void testJavaLanguageLevelToplevel() throws Exception {
    AndroidLintJavaPluginLanguageLevelInspection inspection = new AndroidLintJavaPluginLanguageLevelInspection();
    doTest(inspection, null);
  }

  @Test
  public void testJavaLanguageLevelToplevelMissing() throws Exception {
    AndroidLintJavaPluginLanguageLevelInspection inspection = new AndroidLintJavaPluginLanguageLevelInspection();
    doTest(inspection, null);
  }

  @Test
  public void testJcenterCall() throws Exception {
    AndroidLintJcenterRepositoryObsoleteInspection inspection = new AndroidLintJcenterRepositoryObsoleteInspection();
    doTest(inspection, null);
  }

  @Test
  public void testJcenterBlock() throws Exception {
    AndroidLintJcenterRepositoryObsoleteInspection inspection = new AndroidLintJcenterRepositoryObsoleteInspection();
    doTest(inspection, null);
  }

  private void doTest(@NotNull final AndroidLintInspectionBase inspection, @Nullable String quickFixName) throws Exception {
    createManifest();
    myFixture.enableInspections(inspection);
    String sourceName = BASE_PATH + getTestName(false) + extension;
    if (extension.equals(".gradle.dcl")) {
      assumeTrue("Not implemented for declarative Gradle file", new File(myFixture.getTestDataPath(), sourceName).exists());
    }

    VirtualFile file = myFixture.copyFileToProject(BASE_PATH + getTestName(false) + extension, "build" + extension);
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.checkHighlighting(true, false, false);

    if (quickFixName != null) {
      final IntentionAction quickFix = myFixture.getAvailableIntention(quickFixName);
      assertNotNull(quickFix);
      myFixture.launchAction(quickFix);
      myFixture.checkResultByFile(BASE_PATH + getTestName(false) + "_after" + extension);
    }
  }
}
