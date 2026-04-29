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
package com.android.tools.idea.templates.diff.activity

import com.android.tools.idea.templates.diff.TemplateDiffTestUtils
import com.android.tools.idea.wizard.template.Template
import com.android.utils.FileUtils
import java.io.File
import java.nio.file.Path

const val GOLDEN_FILE_HEADER_COMMENT = "This file should not be edited manually! See go/template-diff-tests"

/**
 * Generates files from a template and performs checks on them to ensure they're valid and can be checked in as golden files, then copies
 * the validated files to the output directory.
 *
 * For context and instructions on running and generating golden files, see go/template-diff-tests
 */
class GoldenFileGenerator(template: Template, goldenDirName: String) : ProjectRenderer(template, goldenDirName) {
  override fun handleDirectories(moduleName: String, goldenDir: Path, projectDir: Path) {
    val outputDir = TemplateDiffTestUtils.getOutputDir("golden").resolve(moduleName)

    FileUtils.deleteRecursivelyIfExists(outputDir.toFile())
    FileUtils.copyDirectory(projectDir.toFile(), outputDir.toFile())

    outputDir.toFile().walk().forEach { file ->
      if (file.isFile) {
        addHeaderComment(file)
      }
    }

    FILES_TO_IGNORE.forEach { FileUtils.deleteRecursivelyIfExists(goldenDir.resolve(it).toFile()) }
  }

  private fun addHeaderComment(file: File) {
    val extension = file.extension
    val comment =
      when (extension) {
        "xml" -> "<!-- $GOLDEN_FILE_HEADER_COMMENT -->"
        "cpp",
        "gradle",
        "h",
        "java",
        "kt" -> "// $GOLDEN_FILE_HEADER_COMMENT"
        "properties",
        "txt",
        "toml" -> "# $GOLDEN_FILE_HEADER_COMMENT"
        else -> return // Skip unknown types
      }
    val content = file.readText()
    val newContent =
      if (extension == "xml" && content.startsWith("<?xml")) {
        if (content.contains("?><!--")) {
          // Formatter bug detected! Put the warning at the bottom to avoid breaking the file.
          if (content.endsWith("\n")) content + comment else "$content\n$comment"
        } else {
          // No collision. Safe to put the warning right after the XML header.
          val headerEnd = content.indexOf("?>")
          content.substring(0, headerEnd + 2) + "\n" + comment + content.substring(headerEnd + 2)
        }
      } else {
        // Non-XML files or XML files without headers
        "$comment\n$content"
      }
    file.writeText(newContent)
  }

  override fun prepareProject(projectRoot: File) {
    prepareProjectImpl(projectRoot)
  }
}
