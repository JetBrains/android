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
package com.android.tools.idea.diagnostics

import java.io.BufferedReader
import java.io.File
import java.io.FileReader

object JVMReportSanitizer {

  @JvmStatic
  fun sanitize(report: File): String {
    val builder = SanitizedBuilder()
    BufferedReader(FileReader(report)).use { reader ->
      val parser = JVMReportParser(reader)
      while (!parser.isEOF()) {
        parser.goToNextSection()
        val sectionType = parser.getCurrentSectionType()
        when (sectionType) {
          SectionType.BeginningOfFile -> throw IllegalStateException("at beginning of file, need to call `goToNextSection()` first")
          SectionType.Header -> sanitizeHeader(parser, builder)
          SectionType.Summary -> sanitizeSummary(parser, builder)
          SectionType.Thread -> sanitizeThread(parser, builder)
          SectionType.Process -> sanitizeProcess(parser, builder)
          SectionType.System -> sanitizeSystem(parser, builder)
          SectionType.Unknown -> sanitizeUnknown(parser, builder)
          SectionType.EOF -> break
        }
      }
    }
    return builder.toString()
  }

  private fun sanitizeHeader(parser: JVMReportParser, builder: SanitizedBuilder) {
    while (!parser.isEndOfSection()) {
      val line = parser.readNextSectionLine()
      if (line == null) {
        return
      }
      builder.sanitizeUntilEOL(line)
    }
  }

  private fun sanitizeSummary(parser: JVMReportParser, builder: SanitizedBuilder) {
    while (!parser.isEndOfSection()) {
      val line = parser.readNextSectionLine()
      if (line == null) {
        return
      }
      builder.sanitizeUntilEOL(line)
    }
  }

  private fun sanitizeThread(parser: JVMReportParser, builder: SanitizedBuilder) {
    while (!parser.isEndOfSection()) {
      val line = parser.readNextSectionLine()
      if (line == null) {
        return
      }
      builder.sanitizeUntilEOL(line)
    }
  }

  private fun sanitizeProcess(parser: JVMReportParser, builder: SanitizedBuilder) {
    while (!parser.isEndOfSection()) {
      val line = parser.readNextSectionLine()
      if (line == null) {
        return
      }
      if (line.startsWith("Heap Regions: ") || line.startsWith("Environment Variables:")) {
        skipSubsection(line, parser, builder)
      }
      else {
        builder.sanitizeUntilEOL(line)
      }
    }
  }

  private fun sanitizeSystem(parser: JVMReportParser, builder: SanitizedBuilder) {
    while (!parser.isEndOfSection()) {
      val line = parser.readNextSectionLine()
      if (line == null) {
        return
      }
      builder.sanitizeUntilEOL(line)
    }
  }

  private fun sanitizeUnknown(parser: JVMReportParser, builder: SanitizedBuilder) {
    while (!parser.isEndOfSection()) {
      val line = parser.readNextSectionLine()
      if (line == null) {
        return
      }
      builder.sanitizeUntilEOL(line)
    }
  }

  private fun skipSubsection(line: String, parser: JVMReportParser, builder: SanitizedBuilder) {
    var lineCount = 0
    while (!parser.isEndOfSubsection()) {
      lineCount++
      parser.readNextSectionLine()
    }
    builder.sanitizeUntilEOL("$line\n<Skipped $lineCount lines>")
  }
}