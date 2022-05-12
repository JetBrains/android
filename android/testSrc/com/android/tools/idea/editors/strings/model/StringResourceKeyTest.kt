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
package com.android.tools.idea.editors.strings.model

import com.google.common.truth.Truth.assertThat
import com.intellij.mock.MockVirtualFile
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class StringResourceKeyTest {
  @Test
  fun compareTo_nullDirectory() {
    val barKey = StringResourceKey("bar")
    val fooKey = StringResourceKey("foo")

    assertThat(barKey).isLessThan(fooKey)
    assertThat(fooKey).isGreaterThan(barKey)
  }

  @Test
  fun compareTo_sameNonNullDirectory() {
    val directory = MockVirtualFile(/* directory= */ true, "dirname")
    val barKey = StringResourceKey("bar", directory)
    val fooKey = StringResourceKey("foo", directory)

    assertThat(barKey).isLessThan(fooKey)
    assertThat(fooKey).isGreaterThan(barKey)
  }

  @Test
  fun compareTo_sameNameDifferentDirectory() {
    val fooDirectory = MockVirtualFile(/* directory= */ true, "foo")
    val barDirectory = MockVirtualFile(/* directory= */ true, "bar")
    val barDirKey = StringResourceKey("name", barDirectory)
    val fooDirKey = StringResourceKey("name", fooDirectory)

    assertThat(barDirKey).isLessThan(fooDirKey)
    assertThat(fooDirKey).isGreaterThan(barDirKey)
  }

  @Test
  fun compareTo_nullAndNonNullDirectory() {
    val barDirectory = MockVirtualFile(/* directory= */ true, "bar")
    val barDirKey = StringResourceKey("name", barDirectory)
    val emptyDirKey = StringResourceKey("name")

    assertThat(emptyDirKey).isLessThan(barDirKey)
    assertThat(barDirKey).isGreaterThan(emptyDirKey)
  }
}
