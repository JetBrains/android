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

import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.base.Splitter
import com.google.common.truth.Truth
import com.intellij.build.events.MessageEvent
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import org.junit.Rule
import org.junit.Test

class TomlErrorParserTest {
  private val projectRule = AndroidProjectRule.onDisk()
  private val edtRule = EdtRule()

  @get:Rule
  val ruleChain = RuleChain(projectRule, edtRule)

  val project by lazy { projectRule.project }

  @Test
  fun testTomlErrorParsed() {
    val buildOutput = getVersionCatalogLibsBuildOutput()

    val parser = TomlErrorParser()
    val reader = TestBuildOutputInstantReader(Splitter.on("\n").split(buildOutput).toList())
    val consumer = TestMessageEventConsumer()

    val line = reader.readLine()!!
    val parsed = parser.parse(line, reader, consumer)

    Truth.assertThat(parsed).isTrue()
    consumer.messageEvents.filterIsInstance<MessageEvent>().single().let {
      Truth.assertThat(it.parentId).isEqualTo(reader.parentEventId)
      Truth.assertThat(it.message).isEqualTo("Invalid TOML catalog definition.")
      Truth.assertThat(it.kind).isEqualTo(MessageEvent.Kind.ERROR)
      Truth.assertThat(it.description).isEqualTo(getVersionCatalogLibsBuildIssueDescription())
      Truth.assertThat(it.getNavigatable(project)).isNull()
    }
  }

  @Test
  @RunsInEdt
  fun testTomlErrorParsedAndNavigable() {
    var file: VirtualFile? = null
    var gradleDir: VirtualFile? = null
    runWriteAction {
      gradleDir = project.baseDir?.createChildDirectory(this, "gradle")
      file = gradleDir?.findOrCreateChildData(this, "libs.versions.toml")
    }
    try {
      val buildOutput = getVersionCatalogLibsBuildOutput()

      val parser = TomlErrorParser()
      val reader = TestBuildOutputInstantReader(Splitter.on("\n").split(buildOutput).toList())
      val consumer = TestMessageEventConsumer()

      val line = reader.readLine()!!
      val parsed = parser.parse(line, reader, consumer)

      Truth.assertThat(parsed).isTrue()
      consumer.messageEvents.filterIsInstance<MessageEvent>().single().let {
        Truth.assertThat(it.parentId).isEqualTo(reader.parentEventId)
        Truth.assertThat(it.message).isEqualTo("Invalid TOML catalog definition.")
        Truth.assertThat(it.kind).isEqualTo(MessageEvent.Kind.ERROR)
        Truth.assertThat(it.description).isEqualTo(getVersionCatalogLibsBuildIssueDescription())
        Truth.assertThat(it.getNavigatable(project)).isInstanceOf(OpenFileDescriptor::class.java)
        (it.getNavigatable(project) as OpenFileDescriptor).let { ofd ->
          Truth.assertThat(ofd.line).isEqualTo(10)
          Truth.assertThat(ofd.column).isEqualTo(18)
          Truth.assertThat(ofd.file).isEqualTo(file)
        }
      }
    }
    finally {
      runWriteAction {
        file?.delete(this)
        gradleDir?.delete(this)
      }
    }
  }

  @Test
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

    val parser = TomlErrorParser()
    val reader = TestBuildOutputInstantReader(Splitter.on("\n").split(buildOutput).toList())
    val consumer = TestMessageEventConsumer()

    val line = reader.readLine()!!
    val parsed = parser.parse(line, reader, consumer)

    Truth.assertThat(parsed).isFalse()
    Truth.assertThat(consumer.messageEvents).isEmpty()
  }

  companion object {
    fun getVersionCatalogLibsBuildOutput(): String = """
FAILURE: Build failed with an exception.

* What went wrong:
org.gradle.api.InvalidUserDataException: Invalid TOML catalog definition:
  - Problem: In version catalog libs, parsing failed with 1 error.
    
    Reason: At line 11, column 19: Unexpected '/', expected a newline or end-of-input.
    
    Possible solution: Fix the TOML file according to the syntax described at https://toml.io.
    
    Please refer to https://docs.gradle.org/7.4/userguide/version_catalog_problems.html#toml_syntax_error for more details about this problem.
> Invalid TOML catalog definition:
    - Problem: In version catalog libs, parsing failed with 1 error.
      
      Reason: At line 11, column 19: Unexpected '/', expected a newline or end-of-input.
      
      Possible solution: Fix the TOML file according to the syntax described at https://toml.io.
      
      Please refer to https://docs.gradle.org/7.4/userguide/version_catalog_problems.html#toml_syntax_error for more details about this problem.

* Try:
> Run with --stacktrace option to get the stack trace.
> Run with --info or --debug option to get more log output.
> Run with --scan to get full insights.

* Get more help at https://help.gradle.org
      """.trimIndent()

    fun getVersionCatalogLibsBuildIssueDescription(): String = """
Invalid TOML catalog definition.
  - Problem: In version catalog libs, parsing failed with 1 error.
    
    Reason: At line 11, column 19: Unexpected '/', expected a newline or end-of-input.
    
    Possible solution: Fix the TOML file according to the syntax described at https://toml.io.
    
    Please refer to https://docs.gradle.org/7.4/userguide/version_catalog_problems.html#toml_syntax_error for more details about this problem.
      """.trimIndent()
  }
}