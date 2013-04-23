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
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.IdeaTestCase;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.Nullable;

public class ResourceJavadocTest extends AndroidTestCase {
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
    checkStrings("/javadoc/strings/Activity2.java", "<html><body><table>" +
                                                    "<tr><td>Default</td><td>Cancel</td></tr>" +
                                                    "<tr><td>ta</td><td>\u0bb0\u0ba4\u0bcd\u0ba4\u0bc1</td></tr>" +
                                                    "<tr><td>zh-rTW</td><td>\u53d6\u6d88</td></tr>" +
                                                    "</table><body></html>");
  }

  public void testString3() {
    checkStrings("/javadoc/strings/Activity3.java", null);
  }

  public void testDimensions1() {
    myFixture.copyFileToProject(getTestDataPath() + "/javadoc/dimens/dimens.xml", "res/values/dimens.xml");
    myFixture.copyFileToProject(getTestDataPath() + "/javadoc/dimens/dimens-sw720dp.xml", "res/values-sw720dp/dimens.xml");
    myFixture.copyFileToProject(getTestDataPath() + "/javadoc/dimens/dimens-land.xml", "res/values-land/dimens.xml");
    checkJavadoc("/javadoc/dimens/Activity1.java",
    "<html><body><table>" +
    "<tr><td>Configuration</td><td>Value</td><td>XXHDPI</td><td>XHDPI</td><td>HDPI</td><td>TVDPI</td><td>MDPI</td><td>LDPI</td></tr>" +
    "<tr><td>Default</td><td><b>200dp</b></td><td>600px</td><td>400px</td><td>300px</td><td>266px</td><td>200px</td><td>150px</td></tr>" +
    "<tr><td>land</td><td><b>200px</b></td><td>200px</td><td>200px</td><td>200px</td><td>200px</td><td>200px</td><td>200px</td></tr>" +
    "<tr><td>sw720dp</td><td><b>300dip</b></td><td>900px</td><td>600px</td><td>450px</td><td>399px</td><td>300px</td><td>225px</td></tr>" +
    "</table><body></html>");
  }
}
