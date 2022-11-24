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

import static com.android.AndroidProjectTypes.PROJECT_TYPE_APP;
import static com.google.common.truth.Truth.assertThat;

import com.android.SdkConstants;
import com.android.tools.idea.res.psi.ResourceReferencePsiElement;
import com.android.tools.idea.testing.AndroidTestUtils;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.impl.JavaPsiFacadeEx;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.PackageWrapper;
import com.intellij.refactoring.actions.RenameElementAction;
import com.intellij.refactoring.move.moveClassesOrPackages.MoveClassesOrPackagesProcessor;
import com.intellij.refactoring.move.moveClassesOrPackages.SingleSourceRootMoveDestination;
import com.intellij.refactoring.move.moveFilesOrDirectories.JavaMoveFilesOrDirectoriesHandler;
import com.intellij.refactoring.rename.RenameHandler;
import com.intellij.refactoring.rename.RenameProcessor;
import com.intellij.refactoring.rename.RenamePsiElementProcessor;
import com.intellij.testFramework.MapDataContext;
import com.intellij.testFramework.TestActionEvent;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import com.intellij.util.containers.ContainerUtil;
import java.io.IOException;
import java.util.List;
import org.jetbrains.annotations.NotNull;

public class AndroidResourceRenameTest extends AndroidTestCase {
  private static final String BASE_PATH = "/rename/";

  // Regression test for http://b/153850296
  public void testOneHandlerAvailableForXmlTag() {
    VirtualFile file = myFixture.copyFileToProject(BASE_PATH + "strings3.xml", "res/values/strings.xml");
    myFixture.configureFromExistingVirtualFile(file);
    assertThat(ContainerUtil
                 .filter(RenameHandler.EP_NAME.getExtensionList(), it -> it.isRenaming(createDataContext()))).hasSize(1);
  }

  private MapDataContext createDataContext() {
    MapDataContext context = new MapDataContext();
    context.put(CommonDataKeys.EDITOR, myFixture.getEditor());
    context.put(CommonDataKeys.PSI_FILE, myFixture.getFile());
    context.put(CommonDataKeys.PSI_ELEMENT, TargetElementUtil.findTargetElement(myFixture.getEditor(),
                                                                                TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED
                                                                                | TargetElementUtil.ELEMENT_NAME_ACCEPTED));
    context.put(CommonDataKeys.CARET, myFixture.getEditor().getCaretModel().getCurrentCaret());
    return context;
  }

  /**
   * Regression test for http://b.android.com/170867656
   * Bug: when res/values file was renamed, the file itself was considered a resource.
   */
  public void testStringsXmlFile() {
    VirtualFile stringsFile = myFixture.copyFileToProject(BASE_PATH + "strings.xml", "res/values/strings.xml");
    myFixture.configureFromExistingVirtualFile(stringsFile);
    myFixture.renameElement(myFixture.getFile(), "otherfile.xml");
    assertEquals("otherfile.xml", stringsFile.getName());
  }

  public void testConstraintReferencedIds() throws Throwable {
    createManifest();
    myFixture.addClass(
      "package androidx.constraintlayout.widget;\n" +
      "public class ConstraintLayout extends android.view.ViewGroup {}");
    myFixture.addClass(
      "package androidx.constraintlayout.widget;\n" +
      "public class Barrier extends androidx.constraintlayout.widget.ConstraintLayout {}");
    myFixture.addFileToProject(
      "res/values/values.xml",
      "<resources>\n" +
      " <declare-styleable name=\"ConstraintLayout_Layout\">\n" +
      "  <attr name=\"constraint_referenced_ids\" format=\"string\" />\n" +
      " </declare-styleable>\n" +
      "</resources>");
    VirtualFile file =
      myFixture.copyFileToProject(BASE_PATH + "layout_constraint_referenced_ids.xml", "res/layout/layout_constraint_referenced_ids.xml");
    myFixture.configureFromExistingVirtualFile(file);
    checkAndRename("anchor1");
    myFixture.checkResultByFile(BASE_PATH + "layout_constraint_referenced_ids_after.xml");
  }

  /**
   * Test related to renaming files differ between the old pipeline and the new one. The new pipeline does not require the user to add
   * the file extension when renaming a file resource from the file element.
   */
  public void testRenameFileWithMultipleExtension() {
    VirtualFile file = myFixture.copyFileToProject(BASE_PATH + "layoutwithicon.xml", "res/layout/layoutwithicon.xml");
    VirtualFile iconXml = myFixture.copyFileToProject(BASE_PATH + "icon.xml", "res/drawable-anydpi/icon.xml");
    VirtualFile iconPng = myFixture.copyFileToProject(BASE_PATH + "icon.png", "res/drawable-mdpi/icon.png");
    VirtualFile iconInValue = myFixture.copyFileToProject(BASE_PATH + "icon.xml", "res/value/icon.xml");
    myFixture.configureFromExistingVirtualFile(file);
    checkAndRename("icon_with_new_name");
    myFixture.checkResult("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                          "\n" +
                          "<RelativeLayout xmlns:android=\"http://schemas.android.com/apk/res/android\">\n" +
                          "  <EditText android:id=\"@+id/anchor\"/>\n" +
                          "  <ImageView android:src=\"@drawable/icon_with_new_name\"/>\n" +
                          "</RelativeLayout>\n");
    assertEquals("icon_with_new_name.xml", iconXml.getName());
    assertEquals("icon_with_new_name.png", iconPng.getName());
    assertEquals("icon.xml", iconInValue.getName());
  }

  public void testRenameFileWithInvalidResourceName() {
    VirtualFile drawableFile = myFixture.copyFileToProject(BASE_PATH + "icon.xml", "res/drawable/icon space.xml");
    myFixture.configureFromExistingVirtualFile(drawableFile);
    myFixture.renameElement(myFixture.getFile(), "icon_with_new_name.xml");
    assertEquals("icon_with_new_name.xml", drawableFile.getName());
    assertThat(ResourceReferencePsiElement.create(myFixture.getFile())).isNotNull();
  }

  public void testXmlReferenceToFileResource() throws Throwable {
    createManifest();
    VirtualFile file = myFixture.copyFileToProject(BASE_PATH + "layout1.xml", "res/layout/layout1.xml");
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.copyFileToProject(BASE_PATH + "pic.png", "res/drawable/pic.png");
    myFixture.copyFileToProject(BASE_PATH + "styles.xml", "res/values/styles.xml");
    myFixture.copyFileToProject(BASE_PATH + "RefR2.java", "src/p1/p2/RefR2.java");
    checkAndRename("pic1");
    myFixture.checkResultByFile(BASE_PATH + "layout_file_after.xml");
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
    myFixture.copyFileToProject(BASE_PATH + "RefR2.java", "src/p1/p2/RefR2.java");
    checkAndRename("pic1");
    myFixture.checkResultByFile(BASE_PATH + "layout_file_after.xml");
    myFixture.checkResultByFile("res/values/styles.xml", BASE_PATH + "styles_after.xml", true);
    myFixture.checkResultByFile("src/p1/p2/RefR2.java", BASE_PATH + "RefR2_after.java", true);
    assertNotNull(myFixture.findFileInTempDir("res/drawable/pic1.9.png"));
  }

  /**
   * Regression test for http://b.android.com/174014
   * Bug: when mipmap resource was renamed, new reference contained ".png" file extension.
   */
  public void testXmlReferenceToFileResource2() {
    myFixture.copyFileToProject(BASE_PATH + "pic.png", "res/mipmap/pic.png");
    myFixture.configureFromExistingVirtualFile(
      myFixture.copyFileToProject(BASE_PATH + "AndroidManifest_mipmap_before.xml", "AndroidManifest.xml"));
    checkAndRename("app_icon");
    myFixture.checkResultByFile("AndroidManifest.xml", BASE_PATH + "AndroidManifest_mipmap_after.xml", true);
    assertNotNull(myFixture.findFileInTempDir("res/mipmap/app_icon.png"));
  }

  /** Regression test for raw resources renaming, http://b.android.com/183128 */
  public void testXmlReferenceToFileResource3() {
    myFixture.copyFileToProject(BASE_PATH + "styles.xml", "res/raw/raw_resource.txt");
    myFixture
      .configureFromExistingVirtualFile(myFixture.copyFileToProject(BASE_PATH + "AndroidManifest_raw_before.xml", "AndroidManifest.xml"));
    checkAndRename("new_raw_resource");
    myFixture.checkResultByFile("AndroidManifest.xml", BASE_PATH + "AndroidManifest_raw_after.xml", true);
    assertNotNull(myFixture.findFileInTempDir("res/raw/new_raw_resource.txt"));
  }

  /** Regression test for transition resources renaming, http://b.android.com/183128 */
  public void testXmlReferenceToFileResource4() {
    myFixture.copyFileToProject(BASE_PATH + "transition.xml", "res/transition/great.xml");
    myFixture.configureFromExistingVirtualFile(myFixture.copyFileToProject(BASE_PATH + "styles12.xml", "res/values/styles.xml"));
    checkAndRename("good");
    myFixture.checkResultByFile("res/values/styles.xml", BASE_PATH + "styles12_after.xml", true);
    assertNotNull(myFixture.findFileInTempDir("res/transition/good.xml"));
  }

  @Override
  protected boolean providesCustomManifest() {
    return true;
  }

  /** Due to http://b/153850296 we are currently not supporting renaming resources from the Xml Tag token. **/
  public void ignore_testStyleInheritance() throws Throwable {
    doTestStyleInheritance("styles1.xml", "styles1_after.xml");
  }

  public void testRenameDeclareStyleableAttrFromXml() throws Throwable {
    createManifest();
    myFixture.copyFileToProject(BASE_PATH + "MyView5.java", "src/p1/p2/MyView.java");
    final VirtualFile file = myFixture.copyFileToProject(BASE_PATH + "attrs14.xml", "res/values/attrs14.xml");
    myFixture.configureFromExistingVirtualFile(file);
    doRename("newname");
    myFixture.checkResultByFile(BASE_PATH + "attrs14_after.xml", true);
    myFixture.checkResultByFile("src/p1/p2/MyView.java", BASE_PATH + "MyView5_after.java", true);
  }

  public void testRenameDeclareStyleableFromXml() throws Throwable {
    // Like testRenameDeclareStyleableFromJava, but the rename request originates from
    // the XML declare-styleable reference rather than a Java field reference.
    createManifest();
    myFixture.copyFileToProject(BASE_PATH + "MyView4.java", "src/p1/p2/MyView.java");
    final VirtualFile file = myFixture.copyFileToProject(BASE_PATH + "attrs13.xml", "res/values/attrs13.xml");
    myFixture.configureFromExistingVirtualFile(file);
    doRename("NewName");
    myFixture.checkResultByFile(BASE_PATH + "attrs13_after.xml", true);
    myFixture.checkResultByFile("src/p1/p2/MyView.java", BASE_PATH + "MyView4_after.java", true);
  }


  public void testRenameDeclareStyleableFromXmlWithNamespaces() throws Throwable {
    // Like testRenameDeclareStyleableFromJava, but the rename request originates from
    // the XML declare-styleable reference rather than a Java field reference.
    createManifest();
    enableNamespacing("p1.p2");
    myFixture.copyFileToProject(BASE_PATH + "MyView4.java", "src/p1/p2/MyView.java");
    final VirtualFile file = myFixture.copyFileToProject(BASE_PATH + "attrs13.xml", "res/values/attrs13.xml");
    myFixture.configureFromExistingVirtualFile(file);
    doRename("NewName");
    myFixture.checkResultByFile(BASE_PATH + "attrs13_after.xml", true);
    myFixture.checkResultByFile("src/p1/p2/MyView.java", BASE_PATH + "MyView4_after.java", true);
  }

  public void testRenameDeclareStyleableFromJava() throws Throwable {
    // Renaming an R styleable field should update the declare styleable declaration, as well as
    // any field references, including those for the attributes
    createManifest();
    myFixture.copyFileToProject(BASE_PATH + "attrs10.xml", "res/values/attrs10.xml");

    final VirtualFile file = myFixture.copyFileToProject(BASE_PATH + "MyView1.java", "src/p1/p2/MyView.java");
    myFixture.configureFromExistingVirtualFile(file);
    checkAndRename("NewName");
    myFixture.checkResultByFile(BASE_PATH + "MyView1_after.java", true);
    myFixture.checkResultByFile("res/values/attrs10.xml", BASE_PATH + "attrs10_after.xml", true);
  }

  public void testRenameColorFromJava() throws IOException {
    createManifest();
    myFixture.addFileToProject(
      "res/values/colors.xml",
      //language=xml
      "<resources><color name=\"colorPrimary\">#008577</color></resources>");
    VirtualFile activityFile = myFixture.addFileToProject(
      "src/p1/p2/Foo.java",
      //language=java
      "package p1.p2;\n" +
      "public class Foo {\n" +
      " public static void example() {\n" +
      "   int colorPrimary = R.color.color<caret>Primary;\n" +
      "   int black = android.R.color.black;\n" +
      " }\n" +
      "}").getVirtualFile();
    myFixture.configureFromExistingVirtualFile(activityFile);
    checkAndRename("newColor");
    myFixture.checkResult(
      //language=java
      "package p1.p2;\n" +
      "public class Foo {\n" +
      " public static void example() {\n" +
      "   int colorPrimary = R.color.newColor;\n" +
      "   int black = android.R.color.black;\n" +
      " }\n" +
      "}", true);
    myFixture.checkResult(
      "res/values/colors.xml",
      //language=xml
      "<resources><color name=\"newColor\">#008577</color></resources>", true);
  }

  public void testRenameColorFromKotlin() throws IOException {
    createManifest();
    myFixture.addFileToProject("res/values/colors.xml", "<resources><color name=\"colorPrimary\">#008577</color></resources>");
    VirtualFile activityFile = myFixture.addFileToProject(
      "src/p1/p2/Activity1.kt",
      //language=kotlin
      "package p1.p2\n" +
      "class Activity1 : android.app.Activity() {\n" +
      "    override fun onCreate(savedInstanceState: Bundle?) {\n" +
      "        val color = R.color.colorPrimary<caret>\n" +
      "    }\n" +
      "}").getVirtualFile();
    myFixture.configureFromExistingVirtualFile(activityFile);
    checkAndRename("newColor");
    myFixture.checkResult(
      //language=kotlin
      "package p1.p2\n" +
      "class Activity1 : android.app.Activity() {\n" +
      "    override fun onCreate(savedInstanceState: Bundle?) {\n" +
      "        val color = R.color.newColor\n" +
      "    }\n" +
      "}", true);
    myFixture.checkResult(
      "res/values/colors.xml",
      //language=xml
      "<resources><color name=\"newColor\">#008577</color></resources>", true);
  }

  public void testJavaReferenceToFileResource() throws Throwable {
    createManifest();
    VirtualFile file = myFixture.copyFileToProject(BASE_PATH + "RefR3.java", "src/p1/p2/RefR3.java");
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.copyFileToProject(BASE_PATH + "layout3.xml", "res/layout/layout3.xml");
    myFixture.copyFileToProject(BASE_PATH + "pic.png", "res/drawable/pic.png");
    checkAndRename("pic1");
    myFixture.checkResultByFile(BASE_PATH + "RefR3_after.java", true);
    myFixture.checkResultByFile("res/layout/layout3.xml", BASE_PATH + "layout_file_after.xml", true);
    assertNotNull(myFixture.findFileInTempDir("res/drawable/pic1.png"));
  }

  /** Regression test for http://b.android.com/205527 */
  public void testRenameLocalisedResourceFromUsage() throws Throwable {
    myFixture.copyFileToProject(BASE_PATH + "dimens.xml", "res/values/dimens.xml");
    myFixture.copyFileToProject(BASE_PATH + "dimens.xml", "res/values-en/dimens.xml");
    VirtualFile file = myFixture.copyFileToProject(BASE_PATH + "layout16.xml", "res/layout/layout16.xml");
    myFixture.configureFromExistingVirtualFile(file);
    checkAndRename("localised_newname_value");
    myFixture.checkResultByFile(BASE_PATH + "layout16_after.xml");
    myFixture.checkResultByFile("res/values/dimens.xml", BASE_PATH + "dimens_after.xml", true);
    myFixture.checkResultByFile("res/values-en/dimens.xml", BASE_PATH + "dimens_after.xml", true);
  }

  /** Regression test for http://b.android.com/135180 */
  public void testJavaReferenceToFileResourceWithUnderscores() throws Throwable {
    createManifest();
    VirtualFile file = myFixture.copyFileToProject(BASE_PATH + "RefR12.java", "src/p1/p2/RefR.java");
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.copyFileToProject(BASE_PATH + "pic.png", "res/drawable/pic.png");
    checkAndRename("my_pic");
    myFixture.checkResultByFile(BASE_PATH + "RefR12_after.java");
    assertNotNull(myFixture.findFileInTempDir("res/drawable/my_pic.png"));
  }

  public void testJavaReferenceToId() throws Throwable {
    createManifest();
    VirtualFile file = myFixture.copyFileToProject(BASE_PATH + "RefR7.java", "src/p1/p2/RefR7.java");
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.copyFileToProject(BASE_PATH + "layout7.xml", "res/layout/layout7.xml");
    checkAndRename("anchor1");
    myFixture.checkResultByFile(BASE_PATH + "RefR7_after.java", true);
    myFixture.checkResultByFile("res/layout/layout7.xml", BASE_PATH + "layout_id_after.xml", true);
  }

  public void testJavaReferenceToId1() throws Throwable {
    createManifest();
    VirtualFile file = myFixture.copyFileToProject(BASE_PATH + "RefR7.java", "src/p1/p2/RefR7.java");
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.copyFileToProject(BASE_PATH + "layout7.xml", "res/layout/l1.xml");
    myFixture.copyFileToProject(BASE_PATH + "layout7.xml", "res/layout/l2.xml");
    checkAndRename("anchor1");
    myFixture.checkResultByFile(BASE_PATH + "RefR7_after.java", true);
    myFixture.checkResultByFile("res/layout/l1.xml", BASE_PATH + "layout_id_after.xml", true);
    myFixture.checkResultByFile("res/layout/l2.xml", BASE_PATH + "layout_id_after.xml", true);
  }

  public void testXmlReferenceToId() throws Throwable {
    createManifest();
    VirtualFile file = myFixture.copyFileToProject(BASE_PATH + "layout5.xml", "res/layout/layout5.xml");
    myFixture.configureFromExistingVirtualFile(file);
    checkAndRename("@+id/anchor1");
    myFixture.checkResultByFile(BASE_PATH + "layout_id_after.xml");
  }

  public void testMoveApplicationClass() throws Throwable {
    deleteManifest();
    myFixture.copyFileToProject(BASE_PATH + "MyApplication.java", "src/p1/p2/MyApplication.java");
    VirtualFile f = myFixture.copyFileToProject(BASE_PATH + getTestName(true) + ".xml", "AndroidManifest.xml");
    myFixture.configureFromExistingVirtualFile(f);
    moveClass("p1.p2.MyApplication", "p1");
    myFixture.checkResultByFile(BASE_PATH + getTestName(true) + "_after.xml");
  }

  public void testAndroidManifestRenameClass1() throws Throwable {
    doTestAndroidManifestRenameClass("AndroidManifest_rename_class1.xml", "AndroidManifest_rename_class1-2_after.xml");
  }

  public void testAndroidManifestRenameClass2() throws Throwable {
    doTestAndroidManifestRenameClass("AndroidManifest_rename_class2.xml", "AndroidManifest_rename_class1-2_after.xml");
  }

  public void testAndroidManifestRenameClass3() throws Throwable {
    doTestAndroidManifestRenameClass("AndroidManifest_rename_class3.xml", "AndroidManifest_rename_class3_after.xml");
  }

  /**
   * Copy {@code filePath} to project as AndroidManifest.xml, rename a class in a Java file and check if class is renamed correctly
   * by comparing the result with {@code expectedFile}. Also check if class is actually renamed in the Java file.
   */
  public void doTestAndroidManifestRenameClass(final String filePath, final String expectedFile) throws Throwable {
    deleteManifest();
    VirtualFile f = myFixture.copyFileToProject(BASE_PATH + "MyClass.java", "src/p1/p2/MyClass.java");
    myFixture.copyFileToProject(BASE_PATH + filePath, "AndroidManifest.xml");
    myFixture.configureFromExistingVirtualFile(f);
    checkAndRename("MyClass2");
    myFixture.checkResultByFile(BASE_PATH + "MyClass2.java", true);
    myFixture.checkResultByFile("AndroidManifest.xml", BASE_PATH + expectedFile, true);
  }

  protected void renameElementWithTextOccurrences(final String newName) throws Throwable {
    Editor editor = myFixture.getEditor();
    PsiFile file = myFixture.getFile();
    Editor completionEditor = InjectedLanguageUtil.getEditorForInjectedLanguageNoCommit(editor, file);
    PsiElement element = TargetElementUtil.findTargetElement(completionEditor, TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED |
                                                                               TargetElementUtil.ELEMENT_NAME_ACCEPTED);
    assert element != null;
    final PsiElement substitution = RenamePsiElementProcessor.forElement(element).substituteElementToRename(element, editor);
    new RenameProcessor(myFixture.getProject(), substitution, newName, false, true).run();
  }

  /**
   * This will do a refactor and update all code AND non-code (such as text/comments) references.
   *
   * @see #moveClassNoTextReferences(String, String)
   */
  private void moveClass(final String className, final String newPackageName) throws Throwable {
    PsiClass aClass = JavaPsiFacade.getInstance(getProject()).findClass(className, GlobalSearchScope.projectScope(getProject()));
    PsiPackage aPackage = JavaPsiFacade.getInstance(getProject()).findPackage(newPackageName);

    assertNotNull(aPackage);
    final PsiDirectory[] dirs = aPackage.getDirectories();
    assertEquals(dirs.length, 1);

    new MoveClassesOrPackagesProcessor(getProject(), new PsiElement[]{aClass}, new SingleSourceRootMoveDestination(
      PackageWrapper.create(JavaDirectoryService.getInstance().getPackage(dirs[0])), dirs[0]), true, true, null).run();
  }

  /**
   * Where as {@link #moveClass(String, String)} will move a class and update all references, including text references
   * This method will ONLY update code references when moving a class
   *
   * @see #moveClass(String, String)
   */
  private void moveClassNoTextReferences(String className, String newPackageName) throws Exception {
    JavaPsiFacadeEx myJavaFacade = JavaPsiFacadeEx.getInstanceEx(getProject());
    final PsiElement element = myJavaFacade.findClass(className, GlobalSearchScope.projectScope(getProject()));
    assertNotNull("Class " + className + " not found", element);

    PsiManagerImpl myPsiManager = (PsiManagerImpl)PsiManager.getInstance(getProject());
    PsiPackage aPackage = JavaPsiFacade.getInstance(myPsiManager.getProject()).findPackage(newPackageName);
    assertNotNull("Package " + newPackageName + " not found", aPackage);
    final PsiDirectory[] dirs = aPackage.getDirectories();
    assertEquals(1, dirs.length);

    final JavaMoveFilesOrDirectoriesHandler handler = new JavaMoveFilesOrDirectoriesHandler();
    PsiElement[] elements = new PsiElement[]{element};
    assertTrue(handler.canMove(elements, dirs[0]));
    handler.doMove(getProject(), elements, dirs[0], null);
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    FileDocumentManager.getInstance().saveAllDocuments();
  }

  public void testXmlReferenceToValueResource() throws Throwable {
    createManifest();
    VirtualFile file = myFixture.copyFileToProject(BASE_PATH + "layout2.xml", "res/layout/layout2.xml");
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.copyFileToProject(BASE_PATH + "strings.xml", "res/values/strings.xml");
    myFixture.copyFileToProject(BASE_PATH + "RefR1.java", "src/p1/p2/RefR1.java");
    checkAndRename("str1");
    myFixture.checkResultByFile(BASE_PATH + "layout_value_after.xml");
    myFixture.checkResultByFile("src/p1/p2/RefR1.java", BASE_PATH + "RefR1_after.java", true);
    myFixture.checkResultByFile("res/values/strings.xml", BASE_PATH + "strings_after.xml", true);
  }

  public void testValueResource1() throws Throwable {
    doTestStringRename("strings1.xml");
  }

  public void testValueResource2() throws Throwable {
    doTestStringRename("strings2.xml");
  }

  /** Due to http://b/153850296 we are currently not supporting renaming resources from the Xml Tag token. **/
  public void ignore_testValueResource3() throws Throwable {
    doTestStringRename("strings3.xml");
  }

  /** Due to http://b/153850296 we are currently not supporting renaming resources from the Xml Tag token. **/
  public void ignore_testValueResource4() throws Throwable {
    doTestStringRename("strings4.xml");
  }

  public void testValueResource5() throws Throwable {
    String before = "strings5.xml";
    String after = "strings5_after.xml";

    createManifest();
    VirtualFile file = myFixture.copyFileToProject(BASE_PATH + before, "res/values/strings.xml");
    myFixture.configureFromExistingVirtualFile(file);
    checkAndRename("str1");
    myFixture.checkResultByFile(BASE_PATH + after);
  }

  public void testStyleInheritance1() throws Throwable {
    doTestStyleInheritance("styles2.xml", "styles2_after.xml");
  }

  public void testStyleInheritance3() throws Throwable {
    doTestStyleInheritance("styles4.xml", "styles4_after.xml");
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

  /**
   * Test the renaming of the parent of a style
   */
  public void testStyleParentRename() throws Throwable {
    doTestStyleInheritance("styles13.xml", "styles13_after.xml", "myStyle42");
  }

  protected void doTestStyleInheritance(String before, String after) throws IOException {
    doTestStyleInheritance(before, after, "newStyle");
  }

  /**
   * Rename a style resource on the caret position (set in the {@code before} file). Compare the output with a result ({@code after}) file.
   *
   * @param before  Path of the file that represents the resource file before renaming. It includes the caret position.
   * @param after   Path of the file that represents the resource file after renaming.
   * @param newName The new name of the style being renamed
   */
  protected void doTestStyleInheritance(String before, String after, String newName) throws IOException {
    createManifest();
    final VirtualFile file = myFixture.copyFileToProject(BASE_PATH + before, "res/values/" + before);
    myFixture.configureFromExistingVirtualFile(file);
    doRename(newName);
    myFixture.checkResultByFile(BASE_PATH + after);
  }

  protected void doTestStringRename(String fileName) throws IOException {
    createManifest();
    VirtualFile file = myFixture.copyFileToProject(BASE_PATH + fileName, "res/values/strings.xml");
    myFixture.configureFromExistingVirtualFile(file);

    myFixture.copyFileToProject(BASE_PATH + "layoutStrUsage.xml", "res/layout/layoutStrUsage.xml");
    doRename("str1");

    myFixture.checkResultByFile(BASE_PATH + "strings_after.xml");
    myFixture.checkResultByFile("res/layout/layoutStrUsage.xml", BASE_PATH + "layoutStrUsage_after.xml", true);
  }

  public void testJavaReferenceToValueResource() throws Throwable {
    createManifest();
    VirtualFile file = myFixture.copyFileToProject(BASE_PATH + "RefR4.java", "src/p1/p2/RefR4.java");
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.copyFileToProject(BASE_PATH + "layout4.xml", "res/layout/layout4.xml");
    myFixture.copyFileToProject(BASE_PATH + "strings.xml", "res/values/strings.xml");
    checkAndRename("str1");
    myFixture.checkResultByFile(BASE_PATH + "RefR4_after.java", true);
    myFixture.checkResultByFile("res/layout/layout4.xml", BASE_PATH + "layout_value_after.xml", true);
    myFixture.checkResultByFile("res/values/strings.xml", BASE_PATH + "strings_after.xml", true);
  }

  public void testIdDeclaration() throws Throwable {
    createManifest();
    VirtualFile file = myFixture.copyFileToProject(BASE_PATH + "layout6.xml", "res/layout/layout6.xml");
    myFixture.configureFromExistingVirtualFile(file);
    checkAndRename("@+id/anchor1");
    myFixture.checkResultByFile(BASE_PATH + "layout_id_after.xml");
  }

  public void testStyleable() throws Throwable {
    createManifest();
    VirtualFile file = myFixture.copyFileToProject(BASE_PATH + "RefR8.java", "src/p1/p2/RefR8.java");
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.copyFileToProject(BASE_PATH + "attrs8.xml", "res/values/attrs8.xml");
    checkAndRename("LabelView1");
    myFixture.checkResultByFile(BASE_PATH + "RefR8_after.java", true);
    myFixture.checkResultByFile("res/values/attrs8.xml", BASE_PATH + "attrs8_after.xml", true);
  }

  public void testAttr() throws Throwable {
    createManifest();
    VirtualFile file = myFixture.copyFileToProject(BASE_PATH + "RefR9.java", "src/p1/p2/RefR9.java");
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.copyFileToProject(BASE_PATH + "attrs9.xml", "res/values/attrs9.xml");
    checkAndRename("attr1");
    myFixture.checkResultByFile(BASE_PATH + "RefR9_after.java", true);
    myFixture.checkResultByFile("res/values/attrs9.xml", BASE_PATH + "attrs9_after.xml", true);
  }

  public void testItemAttr() throws Throwable {
    createManifest();
    PsiFile file = myFixture.addFileToProject(
      "res/values/style.xml",
      //language=XML
      "<resources>\n" +
      "    <style name=\"Example\">\n" +
      "        <item name=\"att<caret>r\">true</item>\n" +
      "    </style>\n" +
      "</resources>");
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
    myFixture.addFileToProject(
      "res/values/attrs.xml",
      //language=xml
      "<resources>\n" +
      "  <declare-styleable name=\"LabelView\">\n" +
      "    <attr name=\"attr\" format=\"boolean\" />\n" +
      "  </declare-styleable>\n" +
      "</resources>");
    checkAndRename("attr1");
    myFixture.checkResult(
      //language=XML
      "<resources>\n" +
      "    <style name=\"Example\">\n" +
      "        <item name=\"attr1\">true</item>\n" +
      "    </style>\n" +
      "</resources>", true);
    myFixture.checkResult(
      "res/values/attrs.xml",
      //language=xml
      "<resources>\n" +
      "  <declare-styleable name=\"LabelView\">\n" +
      "    <attr name=\"attr1\" format=\"boolean\" />\n" +
      "  </declare-styleable>\n" +
      "</resources>", true);
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
      addModuleWithAndroidFacet(projectBuilder, modules, "module1", PROJECT_TYPE_APP);
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
    VirtualFile manifestFile = myFixture.copyFileToProject(BASE_PATH + "RenameJavaPackage1.xml", SdkConstants.FN_ANDROID_MANIFEST_XML);
    myFixture.configureFromExistingVirtualFile(manifestFile);
    doRenameJavaPackage("p1.p2", "p3");
    myFixture.checkResultByFile(BASE_PATH + "RenameJavaPackage1_after.xml");
  }

  public void testRenameJavaPackage2() throws Throwable {
    myFixture.copyDirectoryToProject(BASE_PATH + "empty", "src/p1/p2");
    myFixture.copyFileToProject(BASE_PATH + "MyActivity.java", "src/p1/p2/MyActivity.java");
    VirtualFile manifestFile = myFixture.copyFileToProject(BASE_PATH + "RenameJavaPackage2.xml", SdkConstants.FN_ANDROID_MANIFEST_XML);
    myFixture.configureFromExistingVirtualFile(manifestFile);
    doRenameJavaPackage("p1.p2", "p3");
    myFixture.checkResultByFile(BASE_PATH + "RenameJavaPackage2_after.xml");
  }

  public void testRenameJavaPackage3() throws Throwable {
    myFixture.copyDirectoryToProject(BASE_PATH + "empty", "src/p1/p2");
    myFixture.copyFileToProject(BASE_PATH + "MyActivity.java", "src/p1/p2/MyActivity.java");
    VirtualFile manifestFile = myFixture.copyFileToProject(BASE_PATH + "RenameJavaPackage3.xml", SdkConstants.FN_ANDROID_MANIFEST_XML);
    myFixture.configureFromExistingVirtualFile(manifestFile);
    doRenameJavaPackage("p1", "p3");
    myFixture.checkResultByFile(BASE_PATH + "RenameJavaPackage3_after.xml");
  }

  public void testRenameJavaPackage4() throws Throwable {
    myFixture.copyDirectoryToProject(BASE_PATH + "empty", "src/p1/p2");
    myFixture.copyFileToProject(BASE_PATH + "MyActivity.java", "src/p1/p2/MyActivity.java");
    VirtualFile manifestFile = myFixture.copyFileToProject(BASE_PATH + "RenameJavaPackage4.xml", SdkConstants.FN_ANDROID_MANIFEST_XML);
    myFixture.configureFromExistingVirtualFile(manifestFile);
    doRenameJavaPackage("p1.p2", "p3");
    myFixture.checkResultByFile(BASE_PATH + "RenameJavaPackage4_after.xml");
  }

  public void testRenameJavaPackage5() throws Throwable {
    myFixture.copyDirectoryToProject(BASE_PATH + "empty", "src/p1/p2");
    myFixture.copyFileToProject(BASE_PATH + "MyActivity.java", "src/p1/p2/MyActivity.java");
    VirtualFile manifestFile = myFixture.copyFileToProject(BASE_PATH + "RenameJavaPackage5.xml", SdkConstants.FN_ANDROID_MANIFEST_XML);
    myFixture.configureFromExistingVirtualFile(manifestFile);
    doRenameJavaPackage("p1", "p3");
    myFixture.checkResultByFile(BASE_PATH + "RenameJavaPackage5_after.xml");
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
    checkAndRename("@+id/newname");
    myFixture.checkResultByFile(BASE_PATH + "layout15_after.xml");
  }

  /**
   * Checks that two references to the same resource on the same tag don't invalidate each other's state. Changing value of one attribute
   * will make the DOM layer create new handlers for other attributes, which invalidates {@link com.intellij.util.xml.GenericDomValue}s
   * stored in e.g. references being rewritten during the same refactoring.
   *
   * <p>Regression for b/128436102
   */
  public void testMultipleReferencesSameTag() {
    //noinspection CheckTagEmptyBody
    myFixture.addFileToProject("res/layout/my_layout.xml",
                               // language=XML
                               "<LinearLayout xmlns:android='http://schemas.android.com/apk/res/android'\n" +
                               "  android:layout_width='@dimen/foo'\n" +
                               "  android:layout_height='@dimen/foo'>" +
                               "</LinearLayout>");

    PsiFile dimens =
      myFixture.addFileToProject("res/values/dimens.xml",
                                 // language=XML
                                 "<resources>" +
                                 "  <dimen name='<caret>foo'>10dp</dimen>" +
                                 "</resources>");

    myFixture.configureFromExistingVirtualFile(dimens.getVirtualFile());
    checkAndRename("bar");

    //noinspection CheckTagEmptyBody
    myFixture.checkResult("res/layout/my_layout.xml",
                          // language=XML
                          "<LinearLayout xmlns:android='http://schemas.android.com/apk/res/android'\n" +
                          "  android:layout_width='@dimen/bar'\n" +
                          "  android:layout_height='@dimen/bar'>" +
                          "</LinearLayout>",
                          true);
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

  protected void checkAndRename(String newName) {
    RenameElementAction action = new RenameElementAction();
    AnActionEvent e = TestActionEvent.createTestEvent(
      action, DataManager.getInstance().getDataContext(myFixture.getEditor().getComponent()));
    action.update(e);
    assertTrue(e.getPresentation().isEnabled() && e.getPresentation().isVisible());
    doRename(newName);
  }

  protected void doRename(String newName) {
    if (!AndroidTestUtils.renameElementAtCaretUsingAndroidHandler(myFixture, newName)) {
      myFixture.renameElementAtCaret(newName);
    }
  }
}
