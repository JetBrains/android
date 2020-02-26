/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.tree

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GotoDefinitionTest {

  @Test
  fun testComposeMethodParser() {
    assertThat(ComposeMethod.parse("androidx.ui.layout.ColumnKt.Column"))
      .isEqualTo(ComposeMethod("Column", "androidx.ui.layout", "Column", 0))

    assertThat(ComposeMethod.parse("androidx.ui.material.ButtonKt\$Button\$1.invoke"))
      .isEqualTo(ComposeMethod("Button", "androidx.ui.material", "Button", 1))

    assertThat(ComposeMethod.parse("com.example.mycompose.MainActivity\$onCreate\$1\$1.invoke"))
      .isEqualTo(ComposeMethod("MainActivity", "com.example.mycompose", "onCreate", 2))

    assertThat(ComposeMethod.parse("com.example.mycompose.MainActivity\$onCreate\$1\$1\$1\$1\$6.invoke"))
      .isEqualTo(ComposeMethod("MainActivity", "com.example.mycompose", "onCreate", 5))
  }
}
