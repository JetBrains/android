/*
 * Copyright (C) 2014 The Android Open Source Project
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

@file:JvmName("ImportUIUtil")

package com.android.tools.idea.npw.importing

import com.google.common.base.Functions
import com.google.common.base.Joiner
import com.google.common.base.Splitter
import com.google.common.base.Strings
import com.google.common.collect.Iterables
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile

/**
 * Utility class for common import UI code.
 */

/**
 * Formats a message picking the format string depending on number of arguments
 *
 * @param values                       values that will be used as format argument.
 * @param oneElementMessage            message when only one value is in the list. Should accept one string argument.
 * @param twoOrThreeElementsMessage    message format when there's 2 or 3 values. Should accept two string arguments.
 * @param moreThenThreeElementsMessage message format for over 3 values. Should accept one string and one number.
 * @return formatted message string
 */
fun formatElementListString(values: Iterable<String>,
                            oneElementMessage: String,
                            twoOrThreeElementsMessage: String,
                            moreThenThreeElementsMessage: String): String {
  val size = Iterables.size(values)
  return when {
    // If there's 0 elements, some error happened
    size <= 1 -> String.format(oneElementMessage, Iterables.getFirst(values, "<validation error>"))
    size <= 3 -> String.format(twoOrThreeElementsMessage, atMostTwo(values, size), Iterables.getLast(values))
    else -> String.format(moreThenThreeElementsMessage, atMostTwo(values, size), size - 2)
  }
}

private fun atMostTwo(names: Iterable<String>, size: Int): String? =
  Joiner.on(", ").join(Iterables.limit(names, (size - 1).coerceAtMost(2)))

@Deprecated("Replaced by {@link com.android.tools.idea.ui.wizard.WizardUtils#toHtmlString(String)}")
fun makeHtmlString(templateDescription: String?): String? {
  var templateDescription = templateDescription
  if (!StringUtil.isEmpty(templateDescription) && !templateDescription!!.startsWith("<html>")) {
    templateDescription = String.format("<html>%1\$s</html>", templateDescription.trim())
  }
  return templateDescription
}

/**
 * Returns a relative path string to be shown in the UI. Wizard logic
 * operates with VirtualFile's so these paths are only for user. The paths
 * shown are relative to the file system location user specified, showing
 * relative paths will be easier for the user to read.
 */
internal fun getRelativePath(baseFile: VirtualFile?,
                             file: VirtualFile?): String {
  if (file == null) {
    return ""
  }
  val path = file.path
  return if (baseFile == null) {
    path
  }
  else if (file == baseFile) {
    "."
  }
  else if (!baseFile.isDirectory) {
    getRelativePath(baseFile.parent, file)
  }
  else {
    val basePath = baseFile.path
    if (path.startsWith("$basePath/")) {
      path.substring(basePath.length + 1)
    }
    else if (file.fileSystem == baseFile.fileSystem) {
      val builder = StringBuilder(basePath.length)
      var prefix: String? = Strings.commonPrefix(path, basePath)
      if (!prefix!!.endsWith("/")) {
        prefix = prefix.substring(0, prefix.lastIndexOf('/') + 1)
      }
      if (!path.startsWith(basePath)) {
        val segments: MutableIterable<String?>? = Splitter.on("/").split( basePath.substring(prefix.length))
        Joiner.on("/").appendTo(builder, Iterables.transform(segments, Functions.constant("..")))
        builder.append("/")
      }
      builder.append(path.substring(prefix.length))
      builder.toString()
    }
    else {
      path
    }
  }
}
