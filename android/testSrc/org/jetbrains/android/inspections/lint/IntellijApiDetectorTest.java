package org.jetbrains.android.inspections.lint;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.AndroidTestCase;

import static org.jetbrains.android.inspections.lint.AndroidLintInspectionToolProvider.AndroidLintNewApiInspection;

public class IntellijApiDetectorTest extends AndroidTestCase {
  private static final String BASE_PATH = "intentions/";

  public void testApiCheck1() {
    //myFacet.getConfiguration().LIBRARY_PROJECT = true;
    //myFixture.copyFileToProject(BASE_PATH + "MyActivity.java", "src/p1/p2/MyActivity.java");
    AndroidLintNewApiInspection inspection = new AndroidLintNewApiInspection();
    doTest(inspection, true, inspection.getDisplayName());
  }
  //
  //public void testSwitchOnResourceId() {
  //  myFacet.getConfiguration().LIBRARY_PROJECT = true;
  //  myFixture.copyFileToProject(BASE_PATH + "R.java", "src/p1/p2/R.java");
  //  AndroidLintNewApiInspection inspection = new AndroidLintNewApiInspection();
  //
  //  doTest(inspection, true, inspection.getDisplayName());
  //}
  //
  ////public void testSwitchOnResourceId() {
  ////  myFacet.getConfiguration().LIBRARY_PROJECT = true;
  ////  myFixture.copyFileToProject(BASE_PATH + "R.java", "src/p1/p2/R.java");
  ////  final AndroidNonConstantResIdsInSwitchInspection inspection = new AndroidNonConstantResIdsInSwitchInspection();
  ////  doTest(inspection, true, inspection.getQuickFixName());
  ////}
  //
  //public void testSwitchOnResourceId1() {
  //  myFacet.getConfiguration().LIBRARY_PROJECT = false;
  //  myFixture.copyFileToProject(BASE_PATH + "R.java", "src/p1/p2/R.java");
  //  final AndroidNonConstantResIdsInSwitchInspection inspection = new AndroidNonConstantResIdsInSwitchInspection();
  //  doTest(inspection, false, inspection.getQuickFixName());
  //}
  //
  //public void testSwitchOnResourceId2() {
  //  myFacet.getConfiguration().LIBRARY_PROJECT = true;
  //  myFixture.copyFileToProject(BASE_PATH + "R.java", "src/p1/p2/R.java");
  //  final AndroidNonConstantResIdsInSwitchInspection inspection = new AndroidNonConstantResIdsInSwitchInspection();
  //  doTest(inspection, false, inspection.getQuickFixName());
  //}

  private void doTest(final LocalInspectionTool inspection, boolean available, String quickFixName) {
    myFixture.enableInspections(inspection);

    final VirtualFile file = myFixture.copyFileToProject(BASE_PATH + getTestName(false) + ".java", "src/p1/p2/Class.java");
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.checkHighlighting(true, false, false);

    final IntentionAction quickFix = myFixture.getAvailableIntention(quickFixName);
    if (available) {
      assertNotNull(quickFix);
      myFixture.launchAction(quickFix);
      myFixture.checkResultByFile(BASE_PATH + getTestName(false) + "_after.java");
    }
    else {
      assertNull(quickFix);
    }
  }

  private void doTest(final AndroidLintInspectionBase inspection, boolean available, String quickFixName) {
    myFixture.enableInspections(inspection);

    final VirtualFile file = myFixture.copyFileToProject(BASE_PATH + getTestName(false) + ".java", "src/p1/p2/Class.java");
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.checkHighlighting(true, false, false);

    final IntentionAction quickFix = myFixture.getAvailableIntention(quickFixName);
    if (available) {
      assertNotNull(quickFix);
      myFixture.launchAction(quickFix);
      myFixture.checkResultByFile(BASE_PATH + getTestName(false) + "_after.java");
    }
    else {
      assertNull(quickFix);
    }
  }

}
