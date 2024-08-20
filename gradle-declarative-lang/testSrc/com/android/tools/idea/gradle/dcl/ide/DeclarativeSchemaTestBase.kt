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
package com.android.tools.idea.gradle.dcl.ide

import com.android.tools.idea.testing.EdtAndroidProjectRule
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import org.jetbrains.annotations.SystemIndependent
import java.io.File
import java.io.IOException

abstract class DeclarativeSchemaTestBase {

  abstract val projectRule: EdtAndroidProjectRule

  // Keep this method here for now as schema files suppose to go away soon
  @Throws(IOException::class)
  fun writeToSchemaFile(filename: TestFile) {
    val myTestDataRelativePath = "tools/adt/idea/gradle-declarative-lang/testData"
    val folder = filename.toFile(myTestDataRelativePath, "")
    val children = folder.list()
    val projectDir = projectRule.project.guessProjectDir()!!
    runWriteAction {
      val gradlePath = projectDir.createChildDirectory(this, ".gradle")
      val schemaFolder = gradlePath.createChildDirectory(this, "declarative-schema")
      children.filter { it.endsWith("dcl.schema") }.forEach {
        val newFile = schemaFolder.createChildData(this, it)
        val file = VfsUtil.findFileByIoFile(File(folder, it), true)
        VfsUtil.saveText(newFile, VfsUtilCore.loadText(file!!))
      }
    }
  }


  enum class TestFile(private val path: @SystemIndependent String) {
    DECLARATIVE_SETTINGS_SCHEMAS("settingsSchemas"),
    DECLARATIVE_NEW_FORMAT_SCHEMAS("newFormatSchemas"),
    ;

    fun toFile(basePath: @SystemIndependent String, extension: String): File {
      return File(FileUtil.toSystemDependentName("$basePath/$path/") + extension)
    }
  }
}