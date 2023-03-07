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
package com.android.tools.idea.gradle.project.sync

import com.android.utils.appendCapitalized
import com.android.utils.combineAsCamelCase
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DefaultVariantsTest {

  @Test
  fun oneVariant() {
    val variants = listOf(
      variant("debug", "foo")
    )
    val default = variants.getDefaultVariant(
      userPreferredBuildTypes = setOf(),
      userPreferredProductFlavors = setOf(),
    )
    assertThat(default).isEqualTo("fooDebug")
  }

  @Test
  fun debugPreferred() {
    val variants = listOf(
      variant("release", "foo"),
      variant("debug", "foo")
    )
    val default = variants.getDefaultVariant(
      userPreferredBuildTypes = setOf(),
      userPreferredProductFlavors = setOf(),
    )
    assertThat(default).isEqualTo("fooDebug")
  }

  @Test
  fun preferredBuildType() {
    val variants = listOf(
      variant("debug", "foo"),
      variant("release", "foo")
    )
    val default = variants.getDefaultVariant(
      userPreferredBuildTypes = setOf("release"),
      userPreferredProductFlavors = setOf(),
    )
    assertThat(default).isEqualTo("fooRelease")
  }

  @Test
  fun preferredProductFlavorOverDebugBuildType() {
    val variants = listOf(
      variant("debug", "foo"),
      variant("release", "foo"),
      variant("release", "bar")
    )
    val default = variants.getDefaultVariant(
      userPreferredBuildTypes = setOf(),
      userPreferredProductFlavors = setOf("bar"),
    )
    assertThat(default).isEqualTo("barRelease")
  }

  @Test
  fun preferredFlavorsInTwoDimensions() {
    val variants = listOf(
      variant("debug", "foo", "abc"),
      variant("debug", "foo", "xyz"),
      variant("release", "foo", "abc"),
      variant("release", "foo", "xyz"),
      variant("debug", "bar", "abc"),
      variant("debug", "bar", "xyz"),
      variant("release", "bar", "abc"),
      variant("release", "bar", "xyz")
    )
    val default = variants.getDefaultVariant(
      userPreferredBuildTypes = setOf(),
      userPreferredProductFlavors = setOf("bar", "abc"),
    )
    assertThat(default).isEqualTo("barAbcDebug")
  }

  @Test
  fun preferredFlavorInSecondDimensionOnly() {
    val variants = listOf(
      variant("release", "foo", "abc"),
      variant("release", "foo", "xyz"),
      variant("debug", "foo", "abc"),
      variant("debug", "foo", "xyz"),
      variant("release", "bar", "abc"),
      variant("release", "bar", "xyz"),
      variant("debug", "bar", "abc"),
      variant("debug", "bar", "xyz")
    )
    val default = variants.getDefaultVariant(
      userPreferredBuildTypes = setOf(),
      userPreferredProductFlavors = setOf("abc"),
    )
    assertThat(default).isEqualTo("barAbcDebug")
  }

  @Test
  fun mismatchedProductFlavourLength() {
    val variants = listOf(
      variant("debug", "foo"),
      variant("release", "foo", "abc"),
    )
    val default = variants.getDefaultVariant(
      userPreferredBuildTypes = setOf(),
      userPreferredProductFlavors = setOf("abc")
    )

    assertThat(default).isEqualTo("fooDebug")
  }
}

private fun variant(buildType: String?, vararg productFlavors: String): VariantDef =
  VariantDef(combineAsCamelCase(productFlavors.toList()) { it }.appendCapitalized(buildType.orEmpty()), buildType, productFlavors.toList())
