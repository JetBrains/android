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

package org.jetbrains.kotlin.android.synthetic.idea

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.util.SmartFMap
import com.intellij.util.application
import org.jetbrains.kotlin.android.InTextDirectivesUtils
import org.jetbrains.kotlin.android.KotlinAndroidTestCase
import org.jetbrains.kotlin.android.KotlinTestUtils
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
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPackageDirective
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.renderer.DescriptorRenderer
import java.io.File
import java.util.Collections
import java.util.concurrent.Semaphore

abstract class AbstractAndroidExtractionTest: KotlinAndroidTestCase() {

  override fun runInDispatchThread() = false

  fun doTest(path: String) {
    copyResourceDirectoryForTest(path)
    val testFilePath = path + getTestName(true) + ".kt"
    val virtualFile = myFixture.copyFileToProject(testFilePath, "src/" + getTestName(true) + ".kt")
    myFixture.configureFromExistingVirtualFile(virtualFile)

    checkExtract(ExtractTestFiles("$testDataPath/$testFilePath", myFixture.file)) { file ->
      doExtractFunction(myFixture, file as KtFile)
    }
  }

  // Originally from AbstractExtractionTest.kt.
  class ExtractTestFiles(
    val mainFile: PsiFile,
    val afterFile: File,
    val conflictFile: File,
    val extraFilesToPsi: Map<PsiFile, File> = emptyMap()
  ) {
    constructor(path: String, mainFile: PsiFile, extraFilesToPsi: Map<PsiFile, File> = emptyMap()) :
      this(mainFile, File("$path.after"), File("$path.conflicts"), extraFilesToPsi)
  }

  // Originally from AbstractExtractionTest.kt.
  private fun checkExtract(files: ExtractTestFiles, checkAdditionalAfterdata: Boolean = false, action: (PsiFile) -> Unit) {
    val conflictFile = files.conflictFile
    val afterFile = files.afterFile

    try {
      action(files.mainFile)

      assert(!conflictFile.exists()) { "Conflict file $conflictFile should not exist" }
      KotlinTestUtils.assertEqualsToFile(afterFile, files.mainFile.text!!)

      if (checkAdditionalAfterdata) {
        for ((extraPsiFile, extraFile) in files.extraFilesToPsi) {
          KotlinTestUtils.assertEqualsToFile(File("${extraFile.path}.after"), extraPsiFile.text)
        }
      }
    } catch (e: BaseRefactoringProcessor.ConflictsInTestsException) {
      val message = e.messages.sorted().joinToString(" ").replace("\n", " ")
      KotlinTestUtils.assertEqualsToFile(conflictFile, message)
    } catch (e: CommonRefactoringUtil.RefactoringErrorHintException) {
      KotlinTestUtils.assertEqualsToFile(conflictFile, e.message!!)
    } catch (e: RuntimeException) { // RuntimeException is thrown by IDEA code in CodeInsightUtils.java
      if (e::class.java != RuntimeException::class.java) throw e
      KotlinTestUtils.assertEqualsToFile(conflictFile, e.message!!)
    }
  }

  // Originally from AbstractExtractionTest.kt.
  private fun doExtractFunction(fixture: CodeInsightTestFixture, file: KtFile) {
    val editor = fixture.editor

    val helper = runReadAction {
      val fileText = file.getText() ?: ""
      val expectedNames = InTextDirectivesUtils.findListWithPrefixes(fileText, "// SUGGESTED_NAMES: ")
      val expectedReturnTypes = InTextDirectivesUtils.findListWithPrefixes(fileText, "// SUGGESTED_RETURN_TYPES: ")
      val expectedDescriptors =
        InTextDirectivesUtils.findLinesWithPrefixesRemoved(fileText, "// PARAM_DESCRIPTOR: ").joinToString()
      val expectedTypes =
        InTextDirectivesUtils.findLinesWithPrefixesRemoved(fileText, "// PARAM_TYPES: ").map { "[$it]" }.joinToString()

      val extractionOptions = InTextDirectivesUtils.findListWithPrefixes(fileText, "// OPTIONS: ").let {
        if (it.isNotEmpty()) {
          @Suppress("UNCHECKED_CAST") val args = it.map { it.toBoolean() }.toTypedArray() as Array<Any?>
          ExtractionOptions::class.java.constructors.first { it.parameterTypes.size == args.size }.newInstance(
            *args) as ExtractionOptions
        }
        else ExtractionOptions.DEFAULT
      }

      val renderer = DescriptorRenderer.FQ_NAMES_IN_TYPES

      object : ExtractionEngineHelper(EXTRACT_FUNCTION) {

        private val finishedSemaphore = Semaphore(0)

        fun waitUntilFinished() {
          finishedSemaphore.acquire()
        }

        override fun adjustExtractionData(data: ExtractionData): ExtractionData {
          return data.copy(options = extractionOptions)
        }

        override fun configureAndRun(
          project: Project,
          editor: Editor,
          descriptorWithConflicts: ExtractableCodeDescriptorWithConflicts,
          onFinish: (ExtractionResult) -> Unit) {
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

            doRefactor(ExtractionGeneratorConfiguration(newDescriptor, ExtractionGeneratorOptions.DEFAULT)) { er ->
              onFinish(er)
              finishedSemaphore.release()
            }
          }
      }
    }

    application.invokeAndWait {
      val handler = ExtractKotlinFunctionHandler(helper = helper)
      val explicitPreviousSibling = file.findElementByCommentPrefix("// SIBLING:")
      handler.selectElements(editor, file) { elements, previousSibling ->
        handler.doInvoke(editor, file, elements, explicitPreviousSibling ?: previousSibling)
      }
    }

    helper.waitUntilFinished()
  }

  private fun PsiFile.findElementByCommentPrefix(commentText: String): PsiElement? =
    findElementsByCommentPrefix(commentText).keys.singleOrNull()

  // Originally from jetTestUtils.kt
  private fun PsiFile.findElementsByCommentPrefix(prefix: String): Map<PsiElement, String> {
    var result = SmartFMap.emptyMap<PsiElement, String>()
    accept(
      object : KtTreeVisitorVoid() {
        override fun visitComment(comment: PsiComment) {
          val commentText = comment.text
          if (commentText.startsWith(prefix)) {
            val parent = comment.parent
            val elementToAdd = when (parent) {
                                 is KtDeclaration -> parent
                                 is PsiMember -> parent
                                 else -> PsiTreeUtil.skipSiblingsForward(
                                   comment,
                                   PsiWhiteSpace::class.java, PsiComment::class.java, KtPackageDirective::class.java
                                 )
                               } ?: return

            result = result.plus(elementToAdd, commentText.substring(prefix.length).trim())
          }
        }
      }
    )
    return result
  }
}
