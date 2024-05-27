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

import com.android.test.testutils.TestUtils
import com.android.tools.idea.gradle.dsl.TestFileName
import com.android.tools.idea.testing.EdtAndroidProjectRule
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import org.jetbrains.annotations.SystemIndependent
import java.io.File
import java.io.IOException
import kotlin.io.path.pathString

abstract class DeclarativeSchemaTestBase {

  abstract val projectRule: EdtAndroidProjectRule

  // Keep this method here for now as schema files suppose to go away soon
  @Throws(IOException::class)
  fun writeToSchemaFile(filename: TestFileName) {
    val projectFileName = "project.dcl.schema"
    val pluginFileName = "plugins.dcl.schema"

    val myTestDataRelativePath = TestUtils.resolveWorkspacePath("tools/adt/idea/gradle-dsl/testData/parser").pathString
    val projectFile = filename.toFile(myTestDataRelativePath, projectFileName)
    val pluginFile = filename.toFile(myTestDataRelativePath, pluginFileName)
    val virtualProjectFile = VfsUtil.findFileByIoFile(projectFile, true)
    val virtualPluginFile = VfsUtil.findFileByIoFile(pluginFile, true)
    val projectDir = projectRule.project.guessProjectDir()!!
    runWriteAction {
      val gradlePath = projectDir.createChildDirectory(this, ".gradle")
      val schemaFolder = gradlePath.createChildDirectory(this, "declarative-schema")
      val schemaProjectFile = schemaFolder.createChildData(this, projectFileName)
      val schemaPluginFile = schemaFolder.createChildData(this, pluginFileName)
      VfsUtil.saveText(schemaProjectFile, VfsUtilCore.loadText(
        virtualProjectFile!!))
      VfsUtil.saveText(schemaPluginFile, VfsUtilCore.loadText(
        virtualPluginFile!!))
    }
  }

  internal enum class TestFile(private val path: @SystemIndependent String) : TestFileName {
    DECLARATIVE_GENERATED_SCHEMAS("somethingDeclarative/schemas"),
    DECLARATIVE_ADVANCED_SCHEMAS("somethingDeclarative/advancedSchemas");

    override fun toFile(basePath: @SystemIndependent String, extension: String): File {
      return super.toFile("$basePath/$path/", extension)
    }
  }
}