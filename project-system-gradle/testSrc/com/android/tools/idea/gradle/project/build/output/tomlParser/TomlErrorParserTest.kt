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
package com.android.tools.idea.gradle.project.build.output.tomlParser

import com.android.tools.idea.Projects
import com.android.tools.idea.gradle.dsl.model.EP_NAME
import com.android.tools.idea.gradle.dsl.model.VersionCatalogFilesModel
import com.android.tools.idea.gradle.project.build.events.GradleErrorQuickFixProvider
import com.android.tools.idea.gradle.project.build.output.TestMessageEventConsumer
import com.android.tools.idea.gradle.project.sync.idea.issues.DescribedBuildIssueQuickFix
import com.android.tools.idea.project.hyperlink.SyncMessageHyperlink
import com.android.tools.idea.project.messages.SyncMessage
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.base.Charsets
import com.google.common.truth.Truth
import com.intellij.build.BuildProgressListener
import com.intellij.build.events.BuildEvent
import com.intellij.build.events.BuildIssueEvent
import com.intellij.build.events.MessageEvent
import com.intellij.build.events.impl.BuildIssueEventImpl
import com.intellij.build.issue.BuildIssue
import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.build.output.BuildOutputInstantReaderImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemOutputParserProvider
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.Navigatable
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.registerExtension
import org.jetbrains.annotations.SystemIndependent
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.sequences.forEach

class TomlErrorParserTest {
  private val projectRule = AndroidProjectRule.onDisk()
  private val edtRule = EdtRule()

  @get:Rule
  val ruleChain = RuleChain(projectRule, edtRule)

  val project by lazy { projectRule.project }
  private var catalogMap = mapOf<String, String>()

  private val service = object: VersionCatalogFilesModel {
    override fun getCatalogNameToFileMapping(project: Project): Map<String, String> =
      catalogMap.mapValues { project.basePath + "/" + it.value }
    override fun getCatalogNameToFileMapping(module: Module): Map<String, String>  =
      catalogMap.mapValues { project.basePath + "/" + it.value }
  }

  @Before
  fun setUp() {
    ApplicationManager.getApplication().registerExtension(
      EP_NAME, service, projectRule.fixture.testRootDisposable
    )
    catalogMap = mapOf("libs" to "gradle/libs.versions.toml", "libs2" to "gradle/libs2.versions.toml")
  }

  @Test
  fun testTomlErrorParsed() {
    val buildOutput = getVersionCatalogLibsBuildOutput()

    val taskId = ExternalSystemTaskId.create(GradleConstants.SYSTEM_ID, ExternalSystemTaskType.EXECUTE_TASK, projectRule.project)
    val parentEventId = "Test Id"
    val consumer = consumeOutput(buildOutput, taskId, parentEventId)

    consumer.messageEvents.filterIsInstance<MessageEvent>().single().let {
      Truth.assertThat(it.parentId).isEqualTo(parentEventId)
      Truth.assertThat(it.message).isEqualTo("Invalid TOML catalog definition.")
      Truth.assertThat(it.kind).isEqualTo(MessageEvent.Kind.ERROR)
      Truth.assertThat(it.description).isEqualTo(getVersionCatalogLibsBuildIssueDescription())
      Truth.assertThat(it.getNavigatable(project)).isNull()
    }
  }

  @Test
  fun testTomlErrorWithFileParsed() {
    val buildOutput = getVersionCatalogLibsBuildOutput("/arbitrary/path/to/file.versions.toml")

    val taskId = ExternalSystemTaskId.create(GradleConstants.SYSTEM_ID, ExternalSystemTaskType.EXECUTE_TASK, projectRule.project)
    val parentEventId = "Test Id"
    val consumer = consumeOutput(buildOutput, taskId, parentEventId)

    consumer.messageEvents.filterIsInstance<MessageEvent>().single().let {
      Truth.assertThat(it.parentId).isEqualTo(parentEventId)
      Truth.assertThat(it.message).isEqualTo("Invalid TOML catalog definition.")
      Truth.assertThat(it.kind).isEqualTo(MessageEvent.Kind.ERROR)
      Truth.assertThat(it.description).isEqualTo(getVersionCatalogLibsBuildIssueDescription("/arbitrary/path/to/file.versions.toml"))
      Truth.assertThat(it.getNavigatable(project)).isNull()
    }
  }

  @Test
  fun testTomlErrorWithFileParsedByWrapperWithAdditionalQickfix() {
    registerAdditionalQuickFixProvider()
    val buildOutput = getVersionCatalogLibsBuildOutput("/arbitrary/path/to/file.versions.toml")

    val taskId = ExternalSystemTaskId.create(GradleConstants.SYSTEM_ID, ExternalSystemTaskType.EXECUTE_TASK, projectRule.project)
    val parentEventId = "Test Id"
    val consumer = consumeOutput(buildOutput, taskId, parentEventId)

    consumer.messageEvents.filterIsInstance<MessageEvent>().single().let {
      Truth.assertThat(it.parentId).isEqualTo(parentEventId)
      Truth.assertThat(it.message).isEqualTo("Invalid TOML catalog definition.")
      Truth.assertThat(it.kind).isEqualTo(MessageEvent.Kind.ERROR)
      Truth.assertThat(it.description).isEqualTo(getVersionCatalogLibsBuildIssueDescription(
        "/arbitrary/path/to/file.versions.toml") + "\n<a href=\"com.plugin.gradle.quickfix\">Additional quickfix link</a>")
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
  fun testTomlWrongReference() {
    doTest("libs", 1, 0,
           """
           [libraries]
           androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "reference" }
           """.trimIndent(),
           { getVersionCatalogReferenceIssueBuildOutput() },
           { getVersionCatalogReferenceIssueDescription() }
    )
  }

  @Test
  @RunsInEdt
  fun testTomlWrongReferenceMultiCatalog() {
    doTest("libs2", 1, 0,
           """
           [libraries]
           androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "reference" }
           """.trimIndent(),
           { getVersionCatalogReferenceIssueBuildOutput("libs2") },
           { getVersionCatalogReferenceIssueDescription("libs2") }
    )
  }

  @Test
  @RunsInEdt
  fun testTomlWrongReferenceInPlugin() {
    doTest("libs", 1, 0,
           """
           [plugins]
           android-application = { id = "com.android.application", version.ref = "reference" }
           """.trimIndent(),
           { getVersionCatalogReferenceInPluginIssueBuildOutput() },
           { getVersionCatalogReferenceInPluginIssueDescription() }
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
  fun testTomlAliasIssue() {
    doTest("libs", 1, 0,
           """
        [plugins]
        plugin = { version = "4.0" }
      """.trimIndent(),
           { getVersionCatalogAliasProblem() },
           { getVersionCatalogAliasDescription() }
    )
  }


  @Test
  @RunsInEdt
  fun testTomlAliasIssueMultiCatalog() {
    createCatalog("libs", "[libraries]") //create empty default catalog
    doTest("libs2", 1, 0,
           """
        [plugins]
        plugin = { version = "4.0" }
      """.trimIndent(),
           { getVersionCatalogAliasProblem() },
           { getVersionCatalogAliasDescription() }
    )
  }

  @Test
  @RunsInEdt
  fun testTomlLibraryWrongProperty() {
    doTest("libs", 1, 22,
           """
        [libraries]
        androidx-core-ktx = { group1 = "androidx.core", name = "core-ktx", version = "1.0" }
      """.trimIndent(),
           { getVersionCatalogWrongElementProblem() },
           { getVersionCatalogWrongElementDescription() }
    )
  }

  @Test
  @RunsInEdt
  fun testTomlBundleWrongReference() {
    doTest("libs", 1, 10,
           """
        [bundles]
        bundle = ["aaa"]
      """.trimIndent(),
           { getVersionCatalogWrongBundleElementProblem() },
           { getVersionCatalogWrongBundleElementDescription() }
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

      val taskId = ExternalSystemTaskId.create(GradleConstants.SYSTEM_ID, ExternalSystemTaskType.EXECUTE_TASK, projectRule.project)
      val parentEventId = "Test Id"
      val consumer = consumeOutput(buildOutput, taskId, parentEventId)

      class BuildIssueTest(val logicalLine: Int, val logicalColumn: Int) : BuildIssue {
        override val description: String = getVersionCatalogDuplicateAliasDescription(project.basePath!!)
        override val quickFixes: List<BuildIssueQuickFix> = listOf()
        override val title: String = "Invalid TOML catalog definition."
        override fun getNavigatable(project: Project): Navigatable {
          return OpenFileDescriptor(project, file!!, logicalLine, logicalColumn)
        }
      }

      val expected = arrayOf(
        BuildIssueEventImpl(parentEventId, BuildIssueTest(13, 0), MessageEvent.Kind.ERROR),
        BuildIssueEventImpl(parentEventId, BuildIssueTest(14, 0), MessageEvent.Kind.ERROR)
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

    val taskId = ExternalSystemTaskId.create(GradleConstants.SYSTEM_ID, ExternalSystemTaskType.EXECUTE_TASK, projectRule.project)
    val parentEventId = "Test Id"
    val consumer = consumeOutput(buildOutput, taskId, parentEventId)

    //Not parsable by Toml parser so general message should be issued
    consumer.messageEvents.filterIsInstance<MessageEvent>().single().let {
      Truth.assertThat(it.parentId).isEqualTo(parentEventId)
      Truth.assertThat(it.message).isEqualTo("Invalid TOML catalog definition:")
      Truth.assertThat(it.kind).isEqualTo(MessageEvent.Kind.ERROR)
      Truth.assertThat(it).isNotInstanceOf(BuildIssueEvent::class.java)
    }
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
    catalogMap = mapOf() // check that it fall back to 'gradle/' path if no mapping is available
    doTest("arbitraty", 10, 18, "",
           { path -> getVersionCatalogLibsBuildOutput(path) },
           { path -> getVersionCatalogLibsBuildIssueDescription(path) }
    )
  }

  private fun consumeOutput(
    buildOutput: String,
    taskId: ExternalSystemTaskId,
    parentEventId: String
  ): TestMessageEventConsumer {
    val consumer = TestMessageEventConsumer()

    val progressListener = object : BuildProgressListener {
      override fun onEvent(buildId: Any, event: BuildEvent) {
        if (event is MessageEvent) {
          consumer.accept(event)
        }
      }
    }

    val parsers = ExternalSystemOutputParserProvider.EP_NAME.extensions.flatMap { it.getBuildOutputParsers(taskId) }
    val parser = BuildOutputInstantReaderImpl(taskId, parentEventId, progressListener, parsers)
    parser.disableActiveReading()
    buildOutput.lineSequence().forEach { parser.appendLine(it) }
    parser.closeAndGetFuture().join()
    return consumer
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
      val taskId = ExternalSystemTaskId.create(GradleConstants.SYSTEM_ID, ExternalSystemTaskType.EXECUTE_TASK, projectRule.project)
      val parentEventId = "Test Id"
      val output = buildOutput(absolutePath)
      val consumer = consumeOutput(output, taskId, parentEventId)

      consumer.messageEvents.filterIsInstance<MessageEvent>().single().let {
        Truth.assertThat(it.parentId).isEqualTo(parentEventId)
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

  private fun createCatalog(tomlPrefix: String, content: String): Pair<VirtualFile?, VirtualFile?> {
    var file: VirtualFile? = null
    var gradleDir: VirtualFile? = null
    runWriteAction {
      gradleDir = VfsUtil.createDirectoryIfMissing(getRootFolder(), "gradle")
      file = gradleDir?.findOrCreateChildData(this, tomlPrefix + ".versions.toml")
      file?.setBinaryContent(content.toByteArray(Charsets.UTF_8))
    }
    return gradleDir to file
  }

  private fun registerAdditionalQuickFixProvider() {
    val gradleErrorQuickFixProvider = object : GradleErrorQuickFixProvider {
      override fun createBuildIssueAdditionalQuickFix(buildEvent: BuildEvent, taskId: ExternalSystemTaskId): DescribedBuildIssueQuickFix? {
        return object: DescribedBuildIssueQuickFix {
          override val description: String
            get() = "Additional quickfix link"
          override val id: String
            get() = "com.plugin.gradle.quickfix"
        }
      }

      override fun createSyncMessageAdditionalLink(syncMessage: SyncMessage,
                                                   affectedModules: List<Module>,
                                                   buildFileMap: Map<Module, VirtualFile>,
                                                   rootProjectPath: @SystemIndependent String): SyncMessageHyperlink? {
        error("Should not be called in this test")
      }
    }
    ApplicationManager.getApplication().registerExtension(GradleErrorQuickFixProvider.EP_NAME, gradleErrorQuickFixProvider, projectRule.testRootDisposable)
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


  fun getVersionCatalogReferenceIssueBuildOutput(catalog: String = "libs"): String = """
FAILURE: Build failed with an exception.

* What went wrong:
org.gradle.api.InvalidUserDataException: Invalid catalog definition:
  - Problem: In version catalog $catalog, version reference 'reference' doesn't exist.
    
    Reason: Dependency 'androidx.core:core-ktx' references version 'reference' which doesn't exist.
    
    Possible solution: Declare 'reference' in the catalog.
    
    For more information, please refer to https://docs.gradle.org/8.7/userguide/version_catalog_problems.html#undefined_version_reference in the Gradle documentation.
> Invalid catalog definition:
    - Problem: In version catalog $catalog, version reference 'reference' doesn't exist.
      
      Reason: Dependency 'androidx.core:core-ktx' references version 'reference' which doesn't exist.
      
      Possible solution: Declare 'reference' in the catalog.
      
      For more information, please refer to https://docs.gradle.org/8.7/userguide/version_catalog_problems.html#undefined_version_reference in the Gradle documentation.

* Try:
> Run with --info or --debug option to get more log output.
> Run with --scan to get full insights.
> Get more help at https://help.gradle.org.
  """.trimIndent()

  fun getVersionCatalogReferenceIssueDescription(catalog: String = "libs"): String = """
Invalid catalog definition.
  - Problem: In version catalog $catalog, version reference 'reference' doesn't exist.
    
    Reason: Dependency 'androidx.core:core-ktx' references version 'reference' which doesn't exist.
    
    Possible solution: Declare 'reference' in the catalog.
    
    For more information, please refer to https://docs.gradle.org/8.7/userguide/version_catalog_problems.html#undefined_version_reference in the Gradle documentation.
      """.trimIndent()

fun getVersionCatalogReferenceInPluginIssueBuildOutput(): String = """
FAILURE: Build failed with an exception.

* What went wrong:
org.gradle.api.InvalidUserDataException: Invalid catalog definition:
  - Problem: In version catalog libs, version reference 'reference' doesn't exist.
    
    Reason: Plugin 'com.android.application' references version 'reference' which doesn't exist.
    
    Possible solution: Declare 'reference' in the catalog.
    
    For more information, please refer to https://docs.gradle.org/8.7/userguide/version_catalog_problems.html#undefined_version_reference in the Gradle documentation.
> Invalid catalog definition:
    - Problem: In version catalog libs, version reference 'reference' doesn't exist.
      
      Reason: Plugin 'com.android.application' references version 'reference' which doesn't exist.
      
      Possible solution: Declare 'reference' in the catalog.
      
      For more information, please refer to https://docs.gradle.org/8.7/userguide/version_catalog_problems.html#undefined_version_reference in the Gradle documentation.

* Try:
> Run with --info or --debug option to get more log output.
> Run with --scan to get full insights.
> Get more help at https://help.gradle.org.
  """.trimIndent()

fun getVersionCatalogReferenceInPluginIssueDescription(): String = """
Invalid catalog definition.
  - Problem: In version catalog libs, version reference 'reference' doesn't exist.
    
    Reason: Plugin 'com.android.application' references version 'reference' which doesn't exist.
    
    Possible solution: Declare 'reference' in the catalog.
    
    For more information, please refer to https://docs.gradle.org/8.7/userguide/version_catalog_problems.html#undefined_version_reference in the Gradle documentation.
      """.trimIndent()

}

fun getVersionCatalogAliasProblem(): String = """
FAILURE: Build failed with an exception.

* What went wrong:
org.gradle.api.InvalidUserDataException: Invalid TOML catalog definition:
  - Alias definition 'plugin' is invalid

    Reason: Id for plugin alias 'plugin' wasn't set.

    Possible solution: Add the 'id' element on alias 'plugin'.

    For more information, please refer to https://docs.gradle.org/8.7/userguide/version_catalog_problems.html#toml_syntax_error in the Gradle documentation.
> Invalid TOML catalog definition:
    - Alias definition 'plugin' is invalid

      Reason: Id for plugin alias 'plugin' wasn't set.

      Possible solution: Add the 'id' element on alias 'plugin'.

      For more information, please refer to https://docs.gradle.org/8.7/userguide/version_catalog_problems.html#toml_syntax_error in the Gradle documentation.

* Try:
> Run with --info or --debug option to get more log output.
> Run with --scan to get full insights.
> Get more help at https://help.gradle.org.
""".trimIndent()

fun getVersionCatalogAliasDescription(): String = """
Invalid alias catalog definition.
  - Alias definition 'plugin' is invalid

    Reason: Id for plugin alias 'plugin' wasn't set.

    Possible solution: Add the 'id' element on alias 'plugin'.

    For more information, please refer to https://docs.gradle.org/8.7/userguide/version_catalog_problems.html#toml_syntax_error in the Gradle documentation.
""".trimIndent()

fun getVersionCatalogWrongElementProblem() = """
FAILURE: Build failed with an exception.

* What went wrong:
org.gradle.api.InvalidUserDataException: On library declaration 'androidx-core-ktx' expected to find any of 'group', 'module', 'name', or 'version' but found unexpected key 'group1'.
> On library declaration 'androidx-core-ktx' expected to find any of 'group', 'module', 'name', or 'version' but found unexpected key 'group1'.

* Try:
> Run with --info or --debug option to get more log output.
> Run with --scan to get full insights.
> Get more help at https://help.gradle.org.
""".trimIndent()

fun getVersionCatalogWrongElementDescription() = """
Invalid catalog definition.
On library declaration 'androidx-core-ktx' expected to find any of 'group', 'module', 'name', or 'version' but found unexpected key 'group1'.
""".trimIndent()

fun getVersionCatalogWrongBundleElementProblem() = """
FAILURE: Build failed with an exception.

* What went wrong:
org.gradle.api.InvalidUserDataException: Invalid catalog definition:
  - Problem: In version catalog libs, a bundle with name 'bundle' declares a dependency on 'aaa' which doesn't exist.

    Reason: Bundles can only contain references to existing library aliases.

    Possible solutions:
      1. Make sure that the library alias 'aaa' is declared.
      2. Remove 'aaa' from bundle 'bundle'.

    For more information, please refer to https://docs.gradle.org/8.7/userguide/version_catalog_problems.html#undefined_alias_reference in the Gradle documentation.
> Invalid catalog definition:
    - Problem: In version catalog libs, a bundle with name 'bundle' declares a dependency on 'aaa' which doesn't exist.

      Reason: Bundles can only contain references to existing library aliases.

      Possible solutions:
        1. Make sure that the library alias 'aaa' is declared.
        2. Remove 'aaa' from bundle 'bundle'.

      For more information, please refer to https://docs.gradle.org/8.7/userguide/version_catalog_problems.html#undefined_alias_reference in the Gradle documentation.

* Try:
> Run with --info or --debug option to get more log output.
> Run with --scan to get full insights.
> Get more help at https://help.gradle.org.
""".trimIndent()

fun getVersionCatalogWrongBundleElementDescription() = """
Invalid catalog definition.
  - Problem: In version catalog libs, a bundle with name 'bundle' declares a dependency on 'aaa' which doesn't exist.

    Reason: Bundles can only contain references to existing library aliases.

    Possible solutions:
      1. Make sure that the library alias 'aaa' is declared.
      2. Remove 'aaa' from bundle 'bundle'.

    For more information, please refer to https://docs.gradle.org/8.7/userguide/version_catalog_problems.html#undefined_alias_reference in the Gradle documentation.
""".trimIndent()