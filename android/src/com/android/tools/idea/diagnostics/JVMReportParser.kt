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
import kotlin.text.startsWith

class JVMReportParser(private val reader: BufferedReader) {
  private var bufferedLine: String? = null
  private var currentSectionType = SectionType.BeginningOfFile
  private var currentSectionState = SectionState.BeginningOfFile

  /**
   * Returns the current section type.
   * While inside a certain section, should return the same section type.
   * When not inside a section, e.g. at beginning of the file, will throw NullPointerException.
   */
  fun getCurrentSectionType(): SectionType {
    if (currentSectionType == SectionType.BeginningOfFile) {
      throw IllegalStateException("Not started reading the file yet")
    }
    return currentSectionType
  }

  private fun extractSectionTypeFromCurrentLine(): SectionType {
    val line = bufferedLine
    if (line == null) {
      return SectionType.EOF
    }
    if (line.startsWith("#")) {
      return SectionType.Header
    }
    return when (line.replace("-", "").replace(" ", "").uppercase()) {
      "SUMMARY" -> SectionType.Summary
      "THREAD" -> SectionType.Thread
      "PROCESS" -> SectionType.Process
      "SYSTEM" -> SectionType.System
      else -> SectionType.Unknown
    }
  }

  fun goToNextSection() {
    when (currentSectionState) {
      SectionState.SectionBody, SectionState.Start -> {
        while (!isEndOfSection()) {
          readNextSectionLine()
        }
        updateSectionInfoToNewSection()
      }

      SectionState.EndOfSection -> {
        updateSectionInfoToNewSection()
      }

      SectionState.EOF -> {
        throw IllegalStateException("Reached EOF, cannot proceed further")
      }

      SectionState.BeginningOfFile -> {
        val line = reader.readLine()
        bufferedLine = line
        if (line == null) {
          updateSectionInfoToEOF()
          return
        }
        updateSectionInfoToNewSection()
      }
    }
  }

  private fun updateSectionInfoToNewSection() {
    currentSectionState = if (isEOF()) SectionState.EOF else SectionState.Start
    currentSectionType = extractSectionTypeFromCurrentLine()
  }

  private fun updateSectionInfoToEOF() {
    currentSectionState = SectionState.EOF
    currentSectionType = SectionType.EOF
  }

  fun readNextSectionLine(): String? {
    when (currentSectionState) {
      // the reason why we are still having start is that when our buffered line is a section title,
      // we need to know whether we are at the beginning of a new section or we reached another section
      SectionState.Start -> {
        val line = bufferedLine
        if (line == null) {
          updateSectionInfoToEOF()
          return null
        }
        currentSectionState = SectionState.SectionBody
        bufferedLine = reader.readLine()
        return line
      }

      SectionState.SectionBody -> {
        val line = bufferedLine
        if (line == null) {
          updateSectionInfoToEOF()
          return null
        }
        if (line.startsWith("-----")) {
          currentSectionState = SectionState.EndOfSection
          return null
        }
        bufferedLine = reader.readLine()
        return line
      }

      SectionState.EndOfSection -> {
        return null
      }

      SectionState.EOF -> {
        throw IllegalStateException("Reached EOF, cannot proceed further")
      }

      SectionState.BeginningOfFile -> {
        throw IllegalStateException("Not started reading the file yet")
      }
    }
  }

  fun isEOF(): Boolean {
    return currentSectionState == SectionState.EOF
  }

  fun isEndOfSection(): Boolean {
    return when (currentSectionState) {
      SectionState.EndOfSection, SectionState.EOF -> true
      SectionState.Start, SectionState.SectionBody -> false
      SectionState.BeginningOfFile -> throw IllegalStateException("Not started reading the file yet")
    }
  }

  fun isEndOfSubsection(): Boolean {
    when (currentSectionState) {
      SectionState.EndOfSection, SectionState.EOF -> return true
      SectionState.Start, SectionState.SectionBody -> {
        return bufferedLine.isNullOrEmpty()
      }

      SectionState.BeginningOfFile -> throw IllegalStateException("Not started reading the file yet")
    }
  }
}