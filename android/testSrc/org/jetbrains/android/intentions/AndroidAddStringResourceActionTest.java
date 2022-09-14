// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.android.intentions;

import com.android.resources.ResourceType;
import com.android.tools.idea.startup.ExternalAnnotationsSupport;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

public class AndroidAddStringResourceActionTest extends AndroidTestCase {
  private static final String BASE_PATH = "addStringRes/";

  @Override
  protected boolean providesCustomManifest() {
    return true;
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    TemplateManagerImpl.setTemplateTesting(getTestRootDisposable());

    Sdk sdk = ModuleRootManager.getInstance(myModule).getSdk();
    assertNotNull(sdk);
    ExternalAnnotationsSupport.addAnnotations(sdk);
  }

  public void test1() throws IOException {
    doTest();
  }

  public void test2() throws IOException {
    doTest();
  }

  public void test3() throws IOException {
    doTest();
  }

  public void test4() throws IOException {
    doTest();
  }

  public void test5() throws IOException {
    doTest();
  }

  public void test6() throws IOException {
    doTest();
  }

  public void test7() throws IOException {
    doTest(new Runnable() {
      @Override
      public void run() {
        myFixture.type('c');
        TemplateManagerImpl.getTemplateState(myFixture.getEditor()).nextTab();
      }
    });
  }

  public void test8() throws IOException {
    doTest();
  }

  public void test9() throws IOException {
    doTest();
  }

  public void test10() throws IOException {
    doTest();
  }

  public void test11() throws IOException {
    doTest(new Runnable() {
      @Override
      public void run() {
        myFixture.type('c');
        myFixture.performEditorAction(IdeActions.ACTION_CHOOSE_LOOKUP_ITEM);
      }
    });
  }

  public void test12() throws IOException {
    doTest();
  }

  public void test13() throws IOException {
    doTest();
  }

  public void test14() {
    VirtualFile javaFile = myFixture.copyFileToProject(BASE_PATH + "Class14.java", "src/p1/p2/Class.java");
    myFixture.configureFromExistingVirtualFile(javaFile);
    final PsiFile javaPsiFile = myFixture.getFile();
    assertFalse(new AndroidAddStringResourceAction().isAvailable(myFixture.getProject(), myFixture.getEditor(), javaPsiFile));
  }

  public void test15() throws IOException {
    doTest(getTestName(false), "strings.xml", null, true, "strings15_after.xml");
  }

  public void testPreferencesFile() throws IOException {
    // Regression test for http://b/136596952
    createManifest();
    final VirtualFile file = myFixture.copyFileToProject(BASE_PATH + "preferences.xml", "res/xml/preferences.xml");
    doExtractAndCheckStringsXml("strings.xml", null, true, "strings_after.xml", file);
    myFixture.checkResultByFile(BASE_PATH + "preferences_after.xml");
  }

  public void testFragment() throws IOException {
    doTest();
  }

  public void testEscape() throws IOException {
    doTest(getTestName(false), "strings.xml", null, true, "strings_escape_after.xml");
  }

  public void testNewFile() throws IOException {
    doTest("1", null, null, true);
  }

  public void testInvalidStringsXml() throws IOException {
    try {
      doTest("1", "strings_invalid.xml", null, true);
      fail();
    }
    catch (IncorrectOperationException e) {
      // in normal mode error dialog will be shown
      assertEquals("File strings.xml is not resource file", e.getMessage());
    }
    myFixture.checkResultByFile(BASE_PATH + "Class1.java");
    myFixture.checkResultByFile("res/values/strings.xml", BASE_PATH + "strings_invalid.xml", false);
  }

  public void testFromLayout() throws IOException {
    createManifest();
    final VirtualFile file = myFixture.copyFileToProject(BASE_PATH + getTestName(true) + ".xml", "res/layout/layout.xml");
    doExtractAndCheckStringsXml("strings.xml", null, true, "strings_after.xml", file);
    myFixture.checkResultByFile(BASE_PATH + getTestName(true) + "_after.xml");
  }

  public void testFromLayout1() throws IOException {
    createManifest();
    final VirtualFile file = myFixture.copyFileToProject(BASE_PATH + getTestName(true) + ".xml", "res/layout/layout.xml");
    myFixture.configureFromExistingVirtualFile(file);
    assertFalse(new AndroidAddStringResourceAction().isAvailable(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile()));
  }

  public void testFromLayout2() throws IOException {
    createManifest();
    final VirtualFile file = myFixture.copyFileToProject(BASE_PATH + getTestName(true) + ".xml", "res/layout/layout.xml");
    doExtractAndCheckStringsXml("strings.xml", null, true, getTestName(true) + "_strings_after.xml", file);
    myFixture.checkResultByFile(BASE_PATH + getTestName(true) + "_after.xml");
  }

  public void testFromLayout3() throws IOException {
    createManifest();
    final VirtualFile file = myFixture.copyFileToProject(BASE_PATH + getTestName(true) + ".xml", "res/layout/layout.xml");
    doExtractAndCheckStringsXml("strings.xml", null, true, getTestName(true) + "_strings_after.xml", file);
    myFixture.checkResultByFile(BASE_PATH + getTestName(true) + "_after.xml");
  }

  public void testFromManifest() {
    final VirtualFile file = myFixture.copyFileToProject(BASE_PATH + getTestName(true) + ".xml", "AndroidManifest.xml");
    myFixture.configureFromExistingVirtualFile(file);
    assertFalse(new AndroidAddStringResourceAction().isAvailable(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile()));
  }

  public void testFromManifest1() {
    final VirtualFile file = myFixture.copyFileToProject(BASE_PATH + getTestName(true) + ".xml", "AndroidManifest.xml");
    doExtractAndCheckStringsXml("strings.xml", null, true, "strings_after.xml", file);
    myFixture.checkResultByFile(BASE_PATH + getTestName(true) + "_after.xml");
  }

  public void testFromManifest2() {
    final VirtualFile file = myFixture.copyFileToProject(BASE_PATH + getTestName(true) + ".xml", "AndroidManifest.xml");
    myFixture.configureFromExistingVirtualFile(file);
    assertFalse(new AndroidAddStringResourceAction().isAvailable(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile()));
  }

  public void testUseResourceId() throws IOException {
    doTest();
  }

  public void testDontUseResourceIdForArgWithoutStringResAnnotation() throws IOException {
    doTest();
  }

  private void doTest() throws IOException {
    doTest(getTestName(false), "strings.xml", null, true);
  }

  private void doTest(Runnable invokeAfterTemplate) throws IOException {
    doTest(getTestName(false), "strings.xml", invokeAfterTemplate, false);
  }

  private void doTest(String testName, String stringsXml, final Runnable invokeAfterTemplate, final boolean closePopup) throws IOException {
    doTest(testName, stringsXml, invokeAfterTemplate, closePopup, "strings_after.xml");
  }

  private void doTest(String testName,
                      String stringsXml,
                      @Nullable final Runnable invokeAfterTemplate,
                      final boolean closePopup,
                      String stringsAfter) throws IOException {
    createManifest();
    VirtualFile javaFile = myFixture.copyFileToProject(BASE_PATH + "Class" + testName + ".java", "src/p1/p2/Class.java");
    doExtractAndCheckStringsXml(stringsXml, invokeAfterTemplate, closePopup, stringsAfter, javaFile);
    myFixture.checkResultByFile(BASE_PATH + "Class" + testName + "_after.java");
  }

  private void doExtractAndCheckStringsXml(String stringsXml,
                                           final Runnable invokeAfterTemplate,
                                           final boolean closePopup,
                                           String stringsAfter,
                                           VirtualFile javaFile) {
    if (stringsXml != null) {
      myFixture.copyFileToProject(BASE_PATH + stringsXml, "res/values/strings.xml");
    }
    myFixture.configureFromExistingVirtualFile(javaFile);
    final PsiFile javaPsiFile = myFixture.getFile();
    assertTrue(new AndroidAddStringResourceAction().isAvailable(myFixture.getProject(), myFixture.getEditor(), javaPsiFile));
    CommandProcessor.getInstance().executeCommand(getProject(), new Runnable() {
      @Override
      public void run() {
        new AndroidAddStringResourceAction().invoke(myFixture.getProject(), myFixture.getEditor(), javaPsiFile);
        if (invokeAfterTemplate != null) {
          invokeAfterTemplate.run();
        }
        if (closePopup) {
          myFixture.performEditorAction(IdeActions.ACTION_EDITOR_ESCAPE);
        }
      }
    }, "", "");
    myFixture.checkResultByFile("res/values/strings.xml", BASE_PATH + stringsAfter, false);
  }
}
