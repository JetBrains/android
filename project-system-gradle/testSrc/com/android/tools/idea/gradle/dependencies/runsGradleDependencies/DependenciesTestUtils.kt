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
package com.android.tools.idea.gradle.dependencies.runsGradleDependencies

import com.android.tools.idea.testing.AndroidGradleTestCase.fail
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import java.nio.file.Paths

private fun isRootElement(string: String, elementPosition: Int): Boolean {
  var counter = 0
  for (pos in 0 until elementPosition) {
    when (string[pos]) {
      '{' -> counter += 1
      '}' -> counter -= 1
      else -> Unit
    }
    assertThat(counter).isGreaterThan(-1)
  }
  return counter == 0
}

/**
 * Returns content between curly braces `plugins{ ... }``
 */
fun getBlockContent(string: String, blockStart: Int): String? {
  var start = -1
  var counter = 0
  for (pos in blockStart until string.length) {
    when (string[pos]) {
      '{' -> {
        if (start == -1) start = pos
        counter += 1
      }

      '}' -> counter -= 1
      else -> Unit
    }
    if (counter == 0 && start >= 0) return string.substring(start + 1, pos - 1)
  }
  return null
}

/**
 * Method returns content of block that we specify in path - for example `pluginManagement.plugins`
 * It does not handle block duplication.
 */
fun getBlockContent(text: String, path: String): String {
  val elements = path.split(".")
  assert(elements.isNotEmpty()) { "Path must be formatted as dot separated path `pluginManagement.plugins`" }
  fun snippet(string: String, element: String): String? {
    val blockNamePosition = "$element[ \\t\\n\\{]".toRegex().find(string)?.range?.start
    if (blockNamePosition == null) {
      fail("Cannot find $element")
      return null
    }
    if (blockNamePosition >= 0)
      if (isRootElement(string, blockNamePosition)) {
        return getBlockContent(string, blockNamePosition)
      }
      else return snippet(string.substring(blockNamePosition + element.length), element)
    return null
  }

  var currentSnippet = text
  for (element in elements) {
    snippet(currentSnippet, element)?.let { currentSnippet = it } ?: fail(
      "Cannot get block content for element $element in $path for file: `$text`")
  }
  return currentSnippet
}

fun Project.doesFileExists(relativePath: String) =
  VfsUtil.findFile(Paths.get(basePath, relativePath), false)?.exists() ?: false
