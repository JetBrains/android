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
import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativeAssignment
import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativeBlock
import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativeFile
import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativeLiteral
import com.google.common.truth.Truth.assertThat
import com.intellij.lang.LanguageASTFactory
import com.intellij.testFramework.ParsingTestCase
import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativeAbstractFactory

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

  fun testAssignWithEnum(){
    doTest(true, true)
  }

  fun testNulls(){
    doTest(true, true)
  }

  fun testMultifunctionExpression(){
    doTest(true, true)
  }

  fun testPropertyReceiverFactory(){
    doTest(true, true)
  }

  fun testPropertyDotNewlineFunction() {
    doTest(true, false)
    val psiFile = parseFile(name, loadFile("$testName.$myFileExt"))
    assertThat(psiFile).isInstanceOf(DeclarativeFile::class.java)
    val entries = (psiFile as DeclarativeFile).getEntries()
    assertThat(entries).hasSize(1)

    assertThat(entries[0]).isInstanceOf(DeclarativeAbstractFactory::class.java)
    assertThat((entries[0] as DeclarativeAbstractFactory).identifier.name).isEqualTo("include")

    val argumentsList = (entries[0] as DeclarativeAbstractFactory).argumentsList?.argumentList
    assertThat(argumentsList).isNotNull()
    assertThat(argumentsList).hasSize(1)
    assertThat(argumentsList!![0].value).isInstanceOf(DeclarativeLiteral::class.java)
    // getValue removes quotation marks
    assertThat((argumentsList[0].value as DeclarativeLiteral).value).isEqualTo(":app")
  }

  fun testEscapedAndSingleQuote() {
    doTest(true, true)

    val psiFile = parseFile(name, loadFile("$testName.$myFileExt"))
    assertThat(psiFile).isInstanceOf(DeclarativeFile::class.java)
    val entries = (psiFile as DeclarativeFile).getEntries()

    assertThat(entries).hasSize(1)
    assertThat(entries[0]).isInstanceOf(DeclarativeBlock::class.java)
    // checking that getName removes wrapping ``
    assertThat((entries[0] as DeclarativeBlock).identifier!!.name).isEqualTo("block")

    val blockEntries = (entries[0] as DeclarativeBlock).entries
    assertThat(blockEntries).hasSize(3)

    assertThat(blockEntries[0]).isInstanceOf(DeclarativeAbstractFactory::class.java)
    // checking that getName removes wrapping ``
    assertThat((blockEntries[0] as DeclarativeAbstractFactory).identifier.name).isEqualTo("function")

    assertThat(blockEntries[1]).isInstanceOf(DeclarativeBlock::class.java)
    // checking that getName removes wrapping `` and unescapes
    assertThat((blockEntries[1] as DeclarativeBlock).identifier!!.name).isEqualTo("AnotherFunction")
    assertThat((blockEntries[1] as DeclarativeBlock).embeddedFactory).isNotNull()

    val factoryBlock = (blockEntries[1] as DeclarativeBlock).embeddedFactory!!
    assertThat(factoryBlock.argumentsList).isNotNull()
    assertThat(factoryBlock.argumentsList!!.argumentList).hasSize(1)
    val argument = factoryBlock.argumentsList!!.argumentList[0]

    assertThat(argument.value).isInstanceOf(DeclarativeLiteral::class.java)
    // getValue removes quotation marks
    assertThat((argument.value as DeclarativeLiteral).value).isEqualTo("string")

    val assignment = (blockEntries[2] as DeclarativeAssignment)
    assertThat(assignment.identifier.name).isEqualTo("assignment.my")
    assertThat((assignment.value as DeclarativeLiteral).value).isEqualTo(1)
  }
  override fun getTestDataPath(): String = resolveWorkspacePath("tools/adt/idea/gradle-declarative-lang/testData").toString()
}