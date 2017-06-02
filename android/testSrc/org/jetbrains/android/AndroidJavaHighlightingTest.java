package org.jetbrains.android;

import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspectionBase;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidJavaHighlightingTest extends AndroidTestCase {
  private static final String BASE_PATH = "/javaHighlighting/";

  public void testInjectResourceAnnotation() throws Exception {
    myFixture.enableInspections(new UnusedDeclarationInspectionBase());
    myFixture.copyFileToProject(BASE_PATH + "values.xml", "res/values/values.xml");
    myFixture.copyFileToProject(BASE_PATH + "InjectResource.java", "src/p1/p2/InjectResource.java");
    myFixture.copyFileToProject(BASE_PATH + "SomeAnnotation.java", "src/p1/p2/SomeAnnotation.java");
    myFixture.copyFileToProject(BASE_PATH + "R1.java", "src/p1/p2/R1.java");
    myFixture.copyFileToProject("R.java", "src/p1/p2/R.java");
    final VirtualFile f = myFixture.copyFileToProject(BASE_PATH + getTestName(false) + ".java", "src/p1/p2/MyActivity.java");
    myFixture.configureFromExistingVirtualFile(f);
    myFixture.checkHighlighting(true, false, true);
  }

  public void testParcelable() throws Exception {
    myFixture.enableInspections(new UnusedDeclarationInspectionBase(true));
    final VirtualFile f = myFixture.copyFileToProject(BASE_PATH + getTestName(false) + ".java", "src/p1/p2/MyParcelable.java");
    myFixture.configureFromExistingVirtualFile(f);
    myFixture.checkHighlighting(true, false, true);
  }

  public void testUnusedConstructors() throws Exception {
    // Regression test for https://code.google.com/p/android/issues/detail?id=77054
    // Checks that various constructors are not marked as unused
    final UnusedDeclarationInspection inspection = new UnusedDeclarationInspection(true);
    myFixture.enableInspections(inspection);
    final VirtualFile f = myFixture.copyFileToProject(BASE_PATH + getTestName(false) + ".java", "src/p1/p2/UnusedConstructors.java");
    myFixture.configureFromExistingVirtualFile(f);
    myFixture.checkHighlighting(true, false, true);
  }

  public void testLifecycleUsage() throws Exception {
    // Regression test for issue 37787915: Don't show methods listening to lifecycle events as unused
    final UnusedDeclarationInspection inspection = new UnusedDeclarationInspection(true);
    myFixture.enableInspections(inspection);
    final VirtualFile f = myFixture.copyFileToProject(BASE_PATH + getTestName(false) + ".java", "src/android/arch/lifecycle/LifecycleUsage.java");
    myFixture.configureFromExistingVirtualFile(f);
    myFixture.checkHighlighting(true, false, true);
  }
}
