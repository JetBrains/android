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

import com.android.tools.idea.Projects
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.base.Charsets
import com.google.common.base.Splitter
import com.google.common.truth.Truth
import com.intellij.build.events.MessageEvent
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import org.junit.Rule
import org.junit.Test

class DeclarativeErrorParserTest {
  private val projectRule = AndroidProjectRule.onDisk()
  private val edtRule = EdtRule()

  @get:Rule
  val ruleChain = RuleChain(projectRule, edtRule)

  val project by lazy { projectRule.project }

  @Test
  @RunsInEdt
  fun testDeclarativeErrorParsed() {
    val file = createFile("path","build.gradle.dcl", """
    androidApp {
      productFlavors {
        productFlavor("type1") {
            matchingFallbacks = listOf("a")
        }
      }
    }
    """.trimIndent())
    val buildOutput = getDeclarativeBuildOutput(project.basePath + "/path/build.gradle.dcl")

    val parser = DeclarativeErrorParser()
    val reader = TestBuildOutputInstantReader(Splitter.on("\n").split(buildOutput).toList())
    val consumer = TestMessageEventConsumer()

    val line = reader.readLine()!!
    val parsed = parser.parse(line, reader, consumer)

    Truth.assertThat(parsed).isTrue()
    consumer.messageEvents.filterIsInstance<MessageEvent>().single().let {
      Truth.assertThat(it.parentId).isEqualTo(reader.parentEventId)
      Truth.assertThat(it.message).isEqualTo("Declarative project configure issue")
      Truth.assertThat(it.kind).isEqualTo(MessageEvent.Kind.ERROR)
      Truth.assertThat(it.description).isEqualTo(geDeclarativeBuildIssueDescription(project.basePath + "/path/build.gradle.dcl"))
      Truth.assertThat(it.getNavigatable(project)).isInstanceOf(OpenFileDescriptor::class.java)
      (it.getNavigatable(project) as OpenFileDescriptor).let { ofd ->
        Truth.assertThat(ofd.line).isEqualTo(4)
        Truth.assertThat(ofd.column).isEqualTo(9)
        Truth.assertThat(ofd.file).isEqualTo(file)
      }
    }
  }

  private fun createFile(folder: String, fileName: String, content: String): VirtualFile? {
    var file: VirtualFile? = null
    var dir: VirtualFile? = null
    runWriteAction {
      dir = VfsUtil.findFile(Projects.getBaseDirPath(project).toPath(), true)?.createChildDirectory(this, folder)
      file = dir?.findOrCreateChildData(this, fileName)
      file?.setBinaryContent(content.toByteArray(Charsets.UTF_8))
    }
    return file
  }

  companion object {
    fun getDeclarativeBuildOutput(absolutePath: String): String = """
FAILURE: Build failed with an exception.

* What went wrong:
A problem occurred configuring project ':app'.
> Failed to interpret the declarative DSL file '$absolutePath':
    Failures in resolution:
      4:9: unresolved reference 'matchingFallbacks'
      4:9: unresolved assignment target


* Try:
> Run with --info or --debug option to get more log output.
> Run with --scan to get full insights.
> Get more help at https://help.gradle.org.

* Exception is:
      """.trimIndent()

    fun geDeclarativeBuildIssueDescription(absolutePath: String) = """
    Failed to interpret declarative file '$absolutePath'
          4:9: unresolved reference 'matchingFallbacks'
    """.trimIndent()
  }
}