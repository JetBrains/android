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

import com.android.tools.idea.gradle.project.sync.issues.SyncIssuesReporter.consoleLinkWithSeparatorText
import com.android.tools.idea.studiobot.StudioBot
import com.intellij.build.events.BuildEvent
import com.intellij.build.events.FileMessageEvent
import com.intellij.build.events.impl.FileMessageEventImpl
import com.intellij.build.output.BuildOutputInstantReader
import com.intellij.build.output.BuildOutputParser
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import java.util.function.Consumer


/**
 * A wrapper class for all the build output parsers so we can inject StudioBot help link.
 */
class BuildOutputParserWrapper(val parser: BuildOutputParser) : BuildOutputParser {

  private val explainerAvailable
    get() = StudioBot.getInstance().isAvailable()

  override fun parse(line: String?, reader: BuildOutputInstantReader?, messageConsumer: Consumer<in BuildEvent>?): Boolean {
    return parser.parse(line, reader) {
      val taskId = it.parentId as? ExternalSystemTaskId
      val event =
        if (explainerAvailable
            && ExternalSystemTaskType.RESOLVE_PROJECT == taskId?.type
            && !it.message.startsWith("Unresolved reference:"))
        {
          it.injectExplanationText()
        } else {
          it
        }
      messageConsumer?.accept(event)
    }
  }

  /**
   * Insert text in error to explain the issue; this will be picked up as a URL by
   * ExplainBuildErrorFilter
   */
  private fun BuildEvent.injectExplanationText(): BuildEvent {
    return if (this is FileMessageEvent) {
      val description = (description?.trimEnd()?.plus("\n\n") ?: "") +
                        consoleLinkWithSeparatorText + message
      FileMessageEventImpl(parentId ?: "", kind, group, message, description, filePosition)
    } else {
      this
    }
  }
}