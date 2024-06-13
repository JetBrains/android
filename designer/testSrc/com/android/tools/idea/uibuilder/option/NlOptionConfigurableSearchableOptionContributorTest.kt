/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.option

import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.options.LABEL_MAGNIFY_ZOOMING_SENSITIVITY
import com.android.tools.idea.uibuilder.options.LABEL_TRACK_PAD
import com.android.tools.idea.uibuilder.options.NlOptionConfigurableSearchableOptionContributor
import com.intellij.ide.ui.search.SearchableOptionProcessor
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.registry.Registry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class NlOptionConfigurableSearchableOptionContributorTest {

  @JvmField @Rule val projectRule = AndroidProjectRule.inMemory()

  @Test
  fun testCanFindMagnifyOptionsOnMacWhenMouseGestureEnabled() {
    val magnifySupported =
      SystemInfo.isMac && Registry.`is`("actionSystem.mouseGesturesEnabled", true)
    if (magnifySupported) {
      val contributor = NlOptionConfigurableSearchableOptionContributor()
      val processor = TestSearchableOptionProcessor()
      contributor.processOptions(processor)

      assertTrue(processor.getHits("track").contains(LABEL_TRACK_PAD))
      assertTrue(processor.getHits("pAd").contains(LABEL_TRACK_PAD))
      assertFalse(processor.getHits("trackpad").contains(LABEL_TRACK_PAD))
      assertFalse(processor.getHits("ad").contains(LABEL_TRACK_PAD))
      assertFalse(processor.getHits(" ").contains(LABEL_TRACK_PAD))

      assertTrue(processor.getHits("magnify").contains(LABEL_MAGNIFY_ZOOMING_SENSITIVITY))
      assertTrue(processor.getHits("zoom").contains(LABEL_MAGNIFY_ZOOMING_SENSITIVITY))
      assertTrue(processor.getHits("Zooming").contains(LABEL_MAGNIFY_ZOOMING_SENSITIVITY))
      assertTrue(processor.getHits("pInCH").contains(LABEL_MAGNIFY_ZOOMING_SENSITIVITY))
      assertTrue(processor.getHits("sensi").contains(LABEL_MAGNIFY_ZOOMING_SENSITIVITY))
      assertTrue(processor.getHits("senSitivity").contains(LABEL_MAGNIFY_ZOOMING_SENSITIVITY))
      assertFalse(processor.getHits("gnify").contains(LABEL_MAGNIFY_ZOOMING_SENSITIVITY))
      assertFalse(processor.getHits("oom").contains(LABEL_MAGNIFY_ZOOMING_SENSITIVITY))
      assertFalse(processor.getHits("ensi").contains(LABEL_MAGNIFY_ZOOMING_SENSITIVITY))
      assertFalse(processor.getHits("sensitive").contains(LABEL_MAGNIFY_ZOOMING_SENSITIVITY))
    }
  }
}

private class TestSearchableOptionProcessor : SearchableOptionProcessor() {
  private val hitMap = mutableMapOf<String, MutableSet<String>>()

  override fun addOptions(
    text: String,
    path: String?,
    hit: String?,
    configurableId: String,
    configurableDisplayName: String?,
    applyStemming: Boolean,
  ) {
    if (hit == null) {
      return
    }
    for (keyword in text.split(" ")) {
      val hitSet: MutableSet<String> = hitMap.computeIfAbsent(keyword) { mutableSetOf() }
      hitSet.add(hit)
    }
  }

  fun getHits(text: String): Set<String> =
    hitMap.filterKeys { key -> key.startsWith(text, true) }.values.flatten().toSet()
}
