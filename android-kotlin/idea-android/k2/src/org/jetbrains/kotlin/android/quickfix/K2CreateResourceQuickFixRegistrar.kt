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

import com.intellij.codeInsight.intention.IntentionAction
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic.UnresolvedReference
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixRegistrar
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixesList
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KtQuickFixesListBuilder
import org.jetbrains.kotlin.psi.KtSimpleNameExpression

class K2CreateResourceQuickFixRegistrar : KotlinQuickFixRegistrar() {
    override val list: KotlinQuickFixesList = KtQuickFixesListBuilder.registerPsiQuickFix {
        registerFactory(FACTORY)
    }

    companion object {
        private val FACTORY =  KotlinQuickFixFactory.IntentionBased { diagnostic: UnresolvedReference ->
            val ref = diagnostic.psi as? KtSimpleNameExpression
                      ?: return@IntentionBased emptyList<IntentionAction>()

            getCreateResourceQuickFixActions(ref)
        }
    }
}