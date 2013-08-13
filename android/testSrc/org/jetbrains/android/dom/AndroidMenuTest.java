package org.jetbrains.android.dom;

import com.android.SdkConstants;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.inspections.AndroidMissingOnClickHandlerInspection;

import java.io.IOException;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidMenuTest extends AndroidDomTest {
  public AndroidMenuTest() {
    super(false, "dom/menu");
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFixture.copyFileToProject(SdkConstants.FN_ANDROID_MANIFEST_XML, SdkConstants.FN_ANDROID_MANIFEST_XML);
  }

  @Override
  protected String getPathToCopy(String testFileName) {
    return "res/menu/" + testFileName;
  }

  private void copyOnClickClasses() throws IOException {
    copyFileToProject("OnClick_Class1.java", "src/p1/p2/OnClick_Class1.java");
    copyFileToProject("OnClick_Class2.java", "src/p1/p2/OnClick_Class2.java");
    copyFileToProject("OnClick_Class3.java", "src/p1/p2/OnClick_Class3.java");
    copyFileToProject("OnClick_Class4.java", "src/p1/p2/OnClick_Class4.java");
  }

  public void testOnClickHighlighting1() throws Throwable {
    myFixture.allowTreeAccessForAllFiles();
    myFixture.enableInspections(AndroidMissingOnClickHandlerInspection.class);
    myFixture.copyFileToProject(testFolder + "/OnClickActivity3.java", "src/p1/p2/Activity1.java");
    myFixture.copyFileToProject(testFolder + "/OnClickActivity4.java", "src/p1/p2/Activity2.java");
    doTestHighlighting();
  }

  public void testOnClickHighlighting2() throws Throwable {
    copyOnClickClasses();
    myFixture.enableInspections(AndroidMissingOnClickHandlerInspection.class);
    doTestHighlighting();
  }

  public void testOnClickHighlighting3() throws Throwable {
    myFixture.allowTreeAccessForAllFiles();
    myFixture.enableInspections(AndroidMissingOnClickHandlerInspection.class);
    myFixture.copyFileToProject(testFolder + "/OnClickActivity5.java", "src/p1/p2/Activity1.java");
    doTestHighlighting();
  }

  public void testOnClickHighlighting4() throws Throwable {
    myFixture.allowTreeAccessForAllFiles();
    myFixture.enableInspections(AndroidMissingOnClickHandlerInspection.class);
    myFixture.copyFileToProject(testFolder + "/OnClickActivity6.java", "src/p1/p2/Activity1.java");
    myFixture.copyFileToProject(testFolder + "/OnClickActivity4.java", "src/p1/p2/Activity2.java");
    doTestHighlighting();
  }

  public void testOnClickCompletion() throws Throwable {
    copyOnClickClasses();
    doTestCompletionVariants(getTestName(true) + ".xml", "clickHandler1", "clickHandler7");
  }

  public void testOnClickIntention() throws Throwable {
    myFixture.copyFileToProject(testFolder + "/OnClickActivity.java", "src/p1/p2/Activity1.java");
    final VirtualFile file = copyFileToProject("onClickIntention.xml");
    myFixture.configureFromExistingVirtualFile(file);
    final AndroidCreateOnClickHandlerAction action = new AndroidCreateOnClickHandlerAction();
    assertTrue(action.isAvailable(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile()));
    action.invoke(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile());
    myFixture.checkResultByFile(testFolder + "/onClickIntention.xml");
    myFixture.checkResultByFile("src/p1/p2/Activity1.java", testFolder + "/OnClickActivity_after.java", false);
  }

  public void testOnClickQuickFix1() throws Throwable {
    myFixture.enableInspections(AndroidMissingOnClickHandlerInspection.class);
    myFixture.copyFileToProject(testFolder + "/OnClickActivity.java", "src/p1/p2/Activity1.java");
    final VirtualFile file = copyFileToProject("onClickIntention.xml");
    myFixture.configureFromExistingVirtualFile(file);
    final List<IntentionAction> fixes = highlightAndFindQuickFixes(AndroidMissingOnClickHandlerInspection.MyQuickFix.class);
    assertEmpty(fixes);
  }

  public void testOnClickQuickFix2() throws Throwable {
    myFixture.enableInspections(AndroidMissingOnClickHandlerInspection.class);
    myFixture.copyFileToProject(testFolder + "/OnClickActivity1.java", "src/p1/p2/Activity1.java");
    final VirtualFile file = copyFileToProject("onClickIntention.xml");
    myFixture.configureFromExistingVirtualFile(file);
    final List<IntentionAction> actions = highlightAndFindQuickFixes(AndroidMissingOnClickHandlerInspection.MyQuickFix.class);
    assertEquals(1, actions.size());
    actions.get(0).invoke(getProject(), myFixture.getEditor(), myFixture.getFile());
    myFixture.checkResultByFile(testFolder + "/onClickIntention.xml");
    myFixture.checkResultByFile("src/p1/p2/Activity1.java", testFolder + "/OnClickActivity1_after.java", false);
  }

  public void testOnClickQuickFix3() throws Throwable {
    myFixture.enableInspections(AndroidMissingOnClickHandlerInspection.class);
    myFixture.copyFileToProject(testFolder + "/OnClickActivity1.java", "src/p1/p2/Activity1.java");
    final VirtualFile file = copyFileToProject("onClickIntention.xml");
    doTestOnClickQuickfix(file);
    myFixture.checkResultByFile("src/p1/p2/Activity1.java", testFolder + "/OnClickActivity2_after.java", false);
  }

  public void testOnClickQuickFix4() throws Throwable {
    myFixture.enableInspections(AndroidMissingOnClickHandlerInspection.class);
    myFixture.copyFileToProject(testFolder + "/OnClickActivity1.java", "src/p1/p2/Activity1.java");
    myFixture.copyFileToProject(testFolder + "/OnClickActivity4.java", "src/p1/p2/Activity2.java");
    final VirtualFile file = copyFileToProject("onClickIntention.xml");
    doTestOnClickQuickfix(file);
    myFixture.checkResultByFile("src/p1/p2/Activity1.java", testFolder + "/OnClickActivity1_after.java", false);
  }
}
