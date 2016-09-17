package org.jetbrains.android.refactoring;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.xml.DomManager;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.android.dom.layout.LayoutDomFileDescription;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidFindStyleApplicationsTest extends AndroidTestCase {
  private static final String BASE_PATH = "refactoring/findPossibleUsages/";

  public void test1() throws Exception {
    doTest();
  }

  public void testGranular1() throws Exception {
    myFixture.copyFileToProject(BASE_PATH + "1_layout.xml", "res/layout/layout.xml");
    final VirtualFile f = myFixture.copyFileToProject(BASE_PATH + "1.xml", "res/values/styles.xml");
    myFixture.configureFromExistingVirtualFile(f);
    XmlTag tag = PsiTreeUtil.getParentOfType(myFixture.getElementAtCaret(), XmlTag.class);
    AndroidFindStyleApplicationsAction.MyStyleData styleData = AndroidFindStyleApplicationsAction.getStyleData(tag);
    assertNotNull(styleData);
    AndroidFindStyleApplicationsProcessor processor =
      AndroidFindStyleApplicationsAction.createFindStyleApplicationsProcessor(tag, styleData, null);
    processor.configureScope(AndroidFindStyleApplicationsProcessor.MyScope.PROJECT, null);
    Collection<PsiFile> files = processor.collectFilesToProcess();
    assertEquals(1, files.size());
    XmlFile layoutFile = (XmlFile)files.iterator().next();
    assertInstanceOf(DomManager.getDomManager(myFixture.getProject()).getDomFileDescription(
      (XmlFile)layoutFile), LayoutDomFileDescription.class);
    final List<UsageInfo> usages = new ArrayList<>();
    processor.collectPossibleStyleApplications(layoutFile, usages);
    assertEquals(2, usages.size());
  }

  public void test2() throws Exception {
    doTest();
  }

  public void test3() throws Exception {
    doTest();
  }

  public void test4() throws Exception {
    doTest();
  }

  public void test5() throws Exception {
    doTest();
  }

  public void test6() throws Exception {
    final String testName = getTestName(true);
    myFixture.copyFileToProject(BASE_PATH + testName + "_layout.xml", "res/layout/layout1.xml");
    myFixture.copyFileToProject(BASE_PATH + testName + "_layout.xml", "res/layout/layout2.xml");

    doTest1();

    myFixture.checkResultByFile("res/layout/layout1.xml", BASE_PATH + testName + "_layout_after.xml", true);
    myFixture.checkResultByFile("res/layout/layout2.xml", BASE_PATH + testName + "_layout_after.xml", true);
  }

  public void test7() throws Exception {
    try {
      doTest();
      fail();
    }
    catch (RuntimeException e) {
      assertEquals("IDEA has not found any possible applications of style 'style1'", e.getMessage());
    }
  }

  private void doTest() {
    final String testName = getTestName(true);
    myFixture.copyFileToProject(BASE_PATH + testName + "_layout.xml", "res/layout/layout.xml");

    doTest1();

    myFixture.checkResultByFile("res/layout/layout.xml", BASE_PATH + testName + "_layout_after.xml", true);
  }

  private void doTest1() {
    final String testName = getTestName(true);
    final VirtualFile f = myFixture.copyFileToProject(BASE_PATH + testName + ".xml", "res/values/styles.xml");
    myFixture.configureFromExistingVirtualFile(f);
    myFixture.testAction(new AndroidFindStyleApplicationsAction(new AndroidFindStyleApplicationsAction.MyTestConfig(
      AndroidFindStyleApplicationsProcessor.MyScope.PROJECT)));
    myFixture.checkResultByFile(BASE_PATH + testName + ".xml");
  }
}
