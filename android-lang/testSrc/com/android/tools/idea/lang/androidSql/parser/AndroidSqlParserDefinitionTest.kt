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
package com.android.tools.idea.lang.androidSql.parser

import com.android.tools.idea.lang.androidSql.parser.AndroidSqlParserDefinition.Companion.isValidSqlQuery
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase

class AndroidSqlParserDefinitionTest : JavaCodeInsightFixtureTestCase() {
  fun testValidFile() {
    val validStatement = "update foo set bar=bar*2 where predicate(bar)"

    assertThat(isValidSqlQuery(project, validStatement)).isTrue()
  }

  fun testInvalidFile() {
    val invalidStatement = "CREATE TABLE IF NOT EXISTS wordcount(word TEXT PRIMARY KEY, cnt INTEGER) WITHOUT MADEUP"

    assertThat(isValidSqlQuery(project, invalidStatement)).isFalse()
  }
}