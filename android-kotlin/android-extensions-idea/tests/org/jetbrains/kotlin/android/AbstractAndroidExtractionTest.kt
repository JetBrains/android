/*
 * Copyright 2010-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.android

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.jetbrains.kotlin.idea.refactoring.introduce.ExtractTestFiles
import org.jetbrains.kotlin.idea.refactoring.introduce.checkExtract
import org.jetbrains.kotlin.idea.refactoring.introduce.extractFunction.EXTRACT_FUNCTION
import org.jetbrains.kotlin.idea.refactoring.introduce.extractFunction.ExtractKotlinFunctionHandler
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.ExtractableCodeDescriptorWithConflicts
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.ExtractionData
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.ExtractionEngineHelper
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.ExtractionGeneratorConfiguration
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.ExtractionGeneratorOptions
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.ExtractionOptions
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.ExtractionResult
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.possibleReturnTypes
import org.jetbrains.kotlin.idea.util.IdeDescriptorRenderers
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.renderer.DescriptorRenderer
import org.jetbrains.kotlin.test.InTextDirectivesUtils
import org.jetbrains.kotlin.test.util.findElementByCommentPrefix
import java.util.Collections

abstract class AbstractAndroidExtractionTest: KotlinAndroidTestCase() {

    fun doTest(path: String) {
        copyResourceDirectoryForTest(path)
        val testFilePath = path + getTestName(true) + ".kt"
        val virtualFile = myFixture.copyFileToProject(testFilePath, "src/" + getTestName(true) + ".kt")
        myFixture.configureFromExistingVirtualFile(virtualFile)

        checkExtract(ExtractTestFiles("$testDataPath/$testFilePath", myFixture.file)) { file ->
            doExtractFunction(myFixture, file as KtFile)
        }
    }

    // Test helper method originally declared in AbstractExtractionTest.kt in the Kotlin plugin.
    fun doExtractFunction(fixture: CodeInsightTestFixture, file: KtFile) {
        val explicitPreviousSibling = file.findElementByCommentPrefix("// SIBLING:")
        val fileText = file.getText() ?: ""
        val expectedNames = InTextDirectivesUtils.findListWithPrefixes(fileText, "// SUGGESTED_NAMES: ")
        val expectedReturnTypes = InTextDirectivesUtils.findListWithPrefixes(fileText, "// SUGGESTED_RETURN_TYPES: ")
        val expectedDescriptors =
          InTextDirectivesUtils.findLinesWithPrefixesRemoved(fileText, "// PARAM_DESCRIPTOR: ").joinToString()
        val expectedTypes =
          InTextDirectivesUtils.findLinesWithPrefixesRemoved(fileText, "// PARAM_TYPES: ").map { "[$it]" }.joinToString()

        val extractionOptions = InTextDirectivesUtils.findListWithPrefixes(fileText, "// OPTIONS: ").let {
            if (it.isNotEmpty()) {
                @Suppress("UNCHECKED_CAST")
                val args = it.map { it.toBoolean() }.toTypedArray() as Array<Any?>
                ExtractionOptions::class.java.constructors.first { it.parameterTypes.size == args.size }.newInstance(*args) as ExtractionOptions
            } else ExtractionOptions.DEFAULT
        }

        val renderer = DescriptorRenderer.FQ_NAMES_IN_TYPES

        val editor = fixture.editor
        val handler = ExtractKotlinFunctionHandler(
          helper = object : ExtractionEngineHelper(EXTRACT_FUNCTION) {
              override fun adjustExtractionData(data: ExtractionData): ExtractionData {
                  return data.copy(options = extractionOptions)
              }

              override fun configureAndRun(
                project: Project,
                editor: Editor,
                descriptorWithConflicts: ExtractableCodeDescriptorWithConflicts,
                onFinish: (ExtractionResult) -> Unit
              ) {
                  val descriptor = descriptorWithConflicts.descriptor
                  val actualNames = descriptor.suggestedNames
                  val actualReturnTypes = descriptor.controlFlow.possibleReturnTypes.map {
                      IdeDescriptorRenderers.SOURCE_CODE.renderType(it)
                  }
                  val allParameters = listOfNotNull(descriptor.receiverParameter) + descriptor.parameters
                  val actualDescriptors = allParameters.map { renderer.render(it.originalDescriptor) }.joinToString()
                  val actualTypes = allParameters.map {
                      it.getParameterTypeCandidates().map { renderer.renderType(it) }.joinToString(", ", "[", "]")
                  }.joinToString()

                  if (actualNames.size != 1 || expectedNames.isNotEmpty()) {
                      assertEquals("Expected names mismatch.", expectedNames, actualNames)
                  }
                  if (actualReturnTypes.size != 1 || expectedReturnTypes.isNotEmpty()) {
                      assertEquals("Expected return types mismatch.", expectedReturnTypes, actualReturnTypes)
                  }
                  assertEquals("Expected descriptors mismatch.", expectedDescriptors, actualDescriptors)
                  assertEquals("Expected types mismatch.", expectedTypes, actualTypes)

                  val newDescriptor = if (descriptor.name == "") {
                      descriptor.copy(suggestedNames = Collections.singletonList("__dummyTestFun__"))
                  }
                  else {
                      descriptor
                  }

                  doRefactor(ExtractionGeneratorConfiguration(newDescriptor, ExtractionGeneratorOptions.DEFAULT), onFinish)
              }
          }
        )
        handler.selectElements(editor, file) { elements, previousSibling ->
            handler.doInvoke(editor, file, elements, explicitPreviousSibling ?: previousSibling)
        }
    }
}
