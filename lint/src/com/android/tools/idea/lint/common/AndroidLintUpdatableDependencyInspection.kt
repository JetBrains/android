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
package com.android.tools.idea.lint.common

import com.android.tools.lint.detector.api.Issue
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiElement

abstract class AndroidLintUpdatableDependencyInspection(displayName: String, issue: Issue) :
  AndroidLintInspectionBase(displayName, issue) {
  override fun getIntentions(
    startElement: PsiElement,
    endElement: PsiElement,
  ): Array<out IntentionAction>? {
    val actions = DependencyUpdateProvider.EP_NAME.extensionList.map { it.getUpdateProvider() }
    return actions.union(super.getIntentions(startElement, endElement).toList()).toTypedArray()
  }
}

interface DependencyUpdateProvider {
  fun getUpdateProvider(): IntentionAction

  companion object {
    @JvmStatic
    val EP_NAME: ExtensionPointName<DependencyUpdateProvider> =
      ExtensionPointName.create<DependencyUpdateProvider>(
        "com.android.tools.idea.lint.common.updateDepsProvider"
      )
  }
}
