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
package org.jetbrains.android.compose

import com.android.tools.idea.flags.StudioFlags
import org.jetbrains.android.AndroidTestCase
import org.jetbrains.kotlin.idea.inspections.FunctionNameInspection

class AndroidComposeSuppressorTest : AndroidTestCase() {

  override fun setUp() {
    super.setUp()
    StudioFlags.COMPOSE_EDITOR_SUPPORT.override(true)
  }

  override fun tearDown() {
    StudioFlags.COMPOSE_EDITOR_SUPPORT.clearOverride()
    super.tearDown()
  }

  fun testFunctionNameWarning(): Unit = myFixture.run {
    enableInspections(FunctionNameInspection::class.java)
    stubComposableAnnotation(ANDROIDX_COMPOSE_PACKAGE)

    val file = addFileToProject(
      "src/com/example/views.kt",
      """
      package com.example

      import androidx.compose.Composable

      @Composable
      fun MyView() {}

      fun <weak_warning descr="Function name 'NormalFunction' should start with a lowercase letter">NormalFunction</weak_warning>() {}
      """.trimIndent()
    )

    configureFromExistingVirtualFile(file.virtualFile)
    checkHighlighting()
  }
}
