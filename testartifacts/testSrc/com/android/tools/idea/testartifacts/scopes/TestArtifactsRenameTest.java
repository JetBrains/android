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

import com.intellij.psi.PsiClass;
import com.intellij.testFramework.RunsInEdt;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

@RunsInEdt
public class TestArtifactsRenameTest {
  private static final String MY_CLASS_TEXT = "class My<caret>Class {}";

  @Rule
  public TestArtifactsProjectRule rule = new TestArtifactsProjectRule();

  @Before
  public void setUp() throws Exception {
    // Do not use single import, renaming will followed by import reorganizing which will remove all unused import statement
    rule.setUnitTestFileContent("Test.java", "class Test extends MyClass {}");
    rule.setAndroidTestFileContent("AndroidTest.java", "class AndroidTest extends MyClass {}");
  }

  @Test
  public void testRenameInBothTests() throws Exception {
    rule.setCommonFileContent("MyClass.java", MY_CLASS_TEXT);
    renameAndCheckResults(2);
  }

  @Test
  public void testRenameInOnlyUnitTests() throws Exception {
    rule.setUnitTestFileContent("MyClass.java", MY_CLASS_TEXT);
    renameAndCheckResults(1);
  }

  @Test
  public void testRenameInOnlyAndroidTests() throws Exception {
    rule.setUnitTestFileContent("MyClass.java", MY_CLASS_TEXT);
    renameAndCheckResults(1);
  }

  private void renameAndCheckResults(int expectedUsages) {
    rule.getFixture().renameElementAtCaret("MyNewClass");
    PsiClass newClass = rule.getFixture().findElementByText("MyNewClass", PsiClass.class);
    assertThat(newClass).isNotNull();
    assertThat(rule.getFixture().findUsages(newClass)).hasSize(expectedUsages);
  }
}
