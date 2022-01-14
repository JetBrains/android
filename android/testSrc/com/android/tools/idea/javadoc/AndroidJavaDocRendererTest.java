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

import com.android.tools.idea.testing.SnapshotComparisonTest;
import com.android.tools.lint.client.api.LintClient;
import com.intellij.codeInsight.documentation.DocumentationManager;
import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.util.Consumer;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;

public class AndroidJavaDocRendererTest extends AndroidTestCase implements SnapshotComparisonTest {
  static {
    LintClient.setClientName(LintClient.CLIENT_STUDIO);
  }

  public void checkStrings(String fileName, String targetPath) {
    myFixture.copyFileToProject(getTestDataPath() + "/javadoc/strings/strings.xml", "res/values/strings.xml");
    myFixture.copyFileToProject(getTestDataPath() + "/javadoc/strings/strings-ta.xml", "res/values-ta/strings.xml");
    myFixture.copyFileToProject(getTestDataPath() + "/javadoc/strings/strings-zh-rTW.xml",
                                "res/values-zh-rTW/strings.xml");
    checkDoc(fileName, targetPath);
  }

  private void checkDoc(String fileName, String targetName) {
    checkDoc(fileName, targetName, doc -> {
      String normalizedDoc = normalizeHtmlForTests(getProject(), doc != null ? doc : "");
      assertIsEqualToSnapshot(this, normalizedDoc, "");
    });
  }

  /**
   * Test that the project can fetch documentation at the caret point (which is expected to be set
   * explicitly in the contents of {@code fileName}). {@code javadocConsumer} will be triggered with
   * the actual documentation returned and will be responsible for asserting expected values.
   */
  private void checkDoc(String fileName, String targetName, Consumer<String> javadocConsumer) {
    VirtualFile f = myFixture.copyFileToProject(getTestDataPath() + fileName, targetName);
    myFixture.configureFromExistingVirtualFile(f);
    PsiElement originalElement = myFixture.getFile().findElementAt(myFixture.getEditor().getCaretModel().getOffset());
    assert originalElement != null;
    PsiElement docTargetElement =
      DocumentationManager.getInstance(getProject()).findTargetElement(myFixture.getEditor(), myFixture.getFile(), originalElement);
    assert docTargetElement != null;
    DocumentationProvider provider = DocumentationManager.getProviderFromElement(docTargetElement);
    String doc = provider.generateDoc(docTargetElement, originalElement);
    javadocConsumer.consume(doc);
  }

  public void testBrokenCustomDrawableJava() {
    // Layout lib cannot render the custom drawable here, so it should show an error.
    myFixture.copyFileToProject(getTestDataPath() + "/javadoc/drawables/customDrawable.xml", "res/drawable/ic_launcher.xml");
    checkDoc("/javadoc/drawables/Activity1.java",
             "src/p1/p2/Activity.java", actualDoc -> {
        assertThat(actualDoc).contains("Couldn't render");
        assertThat(actualDoc).doesNotContain("render.png");
      });
  }

  public void testWebPDrawableJava() {
    // WebP images need to be rendered by layoutlib, so should show the rendered PNG.
    myFixture.copyFileToProject(getTestDataPath() + "/javadoc/drawables/ic_launcher.webp", "res/drawable/ic_launcher.webp");
    checkDoc("/javadoc/drawables/Activity1.java",
             "src/p1/p2/Activity.java", actualDoc -> {
        assertThat(actualDoc).contains("render.png");
        assertThat(actualDoc).doesNotContain("Couldn't render");
      });
  }

  public void testString1Java() {
    checkString1("/javadoc/strings/Activity1.java", "src/p1/p2/Activity.java");
  }

  public void testString1Kotlin() {
    checkString1("/javadoc/strings/Activity1.kt", "src/p1/p2/Activity.kt");
  }

  public void checkString1(String sourcePath, String targetPath) {
    checkStrings(sourcePath, targetPath);
  }

  public void testString2Java() {
    checkString2("/javadoc/strings/Activity2.java", "src/p1/p2/Activity.java");
  }

  public void testString2Kotlin() {
    checkString2("/javadoc/strings/Activity2.kt", "src/p1/p2/Activity.kt");
  }

  public void checkString2(String sourcePath, String targetPath) {
    // Use FlagManagerTest#checkEncoding to get Unicode encoding
    checkStrings(sourcePath, targetPath);
  }

  /**
   * Testing R.stri<caret>ng.app_name
   * <p>
   * There is no custom documentation for the inner R class, so the default Java platform documentation for a Java
   * static final field is used.
   */
  public void testString3Java() {
    checkString3("/javadoc/strings/Activity3.java", "src/p1/p2/Activity.java");
  }

  public void testString3Kotlin() {
    checkString3("/javadoc/strings/Activity3.kt", "src/p1/p2/Activity.kt");
  }

  public void checkString3(String sourcePath, String targetPath) {
/* b/214640623
    checkStrings(sourcePath, targetPath);
b/214640623 */
  }

  public void testDimensionsJava() {
    checkDimensions("/javadoc/dimens/Activity1.java", "src/p1/p2/Activity.java");
  }

  public void testDimensionsKotlin() {
    checkDimensions("/javadoc/dimens/Activity1.kt", "src/p1/p2/Activity.kt");
  }

  public void checkDimensions(String sourcePath, String targetPath) {
    myFixture.copyFileToProject(getTestDataPath() + "/javadoc/dimens/dimens.xml", "res/values/dimens.xml");
    myFixture.copyFileToProject(getTestDataPath() + "/javadoc/dimens/dimens-sw720dp.xml", "res/values-sw720dp/dimens.xml");
    myFixture.copyFileToProject(getTestDataPath() + "/javadoc/dimens/dimens-land.xml", "res/values-land/dimens.xml");
    checkDoc(sourcePath, targetPath);
  }

  public void testDrawablesJava() {
    checkDrawables("/javadoc/drawables/Activity1.java", "src/p1/p2/Activity.java");
  }

  public void testDrawablesKotlin() {
    checkDrawables("/javadoc/drawables/Activity1.kt", "src/p1/p2/Activity.kt");
  }

  public void checkDrawables(String sourcePath, String targetPath) {
    String p1 = myFixture.copyFileToProject(getTestDataPath() + "/javadoc/drawables/ic_launcher.png",
                                            "res/drawable/ic_launcher.png").getPath();
    String p2 = myFixture.copyFileToProject(getTestDataPath() + "/javadoc/drawables/ic_launcher.png",
                                            "res/drawable-hdpi/ic_launcher.png").getPath();

    checkDoc(sourcePath, targetPath);
  }

  public void testStateListDrawablesJava() {
    checkStateListDrawables("/javadoc/drawables/Activity2.java", "src/p1/p2/Activity.java");
  }

  public void testStateListDrawablesKotlin() {
    checkStateListDrawables("/javadoc/drawables/Activity2.kt", "src/p1/p2/Activity.kt");
  }

  public void checkStateListDrawables(String sourcePath, String targetPath) {
    myFixture.copyFileToProject(getTestDataPath() + "/javadoc/drawables/selector.xml", "res/drawable/selector.xml");
    String p1 = myFixture.copyFileToProject(getTestDataPath() + "/javadoc/drawables/ic_launcher.png",
                                            "res/drawable/button.png").getPath();
    String p2 = myFixture.copyFileToProject(getTestDataPath() + "/javadoc/drawables/ic_launcher.png",
                                            "res/drawable/button_active.png").getPath();
    String p3 = myFixture.copyFileToProject(getTestDataPath() + "/javadoc/drawables/ic_launcher.png",
                                            "res/drawable/button_disabled.png").getPath();
    checkDoc(sourcePath, targetPath);
  }

  public void testMipmapJava() {
    checkMipmaps("/javadoc/mipmaps/Activity1.java", "src/p1/p2/Activity.java");
  }

  public void testMipmapKotlin() {
    checkMipmaps("/javadoc/mipmaps/Activity1.kt", "src/p1/p2/Activity.kt");
  }

  public void checkMipmaps(String sourcePath, String targetPath) {
    String p1 = myFixture.copyFileToProject(getTestDataPath() + "/javadoc/mipmaps/ic_launcher.png",
                                            "res/mipmap/ic_launcher.png").getPath();
    String p2 = myFixture.copyFileToProject(getTestDataPath() + "/javadoc/mipmaps/ic_launcher.png",
                                            "res/mipmap-hdpi/ic_launcher.png").getPath();

    checkDoc(sourcePath, targetPath);
  }

  public void testArraysJava() {
    checkArrays("/javadoc/arrays/Activity1.java", "src/p1/p2/Activity.java");
  }

  public void testArraysKotlin() {
    checkArrays("/javadoc/arrays/Activity1.kt", "src/p1/p2/Activity.kt");
  }

  public void checkArrays(String sourcePath, String targetPath) {
    myFixture.copyFileToProject(getTestDataPath() + "/javadoc/arrays/arrays.xml", "res/values/arrays.xml");
    myFixture.copyFileToProject(getTestDataPath() + "/javadoc/arrays/arrays-no.xml", "res/values-no/arrays.xml");
    checkDoc(sourcePath, targetPath);
  }

  public void testXmlString1() {
    myFixture.copyFileToProject(getTestDataPath() + "/javadoc/strings/strings.xml", "res/values/strings.xml");
    myFixture.copyFileToProject(getTestDataPath() + "/javadoc/strings/strings-ta.xml", "res/values-ta/strings.xml");
    myFixture.copyFileToProject(getTestDataPath() + "/javadoc/strings/strings-zh-rTW.xml", "res/values-zh-rTW/strings.xml");
    checkDoc("/javadoc/strings/layout1.xml", "res/layout/layout1.xml");
  }

  public void testAttributeValueDoc() {
    checkDoc("/javadoc/layout/layout.xml", "res/layout/layout.xml");
  }

  public void testAttributeEnumDoc() {
    checkDoc("/javadoc/styles/styles2.xml", "res/styles.xml");
  }

  public void testAttributeEnumValueDoc() {
    checkDoc("/javadoc/styles/styles3.xml", "res/styles.xml");
  }

  public void testXmlString2() {
    // Like testXmlString1, but the caret is at the right edge of an attribute value so the document provider has
    // to go to the previous XML token to obtain the resource url
    myFixture.copyFileToProject(getTestDataPath() + "/javadoc/strings/strings.xml", "res/values/strings.xml");
    myFixture.copyFileToProject(getTestDataPath() + "/javadoc/strings/strings-ta.xml", "res/values-ta/strings.xml");
    myFixture.copyFileToProject(getTestDataPath() + "/javadoc/strings/strings-zh-rTW.xml", "res/values-zh-rTW/strings.xml");
    checkDoc("/javadoc/strings/layout2.xml", "res/layout/layout2.xml");
  }

  public void testSystemAttributes() {
    checkDoc("/javadoc/attrs/layout1.xml", "res/layout/layout.xml");
  }

  public void testLocalAttributes1() {
    doTestLocalAttributes("/javadoc/attrs/layout2.xml");
  }

  public void testLocalAttributes2() {
    doTestLocalAttributes("/javadoc/attrs/layout3.xml");
  }

  public void testLocalAttributes3() {
    doTestLocalAttributes("/javadoc/attrs/layout4.xml");
  }

  public void testLocalAttributes4() {
    doTestLocalAttributes("/javadoc/attrs/layout5.xml");
  }

  public void testLocalAttributes5() {
    doTestLocalAttributes("/javadoc/attrs/layout6.xml");
  }

  public void testLocalEnumAttributes1() {
    myFixture.copyFileToProject(getTestDataPath() + "/javadoc/attrs/attrs.xml", "res/values/attrs.xml");
    checkDoc("/javadoc/styles/styles4.xml", "res/styles.xml");
  }

  public void testLocalEnumAttributes2() {
    myFixture.copyFileToProject(getTestDataPath() + "/javadoc/attrs/attrs.xml", "res/values/attrs.xml");
    checkDoc("/javadoc/styles/styles5.xml", "res/styles.xml");
  }

  private void doTestLocalAttributes(String file) {
    myFixture.copyFileToProject(getTestDataPath() + "/javadoc/attrs/attrs.xml", "res/values/attrs.xml");
    myFixture.copyFileToProject(getTestDataPath() + "/javadoc/attrs/MyView1.java", "src/p1/p2/MyView1.java");
    myFixture.copyFileToProject(getTestDataPath() + "/javadoc/attrs/MyView2.java", "src/p1/p2/MyView2.java");
    myFixture.copyFileToProject(getTestDataPath() + "/javadoc/attrs/MyView3.java", "src/p1/p2/MyView3.java");
    checkDoc(file, "res/layout/layout.xml");
  }

  public void testManifestAttributes() throws Exception {
    deleteManifest();
    checkDoc("/javadoc/attrs/manifest.xml", "AndroidManifest.xml");
  }

  public void testFrameworkColors2() {
    checkDoc("/javadoc/colors/layout2.xml", "res/layout/layout.xml");
  }

  public void testAlphaColor() {
    myFixture.copyFileToProject(getTestDataPath() + "/javadoc/colors/values2.xml", "res/values/values2.xml");
    checkDoc("/javadoc/colors/layout3.xml", "res/layout/layout.xml");
  }

  public void testColorsAndResolution() {
    // This test checks
    //  - invoking XML documentation from an XML text node
    //  - a long chain of resource resolutions
    //  - evaluating colors, including XML color state lists
    myFixture.copyFileToProject(getTestDataPath() + "/javadoc/colors/third.xml", "res/color/third.xml");
    checkDoc("/javadoc/colors/values.xml", "res/values/values.xml");
  }

  public void testStyle() {
    myFixture.copyFileToProject(getTestDataPath() + "/javadoc/styles/AndroidManifest.xml", "AndroidManifest.xml");
    myFixture.copyFileToProject(getTestDataPath() + "/javadoc/styles/styles.xml", "res/values/styles.xml");
    checkDoc("/javadoc/styles/layout.xml", "res/layout/layout.xml");
  }

  public void testFrameworkStyleResolution() {
    // Checks that references in framework styles are always understood to point to framework resources,
    // even if the android: prefix is not explicitly written.
    checkDoc("/javadoc/styles/styles.xml", "res/values/styles.xml", actualDoc -> {
      boolean isContained = actualDoc.contains("@android:style/Theme.Holo<BR/>");
      assertTrue("\nExpected: " + "@android:style/Theme.Holo<BR/>" + "\nContained By: " + actualDoc, isContained);
    });
  }

  public void testStyleName() {
    checkDoc("/javadoc/styles/styles_attribute_documentation.xml", "res/values/styles.xml");
  }

  public void testInjectExternalDocumentation() {
    assertEquals("firstsecond", AndroidJavaDocRenderer.injectExternalDocumentation("first", "second"));
    assertEquals("<html a=\"b\"><body b=\"c\">firstsecond</body></html>",
                 AndroidJavaDocRenderer.injectExternalDocumentation("<html a=\"b\"><body b=\"c\">first</body></html>", "second"));
    assertEquals("<HTML a=\"b\"><BODY b=\"c\">firstsecond</BODY></HTML>",
                 AndroidJavaDocRenderer.injectExternalDocumentation("<HTML a=\"b\"><BODY b=\"c\">first</BODY></HTML>", "second"));
    assertEquals("firstsecond",
                 AndroidJavaDocRenderer.injectExternalDocumentation("first", "<html a=\"b\"><body b=\"c\">second</body></html>"));
    assertEquals("firstsecond",
                 AndroidJavaDocRenderer.injectExternalDocumentation("first", "<HTML a=\"b\"><BODY b=\"c\">second</BODY></HTML>"));
    assertEquals("<html a=\"b\">firstsecond</html>",
                 AndroidJavaDocRenderer.injectExternalDocumentation("<html a=\"b\">first</html>", "<html b=\"c\">second</html>"));
    assertEquals("<BODY a=\"b\">firstsecond</BODY>",
                 AndroidJavaDocRenderer.injectExternalDocumentation("<BODY a=\"b\">first</BODY>", "<BODY b=\"c\">second</BODY>"));

    // insert style with head
    assertEquals("<head>head<style>s2</style></head><body>firstsecond</body>",
                 AndroidJavaDocRenderer.injectExternalDocumentation("<head>head</head><body>first</body>", "<style>s2</style>second"));
    // insert style without head
    assertEquals("<head><style>s2</style></head><body>firstsecond</body>",
                 AndroidJavaDocRenderer.injectExternalDocumentation("<body>first</body>", "<style>s2</style>second"));
  }

  public void testLintIssueId() {
    checkDoc("/javadoc/lint/lint_issue_id.xml", "lint.xml");
  }

  /**
   * Regression test for http://b/151964515
   */
  public void testInheritanceLoop() {
    // A layout is needed for the ResourceResolver to be able to automatically pick a default configuration (it will find a layout at
    // random). The layout is not related to the test.
    myFixture.copyFileToProject("/javadoc/layout/layout.xml", "res/layout/layout.xml");
    checkDoc("/javadoc/styles/styles_loop.xml", "res/values/styles.xml",
             doc -> assertTrue(doc.startsWith("<html><body><BR/>@style/TextAppearance<BR/><BR/><hr><B>TextAppearance</B>")));
  }

  @NotNull
  @Override
  public String getSnapshotDirectoryWorkspaceRelativePath() {
    return "tools/adt/idea/android/testData/javadoc/snapshots";
  }
}
