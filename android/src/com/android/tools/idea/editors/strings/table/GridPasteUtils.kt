/*
 * Copyright (C) 2022 The Android Open Source Project
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
@file:JvmName("GridPasteUtils")
package com.android.tools.idea.editors.strings.table

import it.unimi.dsi.fastutil.ints.IntArrayList

/**
 * Splits a string to a grid mimicking the behavior of pasting to a spreadsheet.
 *
 * Newline and tab characters are treated as row and cell delimiters respectively unless they are
 * located inside segments enclosed in double quotes (`'"'`). Two consecutive double quotes inside
 * a quoted segment are interpreted as one double quote.
 *
 * The result is always a rectangular grid, which in some cases requires adding empty cells. If any
 * empty cells had to be added to produce a rectangular grid, the method considered alternative
 * interpretations of the input string, where some newline and tab characters are treated as row
 * and cell delimiters despite being located inside quoted segments. An interpretation that
 * minimizes the number of added empty cells is preferred.
 *
 * See `GridParseUtilsTest` for examples of input strings and the produced grids.
 */
fun String.splitIntoGrid(): List<List<String>> {
  return Parser(this).parse()
}

private const val MAX_PARSE_ITERATIONS = 1000

private class Parser(val str: String) {
  private val currentGrid = mutableListOf<MutableList<String>>()
  private var currentRow = mutableListOf<String>()
  private var insideQuotedSegment = false
  private var openingQuoteAccepted = true
  private var segmentStart = 0
  private var offset = 0
  private var fillerSellCount = 0
  private var quotedDelimiterCount = Int.MAX_VALUE
  private var maxQuotedDelimiterCount = 0
  /**
   * Determines the set of newline and/or tab characters located inside quoted segments that are
   * treated as row and cell delimiters. This is triggered when quotedDelimiterCount is equal to
   * one of the elements in the list.
   */
  private var alternativeInterpretations = IntArrayList()

  fun parse(): List<List<String>> {
    var bestGrid = currentGrid
    var bestFillerSellCount = Int.MAX_VALUE

    // Iterate over different interpretations of the delimiters located inside quoted segments and
    // find one that minimizes the number of added empty cells.
    //
    // Since the algorithm has exponential complexity with respect to the number of newline and tab
    // characters inside quoted segments, the number of attempts is limited by MAX_PARSE_ITERATIONS.
    // The space of possible interpretations is explored by first considering the straightforward
    // interpretation where none of the newline and tab characters located inside quoted segments
    // are treated as delimiters, then one of them is treated as a delimiter, then two, etc.
    for (i in 0 until MAX_PARSE_ITERATIONS) {
      parseOnce()
      if (fillerSellCount == 0) {
        return currentGrid
      }
      if (fillerSellCount < bestFillerSellCount) {
        bestFillerSellCount = fillerSellCount
        bestGrid = ArrayList(currentGrid)
      }
      maxQuotedDelimiterCount = maxQuotedDelimiterCount.coerceAtLeast(quotedDelimiterCount)
      if (!nextAlternative()) {
        break
      }
    }
    return bestGrid
  }

  private fun parseOnce(): List<List<String>> {
    currentGrid.clear()
    currentRow = mutableListOf()
    insideQuotedSegment = false
    openingQuoteAccepted = true
    segmentStart = 0
    offset = 0
    fillerSellCount = 0
    quotedDelimiterCount = 0

    while (offset < str.length) {
      when (val c = str[offset]) {
        '"' -> {
          if (insideQuotedSegment) {
            insideQuotedSegment = false
            openingQuoteAccepted = true
          }
          else if (openingQuoteAccepted) {
            insideQuotedSegment = true
          }
        }
        '\t', '\n' -> processDelimiter(c)
        else -> openingQuoteAccepted = false
      }
      offset++
    }
    if (offset > segmentStart) {
      addRow()
    }
    return currentGrid
  }

  private fun processDelimiter(delimiter: Char) {
    val wasInsideQuotedSegment = insideQuotedSegment
    if (!insideQuotedSegment || quotedDelimiterCount in alternativeInterpretations) {
      insideQuotedSegment = false
      if (offset - segmentStart >= 2 && str[segmentStart] == '"' && str[offset - 1] != '"' && str.containsDelimiter(segmentStart, offset)) {
        // The segment starts with quote but doesn't end with quote. The delimiters contained in it
        // should be starting new segments. Reparse the segment ignoring the opening quote.
        offset = segmentStart - 1
        openingQuoteAccepted = false
      }
      else {
        when (delimiter) {
          '\t' -> {
            addCell()
          }

          '\n' -> {
            addRow()
            currentRow = mutableListOf()
          }
        }
        segmentStart = offset + 1
        openingQuoteAccepted = true
      }
    }

    if (wasInsideQuotedSegment) {
      quotedDelimiterCount++
    }
  }

  private fun addRow() {
    addCell()
    if (currentRow.isNotEmpty()) {
      if (currentGrid.isNotEmpty() && currentRow.size < currentGrid.first().size) {
        fillerSellCount += currentGrid.first().size - currentRow.size
        currentRow.expandToSize(currentGrid.first().size, "")
      }
      currentGrid.add(currentRow)
    }
  }

  private fun addCell() {
    val segment = str.substring(segmentStart, offset)
    currentRow.add(segment.unquote())
    if (currentGrid.isNotEmpty() && currentRow.size > currentGrid.first().size) {
      appendColumn()
      fillerSellCount += currentGrid.size
    }
  }

  private fun appendColumn() {
    for (row in currentGrid) {
      row.add("")
    }
  }

  /**
   * Enumerates all possible contents of the alternativeInterpretations list by generating them
   * one at a time. Every produced alternativeInterpretations list contains elements in strictly
   * monotonically increasing order. All elements are kept below maxQuotedDelimiterCount. All
   * single-element possibilities are enumerated before two-element ones and so forth.
   *
   * Returns true if the next alternativeInterpretations content was produced, or false if all
   * possible combinations have been already exhausted.
   */
  private fun nextAlternative(): Boolean {
    with(alternativeInterpretations) {
      var i = size
      while (--i >= 0) {
        var a = incrementAndGet(i)
        var j = i
        while (++a <= maxQuotedDelimiterCount) {
          if (++j >= size || getInt(j) > a) {
            return true
          }
          set(j, a)
        }

        for (k in i until size) {
          set(k, 0)
        }
      }

      if (maxQuotedDelimiterCount >= 0 && size >= maxQuotedDelimiterCount) {
        return false
      }

      for (k in 0 until size) {
        set(k, k)
      }

      add(size)
      return true
    }
  }
}

private fun String.isQuoted() =
  length >= 2 && first() == '"' && last() == '"'

private fun String.unquote(): String =
  if (isQuoted()) substring(1, length - 1).replace("\"\"", "\"") else this

private fun String.containsDelimiter(start: Int, end: Int): Boolean {
  for (i in start until end) {
    when (get(i)) {
      '\t', '\n' -> return true
    }
  }
  return false
}

private fun <T> MutableList<T>.expandToSize(desiredSize: Int, value: T) {
  while (size < desiredSize) {
    add(value)
  }
}

/** Increments the given element and returns the new value. */
private fun IntArrayList.incrementAndGet(index: Int): Int {
  val value = getInt(index) + 1
  set(index, value)
  return value
}
