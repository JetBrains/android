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
package com.android.tools.idea.room

import com.google.common.truth.Truth.assertThat
import org.jetbrains.android.AndroidTestCase

class GenerateRoomMigrationActionTest : AndroidTestCase() {
  override fun setUp() {
    super.setUp()
  }

  fun testTheAction() {
    myFixture.addFileToProject(
      "src/schema/foo.json",
      // language=JSON
      """
        {
        "some": "json"
        }
      """.trimIndent()
    )

    try {
      myFixture.testAction(GenerateRoomMigrationAction())
      fail()
    }
    catch (e: RuntimeException) {
      // Exception thrown by TestDialog.DEFAULT, making sure the action actually ran.
      assertThat(e::class.java).isEqualTo(java.lang.RuntimeException::class.java)
      assertThat(e.message).isEqualTo("Generating migration")
    }
  }
}