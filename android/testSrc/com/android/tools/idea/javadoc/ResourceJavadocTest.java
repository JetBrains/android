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

public class ResourceJavadocTest extends AndroidTestCase {
  private static final String VERTICAL_ALIGN = "valign=\"top\"";

  @SuppressWarnings("JUnitTestCaseWithNonTrivialConstructors")
  public ResourceJavadocTest() {
    IdeaTestCase.initPlatformPrefix();
  }

  public void checkStrings(String fileName, @Nullable String expectedDoc) {
    myFixture.copyFileToProject(getTestDataPath() + "/javadoc/strings/strings.xml", "res/values/strings.xml");
    myFixture.copyFileToProject(getTestDataPath() + "/javadoc/strings/strings-ta.xml", "res/values-ta/strings.xml");
    myFixture.copyFileToProject(getTestDataPath() + "/javadoc/strings/strings-zh-rTW.xml",
                                "res/values-zh-rTW/strings.xml");
    checkJavadoc(fileName, expectedDoc);
  }

  private void checkJavadoc(String fileName, @Nullable String expectedDoc) {
    final VirtualFile f = myFixture.copyFileToProject(getTestDataPath() + fileName,
                                                      "src/com/foo/Activity.java");
    myFixture.configureFromExistingVirtualFile(f);
    PsiElement element = myFixture.getFile().findElementAt(myFixture.getEditor().getCaretModel().getOffset());
    assert element != null;

    DocumentationProvider provider = DocumentationManager.getProviderFromElement(element);
    assertEquals(expectedDoc, provider.generateDoc(element, element));
  }

  public void testString1() {
    checkStrings("/javadoc/strings/Activity1.java", "<html><body>Application Name</body></html>");
  }

  public void testString2() {
    // Use LocaleManagerTest#checkEncoding to get Unicode encoding
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
}
