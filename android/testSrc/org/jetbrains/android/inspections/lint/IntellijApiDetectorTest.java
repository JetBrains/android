/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.android.inspections.lint;

import com.android.tools.idea.npw.ConfigureAndroidModuleStep;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.idea.Bombed;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Calendar;

import static org.jetbrains.android.inspections.lint.AndroidLintInspectionToolProvider.AndroidLintNewApiInspection;

public class IntellijApiDetectorTest extends AndroidTestCase {
  private static final String BASE_PATH = "apiCheck/";

  @Override
  @Nullable
  protected LanguageLevel getLanguageLevel() {
    return LanguageLevel.JDK_1_7;
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    AndroidLintInspectionBase.invalidateInspectionShortName2IssueMap();
  }

  public void testBasic() throws Exception {
    AndroidLintNewApiInspection inspection = new AndroidLintNewApiInspection();
    doTest(inspection, null);
  }

  public void testInterfaces1() throws Exception {
    AndroidLintNewApiInspection inspection = new AndroidLintNewApiInspection();
    // TODO: check @TargetApi
    doTest(inspection, null /* "Add @TargetApi(ICE_CREAM_SANDWICH) Annotation" */);
  }

  public void testInterfaces2() throws Exception {
    AndroidLintNewApiInspection inspection = new AndroidLintNewApiInspection();
    doTest(inspection, "Suppress: Add @SuppressLint(\"NewApi\") annotation");
  }

  public void testListView() throws Exception {
    AndroidLintNewApiInspection inspection = new AndroidLintNewApiInspection();
    doTest(inspection, null);
  }

  public void testVersionConditional() throws Exception {
    AndroidLintNewApiInspection inspection = new AndroidLintNewApiInspection();
    doTest(inspection, null);
  }

  public void testVersionConditional2() throws Exception {
    AndroidLintNewApiInspection inspection = new AndroidLintNewApiInspection();
    doTest(inspection, null);
  }

  public void testVersionConditional3() throws Exception {
    AndroidLintNewApiInspection inspection = new AndroidLintNewApiInspection();
    doTest(inspection, null);
  }

  public void testFieldWithinCall() throws Exception {
    AndroidLintNewApiInspection inspection = new AndroidLintNewApiInspection();
    doTest(inspection, null);
  }

  public void testCatchClause() throws Exception {
    // Ensure that we flag uses in catch clauses; regression test for
    // https://code.google.com/p/android/issues/detail?id=74900
    // We're testing with OperationApplicationException instead of
    // ReflectiveOperationException since the bundled SDK used by the unit tests
    // is older and doesn't have API 19 data.
    AndroidLintNewApiInspection inspection = new AndroidLintNewApiInspection();
    doTest(inspection, null);
  }

  public void testCatchClause2() throws Exception {
    // Ensure that in addition to checking the upper bound (enforced
    // by testCatchClause above), we also check each individual class
    // since they're listed in the exception jump table.
    // Regression test for
    // https://code.google.com/p/android/issues/detail?id=198854
    AndroidLintNewApiInspection inspection = new AndroidLintNewApiInspection();
    doTest(inspection, null);
  }

  public void testRecursion() throws Exception {
    AndroidLintNewApiInspection inspection = new AndroidLintNewApiInspection();
    doTest(inspection, null);
  }

  public void testSuperCall() throws Exception {
    AndroidLintNewApiInspection inspection = new AndroidLintNewApiInspection();
    doTest(inspection, null);
  }

  public void testThisCall() throws Exception {
    // Regression test for https://code.google.com/p/android/issues/detail?id=93158
    // Make sure we properly resolve super classes in Class.this.call()
    AndroidLintNewApiInspection inspection = new AndroidLintNewApiInspection();
    doTest(inspection, null);
  }

  public void testSuppressArray() throws Exception {
    // Regression test for https://code.google.com/p/android/issues/detail?id=183724
    // Ensure that when a lint error is suppressed from a @SuppressLint annotation,
    // the suppress id works whether it's the first element in the array or not
    AndroidLintNewApiInspection inspection = new AndroidLintNewApiInspection();
    doTest(inspection, null);
  }

  public void testVersionUtility() throws Exception {
    // Regression test for https://code.google.com/p/android/issues/detail?id=178686
    // Makes sure the version conditional lookup peeks into surrounding method calls to see
    // if they're providing version checks.
    AndroidLintNewApiInspection inspection = new AndroidLintNewApiInspection();
    doTest(inspection, null);
  }

  public void testEarlyExit() throws Exception {
    // Regression test for https://code.google.com/p/android/issues/detail?id=37728
    // Makes sure that if a method exits earlier in the method, we don't flag
    // API checks after that
    AndroidLintNewApiInspection inspection = new AndroidLintNewApiInspection();
    doTest(inspection, null);
  }

  public void testBundleTest() throws Exception {
    // Regression test for https://code.google.com/p/android/issues/detail?id=78495
    // Without this fix, the required API level for getString would be 21 instead of 12
    AndroidLintNewApiInspection inspection = new AndroidLintNewApiInspection();
    doTest(inspection, null);
  }

  @Bombed(month = Calendar.SEPTEMBER, day = 31, user = "dmitry.avdeev", description = "No idea why it started to fail")
  public void testBundleTestOk() throws Exception {
    // Regression test for https://code.google.com/p/android/issues/detail?id=78495
    // Checks that if the API level is higher than the base level, the error is filtered out
    deleteManifest();
    myFixture.copyFileToProject("formatter/xml/manifest1.xml", "AndroidManifest.xml");
    AndroidLintNewApiInspection inspection = new AndroidLintNewApiInspection();
    doTest(inspection, null);
  }

  public void testAnonymousInherited() throws Exception {
    // Regression test for https://code.google.com/p/android/issues/detail?id=172621
    AndroidLintNewApiInspection inspection = new AndroidLintNewApiInspection();
    doTest(inspection, null);
  }

  public void testReflectiveOperationException() throws Exception {
    AndroidSdkData sdkData = AndroidSdkUtils.tryToChooseAndroidSdk();
    if (sdkData == null || !ConfigureAndroidModuleStep.isJdk7Supported(sdkData)) {
      System.out.println("Skipping IntellijApiDetectorTest#testReflectiveOperationException: Test JDK must be JDK 7 or higher");
      return;
    }

    // Regression test for https://code.google.com/p/android/issues/detail?id=153406
    // Ensure that we flag implicitly used ReflectiveOperationExceptions
    AndroidLintNewApiInspection inspection = new AndroidLintNewApiInspection();
    doTest(inspection, null);
  }

  // This test does not yet work reliably; need to ensure correct JDK 7 loading.
  public void testTryWithResources() throws Exception {
    // TODO: Allow setting a custom minSdkVersion in the manifest so I can test both with and without

    AndroidSdkData sdkData = AndroidSdkUtils.tryToChooseAndroidSdk();
    if (sdkData == null || !ConfigureAndroidModuleStep.isJdk7Supported(sdkData)) {
      System.out.println("Skipping IntellijApiDetectorTest#testTryWithResources: Test JDK must be JDK 7 or higher");
      return;
    }

    AndroidLintNewApiInspection inspection = new AndroidLintNewApiInspection();
    doTest(inspection, null);
  }

  public void testCast() throws Exception {
    AndroidLintNewApiInspection inspection = new AndroidLintNewApiInspection();
    doTest(inspection, null);
  }

  public void testCast2() throws Exception {
    AndroidLintNewApiInspection inspection = new AndroidLintNewApiInspection();
    doTest(inspection, null);
  }

  public void testCast3() throws Exception {
    // Regression test for
    //   198877: Implicit cast inspection flags casts to Object
    AndroidLintNewApiInspection inspection = new AndroidLintNewApiInspection();
    doTest(inspection, null);
  }

  private void doTest(@NotNull final AndroidLintInspectionBase inspection, @Nullable String quickFixName) throws Exception {
    createManifest();
    myFixture.enableInspections(inspection);
    VirtualFile file = myFixture.copyFileToProject(BASE_PATH + getTestName(false) + ".java", "src/p1/p2/Class.java");
    myFixture.configureFromExistingVirtualFile(file);
    //myFixture.doHighlighting();
    myFixture.checkHighlighting(true, false, false);

    if (quickFixName != null) {
      final IntentionAction quickFix = myFixture.getAvailableIntention(quickFixName);
      assertNotNull(quickFix);
      myFixture.launchAction(quickFix);
      myFixture.checkResultByFile(BASE_PATH + getTestName(false) + "_after.java");
    }
  }
}
