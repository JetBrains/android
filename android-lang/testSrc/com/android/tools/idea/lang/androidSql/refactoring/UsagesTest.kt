/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.lang.androidSql.refactoring

import com.android.test.testutils.TestUtils.resolveWorkspacePath
import com.android.tools.idea.lang.androidSql.createStubRoomClasses
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.moveCaret
import com.android.tools.idea.testing.onEdt
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/** Tests custom "Find Usages" for androidSql PSI references. */
@RunWith(JUnit4::class)
@RunsInEdt
class UsagesTest {

  @get:Rule
  val projectRule = AndroidProjectRule.onDisk().onEdt()

  private val myFixture by lazy { projectRule.fixture as JavaCodeInsightTestFixture }

  @Before
  fun setUp() {
    myFixture.testDataPath = resolveWorkspacePath("tools/adt/idea/android-lang/testData/lang/androidSql").toString()
    myFixture.copyDirectoryToProject("RoomSampleProject", "")

    createStubRoomClasses(myFixture)
  }

  @Test
  fun verifyUsages() {
    myFixture.openFileInEditor(myFixture.findClass("com.example.roomsampleproject.entity.Word").containingFile.virtualFile)
    myFixture.moveCaret("public class Wo|rd {")

    val usagesRepresentation = myFixture.getUsageViewTreeTextRepresentation(myFixture.elementAtCaret)
    assertThat(usagesRepresentation).contains(
      """
      |  Referenced in SQL query (2)
      |   app (2)
      |    main.java.com.example.roomsampleproject.dao (2)
      |     WordDao (2)
      |      deleteAll() (1)
      |       15DELETE from word_table
      |      getAlphabetizedWords() (1)
      |       18SELECT * FROM  word_table ORDER By word Asc
      """.trimMargin())
  }
}
