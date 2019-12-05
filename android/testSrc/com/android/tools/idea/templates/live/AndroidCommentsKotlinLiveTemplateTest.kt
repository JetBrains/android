/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.templates.live

import com.android.tools.idea.templates.live.LiveTemplateTestCase.Location.CLASS
import com.android.tools.idea.templates.live.LiveTemplateTestCase.Location.COMMENT
import com.android.tools.idea.templates.live.LiveTemplateTestCase.Location.EXPRESSION
import com.android.tools.idea.templates.live.LiveTemplateTestCase.Location.OBJECT_DECLARATION
import com.android.tools.idea.templates.live.LiveTemplateTestCase.Location.STATEMENT
import com.android.tools.idea.templates.live.LiveTemplateTestCase.Location.TOP_LEVEL
import com.intellij.openapi.util.Clock
import com.intellij.util.text.DateFormatUtil

class AndroidCommentsKotlinLiveTemplateTest : LiveTemplateTestCase() {

  private val TEMPLATE_CFALSE = "cfalse"

  fun testCfalse_onTopLevel() = testNotOnTopLevel(TEMPLATE_CFALSE)

  fun testCfalse_inClass() = testNotInClass(TEMPLATE_CFALSE)

  fun testCfalse_inCompanion() = testNotInCompanion(TEMPLATE_CFALSE)

  fun testCfalse_inComment() {
    // Given:
    addPreparedFileToProject(COMMENT)

    // When:
    insertTemplate(TEMPLATE_CFALSE)

    // Then:
    myFixture.checkResult(insertIntoPsiFileAt(COMMENT, content = "`false`"))
  }

  fun testCfalse_inStatement() = testNotInStatement(TEMPLATE_CFALSE)

  fun testCfalse_inExpression() = testNotInExpression(TEMPLATE_CFALSE)

  private val TEMPLATE_CTRUE = "ctrue"

  fun testCtrue_onTopLevel() = testNotOnTopLevel(TEMPLATE_CTRUE)

  fun testCtrue_inClass() = testNotInClass(TEMPLATE_CTRUE)

  fun testCtrue_inCompanion() = testNotInCompanion(TEMPLATE_CTRUE)

  fun testCtrue_inComment() {
    // Given:
    addPreparedFileToProject(COMMENT)

    // When:
    insertTemplate(TEMPLATE_CTRUE)

    // Then:
    myFixture.checkResult(insertIntoPsiFileAt(COMMENT, content = "`true`"))
  }

  fun testCtrue_inStatement() = testNotInStatement(TEMPLATE_CTRUE)

  fun testCtrue_inExpression() = testNotInExpression(TEMPLATE_CTRUE)

  private fun testInsertedComment(location: Location, templateName: String) {
    // Given:
    addPreparedFileToProject(location)

    // When:
    insertTemplate(templateName)
    myFixture.type("something\n")

    // Then:
    val today = DateFormatUtil.formatDate(Clock.getTime())
    myFixture.checkResult(insertIntoPsiFileAt(location, content = "// ${templateName.toUpperCase()}: $today something "))
  }

  private val TEMPLATE_TODO = "todo"

  private fun testTodo(location: Location) = testInsertedComment(location, TEMPLATE_TODO)

  fun testTodo_onTopLevel() = testTodo(TOP_LEVEL)

  fun testTodo_inClass() = testTodo(CLASS)

  fun testTodo_inCompanion() = testTodo(OBJECT_DECLARATION)

  fun testTodo_inComment() = testNotInComment(TEMPLATE_TODO)

  fun testTodo_inStatement() = testTodo(STATEMENT)

  fun testTodo_inExpression() = testTodo(EXPRESSION)

  private val TEMPLATE_FIXME = "fixme"

  private fun testFixme(location: Location) = testInsertedComment(location, TEMPLATE_FIXME)

  fun testFixme_onTopLevel() = testFixme(TOP_LEVEL)

  fun testFixme_inClass() = testFixme(CLASS)

  fun testFixme_inCompanion() = testFixme(OBJECT_DECLARATION)

  fun testFixme_inComment() = testNotInComment(TEMPLATE_FIXME)

  fun testFixme_inStatement() = testFixme(STATEMENT)

  fun testFixme_inExpression() = testFixme(EXPRESSION)

  private val TEMPLATE_STOPSHIP = "stopship"

  private fun testStopship(location: Location) = testInsertedComment(location, TEMPLATE_STOPSHIP)

  fun testStopship_onTopLevel() = testStopship(TOP_LEVEL)

  fun testStopship_inClass() = testStopship(CLASS)

  fun testStopship_inCompanion() = testStopship((OBJECT_DECLARATION))

  fun testStopship_inComment() = testNotInComment(TEMPLATE_STOPSHIP)

  fun testStopship_inStatement() = testStopship(STATEMENT)

  fun testStopship_inExpression() = testStopship(EXPRESSION)

  private val TEMPLATE_NOOP = "noop"

  private fun testNoop(location: Location) {
    // Given:
    addPreparedFileToProject(location)

    // When:
    insertTemplate(TEMPLATE_NOOP)

    // Then:
    myFixture.checkResult(insertIntoPsiFileAt(location, content = "/* no-op */"))
  }

  fun testNoop_onTopLevel() = testNoop(TOP_LEVEL)

  fun testNoop_inClass() = testNoop(CLASS)

  fun testNoop_inCompanion() = testNoop(OBJECT_DECLARATION)

  fun testNoop_inComment() = testNotInComment(TEMPLATE_NOOP)

  fun testNoop_inStatement() = testNoop(STATEMENT)

  fun testNoop_inExpression() = testNoop(EXPRESSION)
}