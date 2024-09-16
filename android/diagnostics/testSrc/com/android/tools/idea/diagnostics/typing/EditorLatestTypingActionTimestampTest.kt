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
package com.android.tools.idea.diagnostics.typing

import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.onEdt
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.intellij.util.ReflectionUtil
import org.junit.Assert
import org.junit.Rule
import org.junit.Test

class EditorLatestTypingActionTimestampTest {
  @get:Rule
  val projectRule = AndroidProjectRule.onDisk().onEdt()

  private val myFixture: JavaCodeInsightTestFixture by lazy { projectRule.fixture as JavaCodeInsightTestFixture }

  @Test
  fun testEditorHasLastTypedActionTimestampField() {
    val file = myFixture.addFileToProject(
      "/src/test/Test.java",
      //language=JAVA
      """
      package test;
      """)
    myFixture.configureFromExistingVirtualFile(file.virtualFile)
    myFixture.type("test")

    val field = ReflectionUtil.findField(EditorImpl::class.java, Long::class.java, "myLastTypedActionTimestamp")
    val timestamp = field.get(myFixture.editor) as Long
    Assert.assertNotNull(timestamp)
    Assert.assertNotNull(timestamp > 0)
  }
}