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

import static com.google.common.truth.Truth.assertThat;

import com.android.AndroidProjectTypes;
import com.android.SdkConstants;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceReference;
import com.android.resources.ResourceType;
import com.android.testutils.TestUtils;
import com.android.tools.idea.res.psi.ResourceReferencePsiElement;
import com.android.tools.idea.testing.AndroidTestUtils;
import com.google.common.base.CaseFormat;
import com.google.common.collect.ImmutableList;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.IdentifierHighlighterPassFactory;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction;
import com.intellij.codeInspection.ex.QuickFixWrapper;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbServiceImpl;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.refactoring.actions.InlineAction;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.spellchecker.inspections.SpellCheckingInspection;
import com.intellij.spellchecker.quickfixes.RenameTo;
import com.intellij.spellchecker.quickfixes.SaveTo;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.intellij.util.containers.ContainerUtil;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import javaslang.collection.Array;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.android.dom.inspections.AndroidDomInspection;
import org.jetbrains.android.dom.inspections.AndroidElementNotAllowedInspection;
import org.jetbrains.android.dom.inspections.AndroidUnknownAttributeInspection;
import org.jetbrains.android.inspections.CreateValueResourceQuickFix;
import org.jetbrains.annotations.NotNull;

/**
 * Tests for code editor features when working with resources under res/values.
 *
 * @see AndroidNamespacedValueResourcesDomTest
 */
public class AndroidValueResourcesTest extends AndroidTestCase {
  private static final String MY_TEST_FOLDER = "dom/resources";

  @Override
  protected boolean providesCustomManifest() {
    return true;
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();

    myFixture.enableInspections(AndroidDomInspection.class,
                                AndroidUnknownAttributeInspection.class,
                                AndroidElementNotAllowedInspection.class);

    myFixture.copyFileToProject(SdkConstants.FN_ANDROID_MANIFEST_XML, SdkConstants.FN_ANDROID_MANIFEST_XML);
  }

  @Override
  protected final String getResDir() {
    return "dom/res";
  }

  @Override
  protected void configureAdditionalModules(@NotNull TestFixtureBuilder<IdeaProjectTestFixture> projectBuilder,
                                            @NotNull List<MyAdditionalModuleData> modules) {
    addModuleWithAndroidFacet(projectBuilder, modules, "lib", AndroidProjectTypes.PROJECT_TYPE_LIBRARY);
  }

  private String getPathToCopy(String testFileName) {
    if (getTestName(true).equals("resOverlay")) {
      return "res-overlay/values/" + testFileName;
    }
    return "res/values/" + testFileName;
  }

  public void testMacroTagHighlighting() {
    PsiFile file = myFixture.addFileToProject("res/values/values.xml",
                                              "<resources>\n" +
                                              "  <macro name=\"foo\">@string/bar</macro>\n" +
                                              "  <string name=\"bar\">bar</string>\n" +
                                              "  <string name=\"otherbar\">bar</string>\n" +
                                              "  <color name=\"colorbar\">#123456</color>\n" +
                                              "</resources>");
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
    myFixture.checkHighlighting();
  }

  public void testMacroTagStyleAttributeHighlighting() {
    PsiFile file = myFixture.addFileToProject("res/values/values.xml",
                                              "<resources>\n" +
                                              "  <macro name=\"foo\">@string/bar</macro>\n" +
                                              "  <string name=\"bar\">bar</string>\n" +
                                              "  <macro name=\"asdsf\">?attr/<caret>textColor</macro>\n" +
                                              "  <color name=\"colorbar\">#123456</color>\n" +
                                              "</resources>");
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
    myFixture.checkHighlighting();
    PsiElement elementAtCaret = myFixture.getElementAtCaret();
    assertThat(elementAtCaret).isInstanceOf(ResourceReferencePsiElement.class);
    assertThat(((ResourceReferencePsiElement)elementAtCaret).getResourceReference()).isEqualTo(
      new ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.ATTR, "textColor"));
  }

  public void testStringArrayHighlighting() {
    PsiFile file = myFixture.addFileToProject("res/values/strings.xml",
                                              "<resources>\n" +
                                              "    <string-array name=\"foo\" translatable=\"false\"/>\n" +
                                              "</resources>");
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
    myFixture.checkHighlighting();
  }

  public void testStringArrayCompletion() {
    PsiFile file = myFixture.addFileToProject("res/values/strings.xml",
                                              "<resources>\n" +
                                              "    <string-array name=\"foo\" <caret>/>\n" +
                                              "</resources>");
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
    myFixture.completeBasic();
    List<String> lookupElementStrings = myFixture.getLookupElementStrings();
    assertThat(lookupElementStrings).contains("translatable");
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
    myFixture.checkResultByFile(MY_TEST_FOLDER + '/' + "styles2_after.xml");
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
    assertThat(targets).isNotNull();
    assertThat(targets.length).isEqualTo(1);
    PsiElement targetElement = targets[0];
    // A styleable only refers to the resource, not the class that may not exist.
    assertThat(targetElement).isInstanceOf(XmlAttributeValue.class);
    assertThat(targetElement.getText()).isEqualTo("\"TextView\"");
  }

  public void testDeclareStyleableNameNavigation2() throws Exception {
    copyFileToProject("LabelView.java", "src/p1/p2/LabelView.java");
    VirtualFile file = copyFileToProject("attrs5.xml");
    myFixture.configureFromExistingVirtualFile(file);

    PsiElement[] targets =
      GotoDeclarationAction.findAllTargetElements(myFixture.getProject(), myFixture.getEditor(), myFixture.getCaretOffset());
    assertThat(targets).isNotNull();
    // In the new resources pipeline, a styleable only refers to the resource, not the class that may not exist.
    assertThat(targets.length).isEqualTo(3);
    for (PsiElement target : targets) {
      assertThat(target).isInstanceOf(XmlAttributeValue.class);
    }
    List<String> getTextList = ContainerUtil.map(targets, PsiElement::getText);
    assertThat(getTextList).containsExactlyElementsIn(Array.of("\"LabelView\"", "\"LabelView\"", "\"LabelView\""));
    List<String> containingFileList = ContainerUtil.map(targets, it -> it.getContainingFile().getName());
    assertThat(containingFileList).containsExactlyElementsIn(Array.of("attrs5.xml", "attrs.xml", "attrs.xml"));
  }

  public void testResourceTypeCompletion() throws Throwable {
    // Be careful updating this. "declare-styleable" or "integer-array" is not recognized. "styleable" crashes aapt2.
    doTestCompletionVariants("resourceTypeCompletion.xml",
                             "drawable", "dimen", "bool", "color", "plurals", "string", "raw", "integer", "menu", "transition",
                             "fraction", "layout", "navigation", "mipmap", "array", "interpolator", "xml", "style", "id", "anim", "attr",
                             "animator", "font");
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
    myFixture.checkResultByFile(MY_TEST_FOLDER + '/' + getTestName(true) + "_after.xml");
  }

  public void testPublicTagHighlighting() throws Throwable {
    VirtualFile file = copyFileToProject("public_highlighting.xml", "additionalModules/lib/res/values/public.xml");
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.checkHighlighting();
  }

  public void testPublicTagCompletion() throws Throwable {
    VirtualFile file = copyFileToProject("public.xml", "additionalModules/lib/res/values/public.xml");
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.completeBasic();
    myFixture.checkResultByFile(MY_TEST_FOLDER + '/' + "public_after.xml");
  }

  public void testPublicTagAppModuleCompletion() throws Throwable {
    VirtualFile file = copyFileToProject("public.xml", "res/values/public.xml");
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.completeBasic();
    // In app module, the completion does not work, the file should be unchanged.
    myFixture.checkResultByFile(MY_TEST_FOLDER + '/' + "public.xml");
  }

  public void testPublicTagAttributeValueCompletion() {
    // Resources in app module:
    myFixture.addFileToProject(
      "res/values/strings.xml",
      "<resources>\n" +
      "  <string name=\"foo\">foo</string>\n" +
      "  <color name=\"colorfoo\">#123456</color>\n" +
      "</resources>").getVirtualFile();
    // Resources in lib module:
    myFixture.addFileToProject(
      "additionalModules/lib/res/values/strings.xml",
      "<resources>\n" +
      "  <string name=\"bar\">bar</string>\n" +
      "  <string name=\"otherbar\">bar</string>\n" +
      "  <color name=\"colorbar\">#123456</color>\n" +
      "</resources>").getVirtualFile();

    //Check that all resources are present in code completion for 'name' attribute.
    VirtualFile file = myFixture.addFileToProject(
      "additionalModules/lib/res/values/public.xml",
      "<resources>\n" +
      "  <public name=\"<caret>\" type=\"\"/>\n" +
      "</resources>").getVirtualFile();
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.completeBasic();
    assertThat(myFixture.getLookupElementStrings()).containsAllIn(new String[]{"bar", "colorbar"});

    // Check all resource types are present in code completion for 'type' attribute
    AndroidTestUtils.moveCaret(myFixture, "<public name=\"\" type=\"|\"/>");
    myFixture.completeBasic();
    assertThat(myFixture.getLookupElementStrings())
      .containsAllIn(ResourceType.REFERENCEABLE_TYPES.stream().map(ResourceType::getName).collect(Collectors.toList()));

    // Once a resource type is selected, only resources of that type show up in 'name' attribute completion.
    myFixture.type("string");
    AndroidTestUtils.moveCaret(myFixture, "<public name=\"|\"");
    dispatchEvents();
    myFixture.completeBasic();
    List<String> lookupElementStrings = myFixture.getLookupElementStrings();
    assertThat(lookupElementStrings).containsAllIn(ImmutableList.of("bar", "otherbar"));
    assertThat(lookupElementStrings).doesNotContain(ImmutableList.of("foo"));
  }

  public void testOverlayableTagCompletion() throws Throwable {
    toTestCompletion("overlayable.xml", "overlayable_after.xml");
  }

  public void testOverlayable() throws Throwable {
    // Note that the expected
    doTestHighlighting("overlayable_example.xml");
  }


  public void testPolicyTagCompletion() throws Throwable {
    toTestCompletion("policy.xml", "policy_after.xml");
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

  public void testItemArray() throws Throwable {
    doTestHighlighting("itemArray.xml");
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

  public void testIntResourceReference() {
    myFixture.copyFileToProject(MY_TEST_FOLDER + "/intResReference.xml", "res/layout/main.xml");
    myFixture.copyFileToProject(MY_TEST_FOLDER + "/intbool.xml", "res/values/values.xml");
    myFixture.testCompletion("res/layout/main.xml", MY_TEST_FOLDER + "/intResReference_after.xml");
  }

  public void testBoolResourceReference() {
    myFixture.copyFileToProject(MY_TEST_FOLDER + "/boolResReference.xml", "res/layout/main.xml");
    myFixture.copyFileToProject(MY_TEST_FOLDER + "/intbool.xml", "res/values/values.xml");
    myFixture.testCompletion("res/layout/main.xml", MY_TEST_FOLDER + "/boolResReference_after.xml");
  }

  public void testBoolResourceReferenceDumbMode() {
    myFixture.copyFileToProject(MY_TEST_FOLDER + "/boolResReference.xml", "res/layout/main.xml");
    myFixture.copyFileToProject(MY_TEST_FOLDER + "/intbool.xml", "res/values/values.xml");
    // Completion providers don't actually kick in in dumb mode, but does outside of dumb mode.
    DumbServiceImpl.getInstance(getProject()).setDumb(true);
    myFixture.testCompletion("res/layout/main.xml", MY_TEST_FOLDER + "/boolResReference.xml");
    DumbServiceImpl.getInstance(getProject()).setDumb(false);
    myFixture.testCompletion("res/layout/main.xml", MY_TEST_FOLDER + "/boolResReference_after.xml");
  }

  public void testResourceReferenceAsValueHighlighting() throws Throwable {
    doTestHighlighting();
  }

  public void testNameValidation() throws Throwable {
    doTestHighlighting("nameValidation.xml");
  }

  public void testMissingType() throws Throwable {
    doTestHighlighting("missingType.xml");
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
    assertThat(lookupElements).containsAllOf("@android:", "@color/color1", "@color/color2", "@drawable/picture1");
    // mipmap won't be offered as an option since there are not mipmap resources
    assertThat(lookupElements).doesNotContain("@mipmap/icon");

    // Add a mipmap to resources and expect for it to be listed
    myFixture.copyFileToProject(MY_TEST_FOLDER + "/icon.png", "res/mipmap/icon.png");
    waitForResourceRepositoryUpdates();
    myFixture.complete(CompletionType.BASIC);
    assertThat(myFixture.getLookupElementStrings()).containsAllOf("@android:", "@color/color1", "@drawable/picture1", "@mipmap/icon");
  }

  public void testParentStyleReference() {
    VirtualFile file = myFixture.copyFileToProject(MY_TEST_FOLDER + "/psreference.xml", getPathToCopy("psreference.xml"));
    myFixture.configureFromExistingVirtualFile(file);
    PsiFile psiFile = myFixture.getFile();
    String text = psiFile.getText();
    int rootOffset = text.indexOf("Theme\">");
    PsiReference rootReference = psiFile.findReferenceAt(rootOffset);
    assertThat(rootReference).isNotNull();
    PsiElement element = rootReference.resolve();
    assertThat(element).isInstanceOf(ResourceReferencePsiElement.class);
    assertThat(((ResourceReferencePsiElement)element).getResourceReference())
      .isEqualTo(new ResourceReference(ResourceNamespace.ANDROID, ResourceType.STYLE, "Theme"));
  }

  // see getPathToCopy()
  public void testResOverlay() throws Throwable {
    myFacet.getProperties().RES_OVERLAY_FOLDERS = Arrays.asList("/res-overlay");
    doTestCompletionVariants("styles1.xml", "@drawable/picture1", "@drawable/picture2", "@drawable/picture3");
  }

  public void testCreateResourceFromUsage() throws Throwable {
    VirtualFile virtualFile = copyFileToProject(getTestName(true) + ".xml", "res/values/drawables.xml");
    doCreateValueResourceFromUsage(virtualFile);
    myFixture.checkResultByFile(MY_TEST_FOLDER + '/' + getTestName(true) + "_after.xml", true);
  }

  public void testJavaCompletion1() throws Throwable {
    copyFileToProject("value_resources.xml", "res/values/value_resources.xml");
    String fileName = getTestName(false) + ".java";
    VirtualFile file = copyFileToProject(fileName, "src/" + "p1.p2".replace('/', '.') + '/' + fileName);
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.complete(CompletionType.BASIC);
    myFixture.type('\n');
    myFixture.checkResultByFile(MY_TEST_FOLDER + '/' + getTestName(false) + "_after.java");
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
    myFixture.checkResultByFile(MY_TEST_FOLDER + '/' + getTestName(false) + ".java", true);
  }

  public void testJavaCreateFromUsage() throws Throwable {
    VirtualFile virtualFile = copyFileToProject(getTestName(false) + ".java", "src/p1/p2/" + getTestName(false) + ".java");
/* b/266338533
    doCreateValueResourceFromUsage(virtualFile);
    myFixture.checkResultByFile("res/values/drawables.xml", myTestFolder + '/' + getTestName(true) + "_drawables_after.xml", true);
b/266338533 */
  }

  public void testJavaCreateFromUsage1() throws Throwable {
    VirtualFile virtualFile = copyFileToProject(getTestName(false) + ".java", "src/p1/p2/" + getTestName(false) + ".java");
/* b/263898646
    doCreateValueResourceFromUsage(virtualFile);
    myFixture.checkResultByFile("res/values/bools.xml", myTestFolder + '/' + getTestName(true) + "_bools_after.xml", true);
b/263898646 */
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

    // Should be okay even if main module is missing a manifest since the resources come from the library.
    deleteManifest(myModule);

    final VirtualFile virtualFile = copyFileToProject(getTestName(false) + ".java", "src/p1/p2/" + getTestName(false) + ".java");
/* b/263898646
    doCreateValueResourceFromUsage(virtualFile);
    myFixture.checkResultByFile("additionalModules/lib/res/values/strings.xml",
                                myTestFolder + '/' + getTestName(true) + "_strings_after.xml", true);
b/263898646 */
  }

  public void testAttrReferenceCompletion() throws Throwable {
    doTestCompletion();
  }

  public void testAttrReferenceHighlighting() throws Throwable {
    // New resources pipeline does not treat ATTRs different to other ResourceTypes, having an incorrect type should be surfaced in a lint
    // check, not reference resolution.
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

  public void testAndroidPrefixInsertHandler() throws Throwable {
    String fileName = getTestName(true) + ".xml";

    myFixture.configureFromExistingVirtualFile(copyFileToProject(fileName));
    myFixture.complete(CompletionType.BASIC);
    assertThat(myFixture.getLookupElementStrings().get(0)).isEqualTo("@android:");
    myFixture.type('\n');
    // Assert another completion was started automatically and that all framework resources are present.
    assertThat(myFixture.getLookupElements().length).isGreaterThan(100);
  }


  // Fails when sandboxed, as the fixture tries to write to themes_holo.xml in the SDK
  public void ignore_testNavigationInPlatformXml1_NavigateFromParentAttr() throws Exception {
    VirtualFile themes_holo =
      LocalFileSystem.getInstance().findFileByPath(TestUtils.resolvePlatformPath("data/res/values/themes_holo.xml").toString());
    assertThat(themes_holo).isNotNull();
    VirtualFile themes =
      LocalFileSystem.getInstance().findFileByPath(TestUtils.resolvePlatformPath("data/res/values/themes.xml").toString());
    assertThat(themes).isNotNull();

    // In themes_holo.xml: point to value of "Theme" in the parent attribute on line:
    //     <style name="Theme.Holo.Light" parent="Theme.Light">
    // Goto action should navigate to "Theme" in themes.xml, on line: "<style name="Theme">"
    myFixture.configureFromExistingVirtualFile(themes_holo);
    myFixture.getEditor().getCaretModel().moveToLogicalPosition(new LogicalPosition(406, 45));

    PsiElement[] targets =
      GotoDeclarationAction.findAllTargetElements(myFixture.getProject(), myFixture.getEditor(), myFixture.getCaretOffset());
    assertThat(targets).isNotNull();
    assertThat(targets.length).isEqualTo(1);
    PsiElement targetElement = targets[0];

    assertThat(targetElement).isInstanceOf(XmlAttributeValue.class);
    XmlAttributeValue targetAttrValue = (XmlAttributeValue)targetElement;
    assertThat(targetAttrValue.getValue()).isEqualTo("Theme");
    assertThat(((XmlAttribute)targetAttrValue.getParent()).getName()).isEqualTo("name");
    assertThat(((XmlTag)targetAttrValue.getParent().getParent()).getName()).isEqualTo("style");
    assertThat(targetElement.getContainingFile().getVirtualFile()).isEqualTo(themes);
  }

  // Fails when sandboxed, as the fixture tries to write to themes_holo.xml in the SDK
  public void ignore_testNavigationInPlatformXml2_NavigateFromNameAttr() throws Exception {
    VirtualFile themes_holo =
      LocalFileSystem.getInstance().findFileByPath(TestUtils.resolvePlatformPath("data/res/values/themes_holo.xml").toString());
    assertThat(themes_holo).isNotNull();
    VirtualFile themes =
      LocalFileSystem.getInstance().findFileByPath(TestUtils.resolvePlatformPath("data/res/values/themes.xml").toString());
    assertThat(themes).isNotNull();

    // In themes_holo.xml: point to value of "Theme" in the name attribute on line:
    //     <style name="Theme.Holo.NoActionBar">
    // Goto action should navigate to "Theme" in themes.xml, on line: "<style name="Theme">"
    myFixture.configureFromExistingVirtualFile(themes_holo);
    myFixture.getEditor().getCaretModel().moveToLogicalPosition(new LogicalPosition(776, 19));

    PsiElement[] targets =
      GotoDeclarationAction.findAllTargetElements(myFixture.getProject(), myFixture.getEditor(), myFixture.getCaretOffset());
    assertThat(targets).isNotNull();
    assertThat(targets.length).isEqualTo(1);
    PsiElement targetElement = targets[0];

    assertThat(targetElement).isInstanceOf(XmlAttributeValue.class);
    XmlAttributeValue targetAttrValue = (XmlAttributeValue)targetElement;
    assertThat(targetAttrValue.getValue()).isEqualTo("Theme");
    assertThat(((XmlAttribute)targetAttrValue.getParent()).getName()).isEqualTo("name");
    assertThat(((XmlTag)targetAttrValue.getParent().getParent()).getName()).isEqualTo("style");
    assertThat(targetElement.getContainingFile().getVirtualFile()).isEqualTo(themes);
  }

  // Fails when sandboxed, as the fixture tries to write to themes_holo.xml in the SDK
  public void ignore_testNavigationInPlatformXml3() throws Exception {
    VirtualFile themes_holo =
      LocalFileSystem.getInstance().findFileByPath(TestUtils.resolvePlatformPath("data/res/values/themes_holo.xml").toString());
    assertThat(themes_holo).isNotNull();
    VirtualFile colors_holo =
      LocalFileSystem.getInstance().findFileByPath(TestUtils.resolvePlatformPath("data/res/values/colors_holo.xml").toString());
    assertThat(colors_holo).isNotNull();

    // In themes_holo.xml: point to value of "bright_foreground_holo_light" on line:
    //    <item name="colorForeground">@color/bright_foreground_holo_light</item>
    // Goto action should navigate to "bright_foreground_holo_light" in colors_holo.xml, on line:
    //    <color name="bright_foreground_holo_light">@color/background_holo_dark</color>
    myFixture.configureFromExistingVirtualFile(themes_holo);
    myFixture.getEditor().getCaretModel().moveToLogicalPosition(new LogicalPosition(407, 60));

    PsiElement[] targets =
      GotoDeclarationAction.findAllTargetElements(myFixture.getProject(), myFixture.getEditor(), myFixture.getCaretOffset());
    assertThat(targets).isNotNull();
    assertThat(targets.length).isEqualTo(1);
    PsiElement targetElement = targets[0];

    assertThat(targetElement).isInstanceOf(XmlAttributeValue.class);
    XmlAttributeValue targetAttrValue = (XmlAttributeValue)targetElement;
    assertThat(targetAttrValue.getValue()).isEqualTo("bright_foreground_holo_light");
    assertThat(((XmlAttribute)targetAttrValue.getParent()).getName()).isEqualTo("name");
    assertThat(((XmlTag)targetAttrValue.getParent().getParent()).getName()).isEqualTo("color");
    assertThat(targetElement.getContainingFile().getVirtualFile()).isEqualTo(colors_holo);
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
    doTestNamespaceCompletion(SdkConstants.XLIFF_URI);
  }

  public void testAttrValidation() throws Throwable {
    // Regression test for https://code.google.com/p/android/issues/detail?id=199247
    // Allow colons in names for attributes
    doTestHighlighting("attrValidation.xml");
  }

  public void testIdentifierHighlightingStringName() {
    PsiFile file = myFixture.addFileToProject("res/values/strings.xml",
                                              //language=XML
                                              "<resources>" +
                                              "  <string name=\"foo\">foo</string>" +
                                              "  <string name=\"bar\">@string/foo</string>" +
                                              "</resources>");
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
    myFixture.setReadEditorMarkupModel(true);

    IdentifierHighlighterPassFactory.doWithHighlightingEnabled(getProject(), myFixture.getProjectDisposable(), () -> {
      AndroidTestUtils.moveCaret(myFixture, "<string name=\"f|oo\">foo</string>");
      // Identifier highlighting has been moved out of the highlighting passes, so we need to wait for BackgroundHighlighter to be computed.
      IdentifierHighlighterPassFactory.waitForIdentifierHighlighting();
      // With new resources pipeline, all highlight usages of resources are found.
      List<HighlightInfo> highlightInfos = myFixture.doHighlighting();
      assertThat(highlightInfos).hasSize(2);
      highlightInfos.forEach(it -> assertThat(it.getSeverity()).isEqualTo(HighlightInfoType.ELEMENT_UNDER_CARET_SEVERITY));
      List<String> getTextList = ContainerUtil.map(highlightInfos, HighlightInfo::getText);
      assertThat(getTextList).containsExactlyElementsIn(Array.of("foo", "@string/foo"));

      // b/139262116: manually commit the Document and clear some caches in an attempt to reduce flakiness of this test.
      myFixture.type('X');
      dispatchEvents();
      ResolveCache.getInstance(getProject()).clearCache(myFixture.getFile().isPhysical());
      PsiManager.getInstance(getProject()).dropPsiCaches();
      PsiDocumentManager.getInstance(getProject()).commitDocument(myFixture.getEditor().getDocument());
      dispatchEvents();
      try {
        AndroidTestUtils.waitForResourceRepositoryUpdates(myFacet);
      }
      catch (InterruptedException | TimeoutException ignore) {
      }

      highlightInfos = myFixture.doHighlighting();
      assertThat(highlightInfos).hasSize(2);
      List<Pair<HighlightSeverity, String>> severities =
        ContainerUtil.map(highlightInfos, it -> new Pair<>(it.getSeverity(), it.getText()));
      assertThat(severities).containsExactly(
        Pair.create(HighlightInfoType.ELEMENT_UNDER_CARET_SEVERITY, "fXoo"),
        Pair.create(HighlightSeverity.ERROR, "@string/foo"));
    });
  }

  public void dispatchEvents() {
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
  }

  private void doCreateValueResourceFromUsage(VirtualFile virtualFile) {
    myFixture.configureFromExistingVirtualFile(virtualFile);
    List<HighlightInfo> infos = myFixture.doHighlighting();
    CodeInsightTestFixtureImpl.waitForUnresolvedReferencesQuickFixesUnderCaret(myFixture.getFile(), myFixture.getEditor());
    List<IntentionAction> actions = new ArrayList<>();

    for (HighlightInfo info : infos) {
      info.findRegisteredQuickFix((descriptor, range) -> {
        if (descriptor.getAction() instanceof CreateValueResourceQuickFix) {
          actions.add(descriptor.getAction());
        }
        return null;
      });
    }
    assertThat(actions.size()).isEqualTo(1);

    WriteCommandAction.runWriteCommandAction(getProject(), () -> actions.get(0).invoke(getProject(), myFixture.getEditor(), myFixture.getFile()));
  }

  private void doTestJavaCompletion(@NotNull String aPackage) throws Throwable {
    // TODO: Kill getTestName, make test classes specify the golden file explicitly.
    String fileName = getTestName(false) + ".java";
    VirtualFile file = copyFileToProject(fileName, "src/" + aPackage.replace('/', '.') + '/' + fileName);
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.complete(CompletionType.BASIC);
    myFixture.checkResultByFile(MY_TEST_FOLDER + '/' + getTestName(false) + "_after.java");
  }

  private void doTestNamespaceCompletion(@NotNull String... extraNamespaces)
    throws IOException {
    // TODO: Kill getTestName, make test classes specify the golden file explicitly.
    VirtualFile file = copyFileToProject(getTestName(true) + ".xml");
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.complete(CompletionType.BASIC);
    List<String> variants = myFixture.getLookupElementStrings();
    assertThat(variants).isNotNull();
    List<String> expectedVariants = new ArrayList<>();

    expectedVariants.add(SdkConstants.ANDROID_URI);
    expectedVariants.add(SdkConstants.TOOLS_URI);
    expectedVariants.add(SdkConstants.AUTO_URI);

    expectedVariants.addAll(Arrays.asList(extraNamespaces));

    Collections.sort(expectedVariants);
    assertThat(variants).isEqualTo(expectedVariants);
  }

  /**
   * Loads file, invokes code completion at &lt;caret&gt; marker and verifies the resulting completion variants as strings.
   */
  private void doTestCompletionVariants(@NotNull String fileName, @NotNull String... variants) throws Throwable {
    List<String> lookupElementStrings = getCompletionElements(fileName);
    assertThat(lookupElementStrings).isNotNull();
    assertThat(lookupElementStrings).containsExactlyElementsIn(variants);
  }

  private List<String> getCompletionElements(@NotNull String fileName) throws IOException, InterruptedException, TimeoutException {
    VirtualFile file = copyFileToProject(fileName);
    waitForResourceRepositoryUpdates();
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.complete(CompletionType.BASIC);
    return myFixture.getLookupElementStrings();
  }

  private void doTestHighlighting() throws Throwable {
    // TODO: Kill getTestName, make test classes specify the golden file explicitly.
    String sourceFile = getTestName(true) + ".xml";
    String destinationFile = getPathToCopy(CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, sourceFile));
    doTestHighlighting(sourceFile, destinationFile);
  }

  /**
   * Creates a virtual file from {@code fileName} and calls {@code doTestHighlighting(VirtualFile virtualFile)} passing it as a parameter.
   */
  private void doTestHighlighting(@NotNull String fileName) throws Throwable {
    doTestHighlighting(copyFileToProject(fileName));
  }

  /**
   * Creates a virtual file {@code projectFile} from {@code fileName} and calls {@code doTestHighlighting(VirtualFile virtualFile)} passing
   * it as a parameter.
   */
  private void doTestHighlighting(@NotNull String sourceFile, @NotNull String projectFile) throws Throwable {
    doTestHighlighting(copyFileToProject(sourceFile, projectFile));
  }

  /**
   * Loads a virtual file and checks whether result of highlighting correspond to XML-like markers left in it. Format of the markers is best
   * described by an example, check the usages of the function to find out.
   */
  private void doTestHighlighting(@NotNull VirtualFile virtualFile) throws Throwable {
    myFixture.configureFromExistingVirtualFile(virtualFile);
    myFixture.checkHighlighting(true, false, false);
  }

  private void doTestJavaHighlighting(String aPackage) throws Throwable {
    // TODO: Kill getTestName, make test classes specify the golden file explicitly.
    String fileName = getTestName(false) + ".java";
    VirtualFile virtualFile = copyFileToProject(fileName, "src/" + aPackage.replace('.', '/') + '/' + fileName);
    myFixture.configureFromExistingVirtualFile(virtualFile);
    myFixture.checkHighlighting(true, false, false);
  }

  private void doTestCompletion() throws Throwable {
    doTestCompletion(true);
  }

  private void doTestCompletion(boolean lowercaseFirstLetter) throws Throwable {
    // TODO: Kill getTestName, make test classes specify the golden file explicitly.
    toTestCompletion(getTestName(lowercaseFirstLetter) + ".xml", getTestName(lowercaseFirstLetter) + "_after.xml");
  }

  /**
   * Loads first file, puts caret on the &lt;caret&gt; marker, invokes code completion. If running the code completion results in returning
   * only one completion variant, chooses it to complete code at the caret.
   */
  private void toTestCompletion(String fileBefore, String fileAfter) throws Throwable {
    VirtualFile file = copyFileToProject(fileBefore);
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.complete(CompletionType.BASIC);
    myFixture.checkResultByFile(MY_TEST_FOLDER + '/' + fileAfter);
  }

  private void doTestSpellcheckerQuickFixes() throws IOException {
    //noinspection unchecked
    myFixture.enableInspections(SpellCheckingInspection.class);
    // TODO: Kill getTestName, make test classes specify the golden file explicitly.
    VirtualFile virtualFile = copyFileToProject(getTestName(true) + ".xml");
    myFixture.configureFromExistingVirtualFile(virtualFile);
    List<IntentionAction> fixes = highlightAndFindQuickFixes(null);
    assertThat(fixes.size()).isEqualTo(2);
    assertThat(((QuickFixWrapper)fixes.get(0)).getFix()).isInstanceOf(RenameTo.class);
    assertThat(((QuickFixWrapper)fixes.get(1)).getFix()).isInstanceOf(SaveTo.class);
  }

  private VirtualFile copyFileToProject(String path) throws IOException {
    return copyFileToProject(path, getPathToCopy(path));
  }

  private VirtualFile copyFileToProject(String from, String to) throws IOException {
    return myFixture.copyFileToProject(MY_TEST_FOLDER + '/' + from, to);
  }

  private List<IntentionAction> highlightAndFindQuickFixes(Class<?> aClass) {
    List<HighlightInfo> infos = myFixture.doHighlighting();
    CodeInsightTestFixtureImpl.waitForUnresolvedReferencesQuickFixesUnderCaret(myFixture.getFile(), myFixture.getEditor());
    List<IntentionAction> actions = new ArrayList<>();

    for (HighlightInfo info : infos) {
      info.findRegisteredQuickFix((descriptor, range) -> {
        if (aClass == null || descriptor.getAction().getClass() == aClass) {
          actions.add(descriptor.getAction());
        }
        return null;
      });
    }
    return actions;
  }
}
