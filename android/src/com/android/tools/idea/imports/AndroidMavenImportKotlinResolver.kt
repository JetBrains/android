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
package com.android.tools.idea.imports

import com.intellij.codeInsight.intention.IntentionAction
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixRegistrar
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixesList
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KtQuickFixesListBuilder
import org.jetbrains.kotlin.idea.quickfix.QuickFixContributor
import org.jetbrains.kotlin.idea.quickfix.QuickFixes

/**
 * Registers an unresolved reference resolver in Kotlin files (K1) which recognizes classes from key
 * Maven artifacts and offers to add a dependency on them.
 */
class AndroidMavenImportKotlinResolver : QuickFixContributor {
  override fun registerQuickFixes(quickFixes: QuickFixes) {
    val action: IntentionAction = AndroidMavenImportIntentionAction()
    quickFixes.register(Errors.UNRESOLVED_REFERENCE, action)
  }
}

/**
 * Registers an unresolved reference resolver in Kotlin files (K2) which recognizes classes from key
 * Maven artifacts and offers to add a dependency on them.
 */
class AndroidMavenImportKotlinQuickFixRegistrar : KotlinQuickFixRegistrar() {
  private val factory =
    KotlinQuickFixFactory.IntentionBased<KaFirDiagnostic.UnresolvedReference> { diagnostic ->
      listOf(AndroidMavenImportIntentionAction())
    }

  override val list: KotlinQuickFixesList =
    KtQuickFixesListBuilder.registerPsiQuickFix { registerFactory(factory) }
}
