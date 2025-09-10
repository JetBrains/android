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
package com.android.screenshottest.util

import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.xml.sax.SAXParseException

/**
 * Unit tests for the XML parsing logic in [TestResultParser].
 * These tests do not require any IntelliJ project setup.
 */
class TestResultParserTest {

  private lateinit var dBuilder: DocumentBuilder

  @Before
  fun setup() {
    val dbFactory = DocumentBuilderFactory.newInstance()
    dBuilder = dbFactory.newDocumentBuilder()
  }

  @Test
  fun parseXmlStream_singleValidTestcase() {
    val xmlContent = """
      <?xml version='1.0' encoding='UTF-8'?>
      <testsuite name="com.example.MyTest" tests="1" failures="0" errors="0">
        <testcase name="myPreview" classname="com.example.MyTest" time="1.0">
          <properties>
            <property name="PreviewScreenshot.newImagePath" value="/path/to/image1.png"/>
          </properties>
        </testcase>
      </testsuite>
    """.trimIndent()

    val result = TestResultParser.parseXmlStream(xmlContent.byteInputStream(), dBuilder)

    assertEquals(1, result.size)
    assertEquals("/path/to/image1.png", result["MyTest.myPreview"])
  }

  @Test
  fun parseXmlStream_multipleTestcases() {
    val xmlContent = """
      <?xml version='1.0' encoding='UTF-8'?>
      <testsuite name="com.example.MyTest" tests="2" failures="0" errors="0">
        <testcase name="myPreview1" classname="com.example.MyTest" time="1.0">
          <properties>
            <property name="PreviewScreenshot.newImagePath" value="/path/to/image1.png"/>
          </properties>
        </testcase>
        <testcase name="myPreview2" classname="com.example.MyTest" time="1.0">
          <properties>
            <property name="PreviewScreenshot.newImagePath" value="/path/to/image2.png"/>
          </properties>
        </testcase>
      </testsuite>
    """.trimIndent()

    val result = TestResultParser.parseXmlStream(xmlContent.byteInputStream(), dBuilder)

    assertEquals(2, result.size)
    assertEquals("/path/to/image1.png", result["MyTest.myPreview1"])
    assertEquals("/path/to/image2.png", result["MyTest.myPreview2"])
  }

  @Test
  fun parseXmlStream_topLevelFunctionTestcase() {
    val xmlContent = """
      <?xml version='1.0' encoding='UTF-8'?>
      <testsuite name="com.example.TopLevelFileKt" tests="1" failures="0" errors="0">
        <testcase name="myTopLevelPreview" classname="com.example.TopLevelFileKt" time="1.0">
          <properties>
            <property name="PreviewScreenshot.newImagePath" value="/path/to/top_level.png"/>
          </properties>
        </testcase>
      </testsuite>
    """.trimIndent()

    val result = TestResultParser.parseXmlStream(xmlContent.byteInputStream(), dBuilder)

    assertEquals(1, result.size)
    assertEquals("/path/to/top_level.png", result["TopLevelFileKt.myTopLevelPreview"])
  }

  @Test
  fun parseXmlStream_testcaseWithFailure() {
    val xmlContent = """
      <?xml version='1.0' encoding='UTF-8'?>
      <testsuite name="com.example.MyTest" tests="1" failures="1" errors="0">
        <testcase name="failingPreview" classname="com.example.MyTest" time="1.0">
          <properties>
            <property name="PreviewScreenshot.newImagePath" value="/path/to/failing.png"/>
          </properties>
          <failure>Something went wrong</failure>
        </testcase>
      </testsuite>
    """.trimIndent()

    val result = TestResultParser.parseXmlStream(xmlContent.byteInputStream(), dBuilder)

    // The parser should still extract the path even if the test failed.
    assertEquals(1, result.size)
    assertEquals("/path/to/failing.png", result["MyTest.failingPreview"])
  }

  @Test
  fun parseXmlStream_testcaseWithError() {
    val xmlContent = """
      <?xml version='1.0' encoding='UTF-8'?>
      <testsuite name="com.example.MyTest" tests="1" failures="0" errors="1">
        <testcase name="errorPreview" classname="com.example.MyTest" time="1.0">
          <properties>
            <property name="PreviewScreenshot.newImagePath" value="/path/to/error.png"/>
          </properties>
          <error>Something went very wrong</error>
        </testcase>
      </testsuite>
    """.trimIndent()

    val result = TestResultParser.parseXmlStream(xmlContent.byteInputStream(), dBuilder)

    // The parser should still extract the path even if the test has an error.
    assertEquals(1, result.size)
    assertEquals("/path/to/error.png", result["MyTest.errorPreview"])
  }

  @Test
  fun parseXmlStream_skippedTestcase() {
    val xmlContent = """
      <?xml version='1.0' encoding='UTF-8'?>
      <testsuite name="com.example.MyTest" tests="1" failures="0" errors="0">
        <testcase name="skippedPreview" classname="com.example.MyTest" time="0.0">
          <skipped/>
        </testcase>
      </testsuite>
    """.trimIndent()

    val result = TestResultParser.parseXmlStream(xmlContent.byteInputStream(), dBuilder)

    // Skipped tests should be ignored.
    assertTrue(result.isEmpty())
  }

  @Test
  fun parseXmlStream_emptyImagePathProperty() {
    val xmlContent = """
      <?xml version='1.0' encoding='UTF-8'?>
      <testsuite name="com.example.MyTest" tests="1" failures="0" errors="0">
        <testcase name="previewWithEmptyPath" classname="com.example.MyTest" time="1.0">
          <properties>
            <property name="PreviewScreenshot.newImagePath" value=""/>
          </properties>
        </testcase>
      </testsuite>
    """.trimIndent()

    val result = TestResultParser.parseXmlStream(xmlContent.byteInputStream(), dBuilder)

    // An empty path is not valid, so it should be ignored.
    assertTrue(result.isEmpty())
  }

  @Test
  fun parseXmlStream_missingProperty() {
    val xmlContent = """
      <?xml version='1.0' encoding='UTF-8'?>
      <testsuite name="com.example.MyTest" tests="1" failures="0" errors="0">
        <testcase name="myPreview" classname="com.example.MyTest" time="1.0">
          <properties>
            <property name="some.other.property" value="some_value"/>
          </properties>
        </testcase>
      </testsuite>
    """.trimIndent()

    val result = TestResultParser.parseXmlStream(xmlContent.byteInputStream(), dBuilder)

    assertTrue(result.isEmpty())
  }

  @Test
  fun parseXmlStream_noPropertiesTag() {
    val xmlContent = """
      <?xml version='1.0' encoding='UTF-8'?>
      <testsuite name="com.example.MyTest" tests="1" failures="0" errors="0">
        <testcase name="myPreview" classname="com.example.MyTest" time="1.0" />
      </testsuite>
    """.trimIndent()

    val result = TestResultParser.parseXmlStream(xmlContent.byteInputStream(), dBuilder)

    assertTrue(result.isEmpty())
  }

  @Test
  fun parseXmlStream_parameterizedTestName() {
    val xmlContent = """
      <?xml version='1.0' encoding='UTF-8'?>
      <testsuite name="com.example.MyTest" tests="1" failures="0" errors="0">
        <testcase name="myPreview[param=value]" classname="com.example.MyTest" time="1.0">
          <properties>
            <property name="PreviewScreenshot.newImagePath" value="/path/to/parameterized.png"/>
          </properties>
        </testcase>
      </testsuite>
    """.trimIndent()

    val result = TestResultParser.parseXmlStream(xmlContent.byteInputStream(), dBuilder)

    assertEquals(1, result.size)
    assertEquals("/path/to/parameterized.png", result["MyTest.myPreview[param=value]"])
  }

  @Test
  fun parseXmlStream_mixedValidAndInvalidTestcases() {
    val xmlContent = """
      <?xml version='1.0' encoding='UTF-8'?>
      <testsuite name="com.example.MyTest" tests="2" failures="0" errors="0">
        <testcase name="validPreview" classname="com.example.MyTest" time="1.0">
          <properties>
            <property name="PreviewScreenshot.newImagePath" value="/path/to/valid.png"/>
          </properties>
        </testcase>
        <testcase name="invalidPreview" classname="com.example.MyTest" time="1.0">
          <properties>
            <property name="some.other.property" value="some_value"/>
          </properties>
        </testcase>
      </testsuite>
    """.trimIndent()

    val result = TestResultParser.parseXmlStream(xmlContent.byteInputStream(), dBuilder)

    assertEquals(1, result.size)
    assertTrue("Result should contain the valid preview", result.containsKey("MyTest.validPreview"))
    assertFalse("Result should not contain the invalid preview", result.containsKey("MyTest.invalidPreview"))
    assertEquals("/path/to/valid.png", result["MyTest.validPreview"])
  }

  @Test
  fun parseXmlStream_multipleTestSuitesInOneFile() {
    val xmlContent = """
      <?xml version='1.0' encoding='UTF-8'?>
      <testsuites>
        <testsuite name="com.example.MyTest1" tests="1" failures="0" errors="0">
          <testcase name="preview1" classname="com.example.MyTest1" time="1.0">
            <properties>
              <property name="PreviewScreenshot.newImagePath" value="/path/to/image1.png"/>
            </properties>
          </testcase>
        </testsuite>
        <testsuite name="com.example.MyTest2" tests="1" failures="0" errors="0">
          <testcase name="preview2" classname="com.example.MyTest2" time="1.0">
            <properties>
              <property name="PreviewScreenshot.newImagePath" value="/path/to/image2.png"/>
            </properties>
          </testcase>
        </testsuite>
      </testsuites>
    """.trimIndent()

    val result = TestResultParser.parseXmlStream(xmlContent.byteInputStream(), dBuilder)

    assertEquals(2, result.size)
    assertEquals("/path/to/image1.png", result["MyTest1.preview1"])
    assertEquals("/path/to/image2.png", result["MyTest2.preview2"])
  }

  @Test(expected = SAXParseException::class)
  fun parseXmlStream_emptyFile() {
    TestResultParser.parseXmlStream("".byteInputStream(), dBuilder)
  }

  @Test(expected = SAXParseException::class)
  fun parseXmlStream_malformedFile() {
    TestResultParser.parseXmlStream("<testsuite><testcase".byteInputStream(), dBuilder)
  }
}