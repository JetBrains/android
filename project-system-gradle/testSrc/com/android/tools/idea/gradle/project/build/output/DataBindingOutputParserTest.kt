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
package com.android.tools.idea.gradle.project.build.output

import com.android.testutils.AssumeUtil
import com.google.common.truth.Truth.assertThat
import com.intellij.build.events.MessageEvent

import org.junit.Test

class DataBindingOutputParserTest : BuildOutputParserTest() {

  @Test
  fun handleMultipleJsonFormattedDataBindingParseErrors() {
    AssumeUtil.assumeNotWindows() // TODO (b/399625141): fix on windows
    parseOutput(
      parentEventId = "testId",
      gradleOutput = """
        Found data binding error(s):

        [databinding] {"msg":"Could not find identifier \u0027var1\u0027\n\nCheck that the identifier is spelled correctly, and that no \u003cimport\u003e or \u003cvariable\u003e tags are missing.","file":"/src/main/res/layout/activity_main1.xml","pos":[{"line0":36,"col0":28,"line1":36,"col1":32}]}
        [databinding] {"msg":"Could not find identifier \u0027var2\u0027\n\nCheck that the identifier is spelled correctly, and that no \u003cimport\u003e or \u003cvariable\u003e tags are missing.","file":"/src/main/res/layout/activity_main2.xml","pos":[{"line0":58,"col0":23,"line1":58,"col1":27}]}
      """.trimIndent(),
      expectedEvents = listOf(
        ExpectedEvent(
          message = "Could not find identifier 'var1'",
          isFileMessageEvent = true,
          isBuildIssueEvent = false,
          isDuplicateMessageAware = false,
          group = "Data Binding compiler",
          kind = MessageEvent.Kind.ERROR,
          parentId = "testId",
          filePosition = "/src/main/res/layout/activity_main1.xml:37:29-37:33",
          description = """
          Could not find identifier 'var1'

          Check that the identifier is spelled correctly, and that no <import> or <variable> tags are missing.
          """.trimIndent()),
        ExpectedEvent(
          message = "Could not find identifier 'var2'",
          isFileMessageEvent = true,
          isBuildIssueEvent = false,
          isDuplicateMessageAware = false,
          group = "Data Binding compiler",
          kind = MessageEvent.Kind.ERROR,
          parentId = "testId",
          filePosition = "/src/main/res/layout/activity_main2.xml:59:24-59:28",
          description = """
          Could not find identifier 'var2'

          Check that the identifier is spelled correctly, and that no <import> or <variable> tags are missing.
          """.trimIndent()))
    )
  }

  @Test
  fun handleMultipleLegacyFormattedDataBindingParseErrors() {
    AssumeUtil.assumeNotWindows() // TODO (b/399625141): fix on windows
    parseOutput(
      parentEventId = "testId",
      gradleOutput = """
        Found data binding errors.
          ****/ data binding error ****msg:Identifiers must have user defined types from the XML file. var1 is missing it file:/src/main/res/layout/activity_main1.xml loc:36:28 - 36:32 ****\ data binding error ****
          ****/ data binding error ****msg:Identifiers must have user defined types from the XML file. var2 is missing it file://src/main/res/layout/activity_main2.xml loc:58:23 - 58:27 ****\ data binding error ****
      """.trimIndent(),
      expectedEvents = listOf(
        ExpectedEvent(
          message = "Identifiers must have user defined types from the XML file. var1 is missing it",
          isFileMessageEvent = true,
          isBuildIssueEvent = false,
          isDuplicateMessageAware = false,
          group = "Data Binding compiler",
          kind = MessageEvent.Kind.ERROR,
          parentId = "testId",
          filePosition = "/src/main/res/layout/activity_main1.xml:37:29-37:33",
          description = """
          /src/main/res/layout/activity_main1.xml:37:29
          Identifiers must have user defined types from the XML file. var1 is missing it
          """.trimIndent()
        ),
        ExpectedEvent(
          message = "Identifiers must have user defined types from the XML file. var2 is missing it",
          isFileMessageEvent = true,
          isBuildIssueEvent = false,
          isDuplicateMessageAware = false,
          group = "Data Binding compiler",
          kind = MessageEvent.Kind.ERROR,
          parentId = "testId",
          filePosition = "/src/main/res/layout/activity_main2.xml:59:24-59:28",
          description = """
          /src/main/res/layout/activity_main2.xml:59:24
          Identifiers must have user defined types from the XML file. var2 is missing it
          """.trimIndent()
        )
      )
    )
  }
}

class DataBindingOutputParserInvalidInputHandlingTest {
  @Test
  fun recoverFromInvalidJson() {
    val badJson = "[databinding] {\"msg"
    val reader = TestBuildOutputInstantReader(badJson)
    val consumer = TestMessageEventConsumer()

    val parser = DataBindingOutputParser()

    assertThat(parser.parse(badJson, reader, consumer)).isFalse()
    assertThat(consumer.messageEvents).isEmpty()
  }

  @Test
  fun recoverFromInvalidLegacy() {
    run {
      val badLegacyContents = "****/ data binding error ****invalid contents****\\ data binding error ****"
      val reader = TestBuildOutputInstantReader(badLegacyContents)
      val consumer = TestMessageEventConsumer()

      val parser = DataBindingOutputParser()

      assertThat(parser.parse(badLegacyContents, reader, consumer)).isFalse()
      assertThat(consumer.messageEvents).isEmpty()
    }

    run {
      val badLegacyLocation = "****/ data binding error ****msg:sample file:/sample loc:not-a-number ****\\ data binding error ****"
      val reader = TestBuildOutputInstantReader(badLegacyLocation)
      val consumer = TestMessageEventConsumer()

      val parser = DataBindingOutputParser()

      assertThat(parser.parse(badLegacyLocation, reader, consumer)).isFalse()
      assertThat(consumer.messageEvents).isEmpty()
    }
  }
}
