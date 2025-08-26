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

import static com.google.common.truth.Truth.assertThat;

import com.intellij.psi.PsiElement;
import com.intellij.testFramework.RunsInEdt;
import org.jetbrains.annotations.Nullable;
import org.junit.Rule;
import org.junit.Test;

@RunsInEdt
public class TestArtifactsResolveTest {
  @Rule
  public TestArtifactsProjectRule rule = new TestArtifactsProjectRule();

  @Test
  public void testDependencyResolvableOnlyInUnitTest() throws Exception {
    // Junit is a dependency for android test, defined in project's build.gradle.
    String importString = "import org.junit.Ass<caret>ert;";

    rule.setUnitTestFileContent("Test.java", importString);
    assertThat(resolveReferenceAtCaret()).isNotNull();

    rule.setAndroidTestFileContent("AndroidTest.java", importString);
    assertThat(resolveReferenceAtCaret()).isNull();
  }

  @Test
  public void testDependencyResolvableOnlyInAndroidTest() throws Exception {
    // Gson is a dependency for android test, defined in project's build.gradle.
    String importString = "import com.google.gson.Gs<caret>on;";

    rule.setUnitTestFileContent("Test.java", importString);
    assertThat(resolveReferenceAtCaret()).isNull();

    rule.setAndroidTestFileContent("AndroidTest.java", importString);
    assertThat(resolveReferenceAtCaret()).isNotNull();
  }

  @Test
  public void testSourceResolvableInBothTests() throws Exception {
    // Create class located in main source
    rule.setCommonFileContent("MyClass.java", "class MyClass {}");

    String importString = "import MyC<caret>lass;";
    rule.setUnitTestFileContent("Test.java", importString);
    assertThat(resolveReferenceAtCaret()).isNotNull();

    rule.setAndroidTestFileContent("AndroidTest.java", importString);
    assertThat(resolveReferenceAtCaret()).isNotNull();
  }

  @Test
  public void testSourceResolvableOnlyInUnitTest() throws Exception {
    // Create class located in unit test source
    rule.setUnitTestFileContent("MyClass.java", "class MyClass {}");

    String importString = "import MyC<caret>lass;";
    rule.setUnitTestFileContent("Test.java", importString);;
    assertThat(resolveReferenceAtCaret()).isNotNull();

    rule.setAndroidTestFileContent("AndroidTest.java", importString);
    assertThat(resolveReferenceAtCaret()).isNull();
  }

  @Test
  public void testSourceResolvableOnlyInAndroidTest() throws Exception {
    // Create class located in android test source
    rule.setAndroidTestFileContent("MyClass.java", "class MyClass {}");

    String importString = "import MyC<caret>lass;";
    rule.setUnitTestFileContent("Test.java", importString);;
    assertThat(resolveReferenceAtCaret()).isNull();

    rule.setAndroidTestFileContent("AndroidTest.java", importString);
    assertThat(resolveReferenceAtCaret()).isNotNull();
  }

  @Test
  public void testMultiModuleSourceResolvableInBothTests() throws Exception {
    rule.setFileContent("module2/src/main/java/MyClass.java", "class MyClass {}");

    String importString = "import MyC<caret>lass;";
    rule.setFileContent("module3/src/test/java/Test.java", importString);
    assertThat(resolveReferenceAtCaret()).isNotNull();

    rule.setFileContent("module3/src/androidTest/java/AndroidTest.java", importString);
    assertThat(resolveReferenceAtCaret()).isNotNull();
  }

  @Test
  public void testMultiModuleAndroidSourceNotResolvableInTests() throws Exception {
    rule.setFileContent("module2/src/androidTest/java/MyClass.java", "class MyClass {}");

    String importString = "import MyC<caret>lass;";
    rule.setFileContent("module3/src/test/java/Test.java", importString);
    assertThat(resolveReferenceAtCaret()).isNull();

    rule.setFileContent("module3/src/androidTest/java/AndroidTest.java", importString);
    assertThat(resolveReferenceAtCaret()).isNull();
  }

  @Test
  public void testMultiModuleUnitSourceNotResolvableInTests() throws Exception {
    rule.setFileContent("module2/src/test/java/MyClass.java", "class MyClass {}");

    String importString = "import MyC<caret>lass;";
    rule.setFileContent("module3/src/androidTest/java/Test.java", importString);
    assertThat(resolveReferenceAtCaret()).isNull();

    rule.setFileContent("module3/src/androidTest/java/AndroidTest.java", importString);
    assertThat(resolveReferenceAtCaret()).isNull();
  }

  @Nullable
  private PsiElement resolveReferenceAtCaret() {
    return rule.getFixture().getReferenceAtCaretPositionWithAssertion().resolve();
  }
}
