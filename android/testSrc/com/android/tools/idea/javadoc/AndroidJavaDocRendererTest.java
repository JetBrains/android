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

import com.intellij.codeInsight.documentation.DocumentationManager;
import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.IdeaTestCase;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.Nullable;

public class AndroidJavaDocRendererTest extends AndroidTestCase {
  private static final String VERTICAL_ALIGN = "valign=\"top\"";

  @SuppressWarnings("JUnitTestCaseWithNonTrivialConstructors")
  public AndroidJavaDocRendererTest() {
    IdeaTestCase.initPlatformPrefix();
  }

  public void checkStrings(String fileName, @Nullable String expectedDoc) {
    myFixture.copyFileToProject(getTestDataPath() + "/javadoc/strings/strings.xml", "res/values/strings.xml");
    myFixture.copyFileToProject(getTestDataPath() + "/javadoc/strings/strings-ta.xml", "res/values-ta/strings.xml");
    myFixture.copyFileToProject(getTestDataPath() + "/javadoc/strings/strings-zh-rTW.xml",
                                "res/values-zh-rTW/strings.xml");
    checkJavadoc(fileName, "src/com/foo/Activity.java", expectedDoc);
  }

  private void checkJavadoc(String fileName, @Nullable String expectedDoc) {
    checkJavadoc(fileName, "src/com/foo/Activity.java", expectedDoc);
  }

  private void checkJavadoc(String fileName, String targetName, @Nullable String expectedDoc) {
    final VirtualFile f = myFixture.copyFileToProject(getTestDataPath() + fileName, targetName);
    myFixture.configureFromExistingVirtualFile(f);
    PsiElement originalElement = myFixture.getFile().findElementAt(myFixture.getEditor().getCaretModel().getOffset());
    assert originalElement != null;
    final PsiElement docTargetElement = DocumentationManager.getInstance(getProject()).findTargetElement(
      myFixture.getEditor(), myFixture.getFile(), originalElement);
    assert docTargetElement != null;
    DocumentationProvider provider = DocumentationManager.getProviderFromElement(docTargetElement);
    assertEquals(expectedDoc, provider.generateDoc(docTargetElement, originalElement));
  }

  public void testString1() {
    checkStrings("/javadoc/strings/Activity1.java", "<html><body>Application Name</body></html>");
  }

  public void testString2() {
    // Use FlagManagerTest#checkEncoding to get Unicode encoding
    checkStrings("/javadoc/strings/Activity2.java",
                 String.format("<html><body><table>" +
                               "<tr><th %1$s>Configuration</th><th %1$s>Value</th></tr>" +
                               "<tr><td %1$s>Default</td><td %1$s>Cancel</td></tr>" +
                               "<tr><td %1$s>ta</td><td %1$s>\u0bb0\u0ba4\u0bcd\u0ba4\u0bc1</td></tr>" +
                               "<tr><td %1$s>zh-rTW</td><td %1$s>\u53d6\u6d88</td></tr>" +
                               "</table></body></html>", VERTICAL_ALIGN));
  }

  public void testString3() {
    checkStrings("/javadoc/strings/Activity3.java", null);
  }

  public void testDimensions1() {
    myFixture.copyFileToProject(getTestDataPath() + "/javadoc/dimens/dimens.xml", "res/values/dimens.xml");
    myFixture.copyFileToProject(getTestDataPath() + "/javadoc/dimens/dimens-sw720dp.xml", "res/values-sw720dp/dimens.xml");
    myFixture.copyFileToProject(getTestDataPath() + "/javadoc/dimens/dimens-land.xml", "res/values-land/dimens.xml");
    checkJavadoc("/javadoc/dimens/Activity1.java",
                 String.format("<html><body><table>" +
                 "<tr><th %1$s>Configuration</th><th %1$s>Value</th></tr>" +
                 "<tr><td %1$s>Default</td><td %1$s>200dp</td></tr>" +
                 "<tr><td %1$s>land</td><td %1$s>200px</td></tr>" +
                 "<tr><td %1$s>sw720dp</td><td %1$s>300dip</td></tr>" +
                 "</table></body></html>", VERTICAL_ALIGN));
  }

  public void testDrawables() {
    String p1 = myFixture.copyFileToProject(getTestDataPath() + "/javadoc/drawables/ic_launcher.png",
                                            "res/drawable/ic_launcher.png").getPath();
    String p2 = myFixture.copyFileToProject(getTestDataPath() + "/javadoc/drawables/ic_launcher.png",
                                            "res/drawable-hdpi/ic_launcher.png").getPath();

    String divTag = "<div style=\"background-color:gray;padding:10px\">";
    String imgTag1 = String.format("<img src='file:%1$s' alt=\"%2$s\" />", (p1.startsWith("/") ? p1 : '/' + p1),
                                   FileUtil.toSystemDependentName(p1));
    String imgTag2 = String.format("<img src='file:%1$s' alt=\"%2$s\" />", (p2.startsWith("/") ? p2 : '/' + p2),
                                   FileUtil.toSystemDependentName(p2));
    checkJavadoc("/javadoc/drawables/Activity1.java",
                 String.format("<html><body><table>" +
                 "<tr><th %1$s>Configuration</th><th %1$s>Value</th></tr>" +
                 "<tr><td %1$s>drawable</td><td %1$s>%2$s%3$s</div>12&#xd7;12 px (12&#xd7;12 dp @ mdpi)<BR/>\n</td></tr>" +
                 "<tr><td %1$s>drawable-hdpi</td><td %1$s>%2$s%4$s</div>12&#xd7;12 px (8&#xd7;8 dp @ hdpi)<BR/>\n</td></tr>" +
                 "</table></body></html>", VERTICAL_ALIGN, divTag, imgTag1, imgTag2));
  }

  public void testXmlString1() {
    myFixture.copyFileToProject(getTestDataPath() + "/javadoc/strings/strings.xml", "res/values/strings.xml");
    myFixture.copyFileToProject(getTestDataPath() + "/javadoc/strings/strings-ta.xml", "res/values-ta/strings.xml");
    myFixture.copyFileToProject(getTestDataPath() + "/javadoc/strings/strings-zh-rTW.xml", "res/values-zh-rTW/strings.xml");
    checkJavadoc("/javadoc/strings/layout1.xml", "res/layout/layout1.xml", "<html><body>Application Name</body></html>");
  }

  public void testSystemAttributes() {
    checkJavadoc("/javadoc/attrs/layout1.xml", "res/layout/layout.xml",
                 "<html><body>Formats: enum<br>Values: horizontal, vertical<br><br> Should the layout be a column or a row?  Use \"horizontal\"\n" +
                 "             for a row, \"vertical\" for a column.  The default is\n" +
                 "             horizontal. </body></html>");
  }

  public void testLocalAttributes1() {
    doTestLocalAttributes("/javadoc/attrs/layout2.xml",
                          "<html><body>Formats: boolean, integer<br><br> my attr 1 docs for MyView1 </body></html>");
  }

  public void testLocalAttributes2() {
    doTestLocalAttributes("/javadoc/attrs/layout3.xml",
                          "<html><body>Formats: boolean, reference<br><br> my attr 2 docs for MyView1 </body></html>");
  }

  public void testLocalAttributes3() {
    doTestLocalAttributes("/javadoc/attrs/layout4.xml",
                          "<html><body>Formats: boolean, integer<br><br> my attr 1 docs for MyView2 </body></html>");
  }

  public void testLocalAttributes4() {
    doTestLocalAttributes("/javadoc/attrs/layout5.xml",
                          "<html><body>Formats: boolean, reference<br><br> my attr 2 docs for MyView2 </body></html>");
  }

  public void testLocalAttributes5() {
    doTestLocalAttributes("/javadoc/attrs/layout6.xml",
                          "<html><body>Formats: boolean, integer<br><br> my attr 1 global docs </body></html>");
  }

  private void doTestLocalAttributes(String file, String exp) {
    myFixture.copyFileToProject(getTestDataPath() + "/javadoc/attrs/attrs.xml", "res/values/attrs.xml");
    myFixture.copyFileToProject(getTestDataPath() + "/javadoc/attrs/MyView1.java", "src/p1/p2/MyView1.java");
    myFixture.copyFileToProject(getTestDataPath() + "/javadoc/attrs/MyView2.java", "src/p1/p2/MyView2.java");
    myFixture.copyFileToProject(getTestDataPath() + "/javadoc/attrs/MyView3.java", "src/p1/p2/MyView3.java");
    checkJavadoc(file, "res/layout/layout.xml", exp);
  }

  public void testManifestAttributes() throws Exception {
    deleteManifest();
    checkJavadoc("/javadoc/attrs/manifest.xml", "AndroidManifest.xml",
                 "<html><body>Formats: string<br><br> Required name of the class implementing the activity, deriving from\n" +
                 "            {@link android.app.Activity}.  This is a fully\n" +
                 "            qualified class name (for example, com.mycompany.myapp.MyActivity); as a\n" +
                 "            short-hand if the first character of the class\n" +
                 "            is a period then it is appended to your package name. </body></html>");
  }
}
