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

import com.android.tools.idea.gradle.project.sync.idea.issues.OpenLinkDescribedQuickFix
import com.android.tools.idea.gradle.project.sync.quickFixes.AbstractSetJavaLanguageLevelQuickFix
import com.android.tools.idea.gradle.project.sync.quickFixes.OpenLinkQuickFix
import com.google.common.truth.Truth.assertThat
import com.intellij.build.events.BuildIssueEvent
import com.intellij.build.events.MessageEvent
import com.intellij.lang.LangBundle
import com.intellij.pom.java.LanguageLevel
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class KotlincWithQuickFixesParserTest {

  @get:Rule
  val temporaryFolder = TemporaryFolder()

  @Test
  fun `Cannot inline bytecode with JVM target 8 has correct quickfixes`() {
    val file = temporaryFolder.newFile("file.kt")
    verifyJvmTarget8QuickFixes(
      errorLine = "e: $file: (42, 47): Cannot inline bytecode built with JVM target 1.8 into bytecode that is being built with JVM target 1.6. Please specify proper '-jvm-target' option",
      expectedMessage = "Cannot inline bytecode built with JVM target 1.8 into bytecode that is being built with JVM target 1.6. Please specify proper '-jvm-target' option",
      expectedDetails = """
        e: $file: (42, 47): Cannot inline bytecode built with JVM target 1.8 into bytecode that is being built with JVM target 1.6. Please specify proper '-jvm-target' option
        Adding support for Java 8 language features could solve this issue.

        <a href="set.java.level.JDK_1_8.all">Change Java language level and jvmTarget to 8 in all modules if using a lower level.</a>
        <a href="open.more.details">More information...</a>
      """.trimIndent()

      )
  }

  @Test
  fun `Calls to static methods prohibited in JVM target 6 has correct quickfixes`() {
    val file = temporaryFolder.newFile("file.kt")
    verifyJvmTarget8QuickFixes(
      errorLine = "e: $file: (15, 19): Calls to static methods in Java interfaces are prohibited in JVM target 1.6. Recompile with '-jvm-target 1.8'",
      expectedMessage = "Calls to static methods in Java interfaces are prohibited in JVM target 1.6. Recompile with '-jvm-target 1.8'",
      expectedDetails = """
        e: $file: (15, 19): Calls to static methods in Java interfaces are prohibited in JVM target 1.6. Recompile with '-jvm-target 1.8'
        Adding support for Java 8 language features could solve this issue.

        <a href="set.java.level.JDK_1_8.all">Change Java language level and jvmTarget to 8 in all modules if using a lower level.</a>
        <a href="open.more.details">More information...</a>
      """.trimIndent()
    )
  }

  private fun verifyJvmTarget8QuickFixes(
    errorLine: String,
    expectedMessage: String,
    expectedDetails: String
  ) {
    val reader = TestBuildOutputInstantReader(listOf(errorLine), "testId")
    val parser = KotlincWithQuickFixesParser()
    val consumer = TestMessageEventConsumer()

    assertThat(parser.parse(errorLine, reader, consumer)).isTrue()

    val events = consumer.messageEvents
    assertThat(events).hasSize(1)
    assertThat(events[0]).isInstanceOf(BuildIssueEvent::class.java)
    (events[0] as MessageEvent).let {
      assertThat(it.group).isEqualTo(LangBundle.message("build.event.title.kotlin.compiler"))
      assertThat(it.kind).isEqualTo(MessageEvent.Kind.ERROR)
      assertThat(it.parentId).isEqualTo("testId")
      assertThat(it.message).isEqualTo(expectedMessage)
      assertThat(it.description).isEqualTo(expectedDetails)
    }
    val quickFixes = (events[0] as BuildIssueEvent).issue.quickFixes
    assertThat(quickFixes).hasSize(2)
    val setJvmQuickFixes = quickFixes.filterIsInstance(AbstractSetJavaLanguageLevelQuickFix::class.java)
    assertThat(setJvmQuickFixes).isNotEmpty()
    assertThat(setJvmQuickFixes.map { it.level }).containsAllIn(arrayOf(LanguageLevel.JDK_1_8))
    assertThat(quickFixes.filterIsInstance(OpenLinkQuickFix::class.java)).isNotEmpty()
  }
}