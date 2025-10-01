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

import com.android.testutils.TestUtils
import java.io.BufferedReader
import java.io.FileReader
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.After

class JVMReportParserTest {
  private lateinit var parser: JVMReportParser
  private lateinit var testLogReader: BufferedReader

  @Rule
  @JvmField
  val tmpFolder = TemporaryFolder()

  @Before
  fun setup() {
    val testReportRelativePath = "tools/adt/idea/android/testData/diagnostics/JVMReportFullSanitized.log"
    val testReportPath: Path = TestUtils.resolveWorkspacePath(testReportRelativePath)
    val testLogFile = testReportPath.toFile()
    testLogReader = BufferedReader(FileReader(testLogFile))
    parser = JVMReportParser(testLogReader)
  }

  @After
  fun cleanup() {
    testLogReader.close()
  }

  @Test
  fun testReadNextSectionLineAtBeginningOfFile() {
    val e = assertFailsWith<IllegalStateException> {
      parser.readNextSectionLine()
    }
    assertEquals("Not started reading the file yet", e.message)
  }

  @Test
  fun testReadSectionLineInSection() {
    parser.goToNextSection()
    assertEquals("#", parser.readNextSectionLine())
    assertEquals("# A fatal error has been detected by the Java Runtime Environment:", parser.readNextSectionLine())
  }

  @Test
  fun testReadSectionLineAtEndOfSection() {
    parser.goToNextSection()
    repeat(17) {
      assertFalse(parser.isEndOfSection())
      parser.readNextSectionLine()
    }
    assertTrue(parser.isEndOfSection())
    assertNull(parser.readNextSectionLine())
  }

  @Test
  fun testIsEOF() {
    // each repeat will loop all lines in the specific section
    parser.goToNextSection()
    repeat(17) {
      assertFalse(parser.isEOF())
      parser.readNextSectionLine()
    }
    parser.goToNextSection()
    repeat(8) {
      assertFalse(parser.isEOF())
      parser.readNextSectionLine()
    }
    parser.goToNextSection()
    repeat(108) {
      assertFalse(parser.isEOF())
      parser.readNextSectionLine()
    }
    parser.goToNextSection()
    repeat(362) {
      assertFalse(parser.isEOF())
      parser.readNextSectionLine()
    }
    parser.goToNextSection()
    repeat(116) {
      assertFalse(parser.isEOF())
      parser.readNextSectionLine()
    }
    parser.goToNextSection()
    repeat(6) {
      assertFalse(parser.isEOF())
      parser.readNextSectionLine()
    }
    assertTrue(parser.isEOF())
  }

  @Test
  fun testIsEndOfSection() {
    val e = assertFailsWith<IllegalStateException> {
      parser.isEndOfSection()
    }
    assertEquals("Not started reading the file yet", e.message)
    parser.goToNextSection()

    // at starting of a section
    assertFalse(parser.isEndOfSection())

    // loop through the whole section
    repeat(17) {
      assertFalse(parser.isEndOfSection())
      parser.readNextSectionLine()
    }
    assertTrue(parser.isEndOfSection())

    // when reached EOF, isEndOfSection() should return true
    repeat(6) {
      parser.goToNextSection()
    }
    assertTrue(parser.isEOF()) // reached EOF

    assertTrue(parser.isEndOfSection())
  }

  @Test
  fun testIsEndOfSubsection() {
    val e = assertFailsWith<IllegalStateException> {
      parser.isEndOfSubsection()
    }
    assertEquals("Not started reading the file yet", e.message)

    // set up to a clearer start of a subsection
    parser.goToNextSection()
    parser.goToNextSection()
    parser.goToNextSection()
    repeat(10) {
      parser.readNextSectionLine()
    }

    // reached start of a subsection, start test
    repeat(7) {
      assertFalse(parser.isEndOfSubsection())
      parser.readNextSectionLine()
    }
    assertTrue(parser.isEndOfSubsection())

    // set parser to an end of section, isEndOfSubsection() should also return true
    repeat(91) {
      parser.readNextSectionLine()
    }
    assertTrue(parser.isEndOfSection()) // reached end of section

    assertTrue(parser.isEndOfSubsection())

    // set parser to EOF, isEndOfSubsection() should also return true
    repeat(4) {
      parser.goToNextSection()
    }
    assertTrue(parser.isEOF()) // reached EOF

    assertTrue(parser.isEndOfSubsection())
  }

  @Test
  fun testGoToNextSectionAtStartOrSectionBody() {
    // set to the start of a section
    parser.goToNextSection()

    // at the start of a section
    parser.goToNextSection()
    assertEquals("---------------  S U M M A R Y ------------", parser.readNextSectionLine())

    // set to middle of a section
    repeat(3) {
      assertFalse(parser.isEndOfSection())
      parser.readNextSectionLine()
    }
    assertFalse(parser.isEndOfSection())

    // in  the middle of a section
    parser.goToNextSection()
    assertEquals("---------------  T H R E A D  ---------------", parser.readNextSectionLine())
  }

  @Test
  fun testGoToNextSectionAtEndOfSection() {
    // set to the end of a section
    parser.goToNextSection()
    repeat(17) {
      assertFalse(parser.isEndOfSection())
      parser.readNextSectionLine()
    }
    assertTrue(parser.isEndOfSection())

    // at end of a section
    parser.goToNextSection()
    assertEquals("---------------  S U M M A R Y ------------", parser.readNextSectionLine())
  }

  @Test
  fun testReadLineAndGoToNextSectionAtEOF() {
    // set to EOF
    repeat(7) {
      parser.goToNextSection()
    }

    val e1 = assertFailsWith<IllegalStateException> {
      parser.readNextSectionLine()
    }
    assertEquals("Reached EOF, cannot proceed further", e1.message)

    val e2 = assertFailsWith<IllegalStateException> {
      parser.goToNextSection()
    }
    assertEquals("Reached EOF, cannot proceed further", e2.message)
  }

  @Test
  fun testGetCurrentSectionTypeAtBeginningOfFile() {
    val e = assertFailsWith<IllegalStateException> {
      parser.getCurrentSectionType()
    }
    assertEquals("Not started reading the file yet", e.message)
  }

  @Test
  fun testGetCurrentSectionTypeThroughSectionBody() {
    parser.goToNextSection()
    repeat(17) {
      assertEquals(SectionType.Header, parser.getCurrentSectionType())
      parser.readNextSectionLine()
    }

    parser.goToNextSection()
    repeat(8) {
      assertEquals(SectionType.Summary, parser.getCurrentSectionType())
      parser.readNextSectionLine()
    }
  }

  @Test
  fun testGetCurrentSectionTypeAtEndOfSection() {
    // set to EOF
    parser.goToNextSection()
    repeat(17) {
      assertEquals(SectionType.Header, parser.getCurrentSectionType())
      parser.readNextSectionLine()
    }
    assertTrue(parser.isEndOfSection()) // reached end of section

    assertEquals(SectionType.Header, parser.getCurrentSectionType())
  }

  @Test
  fun testGetCurrentSectionTypeAtEOF() {
    // set to EOF
    repeat(7) {
      parser.goToNextSection()
    }
    assertTrue(parser.isEOF())// reached EOF

    assertEquals(SectionType.EOF, parser.getCurrentSectionType())
  }

  @Test
  fun testParserForEmptyFile() {
    val emptyFile = tmpFolder.newFile()
    val emptyReader = BufferedReader(FileReader(emptyFile))
    val parser = JVMReportParser(emptyReader)

    val e1 = assertFailsWith<IllegalStateException> {
      parser.readNextSectionLine()
    }
    assertEquals("Not started reading the file yet", e1.message)

    parser.goToNextSection()
    assertEquals(SectionType.EOF, parser.getCurrentSectionType())
    val e2 = assertFailsWith<IllegalStateException> {
      parser.readNextSectionLine()
    }
    assertEquals("Reached EOF, cannot proceed further", e2.message)
    assertTrue(parser.isEOF())

    emptyReader.close()
  }

  @Test
  fun testParserForTwoSectionTitlesBackToBack() {
    val file = tmpFolder.newFile()
    val logContent = "---------------  S U M M A R Y ------------\n" +
                     "---------------  T H R E A D  ---------------\n" +
                     "---------------  P R O C E S S  ---------------\n"
    file.writeText(logContent)
    val testReader = BufferedReader(FileReader(file))
    val parser = JVMReportParser(testReader)
    parser.goToNextSection()

    assertEquals(SectionType.Summary, parser.getCurrentSectionType())
    assertEquals("---------------  S U M M A R Y ------------", parser.readNextSectionLine())
    assertNull(parser.readNextSectionLine())

    parser.goToNextSection()
    assertEquals(SectionType.Thread, parser.getCurrentSectionType())
    assertEquals("---------------  T H R E A D  ---------------", parser.readNextSectionLine())
    assertNull(parser.readNextSectionLine())

    parser.goToNextSection()
    assertEquals(SectionType.Process, parser.getCurrentSectionType())
    assertEquals("---------------  P R O C E S S  ---------------", parser.readNextSectionLine())
    parser.readNextSectionLine()
    assertTrue(parser.isEOF())

    testReader.close()
  }

  @Test
  fun testParserForUnusualSectionOrder() {
    val testReportRelativePath = "tools/adt/idea/android/testData/diagnostics/JVMReportUnusualSectionOrder.log"
    val testReportPath: Path = TestUtils.resolveWorkspacePath(testReportRelativePath)
    val testFile = testReportPath.toFile()
    val testReader = BufferedReader(FileReader(testFile))
    val parser = JVMReportParser(testReader)
    parser.goToNextSection()

    assertEquals(SectionType.Header, parser.getCurrentSectionType())
    assertEquals("#", parser.readNextSectionLine())

    parser.goToNextSection()
    assertEquals(SectionType.Thread, parser.getCurrentSectionType())

    parser.goToNextSection()
    assertEquals(SectionType.Summary, parser.getCurrentSectionType())

    parser.goToNextSection()
    assertEquals(SectionType.System, parser.getCurrentSectionType())

    parser.goToNextSection()
    assertEquals(SectionType.Unknown, parser.getCurrentSectionType())

    parser.goToNextSection()
    assertEquals(SectionType.Process, parser.getCurrentSectionType())

    parser.goToNextSection()
    assertEquals(SectionType.EOF, parser.getCurrentSectionType())

    testReader.close()
  }

  @Test
  fun testParserForSameTypeSectionRepeated() {
    val testReportRelativePath = "tools/adt/idea/android/testData/diagnostics/JVMReportSameTypeSectionRepeated.log"
    val testReportPath: Path = TestUtils.resolveWorkspacePath(testReportRelativePath)
    val testFile = testReportPath.toFile()
    val testReader = BufferedReader(FileReader(testFile))
    val parser = JVMReportParser(testReader)
    parser.goToNextSection()

    assertEquals(SectionType.Header, parser.getCurrentSectionType())

    parser.goToNextSection()
    assertEquals(SectionType.Summary, parser.getCurrentSectionType())
    parser.readNextSectionLine()
    assertEquals("Summary 1", parser.readNextSectionLine())
    repeat(5) {
      parser.readNextSectionLine()
    }
    assertNull(parser.readNextSectionLine())

    parser.goToNextSection()
    assertEquals(SectionType.Summary, parser.getCurrentSectionType())
    parser.readNextSectionLine()
    assertEquals("Summary 2", parser.readNextSectionLine())
    repeat(6) {
      parser.readNextSectionLine()
    }
    assertNull(parser.readNextSectionLine())

    parser.goToNextSection()
    assertEquals(SectionType.Summary, parser.getCurrentSectionType())
    parser.readNextSectionLine()
    assertEquals("Summary 3", parser.readNextSectionLine())

    parser.goToNextSection()
    assertEquals(SectionType.EOF, parser.getCurrentSectionType())

    testReader.close()
  }

  @Test
  fun testParserWrongSectionTitleSameSection() {
    val file = tmpFolder.newFile()
    val logContent = "-----------  S U M M A R Y\n" +
                     "-----  T H R e a d  ---------------\n" +
                     "---------------  process  ---------------\n" +
                     "---------------  sy    st  e m\n"
    file.writeText(logContent)
    val testReader = BufferedReader(FileReader(file))
    val parser = JVMReportParser(testReader)
    parser.goToNextSection()

    assertEquals(SectionType.Summary, parser.getCurrentSectionType())
    assertEquals("-----------  S U M M A R Y", parser.readNextSectionLine())

    parser.goToNextSection()
    assertEquals(SectionType.Thread, parser.getCurrentSectionType())
    assertEquals("-----  T H R e a d  ---------------", parser.readNextSectionLine())

    parser.goToNextSection()
    assertEquals(SectionType.Process, parser.getCurrentSectionType())
    assertEquals("---------------  process  ---------------", parser.readNextSectionLine())

    parser.goToNextSection()
    assertEquals(SectionType.System, parser.getCurrentSectionType())
    assertEquals("---------------  sy    st  e m", parser.readNextSectionLine())

    testReader.close()
  }

  @Test
  fun testParserWrongSectionTitleDifferentSection() {
    val file = tmpFolder.newFile()
    val logContent = "--- S U M M A R Y ------------\n" +
                     "---------------  T H R E A D :  ---------------\n"
    file.writeText(logContent)
    val testReader = BufferedReader(FileReader(file))
    val parser = JVMReportParser(testReader)
    parser.goToNextSection()

    assertEquals(SectionType.Summary, parser.getCurrentSectionType())
    assertEquals("--- S U M M A R Y ------------", parser.readNextSectionLine())

    parser.goToNextSection()
    assertEquals(SectionType.Unknown, parser.getCurrentSectionType())
    assertEquals("---------------  T H R E A D :  ---------------", parser.readNextSectionLine())

    testReader.close()
  }

  @Test
  fun testParserWrongSectionTitleNoSection() {
    val file = tmpFolder.newFile()
    val logContent = "#\n" +
                     "---  T H R E A D  ---------------\n"
    file.writeText(logContent)
    val testReader = BufferedReader(FileReader(file))
    val parser = JVMReportParser(testReader)
    parser.goToNextSection()

    assertEquals(SectionType.Header, parser.getCurrentSectionType())
    assertEquals("#", parser.readNextSectionLine())
    assert(!parser.isEndOfSection())
    assertEquals("---  T H R E A D  ---------------", parser.readNextSectionLine())
    assertEquals(SectionType.Header, parser.getCurrentSectionType())

    assertFalse(parser.isEndOfSection())
    parser.readNextSectionLine()
    assertTrue(parser.isEndOfSection())

    testReader.close()
  }
}
