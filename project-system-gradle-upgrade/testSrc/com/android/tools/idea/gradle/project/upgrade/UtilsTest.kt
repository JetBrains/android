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
package com.android.tools.idea.gradle.project.upgrade

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class UtilsTest {
  @Test
  fun testDescriptionTest0() {
    val list = listOf<String>()
    val text = computeDescriptionTextForTests(list)
    assertThat(text).isEqualTo(".")
  }

  @Test
  fun testDescriptionTest1() {
    val list = listOf("app")
    val text = computeDescriptionTextForTests(list)
    assertThat(text).isEqualTo("app.")
  }

  @Test
  fun testDescriptionTest2() {
    val list = listOf("app", "lib")
    val text = computeDescriptionTextForTests(list)
    assertThat(text).isEqualTo("app and lib.")
  }

  @Test
  fun testDescriptionTest3() {
    val list = listOf("app", "lib", "lib2")
    val text = computeDescriptionTextForTests(list)
    assertThat(text).isEqualTo("app, lib, and lib2.")
  }

  @Test
  fun testDescriptionTestLongModuleNames() {
    val list = listOf(
      "application-functionality", "primalibrary", "dualibrary", "trefoilibrary", "quadrilateralibrary",
      "pentagrammaticalibrary", "hexadecimalibrary")
    val text = computeDescriptionTextForTests(list)
    assertThat(text).isEqualTo("application-functionality, primalibrary, dualibrary, trefoilibrary, quadrilateralibrary, \n" +
                               "pentagrammaticalibrary, and hexadecimalibrary.")
  }

  @Test
  fun testDescriptionTest10() {
    val list = listOf("app", "lib1", "lib2", "lib3", "lib4", "lib5", "lib6", "lib7", "lib8", "lib9")
    val text = computeDescriptionTextForTests(list)
    assertThat(text).isEqualTo("app, lib1, lib2, lib3, lib4, lib5, lib6, lib7, and 2 other modules.")
  }
}