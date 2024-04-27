/*
 * Copyright (C) 2023 The Android Open Source Project
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

import com.android.tools.idea.lint.common.AndroidQuickfixContexts
import com.android.tools.idea.lint.common.DefaultLintQuickFix
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.android.util.AndroidBundle
import org.jetbrains.kotlin.android.canAddParcelable
import org.jetbrains.kotlin.android.implementParcelable
import org.jetbrains.kotlin.android.isParcelize
import org.jetbrains.kotlin.idea.base.plugin.suppressAndroidPlugin
import org.jetbrains.kotlin.psi.KtClass


class ParcelableQuickFix : DefaultLintQuickFix(AndroidBundle.message("implement.parcelable.intention.text")) {
    override fun apply(startElement: PsiElement, endElement: PsiElement, context: AndroidQuickfixContexts.Context) {
        startElement.getTargetClass()?.implementParcelable()
    }

    override fun isApplicable(startElement: PsiElement, endElement: PsiElement, contextType: AndroidQuickfixContexts.ContextType): Boolean {
        if (suppressAndroidPlugin()) return false

        val targetClass = startElement.getTargetClass() ?: return false
        return targetClass.canAddParcelable() && !targetClass.isParcelize()
    }

    private fun PsiElement.getTargetClass(): KtClass? = PsiTreeUtil.getParentOfType(this, KtClass::class.java, false)
}