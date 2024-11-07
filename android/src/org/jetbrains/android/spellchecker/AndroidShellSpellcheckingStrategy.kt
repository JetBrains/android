/*
 * Copyright (C) 2020 The Android Open Source Project
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
package org.jetbrains.android.spellchecker

import com.android.SdkConstants
import com.intellij.openapi.project.DumbAware
import com.intellij.psi.PsiElement
import com.intellij.spellchecker.tokenizer.SpellcheckingStrategy
import com.intellij.spellchecker.tokenizer.Tokenizer

/**
 * [SpellcheckingStrategy] for Android Projects
 *
 * This strategy only ignores "gradlew" files, otherwise spell checking for shell files is handled by
 * [com.intellij.sh.spellchecker.ShSpellcheckingStrategy]
 */
class AndroidShellSpellcheckingStrategy : SpellcheckingStrategy(), DumbAware {

  override fun isMyContext(element: PsiElement) = SdkConstants.FN_GRADLE_WRAPPER_UNIX == element.containingFile?.name

  override fun getTokenizer(element: PsiElement?): Tokenizer<PsiElement> = EMPTY_TOKENIZER
}