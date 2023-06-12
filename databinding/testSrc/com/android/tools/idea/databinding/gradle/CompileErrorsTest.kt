/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.databinding.gradle

import com.android.tools.idea.databinding.TestDataPaths
import com.android.tools.idea.gradle.project.build.invoker.TestCompileType
import com.android.tools.idea.gradle.project.sync.snapshots.testProjectTemplateFromPath
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.buildAndWait
import com.google.common.truth.Truth.assertThat
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.RunsInEdt
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * This class compiles a real project with compile errors in it to verify the output.
 */
@RunsInEdt
class CompileErrorsTest {
  // ProjectRule must be initialized off the EDT thread
  @get:Rule
  val projectRule =
    AndroidProjectRule.testProject(testProjectTemplateFromPath(
      path = TestDataPaths.PROJECT_WITH_COMPILE_ERRORS,
      testDataPath = TestDataPaths.TEST_DATA_ROOT
    ))

  @Test
  fun compileErrorsContainExpectedValues() {
    val assembleDebug = projectRule.project.buildAndWait { it.assemble(TestCompileType.NONE) }
    val errorMessage = with(StringWriter()) {
      assembleDebug.invocationResult.invocations.first().buildError!!.printStackTrace(PrintWriter(this))
      toString()
    }

    val result = Regex("""\[databinding] (\{.+})""").find(errorMessage) ?: error("Unexpected error message:\n\n$errorMessage")
    val errorJson = with(result.groupValues[1]) {
      JsonParser().parse(this) as JsonObject
    }

    assertThat(errorJson["msg"].asString).startsWith("Could not find identifier 'usr'")

    val file = errorJson["file"].asString.let { path ->
      if (FileUtil.isAbsolute(path)) File(path) else File(projectRule.project.basePath, path)
    }
    assertThat(LocalFileSystem.getInstance().findFileByIoFile(file)).isNotNull()
    assertThat(file.name).isEqualTo("activity_main.xml")
    assertThat(errorJson["pos"].asJsonArray[0].asJsonObject["line0"].asInt).isEqualTo(12)
  }
}
