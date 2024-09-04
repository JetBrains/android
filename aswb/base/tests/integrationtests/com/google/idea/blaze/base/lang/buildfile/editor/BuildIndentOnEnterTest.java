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
package com.google.idea.blaze.base.lang.buildfile.editor;

import com.google.common.base.Joiner;
import com.google.idea.blaze.base.lang.buildfile.BuildFileIntegrationTestCase;
import com.intellij.openapi.actionSystem.IdeActions;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests that indents are inserted correctly when enter is pressed. */
@RunWith(JUnit4.class)
public class BuildIndentOnEnterTest extends BuildFileIntegrationTestCase {

  private void setInput(String... fileContents) {
    testFixture.configureByText("BUILD", Joiner.on("\n").join(fileContents));
  }

  private void pressEnterAndAssertResult(String... resultingFileContents) {
    editorTest.pressButton(IdeActions.ACTION_EDITOR_ENTER);
    testFixture.getFile().getText();
    testFixture.checkResult(Joiner.on("\n").join(resultingFileContents));
  }

  @Test
  public void testSimpleIndent() {
    setInput("a=1<caret>");
    pressEnterAndAssertResult("a=1", "<caret>");
  }

  @Test
  public void testAlignInListMiddle() {
    setInput("target = [a,<caret>", "          c]");
    pressEnterAndAssertResult("target = [a,", "          <caret>", "          c]");
  }

  @Test
  public void testNoAlignAfterList() {
    setInput("target = [", "    arg", "]<caret>");
    pressEnterAndAssertResult("target = [", "    arg", "]", "<caret>");
  }

  @Test
  public void testAlignInDict() {
    setInput("some_call({'aaa': 'v1',<caret>})");
    pressEnterAndAssertResult("some_call({'aaa': 'v1',", "           <caret>})");
  }

  @Test
  public void testAlignInDictInParams() { // PY-1947
    setInput("foobar({<caret>})");
    pressEnterAndAssertResult("foobar({", "    <caret>", "})");
  }

  @Test
  public void testAlignInEmptyList() {
    setInput("target = [<caret>]");
    pressEnterAndAssertResult("target = [", "    <caret>", "]");
  }

  @Test
  public void testAlignInEmptyParens() {
    setInput("foo(<caret>)");
    pressEnterAndAssertResult("foo(", "    <caret>", ")");
  }

  @Test
  public void testAlignInEmptyDict() {
    setInput("{<caret>}");
    pressEnterAndAssertResult("{", "    <caret>", "}");
  }

  @Test
  public void testAlignInEmptyTuple() {
    setInput("(<caret>)");
    pressEnterAndAssertResult("(", "    <caret>", ")");
  }

  @Test
  public void testEnterInNonEmptyArgList() {
    setInput("func(<caret>params=1)");
    pressEnterAndAssertResult("func(", "    <caret>params=1)");
  }

  @Test
  public void testNoIndentAfterTuple() {
    setInput("()<caret>");
    pressEnterAndAssertResult("()", "<caret>");
  }

  @Test
  public void testNoIndentAfterList() {
    setInput("target = [1, 2]<caret>");
    pressEnterAndAssertResult("target = [1, 2]", "<caret>");
  }

  @Test
  public void testNoIndentAfterDict() {
    setInput("target = {}<caret>");
    pressEnterAndAssertResult("target = {}", "<caret>");
  }

  @Test
  public void testEmptyFuncallStart() {
    setInput("func(<caret>", ")");
    pressEnterAndAssertResult("func(", "    <caret>", ")");
  }

  @Test
  public void testEmptyFuncallAfterNewlineNoIndent() {
    setInput("func(", "<caret>)");
    pressEnterAndAssertResult("func(", "", "<caret>)");
  }

  @Test
  public void testEmptyFuncallAfterNewlineWithIndent() {
    setInput("func(", "    <caret>", ")");
    pressEnterAndAssertResult("func(", "    ", "    <caret>", ")");
  }

  @Test
  public void testFuncallAfterFirstArg() {
    setInput("func(", "    arg1,<caret>", ")");
    pressEnterAndAssertResult("func(", "    arg1,", "    <caret>", ")");
  }

  @Test
  public void testFuncallFirstArgOnSameLine() {
    setInput("func(arg1, arg2,<caret>");
    pressEnterAndAssertResult("func(arg1, arg2,", "     <caret>");
  }

  @Test
  public void testFuncallFirstArgOnSameLineWithClosingBrace() {
    setInput("func(arg1, arg2,<caret>)");
    pressEnterAndAssertResult("func(arg1, arg2,", "     <caret>)");
  }

  @Test
  public void testNonEmptyDict() {
    setInput("{key1 : value1,<caret>}");
    pressEnterAndAssertResult("{key1 : value1,", " <caret>}");
  }

  @Test
  public void testNonEmptyDictFirstArgIndented() {
    setInput("{", "    key1 : value1,<caret>" + "}");
    pressEnterAndAssertResult("{", "    key1 : value1,", "    <caret>" + "}");
  }

  @Test
  public void testEmptyDictAlreadyIndented() {
    setInput("{", "    <caret>" + "}");
    pressEnterAndAssertResult("{", "    ", "    <caret>" + "}");
  }

  @Test
  public void testEmptyParamIndent() {
    setInput("def fn(<caret>)");
    pressEnterAndAssertResult("def fn(", "        <caret>", ")");
  }

  @Test
  public void testNonEmptyParamIndent() {
    setInput("def fn(param1,<caret>)");
    pressEnterAndAssertResult("def fn(param1,", "       <caret>)");
  }

  @Test
  public void testFunctionDefAfterColon() {
    setInput("def fn():<caret>");
    pressEnterAndAssertResult("def fn():", "    <caret>");
  }

  @Test
  public void testFunctionDefSingleStatement() {
    setInput("def fn():stmt<caret>");
    pressEnterAndAssertResult("def fn():stmt", "<caret>");
  }

  @Test
  public void testFunctionDefAfterFirstSuiteStatement() {
    setInput("def fn():", "  stmt1<caret>");
    pressEnterAndAssertResult("def fn():", "  stmt1", "  <caret>");
  }

  @Test
  public void testNoIndentAfterSuiteDedentOnEmptyLine() {
    setInput("def fn():", "  stmt1", "  stmt2", "<caret>");
    pressEnterAndAssertResult("def fn():", "  stmt1", "  stmt2", "", "<caret>");
  }

  @Test
  public void testIndentAfterIf() {
    setInput("if condition:<caret>");
    pressEnterAndAssertResult("if condition:", "    <caret>");
  }

  @Test
  public void testNoIndentAfterIfPlusStatement() {
    setInput("if condition:stmt<caret>");
    pressEnterAndAssertResult("if condition:stmt", "<caret>");
  }

  @Test
  public void testIndentAfterElseIf() {
    setInput("if condition:", "    stmt", "elif:<caret>");
    pressEnterAndAssertResult("if condition:", "    stmt", "elif:", "    <caret>");
  }

  @Test
  public void testNoIndentAfterElseIfPlusStatement() {
    setInput("if condition:", "    stmt", "elif:stmt<caret>");
    pressEnterAndAssertResult("if condition:", "    stmt", "elif:stmt", "<caret>");
  }

  @Test
  public void testIndentAfterElse() {
    setInput("if condition:", "    stmt", "else:<caret>");
    pressEnterAndAssertResult("if condition:", "    stmt", "else:", "    <caret>");
  }

  @Test
  public void testNoIndentAfterElsePlusStatement() {
    setInput("if condition:", "    stmt", "else:stmt<caret>");
    pressEnterAndAssertResult("if condition:", "    stmt", "else:stmt", "<caret>");
  }

  @Test
  public void testIndentAfterForColon() {
    setInput("for x in list:<caret>");
    pressEnterAndAssertResult("for x in list:", "    <caret>");
  }

  @Test
  public void testNoIndentAfterForPlusStatement() {
    setInput("for x in list:do_action<caret>");
    pressEnterAndAssertResult("for x in list:do_action", "<caret>");
  }

  @Test
  public void testCommonRuleCase1() {
    setInput("java_library(", "    name = 'lib'", "    srcs = [<caret>]");
    pressEnterAndAssertResult(
        "java_library(", "    name = 'lib'", "    srcs = [", "        <caret>", "    ]");
  }

  @Test
  public void testCommonRuleCase2() {
    setInput(
        "java_library(", "    name = 'lib'", "    srcs = [", "        'source',<caret>", "    ]");
    pressEnterAndAssertResult(
        "java_library(",
        "    name = 'lib'",
        "    srcs = [",
        "        'source',",
        "        <caret>",
        "    ]");
  }

  @Test
  public void testCommonRuleCase3() {
    setInput("java_library(", "    name = 'lib'", "    srcs = ['first',<caret>]");
    pressEnterAndAssertResult(
        "java_library(", "    name = 'lib'", "    srcs = ['first',", "            <caret>]");
  }

  @Test
  public void testDedentAfterReturn() {
    setInput("def fn():", "  return None<caret>");
    pressEnterAndAssertResult("def fn():", "  return None", "<caret>");
  }

  @Test
  public void testDedentAfterEmptyReturn() {
    setInput("def fn():", "  return<caret>");
    pressEnterAndAssertResult("def fn():", "  return", "<caret>");
  }

  @Test
  public void testDedentAfterReturnWithTrailingWhitespace() {
    setInput("def fn():", "  return<caret>   ");
    pressEnterAndAssertResult("def fn():", "  return", "<caret>");
  }

  @Test
  public void testDedentAfterComplexReturn() {
    setInput("def fn():", "  return a == b<caret>");
    pressEnterAndAssertResult("def fn():", "  return a == b", "<caret>");
  }

  @Test
  public void testDedentAfterPass() {
    setInput("def fn():", "  pass<caret>");
    pressEnterAndAssertResult("def fn():", "  pass", "<caret>");
  }

  @Test
  public void testDedentAfterPassInLoop() {
    setInput("def fn():", "    for a in (1,2,3):", "        pass<caret>");
    pressEnterAndAssertResult("def fn():", "    for a in (1,2,3):", "        pass", "    <caret>");
  }

  // regression test for b/29564041
  @Test
  public void testNoExceptionPressingEnterAtStartOfFile() {
    setInput("#<caret>");
    pressEnterAndAssertResult("#", "<caret>");
  }
}
