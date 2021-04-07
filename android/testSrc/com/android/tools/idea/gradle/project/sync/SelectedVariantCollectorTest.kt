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
package com.android.tools.idea.gradle.project.sync

import com.android.tools.idea.gradle.model.IdeVariantHeader
import com.google.common.truth.Expect
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class SelectedVariantCollectorTest {
  @get:Rule
  val expect: Expect = Expect.createAndEnableStackTrace()

  @Test
  fun testCreateVariantDetailsFrom() {
    assertThat(createVariantDetailsFrom(listOf("dim1", "dim2"), object : IdeVariantHeader {
      override val name = "fl1flADebug"
      override val buildType = "debug"
      override val productFlavors: List<String> = listOf("fl1", "flA")
      override val displayName = name
    })).isEqualTo(
      VariantDetails(
        name = "fl1flADebug",
        buildType = "debug",
        flavors = listOf(
          "dim1" to "fl1",
          "dim2" to "flA"
        )
      )
    )
  }

  @Test
  fun testCreateVariantDetailsFrom_missingDimensions() {
    assertThat(createVariantDetailsFrom(emptyList(), object : IdeVariantHeader {
      override val name = "fl1flADebug"
      override val buildType = "debug"
      override val productFlavors: List<String> = listOf("fl1", "flA")
      override val displayName = name
    })).isEqualTo(
      VariantDetails(
        name = "fl1flADebug",
        buildType = "debug",
        flavors = emptyList()
      )
    )
  }

  @Test
  fun testExtractApplyAndName() {
    fun expect(from: VariantDetails, base: VariantDetails, selectionChange: VariantSelectionChange?) {
      this.expect.that(buildVariantName(base.buildType, base.flavors.asSequence().map { it.second })).isEqualTo(base.name)
      this.expect.that(buildVariantName(from.buildType, from.flavors.asSequence().map { it.second })).isEqualTo(from.name)
      this.expect.that(VariantSelectionChange.extractVariantSelectionChange(from = from, base = base)).isEqualTo(selectionChange)
      if (selectionChange != null) {
        this.expect.that(base.applyChange(selectionChange)).isEqualTo(from)
      }
    }

    expect(
      from = VariantDetails("debug", buildType = "debug", flavors = emptyList()),
      base = VariantDetails("release", buildType = "release", flavors = emptyList()),
      selectionChange = VariantSelectionChange(buildType = "debug")
    )

    expect(
      from = VariantDetails("aDebug", buildType = "debug", flavors = listOf("dim1" to "a")),
      base = VariantDetails("aRelease", buildType = "release", flavors = listOf("dim1" to "a")),
      selectionChange = VariantSelectionChange(buildType = "debug")
    )

    expect(
      from = VariantDetails("aRelease", buildType = "release", flavors = listOf("dim1" to "a")),
      base = VariantDetails("aRelease", buildType = "release", flavors = listOf("dim1" to "a")),
      selectionChange = VariantSelectionChange()
    )

    expect(
      from = VariantDetails("bRelease", buildType = "release", flavors = listOf("dim1" to "b")),
      base = VariantDetails("aRelease", buildType = "release", flavors = listOf("dim1" to "a")),
      selectionChange = VariantSelectionChange(flavors = mapOf("dim1" to "b"))
    )

    expect(
      from = VariantDetails("bRelease", buildType = "release", flavors = listOf("dim1" to "b")),
      base = VariantDetails("aXRelease", buildType = "release", flavors = listOf("dim1" to "a", "dim2" to "x")),
      selectionChange = null
    )

    expect(
      from = VariantDetails("bXRelease", buildType = "release", flavors = listOf("dim1" to "b", "dim2" to "x")),
      base = VariantDetails("aXRelease", buildType = "release", flavors = listOf("dim1" to "a", "dim2" to "x")),
      selectionChange = VariantSelectionChange(flavors = mapOf("dim1" to "b"))
    )

    expect(
      from = VariantDetails("bXRelease", buildType = "release", flavors = listOf("dim1" to "b", "dim2" to "x")),
      base = VariantDetails("bYDebug", buildType = "debug", flavors = listOf("dim1" to "b", "dim2" to "y")),
      selectionChange = VariantSelectionChange(buildType = "release", flavors = mapOf("dim2" to "x"))
    )

    expect(
      from = VariantDetails("bXRelease", buildType = "release", flavors = listOf("dim1" to "b", "dim2" to "x")),
      base = VariantDetails("aRelease", buildType = "release", flavors = listOf("dim1" to "a")),
      selectionChange = null
    )
  }
}