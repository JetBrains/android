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
import com.android.tools.idea.gradle.dsl.api.ext.PropertyType
import com.android.tools.idea.gradle.dsl.model.BuildModelContext
import com.android.tools.idea.gradle.dsl.parser.ExternalNameInfo.ExternalNameSyntax
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslClosure
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslLiteral
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslMethodCall
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement
import com.android.tools.idea.gradle.dsl.parser.files.GradleDslFile
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.VfsTestUtil
import org.jetbrains.annotations.SystemDependent
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameter
import org.junit.runners.Parameterized.Parameters
import org.mockito.Mockito
import java.io.File

@RunWith(Parameterized::class)
class GroovyKotlinDslWriterParityTest : LightPlatformTestCase() {

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

  // tests that kotlin and groovy both can create structure like `a = method(1){ prop = "value" }`
  @Test
  fun testAssignmentWithMethodAndClosure() {
    doTest(TestFile.ASSIGNMENT_METHOD_WITH_CLOSURE_EXPECTED) { dslFile ->

      val methodCall = GradleDslMethodCall(dslFile, GradleNameElement.create("abc"), "release")
      dslFile.setNewElement(methodCall)
      methodCall.externalSyntax = ExternalNameSyntax.ASSIGNMENT
      val versionLiteral = GradleDslLiteral(methodCall.argumentsElement, GradleNameElement.empty())
      versionLiteral.setValue(123)
      methodCall.addNewArgument(versionLiteral)

      val closure = GradleDslClosure(methodCall, null, GradleNameElement.create("release"))
      methodCall.setNewClosureElement(closure)

      val newElement = GradleDslLiteral(closure, GradleNameElement.create("closureProperty"))
      newElement.setValue("value")
      newElement.elementType = PropertyType.REGULAR
      newElement.externalSyntax = ExternalNameSyntax.ASSIGNMENT
      closure.setNewElement(newElement)
    }
  }

  private fun doTest(testFileName: TestFile, createDslModel: (GradleDslFile) -> Unit) {
    val testDataRelativePath = "tools/adt/idea/gradle-dsl/testData/parser"
    val expected = FileUtil.loadFile(testFileName.toFile(testDataRelativePath, myTestDataExtension))


    val file = VfsTestUtil.createFile(project.guessProjectDir()!!, "build$myTestDataExtension", "")
    val dslFile = object : GradleDslFile(file, project, ":", BuildModelContext.create(project, Mockito.mock())) {}
    dslFile.parse()
    createDslModel(dslFile)
    WriteCommandAction.runWriteCommandAction(project) {
      dslFile.applyChanges()
      dslFile.saveAllChanges()
    }
    assertEquals(expected.replace("[ \\r\\t]+".toRegex(), "").trim { it <= ' ' },
                 VfsUtilCore.loadText(file).replace("[ \\r\\t]+".toRegex(), "").trim { it <= ' ' })
  }

  enum class TestFile(private val path: @SystemDependent String) : TestFileName {
    ASSIGNMENT_METHOD_WITH_CLOSURE_EXPECTED("assignmentMethodWithClosureExpected"),
    ;

    override fun toFile(basePath: @SystemDependent String, extension: String): File {
      return super.toFile("$basePath/dslWriter/$path", extension)
    }
  }

}