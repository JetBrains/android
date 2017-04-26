/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.fonts

import com.android.tools.idea.fonts.QueryParserTest.FontMatching.BEST_EFFORT
import com.android.tools.idea.fonts.QueryParserTest.FontMatching.EXACT
import com.android.tools.idea.fonts.QueryParserTest.Typeface.ROMAN
import com.android.tools.idea.fonts.QueryParserTest.Typeface.ITALIC
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.fail
import org.junit.Test

internal class QueryParserTest {
  enum class Typeface {ROMAN, ITALIC }
  enum class FontMatching {EXACT, BEST_EFFORT }

  @Test
  fun openSansV11() {
    val result = parse("name=Open Sans&weight=600&width=110&italic=1")
    assertThat(result.fonts.keys()).hasSize(1)
    assertFontEqual(result.fonts["Open Sans"].first(), 600, 110, ITALIC, BEST_EFFORT)
  }

  @Test
  fun openSansWithExactMatchV11() {
    val result = parse("name=Open Sans&weight=600&italic=1.0&besteffort=false")
    assertThat(result.fonts.keys()).hasSize(1)
    assertFontEqual(result.fonts["Open Sans"].first(), 600, 100, ITALIC, EXACT)
  }

  @Test
  fun openSansWithBestEffortMatchV11() {
    val result = parse("name=Open Sans&weight=800&width=90.0&besteffort=true")
    assertThat(result.fonts.keys()).hasSize(1)
    assertFontEqual(result.fonts["Open Sans"].first(), 800, 90, ROMAN, BEST_EFFORT)
  }

  @Test
  fun allWeightSpecifications() {
    val result = parse("Roboto:100,wght200,300italic,400i")
    assertThat(result.fonts.keys()).hasSize(4)
    assertFontEqual(result.fonts["Roboto"].elementAt(0), 100, 100, ROMAN, EXACT)
    assertFontEqual(result.fonts["Roboto"].elementAt(1), 200, 100, ROMAN, EXACT)
    assertFontEqual(result.fonts["Roboto"].elementAt(2), 300, 100, ITALIC, EXACT)
    assertFontEqual(result.fonts["Roboto"].elementAt(3), 400, 100, ITALIC, EXACT)
  }

  @Test
  fun allWeight100Synonyms() {
    val result = parse("Roboto:thin,extralight,extra-light,ultralight,ultra-light,l,light,r,regular,book,medium,semibold,semi-bold," +
        "demibold,demi-bold,b,bold,extrabold,extra-bold,ultrabold,ultra-bold,black,heavy")
    assertThat(result.fonts.keys()).hasSize(23)
    assertFontEqual(result.fonts["Roboto"].elementAt(0), 100, 100, ROMAN, EXACT)  // thin
    assertFontEqual(result.fonts["Roboto"].elementAt(1), 200, 100, ROMAN, EXACT)  // extralight
    assertFontEqual(result.fonts["Roboto"].elementAt(2), 200, 100, ROMAN, EXACT)  // extra-light
    assertFontEqual(result.fonts["Roboto"].elementAt(3), 200, 100, ROMAN, EXACT)  // ultralight
    assertFontEqual(result.fonts["Roboto"].elementAt(4), 200, 100, ROMAN, EXACT)  // ultra-light
    assertFontEqual(result.fonts["Roboto"].elementAt(5), 300, 100, ROMAN, EXACT)  // l
    assertFontEqual(result.fonts["Roboto"].elementAt(6), 300, 100, ROMAN, EXACT)  // light
    assertFontEqual(result.fonts["Roboto"].elementAt(7), 400, 100, ROMAN, EXACT)  // r
    assertFontEqual(result.fonts["Roboto"].elementAt(8), 400, 100, ROMAN, EXACT)  // regular
    assertFontEqual(result.fonts["Roboto"].elementAt(9), 400, 100, ROMAN, EXACT)  // book
    assertFontEqual(result.fonts["Roboto"].elementAt(10), 500, 100, ROMAN, EXACT) // medium
    assertFontEqual(result.fonts["Roboto"].elementAt(11), 600, 100, ROMAN, EXACT) // semibold
    assertFontEqual(result.fonts["Roboto"].elementAt(12), 600, 100, ROMAN, EXACT) // semi-bold
    assertFontEqual(result.fonts["Roboto"].elementAt(13), 600, 100, ROMAN, EXACT) // demibold
    assertFontEqual(result.fonts["Roboto"].elementAt(14), 600, 100, ROMAN, EXACT) // demi-bold
    assertFontEqual(result.fonts["Roboto"].elementAt(15), 700, 100, ROMAN, EXACT) // b
    assertFontEqual(result.fonts["Roboto"].elementAt(16), 700, 100, ROMAN, EXACT) // bold
    assertFontEqual(result.fonts["Roboto"].elementAt(17), 800, 100, ROMAN, EXACT) // extrabold
    assertFontEqual(result.fonts["Roboto"].elementAt(18), 800, 100, ROMAN, EXACT) // extra-bold
    assertFontEqual(result.fonts["Roboto"].elementAt(19), 800, 100, ROMAN, EXACT) // ultrabold
    assertFontEqual(result.fonts["Roboto"].elementAt(20), 800, 100, ROMAN, EXACT) // ultra-bold
    assertFontEqual(result.fonts["Roboto"].elementAt(21), 900, 100, ROMAN, EXACT) // black
    assertFontEqual(result.fonts["Roboto"].elementAt(22), 900, 100, ROMAN, EXACT) // heavy
  }

  @Test
  fun robotoWidth() {
    val result = parse("Roboto:100:wdth90,wght200:wdth110")
    assertThat(result.fonts.keys()).hasSize(2)
    assertFontEqual(result.fonts["Roboto"].elementAt(0), 100, 90, ROMAN, EXACT)
    assertFontEqual(result.fonts["Roboto"].elementAt(1), 200, 110, ROMAN, EXACT)
  }

  @Test
  fun allItalics() {
    val result = parse("Roboto:200i,300italic,ital0.0,ital0.5,ital1.0,italic,i,bolditalic,bi")
    assertThat(result.fonts.keys()).hasSize(9)
    assertFontEqual(result.fonts["Roboto"].elementAt(0), 200, 100, ITALIC, EXACT)
    assertFontEqual(result.fonts["Roboto"].elementAt(1), 300, 100, ITALIC, EXACT)
    assertFontEqual(result.fonts["Roboto"].elementAt(2), 400, 100, ROMAN, EXACT)
    assertFontEqual(result.fonts["Roboto"].elementAt(3), 400, 100, ITALIC, EXACT)
    assertFontEqual(result.fonts["Roboto"].elementAt(4), 400, 100, ITALIC, EXACT)
    assertFontEqual(result.fonts["Roboto"].elementAt(5), 400, 100, ITALIC, EXACT)
    assertFontEqual(result.fonts["Roboto"].elementAt(6), 400, 100, ITALIC, EXACT)
    assertFontEqual(result.fonts["Roboto"].elementAt(7), 700, 100, ITALIC, EXACT)
    assertFontEqual(result.fonts["Roboto"].elementAt(8), 700, 100, ITALIC, EXACT)
  }

  @Test
  fun openSans() {
    val result = parse("Open+Sans")
    assertThat(result.fonts.keys()).hasSize(1)
    assertFontEqual(result.fonts["Open Sans"].first(), 400, 100, ROMAN, EXACT)
  }

  @Test
  fun openSansWithMultipleWeights() {
    val result = parse("Open+Sans:300,600,700")
    assertThat(result.fonts.keys()).hasSize(3)
    assertFontEqual(result.fonts["Open Sans"].elementAt(0), 300, 100, ROMAN, EXACT)
    assertFontEqual(result.fonts["Open Sans"].elementAt(1), 600, 100, ROMAN, EXACT)
    assertFontEqual(result.fonts["Open Sans"].elementAt(2), 700, 100, ROMAN, EXACT)
  }

  @Test
  fun picaItalics() {
    val result = parse("IM+Fell+DW+Pica:italic")
    assertThat(result.fonts.keys()).hasSize(1)
    assertFontEqual(result.fonts["IM Fell DW Pica"].first(), 400, 100, ITALIC, EXACT)
  }

  @Test
  fun droidSansBoldItalic() {
    val result = parse("Droid+Sans:bolditalic")
    assertThat(result.fonts.keys()).hasSize(1)
    assertFontEqual(result.fonts["Droid Sans"].first(), 700, 100, ITALIC, EXACT)
  }

  @Test
  fun droidSansBoldItalicShortCut() {
    val result = parse("Droid+Sans:bi")
    assertThat(result.fonts.keys()).hasSize(1)
    assertFontEqual(result.fonts["Droid Sans"].first(), 700, 100, ITALIC, EXACT)
  }

  @Test
  fun droidSansBoldItalicAndBold() {
    val result = parse("Droid+Sans:bolditalic,b")
    assertThat(result.fonts.keys()).hasSize(2)
    assertFontEqual(result.fonts["Droid Sans"].elementAt(0), 700, 100, ITALIC, EXACT)
    assertFontEqual(result.fonts["Droid Sans"].elementAt(1), 700, 100, ROMAN, EXACT)
  }

  @Test
  fun multiple() {
    val result = parse("Tangerine|Inconsolata")
    assertThat(result.fonts.keys()).hasSize(2)
    assertFontEqual(result.fonts["Tangerine"].first(), 400, 100, ROMAN, EXACT)
    assertFontEqual(result.fonts["Inconsolata"].first(), 400, 100, ROMAN, EXACT)
  }

  @Test
  fun multipleWithStyles() {
    val result = parse("Tangerine:b|Inconsolata:r,400i")
    assertThat(result.fonts.keys()).hasSize(3)
    assertFontEqual(result.fonts["Tangerine"].first(), 700, 100, ROMAN, EXACT)
    assertFontEqual(result.fonts["Inconsolata"].elementAt(0), 400, 100, ROMAN, EXACT)
    assertFontEqual(result.fonts["Inconsolata"].elementAt(1), 400, 100, ITALIC, EXACT)
  }

  @Test
  fun multipleWithStyles2() {
    val result = parse("Tangerine:b|Inconsolata:r,400:ital0.8")
    assertThat(result.fonts.keys()).hasSize(3)
    assertFontEqual(result.fonts["Tangerine"].first(), 700, 100, ROMAN, EXACT)
    assertFontEqual(result.fonts["Inconsolata"].elementAt(0), 400, 100, ROMAN, EXACT)
    assertFontEqual(result.fonts["Inconsolata"].elementAt(1), 400, 100, ITALIC, EXACT)
  }

  @Test
  fun moreMultipleWithStyles() {
    val result = parse("Open+Sans:400,700|Roboto:700|Slabo+27px:400")
    assertThat(result.fonts.keys()).hasSize(4)
    assertFontEqual(result.fonts["Open Sans"].elementAt(0), 400, 100, ROMAN, EXACT)
    assertFontEqual(result.fonts["Open Sans"].elementAt(1), 700, 100, ROMAN, EXACT)
    assertFontEqual(result.fonts["Roboto"].elementAt(0), 700, 100, ROMAN, EXACT)
    assertFontEqual(result.fonts["Slabo 27px"].elementAt(0), 400, 100, ROMAN, EXACT)
  }

  @Test
  fun tangerineWithVariant() {
    val result = parse("Tangerine:r,b")
    assertThat(result.fonts.keys()).hasSize(2)
    assertFontEqual(result.fonts["Tangerine"].elementAt(0), 400, 100, ROMAN, EXACT)
    assertFontEqual(result.fonts["Tangerine"].elementAt(1), 700, 100, ROMAN, EXACT)
  }

  @Test
  fun robotoWithVariant() {
    val result = parse("Roboto:300,400,500,600,700,800,900,900italic")
    assertThat(result.fonts.keys()).hasSize(8)
    assertFontEqual(result.fonts["Roboto"].elementAt(0), 300, 100, ROMAN, EXACT)
    assertFontEqual(result.fonts["Roboto"].elementAt(1), 400, 100, ROMAN, EXACT)
    assertFontEqual(result.fonts["Roboto"].elementAt(2), 500, 100, ROMAN, EXACT)
    assertFontEqual(result.fonts["Roboto"].elementAt(3), 600, 100, ROMAN, EXACT)
    assertFontEqual(result.fonts["Roboto"].elementAt(4), 700, 100, ROMAN, EXACT)
    assertFontEqual(result.fonts["Roboto"].elementAt(5), 800, 100, ROMAN, EXACT)
    assertFontEqual(result.fonts["Roboto"].elementAt(6), 900, 100, ROMAN, EXACT)
    assertFontEqual(result.fonts["Roboto"].elementAt(7), 900, 100, ITALIC, EXACT)
  }

  @Test
  fun nearestVersusExact() {
    val result = parse("Tangerine:600:nearest,800:exact")
    assertThat(result.fonts.keys()).hasSize(2)
    assertFontEqual(result.fonts["Tangerine"].elementAt(0), 600, 100, ROMAN, BEST_EFFORT)
    assertFontEqual(result.fonts["Tangerine"].elementAt(1), 800, 100, ROMAN, EXACT)
  }

  private fun parse(query: String): QueryParser.DownloadableParseResult {
    val result = QueryParser.parseDownloadableFont(GoogleFontProvider.GOOGLE_FONT_AUTHORITY, query)
    if (result is QueryParser.DownloadableParseResult) {
      assertThat(result.authority).isEqualTo(GoogleFontProvider.GOOGLE_FONT_AUTHORITY)
      return result
    }
    if (result is QueryParser.ParseErrorResult) {
      fail(result.error)
    }
    throw RuntimeException("Unexpected Result " + result.javaClass.name)
  }

  private fun assertFontEqual(font: FontDetail.Builder,
                              expectedWeight: Int,
                              expectedWidth: Int,
                              expectedTypeface: Typeface,
                              expectedMatching: FontMatching) {
    assertThat(font.myWeight).isEqualTo(expectedWeight)
    assertThat(font.myWidth).isEqualTo(expectedWidth)
    assertThat(font.myItalics).isEqualTo(expectedTypeface == ITALIC)
    assertThat(font.myExact).isEqualTo(expectedMatching == EXACT)
  }
}
