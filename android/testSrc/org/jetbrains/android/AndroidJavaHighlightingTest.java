package org.jetbrains.android;

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
}
