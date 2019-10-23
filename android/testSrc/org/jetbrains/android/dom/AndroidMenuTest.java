package org.jetbrains.android.dom;

import com.android.SdkConstants;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.intentions.AndroidCreateOnClickHandlerAction;
import org.jetbrains.android.inspections.AndroidMissingOnClickHandlerInspection;

import java.io.IOException;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidMenuTest extends AndroidDomTestCase {
  public AndroidMenuTest() {
    super("dom/menu");
  }

  @Override
  protected boolean providesCustomManifest() {
    return true;
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
    myFixture.copyFileToProject(myTestFolder + "/OnClickActivity3.java", "src/p1/p2/Activity1.java");
    myFixture.copyFileToProject(myTestFolder + "/OnClickActivity4.java", "src/p1/p2/Activity2.java");
    doTestHighlighting();
  }

  public void testOnClickHighlighting2() throws Throwable {
    myFixture.allowTreeAccessForAllFiles();
    copyOnClickClasses();
    myFixture.enableInspections(AndroidMissingOnClickHandlerInspection.class);
    doTestHighlighting();
  }

  public void testOnClickHighlighting3() throws Throwable {
    myFixture.allowTreeAccessForAllFiles();
    myFixture.enableInspections(AndroidMissingOnClickHandlerInspection.class);
    myFixture.copyFileToProject(myTestFolder + "/OnClickActivity5.java", "src/p1/p2/Activity1.java");
    doTestHighlighting();
  }

  public void testOnClickHighlighting4() throws Throwable {
    myFixture.allowTreeAccessForAllFiles();
    myFixture.enableInspections(AndroidMissingOnClickHandlerInspection.class);
    myFixture.copyFileToProject(myTestFolder + "/OnClickActivity6.java", "src/p1/p2/Activity1.java");
    myFixture.copyFileToProject(myTestFolder + "/OnClickActivity4.java", "src/p1/p2/Activity2.java");
    doTestHighlighting();
  }

  public void testOnClickHighlightingAbs1() throws Throwable {
    copyAbsFiles();
    myFixture.allowTreeAccessForAllFiles();
    myFixture.enableInspections(AndroidMissingOnClickHandlerInspection.class);
    myFixture.copyFileToProject(myTestFolder + "/OnClickActivity2Abs.java", "src/p1/p2/Activity1.java");
    doTestHighlighting("onClickHighlightingAbs.xml");
  }

  public void testOnClickHighlightingAbs2() throws Throwable {
    copyAbsFiles();
    myFixture.allowTreeAccessForAllFiles();
    myFixture.enableInspections(AndroidMissingOnClickHandlerInspection.class);
    myFixture.copyFileToProject(myTestFolder + "/OnClickActivity3Abs.java", "src/p1/p2/Activity1.java");
    doTestHighlighting("onClickHighlightingAbs.xml");
  }

  public void testOnClickHighlightingJava() throws Throwable {
    myFixture.enableInspections(new UnusedDeclarationInspection());
    final VirtualFile f = myFixture.copyFileToProject(myTestFolder + "/" + getTestName(true) + ".java", "src/p1/p2/MyActivity1.java");
    myFixture.configureFromExistingVirtualFile(f);
    myFixture.checkHighlighting(true, false, false);
  }

  public void testOnClickCompletion() throws Throwable {
    copyOnClickClasses();
    doTestCompletionVariants(getTestName(true) + ".xml", "clickHandler1", "clickHandler7");
  }

  public void testOnClickIntention() throws Throwable {
    myFixture.copyFileToProject(myTestFolder + "/OnClickActivity.java", "src/p1/p2/Activity1.java");
    final VirtualFile file = copyFileToProject("onClickIntention.xml");
    myFixture.configureFromExistingVirtualFile(file);
    final AndroidCreateOnClickHandlerAction action = new AndroidCreateOnClickHandlerAction();
    assertTrue(action.isAvailable(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile()));
    WriteCommandAction.runWriteCommandAction(null, () -> action.invoke(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile()));

    myFixture.checkResultByFile(myTestFolder + "/onClickIntention.xml");
    myFixture.checkResultByFile("src/p1/p2/Activity1.java", myTestFolder + "/OnClickActivity_after.java", false);
  }

  public void testOnClickIntentionAbs() throws Throwable {
    copyAbsFiles();
    myFixture.copyFileToProject(myTestFolder + "/OnClickActivityAbs.java", "src/p1/p2/Activity1.java");
    final VirtualFile file = copyFileToProject("onClickIntention.xml");
    myFixture.configureFromExistingVirtualFile(file);
    final AndroidCreateOnClickHandlerAction action = new AndroidCreateOnClickHandlerAction();
    assertTrue(action.isAvailable(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile()));
    WriteCommandAction.runWriteCommandAction(null, () -> action.invoke(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile()));

    myFixture.checkResultByFile(myTestFolder + "/onClickIntention.xml");
    myFixture.checkResultByFile("src/p1/p2/Activity1.java", myTestFolder + "/OnClickActivityAbs_after.java", false);
  }

  public void testOnClickQuickFix1() throws Throwable {
    myFixture.enableInspections(AndroidMissingOnClickHandlerInspection.class);
    myFixture.copyFileToProject(myTestFolder + "/OnClickActivity.java", "src/p1/p2/Activity1.java");
    final VirtualFile file = copyFileToProject("onClickIntention.xml");
    myFixture.configureFromExistingVirtualFile(file);
    final List<IntentionAction> fixes = highlightAndFindQuickFixes(AndroidMissingOnClickHandlerInspection.MyQuickFix.class);
    assertEmpty(fixes);
  }

  public void testOnClickQuickFix2() throws Throwable {
    myFixture.enableInspections(AndroidMissingOnClickHandlerInspection.class);
    myFixture.copyFileToProject(myTestFolder + "/OnClickActivity1.java", "src/p1/p2/Activity1.java");
    final VirtualFile file = copyFileToProject("onClickIntention.xml");
    myFixture.configureFromExistingVirtualFile(file);
    final List<IntentionAction> actions = highlightAndFindQuickFixes(AndroidMissingOnClickHandlerInspection.MyQuickFix.class);
    assertEquals(1, actions.size());

    final IntentionAction action = actions.get(0);
    WriteCommandAction.runWriteCommandAction(null, () -> action.invoke(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile()));

    myFixture.checkResultByFile(myTestFolder + "/onClickIntention.xml");
    myFixture.checkResultByFile("src/p1/p2/Activity1.java", myTestFolder + "/OnClickActivity1_after.java", false);
  }

  public void testOnClickQuickFix3() throws Throwable {
    myFixture.enableInspections(AndroidMissingOnClickHandlerInspection.class);
    myFixture.copyFileToProject(myTestFolder + "/OnClickActivity1.java", "src/p1/p2/Activity1.java");
    final VirtualFile file = copyFileToProject("onClickIntention.xml");
    doTestOnClickQuickfix(file);
    myFixture.checkResultByFile("src/p1/p2/Activity1.java", myTestFolder + "/OnClickActivity2_after.java", false);
  }

  public void testOnClickQuickFix4() throws Throwable {
    myFixture.enableInspections(AndroidMissingOnClickHandlerInspection.class);
    myFixture.copyFileToProject(myTestFolder + "/OnClickActivity1.java", "src/p1/p2/Activity1.java");
    myFixture.copyFileToProject(myTestFolder + "/OnClickActivity4.java", "src/p1/p2/Activity2.java");
    final VirtualFile file = copyFileToProject("onClickIntention.xml");
    doTestOnClickQuickfix(file);
    myFixture.checkResultByFile("src/p1/p2/Activity1.java", myTestFolder + "/OnClickActivity1_after.java", false);
  }

  public void testOnClickQuickFixAbs() throws Throwable {
    copyAbsFiles();
    myFixture.enableInspections(AndroidMissingOnClickHandlerInspection.class);
    myFixture.copyFileToProject(myTestFolder + "/OnClickActivity1Abs.java", "src/p1/p2/Activity1.java");
    final VirtualFile file = copyFileToProject("onClickIntention.xml");
    doTestOnClickQuickfix(file);
    myFixture.checkResultByFile("src/p1/p2/Activity1.java", myTestFolder + "/OnClickActivity1Abs_after.java", false);
  }

  private void copyAbsFiles() {
    myFixture.copyFileToProject(myTestFolder + "/Watson.java", "src/android/support/v4/app/Watson.java");
    myFixture.copyFileToProject(myTestFolder + "/MenuItem.java", "src/com/actionbarsherlock/view/MenuItem.java");
  }

  public void testActionProviderClass() throws Throwable {
    copyFileToProject("MyProvider.java", "src/p1/p2/MyProvider.java");
    copyFileToProject("MyView.java", "src/p1/p2/MyView.java");
    toTestCompletion("actionProvider.xml", "actionProvider_after.xml");
  }

  public void testActionProviderAndroidxClass() throws Throwable {
    myFixture.addClass("package androidx.core.view; public class ActionProvider {}");
    myFixture.addClass("package p1.p2; public class MyAndroidxActionProvider extends androidx.core.view.ActionProvider {}");
    copyFileToProject("MyProvider.java", "src/p1/p2/MyProvider.java");

    doTestCompletionVariants("actionProvider.xml", "p1.p2.MyProvider", "p1.p2.MyAndroidxActionProvider");
  }

  // Test completion for action providers that are provided by Android framework
  // Regression test for http://b.android.com/78022
  public void testActionProviderFrameworkClass() throws Throwable {
    toTestCompletion("actionProviderFramework.xml", "actionProviderFramework_after.xml");
  }

  // Bogus attribute "action-layout" should not be shown in menu item tags
  // Regression test for http://b.android.com/191448
  public void testMenuItemAttributeCompletion() throws Throwable {
    doTestCompletionVariants("menu_item_attribute.xml", "actionLayout", "actionProviderClass", "actionViewClass", "showAsAction");
  }

  // One more regression test for http://b.android.com/191448
  // Non-existing "title-condensed" attribute shouldn't show up in autocompletion
  public void testMenuItemAttributeCompletion2() throws Throwable {
    doTestCompletionVariants("menu_item_attribute_2.xml", "title", "titleCondensed");
  }

  public void testActionViewClass() throws Throwable {
    copyFileToProject("MyProvider.java", "src/p1/p2/MyProvider.java");
    copyFileToProject("MyView.java", "src/p1/p2/MyView.java");
    copyFileToProject("MyPackagePrivateView.java", "src/p1/p2/MyPackagePrivateView.java");

    // There are two View subclasses in the project, but only one is public and it should be inserted.
    toTestCompletion("actionView.xml", "actionView_after.xml");
  }
}
