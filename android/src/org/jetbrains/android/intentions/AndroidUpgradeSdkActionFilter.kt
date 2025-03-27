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
package org.jetbrains.android.intentions

import com.android.tools.idea.util.androidFacet
import com.intellij.codeInsight.daemon.impl.IntentionActionFilter
import com.intellij.codeInsight.daemon.impl.analysis.UpgradeSdkFix
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.psi.PsiFile

/**
 * Filters out upgrade sdk intention when some unsupported language feature is not detected.
 *
 * For the update sdk fix, it never works and clicking on it would lead to null pointer exception because of some implementation
 * assumptions. Besides that, intellij doesn't have all the context of suggesting a proper language level due to desugaring and other
 * factors.
 */
class AndroidUpgradeSdkActionFilter : IntentionActionFilter {

  override fun accept(intentionAction: IntentionAction, file: PsiFile?): Boolean {
    return !(intentionAction is UpgradeSdkFix && file != null && file.androidFacet != null)
  }
}