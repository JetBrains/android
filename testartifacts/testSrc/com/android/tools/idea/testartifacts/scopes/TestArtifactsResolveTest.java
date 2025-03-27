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
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

public class TestArtifactsResolveTest extends TestArtifactsTestCase {

  @Override
  protected boolean shouldRunTest() {
    // Do not run tests on Windows (see http://b.android.com/222904)
    return !SystemInfo.isWindows && super.shouldRunTest();
  }

  public void testDependencyResolvableOnlyInUnitTest() throws Exception {
    // Junit is a dependency for android test, defined in project's build.gradle.
    String importString = "import org.junit.Ass<caret>ert;";

    setUnitTestFileContent("Test.java", importString);
    assertNotNull(resolveReferenceAtCaret());

    setAndroidTestFileContent("AndroidTest.java", importString);
    assertNull(resolveReferenceAtCaret());
  }

  public void testDependencyResolvableOnlyInAndroidTest() throws Exception {
    // Gson is a dependency for android test, defined in project's build.gradle.
    String importString = "import com.google.gson.Gs<caret>on;";

    setUnitTestFileContent("Test.java", importString);
    assertNull(resolveReferenceAtCaret());

    setAndroidTestFileContent("AndroidTest.java", importString);
    assertNotNull(resolveReferenceAtCaret());
  }

  // Flaky test, reactivate when investigated (http://b.android.com/226541)
  public void testSourceResolvableInBothTests() throws Exception {
    // Create class located in main source
    setCommonFileContent("MyClass.java", "class MyClass {}");

    String importString = "import MyC<caret>lass;";
    setUnitTestFileContent("Test.java", importString);
    assertNotNull(resolveReferenceAtCaret());

    setAndroidTestFileContent("AndroidTest.java", importString);
    assertNotNull(resolveReferenceAtCaret());
  }

  // Flaky test, reactivate when investigated (http://b.android.com/226541)
  public void testSourceResolvableOnlyInUnitTest() throws Exception {
    // Create class located in unit test source
    setUnitTestFileContent("MyClass.java", "class MyClass {}");

    String importString = "import MyC<caret>lass;";
    setUnitTestFileContent("Test.java", importString);;
    assertNotNull(resolveReferenceAtCaret());

    setAndroidTestFileContent("AndroidTest.java", importString);
    assertNull(resolveReferenceAtCaret());
  }

  // Flaky test, reactivate when investigated (http://b.android.com/226541)
  public void testSourceResolvableOnlyInAndroidTest() throws Exception {
    // Create class located in android test source
    setAndroidTestFileContent("MyClass.java", "class MyClass {}");

    String importString = "import MyC<caret>lass;";
    setUnitTestFileContent("Test.java", importString);;
    assertNull(resolveReferenceAtCaret());

    setAndroidTestFileContent("AndroidTest.java", importString);
    assertNotNull(resolveReferenceAtCaret());
  }

  // Flaky test, reactivate when investigated (http://b.android.com/226541)
  public void testMultiModuleSourceResolvableInBothTests() throws Exception {
    setFileContent("module2/src/main/java/MyClass.java", "class MyClass {}");

    String importString = "import MyC<caret>lass;";
    setFileContent("module3/src/test/java/Test.java", importString);
    assertNotNull(resolveReferenceAtCaret());

    setFileContent("module3/src/androidTest/java/AndroidTest.java", importString);
    assertNotNull(resolveReferenceAtCaret());
  }

  // Flaky test, reactivate when investigated (http://b.android.com/226541)
  public void testMultiModuleAndroidSourceNotResolvableInTests() throws Exception {
    setFileContent("module2/src/androidTest/java/MyClass.java", "class MyClass {}");

    String importString = "import MyC<caret>lass;";
    setFileContent("module3/src/test/java/Test.java", importString);
    assertNull(resolveReferenceAtCaret());

    // TODO depended android tests should also have no access to the code
    //setFileContent("module3/src/androidTest/java/AndroidTest.java", importString);
    //assertNull(resolveReferenceAtCaret());
  }

  // Flaky test, reactivate when investigated (http://b.android.com/226541)
  public void testMultiModuleUnitSourceNotResolvableInTests() throws Exception {
    setFileContent("module2/src/test/java/MyClass.java", "class MyClass {}");

    String importString = "import MyC<caret>lass;";
    setFileContent("module3/src/androidTest/java/Test.java", importString);
    assertNull(resolveReferenceAtCaret());

    // TODO depended unit tests should also have no access to the code
    //setFileContent("module3/src/androidTest/java/AndroidTest.java", importString);
    //assertNull(resolveReferenceAtCaret());
  }


  @Nullable
  private PsiElement resolveReferenceAtCaret() {
    return myFixture.getReferenceAtCaretPositionWithAssertion().resolve();
  }
}
