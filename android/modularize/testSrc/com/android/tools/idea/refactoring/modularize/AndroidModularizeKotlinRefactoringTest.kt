/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.refactoring.modularize

import com.android.AndroidProjectTypes.PROJECT_TYPE_LIBRARY
import com.intellij.openapi.application.runWriteAction
import com.intellij.refactoring.PackageWrapper
import com.intellij.refactoring.util.RefactoringUtil
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture
import com.intellij.testFramework.fixtures.TestFixtureBuilder
import org.jetbrains.android.AndroidTestCase
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider
import org.jetbrains.kotlin.idea.k2.refactoring.move.processor.K2MoveFilesHandler
import org.jetbrains.kotlin.idea.refactoring.move.moveFilesOrDirectories.MoveKotlinFileHandler
import org.jetbrains.kotlin.idea.util.sourceRoots

class AndroidModularizeKotlinRefactoringTest : AndroidTestCase() {

  override fun configureAdditionalModules(
    projectBuilder: TestFixtureBuilder<IdeaProjectTestFixture>,
    modules: List<MyAdditionalModuleData>
  ) {
    addModuleWithAndroidFacet(projectBuilder, modules, "library", PROJECT_TYPE_LIBRARY, true)
  }

  // Regression test for b/128851036.
  fun testPrepareMoveRefactoring() {
    myFixture.addFileToProject(
      "/res/values/values.xml",
      // language=xml
      """
        <resources>
          <string name="appString">Hello from app</string>
        </resources>
        """.trimIndent()
    )

    val activity = myFixture.addFileToProject(
      "/src/p1/p2/MainActivity.kt",
      // language=kotlin
      """
        package p1.p2

        import android.app.Activity

        class MainActivity : Activity() {
          val s = R.string.appString
        }
        """.trimIndent()
    )
    myFixture.configureFromExistingVirtualFile(activity.virtualFile)

    val moveHandler = if (KotlinPluginModeProvider.isK2Mode()) K2MoveFilesHandler() else MoveKotlinFileHandler()

    @OptIn(KaAllowAnalysisOnEdt::class)
    allowAnalysisOnEdt {
      runWriteAction {
        val psiDirectory = RefactoringUtil.createPackageDirectoryInSourceRoot(
          PackageWrapper(myFixture.psiManager, "p1.p2"), myAdditionalModules[0].sourceRoots[0])

        moveHandler.findUsages(activity, psiDirectory, true, true)
      }
    }
  }
}
