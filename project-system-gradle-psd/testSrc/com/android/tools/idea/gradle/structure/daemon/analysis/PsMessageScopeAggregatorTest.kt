/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.daemon.analysis

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test

class PsMessageScopeAggregatorTest {
  @Test
  fun simple() {
    val aggregator = PsMessageScopeAggregator(setOf("debug", "release"), listOf())
    assertThat(aggregator.aggregate(setOf(PsMessageScope("debug"))), equalTo(setOf(PsMessageAggregatedScope("debug"))))
    assertThat(aggregator.aggregate(setOf(PsMessageScope("release"))), equalTo(setOf(PsMessageAggregatedScope("release"))))
    assertThat(aggregator.aggregate(setOf(PsMessageScope("debug", artifact = "Test"))),
               equalTo(setOf(PsMessageAggregatedScope("debug", scope = "Test"))))
    assertThat(aggregator.aggregate(setOf(PsMessageScope("release", artifact = "AndroidTest"))),
               equalTo(setOf(PsMessageAggregatedScope("release", scope = "AndroidTest"))))

    assertThat(aggregator.aggregate(setOf(PsMessageScope("debug"), PsMessageScope("release"))),
               equalTo(setOf(PsMessageAggregatedScope(buildType = null))))

    assertThat(aggregator.aggregate(setOf(PsMessageScope("debug"), PsMessageScope("release", artifact = "Test"))),
               equalTo(setOf(PsMessageAggregatedScope("debug"), PsMessageAggregatedScope("release", scope = "Test"))))
  }

  @Test
  fun simpleNoBuildTypes_errorneusConfig() {
    val aggregator = PsMessageScopeAggregator(setOf(), listOf())
    assertThat(aggregator.aggregate(setOf(PsMessageScope("debug"))), equalTo(setOf(PsMessageAggregatedScope("debug"))))
  }

  @Test
  fun withOneDimensionAllFlavors() {
    val aggregator = PsMessageScopeAggregator(setOf("debug", "release"), listOf(setOf("A", "B", "C")))
    assertThat(aggregator.aggregate(setOf(
      PsMessageScope("debug", listOf("A")),
      PsMessageScope("debug", listOf("B")),
      PsMessageScope("debug", listOf("C"))
    )), equalTo(setOf(PsMessageAggregatedScope("debug", listOf(null)))))

    assertThat(aggregator.aggregate(setOf(
      PsMessageScope("release", listOf("A")),
      PsMessageScope("release", listOf("B")),
      PsMessageScope("release", listOf("C"))
    )), equalTo(setOf(PsMessageAggregatedScope("release", listOf(null)))))

    assertThat(aggregator.aggregate(setOf(
      PsMessageScope("debug", listOf("A"), artifact = "Test"),
      PsMessageScope("debug", listOf("B"), artifact = "Test"),
      PsMessageScope("debug", listOf("C"), artifact = "Test")
    )), equalTo(setOf(PsMessageAggregatedScope("debug", listOf(null), scope = "Test"))))

    assertThat(aggregator.aggregate(setOf(
      PsMessageScope("debug", listOf("A")),
      PsMessageScope("debug", listOf("B")),
      PsMessageScope("debug", listOf("C")),
      PsMessageScope("release", listOf("A")),
      PsMessageScope("release", listOf("B")),
      PsMessageScope("release", listOf("C"))
    )), equalTo(setOf(PsMessageAggregatedScope(buildType = null, productFlavors = listOf(null)))))

    assertThat(
      aggregator.aggregate(setOf(
        PsMessageScope("debug", listOf("A")),
        PsMessageScope("debug", listOf("B")),
        PsMessageScope("debug", listOf("C")),
        PsMessageScope("release", listOf("A"), artifact = "Test"),
        PsMessageScope("release", listOf("B"), artifact = "Test"),
        PsMessageScope("release", listOf("C"), artifact = "Test")
      )),
      equalTo(setOf(
        PsMessageAggregatedScope("debug", listOf(null)),
        PsMessageAggregatedScope("release", listOf(null), scope = "Test"))))
  }

  @Test
  fun withOneDimensionOneFlavor() {
    val aggregator = PsMessageScopeAggregator(setOf("debug", "release"), listOf(setOf("A", "B", "C")))
    assertThat(aggregator.aggregate(setOf(
      PsMessageScope("debug", listOf("C"))
    )), equalTo(setOf(PsMessageAggregatedScope("debug", listOf("C")))))

    assertThat(aggregator.aggregate(setOf(
      PsMessageScope("release", listOf("C"))
    )), equalTo(setOf(PsMessageAggregatedScope("release", listOf("C")))))

    assertThat(aggregator.aggregate(setOf(
      PsMessageScope("debug", listOf("C"), artifact = "Test")
    )), equalTo(setOf(PsMessageAggregatedScope("debug", listOf("C"), scope = "Test"))))

    assertThat(aggregator.aggregate(setOf(
      PsMessageScope("debug", listOf("C")),
      PsMessageScope("release", listOf("C"))
    )), equalTo(setOf(PsMessageAggregatedScope(buildType = null, productFlavors = listOf("C")))))

    assertThat(
      aggregator.aggregate(setOf(
        PsMessageScope("debug", listOf("C")),
        PsMessageScope("release", listOf("C"), artifact = "Test")
      )),
      equalTo(setOf(
        PsMessageAggregatedScope("debug", listOf("C")),
        PsMessageAggregatedScope("release", listOf("C"), scope = "Test"))))
  }

  @Test
  fun withOneDimensionNoFlavors_errorneousConfig() {
    val aggregator = PsMessageScopeAggregator(setOf("debug", "release"), listOf(setOf()))
    assertThat(aggregator.aggregate(setOf(
      PsMessageScope("debug", listOf("A"))
    )), equalTo(setOf(PsMessageAggregatedScope("debug", listOf("A")))))
  }

  @Test
  fun withTwoDimensionsAllFlavors() {
    val aggregator = PsMessageScopeAggregator(setOf("debug", "release"), listOf(setOf("A", "B"), setOf("X", "Y")))
    assertThat(aggregator.aggregate(setOf(
      PsMessageScope("debug", listOf("A", "X")),
      PsMessageScope("debug", listOf("A", "Y")),
      PsMessageScope("debug", listOf("B", "X")),
      PsMessageScope("debug", listOf("B", "Y"))
    )), equalTo(setOf(PsMessageAggregatedScope("debug", listOf(null, null)))))

    assertThat(aggregator.aggregate(setOf(
      PsMessageScope("release", listOf("A", "X")),
      PsMessageScope("release", listOf("A", "Y")),
      PsMessageScope("release", listOf("B", "X")),
      PsMessageScope("release", listOf("B", "Y"))
    )), equalTo(setOf(PsMessageAggregatedScope("release", listOf(null, null)))))

    assertThat(aggregator.aggregate(setOf(
      PsMessageScope("debug", listOf("A", "X"), artifact = "Test"),
      PsMessageScope("debug", listOf("A", "Y"), artifact = "Test"),
      PsMessageScope("debug", listOf("B", "X"), artifact = "Test"),
      PsMessageScope("debug", listOf("B", "Y"), artifact = "Test")
    )), equalTo(setOf(PsMessageAggregatedScope("debug", listOf(null, null), scope = "Test"))))

    assertThat(aggregator.aggregate(setOf(
      PsMessageScope("debug", listOf("A", "X")),
      PsMessageScope("debug", listOf("A", "Y")),
      PsMessageScope("debug", listOf("B", "X")),
      PsMessageScope("debug", listOf("B", "Y")),
      PsMessageScope("release", listOf("A", "X")),
      PsMessageScope("release", listOf("A", "Y")),
      PsMessageScope("release", listOf("B", "X")),
      PsMessageScope("release", listOf("B", "Y"))
    )), equalTo(setOf(PsMessageAggregatedScope(buildType = null, productFlavors = listOf(null, null)))))

    assertThat(
      aggregator.aggregate(setOf(
        PsMessageScope("debug", listOf("A", "X")),
        PsMessageScope("debug", listOf("A", "Y")),
        PsMessageScope("debug", listOf("B", "X")),
        PsMessageScope("debug", listOf("B", "Y")),
        PsMessageScope("release", listOf("A", "X"), artifact = "Test"),
        PsMessageScope("release", listOf("A", "Y"), artifact = "Test"),
        PsMessageScope("release", listOf("B", "X"), artifact = "Test"),
        PsMessageScope("release", listOf("B", "Y"), artifact = "Test")
      )),
      equalTo(setOf(
        PsMessageAggregatedScope("debug", listOf(null, null)),
        PsMessageAggregatedScope("release", listOf(null, null), scope = "Test"))))
  }

  @Test
  fun withTwoDimensionsOneFlavor() {
    val aggregator = PsMessageScopeAggregator(setOf("debug", "release"), listOf(setOf("A", "B"), setOf("X", "Y")))
    assertThat(aggregator.aggregate(setOf(
      PsMessageScope("debug", listOf("A", "X")),
      PsMessageScope("debug", listOf("A", "Y"))
    )), equalTo(setOf(PsMessageAggregatedScope("debug", listOf("A", null)))))

    assertThat(aggregator.aggregate(setOf(
      PsMessageScope("release", listOf("A", "X")),
      PsMessageScope("release", listOf("B", "X"))
    )), equalTo(setOf(PsMessageAggregatedScope("release", listOf(null, "X")))))

    assertThat(aggregator.aggregate(setOf(
      PsMessageScope("debug", listOf("A", "X"), artifact = "Test"),
      PsMessageScope("debug", listOf("B", "Y"), artifact = "Test")
    )), equalTo(setOf(
      PsMessageAggregatedScope("debug", listOf("A", "X"), scope = "Test"),
      PsMessageAggregatedScope("debug", listOf("B", "Y"), scope = "Test")
    )))

    assertThat(aggregator.aggregate(setOf(
      PsMessageScope("debug", listOf("A", "X")),
      PsMessageScope("debug", listOf("A", "Y")),
      PsMessageScope("debug", listOf("B", "X")),
      PsMessageScope("release", listOf("A", "X")),
      PsMessageScope("release", listOf("A", "Y")),
      PsMessageScope("release", listOf("B", "Y"))
    )), equalTo(setOf(
      PsMessageAggregatedScope(buildType = null, productFlavors = listOf("A", null)),
      PsMessageAggregatedScope(buildType = "debug", productFlavors = listOf("B", "X")),
      PsMessageAggregatedScope(buildType = "release", productFlavors = listOf("B", "Y"))
    )))

    assertThat(
      aggregator.aggregate(setOf(
        PsMessageScope("debug", listOf("A", "X")),
        PsMessageScope("debug", listOf("A", "Y")),
        PsMessageScope("release", listOf("A", "X"), artifact = "Test"),
        PsMessageScope("release", listOf("B", "X"), artifact = "Test"),
        PsMessageScope("release", listOf("B", "Y"), artifact = "Test")
      )),
      equalTo(setOf(
        PsMessageAggregatedScope("debug", listOf("A", null)),
        PsMessageAggregatedScope("release", listOf(null, "X"), scope = "Test"),
        PsMessageAggregatedScope("release", listOf("B", "Y"), scope = "Test")
      )))
  }

  @Test
  fun withThreeDimensionsAllFlavors() {
    val aggregator = PsMessageScopeAggregator(setOf("debug", "release"), listOf(setOf("A", "B"), setOf("K", "L"), setOf("X", "Y")))
    assertThat(aggregator.aggregate(setOf(
      PsMessageScope("debug", listOf("A", "K", "X")),
      PsMessageScope("debug", listOf("A", "K", "Y")),
      PsMessageScope("debug", listOf("A", "L", "X")),
      PsMessageScope("debug", listOf("A", "L", "Y")),
      PsMessageScope("debug", listOf("B", "K", "X")),
      PsMessageScope("debug", listOf("B", "K", "Y")),
      PsMessageScope("debug", listOf("B", "L", "X")),
      PsMessageScope("debug", listOf("B", "L", "Y"))
    )), equalTo(setOf(PsMessageAggregatedScope("debug", listOf(null, null, null)))))

    assertThat(aggregator.aggregate(setOf(
      PsMessageScope("release", listOf("A", "K", "X")),
      PsMessageScope("release", listOf("A", "K", "Y")),
      PsMessageScope("release", listOf("A", "L", "X")),
      PsMessageScope("release", listOf("A", "L", "Y")),
      PsMessageScope("release", listOf("B", "K", "X")),
      PsMessageScope("release", listOf("B", "K", "Y")),
      PsMessageScope("release", listOf("B", "L", "X")),
      PsMessageScope("release", listOf("B", "L", "Y"))
    )), equalTo(setOf(PsMessageAggregatedScope("release", listOf(null, null, null)))))

    assertThat(aggregator.aggregate(setOf(
      PsMessageScope("debug", listOf("A", "K", "X"), artifact = "Test"),
      PsMessageScope("debug", listOf("A", "K", "Y"), artifact = "Test"),
      PsMessageScope("debug", listOf("A", "L", "X"), artifact = "Test"),
      PsMessageScope("debug", listOf("A", "L", "Y"), artifact = "Test"),
      PsMessageScope("debug", listOf("B", "K", "X"), artifact = "Test"),
      PsMessageScope("debug", listOf("B", "K", "Y"), artifact = "Test"),
      PsMessageScope("debug", listOf("B", "L", "X"), artifact = "Test"),
      PsMessageScope("debug", listOf("B", "L", "Y"), artifact = "Test")
    )), equalTo(setOf(PsMessageAggregatedScope("debug", listOf(null, null, null), scope = "Test"))))

    assertThat(aggregator.aggregate(setOf(
      PsMessageScope("debug", listOf("A", "K", "X")),
      PsMessageScope("debug", listOf("A", "K", "Y")),
      PsMessageScope("debug", listOf("A", "L", "X")),
      PsMessageScope("debug", listOf("A", "L", "Y")),
      PsMessageScope("debug", listOf("B", "K", "X")),
      PsMessageScope("debug", listOf("B", "K", "Y")),
      PsMessageScope("debug", listOf("B", "L", "X")),
      PsMessageScope("debug", listOf("B", "L", "Y")),
      PsMessageScope("release", listOf("A", "K", "X")),
      PsMessageScope("release", listOf("A", "K", "Y")),
      PsMessageScope("release", listOf("A", "L", "X")),
      PsMessageScope("release", listOf("A", "L", "Y")),
      PsMessageScope("release", listOf("B", "K", "X")),
      PsMessageScope("release", listOf("B", "K", "Y")),
      PsMessageScope("release", listOf("B", "L", "X")),
      PsMessageScope("release", listOf("B", "L", "Y"))
    )), equalTo(setOf(PsMessageAggregatedScope(buildType = null, productFlavors = listOf(null, null, null)))))

    assertThat(
      aggregator.aggregate(setOf(
        PsMessageScope("debug", listOf("A", "K", "X")),
        PsMessageScope("debug", listOf("A", "K", "Y")),
        PsMessageScope("debug", listOf("A", "L", "X")),
        PsMessageScope("debug", listOf("A", "L", "Y")),
        PsMessageScope("debug", listOf("B", "K", "X")),
        PsMessageScope("debug", listOf("B", "K", "Y")),
        PsMessageScope("debug", listOf("B", "L", "X")),
        PsMessageScope("debug", listOf("B", "L", "Y")),
        PsMessageScope("release", listOf("A", "K", "X"), artifact = "Test"),
        PsMessageScope("release", listOf("A", "K", "Y"), artifact = "Test"),
        PsMessageScope("release", listOf("A", "L", "X"), artifact = "Test"),
        PsMessageScope("release", listOf("A", "L", "Y"), artifact = "Test"),
        PsMessageScope("release", listOf("B", "K", "X"), artifact = "Test"),
        PsMessageScope("release", listOf("B", "K", "Y"), artifact = "Test"),
        PsMessageScope("release", listOf("B", "L", "X"), artifact = "Test"),
        PsMessageScope("release", listOf("B", "L", "Y"), artifact = "Test")
      )),
      equalTo(setOf(
        PsMessageAggregatedScope("debug", listOf(null, null, null)),
        PsMessageAggregatedScope("release", listOf(null, null, null), scope = "Test"))))
  }

  @Test
  fun withThreeDimensionsFlavorCombinations() {
    val aggregator = PsMessageScopeAggregator(setOf("debug", "release"), listOf(setOf("A", "B"), setOf("K", "L"), setOf("X", "Y")))
    assertThat(aggregator.aggregate(setOf(
      PsMessageScope("debug", listOf("B", "K", "X")),
      PsMessageScope("debug", listOf("B", "K", "Y")),
      PsMessageScope("debug", listOf("B", "L", "X")),
      PsMessageScope("debug", listOf("B", "L", "Y"))
    )), equalTo(setOf(PsMessageAggregatedScope("debug", listOf("B", null, null)))))

    assertThat(aggregator.aggregate(setOf(
      PsMessageScope("release", listOf("A", "L", "X")),
      PsMessageScope("release", listOf("A", "L", "Y")),
      PsMessageScope("release", listOf("B", "L", "X")),
      PsMessageScope("release", listOf("B", "L", "Y"))
    )), equalTo(setOf(PsMessageAggregatedScope("release", listOf(null, "L", null)))))

    // Combinations of two flavors are not allowed.
    assertThat(aggregator.aggregate(setOf(
      PsMessageScope("debug", listOf("A", "K", "X"), artifact = "Test"),
      PsMessageScope("debug", listOf("A", "K", "Y"), artifact = "Test")
    )), equalTo(setOf(
      PsMessageAggregatedScope("debug", listOf("A", "K", "X"), scope = "Test"),
      PsMessageAggregatedScope("debug", listOf("A", "K", "Y"), scope = "Test")
    )))

    assertThat(aggregator.aggregate(setOf(
      PsMessageScope("debug", listOf("A", "L", "X")),
      PsMessageScope("debug", listOf("A", "L", "Y")),
      PsMessageScope("debug", listOf("B", "L", "X")),
      PsMessageScope("debug", listOf("B", "L", "Y")),
      PsMessageScope("release", listOf("A", "L", "X")),
      PsMessageScope("release", listOf("A", "L", "Y")),
      PsMessageScope("release", listOf("B", "L", "X")),
      PsMessageScope("release", listOf("B", "L", "Y"))
    )), equalTo(setOf(PsMessageAggregatedScope(buildType = null, productFlavors = listOf(null, "L", null)))))

    assertThat(
      aggregator.aggregate(setOf(
        PsMessageScope("debug", listOf("A", "K", "X")),
        PsMessageScope("debug", listOf("A", "K", "Y")),
        PsMessageScope("debug", listOf("A", "L", "X")),
        PsMessageScope("debug", listOf("A", "L", "Y")),
        PsMessageScope("debug", listOf("B", "K", "X")),
        PsMessageScope("debug", listOf("B", "K", "Y")),
        PsMessageScope("debug", listOf("B", "L", "X")),
        PsMessageScope("debug", listOf("B", "L", "Y")),
        PsMessageScope("release", listOf("A", "K", "X"), artifact = "Test"),
        PsMessageScope("release", listOf("A", "K", "Y"), artifact = "Test"),
        PsMessageScope("release", listOf("A", "L", "X"), artifact = "Test"),
        PsMessageScope("release", listOf("A", "L", "Y"), artifact = "Test"),
        PsMessageScope("release", listOf("B", "K", "X"), artifact = "Test"),
        PsMessageScope("release", listOf("B", "K", "Y"), artifact = "Test"),
        PsMessageScope("release", listOf("B", "L", "X"), artifact = "Test"),
        PsMessageScope("release", listOf("B", "L", "Y"), artifact = "Test")
      )),
      equalTo(setOf(
        PsMessageAggregatedScope("debug", listOf(null, null, null)),
        PsMessageAggregatedScope("release", listOf(null, null, null), scope = "Test"))))
  }
}