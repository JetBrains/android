/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.gradle.dcl.lang.parser

import com.android.test.testutils.TestUtils.resolveWorkspacePath
import com.android.tools.idea.gradle.dcl.lang.DeclarativeParserDefinition
import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativeASTFactory
import com.intellij.lang.LanguageASTFactory
import com.intellij.testFramework.ParsingTestCase

class DeclarativeParserTest : ParsingTestCase("dcl/parser", "dcl", DeclarativeParserDefinition()) {

  override fun setUp() {
    super.setUp()
    addExplicitExtension(LanguageASTFactory.INSTANCE, myLanguage, DeclarativeASTFactory())
  }

  fun testSemi() {
    doTest(true, true)
  }

  fun testTwoAssignmentsWithSemi() {
    doTest(true, true)
  }

  fun testMultipleLinesWithSemi() {
    doTest(true, true)
  }

  fun testAssignment() {
    doTest(true, true)
  }

  fun testBlock() {
    doTest(true, true)
  }

  fun testFactory() {
    doTest(true, true)
  }

  fun testFactoryBlock() {
    doTest(true, true)
  }

  fun testStringHasOnlyOneLine() {
    doTest(true, true)
  }

  fun testStringHandleEscapeQuotes() {
    doTest(true, true)
  }

  fun testMultiLineString() {
    doTest(true, true)
  }

  fun testMultiLineStringNoClosingQuotes() {
    doTest(true, true)
  }

  fun testZeroArgumentFactory() {
    doTest(true, true)
  }

  fun testFactoryWithIdentifiers() {
    doTest(true, true)
  }

  fun testOnlyComments() {
    doTest(true,true)
  }

  fun testCommentsAfterEntity() {
    doTest(true,true)
  }

  fun testCommentInsideBlock() {
    doTest(true,true)
  }

  fun testCommentInsideBlock2() {
    doTest(true,true)
  }

  fun testNumbers() {
    doTest(true,true)
  }

  fun testMultiArgumentFactory() {
    doTest(true,true)
  }

  fun testNewLinePropertyAndProperty() {
    doTest(true, false)
  }

  fun testNewLinePropertyAndBlock() {
    doTest(true,false)
  }

  fun testNewLineComplexTest() {
    doTest(true,true)
  }

  fun testNewLineBlockAndBlock() {
    doTest(true,false)
  }

  fun testNewLineFunctionAndProperty() {
    doTest(true,false)
  }

  fun testNewLineFunctionAndFunction() {
    doTest(true,false)
  }

  fun testSemiEmbedded() {
    doTest(true,true)
  }

  fun testNewLineFunctionAndBlock(){
    doTest(true,false)
  }

  fun testOneLineBlock(){
    doTest(true,true)
  }

  override fun getTestDataPath(): String = resolveWorkspacePath("tools/adt/idea/gradle-declarative-lang/testData").toString()
}