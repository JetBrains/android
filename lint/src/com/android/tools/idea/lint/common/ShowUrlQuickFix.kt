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
package com.android.tools.idea.lint.common

import com.android.tools.lint.detector.api.LintFix.ShowUrl
import com.intellij.ide.BrowserUtil
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.Nls

class ShowUrlQuickFix(private val fix: ShowUrl) : DefaultLintQuickFix(null) {
  @Nls
  override fun getName(): String {
    return fix.getDisplayName() ?: fix.getFamilyName() ?: "Show ${fix.url}"
  }

  override fun startInWriteAction(): Boolean = false

  override fun apply(
    startElement: PsiElement,
    endElement: PsiElement,
    context: AndroidQuickfixContexts.Context
  ) {
    BrowserUtil.browse(fix.url)
    fix.onUrlOpen?.let { it() }
  }
}
