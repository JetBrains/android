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
package com.android.tools.idea.gradle.dsl.kotlin

import com.android.tools.idea.gradle.dsl.model.BuildModelContext
import com.android.tools.idea.gradle.dsl.parser.GradleDslTransformerFactory
import com.android.tools.idea.gradle.dsl.parser.files.GradleDslFile
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.psi.KtFile

class KotlinDslTransformerFactory : GradleDslTransformerFactory {
  override fun canTransform(psiFile: PsiFile) = psiFile is KtFile

  override fun createParser(psiFile: PsiFile, context: BuildModelContext, dslFile: GradleDslFile) =
    KotlinDslParser(psiFile as KtFile, context, dslFile)

  override fun createWriter(context: BuildModelContext) = KotlinDslWriter(context)
}