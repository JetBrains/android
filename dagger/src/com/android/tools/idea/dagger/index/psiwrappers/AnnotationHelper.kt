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
package com.android.tools.idea.dagger.index.psiwrappers

import com.intellij.psi.PsiModifierListOwner
import org.jetbrains.kotlin.psi.KtAnnotated

/**
 * Returns whether the element might be annotated by the given type (specified by fully-qualified name).
 *
 * For example, given an element with the annotation "@Inject", `getIsAnnotatedWith("javax.inject.Inject", ...)` would return true as long
 * as the file has appropriate import statements.
 *
 * Because this evaluation is happening at index time, it's not possible to say that the type actually matches. This function indicates at
 * best that an annotation *may* be of the given type. A return value of `false` means that it definitively will not be of that type.
 */
internal fun KtAnnotated.getIsAnnotatedWith(fqName: String, importHelper: KotlinImportHelper): Boolean =
  annotationEntries.any { annotationEntry ->
    annotationEntry.typeReference?.text?.let { text -> importHelper.getPossibleAnnotationText(fqName).contains(text) } == true
  }

/**
 * Returns whether the element might be annotated by the given type (specified by fully-qualified name).
 *
 * For example, given an element with the annotation "@Inject", `getIsAnnotatedWith("javax.inject.Inject", ...)` would return true as long
 * as the file has appropriate import statements.
 *
 * Because this evaluation is happening at index time, it's not possible to say that the type actually matches. This function indicates at
 * best that an annotation *may* be of the given type. A return value of `false` means that it definitively will not be of that type.
 */
internal fun PsiModifierListOwner.getIsAnnotatedWith(fqName: String, importHelper: JavaImportHelper): Boolean =
  annotations.any { annotation ->
    annotation.nameReferenceElement?.text?.let { text -> importHelper.getPossibleAnnotationText(fqName).contains(text) } == true
  }
