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

import com.android.flags.BooleanFlag
import com.android.flags.FlagGroup
import com.android.flags.Flags
import com.android.flags.junit.FlagRule
import com.android.tools.idea.flags.FeatureConfiguration
import com.android.tools.idea.flags.StudioFlags
import com.android.utils.associateNotNull
import com.google.common.truth.Truth
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException

class FeatureConfigurationOverridesTest {

  @get:Rule
  val studioFlagRule = FlagRule(StudioFlags.FLAG_LEVEL)

  @get:Rule
  val exception = ExpectedException.none()

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

    StudioFlags.FLAG_LEVEL.override(FeatureConfiguration.INTERNAL)
    Truth.assertThat(
      FeatureConfigurationProvider.loadValues(content.byteInputStream()).toMap()
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
    StudioFlags.FLAG_LEVEL.override(FeatureConfiguration.PREVIEW)
    Truth.assertThat(
      FeatureConfigurationProvider.loadValues(content.byteInputStream()).toMap()
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
    StudioFlags.FLAG_LEVEL.override(FeatureConfiguration.COMPLETE)
    Truth.assertThat(
      FeatureConfigurationProvider.loadValues(content.byteInputStream()).toMap()
    ).containsExactly(
      "group1.flag1", "false",
      "group1.flag2", "false",
      "group1.flag3", "true",
    )
  }


  @Test
  fun testDebugInfo() {
    val content = """
    #some comments
    group1.flagInternal=INTERNAL
    group1.flagNightly=NIGHTLY
    group1.flagPreview=PREVIEW
    group1.flagComplete=COMPLETE:2025
    """.trimIndent()

    val flags = Flags()
    val group = FlagGroup(flags, "group1", "display")
    val offFlag = BooleanFlag(group, "flagOff", "name_c", "description_z")
    val internalFlag = BooleanFlag(group, "flagInternal", "name_a", "description_a")
    val nightlyFlag = BooleanFlag(group, "flagNightly", "name_b", "description_b")
    val previewFlag = BooleanFlag(group, "flagPreview", "name_b", "description_b")
    val completeFlag = BooleanFlag(group, "flagComplete", "name_c", "description_c")

    StudioFlags.FLAG_LEVEL.override(FeatureConfiguration.INTERNAL)
    FeatureConfigurationProvider.loadValues(content.byteInputStream()).let { internal ->
      assertThat(internal.getConfigurationExplanation(offFlag)).isNull()
      assertThat(internal.getConfigurationExplanation(internalFlag)).isEqualTo("Enabled only in internal builds")
      assertThat(internal.getConfigurationExplanation(nightlyFlag)).isEqualTo("Enabled only in internal and nightly builds")
      assertThat(internal.getConfigurationExplanation(previewFlag)).isEqualTo("Enabled only in internal, nightly and canary builds")
      assertThat(internal.getConfigurationExplanation(completeFlag)).isNull()
    }

    StudioFlags.FLAG_LEVEL.override(FeatureConfiguration.NIGHTLY)
    FeatureConfigurationProvider.loadValues(content.byteInputStream()).let { nightly ->
      assertThat(nightly.getConfigurationExplanation(offFlag)).isNull()
      assertThat(nightly.getConfigurationExplanation(internalFlag)).isEqualTo("Disabled by default. Enabled only in internal builds")
      assertThat(nightly.getConfigurationExplanation(nightlyFlag)).isEqualTo("Enabled only in internal and nightly builds")
      assertThat(nightly.getConfigurationExplanation(previewFlag)).isEqualTo("Enabled only in internal, nightly and canary builds")
      assertThat(nightly.getConfigurationExplanation(completeFlag)).isNull()
    }

    StudioFlags.FLAG_LEVEL.override(FeatureConfiguration.PREVIEW)
    FeatureConfigurationProvider.loadValues(content.byteInputStream()).let { preview ->
      assertThat(preview.getConfigurationExplanation(offFlag)).isNull()
      assertThat(preview.getConfigurationExplanation(internalFlag)).isEqualTo("Disabled by default. Enabled only in internal builds")
      assertThat(preview.getConfigurationExplanation(nightlyFlag)).isEqualTo("Disabled by default. Enabled only in internal and nightly builds")
      assertThat(preview.getConfigurationExplanation(previewFlag)).isEqualTo("Enabled only in internal, nightly and canary builds")
      assertThat(preview.getConfigurationExplanation(completeFlag)).isNull()
    }

    StudioFlags.FLAG_LEVEL.override(FeatureConfiguration.COMPLETE)
    FeatureConfigurationProvider.loadValues(content.byteInputStream()).let { complete ->
      assertThat(complete.getConfigurationExplanation(offFlag)).isNull()
      assertThat(complete.getConfigurationExplanation(internalFlag)).isEqualTo("Disabled by default. Enabled only in internal builds")
      assertThat(complete.getConfigurationExplanation(nightlyFlag)).isEqualTo("Disabled by default. Enabled only in internal and nightly builds")
      assertThat(complete.getConfigurationExplanation(previewFlag)).isEqualTo("Disabled by default. Enabled only in internal, nightly and canary builds")
      assertThat(complete.getConfigurationExplanation(completeFlag)).isNull()
    }
  }

  @Test
  fun testUnitTest() {
    StudioFlags.FLAG_LEVEL.clearOverride()
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
  fun testTrailingWhitespace() {
    val content = """
    #some comments
    group1.flag1=INTERNAL${' '}
    group1.flag2=PREVIEW${' '}
    group1.flag3=COMPLETE:2025${' '}
    """.trimIndent()

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
    val content = """
    #some comments
    group1.flag1=INTERNAL # some comments
    group1.flag2=PREVIEW # some comments
    group1.flag3=COMPLETE:2025 # some comments with = sign
    """.trimIndent()

    Truth.assertThat(
      FeatureConfigurationProvider.loadValues(content.byteInputStream()).toMap()
    ).containsExactly(
      "group1.flag1", "true",
      "group1.flag2", "true",
      "group1.flag3", "true",
    )
  }

  @Test
  fun testWrongValues() {
    val content = """
    #some comments
    group1.flag1=INTERNAL
    group1.flag2=false
    group1.flag3=COMPLETE:2025
    """.trimIndent()

    exception.expectMessage("Invalid value 'false' for flag 'group1.flag2'")
    FeatureConfigurationProvider.loadValues(content.byteInputStream())
  }

  private fun FeatureConfigurationProvider.toMap(): Map<String, String> {
    return this.getEntries().associateNotNull { entry ->
      getValueById(entry)?.let { value ->
        entry to value
      }
    }
  }
}
