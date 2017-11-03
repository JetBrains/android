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

package org.jetbrains.android.dom;

import com.android.SdkConstants;
import com.android.builder.model.AndroidProject;
import com.android.testutils.TestUtils;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbServiceImpl;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.refactoring.actions.InlineAction;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.spellchecker.inspections.SpellCheckingInspection;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import org.jetbrains.android.dom.wrappers.LazyValueResourceElementWrapper;
import org.jetbrains.android.inspections.CreateValueResourceQuickFix;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AndroidValueResourcesTest extends AndroidDomTestCase {
  public AndroidValueResourcesTest() {
    super("dom/resources");
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
  protected void configureAdditionalModules(@NotNull TestFixtureBuilder<IdeaProjectTestFixture> projectBuilder,
                                            @NotNull List<MyAdditionalModuleData> modules) {
    addModuleWithAndroidFacet(projectBuilder, modules, "lib", AndroidProject.PROJECT_TYPE_LIBRARY);
  }

  @Override
  protected String getPathToCopy(String testFileName) {
    if (getTestName(true).equals("resOverlay")) {
      return "res-overlay/values/" + testFileName;
    }
    return "res/values/" + testFileName;
  }

  public void testHtmlTags() throws Throwable {
    doTestCompletionVariants("htmlTags.xml", "b", "i", "u");
  }

  public void testParentStylesCompletion() throws Throwable {
    doTestCompletionVariants("styles_parent.xml", "Parent", "Parent2", "AppTheme");
  }

  public void testParentStyleReferenceCompletion() throws Throwable {
    doTestCompletionVariants("styles_parent_reference.xml", "@style/Parent", "@style/Parent2", "@style/AppTheme", "@style/NoXxxx",
                             "@style/style1");
  }

  /** Checks the completion of parent styles when the attribute is empty */
  public void testParentStylesEmptyCompletion() throws Throwable {
    doTestCompletionVariants("styles_parent_empty.xml", "android:", "Parent", "AppTheme", "style1");
  }

  /** Checks the completion of parent styles when the attribute is only "@" */
  public void testParentStylesEmptyReferenceCompletion() throws Throwable {
    doTestCompletionVariants("styles_parent_empty_reference.xml", "@android:", "@style/Parent", "@style/AppTheme", "@style/style1");
  }

  public void testStyles1() throws Throwable {
    doTestCompletionVariants("styles1.xml", "@drawable/picture1", "@drawable/picture2", "@drawable/picture3");
  }

  public void testStyles2() throws Throwable {
    VirtualFile file = copyFileToProject("styles2.xml");
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.complete(CompletionType.BASIC);
    myFixture.type('\n');
    myFixture.checkResultByFile(myTestFolder + '/' + "styles2_after.xml");
  }

  public void testStyles3() throws Throwable {
    doTestCompletionVariants("styles3.xml", "normal", "bold", "italic");
  }

  public void testStylesHighlighting() throws Throwable {
    doTestHighlighting("styles4.xml");
  }

  public void testAttrFormatCompletion() throws Throwable {
    toTestCompletion("attrs1.xml", "attrs1_after.xml");
  }

  public void testDeclareStyleableNameCompletion() throws Throwable {
    copyFileToProject("LabelView.java", "src/p1/p2/LabelView.java");
    doTestCompletionVariants("attrs2.xml", "LabelView");
  }

  public void testDeclareStyleableNameHighlighting() throws Throwable {
    copyFileToProject("LabelView.java", "src/p1/p2/LabelView.java");
    doTestHighlighting("attrs3.xml");
  }

  public void testDeclareStyleableNameNavigation1() throws Exception {
    copyFileToProject("LabelView.java", "src/p1/p2/LabelView.java");
    VirtualFile file = copyFileToProject("attrs4.xml");
    myFixture.configureFromExistingVirtualFile(file);

    PsiElement[] targets =
      GotoDeclarationAction.findAllTargetElements(myFixture.getProject(), myFixture.getEditor(), myFixture.getCaretOffset());
    assertNotNull(targets);
    assertEquals(1, targets.length);
    PsiElement targetElement = targets[0];
    assertInstanceOf(targetElement, PsiClass.class);
    assertEquals("android.widget.TextView", ((PsiClass)targetElement).getQualifiedName());
  }

  public void testDeclareStyleableNameNavigation2() throws Exception {
    copyFileToProject("LabelView.java", "src/p1/p2/LabelView.java");
    VirtualFile file = copyFileToProject("attrs5.xml");
    myFixture.configureFromExistingVirtualFile(file);

    PsiElement[] targets =
      GotoDeclarationAction.findAllTargetElements(myFixture.getProject(), myFixture.getEditor(), myFixture.getCaretOffset());
    assertNotNull(targets);
    assertEquals(1, targets.length);
    PsiElement targetElement = targets[0];
    assertInstanceOf(targetElement, PsiClass.class);
    assertEquals("p1.p2.LabelView", ((PsiClass)targetElement).getQualifiedName());
  }

  public void testResourceTypeCompletion() throws Throwable {
    doTestCompletion();
  }

  public void testStyles5() throws Throwable {
    toTestCompletion("styles5.xml", "styles5_after.xml");
  }

  public void testStyles6() throws Throwable {
    toTestCompletion("styles5.xml", "styles5_after.xml");
  }

  public void testStyles7() throws Throwable {
    toTestCompletion("styles5.xml", "styles5_after.xml");
  }

  public void testStyles8() throws Throwable {
    toTestCompletion("styles5.xml", "styles5_after.xml");
  }

  public void testStyles9() throws Throwable {
    toTestCompletion("styles5.xml", "styles5_after.xml");
  }

  public void testStyles10() throws Throwable {
    doTestHighlighting("styles10.xml");
  }

  public void testStylesAttrNameWithoutPrefix() throws Throwable {
    VirtualFile file = copyFileToProject(getTestName(true) + ".xml");
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.complete(CompletionType.BASIC);
    myFixture.type('\n');
    myFixture.checkResultByFile(myTestFolder + '/' + getTestName(true) + "_after.xml");
  }

  public void testMoreTypes() throws Throwable {
    doTestHighlighting("moreTypes.xml");
  }

  public void testBool() throws Throwable {
    toTestCompletion("bool.xml", "bool_after.xml");
  }

  public void testBool1() throws Throwable {
    toTestCompletion("bool1.xml", "bool1_after.xml");
  }

  public void testInteger() throws Throwable {
    doTestCompletionVariants("integer.xml", "integer", "integer-array");
  }

  public void testIntegerArray() throws Throwable {
    toTestCompletion("integerArray.xml", "integerArray_after.xml");
  }

  public void testArray() throws Throwable {
    toTestCompletion("array.xml", "array_after.xml");
  }

  public void testPlurals() throws Throwable {
    doTestCompletion();
  }

  public void testPlurals1() throws Throwable {
    doTestCompletion();
  }

  public void testPlurals2() throws Throwable {
    doTestCompletionVariants(getTestName(true) + ".xml", "zero", "one", "two", "few", "many", "other");
  }

  public void testPlurals3() throws Throwable {
    doTestHighlighting();
  }

  public void testIntResourceReference() throws Throwable {
    myFixture.copyFileToProject(myTestFolder + "/intResReference.xml", "res/layout/main.xml");
    myFixture.copyFileToProject(myTestFolder + "/intbool.xml", "res/values/values.xml");
    myFixture.testCompletion("res/layout/main.xml", myTestFolder + "/intResReference_after.xml");
  }

  public void testBoolResourceReference() throws Throwable {
    myFixture.copyFileToProject(myTestFolder + "/boolResReference.xml", "res/layout/main.xml");
    myFixture.copyFileToProject(myTestFolder + "/intbool.xml", "res/values/values.xml");
    myFixture.testCompletion("res/layout/main.xml", myTestFolder + "/boolResReference_after.xml");
  }

  public void testBoolResourceReferenceDumbMode() throws Throwable {
    myFixture.copyFileToProject(myTestFolder + "/boolResReference.xml", "res/layout/main.xml");
    myFixture.copyFileToProject(myTestFolder + "/intbool.xml", "res/values/values.xml");
    // Completion providers don't actually kick in in dumb mode, but does outside of dumb mode.
    DumbServiceImpl.getInstance(getProject()).setDumb(true);
    myFixture.testCompletion("res/layout/main.xml", myTestFolder + "/boolResReference.xml");
    DumbServiceImpl.getInstance(getProject()).setDumb(false);
    myFixture.testCompletion("res/layout/main.xml", myTestFolder + "/boolResReference_after.xml");
  }

  public void testResourceReferenceAsValueHighlighting() throws Throwable {
    doTestHighlighting();
  }

  public void testNameValidation() throws Throwable {
    doTestHighlighting("nameValidation.xml");
  }

  public void testResourceReferenceAsValueCompletion1() throws Throwable {
    doTestCompletion();
  }

  public void testResourceReferenceAsValueCompletion2() throws Throwable {
    doTestCompletion();
  }

  public void testResourceReferenceAsValueCompletion3() throws Throwable {
    doTestCompletionVariants(getTestName(true) + ".xml", "true", "false");
  }

  public void testResourceReferenceAsValueCompletion4() throws Throwable {
    doTestCompletionVariants(getTestName(true) + ".xml");
  }

  public void testDrawableResourceReference() throws Throwable {
    VirtualFile file = copyFileToProject(getTestName(true) + ".xml", "res/layout/main.xml");
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.complete(CompletionType.BASIC);
    List<String> lookupElements = myFixture.getLookupElementStrings();
    assertContainsElements(lookupElements, "@android:", "@color/color1", "@color/color2", "@drawable/picture1");
    // mipmap won't be offered as an option since there are not mipmap resources
    assertDoesntContain(lookupElements, "@mipmap/icon");

    // Add a mipmap to resources and expect for it to be listed
    myFixture.copyFileToProject(myTestFolder + "/icon.png", "res/mipmap/icon.png");
    myFixture.complete(CompletionType.BASIC);
    assertContainsElements(myFixture.getLookupElementStrings(), "@android:", "@color/color1", "@drawable/picture1", "@mipmap/icon");
  }

  public void testParentStyleReference() throws Throwable {
    VirtualFile file = myFixture.copyFileToProject(myTestFolder + "/psreference.xml", getPathToCopy("psreference.xml"));
    myFixture.configureFromExistingVirtualFile(file);
    PsiFile psiFile = myFixture.getFile();
    String text = psiFile.getText();
    int rootOffset = text.indexOf("android:Theme");
    PsiReference rootReference = psiFile.findReferenceAt(rootOffset);
    assertNotNull(rootReference);
    PsiElement element = rootReference.resolve();
    assertInstanceOf(element, LazyValueResourceElementWrapper.class);
    assertNotNull(((LazyValueResourceElementWrapper)element).computeElement());
  }

  // see getPathToCopy()
  public void testResOverlay() throws Throwable {
    myFacet.getProperties().RES_OVERLAY_FOLDERS = Arrays.asList("/res-overlay");
    doTestCompletionVariants("styles1.xml", "@drawable/picture1", "@drawable/picture2", "@drawable/picture3");
  }

  public void testCreateResourceFromUsage() throws Throwable {
    VirtualFile virtualFile = copyFileToProject(getTestName(true) + ".xml", "res/values/drawables.xml");
    doCreateValueResourceFromUsage(virtualFile);
    myFixture.checkResultByFile(myTestFolder + '/' + getTestName(true) + "_after.xml", true);
  }

  public void testJavaCompletion1() throws Throwable {
    copyFileToProject("value_resources.xml", "res/values/value_resources.xml");
    String fileName = getTestName(false) + ".java";
    VirtualFile file = copyFileToProject(fileName, "src/" + "p1.p2".replace('/', '.') + '/' + fileName);
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.complete(CompletionType.BASIC);
    myFixture.type('\n');
    myFixture.checkResultByFile(myTestFolder + '/' + getTestName(false) + "_after.java");
  }

  public void testJavaCompletion2() throws Throwable {
    copyFileToProject("value_resources.xml", "res/values/value_resources.xml");
    doTestJavaCompletion("p1.p2");
  }

  public void testJavaCompletion3() throws Throwable {
    copyFileToProject("value_resources.xml", "res/values/value_resources.xml");
    doTestJavaCompletion("p1.p2");
  }

  public void testJavaCompletion4() throws Throwable {
    copyFileToProject("value_resources.xml", "res/values/value_resources.xml");
    doTestJavaCompletion("p1.p2");
  }

  public void testJavaHighlighting() throws Throwable {
    copyFileToProject("value_resources.xml", "res/values/value_resources.xml");
    doTestJavaHighlighting("p1.p2");
  }

  public void testFraction() throws Throwable {
    toTestCompletion("bool.xml", "bool_after.xml");
  }

  public void testTranslatableAttributeCompletion() throws Throwable {
    toTestCompletion("strings_translatable_attr.xml", "strings_translatable_attr_after.xml");
  }

  public void testTranslatableAttributeCompletionDumbMode() throws Throwable {
    DumbServiceImpl.getInstance(getProject()).setDumb(true);
    toTestCompletion("strings_translatable_attr.xml", "strings_translatable_attr.xml");
    DumbServiceImpl.getInstance(getProject()).setDumb(false);
    toTestCompletion("strings_translatable_attr.xml", "strings_translatable_attr_after.xml");
  }

  public void testTranslatableFalseCompletion() throws Throwable {
    toTestCompletion("strings_translatable_false.xml", "strings_translatable_false_after.xml");
  }

  public void testTranslatableTrueCompletion() throws Throwable {
    toTestCompletion("strings_translatable_true.xml", "strings_translatable_true_after.xml");
  }

  public void testFormattedAttributeCompletion() throws Throwable {
    toTestCompletion("strings_formatted_attr.xml", "strings_formatted_attr_after.xml");
  }

  public void testFormattedFalseCompletion() throws Throwable {
    toTestCompletion("strings_formatted_false.xml", "strings_formatted_false_after.xml");
  }

  public void testInlineResourceField() throws Exception {
    copyFileToProject("value_resources.xml", "res/values/value_resources.xml");
    VirtualFile virtualFile = copyFileToProject(getTestName(false) + ".java", "src/p1/p2/" + getTestName(false) + ".java");
    myFixture.configureFromExistingVirtualFile(virtualFile);
    try {
      myFixture.testAction(new InlineAction());
      fail();
    }
    catch (CommonRefactoringUtil.RefactoringErrorHintException e) {
    }
    myFixture.checkResultByFile(myTestFolder + '/' + getTestName(false) + ".java", true);
  }

  public void testJavaCreateFromUsage() throws Throwable {
    VirtualFile virtualFile = copyFileToProject(getTestName(false) + ".java", "src/p1/p2/" + getTestName(false) + ".java");
    doCreateValueResourceFromUsage(virtualFile);
    myFixture.checkResultByFile("res/values/drawables.xml", myTestFolder + '/' + getTestName(true) + "_drawables_after.xml", true);
  }

  public void testJavaCreateFromUsage1() throws Throwable {
    VirtualFile virtualFile = copyFileToProject(getTestName(false) + ".java", "src/p1/p2/" + getTestName(false) + ".java");
    doCreateValueResourceFromUsage(virtualFile);
    myFixture.checkResultByFile("res/values/bools.xml", myTestFolder + '/' + getTestName(true) + "_bools_after.xml", true);
  }

  /**
   * Test quickfix where the R class is from a dependency, not the main module. This is a proxy for testing Bazel projects where
   * the main "workspace" module doesn't have a manifest or resources. There are instead some resource-only modules which are
   * dependencies of the main module.
   */
  public void testJavaCreateFromUsageResourcesInDeps() throws Throwable {
    // Replace lib manifest (defaults to p1.p2) with one that has the right package (p1.p2.lib).
    Module libModule = myAdditionalModules.get(0);
    deleteManifest(libModule);
    myFixture.copyFileToProject("util/lib/AndroidManifest.xml", "additionalModules/lib/AndroidManifest.xml");
    myFixture.copyFileToProject("util/lib/R.java", "additionalModules/lib/gen/p1/p2/lib/R.java");
    // Should be okay even if main module is missing a manifest since the resources come from the library.
    deleteManifest(myModule);

    final VirtualFile virtualFile = copyFileToProject(getTestName(false) + ".java", "src/p1/p2/" + getTestName(false) + ".java");
    doCreateValueResourceFromUsage(virtualFile);
    myFixture.checkResultByFile("additionalModules/lib/res/values/strings.xml",
                                myTestFolder + '/' + getTestName(true) + "_strings_after.xml", true);
  }

  public void testAttrReferenceCompletion() throws Throwable {
    doTestCompletion();
  }

  public void testAttrReferenceHighlighting() throws Throwable {
    doTestHighlighting();
  }

  public void testToolsBaseAttribute() throws Throwable {
    doTestHighlighting();
  }

  public void testLocalStyleItemReferenceCompletion() throws Throwable {
    copyFileToProject("localStyleItemReference_layout.xml", "res/layout/myLayout.xml");
    doTestCompletion();
  }

  public void testLocalStyleItemReferenceHighlighting() throws Throwable {
    copyFileToProject("localStyleItemReference_layout.xml", "res/layout/myLayout.xml");
    doTestHighlighting();
  }

  // Fails when sandboxed, as the fixture tries to write to themes_holo.xml in the SDK
  public void ignore_testNavigationInPlatformXml1_NavigateFromParentAttr() throws Exception {
    VirtualFile themes_holo =
      LocalFileSystem.getInstance().findFileByPath(TestUtils.getPlatformFile("data/res/values/themes_holo.xml").toString());
    assertNotNull(themes_holo);
    VirtualFile themes =
      LocalFileSystem.getInstance().findFileByPath(TestUtils.getPlatformFile("data/res/values/themes.xml").toString());
    assertNotNull(themes);

    // In themes_holo.xml: point to value of "Theme" in the parent attribute on line:
    //     <style name="Theme.Holo.Light" parent="Theme.Light">
    // Goto action should navigate to "Theme" in themes.xml, on line: "<style name="Theme">"
    myFixture.configureFromExistingVirtualFile(themes_holo);
    myFixture.getEditor().getCaretModel().moveToLogicalPosition(new LogicalPosition(406, 45));

    PsiElement[] targets =
      GotoDeclarationAction.findAllTargetElements(myFixture.getProject(), myFixture.getEditor(), myFixture.getCaretOffset());
    assertNotNull(targets);
    assertEquals(1, targets.length);
    PsiElement targetElement = LazyValueResourceElementWrapper.computeLazyElement(targets[0]);

    assertInstanceOf(targetElement, XmlAttributeValue.class);
    XmlAttributeValue targetAttrValue = (XmlAttributeValue)targetElement;
    assertEquals("Theme", targetAttrValue.getValue());
    assertEquals("name", ((XmlAttribute)targetAttrValue.getParent()).getName());
    assertEquals("style", ((XmlTag)targetAttrValue.getParent().getParent()).getName());
    assertEquals(themes, targetElement.getContainingFile().getVirtualFile());
  }

  // Fails when sandboxed, as the fixture tries to write to themes_holo.xml in the SDK
  public void ignore_testNavigationInPlatformXml2_NavigateFromNameAttr() throws Exception {
    VirtualFile themes_holo =
      LocalFileSystem.getInstance().findFileByPath(TestUtils.getPlatformFile("data/res/values/themes_holo.xml").toString());
    assertNotNull(themes_holo);
    VirtualFile themes =
      LocalFileSystem.getInstance().findFileByPath(TestUtils.getPlatformFile("data/res/values/themes.xml").toString());
    assertNotNull(themes);

    // In themes_holo.xml: point to value of "Theme" in the name attribute on line:
    //     <style name="Theme.Holo.NoActionBar">
    // Goto action should navigate to "Theme" in themes.xml, on line: "<style name="Theme">"
    myFixture.configureFromExistingVirtualFile(themes_holo);
    myFixture.getEditor().getCaretModel().moveToLogicalPosition(new LogicalPosition(776, 19));

    PsiElement[] targets =
      GotoDeclarationAction.findAllTargetElements(myFixture.getProject(), myFixture.getEditor(), myFixture.getCaretOffset());
    assertNotNull(targets);
    assertEquals(1, targets.length);
    PsiElement targetElement = LazyValueResourceElementWrapper.computeLazyElement(targets[0]);

    assertInstanceOf(targetElement, XmlAttributeValue.class);
    XmlAttributeValue targetAttrValue = (XmlAttributeValue)targetElement;
    assertEquals("Theme", targetAttrValue.getValue());
    assertEquals("name", ((XmlAttribute)targetAttrValue.getParent()).getName());
    assertEquals("style", ((XmlTag)targetAttrValue.getParent().getParent()).getName());
    assertEquals(themes, targetElement.getContainingFile().getVirtualFile());
  }

  // Fails when sandboxed, as the fixture tries to write to themes_holo.xml in the SDK
  public void ignore_testNavigationInPlatformXml3() throws Exception {
    VirtualFile themes_holo =
      LocalFileSystem.getInstance().findFileByPath(TestUtils.getPlatformFile("data/res/values/themes_holo.xml").toString());
    assertNotNull(themes_holo);
    VirtualFile colors_holo =
      LocalFileSystem.getInstance().findFileByPath(TestUtils.getPlatformFile("data/res/values/colors_holo.xml").toString());
    assertNotNull(colors_holo);

    // In themes_holo.xml: point to value of "bright_foreground_holo_light" on line:
    //    <item name="colorForeground">@color/bright_foreground_holo_light</item>
    // Goto action should navigate to "bright_foreground_holo_light" in colors_holo.xml, on line:
    //    <color name="bright_foreground_holo_light">@color/background_holo_dark</color>
    myFixture.configureFromExistingVirtualFile(themes_holo);
    myFixture.getEditor().getCaretModel().moveToLogicalPosition(new LogicalPosition(407, 60));

    PsiElement[] targets =
      GotoDeclarationAction.findAllTargetElements(myFixture.getProject(), myFixture.getEditor(), myFixture.getCaretOffset());
    assertNotNull(targets);
    assertEquals(1, targets.length);
    PsiElement targetElement = LazyValueResourceElementWrapper.computeLazyElement(targets[0]);

    assertInstanceOf(targetElement, XmlAttributeValue.class);
    XmlAttributeValue targetAttrValue = (XmlAttributeValue)targetElement;
    assertEquals("bright_foreground_holo_light", targetAttrValue.getValue());
    assertEquals("name", ((XmlAttribute)targetAttrValue.getParent()).getName());
    assertEquals("color", ((XmlTag)targetAttrValue.getParent().getParent()).getName());
    assertEquals(colors_holo, targetElement.getContainingFile().getVirtualFile());
  }

  public void testSpellchecker1() throws Throwable {
    myFixture.enableInspections(SpellCheckingInspection.class);
    doTestHighlighting();
  }

  public void testSpellchecker2() throws Throwable {
    doTestSpellcheckerQuickFixes();
  }

  public void testSpellchecker3() throws Throwable {
    // In English locale, should highlight typos
    myFixture.enableInspections(SpellCheckingInspection.class);
    VirtualFile file = copyFileToProject("spellchecker3.xml", "res/values-en-rUS/spellchecker3.xml");
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.checkHighlighting(true, false, false);
  }

  public void testSpellchecker4() throws Throwable {
    // In non-English locale, should not highlight typos
    myFixture.enableInspections(SpellCheckingInspection.class);
    VirtualFile file = copyFileToProject("spellchecker4.xml", "res/values-nb/spellchecker4.xml");
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.checkHighlighting(true, false, false);
  }

  public void testSpellchecker5() throws Throwable {
    // In default locale, with a tools:locale defined to non-English, should not get typos highlighted
    myFixture.enableInspections(SpellCheckingInspection.class);
    VirtualFile file = copyFileToProject("spellchecker5.xml", "res/values/spellchecker5.xml");
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.checkHighlighting(true, false, false);
  }

  public void testSpellchecker6() throws Throwable {
    // In default locale, with a tools:locale defined to English, should get typos highlighted
    myFixture.enableInspections(SpellCheckingInspection.class);
    VirtualFile file = copyFileToProject("spellchecker6.xml", "res/values/spellchecker6.xml");
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.checkHighlighting(true, false, false);
  }

  public void testSpellNewlines() throws Throwable {
    myFixture.enableInspections(SpellCheckingInspection.class);
    doTestHighlighting();
  }

  public void testDoNotFlagLintXml() throws Throwable {
    // In default locale, with a tools:locale defined to non-English, should not get typos highlighted
    myFixture.enableInspections(SpellCheckingInspection.class);
    VirtualFile file = copyFileToProject("lint.xml", "lint.xml");
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.checkHighlighting(true, false, false);
  }

  public void testNamespaceCompletion() throws Exception {
    doTestNamespaceCompletion(false, false, true, true);
  }

  public void testAttrValidation() throws Throwable {
    // Regression test for https://code.google.com/p/android/issues/detail?id=199247
    // Allow colons in names for attributes
    doTestHighlighting("attrValidation.xml");
  }

  private void doCreateValueResourceFromUsage(VirtualFile virtualFile) {
    myFixture.configureFromExistingVirtualFile(virtualFile);
    List<HighlightInfo> infos = myFixture.doHighlighting();
    List<IntentionAction> actions = new ArrayList<>();

    for (HighlightInfo info : infos) {
      List<Pair<HighlightInfo.IntentionActionDescriptor, TextRange>> ranges = info.quickFixActionRanges;

      if (ranges != null) {
        for (Pair<HighlightInfo.IntentionActionDescriptor, TextRange> pair : ranges) {
          IntentionAction action = pair.getFirst().getAction();
          if (action instanceof CreateValueResourceQuickFix) {
            actions.add(action);
          }
        }
      }
    }
    assertEquals(1, actions.size());

    new WriteCommandAction.Simple(getProject()) {
      @Override
      protected void run() throws Throwable {
        actions.get(0).invoke(getProject(), myFixture.getEditor(), myFixture.getFile());
      }
    }.execute();
  }
}
