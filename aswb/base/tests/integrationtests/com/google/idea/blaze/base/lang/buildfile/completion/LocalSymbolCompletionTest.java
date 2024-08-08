/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.lang.buildfile.completion;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.base.Joiner;
import com.google.idea.blaze.base.lang.buildfile.BuildFileIntegrationTestCase;
import com.intellij.psi.PsiFile;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests code completion works with general symbols in scope. */
@RunWith(JUnit4.class)
public class LocalSymbolCompletionTest extends BuildFileIntegrationTestCase {

  private PsiFile setInput(String... fileContents) {
    return testFixture.configureByText("BUILD", Joiner.on("\n").join(fileContents));
  }

  private void assertResult(String... resultingFileContents) {
    testFixture.getFile().getText();
    testFixture.checkResult(Joiner.on("\n").join(resultingFileContents));
  }

  @Test
  public void testLocalVariable() {
    setInput("var_name = [a, b]", "def function(name, deps, srcs):", "  var_n<caret>");

    editorTest.completeIfUnique();

    assertResult("var_name = [a, b]", "def function(name, deps, srcs):", "  var_name<caret>");
  }

  @Test
  public void testLocalFunction() {
    setInput("def fnName():return True", "def function(name, deps, srcs):", "  fnN<caret>");

    editorTest.completeIfUnique();

    assertResult("def fnName():return True", "def function(name, deps, srcs):", "  fnName<caret>");
  }

  @Test
  public void testNoCompletionAfterDot() {
    setInput("var_name = [a, b]", "def function(name, deps, srcs):", "  ext.var_na<caret>");

    String[] completionItems = editorTest.getCompletionItemsAsStrings();
    assertThat(completionItems).isEmpty();
  }

  @Test
  public void testFunctionParam() {
    setInput("def test(var_name):", "  var_na<caret>");

    editorTest.completeIfUnique();

    assertResult("def test(var_name):", "  var_name<caret>");
  }

  // b/28912523: when symbol is present in multiple assignment statements, should only be
  // included once in the code-completion dialog
  @Test
  public void testSymbolAssignedMultipleTimes() {
    setInput("var_name = 1", "var_name = 2", "var_name = 3", "var_na<caret>");

    editorTest.completeIfUnique();

    assertResult("var_name = 1", "var_name = 2", "var_name = 3", "var_name<caret>");
  }

  @Test
  public void testSymbolDefinedOutsideScope() {
    setInput("var_na<caret>", "var_name = 1");

    assertThat(editorTest.getCompletionItemsAsStrings()).isEmpty();
  }

  @Test
  public void testSymbolDefinedOutsideScope2() {
    setInput("def fn():", "  var_name = 1", "var_na<caret>");

    assertThat(testFixture.completeBasic()).isEmpty();
  }

  @Test
  public void testSymbolDefinedOutsideScope3() {
    setInput("for var_name in (1, 2, 3): print var_name", "var_na<caret>");

    assertThat(testFixture.completeBasic()).isEmpty();
  }
}
