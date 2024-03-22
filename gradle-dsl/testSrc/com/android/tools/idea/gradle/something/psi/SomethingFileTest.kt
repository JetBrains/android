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
package com.android.tools.idea.gradle.something.psi

import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.LightPlatformTestCase

class SomethingFileTest : LightPlatformTestCase() {
  fun testGetEntries() {
    val file = SomethingPsiFactory(project).createFile("""
      foo {
      }
      
      bar = 3
      
      baz("abc")
      
      quux("def") {
      }
    """.trimIndent())
    val entries = file.getEntries()
    assertThat(entries).hasSize(4)
    assertThat(entries[0]).isInstanceOf(SomethingBlock::class.java)
    assertThat(entries[1]).isInstanceOf(SomethingAssignment::class.java)
    assertThat(entries[2]).isInstanceOf(SomethingFactory::class.java)
    assertThat(entries[3]).isInstanceOf(SomethingBlock::class.java)
  }
}