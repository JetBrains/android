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

import com.google.common.collect.LinkedHashMultimap
import com.google.common.collect.Multimap

/**
 * Parser for a downloadable font query
 *
 * A simple parser that accept both the GMS core v11 and v12 syntax
 * of a downloadable font query.
 * The return type is either a [DownloadableParseResult] upon success
 * or a [ParseResult] if an error was found in the query string.
 */
class QueryParser {
  private val scanner = Scanner()
  private var symbol = Symbol.EOQ
  private var fontName = ""
  private var fonts = LinkedHashMultimap.create<String, FontDetail.Builder>()  // Keep order for tests
  private var fontDetail = FontDetail.Builder()

  companion object {
    @JvmStatic fun parseDownloadableFont(authority: String, query: String): ParseResult {
      val parser = QueryParser()
      return parser.parse(authority, query)
    }
  }

  private fun parse(authority: String, query: String): ParseResult {
    try {
      scanner.init(query)
      symbol = scanner.next()
      if (symbol == Symbol.NAME) {
        return parseV11(authority)
      }
      else {
        return parseV12(authority)
      }
    }
    catch (ex: FontQueryParserError) {
      return error()
    }
  }

  private fun parseV12(authority: String): ParseResult {
    fontDetail.myExact = true  // default in v12

    while (symbol != Symbol.EOQ) {
      parseFontName()
      while (symbol == Symbol.COLON) {
        symbol = scanner.next()
        parseFontStyle()
      }
      while (symbol == Symbol.COMMA) {
        parseFontAlternative()
        parseFontStyle()

        while (symbol == Symbol.COLON) {
          symbol = scanner.next()
          parseFontStyle()
        }
      }
      if (symbol == Symbol.SEPARATOR) {
        parseFontAlternative()
        expect(Symbol.ID)
      }
      else {
        expect(Symbol.EOQ)
      }
    }
    return createResult(authority)
  }

  private fun createResult(authority: String): DownloadableParseResult {
    fonts.put(fontName, fontDetail)
    return DownloadableParseResult(authority, fonts)
  }

  private fun parseFontStyle() {
    when (symbol) {
      Symbol.NUMBER -> parseWeightNumber()
      Symbol.WEIGHT -> parseWeight()
      Symbol.WEIGHT_SYNONYM -> parseWeightSynonym()
      Symbol.WIDTH -> parseWidth()
      Symbol.ITAL -> parseItal()
      Symbol.ITALIC -> parseItalic()
      Symbol.BOLD_ITALIC -> parseBoldItalic()
      Symbol.NEAREST -> parseNearest()
      Symbol.EXACT -> parseExact()
      else -> throw FontQueryParserError()
    }
    symbol = scanner.next()
  }

  private fun parseFontAlternative() {
    fonts.put(fontName, fontDetail)
    fontDetail = FontDetail.Builder()
    fontDetail.myExact = true  // default in v12
    symbol = scanner.next()
  }

  private fun parseFontName() {
    expect(Symbol.ID)
    fontName = scanner.last
    symbol = scanner.next()
  }

  private fun parseWeightNumber() {
    fontDetail.myWeight = scanner.number.toInt()
    if (scanner.peek() == Symbol.ITALIC) {
      scanner.next()
      fontDetail.myItalics = true
    }
  }

  private fun parseWeight() {
    symbol = scanner.next()
    expect(Symbol.NUMBER)
    fontDetail.myWeight = scanner.number.toInt()
  }

  private fun parseWeightSynonym() {
    fontDetail.myWeight = scanner.number.toInt()
  }

  private fun parseWidth() {
    symbol = scanner.next()
    expect(Symbol.NUMBER)
    fontDetail.myWidth = scanner.number.toInt()
  }

  private fun parseItal() {
    symbol = scanner.next()
    expect(Symbol.NUMBER)
    fontDetail.myItalics = (scanner.number >= .5f)
  }

  private fun parseItalic() {
    fontDetail.myItalics = true
  }

  private fun parseBoldItalic() {
    fontDetail.myWeight = 700
    fontDetail.myItalics = true
  }

  private fun parseNearest() {
    // TODO: Check that we dont have both exact & nearest specified
    fontDetail.myExact = false
  }

  private fun parseExact() {
    // TODO: Check that we dont have both exact & nearest specified
    fontDetail.myExact = true
  }

  private fun parseBestEffort() {
    symbol = scanner.next()
    expect(Symbol.ID)
    fontDetail.myExact = (scanner.last != "true")
  }

  private fun expect(expected: Symbol) {
    if (symbol != expected) {
      throw FontQueryParserError()
    }
  }

  private fun parseV11(authority: String): ParseResult {
    fontDetail.myExact = false  // default in v11

    symbol = scanner.next()
    expect(Symbol.EQUALS)
    symbol = scanner.next()
    expect(Symbol.ID)
    fontName = scanner.last
    symbol = scanner.next()

    while (symbol == Symbol.AND) {
      val key = scanner.next()
      symbol = scanner.next()
      expect(Symbol.EQUALS)
      when (key) {
        Symbol.WEIGHT -> parseWeight()
        Symbol.WIDTH -> parseWidth()
        Symbol.ITALIC -> parseItal()
        Symbol.BEST_EFFORT -> parseBestEffort()
        else -> throw FontQueryParserError()
      }
      symbol = scanner.next()
    }
    expect(Symbol.EOQ)
    return createResult(authority)
  }

  private fun error(): ParseResult {
    return ParseErrorResult("Unrecognized query found near: " + scanner.last)
  }

  open class ParseResult

  class ParseErrorResult(val error: String) : ParseResult()

  class DownloadableParseResult(
      val authority: String,
      val fonts: Multimap<String, FontDetail.Builder>) : ParseResult()

  private class FontQueryParserError : RuntimeException()

  /**
   * Scanner Symbols
   */
  private enum class Symbol {
    AND,            // "&"
    BEST_EFFORT,    // "besteffort" keyword
    BOLD_ITALIC,    // "bolditalic", or "bi" keywords
    COLON,          // ":"
    COMMA,          // ","
    EOQ,            // End of Query
    EQUALS,         // "="
    EXACT,          // "exact" keyword
    ID,             // An identifier
    ITAL,           // "ital" keyword
    ITALIC,         // "italic", "i", "ital" keywords
    NAME,           // "name" keyword (from v11)
    NEAREST,        // "nearest"
    NUMBER,         // A floating point number >= 0.0f
    SEPARATOR,      // "|"
    WEIGHT,         // "wght", "weight" keywords
    WEIGHT_SYNONYM, // Keywords with weight associations like: "thin", "bold", "regular", "black", etc
    WIDTH,          // "width", "wdth" keywords
  }

  /**
   * Font Query Scanner
   *
   * Scanner that has all the lexical elements from the font query v11 and v12.
   */
  private class Scanner {
    private var query = ""
    private var index = 0
    private var startIndex = 0
    private var isNumberPrefix = false

    var last = ""
      private set

    var number = 0f
      private set

    fun init(value: String) {
      query = value
      index = 0
      reset()
    }

    fun next(): Symbol {
      reset()
      if (index >= query.length) {
        last = ""
        return Symbol.EOQ
      }
      val ch = query[index++]
      when (ch) {
        in '0'..'9' -> return number()
        '=' -> return symbol(Symbol.EQUALS, ch)
        '&' -> return symbol(Symbol.AND, ch)
        ':' -> return symbol(Symbol.COLON, ch)
        '|' -> return symbol(Symbol.SEPARATOR, ch)
        ',' -> return symbol(Symbol.COMMA, ch)
        in 'a'..'z',
        in 'A'..'Z' -> return id()
        else -> throw FontQueryParserError()
      }
    }

    fun peek(): Symbol {
      val savedIndex = index
      val savedStartIndex = startIndex
      val savedLast = last
      val savedNumber = number
      val symbol = next()
      index = savedIndex
      startIndex = savedStartIndex
      last = savedLast
      number = savedNumber
      return symbol
    }

    private fun reset() {
      startIndex = index
      last = ""
      number = 0f
    }

    private fun number(): Symbol {
      while (index < query.length && query[index] in '0'..'9') {
        index++
      }
      if (index < query.length && query[index] == '.') {
        index++
      }
      while (index < query.length && query[index] in '0'..'9') {
        index++
      }
      last = query.substring(startIndex, index)
      number = last.toFloat()
      return Symbol.NUMBER
    }

    private fun id(): Symbol {
      isNumberPrefix = true
      var isSymbolPart = true
      while (isSymbolPart && index < query.length) {
        val ch = query[index++]
        isSymbolPart = when (ch) {
          in 'a'..'z',
          in 'A'..'Z' -> true
          '+', '-', ' ' -> true
          in '0'..'9' -> !checkNumberPrefix()
          else -> false
        }
      }
      if (!isSymbolPart) {
        index--
      }
      last = query.substring(startIndex, index)
      last = last.replace('+', ' ')
      return checkId(last)
    }

    private fun checkNumberPrefix(): Boolean {
      if (!isNumberPrefix) {
        return false
      }
      val prefix = query.substring(startIndex, index - 1)
      isNumberPrefix = when (prefix) {
        "wght", "wdth", "ital" -> true
        else -> false
      }
      return isNumberPrefix
    }

    private fun checkId(id: String): Symbol {
      return when (id) {
        "bi", "bolditalic" -> Symbol.BOLD_ITALIC
        "besteffort" -> Symbol.BEST_EFFORT
        "i", "italic" -> Symbol.ITALIC
        "ital" -> Symbol.ITAL
        "exact" -> Symbol.EXACT
        "name" -> Symbol.NAME
        "nearest" -> Symbol.NEAREST
        "wght", "weight" -> Symbol.WEIGHT
        "wdth", "width" -> Symbol.WIDTH
        "thin" -> weight(100)
        "extralight", "extra-light", "ultralight", "ultra-light" -> weight(200)
        "l", "light" -> weight(300)
        "r", "regular", "book" -> weight(400)
        "medium" -> weight(500)
        "semibold", "semi-bold", "demibold", "demi-bold" -> weight(600)
        "b", "bold" -> weight(700)
        "extrabold", "extra-bold", "ultrabold", "ultra-bold" -> weight(800)
        "black", "heavy" -> weight(900)
        else -> Symbol.ID
      }
    }

    private fun weight(amount: Int): Symbol {
      number = amount.toFloat()
      return Symbol.WEIGHT_SYNONYM
    }

    private fun symbol(symbol: Symbol, ch: Char): Symbol {
      last = ch.toString()
      return symbol
    }
  }
}
