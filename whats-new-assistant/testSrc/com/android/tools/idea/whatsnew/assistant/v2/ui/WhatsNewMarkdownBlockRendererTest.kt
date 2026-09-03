/*
 * Copyright (C) 2026 The Android Open Source Project
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
@file:Suppress("OPT_IN_USAGE", "UnstableApiUsage")

package com.android.tools.idea.whatsnew.assistant.v2.ui

// JetBrains patch: these tests build MarkdownBlock.HtmlBlock, which Jewel removed in JEWEL-1345, and they assert that cards carrying a
//  `<!-- FEATURE_FLAG=... -->` comment are filtered out. The filtering they cover is switched off in WhatsNewMarkdownBlockRenderer for the
//  same reason, and the What's New Assistant is disabled in IntelliJ.
/*
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.whatsnew.assistant.v2.ui.WhatsNewMarkdownBlockRenderer.Companion.MarkdownGroup
import com.android.tools.idea.whatsnew.assistant.v2.ui.WhatsNewMarkdownBlockRenderer.Companion.filterFeatureFlagGroups
import com.android.tools.idea.whatsnew.assistant.v2.ui.WhatsNewMarkdownBlockRenderer.Companion.groupBlocksByH1ThenH2Blocks
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.markdown.InlineMarkdown
import org.jetbrains.jewel.markdown.MarkdownBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalJewelApi::class)
class WhatsNewMarkdownBlockRendererTest {
  @Test
  fun filterFeatureFlagGroups_discardsCellWhenFlagIsDisabled() {
    // This is just an example flag, not a real usage of the flag. Update this test with a different flag if we want to delete this flag; do
    // not delete the test.
    StudioFlags.LOCAL_GEMMA_ENABLED.override(false)
    try {
      val blocks =
        listOf(
          heading("Title", level = 1),
          paragraph("Intro text"),
          heading("Section 1", level = 2),
          paragraph("Section 1 text"),
          heading("Gemma Section", level = 2),
          htmlBlock("<!-- FEATURE_FLAG=studiobot.local.gemma.enabled -->"),
          paragraph("Gemma text"),
          heading("Section 3", level = 2),
          paragraph("Section 3 text"),
        )

      val groups = filterFeatureFlagGroups(groupBlocksByH1ThenH2Blocks(blocks))

      assertEquals(3, groups.size)
      assertTrue(groups[0] is MarkdownGroup.Rows)
      assertEquals(listOf(heading("Title", 1), paragraph("Intro text")), (groups[0] as MarkdownGroup.Rows).blocks)

      assertTrue(groups[1] is MarkdownGroup.Cell)
      assertEquals(listOf(heading("Section 1", 2), paragraph("Section 1 text")), (groups[1] as MarkdownGroup.Cell).blocks)

      assertTrue(groups[2] is MarkdownGroup.Cell)
      assertEquals(listOf(heading("Section 3", 2), paragraph("Section 3 text")), (groups[2] as MarkdownGroup.Cell).blocks)
    } finally {
      StudioFlags.LOCAL_GEMMA_ENABLED.clearOverride()
    }
  }

  @Test
  fun filterFeatureFlagGroups_keepsCellWhenFlagIsEnabled() {
    // This is just an example flag, not a real usage of the flag. Update this test with a different flag if we want to delete this flag; do
    // not delete the test.
    StudioFlags.LOCAL_GEMMA_ENABLED.override(true)
    try {
      val blocks =
        listOf(
          heading("Title", level = 1),
          paragraph("Intro text"),
          heading("Section 1", level = 2),
          paragraph("Section 1 text"),
          heading("Gemma Section", level = 2),
          htmlBlock("<!-- FEATURE_FLAG=studiobot.local.gemma.enabled -->"),
          paragraph("Gemma text"),
          heading("Section 3", level = 2),
          paragraph("Section 3 text"),
        )

      val groups = filterFeatureFlagGroups(groupBlocksByH1ThenH2Blocks(blocks))

      assertEquals(4, groups.size)
      assertTrue(groups[0] is MarkdownGroup.Rows)
      assertTrue(groups[1] is MarkdownGroup.Cell)
      assertEquals(listOf(heading("Section 1", 2), paragraph("Section 1 text")), (groups[1] as MarkdownGroup.Cell).blocks)

      assertTrue(groups[2] is MarkdownGroup.Cell)
      assertEquals(
        listOf(
          heading("Gemma Section", 2),
          htmlBlock("<!-- FEATURE_FLAG=studiobot.local.gemma.enabled -->"),
          paragraph("Gemma text"),
        ),
        (groups[2] as MarkdownGroup.Cell).blocks,
      )

      assertTrue(groups[3] is MarkdownGroup.Cell)
      assertEquals(listOf(heading("Section 3", 2), paragraph("Section 3 text")), (groups[3] as MarkdownGroup.Cell).blocks)
    } finally {
      StudioFlags.LOCAL_GEMMA_ENABLED.clearOverride()
    }
  }

  @Test
  fun filterFeatureFlagGroups_multipleFlags_enabledAndDisabled() {
    // This is just an example flag, not a real usage of the flag. Update this test with a different flag if we want to delete this flag; do
    // not delete the test.
    StudioFlags.WHATS_NEW_V2.override(true)
    StudioFlags.LOCAL_GEMMA_ENABLED.override(false)
    try {
      val blocks =
        listOf(
          heading("Title", level = 1),
          paragraph("Intro text"),
          heading("What's New V2 Section", level = 2),
          htmlBlock("<!-- FEATURE_FLAG=whatsnew.use.whats.new.v2 -->"),
          paragraph("V2 text"),
          heading("Gemma Section", level = 2),
          htmlBlock("<!-- FEATURE_FLAG=studiobot.local.gemma.enabled -->"),
          paragraph("Gemma text"),
          heading("Section 3", level = 2),
          paragraph("Section 3 text"),
        )

      val groups = filterFeatureFlagGroups(groupBlocksByH1ThenH2Blocks(blocks))

      assertEquals(3, groups.size)
      assertTrue(groups[0] is MarkdownGroup.Rows)

      assertTrue(groups[1] is MarkdownGroup.Cell)
      assertEquals(
        listOf(
          heading("What's New V2 Section", 2),
          htmlBlock("<!-- FEATURE_FLAG=whatsnew.use.whats.new.v2 -->"),
          paragraph("V2 text"),
        ),
        (groups[1] as MarkdownGroup.Cell).blocks,
      )

      assertTrue(groups[2] is MarkdownGroup.Cell)
      assertEquals(
        listOf(
          heading("Section 3", 2),
          paragraph("Section 3 text"),
        ),
        (groups[2] as MarkdownGroup.Cell).blocks,
      )
    } finally {
      StudioFlags.WHATS_NEW_V2.clearOverride()
      StudioFlags.LOCAL_GEMMA_ENABLED.clearOverride()
    }
  }

  private fun heading(text: String, level: Int): MarkdownBlock.Heading {
    return MarkdownBlock.Heading(listOf(InlineMarkdown.Text(text)), level)
  }

  private fun paragraph(text: String): MarkdownBlock.Paragraph {
    return MarkdownBlock.Paragraph(listOf(InlineMarkdown.Text(text)))
  }

  private fun htmlBlock(content: String): MarkdownBlock.HtmlBlock {
    return MarkdownBlock.HtmlBlock(content)
  }
}
*/
