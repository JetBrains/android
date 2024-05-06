/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.javadoc;

import static com.android.tools.idea.testing.SnaphotComparisonTestHelpersKt.normalizeHtmlForTests;
import static com.android.tools.idea.testing.SnapshotComparisonTestUtilsKt.assertIsEqualToSnapshot;
import static com.google.common.truth.Truth.assertThat;

import com.android.SdkConstants;
import com.android.test.testutils.TestUtils;
import com.android.tools.idea.testing.AndroidProjectRule;
import com.android.tools.idea.testing.EdtAndroidProjectRule;
import com.android.tools.idea.testing.SnapshotComparisonTest;
import com.android.tools.lint.client.api.LintClient;
import com.intellij.codeInsight.documentation.DocumentationManager;
import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.RunsInEdt;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import org.jetbrains.annotations.NotNull;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
@RunsInEdt
public class AndroidJavaDocRendererTest implements SnapshotComparisonTest {
  static {
    LintClient.setClientName(LintClient.CLIENT_STUDIO);
  }

  @Rule
  public TestName name = new TestName();
  @Rule
  public EdtAndroidProjectRule projectRule = new EdtAndroidProjectRule(AndroidProjectRule.withSdk());
  private Project myProject;
  private Module myModule;
  private CodeInsightTestFixture myFixture;

  @Before
  public void setUp() {
    myProject = projectRule.getProject();
    myModule = projectRule.getProjectRule().getModule();
    myFixture = projectRule.getFixture();
    myFixture.setTestDataPath(TestUtils.resolveWorkspacePathUnchecked("tools/adt/idea/android/testData").toString());
    myFixture.copyFileToProject(SdkConstants.FN_ANDROID_MANIFEST_XML, SdkConstants.FN_ANDROID_MANIFEST_XML);
  }

  private void copyStrings() {
    myFixture.copyFileToProject("javadoc/strings/strings.xml", "res/values/strings.xml");
    myFixture.copyFileToProject("javadoc/strings/strings-ta.xml", "res/values-ta/strings.xml");
    myFixture.copyFileToProject("javadoc/strings/strings-zh-rTW.xml", "res/values-zh-rTW/strings.xml");
  }

  private void checkStrings(String fileName, String targetPath) {
    copyStrings();
    checkDoc(fileName, targetPath);
  }

  private void checkDoc(String fileName, String targetName) {
    String doc = generateDoc(fileName, targetName);
    String normalizedDoc = normalizeHtmlForTests(myProject, doc != null ? doc : "");
    assertIsEqualToSnapshot(this, normalizedDoc, "");
  }

  /**
   * Test that the project can fetch documentation at the caret point (which is expected to be set
   * explicitly in the contents of {@code fileName}). {@code javadocConsumer} will be triggered with
   * the actual documentation returned and will be responsible for asserting expected values.
   */
  private String generateDoc(String fileName, String targetName) {
    VirtualFile f = myFixture.copyFileToProject(fileName, targetName);
    myFixture.configureFromExistingVirtualFile(f);
    PsiElement originalElement = myFixture.getFile().findElementAt(myFixture.getEditor().getCaretModel().getOffset());
    assert originalElement != null;
    PsiElement docTargetElement =
      DocumentationManager.getInstance(myProject).findTargetElement(myFixture.getEditor(), myFixture.getFile(), originalElement);
    assert docTargetElement != null;
    DocumentationProvider provider = DocumentationManager.getProviderFromElement(docTargetElement);
    return provider.generateDoc(docTargetElement, originalElement);
  }

  @Test
  public void brokenCustomDrawableJava() {
    // Layout lib cannot render the custom drawable here, so it should show an error.
    myFixture.copyFileToProject("javadoc/drawables/customDrawable.xml", "res/drawable/ic_launcher.xml");
    String doc = generateDoc("/javadoc/drawables/Activity1.java", "src/p1/p2/Activity.java");
    assertThat(doc).contains("Couldn't render");
    assertThat(doc).doesNotContain("render.png");
  }

  @Test
  public void webPDrawableJava() {
    // WebP images need to be rendered by layoutlib, so should show the rendered PNG.
    myFixture.copyFileToProject("javadoc/drawables/ic_launcher.webp", "res/drawable/ic_launcher.webp");
    String doc = generateDoc("/javadoc/drawables/Activity1.java", "src/p1/p2/Activity.java");
    assertThat(doc).contains("render.png");
    assertThat(doc).doesNotContain("Couldn't render");
  }

  @Test
  public void string1Java() {
    checkStrings("/javadoc/strings/Activity1.java", "src/p1/p2/Activity.java");
  }

  @Test
  public void string1Kotlin() {
    checkStrings("/javadoc/strings/Activity1.kt", "src/p1/p2/Activity.kt");
  }

  @Test
  public void string2Java() {
    // Use FlagManagerTest#checkEncoding to get Unicode encoding
    checkStrings("/javadoc/strings/Activity2.java", "src/p1/p2/Activity.java");
  }

  @Test
  public void string2Kotlin() {
    // Use FlagManagerTest#checkEncoding to get Unicode encoding
    checkStrings("/javadoc/strings/Activity2.kt", "src/p1/p2/Activity.kt");
  }

  /**
   * Testing R.stri<caret>ng.app_name
   * <p>
   * There is no custom documentation for the inner R class, so the default Java platform documentation for a Java
   * static final field is used. Loosely test that the parts we expect to be there are present.
   */
  @Test
  public void string3Java() {
    copyStrings();
    String doc = generateDoc("/javadoc/strings/Activity3.java", "src/p1/p2/Activity.java");
    Document html = Jsoup.parseBodyFragment(doc);
    Elements links = html.getElementsByAttributeValue("href", "psi_element://p1.p2");
    assertThat(links).hasSize(1);
    assertThat(links.get(0).getElementsContainingOwnText("p1.p2")).hasSize(1);
    assertThat(html.getElementsContainingOwnText("class")).hasSize(1);
    assertThat(html.getElementsContainingOwnText("string")).hasSize(1);
  }

  @Test
  public void string3Kotlin() {
    copyStrings();
    String doc = generateDoc("/javadoc/strings/Activity3.kt", "src/p1/p2/Activity.kt");
    Document html = Jsoup.parseBodyFragment(doc);
    Elements links = html.getElementsByAttributeValue("href", "psi_element://p1.p2");
    assertThat(links).hasSize(1);
    assertThat(links.get(0).getElementsContainingOwnText("p1.p2")).hasSize(1);
    assertThat(html.getElementsContainingOwnText("class")).hasSize(1);
    assertThat(html.getElementsContainingOwnText("string")).hasSize(1);
  }

  @Test
  public void dimensionsJava() {
    checkDimensions("/javadoc/dimens/Activity1.java", "src/p1/p2/Activity.java");
  }

  @Test
  public void dimensionsKotlin() {
    checkDimensions("/javadoc/dimens/Activity1.kt", "src/p1/p2/Activity.kt");
  }

  public void checkDimensions(String sourcePath, String targetPath) {
    myFixture.copyFileToProject("javadoc/dimens/dimens.xml", "res/values/dimens.xml");
    myFixture.copyFileToProject("javadoc/dimens/dimens-sw720dp.xml", "res/values-sw720dp/dimens.xml");
    myFixture.copyFileToProject("javadoc/dimens/dimens-land.xml", "res/values-land/dimens.xml");
    checkDoc(sourcePath, targetPath);
  }

  @Test
  public void drawablesJava() {
    checkDrawables("/javadoc/drawables/Activity1.java", "src/p1/p2/Activity.java");
  }

  @Test
  public void drawablesKotlin() {
    checkDrawables("/javadoc/drawables/Activity1.kt", "src/p1/p2/Activity.kt");
  }

  public void checkDrawables(String sourcePath, String targetPath) {
    myFixture.copyFileToProject("javadoc/drawables/ic_launcher.png",
                                "res/drawable/ic_launcher.png").getPath();
    myFixture.copyFileToProject("javadoc/drawables/ic_launcher.png",
                                "res/drawable-hdpi/ic_launcher.png").getPath();

    checkDoc(sourcePath, targetPath);
  }

  @Test
  public void stateListDrawablesJava() {
    checkStateListDrawables("/javadoc/drawables/Activity2.java", "src/p1/p2/Activity.java");
  }

  @Test
  public void stateListDrawablesKotlin() {
    checkStateListDrawables("/javadoc/drawables/Activity2.kt", "src/p1/p2/Activity.kt");
  }

  public void checkStateListDrawables(String sourcePath, String targetPath) {
    myFixture.copyFileToProject("javadoc/drawables/selector.xml", "res/drawable/selector.xml");
    myFixture.copyFileToProject("javadoc/drawables/ic_launcher.png",
                                "res/drawable/button.png").getPath();
    myFixture.copyFileToProject("javadoc/drawables/ic_launcher.png",
                                "res/drawable/button_active.png").getPath();
    myFixture.copyFileToProject("javadoc/drawables/ic_launcher.png",
                                "res/drawable/button_disabled.png").getPath();
    checkDoc(sourcePath, targetPath);
  }

  @Test
  public void mipmapJava() {
    checkMipmaps("/javadoc/mipmaps/Activity1.java", "src/p1/p2/Activity.java");
  }

  @Test
  public void mipmapKotlin() {
    checkMipmaps("/javadoc/mipmaps/Activity1.kt", "src/p1/p2/Activity.kt");
  }

  public void checkMipmaps(String sourcePath, String targetPath) {
    myFixture.copyFileToProject("javadoc/mipmaps/ic_launcher.png", "res/mipmap/ic_launcher.png");
    myFixture.copyFileToProject("javadoc/mipmaps/ic_launcher.png", "res/mipmap-hdpi/ic_launcher.png");

    checkDoc(sourcePath, targetPath);
  }

  @Test
  public void arraysJava() {
    checkArrays("/javadoc/arrays/Activity1.java", "src/p1/p2/Activity.java");
  }

  @Test
  public void arraysKotlin() {
    checkArrays("/javadoc/arrays/Activity1.kt", "src/p1/p2/Activity.kt");
  }

  public void checkArrays(String sourcePath, String targetPath) {
    myFixture.copyFileToProject("javadoc/arrays/arrays.xml", "res/values/arrays.xml");
    myFixture.copyFileToProject("javadoc/arrays/arrays-no.xml", "res/values-no/arrays.xml");
    checkDoc(sourcePath, targetPath);
  }

  @Test
  public void xmlString1() {
    myFixture.copyFileToProject("javadoc/strings/strings.xml", "res/values/strings.xml");
    myFixture.copyFileToProject("javadoc/strings/strings-ta.xml", "res/values-ta/strings.xml");
    myFixture.copyFileToProject("javadoc/strings/strings-zh-rTW.xml", "res/values-zh-rTW/strings.xml");
    checkDoc("/javadoc/strings/layout1.xml", "res/layout/layout1.xml");
  }

  @Test
  public void attributeValueDoc() {
    checkDoc("/javadoc/layout/layout.xml", "res/layout/layout.xml");
  }

  @Test
  public void attributeEnumDoc() {
    checkDoc("/javadoc/styles/styles2.xml", "res/styles.xml");
  }

  @Test
  public void attributeEnumValueDoc() {
    checkDoc("/javadoc/styles/styles3.xml", "res/styles.xml");
  }

  @Test
  public void xmlString2() {
    // Like testXmlString1, but the caret is at the right edge of an attribute value so the document provider has
    // to go to the previous XML token to obtain the resource url
    myFixture.copyFileToProject("javadoc/strings/strings.xml", "res/values/strings.xml");
    myFixture.copyFileToProject("javadoc/strings/strings-ta.xml", "res/values-ta/strings.xml");
    myFixture.copyFileToProject("javadoc/strings/strings-zh-rTW.xml", "res/values-zh-rTW/strings.xml");
    checkDoc("/javadoc/strings/layout2.xml", "res/layout/layout2.xml");
  }

  @Test
  public void systemAttributes() {
    checkDoc("/javadoc/attrs/layout1.xml", "res/layout/layout.xml");
  }

  @Test
  public void localAttributes1() {
    doTestLocalAttributes("/javadoc/attrs/layout2.xml");
  }

  @Test
  public void localAttributes2() {
    doTestLocalAttributes("/javadoc/attrs/layout3.xml");
  }

  @Test
  public void localAttributes3() {
    doTestLocalAttributes("/javadoc/attrs/layout4.xml");
  }

  @Test
  public void localAttributes4() {
    doTestLocalAttributes("/javadoc/attrs/layout5.xml");
  }

  @Test
  public void localAttributes5() {
    doTestLocalAttributes("/javadoc/attrs/layout6.xml");
  }

  @Test
  public void localEnumAttributes1() {
    myFixture.copyFileToProject( "javadoc/attrs/attrs.xml", "res/values/attrs.xml");
    checkDoc("/javadoc/styles/styles4.xml", "res/styles.xml");
  }

  @Test
  public void localEnumAttributes2() {
    myFixture.copyFileToProject("javadoc/attrs/attrs.xml", "res/values/attrs.xml");
    checkDoc("/javadoc/styles/styles5.xml", "res/styles.xml");
  }

  private void doTestLocalAttributes(String file) {
    myFixture.copyFileToProject("javadoc/attrs/attrs.xml", "res/values/attrs.xml");
    myFixture.copyFileToProject("javadoc/attrs/MyView1.java", "src/p1/p2/MyView1.java");
    myFixture.copyFileToProject("javadoc/attrs/MyView2.java", "src/p1/p2/MyView2.java");
    myFixture.copyFileToProject("javadoc/attrs/MyView3.java", "src/p1/p2/MyView3.java");
    checkDoc(file, "res/layout/layout.xml");
  }

  @Test
  public void manifestAttributes() {
    checkDoc("/javadoc/attrs/manifest.xml", "AndroidManifest.xml");
  }

  @Test
  public void frameworkColors2() {
    checkDoc("/javadoc/colors/layout2.xml", "res/layout/layout.xml");
  }

  @Test
  public void alphaColor() {
    myFixture.copyFileToProject("javadoc/colors/values2.xml", "res/values/values2.xml");
    checkDoc("/javadoc/colors/layout3.xml", "res/layout/layout.xml");
  }

  @Test
  public void colorsAndResolution() {
    // This test checks
    //  - invoking XML documentation from an XML text node
    //  - a long chain of resource resolutions
    //  - evaluating colors, including XML color state lists
    myFixture.copyFileToProject("javadoc/colors/third.xml", "res/color/third.xml");
    checkDoc("/javadoc/colors/values.xml", "res/values/values.xml");
  }

  @Test
  public void style() {
    myFixture.copyFileToProject("javadoc/styles/AndroidManifest.xml", "AndroidManifest.xml");
    myFixture.copyFileToProject("javadoc/styles/styles.xml", "res/values/styles.xml");
    checkDoc("/javadoc/styles/layout.xml", "res/layout/layout.xml");
  }

  @Test
  public void frameworkStyleResolution() {
    // Checks that references in framework styles are always understood to point to framework resources,
    // even if the android: prefix is not explicitly written.
    assertThat(generateDoc("/javadoc/styles/styles.xml", "res/values/styles.xml")).contains("@android:style/Theme.Holo<BR/>");
  }

  @Test
  public void styleName() {
    checkDoc("/javadoc/styles/styles_attribute_documentation.xml", "res/values/styles.xml");
  }

  @Test
  public void injectExternalDocumentation() {
    assertThat(AndroidJavaDocRenderer.injectExternalDocumentation("first", "second")).isEqualTo("firstsecond");
    assertThat(
      AndroidJavaDocRenderer.injectExternalDocumentation("<html a=\"b\"><body b=\"c\">first</body></html>", "second"))
      .isEqualTo("<html a=\"b\"><body b=\"c\">firstsecond</body></html>");
    assertThat(
      AndroidJavaDocRenderer.injectExternalDocumentation("<HTML a=\"b\"><BODY b=\"c\">first</BODY></HTML>", "second"))
      .isEqualTo("<HTML a=\"b\"><BODY b=\"c\">firstsecond</BODY></HTML>");
    assertThat(
      AndroidJavaDocRenderer.injectExternalDocumentation("first", "<html a=\"b\"><body b=\"c\">second</body></html>"))
      .isEqualTo("firstsecond");
    assertThat(
      AndroidJavaDocRenderer.injectExternalDocumentation("first", "<HTML a=\"b\"><BODY b=\"c\">second</BODY></HTML>"))
      .isEqualTo("firstsecond");
    assertThat(
      AndroidJavaDocRenderer.injectExternalDocumentation("<html a=\"b\">first</html>", "<html b=\"c\">second</html>"))
      .isEqualTo("<html a=\"b\">firstsecond</html>");
    assertThat(
      AndroidJavaDocRenderer.injectExternalDocumentation("<BODY a=\"b\">first</BODY>", "<BODY b=\"c\">second</BODY>"))
      .isEqualTo("<BODY a=\"b\">firstsecond</BODY>");

    // insert style with head
    assertThat(
      AndroidJavaDocRenderer.injectExternalDocumentation("<head>head</head><body>first</body>", "<style>s2</style>second"))
      .isEqualTo("<head>head<style>s2</style></head><body>firstsecond</body>");
    // insert style without head
    assertThat(
      AndroidJavaDocRenderer.injectExternalDocumentation("<body>first</body>", "<style>s2</style>second"))
      .isEqualTo("<head><style>s2</style></head><body>firstsecond</body>");
  }

  @Test
  public void lintIssueId() {
    checkDoc("/javadoc/lint/lint_issue_id.xml", "lint.xml");
  }

  /**
   * Regression test for http://b/151964515
   */
  @Test
  public void inheritanceLoop() {
    // A layout is needed for the ResourceResolver to be able to automatically pick a default configuration (it will find a layout at
    // random). The layout is not related to the test.
    myFixture.copyFileToProject("javadoc/layout/layout.xml", "res/layout/layout.xml");
    assertThat(generateDoc("/javadoc/styles/styles_loop.xml", "res/values/styles.xml"))
      .startsWith("<html><body><BR/>@style/TextAppearance<BR/><BR/><hr><B>TextAppearance</B>");
  }

  @NotNull
  @Override
  public String getSnapshotDirectoryWorkspaceRelativePath() {
    return "tools/adt/idea/android/testData/javadoc/snapshots";
  }

  @NotNull
  @Override
  public String getName() {
    return name.getMethodName();
  }
}
