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
package com.android.tools.idea.gradle.project.build.output

import com.intellij.build.events.MessageEvent
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GradleMultipleFailureOutputParserTest : BuildOutputParserTest() {

  @get:Rule
  val temporaryFolder = TemporaryFolder()

  @Test
  fun multipleBuildExceptions_withTaskWithoutLocation() {
    val buildOutput = """
FAILURE: Build completed with 2 failures.

1: Task failed with an exception.
-----------
* What went wrong:
Execution failed for task ':lib:compileJava'.
> General fake failure 1
  error: error description 1
  error: error description 2
  2 errors

* Try:
> Check your code and dependencies to fix the compilation error(s)
> Run with --scan to get full insights.
==============================================================================

2: Task failed with an exception.
-----------
* What went wrong:
Execution failed for task ':app:compileDebugJavaWithJavac'.
> General fake failure 2
  some
  multiline
  description

* Try:
> Run with --stacktrace option to get the stack trace.
> Run with --debug option to get more log output.
> Run with --scan to get full insights.
> Get more help at https://help.gradle.org.
==============================================================================

BUILD FAILED in 4s
50 actionable tasks: 48 executed, 2 up-to-date
""".trimIndent()
    parseOutput(
      parentEventId = "testId",
      gradleOutput = buildOutput,
      // Compilation errors are filtered out, as they should be reported by separate parsers
      expectedEvents = listOf(
        ExpectedEvent(
          message = "General fake failure 1",
          isFileMessageEvent = false,
          isBuildIssueEvent = false,
          isDuplicateMessageAware = true,
          group = "Other Messages",
          kind= MessageEvent.Kind.ERROR,
          parentId = ":lib:compileJava",
          description = """
          Execution failed for task ':lib:compileJava'.
          > General fake failure 1
            error: error description 1
            error: error description 2
            2 errors
          
          * Try:
          > Check your code and dependencies to fix the compilation error(s)
          > Run with --scan to get full insights.
        """.trimIndent()
        ),
        ExpectedEvent(
        message = "General fake failure 2",
        isFileMessageEvent = false,
        isBuildIssueEvent = false,
        isDuplicateMessageAware = true,
        group = "Other Messages",
        kind= MessageEvent.Kind.ERROR,
        parentId = ":app:compileDebugJavaWithJavac",
        description = """
        Execution failed for task ':app:compileDebugJavaWithJavac'.
        > General fake failure 2
          some
          multiline
          description
        
        * Try:
        > Run with --stacktrace option to get the stack trace.
        > Run with --debug option to get more log output.
        > Run with --scan to get full insights.
        > Get more help at https://help.gradle.org.
        """.trimIndent()
      ))
    )
  }

  @Test
  fun multipleBuildExceptions_duplicated_noTask_withFile() {
    val buildGradle = temporaryFolder.newFile("/build.gradle")
    val buildOutput = """
FAILURE: Build completed with 2 failures.

1: Task failed with an exception.
-----------
* Where:
Build file '$buildGradle' line: 62

* What went wrong:
A problem occurred evaluating project ':app'.
> Could not set unknown property 'useIR' for object of type org.jetbrains.kotlin.gradle.plugin.KotlinAndroidPlugin${'$'}apply${'$'}1${'$'}1${'$'}kotlinOptions${'$'}1.

* Try:
> Run with --stacktrace option to get the stack trace.
> Run with --info or --debug option to get more log output.
> Run with --scan to get full insights.
> Get more help at https://help.gradle.org.
==============================================================================

2: Task failed with an exception.
-----------
* Where:
Build file '$buildGradle' line: 62

* What went wrong:
A problem occurred evaluating project ':app'.
> Could not set unknown property 'useIR' for object of type org.jetbrains.kotlin.gradle.plugin.KotlinAndroidPlugin${'$'}apply${'$'}1${'$'}1${'$'}kotlinOptions${'$'}1.

* Try:
> Run with --stacktrace option to get the stack trace.
> Run with --info or --debug option to get more log output.
> Run with --scan to get full insights.
> Get more help at https://help.gradle.org.
==============================================================================

Deprecated Gradle features were used in this build, making it incompatible with Gradle 9.0.

You can use '--warning-mode all' to show the individual deprecation warnings and determine if they come from your own scripts or plugins.

For more on this, please refer to https://docs.gradle.org/8.13/userguide/command_line_interface.html#sec:command_line_warnings in the Gradle documentation.

BUILD FAILED in 251ms
""".trimIndent()
    parseOutput(
      parentEventId = "testId",
      gradleOutput = buildOutput,
      // Errors are duplicated, events get deduplicated resulting to single instance.
      expectedEvents = listOf(ExpectedEvent(
        message = "Could not set unknown property 'useIR' for object of type org.jetbrains.kotlin.gradle.plugin.KotlinAndroidPlugin${'$'}apply\$1\$1${'$'}kotlinOptions\$1",
        isFileMessageEvent = true,
        isBuildIssueEvent = false,
        isDuplicateMessageAware = true,
        group = "Other Messages",
        kind= MessageEvent.Kind.ERROR,
        parentId = taskId,
        filePosition = "$buildGradle:62:1-62:1",
        description = """
          Build file '$buildGradle' line: 62
          
          A problem occurred evaluating project ':app'.
          > Could not set unknown property 'useIR' for object of type org.jetbrains.kotlin.gradle.plugin.KotlinAndroidPlugin${'$'}apply${'$'}1${'$'}1${'$'}kotlinOptions${'$'}1.

          * Try:
          > Run with --stacktrace option to get the stack trace.
          > Run with --info or --debug option to get more log output.
          > Run with --scan to get full insights.
          > Get more help at https://help.gradle.org.
        """.trimIndent()
      ))
    )
  }

  @Test
  fun testWithStacktrace() {
    val stacktrace = RuntimeException("Error Message").stackTraceToString()

    val buildOutput = """
FAILURE: Build completed with 2 failures.

1: Task failed with an exception.
-----------
* What went wrong:
Execution failed for task ':lib:compileJava'.
> General fake failure 1
  error: error description 1
  error: error description 2
  2 errors

* Try:
> Check your code and dependencies to fix the compilation error(s)
> Run with --scan to get full insights.

* Exception is:
$stacktrace

==============================================================================

2: Task failed with an exception.
-----------
* What went wrong:
Execution failed for task ':app:compileDebugJavaWithJavac'.
> General fake failure 2
  some
  multiline
  description

* Try:
> Run with --stacktrace option to get the stack trace.
> Run with --debug option to get more log output.
> Run with --scan to get full insights.
> Get more help at https://help.gradle.org.

* Exception is:
$stacktrace

==============================================================================

BUILD FAILED in 4s
50 actionable tasks: 48 executed, 2 up-to-date
""".trimIndent()
    parseOutput(
      parentEventId = "testId",
      gradleOutput = buildOutput,
      // Compilation errors are filtered out, as they should be reported by separate parsers
      expectedEvents = listOf(
        ExpectedEvent(
          message = "General fake failure 1",
          isFileMessageEvent = false,
          isBuildIssueEvent = false,
          isDuplicateMessageAware = true,
          group = "Other Messages",
          kind= MessageEvent.Kind.ERROR,
          parentId = ":lib:compileJava",
          description = """
Execution failed for task ':lib:compileJava'.
> General fake failure 1
  error: error description 1
  error: error description 2
  2 errors

* Try:
> Check your code and dependencies to fix the compilation error(s)
> Run with --scan to get full insights.

* Exception is:
$stacktrace""".trimIndent()
        ),
        ExpectedEvent(
          message = "General fake failure 2",
          isFileMessageEvent = false,
          isBuildIssueEvent = false,
          isDuplicateMessageAware = true,
          group = "Other Messages",
          kind= MessageEvent.Kind.ERROR,
          parentId = ":app:compileDebugJavaWithJavac",
          description = """
Execution failed for task ':app:compileDebugJavaWithJavac'.
> General fake failure 2
  some
  multiline
  description

* Try:
> Run with --stacktrace option to get the stack trace.
> Run with --debug option to get more log output.
> Run with --scan to get full insights.
> Get more help at https://help.gradle.org.

* Exception is:
$stacktrace""".trimIndent()
        ))
    )
  }
}