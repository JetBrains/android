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
package com.android.tools.compose.aa.intentions

import com.android.tools.compose.intentions.AddComposableAnnotationQuickFix
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixRegistrar
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixesList
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KtQuickFixesListBuilder

class ComposeIdePluginQuickFixRegistrar : KotlinQuickFixRegistrar() {
  override val list: KotlinQuickFixesList =
    KtQuickFixesListBuilder.registerPsiQuickFix {
      registerFactory(ComposeCreateComposableFunctionQuickFix.factory)
      registerFactory(AddComposableAnnotationQuickFix.k2DiagnosticFixFactory)
    }
}
