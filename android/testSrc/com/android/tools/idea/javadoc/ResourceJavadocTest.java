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

  private void copyResources() {
    myFixture.copyFileToProject(getTestDataPath() + "/javadoc/strings.xml", "res/values/strings.xml");
    myFixture.copyFileToProject(getTestDataPath() + "/javadoc/strings-ta.xml", "res/values-ta/strings.xml");
    myFixture.copyFileToProject(getTestDataPath() + "/javadoc/strings-zh-rTW.xml", "res/values-zh-rTW/strings.xml");
  }

  public void doTest(String fileName, @Nullable String expectedDoc) {
    copyResources();
    final VirtualFile f = myFixture.copyFileToProject(getTestDataPath() + "/javadoc/" + fileName,
                                                      "src/com/foo/Activity.java");
    myFixture.configureFromExistingVirtualFile(f);
    PsiElement element = myFixture.getFile().findElementAt(myFixture.getEditor().getCaretModel().getOffset());
    assert element != null;

    DocumentationProvider provider = DocumentationManager.getProviderFromElement(element);
    assertEquals(expectedDoc, provider.generateDoc(element, element));
  }

  public void test1() {
    doTest("Activity1.java", "<html><body>Application Name</body></html>");
  }

  public void test2() {
    // Use LocaleManagerTest#checkEncoding to get Unicode encoding
    doTest("Activity2.java", "<html><body><table>" +
                             "<tr><td>ta</td><td>\u0bb0\u0ba4\u0bcd\u0ba4\u0bc1</td></tr>" +
                             "<tr><td>zh-rTW</td><td>\u53d6\u6d88</td></tr>" +
                             "<tr><td>Default</td><td>Cancel</td></tr>" +
                             "</table><body></html>");
  }

  public void test3() {
    doTest("Activity3.java", null);
  }
}
