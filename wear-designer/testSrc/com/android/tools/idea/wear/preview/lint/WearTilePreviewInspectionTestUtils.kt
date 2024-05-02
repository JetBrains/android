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
package com.android.tools.idea.wear.preview.lint

import com.android.tools.idea.projectsystem.isAndroidTestModule
import com.android.tools.idea.projectsystem.isUnitTestModule
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.modules
import com.intellij.openapi.roots.SourceFolder
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.parentOfType
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.elementsInRange

/**
 * Retrieves the name of the method containing the [PsiElement] associated with the [highlightInfo]
 * in the given [PsiFile]. This method assumes that only a single method contains the [PsiElement].
 * This method supports both Kotlin and Java.
 */
internal fun PsiFile.containingMethodName(highlightInfo: HighlightInfo) =
  runReadAction {
      elementsInRange(TextRange.create(highlightInfo.startOffset, highlightInfo.endOffset))
    }
    .mapNotNull {
      runReadAction {
        it.parentOfType<PsiMethod>()?.name ?: it.parentOfType<KtNamedFunction>()?.name
      }
    }
    .single()

/**
 * Adds the "src/test" source root to the project. The project rule must use
 * [AndroidProjectRule.withAndroidModel] for the unit test module to be available.
 */
internal fun CodeInsightTestFixture.addUnitTestSourceRoot() {
  val unitTestModule = project.modules.single { it.isUnitTestModule() }
  val unitTestRoot = tempDirFixture.findOrCreateDir("src/test")
  runInEdt {
    ApplicationManager.getApplication().runWriteAction<SourceFolder> {
      PsiTestUtil.addSourceRoot(unitTestModule, unitTestRoot, true)
    }
  }
}

/**
 * Adds the "src/androidTest" source root to the project. The project rule must use
 * [AndroidProjectRule.withAndroidModel] for the android test module to be available.
 */
internal fun CodeInsightTestFixture.addAndroidTestSourceRoot() {
  val androidTestModule = project.modules.single { it.isAndroidTestModule() }
  val androidTestSourceRoot = tempDirFixture.findOrCreateDir("src/androidTest")
  runInEdt {
    ApplicationManager.getApplication().runWriteAction<SourceFolder> {
      PsiTestUtil.addSourceRoot(androidTestModule, androidTestSourceRoot, true)
    }
  }
}
