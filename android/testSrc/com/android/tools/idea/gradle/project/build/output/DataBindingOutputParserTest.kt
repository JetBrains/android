/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.google.common.truth.Truth.assertThat

import com.intellij.build.events.impl.FileMessageEventImpl
import org.junit.Test

class DataBindingOutputParserTest {

  @Test
  fun handleMultipleDataBindingParseErrors() {
    val input = "Found data binding error(s):\n" +
                "\n" +
                "[databinding] {\"msg\":\"Could not find identifier \\u0027var1\\u0027\\n\\nCheck that the identifier is spelled correctly, and that no \\u003cimport\\u003e or \\u003cvariable\\u003e tags are missing.\",\"file\":\"/usr/local/google/home/davidherman/Code/studio-master-dev/tools/data-binding/integration-tests/AppWithDataBindingInTests/app/src/main/res/layout/activity_main1.xml\",\"pos\":[{\"line0\":36,\"col0\":28,\"line1\":36,\"col1\":32}]}\n" +
                "[databinding] {\"msg\":\"Could not find identifier \\u0027var2\\u0027\\n\\nCheck that the identifier is spelled correctly, and that no \\u003cimport\\u003e or \\u003cvariable\\u003e tags are missing.\",\"file\":\"/usr/local/google/home/davidherman/Code/studio-master-dev/tools/data-binding/integration-tests/AppWithDataBindingInTests/app/src/main/res/layout/activity_main2.xml\",\"pos\":[{\"line0\":58,\"col0\":23,\"line1\":58,\"col1\":27}]}\n"

    val reader = TestBuildOutputInstantReader(input)
    val consumer = TestMessageEventConsumer()

    val parser = DataBindingOutputParser()

    while (true) {
      val line = reader.readLine() ?: break
      assertThat(parser.parse(line, reader, consumer)).isTrue()
    }

    assertThat(consumer.messageEvents).hasSize(2)

    run {
      val firstError = consumer.messageEvents[0] as FileMessageEventImpl
      assertThat(firstError.result.details).startsWith("Could not find identifier 'var1'\n\nCheck that the identifier")

      val filePos = firstError.filePosition
      assertThat(filePos.startLine).isEqualTo(36)
      assertThat(filePos.startColumn).isEqualTo(28)
      assertThat(filePos.endLine).isEqualTo(36)
      assertThat(filePos.endColumn).isEqualTo(32)
      assertThat(filePos.file.path).contains("activity_main1.xml")
    }

    run {
      val secondError = consumer.messageEvents[1] as FileMessageEventImpl
      assertThat(secondError.result.details).startsWith("Could not find identifier 'var2'\n\nCheck that the identifier")

      val filePos = secondError.filePosition
      assertThat(filePos.startLine).isEqualTo(58)
      assertThat(filePos.startColumn).isEqualTo(23)
      assertThat(filePos.endLine).isEqualTo(58)
      assertThat(filePos.endColumn).isEqualTo(27)
      assertThat(filePos.file.path).contains("activity_main2.xml")
    }
  }

  @Test
  fun rejectUnrelatedInput() {
    val input = "This is not data binding input"
    val reader = TestBuildOutputInstantReader(input)
    val consumer = TestMessageEventConsumer()

    val parser = DataBindingOutputParser()

    assertThat(parser.parse(input, reader, consumer)).isFalse()
  }

  @Test
  fun recoverFromInvalidJson() {
    val badJson = "[databinding] {\"msg"
    val reader = TestBuildOutputInstantReader(badJson)
    val consumer = TestMessageEventConsumer()

    val parser = DataBindingOutputParser()

    assertThat(parser.parse(badJson, reader, consumer)).isFalse()
  }

}
