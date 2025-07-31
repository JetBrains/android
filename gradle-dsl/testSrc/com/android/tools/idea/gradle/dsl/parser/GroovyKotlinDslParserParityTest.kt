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
package com.android.tools.idea.gradle.dsl.parser

import com.android.tools.idea.gradle.dsl.TestFileName
import com.android.tools.idea.gradle.dsl.model.BuildModelContext
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslLiteral
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslMethodCall
import com.android.tools.idea.gradle.dsl.parser.files.GradleDslFile
import com.google.common.truth.Truth
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.LightPlatformTestCase
import org.jetbrains.annotations.SystemDependent
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameter
import org.junit.runners.Parameterized.Parameters
import org.mockito.Mockito
import java.io.File

@RunWith(Parameterized::class)
class GroovyKotlinDslParserParityTest : LightPlatformTestCase() {

  @Parameter
  lateinit var myTestDataExtension: String

  @Parameter(1)
  lateinit var myLanguageName: String

  companion object {
    @JvmStatic
    @Parameters(name = "{1}")
    fun data(): Collection<Array<String>> =
      listOf(
        arrayOf(".groovy", "Groovy"),
        arrayOf(".kts", "Kotlin")
      )
  }

  @Test
  fun testAssignmentWithMethodAndClosure() {
    doTest(TestFile.ASSIGNMENT_METHOD_WITH_CLOSURE) { dslFile ->
      val dslElement = dslFile.getPropertyElement("variable")
      Truth.assertThat(dslElement).isNotNull()
      Truth.assertThat(dslElement!!.name).isEqualTo("variable")
      Truth.assertThat(dslElement).isInstanceOf(GradleDslMethodCall::class.java)

      val method = dslElement as GradleDslMethodCall
      Truth.assertThat(method.methodName).isEqualTo("method")
      Truth.assertThat(method.arguments).hasSize(1)
      Truth.assertThat(method.argumentsElement.getElementAt(0)).isInstanceOf(GradleDslLiteral::class.java)

      val literal = method.argumentsElement.getElementAt(0) as GradleDslLiteral
      Truth.assertThat(literal.value).isEqualTo(12)

      Truth.assertThat(method.closureElement).isNotNull()
    }
  }

  private fun doTest(testFileObject: TestFile, verification: (GradleDslFile) -> Unit) {
    val testDataRelativePath = "tools/adt/idea/gradle-dsl/testData/parser"
    val testFile: File = testFileObject.toFile(testDataRelativePath, myTestDataExtension)

    val virtualTestFile = VfsUtil.findFileByIoFile(testFile, true)
    Truth.assertThat(virtualTestFile).isNotNull()
    val dslFile = object : GradleDslFile(virtualTestFile!!, project, ":", BuildModelContext.create(project, Mockito.mock())) {}
    dslFile.parse()
    verification(dslFile)
  }

  enum class TestFile(private val path: @SystemDependent String) : TestFileName {
    ASSIGNMENT_METHOD_WITH_CLOSURE("assignmentMethodWithClosure"),
    ;

    override fun toFile(basePath: @SystemDependent String, extension: String): File {
      return super.toFile("$basePath/dslParser/$path", extension)
    }
  }

}