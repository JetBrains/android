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

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static org.jetbrains.android.inspections.lint.AndroidLintInspectionToolProvider.AndroidLintNewApiInspection;

public class IntellijApiDetectorTest extends AndroidTestCase {
  private static final String BASE_PATH = "apiCheck/";

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
    doTest(inspection, "Add @SuppressLint(\"NewApi\") annotation");
  }

  public void testListView() throws Exception {
    AndroidLintNewApiInspection inspection = new AndroidLintNewApiInspection();
    doTest(inspection, null);
  }

  /**
   * For unknown reasons this test doesn't work; it cannot resolve the symbol android.os.Build. This
   * is probably related to the fact that the unit tests work with an incomplete snapshot of an ancient
   * SDK (1.5 or something like that.)
  public void testVersionConditional() throws Exception {
    AndroidLintNewApiInspection inspection = new AndroidLintNewApiInspection();
    doTest(inspection, null);
  }
  */

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
