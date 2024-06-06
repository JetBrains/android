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

import com.android.tools.idea.Projects
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.base.Charsets
import com.google.common.base.Splitter
import com.google.common.truth.Truth
import com.intellij.build.events.MessageEvent
import com.intellij.build.events.impl.BuildIssueEventImpl
import com.intellij.build.issue.BuildIssue
import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.Navigatable
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
  fun testTomlErrorWithFileParsed() {
    val buildOutput = getVersionCatalogLibsBuildOutput("/arbitrary/path/to/file.versions.toml")

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
      Truth.assertThat(it.description).isEqualTo(getVersionCatalogLibsBuildIssueDescription("/arbitrary/path/to/file.versions.toml"))
      Truth.assertThat(it.getNavigatable(project)).isNull()
    }
  }

  @Test
  @RunsInEdt
  fun testTomlAliasErrorParsedAndNavigable() {
    doTest("libs", 1, 0,
           """
           [libraries]
           a = "group:name:1.0"
           """.trimIndent(),
           { getVersionCatalogAliasFailureBuildOutput() },
           { getVersionCatalogLibsBuildAliasIssueDescription() }
    )
  }

  @Test
  @RunsInEdt
  fun testTomlTopLevelCatalogIssue() {
    doTest("libs", 0, 0,
           """
        [librariesa]
        junit = { group = "junit", name = "junit", version = "4.0" }
      """.trimIndent(),
           { getVersionCatalogTableMisspelOutput() },
           { getVersionCatalogTableMisspelDescription() }
    )
  }

  @Test
  @RunsInEdt
  fun testTomlAliasDuplication() {
    val (gradleDir, file) = createCatalog("libs",
                             """
          [versions]
          coreKtx = "1.10.1"
          [libraries]
          androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
          androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
          androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
          """.trimIndent()
    )
    try {
      val buildOutput = getVersionCatalogDuplicationAliasBuildOutput(project.basePath!!)

      val parser = TomlErrorParser()
      val reader = TestBuildOutputInstantReader(Splitter.on("\n").split(buildOutput).toList())
      val consumer = TestMessageEventConsumer()

      val line = reader.readLine()!!
      val parsed = parser.parse(line, reader, consumer)
      Truth.assertThat(parsed).isTrue()

      class BuildIssueTest(val logicalLine: Int, val logicalColumn: Int) : BuildIssue {
        override val description: String = getVersionCatalogDuplicateAliasDescription(project.basePath!!)
        override val quickFixes: List<BuildIssueQuickFix> = listOf()
        override val title: String = "Invalid TOML catalog definition."
        override fun getNavigatable(project: Project): Navigatable {
          return OpenFileDescriptor(project, file!!, logicalLine, logicalColumn)
        }
      }

      val expected = arrayOf(
        BuildIssueEventImpl(reader.parentEventId, BuildIssueTest(13, 0), MessageEvent.Kind.ERROR),
        BuildIssueEventImpl(reader.parentEventId, BuildIssueTest(14, 0), MessageEvent.Kind.ERROR)
      )
      val output = consumer.messageEvents.filterIsInstance<MessageEvent>()
      Truth.assertThat(output).hasSize(2)
      expected.zip(output).forEach {
        Truth.assertThat(it.first.parentId).isEqualTo(it.second.parentId)
        Truth.assertThat(it.first.message).isEqualTo(it.second.message)
        Truth.assertThat(it.first.kind).isEqualTo(it.second.kind)
        Truth.assertThat(it.first.description).isEqualTo(it.second.description)

        (it.first.getNavigatable(project) as OpenFileDescriptor to
          it.second.getNavigatable(project) as OpenFileDescriptor).let { ofd ->
          Truth.assertThat(ofd.first.line).isEqualTo(ofd.second.line)
          Truth.assertThat(ofd.first.column).isEqualTo(ofd.second.column)
          Truth.assertThat(ofd.first.file).isEqualTo(ofd.second.file)
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
  fun testTomlUnparsable() {
    val buildOutput = getVersionCatalogTableMisspelOutput2()

    val parser = TomlErrorParser()
    val reader = TestBuildOutputInstantReader(Splitter.on("\n").split(buildOutput).toList())
    val consumer = TestMessageEventConsumer()

    val line = reader.readLine()!!
    val parsed = parser.parse(line, reader, consumer)

    Truth.assertThat(parsed).isFalse()
    Truth.assertThat(consumer.messageEvents.filterIsInstance<MessageEvent>()).isEmpty()
  }

  @Test
  @RunsInEdt
  fun testTomlErrorParsedAndNavigable() {
    doTest("libs", 10, 18, "",
           { getVersionCatalogLibsBuildOutput() },
           { getVersionCatalogLibsBuildIssueDescription() }
    )
  }

  @Test
  @RunsInEdt
  fun testTomlErrorWithFileParsedAndNavigable() {
    doTest("arbitraty", 10, 18, "",
           { path -> getVersionCatalogLibsBuildOutput(path) },
           { path -> getVersionCatalogLibsBuildIssueDescription(path) }
    )
  }

  private fun doTest(tomlPrefix: String,
                     lineOutput: Int,
                     columnOutput: Int,
                     tomlContent: String = "",
                     buildOutput: (String) -> String,
                     description: (String) -> String) {
    val (gradleDir, file) = createCatalog(tomlPrefix, tomlContent)
    try {
      val absolutePath = file!!.toNioPath().toAbsolutePath().toString()

      val parser = TomlErrorParser()
      val reader = TestBuildOutputInstantReader(Splitter.on("\n").split(buildOutput(absolutePath)).toList())
      val consumer = TestMessageEventConsumer()

      val line = reader.readLine()!!
      val parsed = parser.parse(line, reader, consumer)

      Truth.assertThat(parsed).isTrue()
      consumer.messageEvents.filterIsInstance<MessageEvent>().single().let {
        Truth.assertThat(it.parentId).isEqualTo(reader.parentEventId)
        Truth.assertThat(it.message).isEqualTo("Invalid TOML catalog definition.")
        Truth.assertThat(it.kind).isEqualTo(MessageEvent.Kind.ERROR)
        Truth.assertThat(it.description).isEqualTo(description(absolutePath))
        Truth.assertThat(it.getNavigatable(project)).isInstanceOf(OpenFileDescriptor::class.java)
        (it.getNavigatable(project) as OpenFileDescriptor).let { ofd ->
          Truth.assertThat(ofd.line).isEqualTo(lineOutput)
          Truth.assertThat(ofd.column).isEqualTo(columnOutput)
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

  private fun createCatalog(tomlPrefix: String, content: String): Pair<VirtualFile?, VirtualFile?> {
    var file: VirtualFile? = null
    var gradleDir: VirtualFile? = null
    runWriteAction {
      gradleDir = getRootFolder()?.createChildDirectory(this, "gradle")
      file = gradleDir?.findOrCreateChildData(this, tomlPrefix + ".versions.toml")
      file?.setBinaryContent(content.toByteArray(Charsets.UTF_8))
    }
    return gradleDir to file
  }

  private fun getRootFolder() = VfsUtil.findFile(Projects.getBaseDirPath(project).toPath(), true)

  companion object {
    fun getVersionCatalogLibsBuildOutput(absolutePath: String? = null): String = """
FAILURE: Build failed with an exception.

* What went wrong:
org.gradle.api.InvalidUserDataException: Invalid TOML catalog definition:
  - Problem: In version catalog libs, parsing failed with 1 error.
    
    Reason: ${absolutePath?.let { "In file '$it' at" } ?: "At"} line 11, column 19: Unexpected '/', expected a newline or end-of-input.
    
    Possible solution: Fix the TOML file according to the syntax described at https://toml.io.
    
    Please refer to https://docs.gradle.org/7.4/userguide/version_catalog_problems.html#toml_syntax_error for more details about this problem.
> Invalid TOML catalog definition:
    - Problem: In version catalog libs, parsing failed with 1 error.
      
      Reason: ${absolutePath?.let { "Int file '$it' at" } ?: "At"} line 11, column 19: Unexpected '/', expected a newline or end-of-input.
      
      Possible solution: Fix the TOML file according to the syntax described at https://toml.io.
      
      Please refer to https://docs.gradle.org/7.4/userguide/version_catalog_problems.html#toml_syntax_error for more details about this problem.

* Try:
> Run with --stacktrace option to get the stack trace.
> Run with --info or --debug option to get more log output.
> Run with --scan to get full insights.

* Get more help at https://help.gradle.org
      """.trimIndent()

    fun getVersionCatalogLibsBuildIssueDescription(absolutePath: String? = null): String = """
Invalid TOML catalog definition.
  - Problem: In version catalog libs, parsing failed with 1 error.
    
    Reason: ${absolutePath?.let { "In file '$it' at" } ?: "At"} line 11, column 19: Unexpected '/', expected a newline or end-of-input.
    
    Possible solution: Fix the TOML file according to the syntax described at https://toml.io.
    
    Please refer to https://docs.gradle.org/7.4/userguide/version_catalog_problems.html#toml_syntax_error for more details about this problem.
      """.trimIndent()
  }

  fun getVersionCatalogTableMisspelOutput() = """
     FAILURE: Build failed with an exception.
     
     * What went wrong:
     org.gradle.api.InvalidUserDataException: Invalid TOML catalog definition:
       - Problem: In version catalog libs, unknown top level elements [librariesa]
         
         Reason: TOML file contains an unexpected top-level element.
         
         Possible solution: Make sure the top-level elements of your TOML file is one of 'bundles', 'libraries', 'metadata', 'plugins', or 'versions'.
         
         For more information, please refer to https://docs.gradle.org/8.7/userguide/version_catalog_problems.html#toml_syntax_error in the Gradle documentation.
     > Invalid TOML catalog definition:
         - THIS PIECE WAS CHANGED TO PROOF THAT PARSER MUST IGNORE IT
          """.trimIndent()

  fun getVersionCatalogTableMisspelOutput2() = """
     FAILURE: Build failed with an exception.
     
     * What went wrong:
     org.gradle.api.InvalidUserDataException: Invalid TOML catalog definition:
       - Problem: In version catalog libs, SOME RANDOM UNPARSABLE TEXT
         
         Reason: TOML file contains an unexpected top-level element.
         
         Possible solution: Make sure the top-level elements of your TOML file is one of 'bundles', 'libraries', 'metadata', 'plugins', or 'versions'.
         
         For more information, please refer to https://docs.gradle.org/8.7/userguide/version_catalog_problems.html#toml_syntax_error in the Gradle documentation.
     > Invalid TOML catalog definition:
         - Problem: In version catalog libs, unknown top level elements [librariesa] 
          """.trimIndent()

  fun getVersionCatalogTableMisspelDescription() = """
      Invalid TOML catalog definition.
        - Problem: In version catalog libs, unknown top level elements [librariesa]
          
          Reason: TOML file contains an unexpected top-level element.
          
          Possible solution: Make sure the top-level elements of your TOML file is one of 'bundles', 'libraries', 'metadata', 'plugins', or 'versions'.
          
          For more information, please refer to https://docs.gradle.org/8.7/userguide/version_catalog_problems.html#toml_syntax_error in the Gradle documentation.
          """.trimIndent()


  fun getVersionCatalogDuplicationAliasBuildOutput(baseDir: String): String = """
    FAILURE: Build failed with an exception.

    * What went wrong:
    org.gradle.api.InvalidUserDataException: Invalid TOML catalog definition:
      - Problem: In version catalog libs, parsing failed with 3 errors.
        
        Reason: In file '${baseDir}/gradle/libs.versions.toml' at line 14, column 1: androidx-core-ktx previously defined at line 13, column 1
        In file '${baseDir}/gradle/libs.versions.toml' at line 15, column 1: androidx-core-ktx previously defined at line 13, column 1
        
        Possible solution: Fix the TOML file according to the syntax described at https://toml.io.
        
        For more information, please refer to https://docs.gradle.org/8.7/userguide/version_catalog_problems.html#toml_syntax_error in the Gradle documentation.
    > Invalid TOML catalog definition:
        - Problem: In version catalog libs, parsing failed with 3 errors.
          
          Reason: In file '${baseDir}/gradle/libs.versions.toml' at line 14, column 1: androidx-core-ktx previously defined at line 13, column 1
          In file '${baseDir}/gradle/libs.versions.toml' at line 15, column 1: androidx-core-ktx previously defined at line 13, column 1
          
          Possible solution: Fix the TOML file according to the syntax described at https://toml.io.
          
          For more information, please refer to https://docs.gradle.org/8.7/userguide/version_catalog_problems.html#toml_syntax_error in the Gradle documentation.

    * Try:
    > Run with --info or --debug option to get more log output.
    > Run with --scan to get full insights.
    > Get more help at https://help.gradle.org.
  """.trimIndent()

  fun getVersionCatalogDuplicateAliasDescription(baseDir: String): String = """
    Invalid TOML catalog definition.
      - Problem: In version catalog libs, parsing failed with 3 errors.
        
        Reason: In file '${baseDir}/gradle/libs.versions.toml' at line 14, column 1: androidx-core-ktx previously defined at line 13, column 1
        In file '${baseDir}/gradle/libs.versions.toml' at line 15, column 1: androidx-core-ktx previously defined at line 13, column 1
        
        Possible solution: Fix the TOML file according to the syntax described at https://toml.io.
        
        For more information, please refer to https://docs.gradle.org/8.7/userguide/version_catalog_problems.html#toml_syntax_error in the Gradle documentation.
  """.trimIndent()

  fun getVersionCatalogAliasFailureBuildOutput(): String = """
FAILURE: Build failed with an exception.

* What went wrong:
org.gradle.api.InvalidUserDataException: Invalid catalog definition:
  - Problem: In version catalog libs, invalid library alias 'a'.
    
    Reason: Library aliases must match the following regular expression: [a-z]([a-zA-Z0-9_.\-])+.
    
    Possible solution: Make sure the alias matches the [a-z]([a-zA-Z0-9_.\-])+ regular expression.
    
    For more information, please refer to https://docs.gradle.org/8.2/userguide/version_catalog_problems.html#invalid_alias_notation in the Gradle documentation.
> Invalid catalog definition:
    - Problem: In version catalog libs, invalid library alias 'a'.
      
      Reason: Library aliases must match the following regular expression: [a-z]([a-zA-Z0-9_.\-])+.
      
      Possible solution: Make sure the alias matches the [a-z]([a-zA-Z0-9_.\-])+ regular expression.
      
      For more information, please refer to https://docs.gradle.org/8.2/userguide/version_catalog_problems.html#invalid_alias_notation in the Gradle documentation.

* Try:
> Run with --info or --debug option to get more log output.
> Run with --scan to get full insights.
> Get more help at https://help.gradle.org.
  """.trimIndent()

  fun getVersionCatalogLibsBuildAliasIssueDescription(): String = """
Invalid catalog definition.
  - Problem: In version catalog libs, invalid library alias 'a'.
    
    Reason: Library aliases must match the following regular expression: [a-z]([a-zA-Z0-9_.\-])+.
    
    Possible solution: Make sure the alias matches the [a-z]([a-zA-Z0-9_.\-])+ regular expression.
    
    For more information, please refer to https://docs.gradle.org/8.2/userguide/version_catalog_problems.html#invalid_alias_notation in the Gradle documentation.
      """.trimIndent()

}