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
    assertThat(createVariantDetailsFrom(
      dimensions = listOf("dim1", "dim2"),
      variant = object : IdeVariantHeader {
        override val name = "fl1flADebug"
        override val buildType = "debug"
        override val productFlavors: List<String> = listOf("fl1", "flA")
        override val displayName = name
      },
      abi = null
    )).isEqualTo(
      VariantDetails(
        name = "fl1flADebug",
        buildType = "debug",
        flavors = listOf(
          "dim1" to "fl1",
          "dim2" to "flA"
        ),
        abi = null
      )
    )
  }

  @Test
  fun testCreateVariantDetailsFrom_abi() {
    assertThat(createVariantDetailsFrom(
      dimensions = listOf("dim1", "dim2"),
      variant = object : IdeVariantHeader {
        override val name = "fl1flADebug"
        override val buildType = "debug"
        override val productFlavors: List<String> = listOf("fl1", "flA")
        override val displayName = name
      },
      abi = "x86"
    )).isEqualTo(
      VariantDetails(
        name = "fl1flADebug",
        buildType = "debug",
        flavors = listOf(
          "dim1" to "fl1",
          "dim2" to "flA"
        ),
        abi = "x86"
      )
    )
  }

  @Test
  fun testCreateVariantDetailsFrom_missingDimensions() {
    assertThat(createVariantDetailsFrom(
      dimensions = emptyList(),
      variant = object : IdeVariantHeader {
        override val name = "fl1flADebug"
        override val buildType = "debug"
        override val productFlavors: List<String> = listOf("fl1", "flA")
        override val displayName = name
      },
      abi = null
    )).isEqualTo(
      VariantDetails(
        name = "fl1flADebug",
        buildType = "debug",
        flavors = emptyList(),
        abi = null
      )
    )
  }

  private enum class TestMode { BOTH, APPLY, EXTRACT }

  @Test
  fun testExtractApplyAndName() {
    fun expect(
      target: VariantDetails,
      base: VariantDetails,
      selectionChange: VariantSelectionChange?,
      testMode: TestMode = TestMode.BOTH,
      applyAbiMode: ApplyAbiSelectionMode = ApplyAbiSelectionMode.ALWAYS
    ) {
      this.expect.that(buildVariantName(base.buildType, base.flavors.asSequence().map { it.second })).isEqualTo(base.name)
      this.expect.that(buildVariantName(target.buildType, target.flavors.asSequence().map { it.second })).isEqualTo(target.name)
      if (testMode != TestMode.APPLY) {
        this.expect.that(VariantSelectionChange.extractVariantSelectionChange(from = target, base = base)).isEqualTo(selectionChange)
      }
      if (selectionChange != null && testMode != TestMode.EXTRACT) {
        this.expect.that(base.applyChange(selectionChange, applyAbiMode = applyAbiMode)).isEqualTo(target)
      }
    }

    expect(
      target = VariantDetails("debug", buildType = "debug", flavors = emptyList(), abi = null),
      base = VariantDetails("release", buildType = "release", flavors = emptyList(), abi = null),
      selectionChange = VariantSelectionChange(buildType = "debug")
    )

    expect(
      testMode = TestMode.EXTRACT, /* It is not invertible when the configuration structure changes. */
      target = VariantDetails("debug", buildType = "debug", flavors = emptyList(), abi = null),
      base = VariantDetails("release", buildType = "release", flavors = emptyList(), abi = "x86"),
      selectionChange = VariantSelectionChange(buildType = "debug", abi = null/* abi not available after sync */),
    )

    expect(
      target = VariantDetails("debug", buildType = "debug", flavors = emptyList(), abi = "x86"),
      base = VariantDetails("release", buildType = "release", flavors = emptyList(), abi = null),
      selectionChange = VariantSelectionChange(buildType = "debug", abi = "x86")
    )

    expect(
      testMode = TestMode.APPLY,
      applyAbiMode = ApplyAbiSelectionMode.OVERRIDE_ONLY,
      target = VariantDetails("debug", buildType = "debug", flavors = emptyList(), abi = null),
      base = VariantDetails("release", buildType = "release", flavors = emptyList(), abi = null),
      selectionChange = VariantSelectionChange(buildType = "debug", abi = "x86")
    )

    expect(
      target = VariantDetails("aDebug", buildType = "debug", flavors = listOf("dim1" to "a"), abi = "x86"),
      base = VariantDetails("aRelease", buildType = "release", flavors = listOf("dim1" to "a"), abi = "x86_64"),
      selectionChange = VariantSelectionChange(buildType = "debug", abi = "x86")
    )

    expect(
      target = VariantDetails("aRelease", buildType = "release", flavors = listOf("dim1" to "a"), abi = null),
      base = VariantDetails("aRelease", buildType = "release", flavors = listOf("dim1" to "a"), abi = null),
      selectionChange = VariantSelectionChange()
    )

    expect(
      target = VariantDetails("bRelease", buildType = "release", flavors = listOf("dim1" to "b"), abi = null),
      base = VariantDetails("aRelease", buildType = "release", flavors = listOf("dim1" to "a"), abi = null),
      selectionChange = VariantSelectionChange(flavors = mapOf("dim1" to "b"))
    )

    expect(
      target = VariantDetails("bRelease", buildType = "release", flavors = listOf("dim1" to "b"), abi = null),
      base = VariantDetails("aXRelease", buildType = "release", flavors = listOf("dim1" to "a", "dim2" to "x"), abi = null),
      selectionChange = null
    )

    expect(
      target = VariantDetails("bXRelease", buildType = "release", flavors = listOf("dim1" to "b", "dim2" to "x"), abi = null),
      base = VariantDetails("aXRelease", buildType = "release", flavors = listOf("dim1" to "a", "dim2" to "x"), abi = null),
      selectionChange = VariantSelectionChange(flavors = mapOf("dim1" to "b"))
    )

    expect(
      target = VariantDetails("bXRelease", buildType = "release", flavors = listOf("dim1" to "b", "dim2" to "x"), abi = null),
      base = VariantDetails("bYDebug", buildType = "debug", flavors = listOf("dim1" to "b", "dim2" to "y"), abi = null),
      selectionChange = VariantSelectionChange(buildType = "release", flavors = mapOf("dim2" to "x"))
    )

    expect(
      target = VariantDetails("bXRelease", buildType = "release", flavors = listOf("dim1" to "b", "dim2" to "x"), abi = null),
      base = VariantDetails("aRelease", buildType = "release", flavors = listOf("dim1" to "a"), abi = null),
      selectionChange = null
    )
  }
}