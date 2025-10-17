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
package com.android.tools.idea.sqlite.settings

import com.android.tools.idea.projectsystem.SourceProviderManager
import com.android.tools.idea.sqlite.settings.TestClass.Class
import com.android.tools.idea.sqlite.settings.TestClass.Interface
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.util.androidFacet

private val TEST_CLASSES =
  listOf(
    Interface("androidx.sqlite.SQLiteDriver"),
    Interface("androidx.sqlite.SQLiteConnection"),
    Class("com.app.SQLiteDriver", "androidx.sqlite.SQLiteDriver"),
    Class("com.app.SQLiteConnection", "androidx.sqlite.SQLiteConnection"),
    Class("org.app.SQLiteDriver", "androidx.sqlite.SQLiteDriver"),
    Class("org.app.SQLiteConnection", "androidx.sqlite.SQLiteConnection"),
  )

internal fun AndroidProjectRule.setupClasses() {
  TEST_CLASSES.forEach { fixture.addFileToProject(it.fileName, it.content) }
  // Suppress WARN log about a missing AndroidManifest.xml file
  SourceProviderManager.replaceForTest(fixture.module.androidFacet!!, testRootDisposable, null)
}

private sealed class TestClass(name: String) {
  protected val simpleName = name.substringAfterLast('.')

  val fileName: String = "${name.replace('.', '/')}.kt"
  abstract val content: String

  class Interface(name: String) : TestClass(name) {
    override val content: String = buildString {
      appendPackage(name)
      append("interface $simpleName")
    }
  }

  class Class(name: String, base: String) : TestClass(name) {
    override val content: String = buildString {
      appendPackage(name)
      append("class $simpleName: $base\n")
    }
  }
}

private fun StringBuilder.appendPackage(name: String) {
  if (name.contains('.')) {
    append("package ${name.substringBeforeLast('.')}\n")
  }
}
