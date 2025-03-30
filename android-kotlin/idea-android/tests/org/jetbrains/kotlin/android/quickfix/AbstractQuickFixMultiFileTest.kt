/*
 * Copyright (C) 2018 The Android Open Source Project
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

package org.jetbrains.kotlin.android.quickfix

import com.android.tools.tests.AdtTestProjectDescriptors
import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.quickFix.ActionHint
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import com.intellij.util.ArrayUtil
import com.intellij.util.PathUtil
import junit.framework.TestCase
import org.jetbrains.android.LightJavaCodeInsightFixtureAdtTestCase
import org.jetbrains.kotlin.android.DirectiveBasedActionUtils
import org.jetbrains.kotlin.android.InTextDirectivesUtils
import org.jetbrains.kotlin.android.KotlinTestUtils
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider
import org.jetbrains.kotlin.psi.KtFile
import java.io.File
import java.util.regex.Pattern

// Largely copied from the Kotlin test framework (after taking over android-kotlin sources).
abstract class AbstractQuickFixMultiFileTest : LightJavaCodeInsightFixtureAdtTestCase() {

  override fun getProjectDescriptor(): LightProjectDescriptor = AdtTestProjectDescriptors.kotlin()

  override fun setUp() {
    super.setUp()
    CodeInsightSettings.getInstance().EXCLUDED_PACKAGES = arrayOf("excludedPackage", "somePackage.ExcludedClass")
  }

  override fun tearDown() {
    CodeInsightSettings.getInstance().EXCLUDED_PACKAGES = ArrayUtil.EMPTY_STRING_ARRAY
    super.tearDown()
  }

  private fun createTestFile(testFile: TestFile): VirtualFile {
    return runWriteAction {
      val vFile = myFixture.tempDirFixture.createFile(testFile.path)
      vFile.charset = Charsets.UTF_8
      VfsUtil.saveText(vFile, testFile.content)
      vFile
    }
  }

  protected fun doTest(beforeFileNamePrefix: String) {
    val beforeFileName = "$beforeFileNamePrefix.before.Main.kt"
    val mainFile = File(testDataPath, beforeFileName)
    val originalFileText = FileUtil.loadFile(mainFile, true)

    val mainFileDir = mainFile.parentFile!!

    val mainFileName = mainFile.name
    val extraFiles = mainFileDir.listFiles { _, name ->
      name.startsWith(extraFileNamePrefix(mainFileName))
      && name != mainFileName
      && PathUtil.getFileExtension(name).let { it == "kt" || it == "java" || it == "groovy" }
    }!!

    val testFiles = ArrayList<String>()
    testFiles.add(beforeFileName)
    extraFiles.mapTo(testFiles) { file -> File(mainFileDir, file.name).path }

    myFixture.configureByFiles(*testFiles.toTypedArray())

    if (KotlinPluginModeProvider.isK2Mode() && InTextDirectivesUtils.isDirectiveDefined(originalFileText, "// SKIP-K2")) {
      return
    }

    CommandProcessor.getInstance().executeCommand(project, {
      try {
        val psiFile = file

        val actionHint = ActionHint.parse(psiFile, originalFileText)
        val text = actionHint.expectedText

        val actionShouldBeAvailable = actionHint.shouldPresent()

        if (psiFile is KtFile) {
          if (KotlinPluginModeProvider.isK2Mode()) {
            DirectiveBasedActionUtils.checkForUnexpectedErrorsK2(psiFile)
          } else {
            DirectiveBasedActionUtils.checkForUnexpectedErrorsK1(psiFile)
          }
        }

        doAction(text, file, editor, actionShouldBeAvailable, beforeFileName, this::availableActions, myFixture::doHighlighting)

        if (actionShouldBeAvailable) {
          val afterFilePath = beforeFileName.replace(".before.Main.", ".after.")
          myFixture.checkResultByFile(afterFilePath)

          for (file in myFixture.file.containingDirectory.files) {
            val fileName = file.name
            if (fileName == myFixture.file.name || !fileName.startsWith(extraFileNamePrefix(myFixture.file.name))) continue

            val extraFileFullPath = beforeFileName.replace(myFixture.file.name, fileName)
            val afterFile = File(extraFileFullPath.replace(".before.", ".after."))
            if (afterFile.exists()) {
              KotlinTestUtils.assertEqualsToFile(afterFile, file.text)
            }
            else {
              KotlinTestUtils.assertEqualsToFile(File(testDataPath, extraFileFullPath), file.text)
            }
          }
        }
      }
      catch (e: AssertionError) {
        throw e
      }
      catch (e: Throwable) {
        e.printStackTrace()
        TestCase.fail(getTestName(true))
      }
    }, "", "")
  }

  private val availableActions: List<IntentionAction>
    get() = myFixture.availableIntentions + myFixture.getAllQuickFixes()

  class TestFile internal constructor(val path: String, val content: String)

  companion object {
    private fun getActionsTexts(availableActions: List<IntentionAction>): List<String> =
      availableActions.map(IntentionAction::getText)

    private fun extraFileNamePrefix(mainFileName: String): String =
      mainFileName.replace(".Main.kt", ".").replace(".Main.java", ".")

    private fun findActionByPattern(pattern: Pattern, availableActions: List<IntentionAction>): IntentionAction? =
      availableActions.firstOrNull { pattern.matcher(it.text).matches() }

    fun doAction(
      text: String,
      file: PsiFile,
      editor: Editor,
      actionShouldBeAvailable: Boolean,
      testFilePath: String,
      getAvailableActions: () -> List<IntentionAction>,
      doHighlighting: () -> List<HighlightInfo>,
      shouldBeAvailableAfterExecution: Boolean = false
    ) {
      val pattern = if (text.startsWith("/"))
        Pattern.compile(text.substring(1, text.length - 1))
      else
        Pattern.compile(StringUtil.escapeToRegexp(text))

      val availableActions = getAvailableActions()
      val action = findActionByPattern(pattern, availableActions)

      if (action == null) {
        if (actionShouldBeAvailable) {
          val texts = getActionsTexts(availableActions)
          val infos = doHighlighting()
          TestCase.fail("Action with text '" + text + "' is not available in test " + testFilePath + "\n" +
                        "Available actions (" + texts.size + "): \n" +
                        StringUtil.join(texts, "\n") +
                        "\nActions:\n" +
                        StringUtil.join(availableActions, "\n") +
                        "\nInfos:\n" +
                        StringUtil.join(infos, "\n"))
        }
        else {
          DirectiveBasedActionUtils.checkAvailableActionsAreExpected(file, availableActions)
        }
      }
      else {
        if (!actionShouldBeAvailable) {
          TestCase.fail("Action '$text' is available (but must not) in test $testFilePath")
        }

        CodeInsightTestFixtureImpl.invokeIntention(action, file, editor)

        if (!shouldBeAvailableAfterExecution) {
          val afterAction = findActionByPattern(pattern, getAvailableActions())

          if (afterAction != null) {
            TestCase.fail("Action '$text' is still available after its invocation in test $testFilePath")
          }
        }
      }
    }
  }
}
