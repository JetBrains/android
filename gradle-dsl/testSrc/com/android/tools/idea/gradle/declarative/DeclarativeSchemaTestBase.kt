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
package com.android.tools.idea.gradle.declarative

import com.android.tools.idea.gradle.dsl.TestFileName
import com.android.tools.idea.testing.EdtAndroidProjectRule
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import org.jetbrains.annotations.SystemIndependent
import java.io.File
import java.io.IOException

abstract class DeclarativeSchemaTestBase {

  abstract val projectRule: EdtAndroidProjectRule

  // Keep this method here for now as schema files suppose to go away soon
  @Throws(IOException::class)
  fun writeToSchemaFile(filename: TestFileName) {
    val myTestDataRelativePath = "tools/adt/idea/gradle-dsl/testData/parser"
    val folder = filename.toFile(myTestDataRelativePath, "")
      val children = VfsUtil.getChildren(VfsUtil.findFileByIoFile(folder, true)!!)
    val projectDir = projectRule.project.guessProjectDir()!!
    runWriteAction {
      val gradlePath = projectDir.createChildDirectory(this, ".gradle")
      val schemaFolder = gradlePath.createChildDirectory(this, "declarative-schema")
      children.filter { it.name.endsWith("dcl.schema") }.forEach {
        val newFile = schemaFolder.createChildData(this, it.name)
        VfsUtil.saveText(newFile, VfsUtilCore.loadText(
          it))
      }
    }
  }

  internal enum class TestFile(private val path: @SystemIndependent String) : TestFileName {
    DECLARATIVE_GENERATED_SCHEMAS("somethingDeclarative/schemas"),
    DECLARATIVE_ADVANCED_SCHEMAS("somethingDeclarative/advancedSchemas"),
    DECLARATIVE_SETTINGS_SCHEMAS("somethingDeclarative/settingsSchemas"),
    DECLARATIVE_DEMO_SCHEMAS("somethingDeclarative/demoSchemas"),
    ;

    override fun toFile(basePath: @SystemIndependent String, extension: String): File {
      return super.toFile("$basePath/$path/", extension)
    }
  }
}