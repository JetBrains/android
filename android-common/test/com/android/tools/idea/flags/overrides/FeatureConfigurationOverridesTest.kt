/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.flags.overrides

import com.android.tools.idea.flags.FeatureConfiguration
import com.android.utils.associateNotNull
import com.google.common.truth.Truth
import org.junit.Test

class FeatureConfigurationOverridesTest {

  @Test
  fun testEmpty() {
    val content = """
    #some comments
    """.trimIndent()

    Truth.assertThat(FeatureConfigurationProvider.loadValues(content.byteInputStream()).toMap()).isEmpty()
  }

  @Test
  fun testInternal() {
    val content = """
    #some comments
    group1.flag1=INTERNAL
    group1.flag2=PREVIEW
    group1.flag3=COMPLETE:2025
    """.trimIndent()

    Truth.assertThat(
      FeatureConfigurationProvider.loadValues(content.byteInputStream(), FeatureConfiguration.INTERNAL).toMap()
    ).containsExactly(
      "group1.flag1", "true",
      "group1.flag2", "true",
      "group1.flag3", "true",
    )
  }

  @Test
  fun testPreview() {
    val content = """
    #some comments
    group1.flag1=INTERNAL
    group1.flag2=PREVIEW
    group1.flag3=COMPLETE:2025
    """.trimIndent()

    Truth.assertThat(
      FeatureConfigurationProvider.loadValues(content.byteInputStream(), FeatureConfiguration.PREVIEW).toMap()
    ).containsExactly(
      "group1.flag1", "false",
      "group1.flag2", "true",
      "group1.flag3", "true",
    )
  }

  @Test
  fun testComplete() {
    val content = """
    #some comments
    group1.flag1=INTERNAL
    group1.flag2=PREVIEW
    group1.flag3=COMPLETE:2025
    """.trimIndent()

    Truth.assertThat(
      FeatureConfigurationProvider.loadValues(content.byteInputStream(), FeatureConfiguration.COMPLETE).toMap()
    ).containsExactly(
      "group1.flag1", "false",
      "group1.flag2", "false",
      "group1.flag3", "true",
    )
  }

  @Test
  fun testUnitTest() {
    // Unit test should match to DEV channel.

    val content = """
    #some comments
    group1.flag1=INTERNAL
    group1.flag2=PREVIEW
    group1.flag3=COMPLETE:2025
    """.trimIndent()

    // make sure to use the default param for loadValues
    Truth.assertThat(
      FeatureConfigurationProvider.loadValues(content.byteInputStream()).toMap()
    ).containsExactly(
      "group1.flag1", "true",
      "group1.flag2", "true",
      "group1.flag3", "true",
    )
  }

  @Test
  fun testComments() {
    // Unit test should match to DEV channel.

    val content = """
    #some comments
    group1.flag1=INTERNAL # some comments
    group1.flag2=PREVIEW # some comments
    group1.flag3=COMPLETE:2025 # some comments
    """.trimIndent()

    Truth.assertThat(
      FeatureConfigurationProvider.loadValues(content.byteInputStream(), FeatureConfiguration.INTERNAL).toMap()
    ).containsExactly(
      "group1.flag1", "true",
      "group1.flag2", "true",
      "group1.flag3", "true",
    )
  }

  private fun FeatureConfigurationProvider.toMap(): Map<String, String> {
    return this.getEntries().associateNotNull { entry ->
      getValueById(entry)?.let { value ->
        entry to value
      }
    }
  }
}