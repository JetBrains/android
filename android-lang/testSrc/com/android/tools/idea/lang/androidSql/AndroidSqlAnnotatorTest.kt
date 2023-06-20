/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.lang.com.android.tools.idea.lang.androidSql

import com.android.tools.idea.lang.androidSql.AndroidSqlAnnotator
import com.android.tools.idea.lang.androidSql.AndroidSqlFileType
import com.android.tools.idea.lang.androidSql.createStubRoomClasses
import com.android.tools.idea.testing.moveCaret
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.testFramework.fixtures.CodeInsightTestUtil
import org.jetbrains.android.LightJavaCodeInsightFixtureAdtTestCase

class AndroidSqlAnnotatorTest : LightJavaCodeInsightFixtureAdtTestCase() {
  override fun setUp() {
    super.setUp()
    createStubRoomClasses(myFixture)
  }

  fun testAnnotatorOnKeyword() {
    myFixture.configureByText(AndroidSqlFileType.INSTANCE, "REPLACE INTO books VALUES(1,2,3)")

    var element = myFixture.moveCaret("RE|PLACE")
    var annotations = CodeInsightTestUtil.testAnnotator(AndroidSqlAnnotator(), element)
    assertThat(annotations).isEmpty()
 }

  fun testAnnotatorOnIdentifier() {
    myFixture.configureByText(AndroidSqlFileType.INSTANCE, "SELECT REPLACE('a','b','aa') FROM books")

    var element = myFixture.moveCaret("RE|PLACE")
    var annotations = CodeInsightTestUtil.testAnnotator(AndroidSqlAnnotator(), element)
    assertThat(annotations).hasSize(1)
    assertThat(annotations[0].textAttributes).isEqualTo(DefaultLanguageHighlighterColors.IDENTIFIER)
  }
}
