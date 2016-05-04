/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.jetbrains.android;

import com.android.SdkConstants;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.PackageWrapper;
import com.intellij.refactoring.actions.RenameElementAction;
import com.intellij.refactoring.move.moveClassesOrPackages.MoveClassesOrPackagesProcessor;
import com.intellij.refactoring.move.moveClassesOrPackages.SingleSourceRootMoveDestination;
import com.intellij.refactoring.rename.RenameProcessor;
import com.intellij.refactoring.rename.RenamePsiElementProcessor;
import com.intellij.testFramework.TestActionEvent;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: Eugene.Kudelevsky
 * Date: Aug 9, 2009
 * Time: 8:50:20 PM
 * To change this template use File | Settings | File Templates.
 */
public class AndroidRenameTest extends AndroidTestCase {
  private static final String BASE_PATH = "/rename/";
  private static final String R_JAVA_PATH = "gen/p1/p2/R.java";

  public AndroidRenameTest() {
    super(false);
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    AndroidResourceRenameResourceProcessor.ASK = false;
  }

  @Override
  public void tearDown() throws Exception {
    super.tearDown();
    // Restore static flag to its default value.
    AndroidResourceRenameResourceProcessor.ASK = true;
  }

  public void testXmlReferenceToFileResource() throws Throwable {
    createManifest();
    VirtualFile file = myFixture.copyFileToProject(BASE_PATH + "layout1.xml", "res/layout/layout1.xml");
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.copyFileToProject(BASE_PATH + "pic.png", "res/drawable/pic.png");
    myFixture.copyFileToProject(BASE_PATH + "styles.xml", "res/values/styles.xml");
    myFixture.copyFileToProject("R.java", R_JAVA_PATH);
    myFixture.copyFileToProject(BASE_PATH + "RefR2.java", "src/p1/p2/RefR2.java");
    renameElementWithTextOccurences("pic1.png");
    myFixture.checkResultByFile(BASE_PATH + "layout_file_after.xml");
    myFixture.checkResultByFile(R_JAVA_PATH, "R.java", true);
    myFixture.checkResultByFile("res/values/styles.xml", BASE_PATH + "styles_after.xml", true);
    myFixture.checkResultByFile("src/p1/p2/RefR2.java", BASE_PATH + "RefR2_after.java", true);
    assertNotNull(myFixture.findFileInTempDir("res/drawable/pic1.png"));
  }

  public void testXmlReferenceToFileResource1() throws Throwable {
    createManifest();
    VirtualFile file = myFixture.copyFileToProject(BASE_PATH + "layout1.xml", "res/layout/layout1.xml");
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.copyFileToProject(BASE_PATH + "pic.png", "res/drawable/pic.9.png");
    myFixture.copyFileToProject(BASE_PATH + "styles.xml", "res/values/styles.xml");
    myFixture.copyFileToProject("R.java", R_JAVA_PATH);
    myFixture.copyFileToProject(BASE_PATH + "RefR2.java", "src/p1/p2/RefR2.java");
    renameElementWithTextOccurences("pic1.9.png");
    myFixture.checkResultByFile(BASE_PATH + "layout_file_after.xml");
    myFixture.checkResultByFile(R_JAVA_PATH, "R.java", true);
    myFixture.checkResultByFile("res/values/styles.xml", BASE_PATH + "styles_after.xml", true);
    myFixture.checkResultByFile("src/p1/p2/RefR2.java", BASE_PATH + "RefR2_after.java", true);
    assertNotNull(myFixture.findFileInTempDir("res/drawable/pic1.9.png"));
  }

  // Regression test for http://b.android.com/174014
  // Bug: when mipmap resource was renamed, new reference contained ".png" file extension.
  public void testXmlReferenceToFileResource2() throws Throwable {
    myFixture.copyFileToProject(BASE_PATH + "pic.png", "res/mipmap/pic.png");
    myFixture.configureFromExistingVirtualFile(
      myFixture.copyFileToProject(BASE_PATH + "AndroidManifest_mipmap_before.xml", "AndroidManifest.xml"));
    renameElementWithTextOccurences("app_icon.png");
    myFixture.checkResultByFile("AndroidManifest.xml", BASE_PATH + "AndroidManifest_mipmap_after.xml", true);
    assertNotNull(myFixture.findFileInTempDir("res/mipmap/app_icon.png"));
  }

  // Regression test for raw resources renaming, http://b.android.com/183128
  public void testXmlReferenceToFileResource3() throws Throwable {
    myFixture.copyFileToProject(BASE_PATH + "styles.xml", "res/raw/raw_resource.txt");
    myFixture
      .configureFromExistingVirtualFile(myFixture.copyFileToProject(BASE_PATH + "AndroidManifest_raw_before.xml", "AndroidManifest.xml"));
    renameElementWithTextOccurences("new_raw_resource.txt");
    myFixture.checkResultByFile("AndroidManifest.xml", BASE_PATH + "AndroidManifest_raw_after.xml", true);
    assertNotNull(myFixture.findFileInTempDir("res/raw/new_raw_resource.txt"));
  }

  // Regression test for transition resources renaming, http://b.android.com/183128
  public void testXmlReferenceToFileResource4() throws Throwable {
    myFixture.copyFileToProject(BASE_PATH + "transition.xml", "res/transition/great.xml");
    myFixture.configureFromExistingVirtualFile(myFixture.copyFileToProject(BASE_PATH + "styles12.xml", "res/values/styles.xml"));
    renameElementWithTextOccurences("good.xml");
    myFixture.checkResultByFile("res/values/styles.xml", BASE_PATH + "styles12_after.xml", true);
    assertNotNull(myFixture.findFileInTempDir("res/transition/good.xml"));
  }

  public void testMoveApplicationClass() throws Throwable {
    deleteManifest();
    myFixture.copyFileToProject(BASE_PATH + "MyApplication.java", "src/p1/p2/MyApplication.java");
    VirtualFile f = myFixture.copyFileToProject(BASE_PATH + getTestName(true) + ".xml", "AndroidManifest.xml");
    myFixture.configureFromExistingVirtualFile(f);
    moveClass("p1.p2.MyApplication", "p1");
    myFixture.checkResultByFile(BASE_PATH + getTestName(true) + "_after.xml");
  }

  private void renameElementWithTextOccurences(final String newName) throws Throwable {
    Editor editor = myFixture.getEditor();
    PsiFile file = myFixture.getFile();
    Editor completionEditor = InjectedLanguageUtil.getEditorForInjectedLanguageNoCommit(editor, file);
    PsiElement element = TargetElementUtil.findTargetElement(completionEditor, TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED |
                                                                               TargetElementUtil.ELEMENT_NAME_ACCEPTED);
    assert element != null;
    final PsiElement substitution = RenamePsiElementProcessor.forElement(element).substituteElementToRename(element, editor);
    new RenameProcessor(myFixture.getProject(), substitution, newName, false, true).run();
  }

  private void moveClass(final String className, final String newPackageName) throws Throwable {
    PsiClass aClass = JavaPsiFacade.getInstance(getProject()).findClass(className, GlobalSearchScope.projectScope(getProject()));
    PsiPackage aPackage = JavaPsiFacade.getInstance(getProject()).findPackage(newPackageName);

    final PsiDirectory[] dirs = aPackage.getDirectories();
    assertEquals(dirs.length, 1);

    new MoveClassesOrPackagesProcessor(getProject(), new PsiElement[]{aClass}, new SingleSourceRootMoveDestination(
      PackageWrapper.create(JavaDirectoryService.getInstance().getPackage(dirs[0])), dirs[0]), true, true, null).run();
  }

  public void testXmlReferenceToValueResource() throws Throwable {
    createManifest();
    VirtualFile file = myFixture.copyFileToProject(BASE_PATH + "layout2.xml", "res/layout/layout2.xml");
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.copyFileToProject(BASE_PATH + "strings.xml", "res/values/strings.xml");
    myFixture.copyFileToProject("R.java", R_JAVA_PATH);
    myFixture.copyFileToProject(BASE_PATH + "RefR1.java", "src/p1/p2/RefR1.java");
    checkAndRename("str1");
    myFixture.checkResultByFile(BASE_PATH + "layout_value_after.xml");
    myFixture.checkResultByFile(R_JAVA_PATH, "R.java", true);
    myFixture.checkResultByFile("src/p1/p2/RefR1.java", BASE_PATH + "RefR1_after.java", true);
    myFixture.checkResultByFile("res/values/strings.xml", BASE_PATH + "strings_after.xml", true);
  }

  public void testValueResource1() throws Throwable {
    doTestStringRename("strings1.xml");
  }

  public void testValueResource2() throws Throwable {
    doTestStringRename("strings2.xml");
  }

  public void testValueResource3() throws Throwable {
    doTestStringRename("strings3.xml");
  }

  public void testValueResource4() throws Throwable {
    doTestStringRename("strings4.xml");
  }

  public void testValueResource5() throws Throwable {
    String before = "strings5.xml";
    String after = "strings5_after.xml";

    createManifest();
    VirtualFile file = myFixture.copyFileToProject(BASE_PATH + before, "res/values/strings.xml");
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.renameElementAtCaretUsingHandler("str1");
    myFixture.checkResultByFile(BASE_PATH + after);
  }

  public void testStyleInheritance() throws Throwable {
    doTestStyleInheritance("styles1.xml", "styles1_after.xml");
  }

  public void testStyleInheritance1() throws Throwable {
    doTestStyleInheritance("styles2.xml", "styles2_after.xml");
  }

  public void testStyleInheritance2() throws Throwable {
    doTestStyleInheritance("styles3.xml", "styles3_after.xml");
  }

  public void testStyleInheritance3() throws Throwable {
    doTestStyleInheritance("styles4.xml", "styles4_after.xml");
  }

  public void testStyleInheritance4() throws Throwable {
    doTestStyleInheritance("styles5.xml", "styles5_after.xml", "Dilimiter.Horisontal");
  }

  public void testStyleInheritance5() throws Throwable {
    myFixture.copyFileToProject(BASE_PATH + "styles6_1.xml", "res/values-en/styles6.xml");
    myFixture.copyFileToProject(BASE_PATH + "styles6_2.xml", "res/values-ru/styles6.xml");
    doTestStyleInheritance("styles6.xml", "styles6_after.xml");
  }

  public void testStyleInheritance6() throws Throwable {
    doTestStyleInheritance("styles7.xml", "styles7_after.xml");
  }

  public void testStyleInheritance7() throws Throwable {
    myFixture.copyFileToProject(BASE_PATH + "styles8_1.xml", "res/values-en/styles8.xml");
    myFixture.copyFileToProject(BASE_PATH + "styles8_2.xml", "res/values-ru/styles8.xml");
    doTestStyleInheritance("styles8.xml", "styles8_after.xml");
  }

  public void testStyleInheritance8() throws Throwable {
    doTestStyleInheritance("styles9.xml", "styles9_after.xml");
  }

  public void testStyleInheritance9() throws Throwable {
    doTestStyleInheritance("styles10.xml", "styles10_after.xml");
  }

  public void testStyleInheritance10() throws Throwable {
    doTestStyleInheritance("styles11.xml", "styles11_after.xml", "myStyle.s1");
  }

  private void doTestStyleInheritance(String before, String after) throws IOException {
    doTestStyleInheritance(before, after, "newStyle");
  }

  private void doTestStyleInheritance(String before, String after, String newName) throws IOException {
    createManifest();
    final VirtualFile file = myFixture.copyFileToProject(BASE_PATH + before, "res/values/" + before);
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.renameElementAtCaretUsingHandler(newName);
    myFixture.checkResultByFile(BASE_PATH + after);
  }

  private void doTestStringRename(String fileName) throws IOException {
    createManifest();
    VirtualFile file = myFixture.copyFileToProject(BASE_PATH + fileName, "res/values/strings.xml");
    myFixture.configureFromExistingVirtualFile(file);

    myFixture.copyFileToProject(BASE_PATH + "layoutStrUsage.xml", "res/layout/layoutStrUsage.xml");
    myFixture.copyFileToProject("R.java", R_JAVA_PATH);
    myFixture.renameElementAtCaretUsingHandler("str1");

    myFixture.checkResultByFile(BASE_PATH + "strings_after.xml");
    myFixture.checkResultByFile(R_JAVA_PATH, "R.java", true);
    myFixture.checkResultByFile("res/layout/layoutStrUsage.xml", BASE_PATH + "layoutStrUsage_after.xml", true);
  }

  public void testJavaReferenceToFileResource() throws Throwable {
    createManifest();
    VirtualFile file = myFixture.copyFileToProject(BASE_PATH + "RefR3.java", "src/p1/p2/RefR3.java");
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.copyFileToProject("R.java", R_JAVA_PATH);
    myFixture.copyFileToProject(BASE_PATH + "layout3.xml", "res/layout/layout3.xml");
    myFixture.copyFileToProject(BASE_PATH + "pic.png", "res/drawable/pic.png");
    checkAndRename("pic1");
    myFixture.checkResultByFile(BASE_PATH + "RefR3_after.java", true);
    myFixture.checkResultByFile("res/layout/layout3.xml", BASE_PATH + "layout_file_after.xml", true);
    assertNotNull(myFixture.findFileInTempDir("res/drawable/pic1.png"));
  }

  // Regression test for http://b.android.com/135180
  public void testJavaReferenceToFileResourceWithUnderscores() throws Throwable {
    createManifest();
    VirtualFile file = myFixture.copyFileToProject(BASE_PATH + "RefR12.java", "src/p1/p2/RefR.java");
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.copyFileToProject("R.java", R_JAVA_PATH);
    myFixture.copyFileToProject(BASE_PATH + "pic.png", "res/drawable/pic.png");
    checkAndRename("my_pic");
    myFixture.checkResultByFile(BASE_PATH + "RefR12_after.java");
    assertNotNull(myFixture.findFileInTempDir("res/drawable/my_pic.png"));
  }

  public void testJavaReferenceToValueResource() throws Throwable {
    createManifest();
    VirtualFile file = myFixture.copyFileToProject(BASE_PATH + "RefR4.java", "src/p1/p2/RefR4.java");
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.copyFileToProject("R.java", R_JAVA_PATH);
    myFixture.copyFileToProject(BASE_PATH + "layout4.xml", "res/layout/layout4.xml");
    myFixture.copyFileToProject(BASE_PATH + "strings.xml", "res/values/strings.xml");
    checkAndRename("str1");
    myFixture.checkResultByFile(BASE_PATH + "RefR4_after.java", true);
    myFixture.checkResultByFile("res/layout/layout4.xml", BASE_PATH + "layout_value_after.xml", true);
    myFixture.checkResultByFile("res/values/strings.xml", BASE_PATH + "strings_after.xml", true);
  }

  public void testXmlReferenceToId() throws Throwable {
    createManifest();
    VirtualFile file = myFixture.copyFileToProject(BASE_PATH + "layout5.xml", "res/layout/layout5.xml");
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.copyFileToProject("R.java", R_JAVA_PATH);
    checkAndRename("@+id/anchor1");
    myFixture.checkResultByFile(BASE_PATH + "layout_id_after.xml");
    myFixture.checkResultByFile(R_JAVA_PATH, "R.java", true);
  }

  public void testIdDeclaration() throws Throwable {
    createManifest();
    VirtualFile file = myFixture.copyFileToProject(BASE_PATH + "layout6.xml", "res/layout/layout6.xml");
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.copyFileToProject("R.java", R_JAVA_PATH);
    checkAndRename("@+id/anchor1");
    myFixture.checkResultByFile(BASE_PATH + "layout_id_after.xml");
    myFixture.checkResultByFile(R_JAVA_PATH, "R.java", true);
  }

  public void testJavaReferenceToId() throws Throwable {
    createManifest();
    VirtualFile file = myFixture.copyFileToProject(BASE_PATH + "RefR7.java", "src/p1/p2/RefR7.java");
    myFixture.copyFileToProject("R.java", R_JAVA_PATH);
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.copyFileToProject(BASE_PATH + "layout7.xml", "res/layout/layout7.xml");
    checkAndRename("anchor1");
    myFixture.checkResultByFile(BASE_PATH + "RefR7_after.java", true);
    myFixture.checkResultByFile("res/layout/layout7.xml", BASE_PATH + "layout_id_after.xml", true);
  }

  public void testJavaReferenceToId1() throws Throwable {
    createManifest();
    VirtualFile file = myFixture.copyFileToProject(BASE_PATH + "RefR7.java", "src/p1/p2/RefR7.java");
    myFixture.copyFileToProject("R.java", R_JAVA_PATH);
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.copyFileToProject(BASE_PATH + "layout7.xml", "res/layout/l1.xml");
    myFixture.copyFileToProject(BASE_PATH + "layout7.xml", "res/layout/l2.xml");
    checkAndRename("anchor1");
    myFixture.checkResultByFile(BASE_PATH + "RefR7_after.java", true);
    myFixture.checkResultByFile("res/layout/l1.xml", BASE_PATH + "layout_id_after.xml", true);
    myFixture.checkResultByFile("res/layout/l2.xml", BASE_PATH + "layout_id_after.xml", true);
  }

  public void testStyleable() throws Throwable {
    createManifest();
    VirtualFile file = myFixture.copyFileToProject(BASE_PATH + "RefR8.java", "src/p1/p2/RefR8.java");
    myFixture.copyFileToProject("R.java", R_JAVA_PATH);
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.copyFileToProject(BASE_PATH + "attrs8.xml", "res/values/attrs8.xml");
    checkAndRename("LabelView1");
    myFixture.checkResultByFile(BASE_PATH + "RefR8_after.java", true);
    myFixture.checkResultByFile("res/values/attrs8.xml", BASE_PATH + "attrs8_after.xml", true);
  }

  public void testAttr() throws Throwable {
    createManifest();
    VirtualFile file = myFixture.copyFileToProject(BASE_PATH + "RefR9.java", "src/p1/p2/RefR9.java");
    myFixture.copyFileToProject("R.java", R_JAVA_PATH);
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.copyFileToProject(BASE_PATH + "attrs9.xml", "res/values/attrs9.xml");
    checkAndRename("attr1");
    myFixture.checkResultByFile(BASE_PATH + "RefR9_after.java", true);
    myFixture.checkResultByFile("res/values/attrs9.xml", BASE_PATH + "attrs9_after.xml", true);
  }

  public void testRenameDeclareStyleableFromJava() throws Throwable {
    // Renaming an R styleable field should update the declare styleable declaration, as well as
    // any field references, including those for the attributes
    createManifest();
    myFixture.copyFileToProject(BASE_PATH + "attrs10.xml", "res/values/attrs10.xml");
    myFixture.copyFileToProject(BASE_PATH + "R_MyView.java", "src/p1/p2/R.java");

    final VirtualFile file = myFixture.copyFileToProject(BASE_PATH + "MyView1.java", "src/p1/p2/MyView.java");
    myFixture.configureFromExistingVirtualFile(file);
    checkAndRename("NewName");
    myFixture.checkResultByFile(BASE_PATH + "MyView1_after.java", true);
    myFixture.checkResultByFile("res/values/attrs10.xml", BASE_PATH + "attrs10_after.xml", true);
  }

  public void testRenameDeclareStyleableFromXml() throws Throwable {
    // Like testRenameDeclareStyleableFromJava, but the rename request originates from
    // the XML declare-styleable reference rather than a Java field reference.
    createManifest();
    myFixture.copyFileToProject("R.java", R_JAVA_PATH);
    myFixture.copyFileToProject(BASE_PATH + "MyView4.java", "src/p1/p2/MyView.java");
    final VirtualFile file = myFixture.copyFileToProject(BASE_PATH + "attrs13.xml", "res/values/attrs13.xml");
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.renameElementAtCaretUsingHandler("NewName");
    myFixture.checkResultByFile(BASE_PATH + "attrs13_after.xml", true);
    myFixture.checkResultByFile("src/p1/p2/MyView.java", BASE_PATH + "MyView4_after.java", true);
  }

  public void testRenameDeclareStyleableAttrFromJava() throws Throwable {
    // Renaming a styleable field should update the attrs.xml and field references
    createManifest();
    myFixture.copyFileToProject(BASE_PATH + "attrs11.xml", "res/values/attrs11.xml");
    myFixture.copyFileToProject(BASE_PATH + "R_MyView.java", "src/p1/p2/R.java");
    final VirtualFile file = myFixture.copyFileToProject(BASE_PATH + "MyView2.java", "src/p1/p2/MyView.java");
    myFixture.configureFromExistingVirtualFile(file);
    checkAndRename("newname");
    myFixture.checkResultByFile(BASE_PATH + "MyView2_after.java", true);
    myFixture.checkResultByFile("res/values/attrs11.xml", BASE_PATH + "attrs11_after.xml", true);
  }

  public void testRenameDeclareStyleableAttrFromXml() throws Throwable {
    createManifest();
    myFixture.copyFileToProject("R.java", R_JAVA_PATH);
    myFixture.copyFileToProject(BASE_PATH + "MyView5.java", "src/p1/p2/MyView.java");
    final VirtualFile file = myFixture.copyFileToProject(BASE_PATH + "attrs14.xml", "res/values/attrs14.xml");
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.renameElementAtCaretUsingHandler("newname");
    myFixture.checkResultByFile(BASE_PATH + "attrs14_after.xml", true);
    myFixture.checkResultByFile("src/p1/p2/MyView.java", BASE_PATH + "MyView5_after.java", true);
  }

  public void testRenameCustomView() throws Throwable {
    // Make sure renaming a custom view causes the styleable references to be updated as well
    createManifest();
    myFixture.copyFileToProject(BASE_PATH + "attrs12.xml", "res/values/attrs12.xml");
    myFixture.copyFileToProject(BASE_PATH + "R_MyView.java", "src/p1/p2/R.java");

    final VirtualFile file = myFixture.copyFileToProject(BASE_PATH + "MyView3.java", "src/p1/p2/MyView.java");
    myFixture.configureFromExistingVirtualFile(file);
    checkAndRename("NewName");
    myFixture.checkResultByFile(BASE_PATH + "MyView3_after.java", true);
    myFixture.checkResultByFile("res/values/attrs12.xml", BASE_PATH + "attrs12_after.xml", true);
  }

  public void testFileResourceAliases1() throws Throwable {
    // Rename aliases involving drawables; ensure that they are handled correctly.
    // This tests that both the <item name="<name>"> and @layout/<name> references are
    // updated when name.xml is updated
    createManifest();
    VirtualFile file = myFixture.copyFileToProject(BASE_PATH + "RefR10.java", "src/p1/p2/RefR10.java");
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.copyFileToProject("R.java", R_JAVA_PATH);
    myFixture.copyFileToProject(BASE_PATH + "pic.png", "res/drawable/pic.png");
    myFixture.copyFileToProject(BASE_PATH + "pic.png", "res/drawable/pic2.png");
    myFixture.copyFileToProject(BASE_PATH + "aliases.xml", "res/values-sw600dp/aliases.xml");
    checkAndRename("newpic");
    myFixture.checkResultByFile(BASE_PATH + "RefR10_after.java", true);
    myFixture.checkResultByFile("res/values-sw600dp/aliases.xml", BASE_PATH + "aliases_after.xml", true);
    assertNotNull(myFixture.findFileInTempDir("res/drawable/newpic.png"));
  }

  public void testFileResourceAliases2() throws Throwable {
    // Rename aliases involving layouts; this test checks that a layout reference like @layout/foo is
    // updated when foo.xml is renamed
    createManifest();
    VirtualFile file = myFixture.copyFileToProject(BASE_PATH + "RefR11.java", "src/p1/p2/RefR11.java");
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.copyFileToProject("R.java", R_JAVA_PATH);
    myFixture.copyFileToProject(BASE_PATH + "layout3.xml", "res/layout/mainlayout.xml");
    myFixture.copyFileToProject(BASE_PATH + "layout3.xml", "res/layout/layout3.xml");
    //myFixture.copyFileToProject(BASE_PATH + "aliases2.xml", "res/values-sw600dp/aliases.xml");
    myFixture.copyFileToProject(BASE_PATH + "aliases2.xml", "res/values-land/aliases.xml");
    checkAndRename("newlayout");
    myFixture.checkResultByFile(BASE_PATH + "RefR11_after.java", true);
    myFixture.checkResultByFile("res/values-land/aliases.xml", BASE_PATH + "aliases2_after.xml", true);
    assertNotNull(myFixture.findFileInTempDir("res/layout/newlayout.xml"));
  }

  public void testFileResourceAliases3() throws Throwable {
    // Rename aliases involving layouts; this test checks that a resource alias' name declaration (<item name="foo" type="layout">)
    // is updated when foo.xml is renamed
    createManifest();
    VirtualFile file = myFixture.copyFileToProject(BASE_PATH + "RefR11.java", "src/p1/p2/RefR11.java");
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.copyFileToProject("R.java", R_JAVA_PATH);
    myFixture.copyFileToProject(BASE_PATH + "layout3.xml", "res/layout/mainlayout.xml");
    myFixture.copyFileToProject(BASE_PATH + "layout3.xml", "res/layout/layout3.xml");
    myFixture.copyFileToProject(BASE_PATH + "aliases3.xml", "res/values-land/aliases.xml");
    checkAndRename("newlayout");
    myFixture.checkResultByFile(BASE_PATH + "RefR11_after.java", true);
    myFixture.checkResultByFile("res/values-land/aliases.xml", BASE_PATH + "aliases3_after.xml", true);
    assertNotNull(myFixture.findFileInTempDir("res/layout/newlayout.xml"));
  }

  public void testRenameComponent() throws Throwable {
    doRenameComponentTest("MyActivity1");
  }

  public void testRenameComponent2() throws Throwable {
    myFixture.copyFileToProject(BASE_PATH + "ChildActivity.java", "src/p1/p2/ChildActivity.java");
    doRenameComponentTest("MyActivity1");
  }

  @Override
  protected void configureAdditionalModules(@NotNull TestFixtureBuilder<IdeaProjectTestFixture> projectBuilder,
                                            @NotNull List<MyAdditionalModuleData> modules) {
    if ("testRenamePackageFromTestModule".equals(getName())) {
      addModuleWithAndroidFacet(projectBuilder, modules, "module1", false);
    }
  }

  public void testRenamePackage() throws Throwable {
    doRenameComponentTest("p10");
  }

  public void testRenamePackage1() throws Throwable {
    doRenameComponentTest("p20");
  }

  public void testRenamePackage2() throws Throwable {
    doRenameComponentTest("p1.p2");
  }

  public void testRenamePackage3() throws Throwable {
    doRenameComponentTest("p1");
  }

  public void testRenamePackageFromTestModule() throws Throwable {
    doRenameComponentTest("p1.p3");
    myFixture.checkResultByFile("additionalModules/module1/AndroidManifest.xml", BASE_PATH + getTestName(false) + "_module1_after.xml",
                                true);
  }

  public void testMovePackage() throws Throwable {
    myFixture.copyDirectoryToProject(BASE_PATH + "empty", "src/p1/p2/p3");
    myFixture.copyFileToProject(BASE_PATH + "MyActivity1.java", "src/p1/p2/p3/MyActivity.java");
    doMovePackageTest("p1.p2.p3", "p1");
  }

  public void testMovePackage1() throws Throwable {
    myFixture.copyDirectoryToProject(BASE_PATH + "empty", "src/p1/p2/p3");
    myFixture.copyFileToProject(BASE_PATH + "MyActivity1.java", "src/p1/p2/p3/MyActivity.java");
    doMovePackageTest("p1.p2.p3", "p1");
  }

  public void testMovePackage2() throws Throwable {
    myFixture.copyDirectoryToProject(BASE_PATH + "empty", "src/p1/p2/p3");
    myFixture.copyDirectoryToProject(BASE_PATH + "empty", "src/p1/p3");
    myFixture.copyFileToProject(BASE_PATH + "MyActivity2.java", "src/p1/p3/MyActivity.java");
    doMovePackageTest("p1.p3", "p1.p2");
  }

  public void testMovePackage3() throws Throwable {
    myFixture.copyDirectoryToProject(BASE_PATH + "empty", "src/p1/p2/p3");
    myFixture.copyFileToProject(BASE_PATH + "MyActivity1.java", "src/p1/p2/p3/MyActivity.java");
    doMovePackageTest("p1.p2.p3", "p1");
  }

  public void testMovePackage4() throws Throwable {
    myFixture.copyDirectoryToProject(BASE_PATH + "empty", "src/p1/p2/p3");
    myFixture.copyFileToProject(BASE_PATH + "MyActivity1.java", "src/p1/p2/p3/MyActivity.java");
    doMovePackageTest("p1.p2.p3", "p1");
  }

  public void testMovePackage5() throws Throwable {
    myFixture.copyDirectoryToProject(BASE_PATH + "empty", "src/p1/p2/p3");
    myFixture.copyFileToProject(BASE_PATH + "MyActivity1.java", "src/p1/p2/p3/MyActivity.java");
    doMovePackageTest("p1.p2.p3", "p1");
  }

  public void testMovePackage6() throws Throwable {
    myFixture.copyDirectoryToProject(BASE_PATH + "empty", "src/p1/p2/p4/p3");
    myFixture.copyFileToProject(BASE_PATH + "MyActivity3.java", "src/p1/p2/p4/p3/MyActivity.java");
    doMovePackageTest("p1.p2.p4", "p1");
  }

  public void testMovePackage7() throws Throwable {
    myFixture.copyDirectoryToProject(BASE_PATH + "empty", "src/p1/p2");
    myFixture.copyDirectoryToProject(BASE_PATH + "empty", "src/p33");
    myFixture.copyFileToProject(BASE_PATH + "MyActivity.java", "src/p1/p2/MyActivity.java");
    doMovePackageTest("p1.p2", "p33");
  }

  public void testMoveClass1() throws Throwable {
    myFixture.copyDirectoryToProject(BASE_PATH + "empty", "src/p1/p2");
    myFixture.copyDirectoryToProject(BASE_PATH + "empty", "src/p3");
    myFixture.copyFileToProject(BASE_PATH + "MyActivity.java", "src/p1/p2/MyActivity.java");
    doMoveClass("p1.p2.MyActivity", "p3");
  }

  public void testMoveClass2() throws Throwable {
    myFixture.copyDirectoryToProject(BASE_PATH + "empty", "src/p1/p2");
    myFixture.copyDirectoryToProject(BASE_PATH + "empty", "src/p1/p3");
    myFixture.copyFileToProject(BASE_PATH + "MyActivity.java", "src/p1/p2/MyActivity.java");
    doMoveClass("p1.p2.MyActivity", "p1.p3");
  }

  public void testRenameJavaPackage1() throws Throwable {
    myFixture.copyDirectoryToProject(BASE_PATH + "empty", "src/p1/p2");
    myFixture.copyFileToProject(BASE_PATH + "MyActivity.java", "src/p1/p2/MyActivity.java");
    VirtualFile manifestFile = myFixture.copyFileToProject(BASE_PATH + getTestName(false) + ".xml", SdkConstants.FN_ANDROID_MANIFEST_XML);
    myFixture.configureFromExistingVirtualFile(manifestFile);
    doRenameJavaPackage("p1.p2", "p3");
    myFixture.checkResultByFile(BASE_PATH + getTestName(false) + "_after.xml");
  }

  public void testRenameJavaPackage2() throws Throwable {
    myFixture.copyDirectoryToProject(BASE_PATH + "empty", "src/p1/p2");
    myFixture.copyFileToProject(BASE_PATH + "MyActivity.java", "src/p1/p2/MyActivity.java");
    VirtualFile manifestFile = myFixture.copyFileToProject(BASE_PATH + getTestName(false) + ".xml", SdkConstants.FN_ANDROID_MANIFEST_XML);
    myFixture.configureFromExistingVirtualFile(manifestFile);
    doRenameJavaPackage("p1.p2", "p3");
    myFixture.checkResultByFile(BASE_PATH + getTestName(false) + "_after.xml");
  }

  public void testRenameJavaPackage3() throws Throwable {
    myFixture.copyDirectoryToProject(BASE_PATH + "empty", "src/p1/p2");
    myFixture.copyFileToProject(BASE_PATH + "MyActivity.java", "src/p1/p2/MyActivity.java");
    VirtualFile manifestFile = myFixture.copyFileToProject(BASE_PATH + getTestName(false) + ".xml", SdkConstants.FN_ANDROID_MANIFEST_XML);
    myFixture.configureFromExistingVirtualFile(manifestFile);
    doRenameJavaPackage("p1", "p3");
    myFixture.checkResultByFile(BASE_PATH + getTestName(false) + "_after.xml");
  }

  public void testRenameJavaPackage4() throws Throwable {
    myFixture.copyDirectoryToProject(BASE_PATH + "empty", "src/p1/p2");
    myFixture.copyFileToProject(BASE_PATH + "MyActivity.java", "src/p1/p2/MyActivity.java");
    VirtualFile manifestFile = myFixture.copyFileToProject(BASE_PATH + getTestName(false) + ".xml", SdkConstants.FN_ANDROID_MANIFEST_XML);
    myFixture.configureFromExistingVirtualFile(manifestFile);
    doRenameJavaPackage("p1.p2", "p3");
    myFixture.checkResultByFile(BASE_PATH + getTestName(false) + "_after.xml");
  }

  public void testRenameJavaPackage5() throws Throwable {
    myFixture.copyDirectoryToProject(BASE_PATH + "empty", "src/p1/p2");
    myFixture.copyFileToProject(BASE_PATH + "MyActivity.java", "src/p1/p2/MyActivity.java");
    VirtualFile manifestFile = myFixture.copyFileToProject(BASE_PATH + getTestName(false) + ".xml", SdkConstants.FN_ANDROID_MANIFEST_XML);
    myFixture.configureFromExistingVirtualFile(manifestFile);
    doRenameJavaPackage("p1", "p3");
    myFixture.checkResultByFile(BASE_PATH + getTestName(false) + "_after.xml");
  }

  public void testRenameWidget() throws Throwable {
    createManifest();
    myFixture.copyFileToProject(BASE_PATH + "MyWidget.java", "src/p1/p2/MyWidget.java");
    VirtualFile file = myFixture.copyFileToProject(BASE_PATH + "layout_widget.xml", "res/layout/layout_widget.xml");
    myFixture.configureFromExistingVirtualFile(file);
    checkAndRename("MyWidget1");
    myFixture.checkResultByFile(BASE_PATH + "layout_widget_after.xml");
  }

  public void testRenameWidget1() throws Throwable {
    createManifest();
    myFixture.copyFileToProject(BASE_PATH + "MyWidget.java", "src/p1/p2/MyWidget.java");
    VirtualFile file = myFixture.copyFileToProject(BASE_PATH + "layout_widget.xml", "res/layout/layout_widget.xml");
    myFixture.configureFromExistingVirtualFile(file);
    checkAndRename("MyWidget1");
    myFixture.checkResultByFile(BASE_PATH + "layout_widget_after.xml");
  }

  public void testRenameWidgetPackage1() throws Throwable {
    createManifest();
    myFixture.copyFileToProject(BASE_PATH + "MyWidget.java", "src/p1/p2/MyWidget.java");
    VirtualFile file = myFixture.copyFileToProject(BASE_PATH + "layout_widget1.xml", "res/layout/layout_widget1.xml");
    myFixture.configureFromExistingVirtualFile(file);
    checkAndRename("newPackage");
    myFixture.checkResultByFile(BASE_PATH + "layout_widget1_after.xml");
  }

  public void testMoveWidgetPackage1() throws Throwable {
    createManifest();
    myFixture.copyFileToProject(BASE_PATH + "Dummy.java", "src/p1/newp/Dummy.java");
    myFixture.copyFileToProject(BASE_PATH + "MyWidget.java", "src/p1/p2/MyWidget.java");
    myFixture.copyFileToProject(BASE_PATH + "MyPreference.java", "src/p1/p2/MyPreference.java");
    final VirtualFile f = myFixture.copyFileToProject(BASE_PATH + "layout_widget2.xml", "res/layout/layout_widget2.xml");
    myFixture.configureFromExistingVirtualFile(f);
    myFixture.copyFileToProject(BASE_PATH + "custom_pref.xml", "res/xml/custom_pref.xml");
    doMovePackage("p1.p2", "p1.newp");
    myFixture.checkResultByFile("res/layout/layout_widget2.xml", BASE_PATH + "layout_widget2_after.xml", false);
    myFixture.checkResultByFile("res/xml/custom_pref.xml", BASE_PATH + "custom_pref_after.xml", false);
  }

  public void testRenameInlineIdDeclarations() throws Throwable {
    createManifest();
    VirtualFile file = myFixture.copyFileToProject(BASE_PATH + "layout15.xml", "res/layout/layout15.xml");
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.copyFileToProject("R.java", R_JAVA_PATH);
    checkAndRename("newname");
    myFixture.checkResultByFile(BASE_PATH + "layout15_after.xml");
  }

  // Regression test for http://b.android.com/185634
  public void testThemeReferenceRename() throws Throwable {
    myFixture.copyFileToProject(BASE_PATH + "AndroidManifest_theme_before.xml", "AndroidManifest.xml");
    final VirtualFile file = myFixture.copyFileToProject(BASE_PATH + "themes.xml", "res/values/themes.xml");
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.renameElementAtCaretUsingHandler("newTheme");
    myFixture.checkResultByFile(BASE_PATH + "themes_after.xml");
    myFixture.checkResultByFile("AndroidManifest.xml", BASE_PATH + "AndroidManifest_theme_after.xml", true);
  }

  private void doMovePackageTest(String packageName, String newPackageName) throws Exception {
    VirtualFile manifestFile = myFixture.copyFileToProject(BASE_PATH + getTestName(false) + ".xml", SdkConstants.FN_ANDROID_MANIFEST_XML);
    myFixture.configureFromExistingVirtualFile(manifestFile);
    doMovePackage(packageName, newPackageName);
    myFixture.checkResultByFile(BASE_PATH + getTestName(false) + "_after.xml");
  }

  private void doRenameComponentTest(String newName) {
    myFixture.copyFileToProject(BASE_PATH + "MyActivity.java", "src/p1/p2/MyActivity.java");
    VirtualFile manifestFile = myFixture.copyFileToProject(BASE_PATH + getTestName(false) + ".xml", SdkConstants.FN_ANDROID_MANIFEST_XML);
    myFixture.configureFromExistingVirtualFile(manifestFile);
    checkAndRename(newName);
    myFixture.checkResultByFile(BASE_PATH + getTestName(false) + "_after.xml");
  }

  private void doMovePackage(String packageName, String newPackageName) throws Exception {
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(getProject());
    final PsiPackage aPackage = facade.findPackage(packageName);
    final PsiPackage newParentPackage = facade.findPackage(newPackageName);

    assertNotNull(newParentPackage);
    final PsiDirectory[] dirs = newParentPackage.getDirectories();
    assertEquals(dirs.length, 1);

    new MoveClassesOrPackagesProcessor(getProject(), new PsiElement[]{aPackage},
                                       new SingleSourceRootMoveDestination(PackageWrapper.create(newParentPackage), dirs[0]),
                                       true, false, null).run();
    FileDocumentManager.getInstance().saveAllDocuments();
  }

  private void doMoveClass(String className, String newParentPackageName) throws Exception {
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(getProject());
    final PsiClass psiClass = facade.findClass(className, GlobalSearchScope.projectScope(getProject()));
    final PsiPackage newParentPackage = facade.findPackage(newParentPackageName);

    assertNotNull(newParentPackage);
    final PsiDirectory[] dirs = newParentPackage.getDirectories();
    assertEquals(dirs.length, 1);

    new MoveClassesOrPackagesProcessor(getProject(), new PsiElement[]{psiClass},
                                       new SingleSourceRootMoveDestination(PackageWrapper.create(newParentPackage), dirs[0]),
                                       true, false, null).run();
    FileDocumentManager.getInstance().saveAllDocuments();
  }

  private void doRenameJavaPackage(String packageName, String newPackageName) throws Exception {
    final PsiPackage aPackage = JavaPsiFacade.getInstance(getProject()).findPackage(packageName);
    assertNotNull("Package " + packageName + " not found", aPackage);
    new RenameProcessor(getProject(), aPackage, newPackageName, true, true).run();
    FileDocumentManager.getInstance().saveAllDocuments();
  }

  private void checkAndRename(String newName) {
    final RenameElementAction action = new RenameElementAction();
    final AnActionEvent e = new TestActionEvent(DataManager.getInstance().getDataContext(myFixture.getEditor().getComponent()), action);
    action.update(e);
    assertTrue(e.getPresentation().isEnabled() && e.getPresentation().isVisible());
    // Note: This fails when trying to rename XML attribute values: Use myFixture.renameElementAtCaretUsingHandler() instead!
    myFixture.renameElementAtCaret(newName);
  }
}
