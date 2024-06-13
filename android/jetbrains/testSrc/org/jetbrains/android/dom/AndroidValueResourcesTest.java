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

import static com.android.tools.idea.testing.AndroidGradleTestUtilsKt.createAndroidProjectBuilderForDefaultTestProjectStructure;
import static com.android.tools.idea.testing.AndroidTestUtils.getOffsetForWindow;
import static com.android.tools.idea.testing.JavaModuleModelBuilder.getRootModuleBuilder;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.android.SdkConstants;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceReference;
import com.android.resources.ResourceType;
import com.android.test.testutils.TestUtils;
import com.android.tools.idea.gradle.model.IdeAndroidProjectType;
import com.android.tools.idea.res.psi.ResourceReferencePsiElement;
import com.android.tools.idea.testing.AndroidGradleTestUtilsKt;
import com.android.tools.idea.testing.AndroidModuleDependency;
import com.android.tools.idea.testing.AndroidModuleModelBuilder;
import com.android.tools.idea.testing.AndroidProjectRule;
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
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.FoldingModel;
import com.intellij.openapi.editor.impl.ImaginaryEditor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
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
import com.intellij.testFramework.DumbModeTestUtils;
import com.intellij.testFramework.EdtRule;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.RunsInEdt;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.intellij.util.containers.ContainerUtil;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import javaslang.collection.Array;
import kotlin.Unit;
import org.jetbrains.android.dom.inspections.AndroidDomInspection;
import org.jetbrains.android.dom.inspections.AndroidElementNotAllowedInspection;
import org.jetbrains.android.dom.inspections.AndroidUnknownAttributeInspection;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

/**
 * Tests for code editor features when working with resources under res/values.
 *
 * @see AndroidNamespacedValueResourcesDomTest
 */
@RunWith(JUnit4.class)
@RunsInEdt
public class AndroidValueResourcesTest {
  private String MY_TEST_FOLDER = "dom/resources";

  private final List<AndroidModuleDependency> moduleDependencyList =
    ImmutableList.of(new AndroidModuleDependency(":lib", "debug"));

  private final AndroidModuleModelBuilder appModuleBuilder = new AndroidModuleModelBuilder(
    /* gradlePath= */ ":app",
    /* gradleVersion= */ null,
    /* agpVersion= */ null,
    /* selectedBuildVariant= */ "debug",
    createAndroidProjectBuilderForDefaultTestProjectStructure(IdeAndroidProjectType.PROJECT_TYPE_APP, "p1.p2")
      .withAndroidModuleDependencyList((unused1, unused2) -> moduleDependencyList)
  );

  private final AndroidModuleModelBuilder libModuleBuilder = new AndroidModuleModelBuilder(
    /* gradlePath= */ ":lib",
    /* gradleVersion= */ null,
    /* agpVersion= */ null,
    /* selectedBuildVariant= */ "debug",
    /* projectBuilder= */ createAndroidProjectBuilderForDefaultTestProjectStructure(IdeAndroidProjectType.PROJECT_TYPE_LIBRARY, "p1.p2.lib"));

  private static Unit createSourceRoots(File dir) {
    assertThat((new File(dir, "app/src")).mkdirs()).isTrue();
    assertThat((new File(dir, "app/res")).mkdirs()).isTrue();
    assertThat((new File(dir, "lib/src")).mkdirs()).isTrue();
    assertThat((new File(dir, "lib/res")).mkdirs()).isTrue();
    return Unit.INSTANCE;
  }

  private final AndroidProjectRule androidProjectRule =
    AndroidProjectRule.withAndroidModels(
      AndroidValueResourcesTest::createSourceRoots,
      getRootModuleBuilder(),
      appModuleBuilder,
      libModuleBuilder
    ).initAndroid(true);

  @Rule
  public final RuleChain ruleChain = RuleChain.outerRule(androidProjectRule).around(new EdtRule());

  @Rule
  public final TestName nameRule = new TestName();

  private CodeInsightTestFixture myFixture;
  private Project myProject;
  private AndroidFacet myFacet;

  @Before
  public void setUp() {
    myFixture = androidProjectRule.getFixture();
    myFixture.setTestDataPath(TestUtils.resolveWorkspacePath("tools/adt/idea/android/testData").toString());

    myProject = androidProjectRule.getProject();
    myFacet = AndroidFacet.getInstance(myFixture.getModule());

    AndroidGradleTestUtilsKt.gradleModule(myProject, ":app");
    AndroidGradleTestUtilsKt.gradleModule(myProject, ":lib");

    myFixture.enableInspections(AndroidDomInspection.class,
                                AndroidUnknownAttributeInspection.class,
                                AndroidElementNotAllowedInspection.class);

    myFixture.copyFileToProject(SdkConstants.FN_ANDROID_MANIFEST_XML, SdkConstants.FN_ANDROID_MANIFEST_XML);
    myFixture.copyDirectoryToProject("dom/res", "app/res");
    myFixture.copyDirectoryToProject("dom/res", "lib/res");
  }

  private String getPathToCopy(String testFileName) {
    return "app/res/values/" + testFileName;
  }

  private String getTestName(boolean lowercaseFirstLetter) {
    if (lowercaseFirstLetter) {
      return nameRule.getMethodName();
    } else {
      String name = nameRule.getMethodName();
      return CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_CAMEL, nameRule.getMethodName());
    }
  }

  private void waitForResourceRepositoryUpdates() {
    try {
      AndroidTestUtils.waitForResourceRepositoryUpdates(myFacet, 2, TimeUnit.SECONDS);
    } catch (InterruptedException | TimeoutException e) {
      Assert.fail("Exception while waiting for resource repository updates: " + e);
    }
  }

  @Test
  public void macroTagHighlighting() {
    PsiFile file = myFixture.addFileToProject("app/res/values/values.xml",
                                              "<resources>\n" +
                                              "  <macro name=\"foo\">@string/bar</macro>\n" +
                                              "  <string name=\"bar\">bar</string>\n" +
                                              "  <string name=\"otherbar\">bar</string>\n" +
                                              "  <color name=\"colorbar\">#123456</color>\n" +
                                              "</resources>");
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
    myFixture.checkHighlighting();
  }

  @Test
  public void macroTagStyleAttributeHighlighting() {
    PsiFile file = myFixture.addFileToProject("app/res/values/values.xml",
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

  @Test
  public void stringArrayHighlighting() {
    PsiFile file = myFixture.addFileToProject("app/res/values/strings.xml",
                                              "<resources>\n" +
                                              "    <string-array name=\"foo\" translatable=\"false\"/>\n" +
                                              "</resources>");
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
    myFixture.checkHighlighting();
  }

  @Test
  public void stringArrayCompletion() {
    PsiFile file = myFixture.addFileToProject("app/res/values/strings.xml",
                                              "<resources>\n" +
                                              "    <string-array name=\"foo\" <caret>/>\n" +
                                              "</resources>");
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
    myFixture.completeBasic();
    List<String> lookupElementStrings = myFixture.getLookupElementStrings();
    assertThat(lookupElementStrings).contains("translatable");
  }

  @Test
  public void htmlTags() {
    doTestCompletionVariants("htmlTags.xml", "b", "i", "u");
  }

  @Test
  public void parentStylesCompletion() {
    doTestCompletionVariants("styles_parent.xml", "Parent", "Parent2", "AppTheme");
  }

  @Test
  public void parentStyleReferenceCompletion() {
    doTestCompletionVariants("styles_parent_reference.xml", "@style/Parent", "@style/Parent2", "@style/AppTheme", "@style/NoXxxx",
                             "@style/style1");
  }

  /** Checks the completion of parent styles when the attribute is empty */
  @Test
  public void parentStylesEmptyCompletion() {
    doTestCompletionVariants("styles_parent_empty.xml", "android:", "Parent", "AppTheme", "style1");
  }

  /** Checks the completion of parent styles when the attribute is only "@" */
  @Test
  public void parentStylesEmptyReferenceCompletion() {
    doTestCompletionVariants("styles_parent_empty_reference.xml", "@android:", "@style/Parent", "@style/AppTheme", "@style/style1");
  }

  @Test
  public void styles1() {
    doTestCompletionVariants("styles1.xml", "@drawable/picture1", "@drawable/picture2", "@drawable/picture3");
  }

  @Test
  public void styles2() {
    VirtualFile file = copyFileToProject("styles2.xml");
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.complete(CompletionType.BASIC);
    myFixture.type('\n');
    myFixture.checkResultByFile(MY_TEST_FOLDER + '/' + "styles2_after.xml");
  }

  @Test
  public void styles3() {
    doTestCompletionVariants("styles3.xml", "normal", "bold", "italic");
  }

  @Test
  public void stylesHighlighting() {
    doTestHighlighting("styles4.xml");
  }

  @Test
  public void attrFormatCompletion() {
    toTestCompletion("attrs1.xml", "attrs1_after.xml");
  }

  @Test
  public void declareStyleableNameCompletion() {
    copyFileToProject("LabelView.java", "app/src/p1/p2/LabelView.java");
    doTestCompletionVariants("attrs2.xml", "LabelView");
  }

  @Test
  public void declareStyleableNameHighlighting() {
    copyFileToProject("LabelView.java", "app/src/p1/p2/LabelView.java");
    doTestHighlighting("attrs3.xml");
  }

  @Test
  public void declareStyleableNameNavigation1() {
    copyFileToProject("LabelView.java", "app/src/p1/p2/LabelView.java");
    VirtualFile file = copyFileToProject("attrs4.xml");
    myFixture.configureFromExistingVirtualFile(file);

    PsiElement[] targets =
      GotoDeclarationAction.findAllTargetElements(myProject, myFixture.getEditor(), myFixture.getCaretOffset());
    assertThat(targets).isNotNull();
    assertThat(targets.length).isEqualTo(1);
    PsiElement targetElement = targets[0];
    // A styleable only refers to the resource, not the class that may not exist.
    assertThat(targetElement).isInstanceOf(XmlAttributeValue.class);
    assertThat(targetElement.getText()).isEqualTo("\"TextView\"");
  }

  @Test
  public void declareStyleableNameNavigation2() {
    copyFileToProject("LabelView.java", "app/src/p1/p2/LabelView.java");
    VirtualFile file = copyFileToProject("attrs5.xml");
    myFixture.configureFromExistingVirtualFile(file);

    PsiElement[] targets =
      GotoDeclarationAction.findAllTargetElements(myProject, myFixture.getEditor(), myFixture.getCaretOffset());
    // In the new resources pipeline, a styleable only refers to the resource, not the class that may not exist.
    assertThat(targets).hasLength(3);
    for (PsiElement target : targets) {
      assertThat(target).isInstanceOf(XmlAttributeValue.class);
    }
    List<String> getTextList = ContainerUtil.map(targets, PsiElement::getText);
    assertThat(getTextList).containsExactlyElementsIn(Array.of("\"LabelView\"", "\"LabelView\"", "\"LabelView\""));
    List<String> containingFileList = ContainerUtil.map(targets, it -> it.getContainingFile().getName());
    assertThat(containingFileList).containsExactlyElementsIn(Array.of("attrs5.xml", "attrs.xml", "attrs.xml"));
  }

  @Test
  public void resourceTypeCompletion() {
    // Be careful updating this. "declare-styleable" or "integer-array" is not recognized. "styleable" crashes aapt2.
    doTestCompletionVariants("resourceTypeCompletion.xml",
                             "drawable", "dimen", "bool", "color", "plurals", "string", "raw", "integer", "menu", "transition",
                             "fraction", "layout", "navigation", "mipmap", "array", "interpolator", "xml", "style", "id", "anim", "attr",
                             "animator", "font");
  }

  @Test
  public void styles5() {
    toTestCompletion("styles5.xml", "styles5_after.xml");
  }

  @Test
  public void styles6() {
    toTestCompletion("styles5.xml", "styles5_after.xml");
  }

  @Test
  public void styles7() {
    toTestCompletion("styles5.xml", "styles5_after.xml");
  }

  @Test
  public void styles8() {
    toTestCompletion("styles5.xml", "styles5_after.xml");
  }

  @Test
  public void styles9() {
    toTestCompletion("styles5.xml", "styles5_after.xml");
  }

  @Test
  public void styles10() {
    doTestHighlighting("styles10.xml");
  }

  @Test
  public void stylesAttrNameWithoutPrefix() {
    VirtualFile file = copyFileToProject(getTestName(true) + ".xml");
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.complete(CompletionType.BASIC);
    myFixture.type('\n');
    myFixture.checkResultByFile(MY_TEST_FOLDER + '/' + getTestName(true) + "_after.xml");
  }

  @Test
  public void publicTagHighlighting() {
    VirtualFile file = copyFileToProject("public_highlighting.xml", "lib/res/values/public.xml");
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.checkHighlighting();
  }

  @Test
  public void publicTagCompletion() {
    VirtualFile file = copyFileToProject("public.xml", "lib/res/values/public.xml");
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.completeBasic();
    myFixture.checkResultByFile(MY_TEST_FOLDER + '/' + "public_after.xml");
  }

  @Test
  public void publicTagAppModuleCompletion() {
    VirtualFile file = copyFileToProject("public.xml", "app/res/values/public.xml");
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.completeBasic();
    // In app module, the completion does not work, the file should be unchanged.
    myFixture.checkResultByFile(MY_TEST_FOLDER + '/' + "public.xml");
  }

  @Test
  public void publicTagAttributeValueCompletion() {
    // Resources in app module:
    myFixture.addFileToProject(
      "app/res/values/strings.xml",
      "<resources>\n" +
      "  <string name=\"foo\">foo</string>\n" +
      "  <color name=\"colorfoo\">#123456</color>\n" +
      "</resources>").getVirtualFile();
    // Resources in lib module:
    myFixture.addFileToProject(
      "lib/res/values/strings.xml",
      "<resources>\n" +
      "  <string name=\"bar\">bar</string>\n" +
      "  <string name=\"otherbar\">bar</string>\n" +
      "  <color name=\"colorbar\">#123456</color>\n" +
      "</resources>").getVirtualFile();

    //Check that all resources are present in code completion for 'name' attribute.
    VirtualFile file = myFixture.addFileToProject(
      "lib/res/values/public.xml",
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

  @Test
  public void overlayableTagCompletion() {
    toTestCompletion("overlayable.xml", "overlayable_after.xml");
  }

  @Test
  public void overlayable() {
    // Note that the expected
    doTestHighlighting("overlayable_example.xml");
  }


  @Test
  public void policyTagCompletion() {
    toTestCompletion("policy.xml", "policy_after.xml");
  }

  @Test
  public void moreTypes() {
    doTestHighlighting("moreTypes.xml");
  }

  @Test
  public void bool() {
    toTestCompletion("bool.xml", "bool_after.xml");
  }

  @Test
  public void bool1() {
    toTestCompletion("bool1.xml", "bool1_after.xml");
  }

  @Test
  public void integer() {
    doTestCompletionVariants("integer.xml", "integer", "integer-array");
  }

  @Test
  public void integerArray() {
    toTestCompletion("integerArray.xml", "integerArray_after.xml");
  }

  @Test
  public void itemArray() {
    doTestHighlighting("itemArray.xml");
  }

  @Test
  public void array() {
    toTestCompletion("array.xml", "array_after.xml");
  }

  @Test
  public void plurals() {
    doTestCompletion();
  }

  @Test
  public void plurals1() {
    doTestCompletion();
  }

  @Test
  public void plurals2() {
    doTestCompletionVariants(getTestName(true) + ".xml", "zero", "one", "two", "few", "many", "other");
  }

  @Test
  public void plurals3() {
    doTestHighlighting();
  }

  @Test
  public void intResourceReference() {
    myFixture.copyFileToProject(MY_TEST_FOLDER + "/intResReference.xml", "app/res/layout/main.xml");
    myFixture.copyFileToProject(MY_TEST_FOLDER + "/intbool.xml", "app/res/values/values.xml");
    myFixture.testCompletion("app/res/layout/main.xml", MY_TEST_FOLDER + "/intResReference_after.xml");
  }

  @Test
  public void boolResourceReference() {
    myFixture.copyFileToProject(MY_TEST_FOLDER + "/boolResReference.xml", "app/res/layout/main.xml");
    myFixture.copyFileToProject(MY_TEST_FOLDER + "/intbool.xml", "app/res/values/values.xml");
    myFixture.testCompletion("app/res/layout/main.xml", MY_TEST_FOLDER + "/boolResReference_after.xml");
  }

  @Test
  public void boolResourceReferenceDumbMode() {
    myFixture.copyFileToProject(MY_TEST_FOLDER + "/boolResReference.xml", "app/res/layout/main.xml");
    myFixture.copyFileToProject(MY_TEST_FOLDER + "/intbool.xml", "app/res/values/values.xml");
    // Completion providers don't actually kick in in dumb mode, but does outside of dumb mode.
    DumbModeTestUtils.runInDumbModeSynchronously(myProject, () -> {
      myFixture.testCompletion("app/res/layout/main.xml", MY_TEST_FOLDER + "/boolResReference.xml");
    });
    myFixture.testCompletion("app/res/layout/main.xml", MY_TEST_FOLDER + "/boolResReference_after.xml");
  }

  @Test
  public void resourceReferenceAsValueHighlighting() {
    doTestHighlighting();
  }

  @Test
  public void nameValidation() {
    doTestHighlighting("nameValidation.xml");
  }

  @Test
  public void missingType() {
    doTestHighlighting("missingType.xml");
  }

  @Test
  public void resourceReferenceAsValueCompletion1() {
    doTestCompletion();
  }

  @Test
  public void resourceReferenceAsValueCompletion2() {
    doTestCompletion();
  }

  @Test
  public void resourceReferenceAsValueCompletion3() {
    doTestCompletionVariants(getTestName(true) + ".xml", "true", "false");
  }

  @Test
  public void resourceReferenceAsValueCompletion4() {
    doTestCompletionVariants(getTestName(true) + ".xml");
  }

  @Test
  public void drawableResourceReference() {
    VirtualFile file = copyFileToProject(getTestName(true) + ".xml", "app/res/layout/main.xml");
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.complete(CompletionType.BASIC);
    List<String> lookupElements = myFixture.getLookupElementStrings();
    assertThat(lookupElements).containsAllOf("@android:", "@color/color1", "@color/color2", "@drawable/picture1");
    // mipmap won't be offered as an option since there are not mipmap resources
    assertThat(lookupElements).doesNotContain("@mipmap/icon");

    // Add a mipmap to resources and expect for it to be listed
    myFixture.copyFileToProject(MY_TEST_FOLDER + "/icon.png", "app/res/mipmap/icon.png");
    waitForResourceRepositoryUpdates();
    myFixture.complete(CompletionType.BASIC);
    assertThat(myFixture.getLookupElementStrings()).containsAllOf("@android:", "@color/color1", "@drawable/picture1", "@mipmap/icon");
  }

  @Test
  public void parentStyleReference() {
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

  @Test
  public void createResourceFromUsage() {
    VirtualFile virtualFile = copyFileToProject("createResourceFromUsage.xml", "app/res/values/drawables.xml");
    myFixture.configureFromExistingVirtualFile(virtualFile);

    myFixture.doHighlighting();
    CodeInsightTestFixtureImpl.waitForUnresolvedReferencesQuickFixesUnderCaret(myFixture.getFile(), myFixture.getEditor());

    IntentionAction action = myFixture.findSingleIntention("Create drawable value resource");
    WriteCommandAction.runWriteCommandAction(myProject, () -> action.invoke(myProject, myFixture.getEditor(), myFixture.getFile()));

    myFixture.checkResultByFile(MY_TEST_FOLDER + "/createResourceFromUsage_after.xml", true);
  }

  @Test
  public void javaCompletion1() {
    copyFileToProject("value_resources.xml", "app/res/values/value_resources.xml");
    String fileName = getTestName(false) + ".java";
    VirtualFile file = copyFileToProject(fileName, "app/src/" + "p1.p2".replace('/', '.') + '/' + fileName);
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.complete(CompletionType.BASIC);
    myFixture.type('\n');
    myFixture.checkResultByFile(MY_TEST_FOLDER + '/' + getTestName(false) + "_after.java");
  }

  @Test
  public void javaCompletion2() {
    copyFileToProject("value_resources.xml", "app/res/values/value_resources.xml");
    doTestJavaCompletion("p1.p2");
  }

  @Test
  public void javaCompletion3() {
    copyFileToProject("value_resources.xml", "app/res/values/value_resources.xml");
    doTestJavaCompletion("p1.p2");
  }

  @Test
  public void javaCompletion4() {
    copyFileToProject("value_resources.xml", "app/res/values/value_resources.xml");
    doTestJavaCompletion("p1.p2");
  }

  @Test
  public void javaHighlighting() {
    copyFileToProject("value_resources.xml", "app/res/values/value_resources.xml");
    doTestJavaHighlighting("p1.p2");
  }

  @Test
  public void fraction() {
    toTestCompletion("bool.xml", "bool_after.xml");
  }

  @Test
  public void translatableAttributeCompletion() {
    toTestCompletion("strings_translatable_attr.xml", "strings_translatable_attr_after.xml");
  }

  @Test
  public void translatableAttributeCompletionDumbMode() {
    DumbModeTestUtils.runInDumbModeSynchronously(myProject, () -> {
      toTestCompletion("strings_translatable_attr.xml", "strings_translatable_attr.xml");
    });
    toTestCompletion("strings_translatable_attr.xml", "strings_translatable_attr_after.xml");
  }

  @Test
  public void translatableFalseCompletion() {
    toTestCompletion("strings_translatable_false.xml", "strings_translatable_false_after.xml");
  }

  @Test
  public void translatableTrueCompletion() {
    toTestCompletion("strings_translatable_true.xml", "strings_translatable_true_after.xml");
  }

  @Test
  public void formattedAttributeCompletion() {
    toTestCompletion("strings_formatted_attr.xml", "strings_formatted_attr_after.xml");
  }

  @Test
  public void formattedFalseCompletion() {
    toTestCompletion("strings_formatted_false.xml", "strings_formatted_false_after.xml");
  }

  @Test
  public void inlineResourceField() {
    copyFileToProject("value_resources.xml", "app/res/values/value_resources.xml");
    VirtualFile virtualFile = copyFileToProject(getTestName(false) + ".java", "app/src/p1/p2/" + getTestName(false) + ".java");
    myFixture.configureFromExistingVirtualFile(virtualFile);
    assertThrows(CommonRefactoringUtil.RefactoringErrorHintException.class, () -> {
      myFixture.testAction(new InlineAction());
    });
    myFixture.checkResultByFile(MY_TEST_FOLDER + '/' + getTestName(false) + ".java", true);
  }

  @Test
  public void javaCreateFromUsage() {
    VirtualFile virtualFile = copyFileToProject("JavaCreateFromUsage.java", "app/src/p1/p2/JavaCreateFromUsage.java");
    myFixture.configureFromExistingVirtualFile(virtualFile);

    myFixture.doHighlighting();
    CodeInsightTestFixtureImpl.waitForUnresolvedReferencesQuickFixesUnderCaret(myFixture.getFile(), myFixture.getEditor());

    IntentionAction action = myFixture.findSingleIntention("Create drawable value resource");
    WriteCommandAction.runWriteCommandAction(myProject, () -> action.invoke(myProject, myFixture.getEditor(), myFixture.getFile()));

    myFixture.checkResultByFile(
      "app/res/values/drawables.xml",
      MY_TEST_FOLDER + "/javaCreateFromUsage_drawables_after.xml",
      true);
  }

  @Test
  public void javaCreateFromUsage1() {
    myFixture.copyFileToProject(MY_TEST_FOLDER + "/intbool.xml", "app/res/values/values.xml");

    VirtualFile virtualFile = copyFileToProject("JavaCreateFromUsage1.java", "app/src/p1/p2/JavaCreateFromUsage1.java");
    myFixture.configureFromExistingVirtualFile(virtualFile);

    myFixture.doHighlighting();
    CodeInsightTestFixtureImpl.waitForUnresolvedReferencesQuickFixesUnderCaret(myFixture.getFile(), myFixture.getEditor());

    IntentionAction action = myFixture.findSingleIntention("Create bool value resource");
    WriteCommandAction.runWriteCommandAction(myProject, () -> action.invoke(myProject, myFixture.getEditor(), myFixture.getFile()));

    myFixture.checkResultByFile(
      "app/res/values/bools.xml",
      MY_TEST_FOLDER + "/javaCreateFromUsage1_bools_after.xml",
      true);
  }

  /**
   * Test quickfix where the R class is from a dependency, not the main module. This is a proxy for testing Bazel projects where
   * the main "workspace" module doesn't have a manifest or resources. There are instead some resource-only modules which are
   * dependencies of the main module.
   */
  @Test
  public void javaCreateFromUsageResourcesInDeps() {
    final VirtualFile virtualFile =
      copyFileToProject("JavaCreateFromUsageResourcesInDeps.java", "app/src/p1/p2/JavaCreateFromUsageResourcesInDeps.java");
    myFixture.configureFromExistingVirtualFile(virtualFile);

    myFixture.doHighlighting();
    CodeInsightTestFixtureImpl.waitForUnresolvedReferencesQuickFixesUnderCaret(myFixture.getFile(), myFixture.getEditor());

    IntentionAction action = myFixture.findSingleIntention("Create string value resource");
    WriteCommandAction.runWriteCommandAction(myProject, () -> action.invoke(myProject, myFixture.getEditor(), myFixture.getFile()));

    myFixture.checkResultByFile(
      "lib/res/values/strings.xml",
      MY_TEST_FOLDER + "/javaCreateFromUsageResourcesInDeps_strings_after.xml",
      true);
  }

  @Test
  public void attrReferenceCompletion() {
    doTestCompletion();
  }

  @Test
  public void attrReferenceHighlighting() {
    // New resources pipeline does not treat ATTRs different to other ResourceTypes, having an incorrect type should be surfaced in a lint
    // check, not reference resolution.
    doTestHighlighting();
  }

  @Test
  public void toolsBaseAttribute() {
    doTestHighlighting();
  }

  @Test
  public void localStyleItemReferenceCompletion() {
    copyFileToProject("localStyleItemReference_layout.xml", "app/res/layout/myLayout.xml");
    doTestCompletion();
  }

  @Test
  public void localStyleItemReferenceHighlighting() {
    copyFileToProject("localStyleItemReference_layout.xml", "app/res/layout/myLayout.xml");
    doTestHighlighting();
  }

  @Test
  public void androidPrefixInsertHandler() {
    String fileName = getTestName(true) + ".xml";

    myFixture.configureFromExistingVirtualFile(copyFileToProject(fileName));
    myFixture.complete(CompletionType.BASIC);
    assertThat(myFixture.getLookupElementStrings().get(0)).isEqualTo("@android:");
    myFixture.type('\n');
    // Assert another completion was started automatically and that all framework resources are present.
    assertThat(myFixture.getLookupElements().length).isGreaterThan(100);
  }

  @Test
  public void navigationInPlatformXml1_NavigateFromParentAttr() {
    VirtualFile themes_holo =
      LocalFileSystem.getInstance().findFileByPath(TestUtils.resolvePlatformPath("data/res/values/themes_holo.xml").toString());
    assertThat(themes_holo).isNotNull();
    VirtualFile themes =
      LocalFileSystem.getInstance().findFileByPath(TestUtils.resolvePlatformPath("data/res/values/themes.xml").toString());
    assertThat(themes).isNotNull();

    // In themes_holo.xml: point to value of "Theme" in the parent attribute on line:
    //     <style name="Theme.Holo.Light" parent="Theme.Light">
    Document themesHoloDocument = FileDocumentManager.getInstance().getDocument(themes_holo);
    ImaginaryEditor editor = new MyImaginaryEditor(myProject, themesHoloDocument);
    int offset = getOffsetForWindow(themesHoloDocument, "<style name=\"Theme.Holo.Light\" parent=\"The|me.Light\">");

    PsiElement[] targets = GotoDeclarationAction.findAllTargetElements(myProject, editor, offset);
    assertThat(targets).isNotNull();
    assertThat(targets.length).isEqualTo(1);
    PsiElement targetElement = targets[0];

    // Goto action should navigate to "Theme" in themes.xml, on line: "<style name="Theme">"
    assertThat(targetElement).isInstanceOf(XmlAttributeValue.class);
    XmlAttributeValue targetAttrValue = (XmlAttributeValue)targetElement;
    assertThat(targetAttrValue.getValue()).isEqualTo("Theme");
    assertThat(((XmlAttribute)targetAttrValue.getParent()).getName()).isEqualTo("name");
    assertThat(((XmlTag)targetAttrValue.getParent().getParent()).getName()).isEqualTo("style");
    assertThat(targetElement.getContainingFile().getVirtualFile()).isEqualTo(themes);
  }

  @Test
  public void navigationInPlatformXml2_NavigateFromNameAttr() {
    VirtualFile themes_holo =
      LocalFileSystem.getInstance().findFileByPath(TestUtils.resolvePlatformPath("data/res/values/themes_holo.xml").toString());
    assertThat(themes_holo).isNotNull();
    VirtualFile themes =
      LocalFileSystem.getInstance().findFileByPath(TestUtils.resolvePlatformPath("data/res/values/themes.xml").toString());
    assertThat(themes).isNotNull();

    // In themes_holo.xml: point to value of "Theme" in the name attribute on line:
    //     <style name="Theme.Holo.NoActionBar">
    Document themesHoloDocument = FileDocumentManager.getInstance().getDocument(themes_holo);
    ImaginaryEditor editor = new MyImaginaryEditor(myProject, themesHoloDocument);
    int offset = getOffsetForWindow(themesHoloDocument, "<style name=\"Th|eme.Holo.NoActionBar\">");

    PsiElement[] targets = GotoDeclarationAction.findAllTargetElements(myProject, editor, offset);
    assertThat(targets).isNotNull();
    assertThat(targets.length).isEqualTo(1);
    PsiElement targetElement = targets[0];

    // Goto action should navigate to "Theme" in themes.xml, on line: "<style name="Theme">"
    assertThat(targetElement).isInstanceOf(XmlAttributeValue.class);
    XmlAttributeValue targetAttrValue = (XmlAttributeValue)targetElement;
    assertThat(targetAttrValue.getValue()).isEqualTo("Theme");
    assertThat(((XmlAttribute)targetAttrValue.getParent()).getName()).isEqualTo("name");
    assertThat(((XmlTag)targetAttrValue.getParent().getParent()).getName()).isEqualTo("style");
    assertThat(targetElement.getContainingFile().getVirtualFile()).isEqualTo(themes);
  }

  @Test
  public void navigationInPlatformXml3() {
    VirtualFile themes_holo =
      LocalFileSystem.getInstance().findFileByPath(TestUtils.resolvePlatformPath("data/res/values/themes_holo.xml").toString());
    assertThat(themes_holo).isNotNull();
    VirtualFile colors_holo =
      LocalFileSystem.getInstance().findFileByPath(TestUtils.resolvePlatformPath("data/res/values/colors_holo.xml").toString());
    assertThat(colors_holo).isNotNull();

    // In themes_holo.xml: point to value of "bright_foreground_holo_light" on line:
    //    <item name="colorForeground">@color/bright_foreground_holo_light</item>
    Document themesHoloDocument = FileDocumentManager.getInstance().getDocument(themes_holo);
    ImaginaryEditor editor = new MyImaginaryEditor(myProject, themesHoloDocument);
    int offset = getOffsetForWindow(themesHoloDocument, "<item name=\"colorForeground\">@color/bright_fore|ground_holo_light</item>");

    PsiElement[] targets =
      GotoDeclarationAction.findAllTargetElements(myProject, editor, offset);
    assertThat(targets).isNotNull();
    assertThat(targets.length).isEqualTo(1);
    PsiElement targetElement = targets[0];

    // Goto action should navigate to "bright_foreground_holo_light" in colors_holo.xml, on line:
    //    <color name="bright_foreground_holo_light">@color/background_holo_dark</color>
    assertThat(targetElement).isInstanceOf(XmlAttributeValue.class);
    XmlAttributeValue targetAttrValue = (XmlAttributeValue)targetElement;
    assertThat(targetAttrValue.getValue()).isEqualTo("bright_foreground_holo_light");
    assertThat(((XmlAttribute)targetAttrValue.getParent()).getName()).isEqualTo("name");
    assertThat(((XmlTag)targetAttrValue.getParent().getParent()).getName()).isEqualTo("color");
    assertThat(targetElement.getContainingFile().getVirtualFile()).isEqualTo(colors_holo);
  }

  @Test
  public void spellchecker1() {
    myFixture.enableInspections(SpellCheckingInspection.class);
    doTestHighlighting();
  }

  @Test
  public void spellchecker2() {
    doTestSpellcheckerQuickFixes();
  }

  @Test
  public void spellchecker3() {
    // In English locale, should highlight typos
    myFixture.enableInspections(SpellCheckingInspection.class);
    VirtualFile file = copyFileToProject("spellchecker3.xml", "app/res/values-en-rUS/spellchecker3.xml");
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.checkHighlighting(true, false, false);
  }

  @Test
  public void spellchecker4() {
    // In non-English locale, should not highlight typos
    myFixture.enableInspections(SpellCheckingInspection.class);
    VirtualFile file = copyFileToProject("spellchecker4.xml", "app/res/values-nb/spellchecker4.xml");
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.checkHighlighting(true, false, false);
  }

  @Test
  public void spellchecker5() {
    // In default locale, with a tools:locale defined to non-English, should not get typos highlighted
    myFixture.enableInspections(SpellCheckingInspection.class);
    VirtualFile file = copyFileToProject("spellchecker5.xml", "app/res/values/spellchecker5.xml");
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.checkHighlighting(true, false, false);
  }

  @Test
  public void spellchecker6() {
    // In default locale, with a tools:locale defined to English, should get typos highlighted
    myFixture.enableInspections(SpellCheckingInspection.class);
    VirtualFile file = copyFileToProject("spellchecker6.xml", "app/res/values/spellchecker6.xml");
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.checkHighlighting(true, false, false);
  }

  @Test
  public void spellNewlines() {
    myFixture.enableInspections(SpellCheckingInspection.class);
    doTestHighlighting();
  }

  @Test
  public void doNotFlagLintXml() {
    // In default locale, with a tools:locale defined to non-English, should not get typos highlighted
    myFixture.enableInspections(SpellCheckingInspection.class);
    VirtualFile file = copyFileToProject("lint.xml", "lint.xml");
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.checkHighlighting(true, false, false);
  }

  @Test
  public void namespaceCompletion() {
    doTestNamespaceCompletion(SdkConstants.XLIFF_URI);
  }

  @Test
  public void attrValidation() {
    // Regression test for https://code.google.com/p/android/issues/detail?id=199247
    // Allow colons in names for attributes
    doTestHighlighting("attrValidation.xml");
  }

  @Test
  public void identifierHighlightingStringName() {
    PsiFile file = myFixture.addFileToProject("app/res/values/strings.xml",
                                              //language=XML
                                              "<resources>" +
                                              "  <string name=\"foo\">foo</string>" +
                                              "  <string name=\"bar\">@string/foo</string>" +
                                              "</resources>");
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
    myFixture.setReadEditorMarkupModel(true);

    IdentifierHighlighterPassFactory.doWithHighlightingEnabled(myProject, myFixture.getProjectDisposable(), () -> {
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
      ResolveCache.getInstance(myProject).clearCache(myFixture.getFile().isPhysical());
      PsiManager.getInstance(myProject).dropPsiCaches();
      PsiDocumentManager.getInstance(myProject).commitDocument(myFixture.getEditor().getDocument());
      dispatchEvents();
      try {
        AndroidTestUtils.waitForResourceRepositoryUpdates(myFacet);
      }
      catch (InterruptedException | TimeoutException ignore) {
      }

      IdentifierHighlighterPassFactory.waitForIdentifierHighlighting();
      highlightInfos = myFixture.doHighlighting();
      assertThat(highlightInfos).hasSize(2);
      List<Pair<HighlightSeverity, String>> severities =
        ContainerUtil.map(highlightInfos, it -> new Pair<>(it.getSeverity(), it.getText()));
      assertThat(severities).containsExactly(
        Pair.create(HighlightInfoType.ELEMENT_UNDER_CARET_SEVERITY, "fXoo"),
        Pair.create(HighlightSeverity.ERROR, "@string/foo"));
    });
  }

  private void dispatchEvents() {
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
  }

  private void doTestJavaCompletion(@NotNull String aPackage) {
    // TODO: Kill getTestName, make test classes specify the golden file explicitly.
    String fileName = getTestName(false) + ".java";
    VirtualFile file = copyFileToProject(fileName, "app/src/" + aPackage.replace('/', '.') + '/' + fileName);
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.complete(CompletionType.BASIC);
    myFixture.checkResultByFile(MY_TEST_FOLDER + '/' + getTestName(false) + "_after.java");
  }

  private void doTestNamespaceCompletion(@NotNull String... extraNamespaces) {
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
  private void doTestCompletionVariants(@NotNull String fileName, @NotNull String... variants) {
    List<String> lookupElementStrings = getCompletionElements(fileName);
    assertThat(lookupElementStrings).isNotNull();
    assertThat(lookupElementStrings).containsExactlyElementsIn(variants);
  }

  private List<String> getCompletionElements(@NotNull String fileName) {
    VirtualFile file = copyFileToProject(fileName);
    waitForResourceRepositoryUpdates();
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.complete(CompletionType.BASIC);
    return myFixture.getLookupElementStrings();
  }

  private void doTestHighlighting() {
    // TODO: Kill getTestName, make test classes specify the golden file explicitly.
    String sourceFile = getTestName(true) + ".xml";
    String destinationFile = getPathToCopy(CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, sourceFile));
    doTestHighlighting(sourceFile, destinationFile);
  }

  /**
   * Creates a virtual file from {@code fileName} and calls {@code doTestHighlighting(VirtualFile virtualFile)} passing it as a parameter.
   */
  private void doTestHighlighting(@NotNull String fileName) {
    doTestHighlighting(copyFileToProject(fileName));
  }

  /**
   * Creates a virtual file {@code projectFile} from {@code fileName} and calls {@code doTestHighlighting(VirtualFile virtualFile)} passing
   * it as a parameter.
   */
  private void doTestHighlighting(@NotNull String sourceFile, @NotNull String projectFile) {
    doTestHighlighting(copyFileToProject(sourceFile, projectFile));
  }

  /**
   * Loads a virtual file and checks whether result of highlighting correspond to XML-like markers left in it. Format of the markers is best
   * described by an example, check the usages of the function to find out.
   */
  private void doTestHighlighting(@NotNull VirtualFile virtualFile) {
    myFixture.configureFromExistingVirtualFile(virtualFile);
    myFixture.checkHighlighting(true, false, false);
  }

  private void doTestJavaHighlighting(String aPackage) {
    // TODO: Kill getTestName, make test classes specify the golden file explicitly.
    String fileName = getTestName(false) + ".java";
    VirtualFile virtualFile = copyFileToProject(fileName, "app/src/" + aPackage.replace('.', '/') + '/' + fileName);
    myFixture.configureFromExistingVirtualFile(virtualFile);
    myFixture.checkHighlighting(true, false, false);
  }

  private void doTestCompletion() {
    doTestCompletion(true);
  }

  private void doTestCompletion(boolean lowercaseFirstLetter) {
    // TODO: Kill getTestName, make test classes specify the golden file explicitly.
    toTestCompletion(getTestName(lowercaseFirstLetter) + ".xml", getTestName(lowercaseFirstLetter) + "_after.xml");
  }

  /**
   * Loads first file, puts caret on the &lt;caret&gt; marker, invokes code completion. If running the code completion results in returning
   * only one completion variant, chooses it to complete code at the caret.
   */
  private void toTestCompletion(String fileBefore, String fileAfter) {
    VirtualFile file = copyFileToProject(fileBefore);
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.complete(CompletionType.BASIC);
    myFixture.checkResultByFile(MY_TEST_FOLDER + '/' + fileAfter);
  }

  private void doTestSpellcheckerQuickFixes() {
    //noinspection unchecked
    myFixture.enableInspections(SpellCheckingInspection.class);
    // TODO: Kill getTestName, make test classes specify the golden file explicitly.
    VirtualFile virtualFile = copyFileToProject(getTestName(true) + ".xml");
    myFixture.configureFromExistingVirtualFile(virtualFile);
    List<IntentionAction> fixes = highlightAndFindQuickFixes(null);
    assertThat(fixes.size()).isEqualTo(2);
    assertThat(QuickFixWrapper.unwrap(fixes.get(0))).isInstanceOf(RenameTo.class);
    assertThat(QuickFixWrapper.unwrap(fixes.get(1))).isInstanceOf(SaveTo.class);
  }

  private VirtualFile copyFileToProject(String path) {
    return copyFileToProject(path, getPathToCopy(path));
  }

  private VirtualFile copyFileToProject(String from, String to) {
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

  /**
   * Allows opening of platform SDK files in an {@link com.intellij.openapi.editor.Editor} for test purposes.
   * <p>
   * The default test editor provided by the fixture requires that an opened file not be read-only. Platform SDK files are read-only when
   * running via bazel locally (ie, not using --config=remote).
   * <p>
   * The platform's {@link ImaginaryEditor} provides almost all of the fake functionality we need, but not quite everything. This derived
   * class provides the missing pieces.
   */
  private static class MyImaginaryEditor extends ImaginaryEditor {

    private final FoldingModel myFoldingModel = Mockito.mock(FoldingModel.class);

    public MyImaginaryEditor(@NotNull Project project, @NotNull Document document) {
      super(project, document);
    }

    @Override
    public @NotNull FoldingModel getFoldingModel() {
      return myFoldingModel;
    }
  }
}
