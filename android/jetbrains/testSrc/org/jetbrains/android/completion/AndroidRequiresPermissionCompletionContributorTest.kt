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
package org.jetbrains.android.completion

import com.android.test.testutils.TestUtils
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.intellij.util.application
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisFromWriteAction
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

private const val TEST_DATA_DIRECTORY = "requiresPermission"

@RunWith(JUnit4::class)
class AndroidRequiresPermissionCompletionContributorTest {

  @get:Rule val androidProjectRule = AndroidProjectRule.withSdk()

  private val fixture by lazy { androidProjectRule.fixture }

  @Before
  fun setUp() {
    fixture.testDataPath =
      TestUtils.resolveWorkspacePath("tools/adt/idea/android/testData").toString()
    fixture.copyFileToProject(
      "$TEST_DATA_DIRECTORY/RequiresPermission.java",
      "src/androidx/annotation/RequiresPermission.java",
    )
  }

  @Test
  @OptIn(KaAllowAnalysisFromWriteAction::class)
  fun basicCompletionKotlin() {
    // b/364384369: The machinery for completion runs on the EDT in unit-test mode.
    // This causes the test to fail under K2 due to running analysis inside a write
    // action, unless we exempt it here.
    // (In production, the completion contributor runs on a background thread.)
    allowAnalysisFromWriteAction {
      val addedFile =
        fixture.addFileToProject(
          "src/com/example/Foo.kt",
          // language=kotlin
          """
          package com.example

          import androidx.annotation.RequiresPermission

          class Foo {
            @RequiresPermission(<caret>)
            fun doSomething() {}
          }
          """
            .trimIndent(),
        )

      fixture.configureFromExistingVirtualFile(addedFile.virtualFile)

      // Validate that completion contains various permissions entries.
      fixture.completeBasic()
      assertThat(fixture.lookupElementStrings)
        .containsAllOf("ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION", "READ_EXTERNAL_STORAGE")

      // Now filter the list down to a single entry, and verify its insertion.
      application.invokeAndWait { fixture.type("ACCESS_FINE") }
      val lookupElements = fixture.completeBasic()
      assertWithMessage(
        "Expect lookupElements to be null, signifying there is a single lookup entry."
      )
        .that(lookupElements)
        .isNull()

      fixture.checkResult(
        // language=kotlin
        """
        package com.example

        import android.Manifest
        import androidx.annotation.RequiresPermission

        class Foo {
          @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
          fun doSomething() {}
        }
        """
          .trimIndent()
      )
    }
  }

  @Test
  fun basicCompletionJava() {
    val addedFile =
      fixture.addFileToProject(
        "src/com/example/Foo.java",
        // language=JAVA
        """
        package com.example;

        import androidx.annotation.RequiresPermission;

        class Foo {
          @RequiresPermission(<caret>)
          public static void doSomething() {}
        }
        """
          .trimIndent(),
      )

    fixture.configureFromExistingVirtualFile(addedFile.virtualFile)

    // Validate that completion contains various permissions entries.
    fixture.completeBasic()
    assertThat(fixture.lookupElementStrings)
      .containsAllOf("ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION", "READ_EXTERNAL_STORAGE")

    // Now filter the list down to a single entry, and verify its insertion.
    application.invokeAndWait { fixture.type("ACCESS_FINE") }
    val lookupElements = fixture.completeBasic()
    assertWithMessage(
        "Expect lookupElements to be null, signifying there is a single lookup entry."
      )
      .that(lookupElements)
      .isNull()

    fixture.checkResult(
      // language=JAVA
      """
      package com.example;

      import android.Manifest;

      import androidx.annotation.RequiresPermission;

      class Foo {
        @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        public static void doSomething() {}
      }
      """
        .trimIndent()
    )
  }
}
