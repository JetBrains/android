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
package com.android.tools.idea.editing.metrics

import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.moveCaret
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.replaceService
import com.intellij.util.application
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.jetbrains.kotlin.idea.KotlinFileType
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class CodeEditedBackspaceHandlerDelegateTest {
  @get:Rule val projectRule = AndroidProjectRule.inMemory()
  private val fixture by lazy { projectRule.fixture }
  private val codeEditedListener = TestCodeEditedListener()

  private val testDispatcher = StandardTestDispatcher()
  private val testScope = TestScope(testDispatcher)

  @Before
  fun setUp() {
    CodeEditedListener.EP_NAME.point.registerExtension(
      codeEditedListener,
      projectRule.testRootDisposable,
    )

    application.replaceService(
      CodeEditedMetricsService::class.java,
      CodeEditedMetricsServiceImpl(testScope, testDispatcher),
      projectRule.testRootDisposable,
    )
  }

  @Test
  fun typing() {
    fixture.configureByText(
      KotlinFileType.INSTANCE,
      // language=kotlin
      """
      package com.example

      val fo

      """
        .trimIndent(),
    )
    application.invokeAndWait { fixture.moveCaret("val fo|") }
    fixture.type('\b')

    testScope.advanceUntilIdle()
    assertThat(codeEditedListener.events).containsExactly(CodeEdited(0, 1, Source.TYPING))
  }
}
