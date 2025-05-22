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
import com.intellij.build.output.BuildOutputInstantReader
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
    doTest("""
    androidApp {
      productFlavors {
        productFlavor("type1") {
            matchingFallbacks = listOf("a")
        }
      }
    }
    """.trimIndent(), ::getDeclarativeBuildOutput) { file, iterator, reader ->
      verifyIssue(iterator,
                  reader.parentEventId,
                  getDeclarativeBuildIssueDescription(project.basePath + "/path/build.gradle.dcl"),
                  4,
                  9,
                  file)
    }
  }

  @Test
  @RunsInEdt
  fun testDeclarativeTreeError() {
    doTest("""
    androidApp {
      produ{}ctFlavors {
      }
    }
    """.trimIndent(), ::getFailureBuildingLanguageTree) { file, iterator, reader ->
      verifyIssue(iterator,
                  reader.parentEventId,
                  getFailureBuildingLanguageTreeDescription(project.basePath + "/path/build.gradle.dcl"),
                  2,
                  10,
                  file)
    }
  }

  @Test
  @RunsInEdt
  fun testMultipleSubjectErrors() {
    doTest("""
    androidApp {
      bui{}ldFeatures {
      }
      namespace = 1
    }
    """.trimIndent(), ::getMultipleSubject) { file, iterator, reader ->
      verifyIssue(iterator, reader.parentEventId, getMultipleSubject1(file.path), 2, 10, file)
      verifyIssue(iterator, reader.parentEventId, getMultipleSubject2(file.path), 4, 5, file)
    }
  }

  @Test
  @RunsInEdt
  fun testMultipleIssueErrors() {
    doTest("""
    androidApp {
      namespace = 1
      defaultConfig {
        minSdk = "24"
      }
    }
    """.trimIndent(), ::getMultipleIssueOutput) { file, iterator, reader ->
      verifyIssue(iterator, reader.parentEventId, getMultipleIssue1(file.path), 2, 5, file)
      verifyIssue(iterator, reader.parentEventId, getMultipleIssue2(file.path), 4, 9, file)
    }
  }

  private fun doTest(content: String,
                     buildOutput: (String) -> String,
                     assert: (VirtualFile, Iterator<MessageEvent>, BuildOutputInstantReader) -> Unit) {
    val file = createFile("path", "build.gradle.dcl", content)
    val parser = DeclarativeErrorParser()
    val reader = TestBuildOutputInstantReader(Splitter.on("\n").split(buildOutput(file!!.path)).toList())
    val consumer = TestMessageEventConsumer()

    val line = reader.readLine()!!
    val parsed = parser.parse(line, reader, consumer)

    Truth.assertThat(parsed).isTrue()
    val iterator = consumer.messageEvents.filterIsInstance<MessageEvent>().listIterator()
    assert(file, iterator, reader)
  }

  private fun verifyIssue(iterator: Iterator<MessageEvent>, parentId: Any, description: String, line: Int, column: Int, file: VirtualFile) {
    iterator.next().let {
      Truth.assertThat(it.parentId).isEqualTo(parentId)
      Truth.assertThat(it.message).isEqualTo("Declarative project configure issue")
      Truth.assertThat(it.kind).isEqualTo(MessageEvent.Kind.ERROR)
      Truth.assertThat(it.description).isEqualTo(description)
      Truth.assertThat(it.getNavigatable(project)).isInstanceOf(OpenFileDescriptor::class.java)
      (it.getNavigatable(project) as OpenFileDescriptor).let { ofd ->
        Truth.assertThat(ofd.line).isEqualTo(line)
        Truth.assertThat(ofd.column).isEqualTo(column)
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
    private fun getDeclarativeBuildOutput(absolutePath: String): String = """
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

    private fun getDeclarativeBuildIssueDescription(absolutePath: String) = """
    Failed to interpret declarative file '$absolutePath'
          4:9: unresolved reference 'matchingFallbacks'
    """.trimIndent()

    private fun getFailureBuildingLanguageTree(absolutePath: String): String = """
FAILURE: Build failed with an exception.

* What went wrong:
A problem occurred configuring project ':app'.
> Failed to interpret the declarative DSL file '$absolutePath':
    Failures in building the language tree:
      2:10: unsupported language feature: InfixFunctionCall

* Try:
> Run with --info or --debug option to get more log output.
> Run with --scan to get full insights.
> Get more help at https://help.gradle.org.
 """""".trimIndent()
  private fun getFailureBuildingLanguageTreeDescription(absolutePath: String) = """
    Failed to interpret declarative file '$absolutePath'
          2:10: unsupported language feature: InfixFunctionCall
    """.trimIndent()
  }

  private fun getMultipleSubject(absolutePath: String) =
    """
FAILURE: Build failed with an exception.

* What went wrong:
A problem occurred configuring project ':app'.
> Failed to interpret the declarative DSL file '$absolutePath':
    Failures in building the language tree:
      2:10: unsupported language feature: InfixFunctionCall
    Failures in resolution:
      4:5: assignment type mismatch, expected 'String', got 'Int'


* Try:
> Run with --info or --debug option to get more log output.
> Run with --scan to get full insights.
> Get more help at https://help.gradle.org.
    """.trimIndent()

  private fun getMultipleSubject1(absolutePath: String) =
    """
      Failed to interpret declarative file '$absolutePath'
            2:10: unsupported language feature: InfixFunctionCall
    """.trimIndent()

  private fun getMultipleSubject2(absolutePath: String) =
    """
      Failed to interpret declarative file '$absolutePath'
            4:5: assignment type mismatch, expected 'String', got 'Int'
    """.trimIndent()

  private fun getMultipleIssueOutput(absolutePath: String) =
    """
FAILURE: Build failed with an exception.

* What went wrong:
A problem occurred configuring project ':app'.
> Failed to interpret the declarative DSL file '$absolutePath':
    Failures in resolution:
      2:5: assignment type mismatch, expected 'String', got 'Int'
      4:9: assignment type mismatch, expected 'Int', got 'String'


* Try:
> Run with --info or --debug option to get more log output.
> Run with --scan to get full insights.
> Get more help at https://help.gradle.org.
    """.trimIndent()

  private fun getMultipleIssue1(absolutePath: String) =
    """
      Failed to interpret declarative file '$absolutePath'
            2:5: assignment type mismatch, expected 'String', got 'Int'
    """.trimIndent()

  private fun getMultipleIssue2(absolutePath: String) =
    """
      Failed to interpret declarative file '$absolutePath'
            4:9: assignment type mismatch, expected 'Int', got 'String'
    """.trimIndent()
}