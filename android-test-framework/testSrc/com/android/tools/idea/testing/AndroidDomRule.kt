/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.testing

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.jetbrains.android.inspections.AndroidDomInspection
import org.jetbrains.android.inspections.AndroidElementNotAllowedInspection
import org.jetbrains.android.inspections.AndroidUnknownAttributeInspection
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain

/**
 * A rule useful for running semantic highlighting / completion tests against various Android
 * classes.
 *
 * This rule isn't useful without a fixture - to get one, using a JUnit [RuleChain] is
 * recommended:
 *
 *     private val projectRule: AndroidProjectRule = AndroidProjectRule.withSdk().initAndroid(true)
 *     private val domRule = AndroidDomRule("res/layout", () -> projectRule.fixture)
 *
 *     @get:Rule
 *     public val ruleChain = RuleChain.outerRule(projectRule).around(domRule)
 *
 * *Note: This class is a JUnit4 port of `AndroidDomTestCase`.*
 */
class AndroidDomRule(
  /**
   * The root path in the Android project that all files we're testing / highlighting should exist
   * under, e.g. 'res/layout'. The location of a file gives the system more context about which
   * sort of completions should be enabled.
   */
  private val projectRoot: String,

  /**
   * A callback that returns a completed fixture. This won't get called until [before] happens,
   * allowing another rule time to initialize the fixture.
   */
  private val fixtureProvider: () -> CodeInsightTestFixture
) : ExternalResource() {

  private lateinit var fixture: CodeInsightTestFixture

  override fun before() {
    fixture = fixtureProvider()

    fixture.enableInspections(AndroidDomInspection::class.java,
                              AndroidUnknownAttributeInspection::class.java,
                              AndroidElementNotAllowedInspection::class.java)
  }

  /**
   * Given a properly formatted file with highlight warnings and errors marked within it, verify
   * that its state matches what actually happens when you execute a highlight pass.
   */
  fun testHighlighting(file: String) {
    val virtualFile = fixture.copyFileToProject(file, "$projectRoot/$file")
    fixture.configureFromExistingVirtualFile(virtualFile)
    fixture.checkHighlighting(true, false, false)
  }

  /**
   * Assert that expected code completion happens, given a before / after set of files, with the
   * prior indicating a caret position and the latter indicating what the file should look like
   * after executing a completion action at that caret position.
   *
   * Note: This test is useful if you expect exactly one completion possibility. If you want to
   * test for multiple possible completion matches, use [getCompletionResults] instead.
   */
  fun testCompletion(fileBefore: String, fileAfter: String) {
    val virtualFile = fixture.copyFileToProject(fileBefore, "$projectRoot/$fileBefore")
    fixture.configureFromExistingVirtualFile(virtualFile)
    fixture.complete(CompletionType.BASIC)
    fixture.checkResultByFile(fileAfter)
  }

  /**
   * Given a file which indicates a caret position, return the list of completion choices that
   * should show up when executing a completion action at that caret position.
   */
  fun getCompletionResults(file: String): List<String> {
    val virtualFile = fixture.copyFileToProject(file, "$projectRoot/$file")
    fixture.configureFromExistingVirtualFile(virtualFile)
    fixture.complete(CompletionType.BASIC)
    return fixture.lookupElementStrings!!
  }

}
