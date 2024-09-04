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

import com.google.common.truth.Truth.assertThat
import org.jsoup.Jsoup
import org.junit.Test

// This test was copied from Gemini plugin's MarkdownConverterTest

class MarkdownConverterTest {

  private val converter = MarkDownConverter { AqiHtmlRenderer(it) }

  @Test
  fun render_plaintext_by_wrapping_it() {
    val md = "write a kotlin function"

    val expected = "<p>write a kotlin function</p>".wrapWithHtml()

    converter.toHtml(md).assertEqualsSemantically(expected)
  }

  @Test
  fun render_ordered_lists_without_wrapping() {
    val md =
      """
      1. Kotlin coroutines are lightweight
      2. Kotlin coroutines are cancellable
      """
        .trimIndent()

    val expected =
      """
      <ol>
      <li>Kotlin coroutines are lightweight</li>
      <li>Kotlin coroutines are cancellable</li>
      </ol>
      """
        .trimIndent()
        .wrapWithHtml()

    converter.toHtml(md).assertEqualsSemantically(expected)
  }

  @Test
  fun render_unordered_lists_without_wrapping() {
    val md =
      """
      * Kotlin coroutines are **lightweight**
      * Kotlin coroutines are _cancellable_
      """
        .trimIndent()

    val expected =
      """
      <ul>
      <li>Kotlin coroutines are <strong>lightweight</strong></li>
      <li>Kotlin coroutines are <em>cancellable</em></li>
      </ul>
      """
        .trimIndent()
        .wrapWithHtml()

    converter.toHtml(md).assertEqualsSemantically(expected)
  }

  @Test
  fun escape_tags() {
    val md = "write <a> kotlin function"

    val expected = "<p>write &lt;a&gt; kotlin function</p>".wrapWithHtml()

    converter.toHtml(md).assertEqualsSemantically(expected)
  }

  @Test
  fun escape_entities() {
    val md = "write &aacute; kotlin function"

    val expected = "<p>write á kotlin function</p>".wrapWithHtml()

    converter.toHtml(md).assertEqualsSemantically(expected)
  }

  @Test
  fun do_not_escape_tags_in_codeblocks() {
    val md =
      """
      I am a friendly intro.

      ```
      write <a> kotlin function
      ```

      ...and this is the outro.
      """
        .trimIndent()

    val expected =
      """
      <p>I am a friendly intro.</p>
      <pre><code>write &lt;a&gt; kotlin function
      </code></pre>
      <p>...and this is the outro.</p>
      """
        .trimIndent()
        .wrapWithHtml()

    converter.toHtml(md).assertEqualsSemantically(expected)
  }

  @Test
  fun do_not_escape_entities_in_codeblocks() {
    val md =
      """
      I am a friendly intro.

      ```
      write &aacute; kotlin function
      ```

      ...and this is the outro.
      """
        .trimIndent()

    val expected =
      """
      <p>I am a friendly intro.</p>
      <pre><code>write &amp;aacute; kotlin function
      </code></pre>
      <p>...and this is the outro.</p>
      """
        .trimIndent()
        .wrapWithHtml()

    converter.toHtml(md).assertEqualsSemantically(expected)
  }

  @Test
  fun do_not_double_escape_tags_in_backticks() {
    val md = "write `<a>` kotlin function"

    val expected = "<p>write <code>&lt;a&gt;</code> kotlin function</p>".wrapWithHtml()

    converter.toHtml(md).assertEqualsSemantically(expected)
  }

  @Test
  fun do_not_double_escape_entities_in_backticks() {
    val md = "write `&aacute;` kotlin function"

    val expected = "<p>write <code>&amp;aacute;</code> kotlin function</p>".wrapWithHtml()

    converter.toHtml(md).assertEqualsSemantically(expected)
  }

  @Test
  fun handle_escaped_characters() {
    val md = "write \\<br> kotlin \\\\`&aacute;` function"

    val expected =
      "<p>write &lt;br&gt; kotlin \\<code>&amp;aacute;</code> function</p>".wrapWithHtml()

    converter.toHtml(md).assertEqualsSemantically(expected)
  }

  @Test
  fun render_inline_code_block() {
    val input =
      "If you are still having trouble, you can try using a different type of list, such as a `List<Object>`"

    val expected =
      "<p>If you are still having trouble, you can try using a different type of list, such as a <code>List&lt;Object&gt;</code></p>"
        .wrapWithHtml()
    converter.toHtml(input).assertEqualsSemantically(expected)
  }

  @Test
  fun render_md_paragraphs() {
    val input =
      "\r\n\r\nHere is a song that I can sing:\r\n\r\nTwinkle, Twinkle, Little Star\r\n\r\nTwinkle, twinkle, ..."

    val expected =
      "<p>Here is a song that I can sing:</p>  <p>Twinkle, Twinkle, Little Star</p><p>Twinkle, twinkle, ...</p>"
        .wrapWithHtml()
    converter.toHtml(input).assertEqualsSemantically(expected)
  }

  @Test
  fun render_removes_images() {
    val input = "Consider the following ![Block Diagram](block.png):"
    val expected = "<p>Consider the following Block Diagram:</p>"
    converter.toHtml(input).assertEqualsSemantically(expected)
  }

  @Test
  fun render_inserts_wbr_in_urls() {
    val input = "Consider this: https://thisisaverylongtoken.com/hello/break/please yey"

    val expected =
      "<p>Consider this: " +
        "https://thisisaverylongtoken.<wbr>com/<wbr>hello/<wbr>break/<wbr>please yey</p>"
    converter.toHtml(input).assertEqualsSemantically(expected)
  }

  private fun String.assertEqualsSemantically(expected: String) {
    val cleanedActual = Jsoup.parse(this).html().replace("\n", "")
    val cleanedExpected = Jsoup.parse(expected).html().replace("\n", "")

    assertThat(cleanedActual).isEqualTo(cleanedExpected)
  }

  private fun String.wrapWithHtml() = "<html><body>${this.trim()}</body></html>"
}
