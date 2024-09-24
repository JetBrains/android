/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.build.events

import com.android.tools.idea.gradle.project.build.events.studiobot.GradleErrorContext
import com.android.tools.idea.gradle.project.sync.idea.issues.BuildIssueComposer
import com.android.tools.idea.gradle.project.sync.idea.issues.ErrorMessageAwareBuildIssue
import com.android.tools.idea.gradle.project.sync.quickFixes.OpenLinkQuickFix
import com.android.tools.idea.gradle.project.sync.quickFixes.OpenStudioBotBuildIssueQuickFix
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.BuildErrorMessage
import com.intellij.build.FilePosition
import com.intellij.build.events.MessageEvent
import com.intellij.build.events.impl.BuildIssueEventImpl
import com.intellij.build.events.impl.FileMessageEventImpl
import com.intellij.build.events.impl.MessageEventImpl
import org.junit.Test
import java.io.File

class BuildIssueEventWrappersTest {
  private val buildIssueFix = OpenStudioBotBuildIssueQuickFix(GradleErrorContext(null, "some error message", null, null))

  @Test
  fun testWrappedEventAddsQuickFixAndPreservesFileMessageEventImplFields() {
    val originalEvent = FileMessageEventImpl(
      "parentId",
      MessageEvent.Kind.ERROR,
      "title",
      "message",
      "detailed message",
      FilePosition(File.createTempFile("someprefix", "some suffix"), 1, 1)
    )

    val wrappedEvent = FileMessageBuildIssueEvent(originalEvent, buildIssueFix)

    assertThat(wrappedEvent.issue.quickFixes.size).isEqualTo(1)
    assertThat(wrappedEvent.issue.quickFixes.first()).isSameAs(buildIssueFix)
    assertThat(wrappedEvent.result.filePosition).isEqualTo(originalEvent.result.filePosition)
    assertThat(wrappedEvent.result.kind).isEqualTo(originalEvent.result.kind)
    assertThat(wrappedEvent.result.details).isEqualTo(originalEvent.result.details + "\n<a href=\"open.plugin.studio.bot\">Ask Gemini</a>")
    assertThat(wrappedEvent.filePosition).isEqualTo(originalEvent.filePosition)
    assertThat(wrappedEvent.hint).isEqualTo(originalEvent.hint)
    assertThat(wrappedEvent.description).isEqualTo(originalEvent.description + "\n<a href=\"open.plugin.studio.bot\">Ask Gemini</a>")
  }

  @Test
  fun testWrappedEventAddsQuickFixAndPreservesMessageEventImplFields() {
    val originalEvent = MessageEventImpl(
      "parentId",
      MessageEvent.Kind.ERROR,
      "title",
      "message",
      "detailed message")

    val wrappedEvent = MessageBuildIssueEvent(originalEvent, buildIssueFix)

    assertThat(wrappedEvent.issue.quickFixes.size).isEqualTo(1)
    assertThat(wrappedEvent.issue.quickFixes.first()).isSameAs(buildIssueFix)
    assertThat(wrappedEvent.kind).isEqualTo(originalEvent.kind)
    assertThat(wrappedEvent.group).isEqualTo(originalEvent.group)
    assertThat(wrappedEvent.result.kind).isEqualTo(originalEvent.result.kind)
    assertThat(wrappedEvent.result.details).isEqualTo(originalEvent.result.details + "\n<a href=\"open.plugin.studio.bot\">Ask Gemini</a>")
    assertThat(wrappedEvent.description).isEqualTo(originalEvent.description + "\n<a href=\"open.plugin.studio.bot\">Ask Gemini</a>")
  }

  @Test
  fun testWrappedEventAddsQuickFixAndPreservesBuildIssueEventImplFields() {
    val originalEvent = BuildIssueEventImpl(
      "parentId",
      BuildIssueComposer("base message", "title")
        .addQuickFix("Open docs", OpenLinkQuickFix("https://docs.gradle.org"))
        .composeBuildIssue(),
      MessageEvent.Kind.ERROR,)

    val wrappedEvent = originalEvent.copyWithQuickFix(buildIssueFix)

    assertThat(wrappedEvent.issue.quickFixes.size).isEqualTo(2)
    assertThat(wrappedEvent.issue.quickFixes[0]).isInstanceOf(OpenLinkQuickFix::class.java)
    assertThat(wrappedEvent.issue.quickFixes[1]).isEqualTo(buildIssueFix)
    assertThat(wrappedEvent.eventTime).isEqualTo(originalEvent.eventTime)
    assertThat(wrappedEvent.id).isEqualTo(originalEvent.id)
    assertThat(wrappedEvent.message).isEqualTo(originalEvent.message)
    assertThat(wrappedEvent.parentId).isEqualTo(originalEvent.parentId)
    assertThat(wrappedEvent.kind).isEqualTo(originalEvent.kind)
    assertThat(wrappedEvent.group).isEqualTo(originalEvent.group)
    assertThat(wrappedEvent.result.kind).isEqualTo(originalEvent.result.kind)
    assertThat(wrappedEvent.result.details).isEqualTo(originalEvent.result.details + "\n<a href=\"open.plugin.studio.bot\">Ask Gemini</a>")
    assertThat(wrappedEvent.description).isEqualTo(originalEvent.description + "\n<a href=\"open.plugin.studio.bot\">Ask Gemini</a>")
  }

  @Test
  fun testWrappedEventPreservesErrorMessageAwareBuildIssue() {
    val buildErrorMessage = BuildErrorMessage.newBuilder().setErrorShownType(
      BuildErrorMessage.ErrorType.JAVA_NOT_SUPPORTED_LANGUAGE_LEVEL).build()
    val originalEvent = BuildIssueEventImpl(
      "parentId",
      BuildIssueComposer("base message", "title")
        .addQuickFix("Open docs", OpenLinkQuickFix("https://docs.gradle.org"))
        .composeErrorMessageAwareBuildIssue(
          buildErrorMessage
        ),
      MessageEvent.Kind.ERROR,)

    val wrappedEvent = originalEvent.copyWithQuickFix(buildIssueFix)

    assertThat(wrappedEvent.issue).isInstanceOf(ErrorMessageAwareBuildIssue::class.java)
    assertThat((wrappedEvent.issue as ErrorMessageAwareBuildIssue).buildErrorMessage).isEqualTo(buildErrorMessage)
    assertThat(wrappedEvent.issue.quickFixes[0]).isInstanceOf(OpenLinkQuickFix::class.java)
    assertThat(wrappedEvent.issue.quickFixes[1]).isEqualTo(buildIssueFix)
    assertThat(wrappedEvent.issue.title).isEqualTo(originalEvent.issue.title)
    assertThat(wrappedEvent.issue.description).isEqualTo(originalEvent.issue.description + "\n<a href=\"open.plugin.studio.bot\">Ask Gemini</a>")
  }
}