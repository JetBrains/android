/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.templates.diff

import com.android.tools.idea.wizard.template.Template
import com.android.utils.FileUtils
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Generates files from a template and performs checks on them to ensure they're valid and can be
 * checked in as golden files, then copies the validated files to the output directory.
 *
 * For context and instructions on running and generating golden files, see go/template-diff-tests
 */
class BaselineGenerator(template: Template, goldenDirName: String) :
  ProjectRenderer(template, goldenDirName) {
  override fun handleDirectories(moduleName: String, goldenDir: Path, projectDir: Path) {
    val outputDir = getOutputDir(moduleName)

    FileUtils.deleteRecursivelyIfExists(outputDir.toFile())
    FileUtils.copyDirectory(projectDir.toFile(), outputDir.toFile())
    FILES_TO_IGNORE.forEach { FileUtils.deleteRecursivelyIfExists(goldenDir.resolve(it).toFile()) }
  }

  override fun prepareProject(projectRoot: File) {
    prepareProjectImpl(projectRoot)
  }

  /**
   * Gets the output directory where we should put the generated golden files. Bazel places files in
   * TEST_UNDECLARED_OUTPUTS_DIR which produces outputs.zip as a test artifact, so we can put the
   * files there.
   */
  private fun getOutputDir(moduleName: String): Path {
    val undeclaredOutputs = System.getenv("TEST_UNDECLARED_OUTPUTS_DIR")
    checkNotNull(undeclaredOutputs) {
      "The 'TEST_UNDECLARED_OUTPUTS_DIR' env. variable should already be set, because TemplateDiffTest#setUp checks for it"
    }

    val outputDir = Paths.get(undeclaredOutputs).resolve("golden").resolve(moduleName)

    println("\n----------------------------------------")
    println(
      "Outputting generated golden files to $outputDir\n\n" +
        "To update these files, unzip golden/ from outputs.zip to the android-templates/testData/golden directory.\n" +
        "For a remote invocation, download and unzip golden/ from outputs.zip:\n" +
        "    unzip outputs.zip \"golden/*\" -d \"$(bazel info workspace)/tools/adt/idea/android-templates/testData/\"\n" +
        "\n" +
        "For a local invocation, outputs.zip will be in bazel-testlogs:\n" +
        "    unzip $(bazel info bazel-testlogs)/tools/adt/idea/android-templates/intellij.android.templates.tests_tests__TemplateDiffTest/test.outputs/outputs.zip \\\n" +
        "    \"golden/*\" -d \"$(bazel info workspace)/tools/adt/idea/android-templates/testData/\""
    )
    println("----------------------------------------\n")
    return outputDir
  }
}
