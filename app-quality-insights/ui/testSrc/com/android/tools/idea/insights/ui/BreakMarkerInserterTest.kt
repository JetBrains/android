/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.insights.ui

import com.android.tools.idea.insights.ui.BreakMarkerInserter.BREAK_MARKER
import com.google.common.truth.Truth.assertThat
import org.junit.Test

// This test was copied from Gemini plugin's BreakMarkerInserterTest

class BreakMarkerInserterTest {

  // Note: the default min breakable token length is MIN_BREAKABLE_TOKEN_LENGTH = 15

  @Test
  fun does_not_break_tokens_shorter_than_min() {
    val input = "This string should not be broken up"
    val expected = "This string should not be broken up"
    assertThat(BreakMarkerInserter.insertBreakMarkersInLongTokens(input)).isEqualTo(expected)
  }

  @Test
  fun breaks_long_tokens_only_after_non_word_characters() {
    val input =
      "Not breaking: hippopotomonstrosesquipedaliophobia antidisestablishmentarianism\n" +
        "Breaking: hippopotomonstrosesquipedaliophobia.antidisestablishmentarianism"
    val expected =
      "Not breaking: hippopotomonstrosesquipedaliophobia antidisestablishmentarianism\n" +
        "Breaking: hippopotomonstrosesquipedaliophobia.${BREAK_MARKER}antidisestablishmentarianism"
    assertThat(BreakMarkerInserter.insertBreakMarkersInLongTokens(input)).isEqualTo(expected)
  }

  @Test
  fun breaks_long_tokens_after_the_min_and_not_before() {
    val input = "This string should-not-be-broken-up"
    val expected = "This string should-not-be-broken-${BREAK_MARKER}up"
    assertThat(BreakMarkerInserter.insertBreakMarkersInLongTokens(input)).isEqualTo(expected)
  }

  @Test
  fun does_not_break_tokens_shorter_than_custom_min() {
    val input = "This string should not be broken up"
    val expected = "This string should not be broken up"
    val actual =
      BreakMarkerInserter.insertBreakMarkersInLongTokens(input, minBreakableTokenLength = 10)
    assertThat(actual).isEqualTo(expected)
  }

  @Test
  fun does_not_break_on_whitespace() {
    val input = "hippopotomonstrosesquipedaliophobia\nantidisestablishmentarianism"
    val expected = "hippopotomonstrosesquipedaliophobia\nantidisestablishmentarianism"
    assertThat(BreakMarkerInserter.insertBreakMarkersInLongTokens(input)).isEqualTo(expected)
  }

  @Test
  fun breaks_long_tokens_after_custom_min_and_not_before() {
    val input = "This string should-not-be-broken-up"
    val expected =
      "This string should-not-${BREAK_MARKER}be-${BREAK_MARKER}broken-${BREAK_MARKER}up"
    val actual =
      BreakMarkerInserter.insertBreakMarkersInLongTokens(input, minBreakableTokenLength = 10)
    assertThat(actual).isEqualTo(expected)
  }

  @Test
  fun does_not_break_long_tokens_shorter_than_custom_min() {
    val input = "This string should-not-be-broken-up"
    val expected =
      "This string should-not-${BREAK_MARKER}be-${BREAK_MARKER}broken-${BREAK_MARKER}up"
    val actual =
      BreakMarkerInserter.insertBreakMarkersInLongTokens(input, minBreakableTokenLength = 10)
    assertThat(actual).isEqualTo(expected)
  }

  @Test
  fun breaks_urls_correctly() {
    val input =
      "This URL should be broken up correctly: " +
        "https://developer.android.com/studio/preview/studio-bot?q=hello+world"
    val expected =
      "This URL should be broken up correctly: " +
        "https://developer.%%%%%break-goes-here%%%%%android.%%%%%break-goes-here%%%%%com/%%%%%break-goes-here%%%%%studio/%%%%%break-goes-here%%%%%preview/%%%%%break-goes-here%%%%%studio-%%%%%break-goes-here%%%%%bot?%%%%%break-goes-here%%%%%q=%%%%%break-goes-here%%%%%hello+%%%%%break-goes-here%%%%%world"
    assertThat(BreakMarkerInserter.insertBreakMarkersInLongTokens(input)).isEqualTo(expected)
  }
}
