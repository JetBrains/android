/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.compose.annotator

import com.intellij.lang.Language
import com.intellij.lang.LanguageExtension
import com.intellij.lang.LanguageExtensionPoint
import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.testFramework.fixtures.CodeInsightTestFixture

internal fun CodeInsightTestFixture.findPsiFile(tempDirPath: String): PsiFile {
  val file = checkNotNull(findFileInTempDir(tempDirPath))
  return checkNotNull(PsiManager.getInstance(project).findFile(file))
}

internal fun <E : Any> CodeInsightTestFixture.registerLanguageExtensionPoint(
  extension: LanguageExtension<E>,
  implementation: E,
  language: Language
) {
  ApplicationManager.getApplication()
    .extensionArea
    .getExtensionPoint<LanguageExtensionPoint<E>>(extension.name)
    .registerExtension(LanguageExtensionPoint(language.id, implementation), testRootDisposable)
}
