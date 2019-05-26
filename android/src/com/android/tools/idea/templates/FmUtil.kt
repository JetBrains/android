/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.templates

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.ProjectLocator
import com.intellij.openapi.vfs.LocalFileSystem
import java.io.File

/**
 * Strip off the end portion of the name.
 * The user might be typing the activity name such that only a portion has been entered so far (e.g. "MainActivi") and we want to
 * chop off that portion too such that we don't offer a layout name partially containing the activity suffix (e.g. "main_activi").
 */
tailrec fun String.stripSuffix(suffix: String, recursively: Boolean = false): String {
  if (length < 2) {
    return this
  }

  val suffixStart = lastIndexOf(suffix[0])

  val name = if (suffixStart != -1 && regionMatches(suffixStart, suffix, 0, length - suffixStart))
    substring(0, suffixStart)
  else
    this

  // Recursively continue to strip the suffix (catch the FooActivityActivity case)
  return if (recursively && name.endsWith(suffix)) name.stripSuffix(suffix, recursively) else name
}

/**
 * Tries to find the [Module] for the given `modulePath`. Returns `null` when a valid module is not found.
 */
fun findModule(modulePath: String): Module? {
  val file = LocalFileSystem.getInstance().findFileByIoFile(File(modulePath.replace('/', File.separatorChar))) ?: return null
  val project = ProjectLocator.getInstance().guessProjectForFile(file) ?: return null
  return ModuleUtilCore.findModuleForFile(file, project)
}
