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
import com.intellij.build.events.DuplicateMessageAware
import com.intellij.build.events.impl.FileMessageEventImpl
import com.intellij.build.events.impl.MessageEventImpl
import com.intellij.build.output.BuildOutputInstantReader
import com.intellij.build.output.BuildOutputParser
import com.intellij.build.output.JavacOutputParser
import org.jetbrains.plugins.gradle.execution.build.output.GradleCompilationReportParser
import java.util.function.Consumer

/**
 * This parser delegates work to original [JavacOutputParser] but allows to filter some of the generated messages.
 */
class FilteringJavacOutputParser : BuildOutputParser {
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

/**
 * This parser delegates work to original [GradleCompilationReportParser] but allows to filter some of the generated messages.
 */
class FilteringGradleCompilationReportParser : BuildOutputParser {
  private val myCompilationParser = GradleCompilationReportParser()

  override fun parse(line: String, reader: BuildOutputInstantReader, messageConsumer: Consumer<in BuildEvent>): Boolean {
    val wrappedConsumer = Consumer<BuildEvent> { event ->
      if (JavaLanguageLevelDeprecationOutputParser.notSupportedMessagePattern.matcher(event.message).matches()) {
        return@Consumer
      }
      // Convert generated events to DuplicateMessageAware. Compilation errors are already parsed from tasks output normally,
      // so lines in final failure message are most likely a duplication.
      when (event) {
        is FileMessageEventImpl -> messageConsumer.accept(
          object : FileMessageEventImpl(event.parentId!!, event.kind, event.group, event.message, event.description, event.filePosition), DuplicateMessageAware {}
        )
        is MessageEventImpl -> messageConsumer.accept(
          object : MessageEventImpl(event.parentId!!, event.kind, event.group, event.message, event.description), DuplicateMessageAware {}
        )
        else -> messageConsumer.accept(event)
      }
    }
    return myCompilationParser.parse(line, reader, wrappedConsumer)
  }
}