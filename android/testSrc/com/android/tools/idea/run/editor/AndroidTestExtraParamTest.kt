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
package com.android.tools.idea.run.editor

import com.android.tools.idea.gradle.project.model.AndroidModuleModel
import com.android.tools.idea.run.editor.AndroidTestExtraParam.Companion.parseFromString
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.TestProjectPaths
import com.google.common.truth.Truth.assertThat
import org.jetbrains.android.facet.AndroidFacet
import org.junit.Test

/**
 * Unit tests for [AndroidTestExtraParam]
 */
class AndroidTestExtraParamTest : AndroidGradleTestCase() {
  @Test
  fun testParseFromString() {
    assertThat(parseFromString("-e key1 value1 -e key2 value2").toList())
      .containsExactly(AndroidTestExtraParam("key1", "value1"),
                       AndroidTestExtraParam("key2", "value2"))

    assertThat(parseFromString("-e key1_no_value -e key2 value2 -e key3_no_value").toList())
      .containsExactly(AndroidTestExtraParam("key1_no_value", ""),
                       AndroidTestExtraParam("key2", "value2"),
                       AndroidTestExtraParam("key3_no_value", ""))

    assertThat(parseFromString("-e key1 value1 with space -e key2 value2 with space").toList())
      .containsExactly(AndroidTestExtraParam("key1", "value1 with space"),
                       AndroidTestExtraParam("key2", "value2 with space"))

    // Leading and trailing whitespace should be removed.
    assertThat(parseFromString("    -e    key1     value1     -e    key2    value2     ").toList())
      .containsExactly(AndroidTestExtraParam("key1", "value1"),
                       AndroidTestExtraParam("key2", "value2"))

    // Malformed input.
    assertThat(parseFromString("This is invalid input").toList()).isEmpty()
    assertThat(parseFromString("-e -e -e -e -e").toList()).isEmpty()
  }

  @Test
  fun testMerge() {
    // Merge two distinct lists.
    var seq1 = sequenceOf(AndroidTestExtraParam("key1", "value1"),
                          AndroidTestExtraParam("key2", "value2"))
    var seq2 = sequenceOf(AndroidTestExtraParam("key3", "value3"))
    assertThat(seq1.merge(seq2)).containsExactly(
      AndroidTestExtraParam("key1", "value1"),
      AndroidTestExtraParam("key2", "value2"),
      AndroidTestExtraParam("key3", "value3"))

    // There are two "key1" param. The last one ("value3") should be used.
    seq1 = sequenceOf(AndroidTestExtraParam("key1", "value1"),
                      AndroidTestExtraParam("key2", "value2"))
    seq2 = sequenceOf(AndroidTestExtraParam("key1", "value3"))
    assertThat(seq1.merge(seq2)).containsExactly(
      AndroidTestExtraParam("key1", "value3"),
      AndroidTestExtraParam("key2", "value2"))

    // There are two "key1" param. The one with no original source should be used.
    seq1 = sequenceOf(AndroidTestExtraParam("key1", "value1"),
                      AndroidTestExtraParam("key2", "value2"))
    seq2 = sequenceOf(AndroidTestExtraParam("key1", "value3", "value3", AndroidTestExtraParamSource.GRADLE))
    assertThat(seq1.merge(seq2)).containsExactly(
      AndroidTestExtraParam("key1", "value1", "value3", AndroidTestExtraParamSource.GRADLE),
      AndroidTestExtraParam("key2", "value2"))

    // There are two "key1" param. The one with no original source should be used.
    seq1 = sequenceOf(AndroidTestExtraParam("key1", "value1", "value1", AndroidTestExtraParamSource.GRADLE),
                      AndroidTestExtraParam("key2", "value2"))
    seq2 = sequenceOf(AndroidTestExtraParam("key1", "value3"))
    assertThat(seq1.merge(seq2)).containsExactly(
      AndroidTestExtraParam("key1", "value3", "value1", AndroidTestExtraParamSource.GRADLE),
      AndroidTestExtraParam("key2", "value2"))

    // There are two "key1" param with the same source. The last one ("value3") should be used.
    seq1 = sequenceOf(AndroidTestExtraParam("key1", "value1", "value1", AndroidTestExtraParamSource.GRADLE),
                      AndroidTestExtraParam("key2", "value2", "value2", AndroidTestExtraParamSource.GRADLE))
    seq2 = sequenceOf(AndroidTestExtraParam("key1", "value3", "value3", AndroidTestExtraParamSource.GRADLE))
    assertThat(seq1.merge(seq2)).containsExactly(
      AndroidTestExtraParam("key1", "value3", "value3", AndroidTestExtraParamSource.GRADLE),
      AndroidTestExtraParam("key2", "value2", "value2", AndroidTestExtraParamSource.GRADLE))
  }

  @Test
  fun testGetAndroidTestExtraParamsFromAndroidModuleModel() {
    loadProject(TestProjectPaths.RUN_CONFIG_RUNNER_ARGUMENTS)
    assertThat(AndroidModuleModel.get(myAndroidFacet).getAndroidTestExtraParams().toList()).containsExactly(
      AndroidTestExtraParam("size", "medium", "medium", AndroidTestExtraParamSource.GRADLE),
      AndroidTestExtraParam("foo", "bar", "bar", AndroidTestExtraParamSource.GRADLE))
  }

  @Test
  fun testGetAndroidTestExtraParamsFromAndroidModuleModelOfNullPointer() {
    assertThat((null as AndroidModuleModel?).getAndroidTestExtraParams().toList()).isEmpty()
  }

  @Test
  fun testGetAndroidTestExtraParamsFromAndroidFacet() {
    loadProject(TestProjectPaths.RUN_CONFIG_RUNNER_ARGUMENTS)
    assertThat(myAndroidFacet.getAndroidTestExtraParams().toList()).containsExactly(
      AndroidTestExtraParam("size", "medium", "medium", AndroidTestExtraParamSource.GRADLE),
      AndroidTestExtraParam("foo", "bar", "bar", AndroidTestExtraParamSource.GRADLE))
  }

  @Test
  fun testGetAndroidTestExtraParamsFromAndroidFacetOfNullPointer() {
    assertThat((null as AndroidFacet?).getAndroidTestExtraParams().toList()).isEmpty()
  }
}