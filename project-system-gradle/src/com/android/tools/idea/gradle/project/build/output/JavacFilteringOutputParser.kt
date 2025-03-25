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
package com.android.tools.idea.gradle.project.build.output

import com.intellij.build.events.BuildEvent
import com.intellij.build.output.BuildOutputInstantReader
import com.intellij.build.output.BuildOutputParser
import com.intellij.build.output.JavacOutputParser
import java.util.function.Consumer

/**
 * This parser delegates work to original [JavacOutputParser] but allows to filter some of the generated messages.
 */
class JavacFilteringOutputParser : BuildOutputParser {
  private val myJavacParser = JavacOutputParser()

  private var buildFailedWithExceptionLineSeen = false

  override fun parse(line: String, reader: BuildOutputInstantReader, messageConsumer: Consumer<in BuildEvent>): Boolean {
    if (line.startsWith(BuildOutputParserUtils.BUILD_COMPLETED_WITH_FAILURES_LINE)
        || line.startsWith(BuildOutputParserUtils.BUILD_FAILED_WITH_EXCEPTION_LINE)) {
      buildFailedWithExceptionLineSeen = true
    }
    val wrappedConsumer = Consumer<BuildEvent> { event ->
      if (buildFailedWithExceptionLineSeen && JavaLanguageLevelDeprecationOutputParser.notSupportedMessagePattern.matcher(event.message).matches()) {
        return@Consumer
      }
      messageConsumer.accept(event)
    }
    return myJavacParser.parse(line, reader, wrappedConsumer)
  }
}