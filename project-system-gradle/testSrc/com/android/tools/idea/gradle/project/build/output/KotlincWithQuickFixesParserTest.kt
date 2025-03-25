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

import com.intellij.build.events.MessageEvent
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class KotlincWithQuickFixesParserTest : BuildOutputParserTest() {

  @get:Rule
  val temporaryFolder = TemporaryFolder()

  private lateinit var file: File

  @Before
  fun setUp() {
    file = temporaryFolder.newFile("file.kt")
  }

  @Test
  fun `Cannot inline bytecode with JVM target 8 has correct quickfixes`() {
    parseOutput(
      parentEventId = "testId",
      gradleOutput = "e: $file: (42, 47): Cannot inline bytecode built with JVM target 1.8 into bytecode that is being built with JVM target 1.6. Please specify proper '-jvm-target' option",
      expectedEvents = listOf(ExpectedEvent(
        message = "Cannot inline bytecode built with JVM target 1.8 into bytecode that is being built with JVM target 1.6. Please specify proper '-jvm-target' option",
        isFileMessageEvent = true,
        isBuildIssueEvent = true,
        isDuplicateMessageAware = false,
        group = "Kotlin Compiler",
        kind= MessageEvent.Kind.ERROR,
        parentId = "testId",
        filePosition = "${file}:42:47-42:47",
        description = """
        e: $file: (42, 47): Cannot inline bytecode built with JVM target 1.8 into bytecode that is being built with JVM target 1.6. Please specify proper '-jvm-target' option
        Adding support for Java 8 language features could solve this issue.

        <a href="set.java.level.JDK_1_8.all">Change Java language level and jvmTarget to 8 in all modules if using a lower level.</a>
        <a href="open.more.details">More information...</a>
        """.trimIndent()))
    )
  }

  @Test
  fun `Calls to static methods prohibited in JVM target 6 has correct quickfixes`() {
    parseOutput(
      parentEventId = "testId",
      gradleOutput = "e: $file: (15, 19): Calls to static methods in Java interfaces are prohibited in JVM target 1.6. Recompile with '-jvm-target 1.8'",
      expectedEvents = listOf(ExpectedEvent(
        message = "Calls to static methods in Java interfaces are prohibited in JVM target 1.6. Recompile with '-jvm-target 1.8'",
        isFileMessageEvent = true,
        isBuildIssueEvent = true,
        isDuplicateMessageAware = false,
        group = "Kotlin Compiler",
        kind= MessageEvent.Kind.ERROR,
        parentId = "testId",
        filePosition = "${file}:15:19-15:19",
        description = """
        e: $file: (15, 19): Calls to static methods in Java interfaces are prohibited in JVM target 1.6. Recompile with '-jvm-target 1.8'
        Adding support for Java 8 language features could solve this issue.

        <a href="set.java.level.JDK_1_8.all">Change Java language level and jvmTarget to 8 in all modules if using a lower level.</a>
        <a href="open.more.details">More information...</a>
        """.trimIndent()))
    )
  }
}