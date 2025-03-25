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

import com.intellij.codeInsight.daemon.QuickFixActionRegistrar
import com.intellij.codeInsight.quickfix.UnresolvedReferenceQuickFixProvider
import com.intellij.psi.PsiJavaCodeReferenceElement

/**
 * Registers an unresolved reference resolver in Java files which recognizes classes from key Maven
 * artifacts and offers to add a dependency on them
 */
class AndroidMavenImportJavaResolver :
  UnresolvedReferenceQuickFixProvider<PsiJavaCodeReferenceElement>() {
  override fun registerFixes(ref: PsiJavaCodeReferenceElement, registrar: QuickFixActionRegistrar) {
    registrar.register(AndroidMavenImportIntentionAction())
  }

  override fun getReferenceClass(): Class<PsiJavaCodeReferenceElement> =
    PsiJavaCodeReferenceElement::class.java
}
