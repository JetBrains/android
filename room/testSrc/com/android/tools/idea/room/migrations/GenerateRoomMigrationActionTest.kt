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
package com.android.tools.idea.room.migrations

import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.testFramework.MapDataContext
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.TestActionEvent
import org.jetbrains.android.AndroidTestCase

class GenerateRoomMigrationActionTest : AndroidTestCase() {
  override fun setUp() {
    super.setUp()
  }

  fun testTheAction() {
    // TODO(b/234009550): Re-enable this test when fixed.
    return

    val jsonOne = myFixture.addFileToProject(
      "schemas/com.example.FooDb/1.json",
      // language=JSON
      """
        {
          "formatVersion": 1,
          "database": {
            "version": 2,
            "identityHash": "99c7946712f93a4d723efbe10a500eb0",
            "entities": [],
            "setupQueries": [
              "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)",
              "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, \"99c7946712f93a4d723efbe10a500eb0\")"
            ]
          }
        }
      """.trimIndent()
    ).virtualFile

    val jsonTwo = myFixture.addFileToProject(
      "schemas/com.example.FooDb/2.json",
      // language=JSON
      """
        {
          "formatVersion": 1,
          "database": {
            "version": 3,
            "identityHash": "30a079c09b902b7e6d50fb4eca6380b0",
            "entities": [],
            "setupQueries": [
              "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)",
              "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, \"30a079c09b902b7e6d50fb4eca6380b0\")"
            ]
          }
        }
      """.trimIndent()
    ).virtualFile

    myFixture.addClass("""
      package com.example;
      class FooDb {}
    """.trimIndent())

    val testSrc = myFixture.tempDirFixture.findOrCreateDir("testDir")
    PsiTestUtil.addSourceRoot(myFixture.module, testSrc, true)

    val context = MapDataContext()
    context.put(CommonDataKeys.VIRTUAL_FILE_ARRAY.name, arrayOf(jsonOne, jsonTwo))
    context.put(CommonDataKeys.PROJECT, myFixture.project)

    GenerateRoomMigrationAction().actionPerformed(TestActionEvent.createTestEvent(context))

    val migration = myFixture.findClass("Migration_2_3")
    assertThat(migration).isNotNull()

    val test = myFixture.findClass("Migration_2_3_Test")
    assertThat(test).isNotNull()
  }
}
