/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.testartifacts.scopes;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.psi.PsiClass;

public class TestArtifactsRenameTest extends TestArtifactsTestCase {
  private static final String MY_CLASS_TEXT = "class My<caret>Class {}";

  @Override
  protected boolean shouldRunTest() {
    // Do not run tests on Windows (see http://b.android.com/222904)
    return !SystemInfo.isWindows && super.shouldRunTest();
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    // Do not use single import, renaming will followed by import reorganizing which will remove all unused import statement
    setUnitTestFileContent("Test.java", "class Test extends MyClass {}");
    setAndroidTestFileContent("AndroidTest.java", "class AndroidTest extends MyClass {}");
  }

  public void testRenameInBothTests() throws Exception {
    setCommonFileContent("MyClass.java", MY_CLASS_TEXT);
    renameAndCheckResults(2);
  }

  public void testRenameInOnlyUnitTests() throws Exception {
    setUnitTestFileContent("MyClass.java", MY_CLASS_TEXT);

    renameAndCheckResults(1);
  }

  public void testRenameInOnlyAndroidTests() throws Exception {
    setUnitTestFileContent("MyClass.java", MY_CLASS_TEXT);
    renameAndCheckResults(1);
  }

  private void renameAndCheckResults(int expectedUsages) {
    myFixture.renameElementAtCaret("MyNewClass");

    PsiClass newClass = myFixture.findElementByText("MyNewClass", PsiClass.class);
    assertNotNull(newClass);

    assertSize(expectedUsages, myFixture.findUsages(newClass));
  }
}
