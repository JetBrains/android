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

import com.android.testutils.AssumeUtil
import com.android.tools.idea.gradle.project.build.output.tomlParser.TomlErrorParserTest.Companion.getVersionCatalogLibsBuildIssueDescription
import com.android.tools.idea.gradle.project.build.output.tomlParser.TomlErrorParserTest.Companion.getVersionCatalogLibsBuildOutput
import com.intellij.build.events.MessageEvent
import com.intellij.openapi.util.io.FileUtil
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * This class focuses on testing parsing of Gradle failure message:
 *
 * ```
 * FAILURE: Build failed with an exception.
 *
 * * Where:
 * ...
 *
 * * What went wrong:
 * ...
 * ```
 *
 * We currently have several parsers that try to parse this, so we need to make sure they work together in different cases.
 * - [com.android.tools.idea.gradle.project.build.output.tomlParser.TomlErrorParser]
 * - [ConfigurationCacheErrorParser]
 * - [org.jetbrains.plugins.gradle.execution.build.output.GradleBuildScriptErrorParser]
 */
class GradleFailureOutputParserTest : BuildOutputParserTest() {

  @get:Rule
  val temporaryFolder = TemporaryFolder()

  @Test
  fun configurationCacheErrorParsed() {
    val basePath = temporaryFolder.newFolder()
    val buildOutput = """
FAILURE: Build failed with an exception.

* Where:
Build file '$basePath/app/build.gradle' line: 6

* What went wrong:
Configuration cache problems found in this build.

7 problems were found storing the configuration cache, 6 of which seem unique.
- Plugin 'com.android.internal.application': registration of listener on 'Gradle.addListener' is unsupported
  See https://docs.gradle.org/7.0-rc-1/userguide/configuration_cache.html#config_cache:requirements:build_listeners
- Plugin 'com.android.internal.application': registration of listener on 'TaskExecutionGraph.addTaskExecutionListener' is unsupported
  See https://docs.gradle.org/7.0-rc-1/userguide/configuration_cache.html#config_cache:requirements:build_listeners
- Plugin 'kotlin-android': registration of listener on 'Gradle.addBuildListener' is unsupported
  See https://docs.gradle.org/7.0-rc-1/userguide/configuration_cache.html#config_cache:requirements:build_listeners
- Task `:app:compileDebugKotlin` of type `org.jetbrains.kotlin.gradle.tasks.KotlinCompile`: cannot serialize object of type 'org.gradle.api.internal.project.DefaultProject', a subtype of 'org.gradle.api.Project', as these are not supported with the configuration cache.
  See https://docs.gradle.org/7.0-rc-1/userguide/configuration_cache.html#config_cache:requirements:disallowed_types

See the complete report at file:///$basePath/build/reports/configuration-cache/2gkns3dn3dw9zvkxnlpqbiba2-41/configuration-cache-report.html
> Listener registration 'Gradle.addListener' by build 'SimpleApplication2' is unsupported.
> Listener registration 'TaskExecutionGraph.addTaskExecutionListener' by org.gradle.execution.taskgraph.DefaultTaskExecutionGraph@43a21c71 is unsupported.
> Listener registration 'Gradle.addBuildListener' by build 'SimpleApplication2' is unsupported.

* Try:
Run with --stacktrace option to get the stack trace. Run with --info or --debug option to get more log output. Run with --scan to get full insights.

* Get more help at https://help.gradle.org
""".trimIndent()
    parseOutput(
      parentEventId = "testId",
      gradleOutput = buildOutput,
      expectedEvents = listOf(ExpectedEvent(
        message = "Configuration cache problems found in this build.",
        isFileMessageEvent = false,
        isBuildIssueEvent = true,
        isDuplicateMessageAware = false,
        group = "Build Issues",
        kind= MessageEvent.Kind.ERROR,
        parentId = "testId",
        description = """
        Configuration cache problems found in this build.

        7 problems were found storing the configuration cache, 6 of which seem unique.
        - Plugin 'com.android.internal.application': registration of listener on 'Gradle.addListener' is unsupported
          See https://docs.gradle.org/7.0-rc-1/userguide/configuration_cache.html#config_cache:requirements:build_listeners
        - Plugin 'com.android.internal.application': registration of listener on 'TaskExecutionGraph.addTaskExecutionListener' is unsupported
          See https://docs.gradle.org/7.0-rc-1/userguide/configuration_cache.html#config_cache:requirements:build_listeners
        - Plugin 'kotlin-android': registration of listener on 'Gradle.addBuildListener' is unsupported
          See https://docs.gradle.org/7.0-rc-1/userguide/configuration_cache.html#config_cache:requirements:build_listeners
        - Task `:app:compileDebugKotlin` of type `org.jetbrains.kotlin.gradle.tasks.KotlinCompile`: cannot serialize object of type 'org.gradle.api.internal.project.DefaultProject', a subtype of 'org.gradle.api.Project', as these are not supported with the configuration cache.
          See https://docs.gradle.org/7.0-rc-1/userguide/configuration_cache.html#config_cache:requirements:disallowed_types

        See the complete report at file:///$basePath/build/reports/configuration-cache/2gkns3dn3dw9zvkxnlpqbiba2-41/configuration-cache-report.html
        > Listener registration 'Gradle.addListener' by build 'SimpleApplication2' is unsupported.
        > Listener registration 'TaskExecutionGraph.addTaskExecutionListener' by org.gradle.execution.taskgraph.DefaultTaskExecutionGraph@43a21c71 is unsupported.
        > Listener registration 'Gradle.addBuildListener' by build 'SimpleApplication2' is unsupported.
        """.trimIndent()
      ))
    )
  }

  @Test
  fun aaptErrorParsed() {
    AssumeUtil.assumeNotWindows() // TODO (b/399625141): fix on windows
    val path = temporaryFolder.newFile("/styles.xml")
    val systemIndependentPath = FileUtil.toSystemIndependentName(path.path)
    val buildOutput = """
      > Task :app:processDebugResources FAILED
      AGPBI: {"kind":"error","text":"Android resource linking failed","sources":[{"file":"$systemIndependentPath","position":{"startLine":3,"startColumn":4,"startOffset":54,"endLine":14,"endColumn":12,"endOffset":686}}],"original":"$systemIndependentPath:4:5-15:13: AAPT: error: style attribute 'attr/colorPrfimary (aka com.example.myapplication:attr/colorPrfimary)' not found.\n    ","tool":"AAPT"}
      AGPBI: {"kind":"error","text":"Android resource linking failed","sources":[{"file":"$systemIndependentPath","position":{"startLine":3,"startColumn":4,"startOffset":54,"endLine":14,"endColumn":12,"endOffset":686}}],"original":"$systemIndependentPath:4:5-15:13: AAPT: error: style attribute 'attr/colorPgfrimaryDark (aka com.example.myapplication:attr/colorPgfrimaryDark)' not found.\n    ","tool":"AAPT"}
      AGPBI: {"kind":"error","text":"Android resource linking failed","sources":[{"file":"$systemIndependentPath","position":{"startLine":3,"startColumn":4,"startOffset":54,"endLine":14,"endColumn":12,"endOffset":686}}],"original":"$systemIndependentPath:4:5-15:13: AAPT: error: style attribute 'attr/dfg (aka com.example.myapplication:attr/dfg)' not found.\n    ","tool":"AAPT"}
      AGPBI: {"kind":"error","text":"Android resource linking failed","sources":[{"file":"$systemIndependentPath","position":{"startLine":3,"startColumn":4,"startOffset":54,"endLine":14,"endColumn":12,"endOffset":686}}],"original":"$systemIndependentPath:4:5-15:13: AAPT: error: style attribute 'attr/colorEdfdrror (aka com.example.myapplication:attr/colorEdfdrror)' not found.\n    ","tool":"AAPT"}

      FAILURE: Build failed with an exception.

      * What went wrong:
      Execution failed for task ':app:processDebugResources'.
      > A failure occurred while executing com.android.build.gradle.internal.tasks.Workers.ActionFacade
         > Android resource linking failed
           $path:4:5-15:13: AAPT: error: style attribute 'attr/colorPrfimary (aka com.example.myapplication:attr/colorPrfimary)' not found.

           $path:4:5-15:13: AAPT: error: style attribute 'attr/colorPgfrimaryDark (aka com.example.myapplication:attr/colorPgfrimaryDark)' not found.

           $path:4:5-15:13: AAPT: error: style attribute 'attr/dfg (aka com.example.myapplication:attr/dfg)' not found.

           $path:4:5-15:13: AAPT: error: style attribute 'attr/colorEdfdrror (aka com.example.myapplication:attr/colorEdfdrror)' not found.


      * Try:
      Run with --stacktrace option to get the stack trace. Run with --info or --debug option to get more log output. Run with --scan to get full insights.

      * Get more help at https://help.gradle.org
    """.trimIndent()
    parseOutput(
      parentEventId = "testId",
      gradleOutput = buildOutput,
      expectedEvents = listOf(
        ExpectedEvent(
          message = "Android resource linking failed",
          isFileMessageEvent = true,
          isBuildIssueEvent = false,
          isDuplicateMessageAware = false,
          group = "AAPT errors",
          kind= MessageEvent.Kind.ERROR,
          parentId = "testId",
          filePosition = "$path:4:5-15:13",
          description = """
          $path:4:5-15:13: AAPT: error: style attribute 'attr/colorPrfimary (aka com.example.myapplication:attr/colorPrfimary)' not found.
          """.trimIndent()
        ),
        ExpectedEvent(
          message = "Android resource linking failed",
          isFileMessageEvent = true,
          isBuildIssueEvent = false,
          isDuplicateMessageAware = false,
          group = "AAPT errors",
          kind= MessageEvent.Kind.ERROR,
          parentId = "testId",
          filePosition = "$path:4:5-15:13",
          description = """
          $path:4:5-15:13: AAPT: error: style attribute 'attr/colorPgfrimaryDark (aka com.example.myapplication:attr/colorPgfrimaryDark)' not found.
          """.trimIndent()
        ),
        ExpectedEvent(
          message = "Android resource linking failed",
          isFileMessageEvent = true,
          isBuildIssueEvent = false,
          isDuplicateMessageAware = false,
          group = "AAPT errors",
          kind= MessageEvent.Kind.ERROR,
          parentId = "testId",
          filePosition = "$path:4:5-15:13",
          description = """
          $path:4:5-15:13: AAPT: error: style attribute 'attr/dfg (aka com.example.myapplication:attr/dfg)' not found.
          """.trimIndent()
        ),
        ExpectedEvent(
          message = "Android resource linking failed",
          isFileMessageEvent = true,
          isBuildIssueEvent = false,
          isDuplicateMessageAware = false,
          group = "AAPT errors",
          kind= MessageEvent.Kind.ERROR,
          parentId = "testId",
          filePosition = "$path:4:5-15:13",
          description = """
          $path:4:5-15:13: AAPT: error: style attribute 'attr/colorEdfdrror (aka com.example.myapplication:attr/colorEdfdrror)' not found.
          """.trimIndent()
        ),
      )
    )
  }

  @Test
  fun testTomlErrorWithFileParsed() {
    val buildOutput = getVersionCatalogLibsBuildOutput("/arbitrary/path/to/file.versions.toml")
    parseOutput(
      parentEventId = "testId",
      gradleOutput = buildOutput,
      expectedEvents = listOf(ExpectedEvent(
      message = "Invalid TOML catalog definition.",
      isFileMessageEvent = false,
      isBuildIssueEvent = true,
      isDuplicateMessageAware = false,
      group = "Build Issues",
      kind= MessageEvent.Kind.ERROR,
      parentId = "testId",
      description = getVersionCatalogLibsBuildIssueDescription("/arbitrary/path/to/file.versions.toml")
    )))
  }
}