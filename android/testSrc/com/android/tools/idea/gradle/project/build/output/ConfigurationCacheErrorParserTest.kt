/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.android.utils.toSystemLineSeparator
import com.google.common.base.Splitter
import com.google.common.truth.Truth
import com.intellij.build.events.MessageEvent
import com.intellij.testFramework.LightPlatformTestCase

class ConfigurationCacheErrorParserTest : LightPlatformTestCase() {

  fun testConfigurationCacheErrorParsed() {
    val buildOutput = """
FAILURE: Build failed with an exception.

* Where:
Build file '${project.basePath}/app/build.gradle' line: 6

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

See the complete report at file:///${project.basePath}/build/reports/configuration-cache/2gkns3dn3dw9zvkxnlpqbiba2-41/configuration-cache-report.html
> Listener registration 'Gradle.addListener' by build 'SimpleApplication2' is unsupported.
> Listener registration 'TaskExecutionGraph.addTaskExecutionListener' by org.gradle.execution.taskgraph.DefaultTaskExecutionGraph@43a21c71 is unsupported.
> Listener registration 'Gradle.addBuildListener' by build 'SimpleApplication2' is unsupported.

* Try:
Run with --stacktrace option to get the stack trace. Run with --info or --debug option to get more log output. Run with --scan to get full insights.

* Get more help at https://help.gradle.org
""".trimIndent()

    val parser = ConfigurationCacheErrorParser()
    val reader = TestBuildOutputInstantReader(Splitter.on("\n").split(buildOutput).toList())
    val consumer = TestMessageEventConsumer()

    val line = reader.readLine()!!
    val parsed = parser.parse(line, reader, consumer)

    Truth.assertThat(parsed).isTrue()
    consumer.messageEvents.filterIsInstance<MessageEvent>().single().let {
      Truth.assertThat(it.parentId).isEqualTo(reader.parentEventId)
      Truth.assertThat(it.message).isEqualTo("Configuration cache problems found in this build.")
      Truth.assertThat(it.kind).isEqualTo(MessageEvent.Kind.ERROR)
      Truth.assertThat(it.description).isEqualTo("""
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

See the complete report at file:///${project.basePath}/build/reports/configuration-cache/2gkns3dn3dw9zvkxnlpqbiba2-41/configuration-cache-report.html
> Listener registration 'Gradle.addListener' by build 'SimpleApplication2' is unsupported.
> Listener registration 'TaskExecutionGraph.addTaskExecutionListener' by org.gradle.execution.taskgraph.DefaultTaskExecutionGraph@43a21c71 is unsupported.
> Listener registration 'Gradle.addBuildListener' by build 'SimpleApplication2' is unsupported.
""".trimIndent().toSystemLineSeparator())

    }
  }

  fun testOtherErrorNotParsed() {
    val path = "${project.basePath}/styles.xml"
    val buildOutput = """
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

    val parser = ConfigurationCacheErrorParser()
    val reader = TestBuildOutputInstantReader(Splitter.on("\n").split(buildOutput).toList())
    val consumer = TestMessageEventConsumer()

    val line = reader.readLine()!!
    val parsed = parser.parse(line, reader, consumer)

    Truth.assertThat(parsed).isFalse()
    Truth.assertThat(consumer.messageEvents).isEmpty()
  }
}